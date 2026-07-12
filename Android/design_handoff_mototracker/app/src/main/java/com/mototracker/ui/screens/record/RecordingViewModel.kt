package com.mototracker.ui.screens.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.time.TimeProvider
import com.mototracker.data.diagnostics.RideDebugLogger
import com.mototracker.data.location.LocationClient
import com.mototracker.data.model.Route
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.sensor.LeanSensorSource
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.domain.recording.RecordingEngine
import com.mototracker.ui.state.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Recording screen.
 *
 * Owns the recording state machine (Idle → Recording → Paused → Idle) and
 * co-ordinates:
 * - A 1-second ticker that advances elapsed time via [RecordingEngine.tick].
 * - Location updates from [LocationClient] feeding [RecordingEngine.onLocation].
 * - Lean-sensor readings from [LeanSensorSource] feeding [RecordingEngine.onLean].
 * - On Finish: persists the route via [RouteRepository] and queues it for upload
 *   via [SyncRepository]; emits a [RecordingEffect.Saved] with the online/offline flag.
 *
 * All collaborators are injectable interfaces so the ViewModel is fully unit-testable
 * with fakes (no Android runtime needed).
 *
 * @param locationClient  GPS location updates.
 * @param leanSensorSource  Gravity-sensor lean angles.
 * @param routeRepository   Persistence for completed routes.
 * @param syncRepository    Outbound sync queue.
 * @param settingsSource    Read-only app settings stream.
 * @param networkMonitor    Online/offline connectivity.
 * @param timeProvider      Wall-clock source (injectable for tests).
 * @param carBridge         App-scoped bridge that mirrors recording state to the Android Auto screen.
 * @param rideDebugLogger   Diagnostic logger; writes GPS/lean/lifecycle events to a per-ride log
 *                          file when diagnostics are enabled (no-op otherwise).
 *                          Note: weather logging is not wired here — there is no weather seam
 *                          in this ViewModel; weather events are logged at the data layer.
 */
@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val locationClient: LocationClient,
    private val leanSensorSource: LeanSensorSource,
    private val routeRepository: RouteRepository,
    private val syncRepository: SyncRepository,
    private val settingsSource: AppSettingsSource,
    private val networkMonitor: NetworkMonitor,
    private val timeProvider: TimeProvider,
    private val carBridge: CarRecordingBridge,
    private val rideDebugLogger: RideDebugLogger,
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
    private var leanJob: Job? = null

    init {
        // Keep gpsOnRoad in sync with the user's GPS-correction setting;
        // also forward the active units preference to the Android Auto bridge.
        viewModelScope.launch {
            settingsSource.settings.collect { s ->
                _uiState.update { it.copy(gpsOnRoad = s.gpsCorrect) }
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
        }
    }

    // ── State machine ────────────────────────────────────────────────────────

    private fun doStart() {
        engine.reset()
        rideDebugLogger.beginRide()
        _uiState.update { it.copy(phase = RecordingPhase.Recording) }
        startTicker()
        startLocationUpdates()
        startLeanUpdates()
    }

    private fun doPause() {
        _uiState.update { it.copy(phase = RecordingPhase.Paused) }
        rideDebugLogger.log("LIFECYCLE", "pause")
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun doResume() {
        _uiState.update { it.copy(phase = RecordingPhase.Recording) }
        rideDebugLogger.log("LIFECYCLE", "resume")
        startTicker()
    }

    private fun doFinish() {
        rideDebugLogger.log("LIFECYCLE", "finish")
        tickerJob?.cancel()
        locationJob?.cancel()
        leanJob?.cancel()
        tickerJob = null
        locationJob = null
        leanJob = null

        viewModelScope.launch {
            val settings = settingsSource.settings.first()
            val isOnline = networkMonitor.isOnline.first()
            val offline = settings.offline || settings.offlineOnly || !isOnline

            val result = engine.buildRoutePayload()
            val route = Route(
                id = UUID.randomUUID().toString(),
                name = "",
                dateEpochMs = timeProvider.nowEpochMs(),
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
            )

            routeRepository.save(route)
            syncRepository.enqueue(route.id)

            _uiState.update { it.copy(phase = RecordingPhase.Idle) }
            _effects.emit(RecordingEffect.Saved(offline = offline))
            _effects.emit(RecordingEffect.NavigateToDetail(route.id))
            rideDebugLogger.endRide()
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
            try {
                locationClient.locationUpdates().collect { sample ->
                    rideDebugLogger.log(
                        "GPS",
                        "lat=${sample.lat} lon=${sample.lng} alt=${sample.altitudeM} spd=${sample.speedMps}",
                    )
                    val metrics = engine.onLocation(sample)
                    _uiState.update { it.copy(metrics = metrics) }
                }
            } catch (_: SecurityException) {
                rideDebugLogger.log("ERROR", "SecurityException — location permission revoked mid-session")
                // Permission was revoked mid-session; recording continues on ticker/lean only.
            }
        }
    }

    private fun startLeanUpdates() {
        leanJob = viewModelScope.launch {
            leanSensorSource.leanAngles.collect { deg ->
                rideDebugLogger.log("LEAN", "angle=$deg")
                engine.onLean(deg)
                _uiState.update { it.copy(metrics = engine.snapshot()) }
            }
        }
    }
}
