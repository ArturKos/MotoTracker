package com.mototracker.ui.screens.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.R
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.format.CoordFormat
import com.mototracker.core.resource.StringResolver
import com.mototracker.core.time.TimeProvider
import com.mototracker.data.diagnostics.RideDebugLogger
import com.mototracker.data.location.ReverseGeocoder
import com.mototracker.data.location.RideLocationCollector
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.network.WeatherClient
import com.mototracker.domain.fuel.AutoUpdateBikeConsumptionUseCase
import com.mototracker.domain.fuel.FuelAdjustmentMode
import com.mototracker.domain.fuel.FuelConsumptionCalculator
import com.mototracker.data.recording.RecordingSessionStore
import com.mototracker.data.recording.ResumeRouteBus
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.FuelAdjustmentRepository
import com.mototracker.data.repository.RefuelRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.sensor.HeadingSensorSource
import com.mototracker.data.sensor.LeanSensorSource
import com.mototracker.data.battery.BatteryOptimizationIntents
import com.mototracker.data.bluetooth.InRangeFilter
import com.mototracker.data.repository.RiderRepository
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.data.settings.SettingsStore
import com.mototracker.domain.battery.BatteryOptimizationChecker
import com.mototracker.domain.battery.BatteryOptimizationGate
import com.mototracker.domain.naming.PartOfDay
import com.mototracker.domain.naming.RouteNameComposer
import com.mototracker.domain.recording.RecordingEngine
import com.mototracker.domain.recording.RouteResumeSeed
import com.mototracker.ui.map.GeoCoord
import com.mototracker.ui.map.TrackGeometry
import com.mototracker.ui.state.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
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
 * @param weatherClient                     Fetches current weather once at ride start when online (K5).
 * @param batteryOptimizationChecker        Checks whether the app is exempt from battery optimization (O1).
 * @param settingsStore                     Write access to persisted settings, used to record prompt-dismissed (O1).
 * @param fuelAdjustmentRepository          Persistence for per-bike fuel-level correction events (R1).
 * @param riderRepository                   Domain repository for BLE-discovered riders; used to
 *                                          populate the in-range roster sheet (X2).
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
    private val weatherClient: WeatherClient,
    private val batteryOptimizationChecker: BatteryOptimizationChecker,
    private val settingsStore: SettingsStore,
    private val fuelAdjustmentRepository: FuelAdjustmentRepository,
    private val riderRepository: RiderRepository,
    rideHolder: ActiveRideHolder,
) : ViewModel() {

    /**
     * Process-lived ride state. Reused when this ViewModel is recreated mid-ride (Activity
     * destroyed, app swiped from recents) so recording continues instead of silently stopping.
     */
    private val ride: ActiveRide = rideHolder.ride ?: ActiveRide(
        rideLocationCollector = rideLocationCollector,
        leanSensorSource = leanSensorSource,
        sessionStore = sessionStore,
        timeProvider = timeProvider,
        rideDebugLogger = rideDebugLogger,
        settingsSource = settingsSource,
        networkMonitor = networkMonitor,
        weatherClient = weatherClient,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    ).also { rideHolder.ride = it }

    private val engine: RecordingEngine get() = ride.engine

    private val _uiState = MutableStateFlow(RecordingUiState())
    /** Live UI state for the Recording screen. */
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<RecordingEffect>(extraBufferCapacity = 2)
    /** One-shot side-effects (navigate-away, show toast). */
    val effects: SharedFlow<RecordingEffect> = _effects.asSharedFlow()

    /** Per-session fuel consumption resolved from the current bike; defaults to 5.0 L/100km. */
    private var currentBikeConsumption: Double = 5.0

    /** Tank capacity of the current bike in litres; null when not configured (E4: fill-to-full inert). */
    private var currentBikeTankCapacity: Double? = null

    /**
     * Cached value of [AppSettings.batteryPromptDismissed] kept in sync from the settings stream
     * so [doStart] can read it synchronously without suspending (O1).
     */
    private var batteryPromptDismissed: Boolean = false

    /** Copies the ride-owned fields into [_uiState] synchronously. */
    private fun mirrorRide(r: ActiveRide.State = ride.state.value) {
        _uiState.update {
            it.copy(
                phase = r.phase,
                metrics = r.metrics,
                trackPoints = r.trackPoints,
                activeRouteId = r.activeRouteId,
            )
        }
    }

    init {
        // Attach to the (possibly already running) ride: show its state immediately, then
        // follow ticker / GPS / lean updates produced by the ride's own workers.
        mirrorRide()
        viewModelScope.launch { ride.state.collect { mirrorRide(it) } }

        // Keep gpsOnRoad in sync with the user's GPS-correction setting;
        // also forward the active units preference to the Android Auto bridge.
        viewModelScope.launch {
            settingsSource.settings.collect { s ->
                ride.currentBikeId = s.currentBikeId
                batteryPromptDismissed = s.batteryPromptDismissed
                _uiState.update {
                    it.copy(
                        gpsOnRoad = s.gpsCorrect,
                        keepScreenOn = s.keepScreenOn,
                        coordFormat = CoordFormat.fromKey(s.coordFormat),
                        smsShareEnabled = s.smsShareEnabled,
                    )
                }
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
            ride.updateFuelConfig(bs.consumption, bs.tankCapacity)
            _uiState.update {
                it.copy(
                    metrics = ride.state.value.metrics,
                    fuelPricePerL = bs.fuelPricePerL,
                    currency = bs.currency,
                )
            }
        }.launchIn(viewModelScope)

        // B20: Detect an unfinished session from a previous process lifetime.
        viewModelScope.launch {
            val existing = sessionStore.snapshot.first()
            // A ride already running in this process also has a snapshot — that is not an
            // interrupted session, so don't offer to resume it.
            if (existing != null && ride.state.value.phase == RecordingPhase.Idle) {
                _uiState.update { it.copy(resumableSession = existing) }
            }
        }

        // J5: React to "continue an existing route" requests from the Route Detail bus.
        viewModelScope.launch {
            resumeRouteBus.requests.collect { routeId -> doResumeRoute(routeId) }
        }

        // F2: Always-on lean collector — liveLeanDeg updates regardless of phase so the
        // rider can see the tilt bar before starting a ride. The engine is fed by [ActiveRide]'s
        // own lean worker so lean keeps recording while this ViewModel is gone.
        viewModelScope.launch {
            leanSensorSource.leanAngles.collect { deg ->
                _uiState.update { it.copy(liveLeanDeg = deg) }
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

        // L2: Always-on GNSS satellite count — gpsSatCount updates regardless of phase so
        // the GPS chip shows real satellite count whenever the GNSS subsystem is active.
        viewModelScope.launch {
            rideLocationCollector.satelliteCounts.collect { c ->
                _uiState.update { it.copy(gpsSatCount = c.usedInFix) }
            }
        }

        // K7: Start GNSS immediately so the sat-count chip is live in Idle before the
        // rider taps Start (location permission not required; degrades to count=0 if denied).
        rideLocationCollector.startGnss()

        // M2: Always-on GPS sample collector — liveSpeedKmh and liveAltitudeM mirror the
        // latest raw GPS sample regardless of recording phase so the Idle cockpit shows a
        // real-time speed/altitude readout before the ride starts.  The engine is NOT
        // advanced here; startLocationUpdates() owns the engine-feeding path exclusively.
        // During Recording both collectors subscribe concurrently to the same SharedFlow —
        // this is supported; the live fields simply reflect the latest raw sample.
        viewModelScope.launch {
            rideLocationCollector.samples.collect { sample ->
                _uiState.update {
                    it.copy(
                        liveSpeedKmh = sample.speedMps * 3.6,
                        liveAltitudeM = sample.altitudeM,
                        liveLat = sample.lat,
                        liveLng = sample.lng,
                    )
                }
            }
        }

        // M2: Start the GPS subscription alongside GNSS so speed/altitude are live in Idle.
        // start() is idempotent; doStart()'s defensive start() and the service's start() are
        // both unaffected.
        rideLocationCollector.start()

        // X2: Maintain the in-range rider list by combining the full rider DB with a ~2 s tick.
        // The tick keeps the list fresh even when no new BLE sightings arrive (so riders that
        // drop out of the 30-second window are removed automatically).
        combine(
            riderRepository.observeAll(),
            // flowOn(Default) keeps the 2-second delay off the test scheduler so
            // advanceUntilIdle() in unit tests doesn't spin forever on this infinite flow.
            tickerFlow(intervalMs = 2_000L).flowOn(Dispatchers.Default),
        ) { riders, _ ->
            InRangeFilter.filter(riders, System.currentTimeMillis())
        }.onEach { inRange ->
            _uiState.update { it.copy(inRangeRiders = inRange) }
        }.launchIn(viewModelScope)
    }

    /**
     * Stops the GPS and GNSS streams when the Recording screen is destroyed and no ride
     * is in progress.
     *
     * Both [RideLocationCollector.stop] (GPS subscription) and [RideLocationCollector.stopGnss]
     * (GNSS satellite listener) are called when phase is [RecordingPhase.Idle].  If a ride is
     * active or paused the service-owned streams must not be interrupted, so this is a no-op
     * then (M2 + K7).
     */
    override fun onCleared() {
        super.onCleared()
        val phase = ride.state.value.phase
        if (phase != RecordingPhase.Recording && phase != RecordingPhase.Paused) {
            rideLocationCollector.stop()
            rideLocationCollector.stopGnss()
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
            is RecordingEvent.BatteryOptConfirm -> _uiState.update { it.copy(showBatteryOptPrompt = false) }
            is RecordingEvent.BatteryOptDismiss -> doBatteryOptDismiss()
            is RecordingEvent.ShowFuelCorrectionDialog -> doShowFuelCorrectionDialog()
            is RecordingEvent.ConfirmFuelCorrection -> doConfirmFuelCorrection(event.mode, event.value)
            is RecordingEvent.DismissFuelCorrectionDialog -> _uiState.update { it.copy(showFuelCorrectionDialog = false) }
            is RecordingEvent.ShowGroupRoster -> _uiState.update { it.copy(showGroupRosterSheet = true) }
            is RecordingEvent.DismissGroupRoster -> _uiState.update { it.copy(showGroupRosterSheet = false) }
            is RecordingEvent.ToggleGroup -> toggleGroup(event.shortId, event.inGroup)
        }
    }

    /**
     * Toggles the group-membership flag for [shortId] (X2).
     *
     * Delegates to [RiderRepository.setInGroup] on the IO dispatcher.
     *
     * @param shortId BLE device identifier.
     * @param inGroup New group-membership state.
     */
    fun toggleGroup(shortId: String, inGroup: Boolean) {
        viewModelScope.launch { riderRepository.setInGroup(shortId, inGroup) }
    }

    // ── State machine ────────────────────────────────────────────────────────

    private fun doStart() {
        if (BatteryOptimizationGate.shouldPrompt(
                isExempt = batteryOptimizationChecker.isIgnoringBatteryOptimizations(),
                promptDismissed = batteryPromptDismissed,
            )
        ) {
            _uiState.update { it.copy(showBatteryOptPrompt = true) }
            return
        }
        doStartRecording()
    }

    private fun doStartRecording() {
        ride.startNew(fuelLper100km = currentBikeConsumption, tankCapacityL = currentBikeTankCapacity)
        _uiState.update { it.copy(resumableSession = null) }
        mirrorRide()
    }

    private fun doBatteryOptDismiss() {
        _uiState.update { it.copy(showBatteryOptPrompt = false) }
        viewModelScope.launch { settingsStore.setBatteryPromptDismissed(true) }
    }

    /**
     * Opens the fuel-correction dialog pre-filled with the current engine estimate (R1).
     *
     * Only meaningful when [tankCapacityL] is configured; shows the dialog unconditionally
     * so the rider can always access it during Recording or Paused phases.
     */
    private fun doShowFuelCorrectionDialog() {
        val currentRemaining = engine.snapshot().remainingFuelL ?: 0.0
        _uiState.update {
            it.copy(
                showFuelCorrectionDialog = true,
                fuelCorrectionCurrentRemaining = currentRemaining,
            )
        }
    }

    /**
     * Applies a fuel-level correction from the dialog (R1).
     *
     * Calls [RecordingEngine.applyFuelCorrection] to re-anchor the live fuel estimate,
     * persists a [com.mototracker.domain.fuel.FuelAdjustmentEvent] and updates [_uiState].
     *
     * @param mode  Whether the correction sets an absolute level or applies a signed delta.
     * @param value Correction value in litres.
     */
    private fun doConfirmFuelCorrection(mode: FuelAdjustmentMode, value: Double) {
        ride.applyFuelCorrection(mode, value)
        val bikeId = ride.currentBikeId
        val routeId = ride.pendingRouteId
        mirrorRide()
        _uiState.update { it.copy(showFuelCorrectionDialog = false) }
        viewModelScope.launch {
            if (bikeId != null) {
                runCatching {
                    fuelAdjustmentRepository.addAdjustment(
                        bikeId = bikeId,
                        routeId = routeId,
                        epochMs = timeProvider.nowEpochMs(),
                        mode = mode,
                        litres = value,
                    )
                }
            }
        }
    }

    private fun doPause() {
        ride.pause()
        mirrorRide()
    }

    private fun doResume() {
        ride.resume()
        mirrorRide()
    }

    private fun doFinish() {
        rideDebugLogger.log("LIFECYCLE", "finish")
        ride.stopWorkers()

        viewModelScope.launch {
            val settings = settingsSource.settings.first()
            val isOnline = networkMonitor.isOnline.first()
            val offline = settings.noInternet || !isOnline

            val result = engine.buildRoutePayload()

            // J5: When continuing an existing route, keep its original name and start time.
            val isResume = ride.resumingExistingRoute

            val routeName: String
            val dateEpochMs: Long
            if (isResume) {
                routeName = ride.existingRouteName
                dateEpochMs = ride.existingRouteDateEpochMs
            } else {
                // Compose a sensible default name from part-of-day + optional reverse geocoding.
                val startMs = if (ride.recordingStartMs > 0L) ride.recordingStartMs else timeProvider.nowEpochMs()
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

            val routeId = ride.pendingRouteId ?: UUID.randomUUID().toString()
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
                wxJson = if (isResume) null else ride.pendingWxJson,
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
            val refuelsToSave = ride.pendingRefuels
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

            ride.markIdle()
            mirrorRide()
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
        ride.restoreSnapshot(snap)
        _uiState.update { it.copy(resumableSession = null) }
        mirrorRide()
    }

    /** Clears a detected resumable session without restoring it (B20). */
    private fun doDiscardSession() {
        ride.discardPendingRoute()
        _uiState.update { it.copy(resumableSession = null) }
        mirrorRide()
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
        ride.restoreRoute(
            routeId = routeId,
            routeName = route.name,
            routeDateEpochMs = route.dateEpochMs,
            seed = seed,
            fuelLper100km = currentBikeConsumption,
            tankCapacityL = currentBikeTankCapacity,
        )
        _uiState.update { it.copy(resumableSession = null) }
        mirrorRide()
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
     * a [com.mototracker.data.recording.PendingRefuel] in-memory (and in the durable snapshot) for persistence on Finish.
     *
     * @param litres    Volume of fuel added in litres as entered by the rider.
     * @param pricePerL Price per litre at the time of the event.
     */
    private fun doConfirmRefuel(litres: Double, pricePerL: Double) {
        ride.refuel(litres, pricePerL)
        mirrorRide()
        _uiState.update { it.copy(showRefuelDialog = false) }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns up to [maxCount] evenly-spaced points sampled from [points]. */
    private fun sampleEvenly(points: List<GeoCoord>, maxCount: Int): List<GeoCoord> {
        if (points.size <= maxCount) return points
        val step = points.size.toDouble() / maxCount
        return List(maxCount) { i -> points[(i * step).toInt()] }
    }

    /** Produces a cold flow that emits [Unit] at [intervalMs] intervals, starting immediately. */
    private fun tickerFlow(intervalMs: Long) = flow {
        while (true) {
            emit(Unit)
            delay(intervalMs)
        }
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
