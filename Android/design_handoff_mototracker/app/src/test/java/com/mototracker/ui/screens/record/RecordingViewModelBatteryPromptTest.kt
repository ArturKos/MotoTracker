package com.mototracker.ui.screens.record

import com.mototracker.R
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.resource.StringResolver
import com.mototracker.core.time.TimeProvider
import com.mototracker.data.diagnostics.RideDebugLogger
import com.mototracker.data.location.GnssSatelliteCount
import com.mototracker.data.location.GnssStatusClient
import com.mototracker.data.location.LocationClient
import com.mototracker.data.location.ReverseGeocoder
import com.mototracker.data.location.RideLocationCollector
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.model.mapper.toRouteSummaryModel
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.network.WeatherClient
import com.mototracker.data.network.WeatherSnapshot
import com.mototracker.data.recording.ActiveSessionSnapshot
import com.mototracker.data.recording.RecordingSessionStore
import com.mototracker.data.recording.ResumeRouteBus
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RefuelRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.sensor.HeadingSensorSource
import com.mototracker.data.sensor.LeanSensorSource
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.data.settings.SettingsStore
import com.mototracker.domain.battery.BatteryOptimizationChecker
import com.mototracker.domain.fuel.AutoUpdateBikeConsumptionUseCase
import com.mototracker.domain.fuel.RefuelEvent
import com.mototracker.domain.recording.LocationSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class BPFakeLocationCollector : RideLocationCollector(
    locationClient = object : LocationClient {
        override fun locationUpdates(intervalMs: Long): Flow<LocationSample> = flow {}
    },
    gnssStatusClient = object : GnssStatusClient {
        override fun satelliteCounts(): Flow<GnssSatelliteCount> = flow {}
    },
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
) {
    private val _flow = MutableSharedFlow<LocationSample>(
        replay = 0, extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _satFlow = MutableSharedFlow<GnssSatelliteCount>(
        replay = 1, extraBufferCapacity = 0, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val samples: SharedFlow<LocationSample> = _flow.asSharedFlow()
    override val satelliteCounts: SharedFlow<GnssSatelliteCount> = _satFlow.asSharedFlow()
    override fun start(intervalMs: Long) {}
    override fun stop() {}
    override fun startGnss() {}
    override fun stopGnss() {}
}

private class BPFakeRouteRepository : RouteRepository {
    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    override suspend fun save(route: Route) { _routes.value = _routes.value + route }
    override fun observeSummaries(): Flow<List<RouteSummaryModel>> =
        _routes.map { list -> list.map { it.toRouteSummaryModel() } }
    override suspend fun getById(id: String): Route? = _routes.value.find { it.id == id }
    override fun observeById(id: String): Flow<Route?> = MutableStateFlow(null)
    override suspend fun clearCorrectedTrace(id: String) {}
    override suspend fun deleteAll() {}
    override suspend fun rename(id: String, name: String) {}
    override suspend fun setBike(routeId: String, bikeId: String?) {}
}

private class BPFakeBikeRepository : BikeRepository {
    override fun observeAll(): Flow<List<Bike>> = MutableStateFlow(emptyList())
    override suspend fun addBike(bike: Bike) {}
    override suspend fun deleteAll() {}
}

private class BPFakeRefuelRepository : RefuelRepository {
    override suspend fun addRefuel(routeId: String, epochMs: Long, litres: Double, pricePerL: Double) {}
    override fun observeRefuels(routeId: String): Flow<List<RefuelEvent>> = MutableStateFlow(emptyList())
    override suspend fun deleteRefuel(id: Long) {}
    override fun observeAllForBike(bikeId: String): Flow<List<RefuelEvent>> = MutableStateFlow(emptyList())
}

private class BPFakeSyncRepository : SyncRepository {
    override val pendingCount: Flow<Int> = MutableStateFlow(0)
    override suspend fun enqueue(routeId: String) {}
    override suspend fun syncNow(): Int = 0
    override fun start(scope: CoroutineScope) = Unit
}

private class BPFakeSessionStore : RecordingSessionStore {
    override val snapshot: Flow<ActiveSessionSnapshot?> = MutableStateFlow(null)
    override suspend fun save(s: ActiveSessionSnapshot) {}
    override suspend fun clear() {}
}

private class BPFakeSettingsSource(settings: AppSettings) : AppSettingsSource {
    override val settings: Flow<AppSettings> = MutableStateFlow(settings)
}

/**
 * Tracking [SettingsStore] that records calls to [setBatteryPromptDismissed].
 */
class TrackingSettingsStore(
    initialSettings: AppSettings = AppSettings(),
) : SettingsStore {
    private val _settings = MutableStateFlow(initialSettings)
    override val settings: Flow<AppSettings> = _settings

    var batteryPromptDismissedCalls = 0
    var lastDismissedValue: Boolean? = null

    override suspend fun setBatteryPromptDismissed(value: Boolean) {
        batteryPromptDismissedCalls++
        lastDismissedValue = value
        _settings.value = _settings.value.copy(batteryPromptDismissed = value)
    }

    override suspend fun setNoInternet(value: Boolean) {}
    override suspend fun setSyncEnabled(value: Boolean) {}
    override suspend fun setGpsCorrect(value: Boolean) {}
    override suspend fun setCurrentBikeId(bikeId: String?) {}
    override suspend fun setUnits(units: String) {}
    override suspend fun setTheme(theme: String) {}
    override suspend fun setAccent(accent: String) {}
    override suspend fun setLang(lang: String) {}
    override suspend fun setAutoPause(value: Boolean) {}
    override suspend fun setKeepScreenOn(value: Boolean) {}
    override suspend fun setAndroidAutoEnabled(value: Boolean) {}
    override suspend fun setBcName(name: String) {}
    override suspend fun setBcPhone(phone: String) {}
    override suspend fun setBcOrigin(origin: String) {}
    override suspend fun setBcSocial(social: String) {}
    override suspend fun setDebugLoggingEnabled(value: Boolean) {}
    override suspend fun setServerAddress(address: String) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModelBatteryPromptTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(
        checker: BatteryOptimizationChecker,
        settingsStore: TrackingSettingsStore,
        settings: AppSettings = AppSettings(),
    ): RecordingViewModel {
        val routeRepo = BPFakeRouteRepository()
        val bikeRepo = BPFakeBikeRepository()
        val refuelRepo = BPFakeRefuelRepository()
        return RecordingViewModel(
            rideLocationCollector = BPFakeLocationCollector(),
            leanSensorSource = object : LeanSensorSource { override val leanAngles: Flow<Double> = flow {} },
            headingSensorSource = object : HeadingSensorSource { override val headings: Flow<Float> = flow {} },
            routeRepository = routeRepo,
            syncRepository = BPFakeSyncRepository(),
            settingsSource = BPFakeSettingsSource(settings),
            bikeRepository = bikeRepo,
            networkMonitor = object : NetworkMonitor { override val isOnline: Flow<Boolean> = MutableStateFlow(true) },
            timeProvider = object : TimeProvider { override fun nowEpochMs(): Long = 1_000_000L },
            carBridge = CarRecordingBridge(),
            rideDebugLogger = object : RideDebugLogger {
                override fun beginRide() {}
                override fun endRide() {}
                override fun log(tag: String, message: String) {}
            },
            reverseGeocoder = object : ReverseGeocoder {
                override suspend fun areaName(lat: Double, lng: Double): String? = null
            },
            stringResolver = object : StringResolver {
                override fun getString(resId: Int): String = when (resId) {
                    R.string.route_name_ride_morning   -> "morning ride"
                    R.string.route_name_ride_afternoon -> "afternoon ride"
                    R.string.route_name_ride_evening   -> "evening ride"
                    R.string.route_name_ride_night     -> "night ride"
                    R.string.route_name_with_area      -> "%1\$s – %2\$s"
                    else -> "stub"
                }
                override fun getString(resId: Int, vararg args: Any): String = getString(resId)
            },
            sessionStore = BPFakeSessionStore(),
            refuelRepository = refuelRepo,
            resumeRouteBus = object : ResumeRouteBus {
                override val requests: Flow<String> = flow {}
                override suspend fun request(routeId: String) {}
            },
            autoUpdateBikeConsumptionUseCase = AutoUpdateBikeConsumptionUseCase(
                bikeRepository = bikeRepo,
                routeRepository = routeRepo,
                refuelRepository = refuelRepo,
            ),
            weatherClient = object : WeatherClient {
                override suspend fun fetch(lat: Double, lon: Double): Result<WeatherSnapshot> =
                    Result.success(WeatherSnapshot(tempC = 20, humidity = 60, rain = false))
            },
            batteryOptimizationChecker = checker,
            settingsStore = settingsStore,
            fuelAdjustmentRepository = object : com.mototracker.data.repository.FuelAdjustmentRepository {
                override suspend fun addAdjustment(bikeId: String, routeId: String?, epochMs: Long, mode: com.mototracker.domain.fuel.FuelAdjustmentMode, litres: Double) {}
                override fun observeForBike(bikeId: String): kotlinx.coroutines.flow.Flow<List<com.mototracker.domain.fuel.FuelAdjustmentEvent>> = MutableStateFlow(emptyList())
                override suspend fun latestForBike(bikeId: String): com.mototracker.domain.fuel.FuelAdjustmentEvent? = null
            },
        )
    }

    @Test
    fun `Start shows prompt when not exempt and not dismissed`() = runTest(testDispatcher) {
        val store = TrackingSettingsStore(AppSettings(batteryPromptDismissed = false))
        val vm = buildVm(
            checker = object : BatteryOptimizationChecker {
                override fun isIgnoringBatteryOptimizations() = false
            },
            settingsStore = store,
            settings = AppSettings(batteryPromptDismissed = false),
        )

        vm.onEvent(RecordingEvent.Start)
        advanceUntilIdle()

        assertTrue("prompt should be shown", vm.uiState.value.showBatteryOptPrompt)
        assertFalse("phase should remain Idle", vm.uiState.value.phase == RecordingPhase.Recording)
    }

    @Test
    fun `Start does NOT show prompt when already exempt`() = runTest(testDispatcher) {
        val store = TrackingSettingsStore(AppSettings(batteryPromptDismissed = false))
        val vm = buildVm(
            checker = object : BatteryOptimizationChecker {
                override fun isIgnoringBatteryOptimizations() = true
            },
            settingsStore = store,
            settings = AppSettings(batteryPromptDismissed = false),
        )

        vm.onEvent(RecordingEvent.Start)
        // advanceUntilIdle() would hang with the infinite ticker; advance just enough for
        // zero-delay coroutines (doStart + doStartRecording) to complete.
        advanceTimeBy(200L)

        assertFalse("prompt should NOT be shown when exempt", vm.uiState.value.showBatteryOptPrompt)

        vm.onEvent(RecordingEvent.Pause) // stop ticker before runTest drains scheduler
    }

    @Test
    fun `BatteryOptDismiss calls setBatteryPromptDismissed(true) and clears prompt`() =
        runTest(testDispatcher) {
            val store = TrackingSettingsStore(AppSettings(batteryPromptDismissed = false))
            val vm = buildVm(
                checker = object : BatteryOptimizationChecker {
                    override fun isIgnoringBatteryOptimizations() = false
                },
                settingsStore = store,
                settings = AppSettings(batteryPromptDismissed = false),
            )

            vm.onEvent(RecordingEvent.Start)
            advanceUntilIdle()
            assertTrue("precondition: prompt shown", vm.uiState.value.showBatteryOptPrompt)

            vm.onEvent(RecordingEvent.BatteryOptDismiss)
            advanceUntilIdle()

            assertFalse("prompt should be cleared after dismiss", vm.uiState.value.showBatteryOptPrompt)
            assertTrue("setBatteryPromptDismissed should have been called once",
                store.batteryPromptDismissedCalls >= 1)
            assertTrue("value persisted should be true", store.lastDismissedValue == true)
        }

    @Test
    fun `BatteryOptConfirm clears prompt without persisting dismissed flag`() =
        runTest(testDispatcher) {
            val store = TrackingSettingsStore(AppSettings(batteryPromptDismissed = false))
            val vm = buildVm(
                checker = object : BatteryOptimizationChecker {
                    override fun isIgnoringBatteryOptimizations() = false
                },
                settingsStore = store,
                settings = AppSettings(batteryPromptDismissed = false),
            )

            vm.onEvent(RecordingEvent.Start)
            advanceUntilIdle()
            assertTrue("precondition: prompt shown", vm.uiState.value.showBatteryOptPrompt)

            vm.onEvent(RecordingEvent.BatteryOptConfirm)
            advanceUntilIdle()

            assertFalse("prompt should be cleared after confirm", vm.uiState.value.showBatteryOptPrompt)
            assertTrue("dismiss flag should NOT be set on confirm path (intent fires instead)",
                store.batteryPromptDismissedCalls == 0)
        }
}
