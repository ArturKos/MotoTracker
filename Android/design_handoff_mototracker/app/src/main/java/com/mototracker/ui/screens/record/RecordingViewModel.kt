package com.mototracker.ui.screens.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.R
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.resource.StringResolver
import com.mototracker.core.time.TimeProvider
import com.mototracker.data.diagnostics.RideDebugLogger
import com.mototracker.data.location.ReverseGeocoder
import com.mototracker.data.location.RideLocationCollector
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.domain.fuel.AutoUpdateBikeConsumptionUseCase
import com.mototracker.domain.fuel.FuelConsumptionCalculator
import com.mototracker.data.recording.ActiveSessionSnapshot
import com.mototracker.data.recording.PendingRefuel
import com.mototracker.data.recording.RecordingSessionStore
import com.mototracker.data.recording.ResumeRouteBus
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RefuelRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.sensor.HeadingSensorSource
import com.mototracker.data.sensor.LeanSensorSource
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.domain.naming.PartOfDay
import com.mototracker.domain.naming.RouteNameComposer
import com.mototracker.domain.recording.RecordingEngine
import com.mototracker.domain.recording.RouteResumeSeed
import com.mototracker.ui.map.GeoCoord
import com.mototracker.ui.map.TrackGeometry
import com.mototracker.ui.state.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Recording screen.
 *
 * Owns the recording state machine (Idle → Recording → Paused → Idle) and
 * co-ordinates:
 * - A 1-second ticker that advances elapsed time via [RecordingEngine.tick].
 * - Location updates from [RideLocationCollector] feeding [RecordingEngine.onLocation].
 * - Lean-sensor readings from [LeanSensorSource] feeding [RecordingEngine.onLean].
 * - On Finish: composes a sensible default route name (using [RouteNameComposer] +
 *   [ReverseGeocoder] when online), persists the route via [RouteRepository], and
 *   queues it for upload via [SyncRepository]; emits a [RecordingEffect.Saved] with
 *   the online/offline flag.
 * - On startup: checks [RecordingSessionStore] for an unfinished session and, if found,
 *   exposes it as [RecordingUiState.resumableSession] so the UI can prompt the user
 *   to resume or discard (B20).
 *
 * All collaborators are injectable so the ViewModel is fully unit-testable
 * with fakes (no Android runtime needed).
 *
 * @param rideLocationCollector  Process-scoped GPS stream owner; survives screen-off / Doze
 *                               because the stream is driven by [com.mototracker.service.RecordingService].
 * @param leanSensorSource    Gravity-sensor lean angles.
 * @param headingSensorSource Magnetometer + gravity heading source; feeds [RecordingUiState.liveHeadingDeg]
 *                            always, regardless of phase (F2 — live compass in Idle).
 * @param routeRepository     Persistence for completed routes.
 * @param syncRepository    Outbound sync queue.
 * @param settingsSource    Read-only app settings stream.
 * @param bikeRepository    Read-only bike list for resolving per-bike fuel consumption.
 * @param networkMonitor    Online/offline connectivity.
 * @param timeProvider      Wall-clock source (injectable for tests).
 * @param carBridge         App-scoped bridge that mirrors recording state to the Android Auto screen.
 * @param rideDebugLogger   Diagnostic logger; writes GPS/lean/lifecycle events to a per-ride log
 *                          file when diagnostics are enabled (no-op otherwise).
 * @param reverseGeocoder   Converts GPS coordinates to area names for the default route name.
 *                          Only called when the device is online.
 * @param stringResolver    Resolves localized string resources for the composed route name.
 * @param sessionStore      Durable storage for the in-progress recording session snapshot (B20).
 * @param refuelRepository                  Persistence for per-route refuel events (G5).
 * @param resumeRouteBus                    App-scoped bus for receiving "continue existing route" requests (J5).
 * @param autoUpdateBikeConsumptionUseCase  Refreshes bike consumption from the refuel ledger after a refuel is saved (K2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val rideLocationCollector: RideLocationCollector,
    private val leanSensorSource: LeanSensorSource,
    private val headingSensorSource: HeadingSensorSource,
    private val routeRepository: RouteRepository,
    private val syncRepository: SyncRepository,
    private val settingsSource: AppSettingsSource,
    private val bikeRepository: BikeRepository,
    private val networkMonitor: NetworkMonitor,
    private val timeProvider: TimeProvider,
    private val carBridge: CarRecordingBridge,
    private val rideDebugLogger: RideDebugLogger,
    private val reverseGeocoder: ReverseGeocoder,
    private val stringResolver: StringResolver,
    private val sessionStore: RecordingSessionStore,
    private val refuelRepository: RefuelRepository,
    private val resumeRouteBus: ResumeRouteBus,
    private val autoUpdateBikeConsumptionUseCase: AutoUpdateBikeConsumptionUseCase,
) : ViewModel() {

    private val engine = RecordingEngine()

    private val _uiState = MutableStateFlow(RecordingUiState())
    /** Live UI state for the Recording screen. */
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<RecordingEffect>(extraBufferCapacity = 2)
    /** One-shot side-effects (navigate-away, show toast). */
    val effects: SharedFlow<RecordingEffect> = _effects.asSharedFlow()

    private var tickerJob: Job? = null
    private var locationJob: Job? = null

    /** Epoch-ms timestamp captured at recording start; used for part-of-day naming. */
    private var recordingStartMs: Long = 0L

    /** Most-recently observed bike ID from settings; used when writing snapshots. */
    private var currentBikeId: String? = null

    /** Per-session fuel consumption resolved from the current bike; defaults to 5.0 L/100km. */
    private var currentBikeConsumption: Double = 5.0

    /** Tank capacity of the current bike in litres; null when not configured (E4: fill-to-full inert). */
    private var currentBikeTankCapacity: Double? = null

    /**
     * Route UUID pre-assigned when recording starts so BLE wave rows discovered
     * during the ride can reference the route before it is persisted at Finish.
     */
    private var pendingRouteId: String? = null

    /** In-memory buffer of refuel events logged before the route row exists (G5). */
    private val pendingRefuels = mutableListOf<PendingRefuel>()

    /** True while the session is a continuation of a previously saved route (J5). */
    private var resumingExistingRoute: Boolean = false

    /** Original name of the route being continued; used by [doFinish] to skip renaming (J5). */
    private var existingRouteName: String = ""

    /** Original start timestamp of the route being continued; preserved on save (J5). */
    private var existingRouteDateEpochMs: Long = 0L

    init {
        // Keep gpsOnRoad in sync with the user's GPS-correction setting;
        // also forward the active units preference to the Android Auto bridge.
        viewModelScope.launch {
            settingsSource.settings.collect { s ->
                currentBikeId = s.currentBikeId
                _uiState.update { it.copy(gpsOnRoad = s.gpsCorrect, keepScreenOn = s.keepScreenOn) }
                carBridge.publishUnits(if (s.units == "imperial") Units.IMPERIAL else Units.METRIC)
            }
        }
        // Mirror uiState to the Android Auto bridge so the car screen stays in sync.
        viewModelScope.launch {
            uiState.collect { s -> carBridge.publish(s.metrics, s.phase) }
        }
        // Handle recording control commands coming from the car screen.
        viewModelScope.launch {
            carBridge.commands.collect { event -> onEvent(event) }
        }
        // H4: Resolve per-bike fuel consumption from the refuel ledger; fall back to the
        // configured L/100km value, then to 5.0 when neither is available.
        // Also threads fuelPricePerL and currency into uiState for the live cost readout (G2).
        combine(
            settingsSource.settings,
            bikeRepository.observeAll(),
            routeRepository.observeSummaries(),
        ) { s, bikes, summaries ->
            val bike = bikes.find { it.id == s.currentBikeId }
            BikeData(
                bikeId = s.currentBikeId,
                bike = bike,
                bikeSummaries = summaries
                    .filter { it.bikeId == s.currentBikeId }
                    .sortedBy { it.dateEpochMs },
                currency = s.currency,
            )
        }.flatMapLatest { bd ->
            val refuelsFlow = bd.bikeId?.let { refuelRepository.observeAllForBike(it) }
                ?: flowOf(emptyList())
            refuelsFlow.map { refuels ->
                val routeRefuelMap = refuels.groupBy { it.routeId }
                val ledgerInput = bd.bikeSummaries.map { r ->
                    r.km to (routeRefuelMap[r.id] ?: emptyList())
                }
                val fills = FuelConsumptionCalculator.fillsFromLedger(ledgerInput)
                BikeSnapshot(
                    consumption = FuelConsumptionCalculator.consumptionLper100km(
                        fills,
                        bd.bike?.consumptionLper100km,
                    ) ?: 5.0,
                    tankCapacity = bd.bike?.tankCapacityL,
                    fuelPricePerL = bd.bike?.fuelPricePerL,
                    currency = bd.currency,
                )
            }
        }.onEach { bs ->
            currentBikeConsumption = bs.consumption
            currentBikeTankCapacity = bs.tankCapacity
            // I2: Feed the resolved fuel config into the engine reactively so remaining-fuel/range
            // and the icon colour are correct regardless of whether this emits before or after doStart().
            engine.updateFuelConfig(bs.consumption, bs.tankCapacity)
            _uiState.update {
                it.copy(
                    metrics = engine.snapshot(),
                    fuelPricePerL = bs.fuelPricePerL,
                    currency = bs.currency,
                )
            }
        }.launchIn(viewModelScope)

        // B20: Detect an unfinished session from a previous process lifetime.
        viewModelScope.launch {
            val existing = sessionStore.snapshot.first()
            if (existing != null) {
                _uiState.update { it.copy(resumableSession = existing) }
            }
        }

        // J5: React to "continue an existing route" requests from the Route Detail bus.
        viewModelScope.launch {
            resumeRouteBus.requests.collect { routeId -> doResumeRoute(routeId) }
        }

        // F2: Always-on lean collector — liveLeanDeg updates regardless of phase so the
        // rider can see the tilt bar before starting a ride.  Engine and logger are only
        // fed while actively Recording.
        viewModelScope.launch {
            leanSensorSource.leanAngles.collect { deg ->
                val currentPhase = _uiState.value.phase
                _uiState.update { it.copy(liveLeanDeg = deg) }
                if (currentPhase == RecordingPhase.Recording) {
                    rideDebugLogger.log("LEAN", "angle=$deg")
                    engine.onLean(deg)
                    _uiState.update { it.copy(metrics = engine.snapshot()) }
                }
            }
        }

        // F2: Always-on heading collector — liveHeadingDeg updates regardless of phase so
        // the compass needle is live on the Idle screen.  GPS bearing (engine) remains the
        // authoritative heading recorded per D6.
        viewModelScope.launch {
            headingSensorSource.headings.collect { deg ->
                _uiState.update { it.copy(liveHeadingDeg = deg) }
            }
        }
    }

    /**
     * Dispatches a [RecordingEvent] from the UI, driving the recording state machine.
     */
    fun onEvent(event: RecordingEvent) {
        when (event) {
            is RecordingEvent.Start -> doStart()
            is RecordingEvent.Pause -> doPause()
            is RecordingEvent.Resume -> doResume()
            is RecordingEvent.Finish -> doFinish()
            is RecordingEvent.ResumeSession -> doResumeSession()
            is RecordingEvent.DiscardSession -> doDiscardSession()
            is RecordingEvent.ShowRefuelDialog -> doShowRefuelDialog()
            is RecordingEvent.ConfirmRefuel -> doConfirmRefuel(event.litres, event.pricePerL)
            is RecordingEvent.DismissRefuelDialog -> _uiState.update { it.copy(showRefuelDialog = false) }
            is RecordingEvent.RequestStop -> _uiState.update { it.copy(showStopConfirmDialog = true) }
            is RecordingEvent.DismissStopDialog -> _uiState.update { it.copy(showStopConfirmDialog = false) }
            is RecordingEvent.ConfirmStop -> {
                _uiState.update { it.copy(showStopConfirmDialog = false) }
                doFinish()
            }
            is RecordingEvent.ResumeRoute -> viewModelScope.launch { doResumeRoute(event.routeId) }
        }
    }

    // ── State machine ────────────────────────────────────────────────────────

    private fun doStart() {
        engine.reset(fuelLper100km = currentBikeConsumption, tankCapacityL = currentBikeTankCapacity)
        rideDebugLogger.beginRide()
        recordingStartMs = timeProvider.nowEpochMs()
        pendingRouteId = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(
                phase = RecordingPhase.Recording,
                metrics = engine.snapshot(),
                trackPoints = emptyList(),
                resumableSession = null,
                activeRouteId = pendingRouteId,
            )
        }
        // Defensive start — idempotent; the service owns stop() and is the primary starter.
        rideLocationCollector.start()
        startTicker()
        startLocationUpdates()
    }

    private fun doPause() {
        _uiState.update { it.copy(phase = RecordingPhase.Paused) }
        rideDebugLogger.log("LIFECYCLE", "pause")
        tickerJob?.cancel()
        tickerJob = null
        // Persist paused flag so a kill during pause is recoverable.
        viewModelScope.launch {
            sessionStore.save(
                ActiveSessionSnapshot(
                    engineState = engine.exportState(),
                    recordingStartMs = recordingStartMs,
                    bikeId = currentBikeId,
                    paused = true,
                    pendingRefuels = pendingRefuels.toList(),
                ),
            )
        }
    }

    private fun doResume() {
        _uiState.update { it.copy(phase = RecordingPhase.Recording) }
        rideDebugLogger.log("LIFECYCLE", "resume")
        startTicker()
        // Update paused=false in the persisted snapshot.
        viewModelScope.launch {
            sessionStore.save(
                ActiveSessionSnapshot(
                    engineState = engine.exportState(),
                    recordingStartMs = recordingStartMs,
                    bikeId = currentBikeId,
                    paused = false,
                    pendingRefuels = pendingRefuels.toList(),
                ),
            )
        }
    }

    private fun doFinish() {
        rideDebugLogger.log("LIFECYCLE", "finish")
        tickerJob?.cancel()
        locationJob?.cancel()
        tickerJob = null
        locationJob = null

        viewModelScope.launch {
            val settings = settingsSource.settings.first()
            val isOnline = networkMonitor.isOnline.first()
            val offline = settings.offline || settings.offlineOnly || !isOnline

            val result = engine.buildRoutePayload()

            // J5: When continuing an existing route, keep its original name and start time.
            val isResume = resumingExistingRoute
            resumingExistingRoute = false

            val routeName: String
            val dateEpochMs: Long
            if (isResume) {
                routeName = existingRouteName
                dateEpochMs = existingRouteDateEpochMs
            } else {
                // Compose a sensible default name from part-of-day + optional reverse geocoding.
                val startMs = if (recordingStartMs > 0L) recordingStartMs else timeProvider.nowEpochMs()
                val pod = RouteNameComposer.partOfDay(startMs, ZoneId.systemDefault())
                val rideLabelResId = when (pod) {
                    PartOfDay.MORNING   -> R.string.route_name_ride_morning
                    PartOfDay.AFTERNOON -> R.string.route_name_ride_afternoon
                    PartOfDay.EVENING   -> R.string.route_name_ride_evening
                    PartOfDay.NIGHT     -> R.string.route_name_ride_night
                }
                val rideLabel = stringResolver.getString(rideLabelResId)
                routeName = if (!offline) {
                    val pts = TrackGeometry.parsePathJson(result.pathJson)
                    val sampled = sampleEvenly(pts, maxCount = 5)
                    val areas = sampled.map { pt ->
                        try { reverseGeocoder.areaName(pt.lat, pt.lon) } catch (_: Exception) { null }
                    }
                    val area = RouteNameComposer.dominantArea(areas)
                    if (area != null) {
                        val template = stringResolver.getString(R.string.route_name_with_area)
                        RouteNameComposer.compose(rideLabel, area, template)
                    } else {
                        rideLabel
                    }
                } else {
                    rideLabel
                }
                dateEpochMs = timeProvider.nowEpochMs()
            }

            val routeId = pendingRouteId ?: UUID.randomUUID().toString()
            pendingRouteId = null
            val route = Route(
                id = routeId,
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
                wxJson = null,
                pathJson = result.pathJson,
                speedJson = result.speedJson,
                elevProfileJson = result.elevProfileJson,
                notes = null,
                maxLeanLeftDeg = result.metrics.maxLeanLeftDeg,
                maxLeanRightDeg = result.metrics.maxLeanRightDeg,
            )

            routeRepository.save(route)
            syncRepository.enqueue(route.id)

            // G5: Persist any buffered refuel events now that the route row exists.
            val refuelsToSave = pendingRefuels.toList()
            pendingRefuels.clear()
            refuelsToSave.forEach { r ->
                refuelRepository.addRefuel(
                    routeId = route.id,
                    epochMs = r.epochMs,
                    litres = r.litres,
                    pricePerL = r.pricePerL,
                )
            }
            // K2: refresh per-bike consumption from ledger if any refuels were just saved.
            if (refuelsToSave.isNotEmpty()) {
                val bikeId = route.bikeId
                if (bikeId != null) {
                    runCatching { autoUpdateBikeConsumptionUseCase.run(bikeId) }
                }
            }

            // B20: Clear snapshot AFTER the route is durably saved.
            sessionStore.clear()

            _uiState.update { it.copy(phase = RecordingPhase.Idle, trackPoints = emptyList(), activeRouteId = null) }
            _effects.emit(RecordingEffect.Saved(offline = offline))
            _effects.emit(RecordingEffect.NavigateToDetail(route.id))
            rideDebugLogger.endRide()
        }
    }

    /**
     * Restores engine state from an interrupted session snapshot (B20).
     *
     * Sets phase to [RecordingPhase.Paused] so the user must explicitly tap Resume
     * to continue recording (live GPS / service restart is an on-device concern 🔬).
     * Track points are rebuilt from [RecordingEngineState.pathPoints].
     */
    private fun doResumeSession() {
        val snap = _uiState.value.resumableSession ?: return
        engine.restore(snap.engineState)
        recordingStartMs = snap.recordingStartMs
        // G5: Restore the pending refuel buffer from the snapshot so events are not lost.
        pendingRefuels.clear()
        pendingRefuels.addAll(snap.pendingRefuels)
        val trackPoints = snap.engineState.pathPoints.map { (lat, lng) -> GeoCoord(lat, lng) }
        _uiState.update {
            it.copy(
                phase = RecordingPhase.Paused,
                metrics = engine.snapshot(),
                trackPoints = trackPoints,
                resumableSession = null,
            )
        }
        // Start location updates so GPS fixes accumulate once the user taps Resume.
        // Lean is already live from the always-on init collector (F2).
        startLocationUpdates()
    }

    /** Clears a detected resumable session without restoring it (B20). */
    private fun doDiscardSession() {
        pendingRouteId = null
        _uiState.update { it.copy(resumableSession = null, activeRouteId = null) }
        viewModelScope.launch { sessionStore.clear() }
    }

    /**
     * Restores engine state from an existing saved route so the rider can append to it (J5).
     *
     * Guard: no-op unless the current phase is [RecordingPhase.Idle] with no pending
     * resumable session, preventing state clobbering during an active or paused ride.
     *
     * Sets phase to [RecordingPhase.Paused] so the rider must tap Resume explicitly;
     * live GPS continuation is an on-device concern (🔬).
     *
     * @param routeId UUID of the saved route to continue.
     */
    private suspend fun doResumeRoute(routeId: String) {
        val state = _uiState.value
        if (state.phase != RecordingPhase.Idle || state.resumableSession != null) return
        val route = routeRepository.getById(routeId) ?: return

        val seed = RouteResumeSeed.fromRoute(route)
        engine.restore(seed)
        engine.updateFuelConfig(currentBikeConsumption, currentBikeTankCapacity)

        pendingRouteId = routeId
        recordingStartMs = route.dateEpochMs
        resumingExistingRoute = true
        existingRouteName = route.name
        existingRouteDateEpochMs = route.dateEpochMs

        val trackPoints = seed.pathPoints.map { (lat, lng) -> GeoCoord(lat, lng) }
        _uiState.update {
            it.copy(
                phase = RecordingPhase.Paused,
                metrics = engine.snapshot(),
                trackPoints = trackPoints,
                resumableSession = null,
                activeRouteId = routeId,
            )
        }
        startLocationUpdates()
    }

    /**
     * Shows the refuel input dialog pre-filled with the current bike's tank capacity and price (G5).
     *
     * Replaces the old instant fill-to-full so the rider can confirm or adjust the values
     * before the event is persisted. Opens for ALL bikes, including those without a configured
     * tank capacity — in that case [refuelDialogLitres] is pre-filled with 0.0 and the rider
     * must enter a value; the dialog validates litres > 0.0 before enabling confirm.
     */
    private fun doShowRefuelDialog() {
        val tankCap = currentBikeTankCapacity ?: 0.0
        _uiState.update {
            it.copy(
                showRefuelDialog = true,
                refuelDialogLitres = tankCap,
                refuelDialogPricePerL = it.fuelPricePerL,
            )
        }
    }

    /**
     * Confirms a refuel event from the dialog (G5).
     *
     * Calls [RecordingEngine.fillToFull] to re-anchor the live fuel estimate AND buffers
     * a [PendingRefuel] in-memory (and in the durable snapshot) for persistence on Finish.
     *
     * @param litres    Volume of fuel added in litres as entered by the rider.
     * @param pricePerL Price per litre at the time of the event.
     */
    private fun doConfirmRefuel(litres: Double, pricePerL: Double) {
        engine.fillToFull()
        val km = engine.snapshot().distanceKm
        rideDebugLogger.log("FUEL", "refuel litres=$litres price=$pricePerL km=$km")
        val pending = PendingRefuel(
            epochMs = timeProvider.nowEpochMs(),
            litres = litres,
            pricePerL = pricePerL,
        )
        pendingRefuels.add(pending)
        _uiState.update { it.copy(metrics = engine.snapshot(), showRefuelDialog = false) }
        viewModelScope.launch {
            sessionStore.save(
                ActiveSessionSnapshot(
                    engineState = engine.exportState(),
                    recordingStartMs = recordingStartMs,
                    bikeId = currentBikeId,
                    paused = _uiState.value.phase == RecordingPhase.Paused,
                    pendingRefuels = pendingRefuels.toList(),
                ),
            )
        }
    }

    // ── Background workers ───────────────────────────────────────────────────

    private fun startTicker() {
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1_000L)
                engine.tick(1L)
                _uiState.update { it.copy(metrics = engine.snapshot()) }
            }
        }
    }

    private fun startLocationUpdates() {
        locationJob = viewModelScope.launch {
            rideLocationCollector.samples.collect { sample ->
                rideDebugLogger.log(
                    "GPS",
                    "lat=${sample.lat} lon=${sample.lng} alt=${sample.altitudeM} spd=${sample.speedMps}",
                )
                val metrics = engine.onLocation(sample)
                val coord = GeoCoord(sample.lat, sample.lng)
                _uiState.update { prev ->
                    prev.copy(metrics = metrics, trackPoints = prev.trackPoints + coord)
                }
                // B20: Persist snapshot on each GPS fix. Launched in a sibling coroutine
                // so the location collector is not blocked by the DataStore write.
                viewModelScope.launch {
                    sessionStore.save(
                        ActiveSessionSnapshot(
                            engineState = engine.exportState(),
                            recordingStartMs = recordingStartMs,
                            bikeId = currentBikeId,
                            paused = false,
                            pendingRefuels = pendingRefuels.toList(),
                        ),
                    )
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns up to [maxCount] evenly-spaced points sampled from [points]. */
    private fun sampleEvenly(points: List<GeoCoord>, maxCount: Int): List<GeoCoord> {
        if (points.size <= maxCount) return points
        val step = points.size.toDouble() / maxCount
        return List(maxCount) { i -> points[(i * step).toInt()] }
    }
}

/** Aggregated per-bike and per-settings values resolved in the settings/bike combine block. */
private data class BikeSnapshot(
    val consumption: Double,
    val tankCapacity: Double?,
    val fuelPricePerL: Double?,
    val currency: String,
)

/** Intermediate holder produced by the 3-way combine before refuels are fetched (H4). */
private data class BikeData(
    val bikeId: String?,
    val bike: Bike?,
    val bikeSummaries: List<RouteSummaryModel>,
    val currency: String,
)
