package com.mototracker.ui.screens.record

import com.mototracker.R
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.format.RouteWeather
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
import com.mototracker.domain.fuel.AutoUpdateBikeConsumptionUseCase
import com.mototracker.domain.fuel.RefuelEvent
import com.mototracker.data.sensor.HeadingSensorSource
import com.mototracker.data.sensor.LeanSensorSource
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.data.settings.SettingsStore
import com.mototracker.domain.battery.BatteryOptimizationChecker
import com.mototracker.domain.recording.LocationSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class WxFakeRideLocationCollector : RideLocationCollector(
    locationClient = object : LocationClient {
        override fun locationUpdates(intervalMs: Long): Flow<LocationSample> = flow {}
    },
    gnssStatusClient = object : GnssStatusClient {
        override fun satelliteCounts(): Flow<GnssSatelliteCount> = flow {}
    },
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
) {
    private val _flow = MutableSharedFlow<LocationSample>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val samples: SharedFlow<LocationSample> = _flow.asSharedFlow()
    override fun start(intervalMs: Long) {}
    override fun stop() {}
    fun tryEmit(sample: LocationSample): Boolean = _flow.tryEmit(sample)
}

private class WxFakeLeanSource : LeanSensorSource {
    override val leanAngles: Flow<Double> = flow {}
}

private class WxFakeHeadingSource : HeadingSensorSource {
    override val headings: Flow<Float> = flow {}
}

private class WxFakeRouteRepository : RouteRepository {
    val saved = mutableListOf<Route>()
    private val allFlow = MutableStateFlow<List<Route>>(emptyList())
    override suspend fun save(route: Route) { saved += route; allFlow.value = saved.toList() }
    override fun observeSummaries(): Flow<List<RouteSummaryModel>> =
        allFlow.map { list -> list.map { it.toRouteSummaryModel() } }
    override suspend fun getById(id: String): Route? = saved.find { it.id == id }
    override fun observeById(id: String): Flow<Route?> = MutableStateFlow(saved.find { it.id == id })
    override suspend fun clearCorrectedTrace(id: String) {}
    override suspend fun deleteAll() { saved.clear(); allFlow.value = emptyList() }
    override suspend fun rename(id: String, name: String) {}
    override suspend fun setBike(routeId: String, bikeId: String?) {}
}

private class WxFakeBikeRepository : BikeRepository {
    override fun observeAll(): Flow<List<Bike>> = MutableStateFlow(emptyList())
    override suspend fun addBike(bike: Bike) {}
    override suspend fun deleteAll() {}
}

private class WxFakeSyncRepository : SyncRepository {
    override val pendingCount: Flow<Int> = MutableStateFlow(0)
    override suspend fun enqueue(routeId: String) {}
    override suspend fun syncNow(): Int = 0
    override fun start(scope: CoroutineScope) = Unit
}

private class WxFakeSettingsSource(settings: AppSettings = AppSettings()) : AppSettingsSource {
    override val settings: Flow<AppSettings> = MutableStateFlow(settings)
}

private class WxFakeNetworkMonitor(isOnline: Boolean = true) : NetworkMonitor {
    override val isOnline: Flow<Boolean> = MutableStateFlow(isOnline)
}

private class WxFakeTimeProvider : TimeProvider {
    override fun nowEpochMs(): Long = 1_000_000L
}

private class WxFakeLogger : RideDebugLogger {
    override fun beginRide() {}
    override fun endRide() {}
    override fun log(tag: String, message: String) {}
}

private class WxFakeGeocoder : ReverseGeocoder {
    override suspend fun areaName(lat: Double, lng: Double): String? = null
}

private class WxFakeStringResolver : StringResolver {
    override fun getString(resId: Int): String = when (resId) {
        R.string.route_name_ride_morning   -> "morning ride"
        R.string.route_name_ride_afternoon -> "afternoon ride"
        R.string.route_name_ride_evening   -> "evening ride"
        R.string.route_name_ride_night     -> "night ride"
        R.string.route_name_with_area      -> "%1\$s – %2\$s"
        else -> "stub"
    }
    override fun getString(resId: Int, vararg args: Any): String = getString(resId)
}

