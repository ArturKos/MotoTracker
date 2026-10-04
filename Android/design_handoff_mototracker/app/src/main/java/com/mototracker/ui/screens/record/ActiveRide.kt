package com.mototracker.ui.screens.record

import android.content.Context
import android.content.Intent
import com.mototracker.R
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.resource.StringResolver
import com.mototracker.data.diagnostics.RideDebugLogger
import com.mototracker.data.location.ReverseGeocoder
import com.mototracker.data.model.Route
import com.mototracker.data.repository.RefuelRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.domain.fuel.AutoUpdateBikeConsumptionUseCase
import com.mototracker.domain.naming.PartOfDay
import com.mototracker.domain.naming.RouteNameComposer
import com.mototracker.service.RecordingService
import com.mototracker.ui.map.GeoCoord
import com.mototracker.ui.map.TrackGeometry
import com.mototracker.ui.state.Units
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import com.mototracker.data.location.RideLocationCollector
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.network.WeatherClient
import com.mototracker.data.recording.ActiveSessionSnapshot
import com.mototracker.data.recording.PendingRefuel
import com.mototracker.data.recording.RecordingSessionStore
import com.mototracker.data.sensor.LeanSensorSource
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.core.time.TimeProvider
import com.mototracker.domain.fuel.FuelAdjustmentMode
import com.mototracker.domain.recording.RecordingEngine
import com.mototracker.domain.recording.RecordingEngineState
import com.mototracker.domain.recording.RecordingMetrics
import com.mototracker.domain.recording.TrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide slot for the single [ActiveRide].
 *
 * The ride must outlive any one [RecordingViewModel]: when the rider swipes the app away from
 * recents (or Android destroys the Activity to reclaim memory) the ViewModel is cleared, but
 * [RecordingService] keeps the process alive and the ride must keep recording. A freshly created
 * ViewModel picks the same [ActiveRide] up from here, and the Android Auto service creates it
 * when the car connects before the phone UI was ever opened.
 *
 * @param factory Builds the production ride from injected singletons; null in unit tests, where
 *                the ViewModel supplies its own fakes through [obtain]'s fallback.
 */
@Singleton
class ActiveRideHolder private constructor(private val factory: (() -> ActiveRide)?) {

    /** Production constructor: the ride is built from [deps]. */
    @Inject
    constructor(deps: ActiveRideDeps) : this({ deps.create() })

    /** Test constructor: the first [obtain] caller supplies the ride. */
    constructor() : this(null)

    /** The ride for this process, once created. */
    var ride: ActiveRide? = null
        private set

    /** Returns the process ride, creating it on first use (from [factory], else from [fallback]). */
    fun obtain(fallback: () -> ActiveRide): ActiveRide =
        ride ?: (factory?.invoke() ?: fallback()).also { ride = it }

    /** Returns the process ride, creating it from the injected factory; null only in tests. */
    fun obtainOrNull(): ActiveRide? = ride ?: factory?.invoke()?.also { ride = it }
}

/**
 * Injected collaborators for the production [ActiveRide].
 *
 * Starts and stops [RecordingService] from the ride's phase, so the foreground service follows
 * the ride whether it was started from the phone screen or from the car.
 */
class ActiveRideDeps @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rideLocationCollector: RideLocationCollector,
    private val leanSensorSource: LeanSensorSource,
    private val sessionStore: RecordingSessionStore,
    private val timeProvider: TimeProvider,
    private val rideDebugLogger: RideDebugLogger,
    private val settingsSource: AppSettingsSource,
    private val networkMonitor: NetworkMonitor,
    private val weatherClient: WeatherClient,
    private val routeRepository: RouteRepository,
    private val syncRepository: SyncRepository,
    private val refuelRepository: RefuelRepository,
    private val autoUpdateBikeConsumptionUseCase: AutoUpdateBikeConsumptionUseCase,
    private val reverseGeocoder: ReverseGeocoder,
    private val stringResolver: StringResolver,
    private val carBridge: CarRecordingBridge,
) {
    /** Builds the process ride on the main dispatcher. */
    fun create(): ActiveRide = ActiveRide(
        rideLocationCollector = rideLocationCollector,
        leanSensorSource = leanSensorSource,
        sessionStore = sessionStore,
        timeProvider = timeProvider,
        rideDebugLogger = rideDebugLogger,
        settingsSource = settingsSource,
        networkMonitor = networkMonitor,
        weatherClient = weatherClient,
        routeRepository = routeRepository,
        syncRepository = syncRepository,
        refuelRepository = refuelRepository,
        autoUpdateBikeConsumptionUseCase = autoUpdateBikeConsumptionUseCase,
        reverseGeocoder = reverseGeocoder,
        stringResolver = stringResolver,
        carBridge = carBridge,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        onPhaseChanged = { phase, routeId -> driveService(phase, routeId) },
    )

    private fun driveService(phase: RecordingPhase, routeId: String?) {
        val intent = Intent(context, RecordingService::class.java)
        when (phase) {
            RecordingPhase.Recording -> {
                if (routeId != null) intent.putExtra(RecordingService.EXTRA_ROUTE_ID, routeId)
                runCatching { context.startForegroundService(intent) }
                    .onFailure { rideDebugLogger.log("ERROR", "startForegroundService: ${it.message}") }
            }
            RecordingPhase.Idle -> context.stopService(intent)
            RecordingPhase.Paused -> Unit
        }
    }
}

