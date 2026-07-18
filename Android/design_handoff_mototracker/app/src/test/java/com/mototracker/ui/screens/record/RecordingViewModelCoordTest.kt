package com.mototracker.ui.screens.record

import com.mototracker.R
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.format.CoordFormat
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Minimal fakes for P1 coordinate / coordFormat tests
// ─────────────────────────────────────────────────────────────────────────────

private class CCoordFakeLocationCollector : RideLocationCollector(
    locationClient = object : LocationClient {
        override fun locationUpdates(intervalMs: Long): Flow<LocationSample> = flow {}
    },
    gnssStatusClient = object : GnssStatusClient {
        override fun satelliteCounts(): Flow<GnssSatelliteCount> = flow {}
    },
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
) {
    private val _flow = MutableSharedFlow<LocationSample>(
        replay = 0, extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST,
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

    fun tryEmit(sample: LocationSample): Boolean = _flow.tryEmit(sample)
}

private class CCoordFakeSettingsSource(
    private val settingsFlow: MutableStateFlow<AppSettings>,
) : AppSettingsSource {
    constructor(initial: AppSettings = AppSettings()) : this(MutableStateFlow(initial))
    override val settings: Flow<AppSettings> = settingsFlow
    fun update(settings: AppSettings) { settingsFlow.value = settings }
}

private class CCoordFakeRouteRepository : RouteRepository {
    private val flow = MutableStateFlow<List<Route>>(emptyList())
    override suspend fun save(route: Route) { flow.value = flow.value + route }
    override fun observeSummaries(): Flow<List<RouteSummaryModel>> =
        flow.map { list -> list.map { it.toRouteSummaryModel() } }
    override suspend fun getById(id: String): Route? = flow.value.find { it.id == id }
    override fun observeById(id: String): Flow<Route?> = MutableStateFlow(null)
    override suspend fun clearCorrectedTrace(id: String) {}
    override suspend fun deleteAll() { flow.value = emptyList() }
    override suspend fun rename(id: String, name: String) {}
    override suspend fun setBike(routeId: String, bikeId: String?) {}
}

private class CCoordFakeLogger : RideDebugLogger {
    override fun beginRide() {}
    override fun endRide() {}
    override fun log(tag: String, message: String) {}
}

private class CCoordFakeStringResolver : StringResolver {
    override fun getString(resId: Int): String = when (resId) {
        R.string.route_name_ride_morning   -> "morning ride"
        R.string.route_name_ride_afternoon -> "afternoon ride"
        R.string.route_name_ride_evening   -> "evening ride"
        R.string.route_name_ride_night     -> "night ride"
        R.string.route_name_with_area      -> "%1\$s – %2\$s"
        else -> "stub_$resId"
    }
    override fun getString(resId: Int, vararg args: Any): String = getString(resId)
}

private class CCoordFakeSettingsStore : SettingsStore {
    override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())
    override suspend fun setOffline(value: Boolean) {}
    override suspend fun setAutoSync(value: Boolean) {}
    override suspend fun setOfflineOnly(value: Boolean) {}
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
    override suspend fun setBatteryPromptDismissed(value: Boolean) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModelCoordTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm(
        collector: CCoordFakeLocationCollector = CCoordFakeLocationCollector(),
        settingsSource: CCoordFakeSettingsSource = CCoordFakeSettingsSource(),
    ): RecordingViewModel {
        val routeRepo = CCoordFakeRouteRepository()
        val syncRepo = object : SyncRepository {
            override val pendingCount: Flow<Int> = MutableStateFlow(0)
            override suspend fun enqueue(routeId: String) {}
            override suspend fun syncNow(): Int = 0
            override fun start(scope: CoroutineScope) {}
        }
        val bikeRepo = object : BikeRepository {
            override fun observeAll(): Flow<List<Bike>> = MutableStateFlow(emptyList())
            override suspend fun addBike(bike: Bike) {}
            override suspend fun deleteAll() {}
        }
        val refuelRepo = object : RefuelRepository {
            override suspend fun addRefuel(routeId: String, epochMs: Long, litres: Double, pricePerL: Double) {}
            override fun observeRefuels(routeId: String): Flow<List<RefuelEvent>> = MutableStateFlow(emptyList())
            override suspend fun deleteRefuel(id: Long) {}
            override fun observeAllForBike(bikeId: String): Flow<List<RefuelEvent>> = MutableStateFlow(emptyList())
        }
        val sessionStore = object : RecordingSessionStore {
            override val snapshot: Flow<ActiveSessionSnapshot?> = MutableStateFlow(null)
            override suspend fun save(s: ActiveSessionSnapshot) {}
            override suspend fun clear() {}
        }
        val resumeBus = object : ResumeRouteBus {
            override val requests: Flow<String> = flow {}
            override suspend fun request(routeId: String) {}
        }
        val autoUpdate = AutoUpdateBikeConsumptionUseCase(
            bikeRepository = bikeRepo,
            routeRepository = routeRepo,
            refuelRepository = refuelRepo,
        )
        return RecordingViewModel(
            rideLocationCollector = collector,
            leanSensorSource = object : LeanSensorSource { override val leanAngles: Flow<Double> = flow {} },
            headingSensorSource = object : HeadingSensorSource { override val headings: Flow<Float> = flow {} },
            routeRepository = routeRepo,
            syncRepository = syncRepo,
            settingsSource = settingsSource,
            bikeRepository = bikeRepo,
            networkMonitor = object : NetworkMonitor { override val isOnline: Flow<Boolean> = MutableStateFlow(true) },
            timeProvider = object : TimeProvider { override fun nowEpochMs(): Long = 1_000_000L },
            carBridge = CarRecordingBridge(),
            rideDebugLogger = CCoordFakeLogger(),
            reverseGeocoder = object : ReverseGeocoder { override suspend fun areaName(lat: Double, lng: Double): String? = null },
            stringResolver = CCoordFakeStringResolver(),
            sessionStore = sessionStore,
            refuelRepository = refuelRepo,
            resumeRouteBus = resumeBus,
            autoUpdateBikeConsumptionUseCase = autoUpdate,
            weatherClient = object : WeatherClient {
                override suspend fun fetch(lat: Double, lon: Double) = Result.success(WeatherSnapshot(20, 65, false))
            },
            batteryOptimizationChecker = object : BatteryOptimizationChecker { override fun isIgnoringBatteryOptimizations() = true },
            settingsStore = CCoordFakeSettingsStore(),
        )
    }

    // ── P1: liveLat / liveLng from GPS samples ─────────────────────────────

    @Test
    fun `P1a liveLat and liveLng are null before any GPS sample`() = runTest(testDispatcher) {
        val vm = buildVm()
        advanceTimeBy(100L)

        assertNull("liveLat should be null before any fix", vm.uiState.value.liveLat)
        assertNull("liveLng should be null before any fix", vm.uiState.value.liveLng)
    }

    @Test
    fun `P1b GPS sample in Idle sets liveLat and liveLng`() = runTest(testDispatcher) {
        val collector = CCoordFakeLocationCollector()
        val vm = buildVm(collector = collector)
        advanceTimeBy(100L)

        collector.tryEmit(
            LocationSample(lat = 51.26059, lng = 15.56916, speedMps = 0.0, altitudeM = 200.0, bearingDeg = 0f, timeMs = 1_000L),
        )
        advanceTimeBy(100L)

        assertEquals(
            "liveLat should match sample latitude",
            51.26059, vm.uiState.value.liveLat ?: Double.NaN, 0.00001,
        )
        assertEquals(
            "liveLng should match sample longitude",
            15.56916, vm.uiState.value.liveLng ?: Double.NaN, 0.00001,
        )
        // Engine must NOT have been advanced in Idle
        assertEquals(
            "distanceKm must stay 0 in Idle",
            0.0, vm.uiState.value.metrics.distanceKm, 0.0,
        )
    }

    @Test
    fun `P1c GPS sample during Recording also updates liveLat and liveLng`() = runTest(testDispatcher) {
        val collector = CCoordFakeLocationCollector()
        val vm = buildVm(collector = collector)
        advanceTimeBy(100L)

        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(100L)

        collector.tryEmit(
            LocationSample(lat = 50.0, lng = 20.0, speedMps = 10.0, altitudeM = 100.0, bearingDeg = 90f, timeMs = 1_000L),
        )
        collector.tryEmit(
            LocationSample(lat = 50.00025, lng = 20.0, speedMps = 12.0, altitudeM = 110.0, bearingDeg = 90f, timeMs = 2_000L),
        )
        advanceTimeBy(200L)

        assertEquals(
            "liveLat should reflect latest sample",
            50.00025, vm.uiState.value.liveLat ?: Double.NaN, 0.00001,
        )
        assertEquals(
            "liveLng should reflect latest sample",
            20.0, vm.uiState.value.liveLng ?: Double.NaN, 0.00001,
        )
        vm.onEvent(RecordingEvent.Pause)
    }

    // ── P1: coordFormat from settings ────────────────────────────────────────

    @Test
    fun `P1d coordFormat defaults to DECIMAL_DEGREES when settings key is dd`() = runTest(testDispatcher) {
        val vm = buildVm(settingsSource = CCoordFakeSettingsSource(AppSettings(coordFormat = "dd")))
        advanceTimeBy(100L)

        assertEquals(CoordFormat.DECIMAL_DEGREES, vm.uiState.value.coordFormat)
    }

    @Test
    fun `P1e settings key dms maps coordFormat to DMS`() = runTest(testDispatcher) {
        val vm = buildVm(settingsSource = CCoordFakeSettingsSource(AppSettings(coordFormat = "dms")))
        advanceTimeBy(100L)

        assertEquals(CoordFormat.DMS, vm.uiState.value.coordFormat)
    }

    @Test
    fun `P1f settings key utm maps coordFormat to UTM`() = runTest(testDispatcher) {
        val vm = buildVm(settingsSource = CCoordFakeSettingsSource(AppSettings(coordFormat = "utm")))
        advanceTimeBy(100L)

        assertEquals(CoordFormat.UTM, vm.uiState.value.coordFormat)
    }

    @Test
    fun `P1g unknown settings key maps coordFormat to DECIMAL_DEGREES`() = runTest(testDispatcher) {
        val vm = buildVm(settingsSource = CCoordFakeSettingsSource(AppSettings(coordFormat = "garbage")))
        advanceTimeBy(100L)

        assertEquals(CoordFormat.DECIMAL_DEGREES, vm.uiState.value.coordFormat)
    }

    @Test
    fun `P1h coordFormat updates reactively when settings change`() = runTest(testDispatcher) {
        val src = CCoordFakeSettingsSource(AppSettings(coordFormat = "dd"))
        val vm = buildVm(settingsSource = src)
        advanceTimeBy(100L)

        assertEquals(CoordFormat.DECIMAL_DEGREES, vm.uiState.value.coordFormat)

        src.update(AppSettings(coordFormat = "utm"))
        advanceTimeBy(100L)

        assertEquals(CoordFormat.UTM, vm.uiState.value.coordFormat)
    }
}