private class WxFakeSessionStore : RecordingSessionStore {
    private val _flow = MutableStateFlow<ActiveSessionSnapshot?>(null)
    override val snapshot: Flow<ActiveSessionSnapshot?> = _flow
    override suspend fun save(s: ActiveSessionSnapshot) { _flow.value = s }
    override suspend fun clear() { _flow.value = null }
}

private class WxFakeResumeRouteBus : ResumeRouteBus {
    override val requests: Flow<String> = flow {}
    override suspend fun request(routeId: String) {}
}

private class WxChannelResumeRouteBus : ResumeRouteBus {
    private val _channel = Channel<String>(Channel.BUFFERED)
    override val requests: Flow<String> = _channel.receiveAsFlow()
    override suspend fun request(routeId: String) { _channel.send(routeId) }
}

private class WxFakeRefuelRepository : RefuelRepository {
    override suspend fun addRefuel(routeId: String, epochMs: Long, litres: Double, pricePerL: Double) {}
    override fun observeRefuels(routeId: String): Flow<List<RefuelEvent>> = MutableStateFlow(emptyList())
    override suspend fun deleteRefuel(id: Long) {}
    override fun observeAllForBike(bikeId: String): Flow<List<RefuelEvent>> = MutableStateFlow(emptyList())
}