/**
 * Live recording session state that lives for the whole process instead of a ViewModel.
 *
 * Owns the [RecordingEngine], the recording phase, the on-screen track, and the background
 * workers that must keep running while the UI is gone: the 1 Hz ticker, the GPS sample feed,
 * the lean-angle feed, the one-shot weather fetch and the crash-recovery snapshot (B20).
 * [RecordingViewModel] drives it through the methods below and mirrors [state] into its UI state.
 *
 * Not thread-safe by design: every caller (the ViewModel and the workers launched in [scope])
 * runs on the main dispatcher, so all mutations are serialised on one thread.
 *
 * It also serves Android Auto directly — it publishes metrics/phase/units to [CarRecordingBridge]
 * and executes the car's start/pause/resume/stop commands — so the car keeps working when the
 * phone UI is closed.
 *
 * @param scope          Process-lived scope (main dispatcher) that owns the background workers.
 * @param onPhaseChanged Called on every phase change after creation; production starts/stops
 *                       [RecordingService] here. Null in unit tests.
 */
class ActiveRide(
    private val rideLocationCollector: RideLocationCollector,
    private val leanSensorSource: LeanSensorSource,
    private val sessionStore: RecordingSessionStore,
    private val timeProvider: TimeProvider,
    private val rideDebugLogger: RideDebugLogger,
    private val settingsSource: AppSettingsSource,
    private val networkMonitor: NetworkMonitor,
    private val weatherClient: WeatherClient,
    private val routeRepository: RouteRepository,
    private val syncRepository: SyncRepository,
    private val refuelRepository: RefuelRepository,
    private val autoUpdateBikeConsumptionUseCase: AutoUpdateBikeConsumptionUseCase,
    private val reverseGeocoder: ReverseGeocoder,
    private val stringResolver: StringResolver,
    private val carBridge: CarRecordingBridge,
    private val scope: CoroutineScope,
    private val onPhaseChanged: ((RecordingPhase, String?) -> Unit)? = null,
) {

    /** Snapshot of the ride fields the Recording screen renders. */
    data class State(
        val phase: RecordingPhase = RecordingPhase.Idle,
        val metrics: RecordingMetrics,
        val trackPoints: List<TrackPoint> = emptyList(),
        val activeRouteId: String? = null,
    )

    /** The accumulator for the current session; exposed for read-only snapshots and the finish payload. */
    val engine = RecordingEngine()

    private val _state = MutableStateFlow(State(metrics = engine.snapshot()))

    /** Live ride state; survives ViewModel recreation. */
    val state: StateFlow<State> = _state.asStateFlow()

    /** Epoch-ms timestamp captured at recording start; used for part-of-day naming. */
    var recordingStartMs: Long = 0L
        private set

    /** Most recent bike id from settings; written into crash-recovery snapshots. */
    var currentBikeId: String? = null

    /** Route UUID pre-assigned at start so BLE waves can reference the route before it is saved. */
    var pendingRouteId: String? = null
        private set

    /** Refuel events logged before the route row exists (G5). */
    val pendingRefuels: List<PendingRefuel> get() = _pendingRefuels.toList()
    private val _pendingRefuels = mutableListOf<PendingRefuel>()

    /** True while the session continues a previously saved route (J5). */
    var resumingExistingRoute: Boolean = false
        private set

    /** Original name of the continued route (J5). */
    var existingRouteName: String = ""
        private set

    /** Original start timestamp of the continued route (J5). */
    var existingRouteDateEpochMs: Long = 0L
        private set

    /** Weather JSON fetched on the first GPS fix; null when offline or not yet fetched (K5). */
    var pendingWxJson: String? = null
        private set

    private var weatherFetchedForRide = false

    /** Latest fuel model; used when a ride is started from the car without the phone UI. */
    private var lastFuelLper100km: Double = 5.0
    private var lastTankCapacityL: Double? = null

    private var finishJob: Deferred<FinishResult>? = null

    /** Outcome of [finishAsync]: the saved route id and whether it was saved while offline. */
    data class FinishResult(val routeId: String, val offline: Boolean)
    private var tickerJob: Job? = null
    private var locationJob: Job? = null
    private var leanJob: Job? = null

    init {
        // Android Auto: mirror the ride to the car and keep units in sync.
        scope.launch { state.collect { carBridge.publish(it.metrics, it.phase) } }
        scope.launch {
            settingsSource.settings
                .map { if (it.units == "imperial") Units.IMPERIAL else Units.METRIC }
                .distinctUntilChanged()
                .collect { carBridge.publishUnits(it) }
        }
        // Android Auto: the ride itself executes car commands, so they work without the phone UI.
        scope.launch { carBridge.commands.collect { onCarCommand(it) } }
        // Foreground service follows the ride phase (skip the initial Idle).
        onPhaseChanged?.let { hook ->
            scope.launch {
                state.map { it.phase to it.activeRouteId }
                    .distinctUntilChanged { a, b -> a.first == b.first }
                    .drop(1)
                    .collect { (phase, routeId) -> hook(phase, routeId) }
            }
        }
    }

    private fun onCarCommand(event: RecordingEvent) {
        when (event) {
            is RecordingEvent.Start ->
                if (_state.value.phase == RecordingPhase.Idle) startNew(lastFuelLper100km, lastTankCapacityL)
            is RecordingEvent.Pause -> if (_state.value.phase == RecordingPhase.Recording) pause()
            is RecordingEvent.Resume -> if (_state.value.phase == RecordingPhase.Paused) resume()
            is RecordingEvent.Finish -> if (_state.value.phase != RecordingPhase.Idle) finishAsync()
            else -> Unit
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /** Starts a brand-new ride with the given fuel model and moves to [RecordingPhase.Recording]. */
    fun startNew(fuelLper100km: Double, tankCapacityL: Double?) {
        engine.reset(fuelLper100km = fuelLper100km, tankCapacityL = tankCapacityL)
        rideDebugLogger.beginRide()
        recordingStartMs = timeProvider.nowEpochMs()
        pendingRouteId = UUID.randomUUID().toString()
        pendingWxJson = null
        weatherFetchedForRide = false
        resumingExistingRoute = false
        _pendingRefuels.clear()
        _state.value = State(
            phase = RecordingPhase.Recording,
            metrics = engine.snapshot(),
            trackPoints = emptyList(),
            activeRouteId = pendingRouteId,
        )
        // Defensive start — idempotent; the service is the primary starter.
        rideLocationCollector.start()
        startTicker()
        startLocationUpdates()
        startLeanUpdates()
    }

    /** Pauses the ride: stops the ticker and persists the paused flag. */
    fun pause() {
        _state.update { it.copy(phase = RecordingPhase.Paused) }
        rideDebugLogger.log("LIFECYCLE", "pause")
        tickerJob?.cancel()
        tickerJob = null
        saveSnapshot(paused = true)
    }

    /** Resumes a paused ride. */
    fun resume() {
        _state.update { it.copy(phase = RecordingPhase.Recording) }
        rideDebugLogger.log("LIFECYCLE", "resume")
        startTicker()
        saveSnapshot(paused = false)
    }

    /**
     * Restores an interrupted session from its crash-recovery snapshot (B20).
     * Lands in [RecordingPhase.Paused] so the rider resumes explicitly.
     */
    fun restoreSnapshot(snap: ActiveSessionSnapshot) {
        engine.restore(snap.engineState)
        recordingStartMs = snap.recordingStartMs
        _pendingRefuels.clear()
        _pendingRefuels.addAll(snap.pendingRefuels)
        _state.update {
            it.copy(
                phase = RecordingPhase.Paused,
                metrics = engine.snapshot(),
                trackPoints = snap.engineState.pathPoints,
            )
        }
        startLocationUpdates()
        startLeanUpdates()
    }

    /**
     * Continues a saved route (J5): seeds the engine from it and lands in [RecordingPhase.Paused].
     *
     * @param seed Engine state rebuilt from the saved route.
     */
    fun restoreRoute(
        routeId: String,
        routeName: String,
        routeDateEpochMs: Long,
        seed: RecordingEngineState,
        fuelLper100km: Double,
        tankCapacityL: Double?,
    ) {
        engine.restore(seed)
        engine.updateFuelConfig(fuelLper100km, tankCapacityL)
        pendingRouteId = routeId
        recordingStartMs = routeDateEpochMs
        resumingExistingRoute = true
        existingRouteName = routeName
        existingRouteDateEpochMs = routeDateEpochMs
        _state.value = State(
            phase = RecordingPhase.Paused,
            metrics = engine.snapshot(),
            trackPoints = seed.pathPoints,
            activeRouteId = routeId,
        )
        startLocationUpdates()
        startLeanUpdates()
    }

    /** Stops every background worker; called at the start of Finish before the payload is built. */
    fun stopWorkers() {
        tickerJob?.cancel(); tickerJob = null
        locationJob?.cancel(); locationJob = null
        leanJob?.cancel(); leanJob = null
    }

    /** Returns the ride to Idle after the route has been durably saved. */
    fun markIdle() {
        stopWorkers()
        pendingRouteId = null
        resumingExistingRoute = false
        _pendingRefuels.clear()
        _state.update {
            it.copy(phase = RecordingPhase.Idle, trackPoints = emptyList(), activeRouteId = null)
        }
    }

    /**
     * Ends the ride: stops the workers, names and saves the route, queues it for sync, persists
     * buffered refuels, clears the crash-recovery snapshot and returns to Idle.
     *
     * Runs in the process scope so a ViewModel being cleared mid-save cannot cut it short.
     * Calling it again while a finish is in flight returns the same [Deferred].
     */
    fun finishAsync(): Deferred<FinishResult> {
        finishJob?.let { if (it.isActive) return it }
        rideDebugLogger.log("LIFECYCLE", "finish")
        stopWorkers()
        return scope.async { saveFinishedRide() }.also { finishJob = it }
    }

    private suspend fun saveFinishedRide(): FinishResult {
        val settings = settingsSource.settings.first()
        val isOnline = networkMonitor.isOnline.first()
        val offline = settings.noInternet || !isOnline

        val result = engine.buildRoutePayload()

        // J5: When continuing an existing route, keep its original name and start time.
        val isResume = resumingExistingRoute
        val routeName: String
        val dateEpochMs: Long
        if (isResume) {
            routeName = existingRouteName
            dateEpochMs = existingRouteDateEpochMs
        } else {
            val startMs = if (recordingStartMs > 0L) recordingStartMs else timeProvider.nowEpochMs()
            val rideLabelResId = when (RouteNameComposer.partOfDay(startMs, ZoneId.systemDefault())) {
                PartOfDay.MORNING   -> R.string.route_name_ride_morning
                PartOfDay.AFTERNOON -> R.string.route_name_ride_afternoon
                PartOfDay.EVENING   -> R.string.route_name_ride_evening
                PartOfDay.NIGHT     -> R.string.route_name_ride_night
            }
            val rideLabel = stringResolver.getString(rideLabelResId)
            routeName = if (!offline) {
                val pts = TrackGeometry.parsePathJson(result.pathJson)
                val areas = sampleEvenly(pts, maxCount = 5).map { pt ->
                    try { reverseGeocoder.areaName(pt.lat, pt.lon) } catch (_: Exception) { null }
                }
                val area = RouteNameComposer.dominantArea(areas)
                if (area != null) {
                    RouteNameComposer.compose(rideLabel, area, stringResolver.getString(R.string.route_name_with_area))
                } else {
                    rideLabel
                }
            } else {
                rideLabel
            }
            dateEpochMs = timeProvider.nowEpochMs()
        }

        val route = Route(
            id = pendingRouteId ?: UUID.randomUUID().toString(),
            name = routeName,
            dateEpochMs = dateEpochMs,
            bikeId = settings.currentBikeId,
            km = result.metrics.distanceKm,
            durSec = result.metrics.durationSec,
            avg = result.metrics.avgSpeedKmh,
            max = result.metrics.maxSpeedKmh,
            lean = result.metrics.maxLeanDeg,
            elev = result.metrics.elevGainM,
            fuel = result.metrics.fuelL,
            synced = false,
            wxJson = if (isResume) null else pendingWxJson,
            pathJson = result.pathJson,
            speedJson = result.speedJson,
            elevProfileJson = result.elevProfileJson,
            notes = null,
            maxLeanLeftDeg = result.metrics.maxLeanLeftDeg,
            maxLeanRightDeg = result.metrics.maxLeanRightDeg,
            leanHistogramJson = result.leanHistogramJson,
        )

        routeRepository.save(route)
        syncRepository.enqueue(route.id)

        // G5: Persist any buffered refuel events now that the route row exists.
        val refuelsToSave = _pendingRefuels.toList()
        refuelsToSave.forEach { r ->
            refuelRepository.addRefuel(
                routeId = route.id,
                epochMs = r.epochMs,
                litres = r.litres,
                pricePerL = r.pricePerL,
            )
        }
        // K2: refresh per-bike consumption from ledger if any refuels were just saved.
        val bikeId = route.bikeId
        if (refuelsToSave.isNotEmpty() && bikeId != null) {
            runCatching { autoUpdateBikeConsumptionUseCase.run(bikeId) }
        }

        // B20: Clear snapshot AFTER the route is durably saved.
        sessionStore.clear()
        markIdle()
        rideDebugLogger.endRide()
        return FinishResult(routeId = route.id, offline = offline)
    }

    /** Forgets the pre-assigned route id when a resumable session is discarded (B20). */
    fun discardPendingRoute() {
        pendingRouteId = null
        _state.update { it.copy(activeRouteId = null) }
    }

    // ── Fuel ─────────────────────────────────────────────────────────────────

    /** Feeds a newly resolved fuel model into the engine without touching session anchors (I2). */
    fun updateFuelConfig(fuelLper100km: Double, tankCapacityL: Double?) {
        lastFuelLper100km = fuelLper100km
        lastTankCapacityL = tankCapacityL
        engine.updateFuelConfig(fuelLper100km, tankCapacityL)
        publishMetrics()
    }

    /** Applies a rider fuel-level correction (R1). */
    fun applyFuelCorrection(mode: FuelAdjustmentMode, value: Double) {
        engine.applyFuelCorrection(mode, value)
        publishMetrics()
    }

    /** Records a refuel: re-anchors the tank to full and buffers the event until Finish (G5). */
    fun refuel(litres: Double, pricePerL: Double) {
        engine.fillToFull()
        rideDebugLogger.log("FUEL", "refuel litres=$litres price=$pricePerL km=${engine.snapshot().distanceKm}")
        _pendingRefuels.add(
            PendingRefuel(epochMs = timeProvider.nowEpochMs(), litres = litres, pricePerL = pricePerL),
        )
        publishMetrics()
        saveSnapshot(paused = _state.value.phase == RecordingPhase.Paused)
    }

    // ── Workers ──────────────────────────────────────────────────────────────

    private fun publishMetrics() {
        _state.update { it.copy(metrics = engine.snapshot()) }
    }

    private fun saveSnapshot(paused: Boolean) {
        val snapshot = ActiveSessionSnapshot(
            engineState = engine.exportState(),
            recordingStartMs = recordingStartMs,
            bikeId = currentBikeId,
            paused = paused,
            pendingRefuels = _pendingRefuels.toList(),
        )
        scope.launch { sessionStore.save(snapshot) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                delay(1_000L)
                engine.tick(1L)
                publishMetrics()
            }
        }
    }

    private fun startLeanUpdates() {
        leanJob?.cancel()
        leanJob = scope.launch {
            leanSensorSource.leanAngles.collect { deg ->
                if (_state.value.phase == RecordingPhase.Recording) {
                    rideDebugLogger.log("LEAN", "angle=$deg")
                    engine.onLean(deg)
                    publishMetrics()
                }
            }
        }
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = scope.launch {
            rideLocationCollector.samples.collect { sample ->
                rideDebugLogger.log(
                    "GPS",
                    "lat=${sample.lat} lon=${sample.lng} alt=${sample.altitudeM} spd=${sample.speedMps}",
                )
                val metrics = engine.onLocation(sample)
                val pt = TrackPoint(sample.lat, sample.lng, sample.altitudeM, sample.timeMs)
                _state.update { it.copy(metrics = metrics, trackPoints = it.trackPoints + pt) }
                // K5: Fetch weather once on the first GPS fix when online.
                if (!weatherFetchedForRide) {
                    weatherFetchedForRide = true
                    scope.launch {
                        val settings = settingsSource.settings.first()
                        val isOnline = networkMonitor.isOnline.first()
                        if (!settings.noInternet && isOnline) {
                            weatherClient.fetch(sample.lat, sample.lng).onSuccess { wx ->
                                pendingWxJson = wx.toWxJson()
                                rideDebugLogger.log(
                                    "WEATHER",
                                    "tempC=${wx.tempC} hum=${wx.humidity} rain=${wx.rain}",
                                )
                            }
                        }
                    }
                }
                // B20: Persist the crash-recovery snapshot on each GPS fix.
                saveSnapshot(paused = false)
            }
        }
    }

    /** Returns up to [maxCount] evenly-spaced points sampled from [points]. */
    private fun sampleEvenly(points: List<GeoCoord>, maxCount: Int): List<GeoCoord> {
        if (points.size <= maxCount) return points
        val step = points.size.toDouble() / maxCount
        return List(maxCount) { i -> points[(i * step).toInt()] }
    }
}
