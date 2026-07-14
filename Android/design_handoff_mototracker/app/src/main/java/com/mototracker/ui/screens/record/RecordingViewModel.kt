package com.mototracker.ui.screens.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.R
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.resource.StringResolver
import com.mototracker.core.time.TimeProvider
import com.mototracker.data.diagnostics.RideDebugLogger
import com.mototracker.data.location.LocationClient
import com.mototracker.data.location.ReverseGeocoder
import com.mototracker.data.model.Route
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.recording.ActiveSessionSnapshot
import com.mototracker.data.recording.RecordingSessionStore
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.sensor.HeadingSensorSource
import com.mototracker.data.sensor.LeanSensorSource
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.domain.naming.PartOfDay
import com.mototracker.domain.naming.RouteNameComposer
import com.mototracker.domain.recording.RecordingEngine
import com.mototracker.ui.map.GeoCoord
import com.mototracker.ui.map.TrackGeometry
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
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
 * - Location updates from [LocationClient] feeding [RecordingEngine.onLocation].
 * - Lean-sensor readings from [LeanSensorSource] feeding [RecordingEngine.onLean].
 * - On Finish: composes a sensible default route name (using [RouteNameComposer] +
 *   [ReverseGeocoder] when online), persists the route via [RouteRepository], and
 *   queues it for upload via [SyncRepository]; emits a [RecordingEffect.Saved] with
 *   the online/offline flag.
 * - On startup: checks [RecordingSessionStore] for an unfinished session and, if found,
 *   exposes it as [RecordingUiState.resumableSession] so the UI can prompt the user
 *   to resume or discard (B20).
 *
 * All collaborators are injectable interfaces so the ViewModel is fully unit-testable
 * with fakes (no Android runtime needed).
 *
 * @param locationClient      GPS location updates.
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
 */
@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val locationClient: LocationClient,
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
        // Track the current bike's fuel properties so doStart() passes the right values to the engine.
        // Also resolves fuelPricePerL and currency for the live fuel-cost readout (G2).
        combine(settingsSource.settings, bikeRepository.observeAll()) { s, bikes ->
            val bike = bikes.find { it.id == s.currentBikeId }
            BikeSnapshot(
                consumption = bike?.consumptionLper100km ?: 5.0,
                tankCapacity = bike?.tankCapacityL,
                fuelPricePerL = bike?.fuelPricePerL,
                currency = s.currency,
            )
        }.onEach { bs ->
            currentBikeConsumption = bs.consumption
            currentBikeTankCapacity = bs.tankCapacity
            _uiState.update { it.copy(fuelPricePerL = bs.fuelPricePerL, currency = bs.currency) }
        }.launchIn(viewModelScope)

        // B20: Detect an unfinished session from a previous process lifetime.
        viewModelScope.launch {
            val existing = sessionStore.snapshot.first()
            if (existing != null) {
                _uiState.update { it.copy(resumableSession = existing) }
            }
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
            is RecordingEvent.FillToFull -> doFillToFull()
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
            val routeName = if (!offline) {
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

            val routeId = pendingRouteId ?: UUID.randomUUID().toString()
            pendingRouteId = null
            val route = Route(
                id = routeId,
                name = routeName,
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
                maxLeanLeftDeg = result.metrics.maxLeanLeftDeg,
                maxLeanRightDeg = result.metrics.maxLeanRightDeg,
            )

            routeRepository.save(route)
            syncRepository.enqueue(route.id)

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
     * Re-anchors the fill point to the current odometer, resetting the fuel-remaining estimate (E4).
     *
     * Only meaningful when the current bike has a tank capacity configured; the engine method
     * is safe to call regardless (it simply moves the fill anchor). The session snapshot is
     * persisted so that a process-death resume preserves the fill event.
     */
    private fun doFillToFull() {
        engine.fillToFull()
        val km = engine.snapshot().distanceKm
        rideDebugLogger.log("FUEL", "fill-to-full km=$km")
        _uiState.update { it.copy(metrics = engine.snapshot()) }
        viewModelScope.launch {
            sessionStore.save(
                ActiveSessionSnapshot(
                    engineState = engine.exportState(),
                    recordingStartMs = recordingStartMs,
                    bikeId = currentBikeId,
                    paused = _uiState.value.phase == RecordingPhase.Paused,
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
            try {
                locationClient.locationUpdates().collect { sample ->
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
                            ),
                        )
                    }
                }
            } catch (_: SecurityException) {
                rideDebugLogger.log("ERROR", "SecurityException — location permission revoked mid-session")
                // Permission was revoked mid-session; recording continues on ticker/lean only.
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
