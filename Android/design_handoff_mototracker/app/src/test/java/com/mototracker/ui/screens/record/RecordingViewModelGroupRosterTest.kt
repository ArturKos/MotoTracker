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
import com.mototracker.data.model.Rider
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
import com.mototracker.data.repository.RiderRepository
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class GRFakeLocationCollector : RideLocationCollector(
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

private class GRFakeRouteRepository : RouteRepository {
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

private class GRFakeBikeRepository : BikeRepository {
    override fun observeAll(): Flow<List<Bike>> = MutableStateFlow(emptyList())
    override suspend fun addBike(bike: Bike) {}
    override suspend fun deleteAll() {}
}

private class GRFakeRefuelRepository : RefuelRepository {
    override suspend fun addRefuel(routeId: String, epochMs: Long, litres: Double, pricePerL: Double) {}
    override fun observeRefuels(routeId: String): Flow<List<RefuelEvent>> = MutableStateFlow(emptyList())
    override suspend fun deleteRefuel(id: Long) {}
    override fun observeAllForBike(bikeId: String): Flow<List<RefuelEvent>> = MutableStateFlow(emptyList())
}

private class GRFakeSyncRepository : SyncRepository {
    override val pendingCount: Flow<Int> = MutableStateFlow(0)
    override suspend fun enqueue(routeId: String) {}
    override suspend fun syncNow(): Int = 0
    override fun start(scope: CoroutineScope) = Unit
}

private class GRFakeSessionStore : RecordingSessionStore {
    override val snapshot: Flow<ActiveSessionSnapshot?> = MutableStateFlow(null)
    override suspend fun save(s: ActiveSessionSnapshot) {}
    override suspend fun clear() {}
}

private class GRFakeSettingsSource(settings: AppSettings = AppSettings()) : AppSettingsSource {
    override val settings: Flow<AppSettings> = MutableStateFlow(settings)
}

private class GRFakeSettingsStore : SettingsStore {
    override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())
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

/**
 * Tracking [RiderRepository] that records every [setInGroup] call so tests can assert
 * delegation without an Android runtime.
 */
private class TrackingRiderRepository(
    initialRiders: List<Rider> = emptyList(),
) : RiderRepository {
    private val _riders = MutableStateFlow(initialRiders)
    override fun observeAll(): Flow<List<Rider>> = _riders

    data class SetInGroupCall(val shortId: String, val inGroup: Boolean)

    val setInGroupCalls = mutableListOf<SetInGroupCall>()

    override suspend fun setInGroup(shortId: String, inGroup: Boolean) {
        setInGroupCalls += SetInGroupCall(shortId, inGroup)
        _riders.value = _riders.value.map {
            if (it.shortId == shortId) it.copy(inGroup = inGroup) else it
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests — X2 group-roster ViewModel paths
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModelGroupRosterTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── ShowGroupRoster ──────────────────────────────────────────────────────

    @Test
    fun `ShowGroupRoster event sets showGroupRosterSheet to true`() = runTest(testDispatcher) {
        val vm = buildVm()
        advanceUntilIdle()

        assertFalse("sheet should start closed", vm.uiState.value.showGroupRosterSheet)
        vm.onEvent(RecordingEvent.ShowGroupRoster)
        advanceUntilIdle()

        assertTrue("ShowGroupRoster should open the sheet", vm.uiState.value.showGroupRosterSheet)
    }

    // ── DismissGroupRoster ───────────────────────────────────────────────────

    @Test
    fun `DismissGroupRoster event sets showGroupRosterSheet to false`() = runTest(testDispatcher) {
        val vm = buildVm()
        advanceUntilIdle()

        vm.onEvent(RecordingEvent.ShowGroupRoster)
        advanceUntilIdle()
        assertTrue("sheet should be open before dismiss", vm.uiState.value.showGroupRosterSheet)

        vm.onEvent(RecordingEvent.DismissGroupRoster)
        advanceUntilIdle()
        assertFalse("DismissGroupRoster should close the sheet", vm.uiState.value.showGroupRosterSheet)
    }

    @Test
    fun `DismissGroupRoster is a no-op when sheet is already closed`() = runTest(testDispatcher) {
        val vm = buildVm()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showGroupRosterSheet)
        vm.onEvent(RecordingEvent.DismissGroupRoster)
        advanceUntilIdle()
        assertFalse(
            "DismissGroupRoster on closed sheet should leave it closed",
            vm.uiState.value.showGroupRosterSheet,
        )
    }

    // ── ToggleGroup → riderRepository.setInGroup ─────────────────────────────

    @Test
    fun `ToggleGroup event delegates to riderRepository setInGroup with correct args`() =
        runTest(testDispatcher) {
            val repo = TrackingRiderRepository()
            val vm = buildVm(riderRepo = repo)
            advanceUntilIdle()

            vm.onEvent(RecordingEvent.ToggleGroup(shortId = "aa:bb:cc", inGroup = true))
            advanceUntilIdle()

            assertEquals("setInGroup should have been called once", 1, repo.setInGroupCalls.size)
            val call = repo.setInGroupCalls.first()
            assertEquals("shortId should match", "aa:bb:cc", call.shortId)
            assertTrue("inGroup should be true", call.inGroup)
        }

    @Test
    fun `ToggleGroup with inGroup=false removes rider from group in repository`() =
        runTest(testDispatcher) {
            val rider = Rider(
                shortId = "de:ad:be",
                nick = "Rider1",
                bike = "MT-07",
                lastSeenMs = 1_000L,
                inGroup = true,
            )
            val repo = TrackingRiderRepository(initialRiders = listOf(rider))
            val vm = buildVm(riderRepo = repo)
            advanceUntilIdle()

            vm.onEvent(RecordingEvent.ToggleGroup(shortId = "de:ad:be", inGroup = false))
            advanceUntilIdle()

            assertEquals(1, repo.setInGroupCalls.size)
            assertFalse("inGroup should be false", repo.setInGroupCalls.first().inGroup)
            assertEquals("de:ad:be", repo.setInGroupCalls.first().shortId)
        }

    @Test
    fun `multiple ToggleGroup calls each reach the repository`() = runTest(testDispatcher) {
        val repo = TrackingRiderRepository()
        val vm = buildVm(riderRepo = repo)
        advanceUntilIdle()

        vm.onEvent(RecordingEvent.ToggleGroup(shortId = "aa:aa:aa", inGroup = true))
        vm.onEvent(RecordingEvent.ToggleGroup(shortId = "bb:bb:bb", inGroup = false))
        advanceUntilIdle()

        assertEquals("both toggle events should reach repository", 2, repo.setInGroupCalls.size)
        assertEquals("aa:aa:aa", repo.setInGroupCalls[0].shortId)
        assertEquals("bb:bb:bb", repo.setInGroupCalls[1].shortId)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildVm(
        riderRepo: RiderRepository = TrackingRiderRepository(),
        settings: AppSettings = AppSettings(),
    ): RecordingViewModel {
        val routeRepo = GRFakeRouteRepository()
        val bikeRepo = GRFakeBikeRepository()
        val refuelRepo = GRFakeRefuelRepository()
        return RecordingViewModel(
            rideLocationCollector = GRFakeLocationCollector(),
            leanSensorSource = object : LeanSensorSource {
                override val leanAngles: Flow<Double> = flow {}
            },
            headingSensorSource = object : HeadingSensorSource {
                override val headings: Flow<Float> = flow {}
            },
            routeRepository = routeRepo,
            syncRepository = GRFakeSyncRepository(),
            settingsSource = GRFakeSettingsSource(settings),
            bikeRepository = bikeRepo,
            networkMonitor = object : NetworkMonitor {
                override val isOnline: Flow<Boolean> = MutableStateFlow(true)
            },
            timeProvider = object : TimeProvider {
                override fun nowEpochMs(): Long = 1_000_000L
            },
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
                    R.string.route_name_ride_morning -> "morning ride"
                    R.string.route_name_ride_afternoon -> "afternoon ride"
                    R.string.route_name_ride_evening -> "evening ride"
                    R.string.route_name_ride_night -> "night ride"
                    R.string.route_name_with_area -> "%1\$s – %2\$s"
                    else -> "stub"
                }
                override fun getString(resId: Int, vararg args: Any): String = getString(resId)
            },
            sessionStore = GRFakeSessionStore(),
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
            batteryOptimizationChecker = object : BatteryOptimizationChecker {
                override fun isIgnoringBatteryOptimizations(): Boolean = true
            },
            settingsStore = GRFakeSettingsStore(),
            fuelAdjustmentRepository = object : com.mototracker.data.repository.FuelAdjustmentRepository {
                override suspend fun addAdjustment(
                    bikeId: String,
                    routeId: String?,
                    epochMs: Long,
                    mode: com.mototracker.domain.fuel.FuelAdjustmentMode,
                    litres: Double,
                ) {}
                override fun observeForBike(bikeId: String): Flow<List<com.mototracker.domain.fuel.FuelAdjustmentEvent>> =
                    MutableStateFlow(emptyList())
                override suspend fun latestForBike(bikeId: String): com.mototracker.domain.fuel.FuelAdjustmentEvent? = null
            },
            riderRepository = riderRepo,
        )
    }
}