/** Controllable [WeatherClient] fake that records how many times it was called. */
private class WxFakeWeatherClient(
    private val result: Result<WeatherSnapshot> = Result.success(
        WeatherSnapshot(tempC = 22, humidity = 65, rain = false),
    ),
) : WeatherClient {
    var fetchCallCount = 0
    var lastLat: Double? = null
    var lastLon: Double? = null

    override suspend fun fetch(lat: Double, lon: Double): Result<WeatherSnapshot> {
        fetchCallCount++
        lastLat = lat
        lastLon = lon
        return result
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModelWeatherTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun noOpAutoUpdate() = AutoUpdateBikeConsumptionUseCase(
        bikeRepository = WxFakeBikeRepository(),
        routeRepository = WxFakeRouteRepository(),
        refuelRepository = WxFakeRefuelRepository(),
    )

    private fun buildVm(
        online: Boolean = true,
        settings: AppSettings = AppSettings(),
        locationCollector: WxFakeRideLocationCollector = WxFakeRideLocationCollector(),
        routeRepo: WxFakeRouteRepository = WxFakeRouteRepository(),
        weatherClient: WeatherClient = WxFakeWeatherClient(),
        resumeRouteBus: ResumeRouteBus = WxFakeResumeRouteBus(),
    ) = RecordingViewModel(
        rideLocationCollector = locationCollector,
        leanSensorSource = WxFakeLeanSource(),
        headingSensorSource = WxFakeHeadingSource(),
        routeRepository = routeRepo,
        syncRepository = WxFakeSyncRepository(),
        settingsSource = WxFakeSettingsSource(settings),
        bikeRepository = WxFakeBikeRepository(),
        networkMonitor = WxFakeNetworkMonitor(isOnline = online),
        timeProvider = WxFakeTimeProvider(),
        carBridge = CarRecordingBridge(),
        rideDebugLogger = WxFakeLogger(),
        reverseGeocoder = WxFakeGeocoder(),
        stringResolver = WxFakeStringResolver(),
        sessionStore = WxFakeSessionStore(),
        refuelRepository = WxFakeRefuelRepository(),
        resumeRouteBus = resumeRouteBus,
        autoUpdateBikeConsumptionUseCase = noOpAutoUpdate(),
        weatherClient = weatherClient,
        batteryOptimizationChecker = object : BatteryOptimizationChecker {
            override fun isIgnoringBatteryOptimizations() = true
        },
        settingsStore = object : SettingsStore {
            override val settings: kotlinx.coroutines.flow.Flow<AppSettings> = MutableStateFlow(AppSettings())
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
        },
        fuelAdjustmentRepository = object : com.mototracker.data.repository.FuelAdjustmentRepository {
            override suspend fun addAdjustment(bikeId: String, routeId: String?, epochMs: Long, mode: com.mototracker.domain.fuel.FuelAdjustmentMode, litres: Double) {}
            override fun observeForBike(bikeId: String): kotlinx.coroutines.flow.Flow<List<com.mototracker.domain.fuel.FuelAdjustmentEvent>> = MutableStateFlow(emptyList())
            override suspend fun latestForBike(bikeId: String): com.mototracker.domain.fuel.FuelAdjustmentEvent? = null
        },
        riderRepository = FakeRiderRepository(),
    )

    private val sampleFix = LocationSample(
        lat = 50.0, lng = 20.0,
        speedMps = 10.0, altitudeM = 100.0, bearingDeg = 90f, timeMs = 1000L,
    )

    // ── Online, not-offline: client called once, wxJson populated ─────────────

    @Test
    fun `online not-offline — weather client called exactly once on first GPS fix`() =
        runTest(testDispatcher) {
            val fakeWeather = WxFakeWeatherClient()
            val collector = WxFakeRideLocationCollector()
            val vm = buildVm(
                online = true,
                settings = AppSettings(),
                locationCollector = collector,
                weatherClient = fakeWeather,
            )
            vm.onEvent(RecordingEvent.Start)
            advanceTimeBy(100L)

            collector.tryEmit(sampleFix)
            advanceTimeBy(300L) // let sibling weather coroutine run

            // Second fix — should not trigger another fetch
            collector.tryEmit(sampleFix.copy(lat = 50.01))
            advanceTimeBy(100L)

            assertEquals("weather client should be called exactly once", 1, fakeWeather.fetchCallCount)
            vm.onEvent(RecordingEvent.Pause)
        }

    @Test
    fun `online not-offline — saved Route wxJson is non-null and parseable`() =
        runTest(testDispatcher) {
            val fakeWeather = WxFakeWeatherClient(
                Result.success(WeatherSnapshot(tempC = 22, humidity = 65, rain = false)),
            )
            val collector = WxFakeRideLocationCollector()
            val routeRepo = WxFakeRouteRepository()
            val vm = buildVm(
                online = true,
                settings = AppSettings(),
                locationCollector = collector,
                routeRepo = routeRepo,
                weatherClient = fakeWeather,
            )
            vm.onEvent(RecordingEvent.Start)
            advanceTimeBy(100L)
            collector.tryEmit(sampleFix)
            advanceTimeBy(300L)

            vm.onEvent(RecordingEvent.Finish)
            testDispatcher.scheduler.advanceUntilIdle()

            val route = routeRepo.saved.firstOrNull()
            assertNotNull("route should be saved", route)
            assertNotNull("wxJson should be non-null when online", route!!.wxJson)

            val ui = RouteWeather.parse(route.wxJson)
            assertFalse("WeatherUi should not be offline", ui.offline)
            assertEquals("22°C", ui.tempDisplay)
            assertEquals("65%", ui.humDisplay)
        }

    @Test
    fun `online not-offline — weather client receives correct GPS coordinates from first fix`() =
        runTest(testDispatcher) {
            val fakeWeather = WxFakeWeatherClient()
            val collector = WxFakeRideLocationCollector()
            val vm = buildVm(online = true, locationCollector = collector, weatherClient = fakeWeather)
            vm.onEvent(RecordingEvent.Start)
            advanceTimeBy(100L)
            collector.tryEmit(sampleFix)
            advanceTimeBy(200L)

            assertEquals(50.0, fakeWeather.lastLat ?: -1.0, 0.0001)
            assertEquals(20.0, fakeWeather.lastLon ?: -1.0, 0.0001)
            vm.onEvent(RecordingEvent.Pause)
        }

    // ── Offline gate: client must not be called ───────────────────────────────

    @Test
    fun `settings noInternet=true — client never called and wxJson is null`() =
        runTest(testDispatcher) {
            val fakeWeather = WxFakeWeatherClient()
            val collector = WxFakeRideLocationCollector()
            val routeRepo = WxFakeRouteRepository()
            val vm = buildVm(
                online = true,
                settings = AppSettings(noInternet = true),
                locationCollector = collector,
                routeRepo = routeRepo,
                weatherClient = fakeWeather,
            )
            vm.onEvent(RecordingEvent.Start)
            advanceTimeBy(100L)
            collector.tryEmit(sampleFix)
            advanceTimeBy(300L)
            vm.onEvent(RecordingEvent.Finish)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("client should not be called when noInternet=true", 0, fakeWeather.fetchCallCount)
            assertNull("wxJson should be null when noInternet", routeRepo.saved.firstOrNull()?.wxJson)
        }

    @Test
    fun `no network connectivity — client never called and wxJson is null`() =
        runTest(testDispatcher) {
            val fakeWeather = WxFakeWeatherClient()
            val collector = WxFakeRideLocationCollector()
            val routeRepo = WxFakeRouteRepository()
            val vm = buildVm(
                online = false,
                settings = AppSettings(),
                locationCollector = collector,
                routeRepo = routeRepo,
                weatherClient = fakeWeather,
            )
            vm.onEvent(RecordingEvent.Start)
            advanceTimeBy(100L)
            collector.tryEmit(sampleFix)
            advanceTimeBy(300L)
            vm.onEvent(RecordingEvent.Finish)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("client should not be called when not online", 0, fakeWeather.fetchCallCount)
            assertNull("wxJson should be null when not online", routeRepo.saved.firstOrNull()?.wxJson)
        }

    // ── Weather client failure does not break recording ───────────────────────

    @Test
    fun `weather client failure does not block recording and wxJson is null`() =
        runTest(testDispatcher) {
            val fakeWeather = WxFakeWeatherClient(Result.failure(RuntimeException("no weather")))
            val collector = WxFakeRideLocationCollector()
            val routeRepo = WxFakeRouteRepository()
            val vm = buildVm(
                online = true,
                locationCollector = collector,
                routeRepo = routeRepo,
                weatherClient = fakeWeather,
            )
            vm.onEvent(RecordingEvent.Start)
            advanceTimeBy(100L)
            collector.tryEmit(sampleFix)
            advanceTimeBy(300L)

            assertEquals("recording should still be active despite weather failure",
                RecordingPhase.Recording, vm.uiState.value.phase)

            vm.onEvent(RecordingEvent.Finish)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(RecordingPhase.Idle, vm.uiState.value.phase)
            assertNull("wxJson should be null when weather fetch failed", routeRepo.saved.firstOrNull()?.wxJson)
        }

    // ── J5 resume path: wxJson must be null ──────────────────────────────────

    @Test
    fun `J5 resume path — wxJson is null even when weather was fetched`() =
        runTest(testDispatcher) {
            val fakeWeather = WxFakeWeatherClient(
                Result.success(WeatherSnapshot(tempC = 15, humidity = 80, rain = true)),
            )
            val existingRoute = Route(
                id = "route-j5-wx",
                name = "Alpine Pass",
                dateEpochMs = 1_700_000_000_000L,
                bikeId = null, km = 50.0, durSec = 1800L, avg = 100.0, max = 140.0,
                lean = 25.0, elev = 300.0, fuel = 3.0, synced = true, wxJson = null,
                pathJson = """[{"lat":50.0,"lng":20.0},{"lat":50.1,"lng":20.1}]""",
                speedJson = null, elevProfileJson = null, notes = null,
            )
            val routeRepo = WxFakeRouteRepository().apply { saved += existingRoute }
            val collector = WxFakeRideLocationCollector()
            val bus = WxChannelResumeRouteBus()

            val vm = buildVm(
                online = true,
                locationCollector = collector,
                routeRepo = routeRepo,
                weatherClient = fakeWeather,
                resumeRouteBus = bus,
            )
            testDispatcher.scheduler.runCurrent()

            bus.request(existingRoute.id)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(RecordingPhase.Paused, vm.uiState.value.phase)

            vm.onEvent(RecordingEvent.Resume)
            advanceTimeBy(100L)
            collector.tryEmit(sampleFix)
            advanceTimeBy(300L)

            vm.onEvent(RecordingEvent.Finish)
            testDispatcher.scheduler.advanceUntilIdle()

            val saved = routeRepo.saved.lastOrNull { it.id == existingRoute.id }
            assertNotNull("route should be saved with the original id", saved)
            assertNull("wxJson must be null on J5 resume path", saved!!.wxJson)
        }
}
