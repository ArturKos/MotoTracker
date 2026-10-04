package com.mototracker.ui.screens.record

import com.mototracker.data.diagnostics.RideDebugLogger
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
 * [com.mototracker.service.RecordingService] keeps the process alive and the ride must keep
 * recording. A freshly created ViewModel picks the same [ActiveRide] up from here.
 */
@Singleton
class ActiveRideHolder @Inject constructor() {
    /** The ride for this process; created lazily by the first [RecordingViewModel]. */
    var ride: ActiveRide? = null
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
 * @param scope Process-lived scope (main dispatcher) that owns the background workers.
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
    private val scope: CoroutineScope,
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
    private var tickerJob: Job? = null
    private var locationJob: Job? = null
    private var leanJob: Job? = null

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

    /** Forgets the pre-assigned route id when a resumable session is discarded (B20). */
    fun discardPendingRoute() {
        pendingRouteId = null
        _state.update { it.copy(activeRouteId = null) }
    }

    // ── Fuel ─────────────────────────────────────────────────────────────────

    /** Feeds a newly resolved fuel model into the engine without touching session anchors (I2). */
    fun updateFuelConfig(fuelLper100km: Double, tankCapacityL: Double?) {
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
}
