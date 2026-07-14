package com.mototracker.ui.screens.record

import app.cash.turbine.test
import com.mototracker.R
import com.mototracker.core.resource.StringResolver
import com.mototracker.core.time.TimeProvider
import com.mototracker.data.diagnostics.RideDebugLogger
import com.mototracker.data.location.LocationClient
import com.mototracker.data.location.ReverseGeocoder
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.model.mapper.toRouteSummaryModel
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.recording.ActiveSessionSnapshot
import com.mototracker.data.recording.PendingRefuel
import com.mototracker.data.recording.RecordingSessionStore
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RefuelRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.domain.fuel.RefuelEvent
import com.mototracker.data.sensor.HeadingSensorSource
import com.mototracker.data.sensor.LeanSensorSource
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.domain.recording.LocationSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class FakeLocationClient(
    private val locations: Flow<LocationSample> = flow {},
) : LocationClient {
    override fun locationUpdates(intervalMs: Long): Flow<LocationSample> = locations
}

private class FakeLeanSensorSource(
    private val angles: Flow<Double> = flow {},
) : LeanSensorSource {
    override val leanAngles: Flow<Double> = angles
}

private class FakeHeadingSensorSource(
    private val headingFlow: Flow<Float> = flow {},
) : HeadingSensorSource {
    override val headings: Flow<Float> = headingFlow
}

private class FakeRouteRepository : RouteRepository {
    val saved = mutableListOf<Route>()
    private val allFlow = MutableStateFlow<List<Route>>(emptyList())

    override suspend fun save(route: Route) {
        saved += route
        allFlow.value = saved.toList()
    }

    override fun observeSummaries(): Flow<List<RouteSummaryModel>> =
        allFlow.map { list -> list.map { it.toRouteSummaryModel() } }
    override suspend fun getById(id: String): Route? = saved.find { it.id == id }
    override fun observeById(id: String): Flow<Route?> = MutableStateFlow(saved.find { it.id == id })
    override suspend fun clearCorrectedTrace(id: String) { /* stub */ }
    override suspend fun deleteAll() { saved.clear(); allFlow.value = emptyList() }
    override suspend fun rename(id: String, name: String) { /* stub */ }
    override suspend fun setBike(routeId: String, bikeId: String?) { /* stub */ }
}

private class FakeBikeRepository(
    private val bikes: List<Bike> = emptyList(),
) : BikeRepository {
    override fun observeAll(): Flow<List<Bike>> = MutableStateFlow(bikes)
    override suspend fun addBike(bike: Bike) {}
    override suspend fun deleteAll() {}
}

private class FakeSyncRepository : SyncRepository {
    val enqueued = mutableListOf<String>()
    override val pendingCount: Flow<Int> = MutableStateFlow(0)
    override suspend fun enqueue(routeId: String) { enqueued += routeId }
    override suspend fun syncNow(): Int = 0
    override fun start(scope: CoroutineScope) = Unit
}

private class FakeSettingsSource(settings: AppSettings = AppSettings()) : AppSettingsSource {
    override val settings: Flow<AppSettings> = MutableStateFlow(settings)
}

private class FakeNetworkMonitor(isOnline: Boolean = true) : NetworkMonitor {
    override val isOnline: Flow<Boolean> = MutableStateFlow(isOnline)
}

private class FakeTimeProvider(private val nowMs: Long = 1_000_000L) : TimeProvider {
    override fun nowEpochMs(): Long = nowMs
}

private class FakeRideDebugLogger : RideDebugLogger {
    val beginRideCalls = mutableListOf<Unit>()
    val endRideCalls = mutableListOf<Unit>()
    val logCalls = mutableListOf<Pair<String, String>>()

    override fun beginRide() { beginRideCalls += Unit }
    override fun endRide() { endRideCalls += Unit }
    override fun log(tag: String, message: String) { logCalls += tag to message }
}

private class FakeReverseGeocoder(
    private val result: String? = null,
    private val shouldThrow: Boolean = false,
) : ReverseGeocoder {
    override suspend fun areaName(lat: Double, lng: Double): String? {
        if (shouldThrow) throw RuntimeException("geocoder error")
        return result
    }
}

private class FakeRecordingSessionStore(
    initialSnapshot: ActiveSessionSnapshot? = null,
) : RecordingSessionStore {
    val saveCalls = mutableListOf<ActiveSessionSnapshot>()
    var clearCalled = 0
    private val flow = MutableStateFlow(initialSnapshot)

    override val snapshot: kotlinx.coroutines.flow.Flow<ActiveSessionSnapshot?> = flow
    override suspend fun save(s: ActiveSessionSnapshot) { saveCalls += s; flow.value = s }
    override suspend fun clear() { clearCalled++; flow.value = null }
}

/** Fake [StringResolver] backed by the actual R.string constants (available via isIncludeAndroidResources). */
private class FakeStringResolver : StringResolver {
    override fun getString(resId: Int): String = when (resId) {
        R.string.route_name_ride_morning   -> "morning ride"
        R.string.route_name_ride_afternoon -> "afternoon ride"
        R.string.route_name_ride_evening   -> "evening ride"
        R.string.route_name_ride_night     -> "night ride"
        R.string.route_name_with_area      -> "%1\$s – %2\$s"
        else -> "stub_string_$resId"
    }
    override fun getString(resId: Int, vararg args: Any): String = getString(resId)
}

private class FakeRefuelRepository : RefuelRepository {
    val added = mutableListOf<RefuelEvent>()
    override suspend fun addRefuel(routeId: String, epochMs: Long, litres: Double, pricePerL: Double) {
        added += RefuelEvent(id = (added.size + 1).toLong(), routeId = routeId, epochMs = epochMs, litres = litres, pricePerL = pricePerL)
    }
    override fun observeRefuels(routeId: String): kotlinx.coroutines.flow.Flow<List<RefuelEvent>> = MutableStateFlow(emptyList())
    override suspend fun deleteRefuel(id: Long) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var routeRepo: FakeRouteRepository
    private lateinit var syncRepo: FakeSyncRepository
    private lateinit var fakeLogger: FakeRideDebugLogger
    private lateinit var viewModel: RecordingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        routeRepo = FakeRouteRepository()
        syncRepo = FakeSyncRepository()
        fakeLogger = FakeRideDebugLogger()
        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ────────────────────────────────────────────────────────

    @Test
    fun `initial phase is Idle`() {
        assertEquals(RecordingPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `initial metrics are all zero`() {
        val m = viewModel.uiState.value.metrics
        assertEquals(0.0, m.distanceKm, 0.0)
        assertEquals(0L, m.durationSec)
        assertEquals(0.0, m.currentSpeedKmh, 0.0)
    }

    // ── Start ────────────────────────────────────────────────────────────────

    @Test
    fun `Start event transitions phase to Recording`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.onEvent(RecordingEvent.Start)
            val state = awaitItem()
            assertEquals(RecordingPhase.Recording, state.phase)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.onEvent(RecordingEvent.Pause) // cancel ticker before runTest drains scheduler
    }

    // ── Ticker ───────────────────────────────────────────────────────────────

    @Test
    fun `ticker advances durationSec every second while Recording`() = runTest(testDispatcher) {
        viewModel.onEvent(RecordingEvent.Start)
        advanceTimeBy(3_100L) // 3 full seconds + slack
        val duration = viewModel.uiState.value.metrics.durationSec
        assertTrue("duration should be ≥ 3, was $duration", duration >= 3L)
        viewModel.onEvent(RecordingEvent.Pause) // cancel ticker before runTest drains scheduler
    }

    // ── Pause ────────────────────────────────────────────────────────────────

    @Test
    fun `Pause event transitions phase to Paused`() = runTest(testDispatcher) {
        viewModel.onEvent(RecordingEvent.Start)
        viewModel.uiState.test {
            awaitItem() // Recording
            viewModel.onEvent(RecordingEvent.Pause)
            val state = awaitItem()
            assertEquals(RecordingPhase.Paused, state.phase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ticker halts while Paused`() = runTest(testDispatcher) {
        viewModel.onEvent(RecordingEvent.Start)
        advanceTimeBy(1_100L)
        val beforePause = viewModel.uiState.value.metrics.durationSec

        viewModel.onEvent(RecordingEvent.Pause)
        advanceTimeBy(3_000L) // time passes but ticker should be stopped
        val afterPause = viewModel.uiState.value.metrics.durationSec

        assertEquals(beforePause, afterPause)
    }

    // ── Resume ───────────────────────────────────────────────────────────────

    @Test
    fun `Resume event transitions phase back to Recording`() = runTest(testDispatcher) {
        viewModel.onEvent(RecordingEvent.Start)
        viewModel.onEvent(RecordingEvent.Pause)
        viewModel.uiState.test {
            awaitItem() // Paused
            viewModel.onEvent(RecordingEvent.Resume)
            val state = awaitItem()
            assertEquals(RecordingPhase.Recording, state.phase)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.onEvent(RecordingEvent.Pause) // cancel ticker before runTest drains scheduler
    }

    @Test
    fun `ticker resumes incrementing after Resume`() = runTest(testDispatcher) {
        viewModel.onEvent(RecordingEvent.Start)
        advanceTimeBy(1_100L)
        viewModel.onEvent(RecordingEvent.Pause)
        val pausedAt = viewModel.uiState.value.metrics.durationSec

        viewModel.onEvent(RecordingEvent.Resume)
        advanceTimeBy(2_100L)
        val resumed = viewModel.uiState.value.metrics.durationSec

        assertTrue("Timer should advance after resume", resumed > pausedAt)
        viewModel.onEvent(RecordingEvent.Pause) // cancel ticker before runTest drains scheduler
    }

    // ── Finish ───────────────────────────────────────────────────────────────

    @Test
    fun `Finish event saves route to repository`() = runTest(testDispatcher) {
        viewModel.onEvent(RecordingEvent.Start)
        advanceTimeBy(1_100L)
        viewModel.onEvent(RecordingEvent.Finish)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("route should be saved", routeRepo.saved.isNotEmpty())
    }

    @Test
    fun `Finish event enqueues route id in sync repository`() = runTest(testDispatcher) {
        viewModel.onEvent(RecordingEvent.Start)
        advanceTimeBy(1_100L)
        viewModel.onEvent(RecordingEvent.Finish)
        testDispatcher.scheduler.advanceUntilIdle()

        val savedId = routeRepo.saved.firstOrNull()?.id
        assertNotNull(savedId)
        assertTrue("route id should be enqueued", syncRepo.enqueued.contains(savedId))
    }

    @Test
    fun `Finish transitions phase to Idle`() = runTest(testDispatcher) {
        viewModel.onEvent(RecordingEvent.Start)
        viewModel.onEvent(RecordingEvent.Finish)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(RecordingPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun `Finish emits Saved effect with offline=false when online`() = runTest(testDispatcher) {
        val vm = buildViewModel(online = true, offline = false)
        vm.effects.test {
            vm.onEvent(RecordingEvent.Start)
            vm.onEvent(RecordingEvent.Finish)
            val effect = awaitItem()
            assertTrue(effect is RecordingEffect.Saved)
            assertFalse("online session should emit offline=false", (effect as RecordingEffect.Saved).offline)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Finish emits Saved effect with offline=true when offline`() = runTest(testDispatcher) {
        val vm = buildViewModel(online = false, offline = false)
        vm.effects.test {
            vm.onEvent(RecordingEvent.Start)
            vm.onEvent(RecordingEvent.Finish)
            val effect = awaitItem()
            assertTrue(effect is RecordingEffect.Saved)
            assertTrue("offline session should emit offline=true", (effect as RecordingEffect.Saved).offline)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Finish emits Saved effect with offline=true when settings offline flag is true`() = runTest(testDispatcher) {
        val vm = buildViewModel(online = true, offline = true)
        vm.effects.test {
            vm.onEvent(RecordingEvent.Start)
            vm.onEvent(RecordingEvent.Finish)
            val effect = awaitItem()
            assertTrue((effect as RecordingEffect.Saved).offline)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Finish emits Saved effect with offline=true when offlineOnly flag is true`() = runTest(testDispatcher) {
        val vm = buildViewModel(online = true, offline = false, offlineOnly = true)
        vm.effects.test {
            vm.onEvent(RecordingEvent.Start)
            vm.onEvent(RecordingEvent.Finish)
            val effect = awaitItem()
            assertTrue((effect as RecordingEffect.Saved).offline)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saved route has bikeId from settings`() = runTest(testDispatcher) {
        val repo = FakeRouteRepository()
        val vm = buildViewModel(
            routeRepository = repo,
            settings = AppSettings(currentBikeId = "bike-42"),
        )
        vm.onEvent(RecordingEvent.Start)
        vm.onEvent(RecordingEvent.Finish)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("bike-42", repo.saved.first().bikeId)
    }

    @Test
    fun `Finish emits NavigateToDetail effect with saved route id`() = runTest(testDispatcher) {
        val vm = buildViewModel(online = true, offline = false)
        vm.effects.test {
            vm.onEvent(RecordingEvent.Start)
            vm.onEvent(RecordingEvent.Finish)
            val first = awaitItem()
            assertTrue(first is RecordingEffect.Saved)
            val second = awaitItem()
            assertTrue("second effect should be NavigateToDetail", second is RecordingEffect.NavigateToDetail)
            val navEffect = second as RecordingEffect.NavigateToDetail
            assertTrue("routeId should be non-blank", navEffect.routeId.isNotBlank())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saved route uses timestamp from TimeProvider`() = runTest(testDispatcher) {
        val fixedTime = 1_234_567_890L
        val repo = FakeRouteRepository()
        val vm = buildViewModel(routeRepository = repo, fixedTimeMs = fixedTime)
        vm.onEvent(RecordingEvent.Start)
        vm.onEvent(RecordingEvent.Finish)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(fixedTime, repo.saved.first().dateEpochMs)
    }

    // ── SecurityException resilience ─────────────────────────────────────────

    @Test
    fun `ViewModel stays in Recording phase when location flow throws SecurityException`() =
        runTest(testDispatcher) {
            val securityFlow = flow<LocationSample> { throw SecurityException("Permission denied") }
            val vm = buildViewModel(locationClient = FakeLocationClient(securityFlow))

            vm.uiState.test {
                awaitItem() // Idle
                vm.onEvent(RecordingEvent.Start)
                val recordingState = awaitItem()
                assertEquals(RecordingPhase.Recording, recordingState.phase)

                // Advance enough for the SecurityException to propagate through the location job
                // without using advanceUntilIdle() which hangs with the infinite ticker loop.
                advanceTimeBy(200L)

                assertEquals(
                    "ViewModel should remain in Recording despite SecurityException in location flow",
                    RecordingPhase.Recording,
                    vm.uiState.value.phase,
                )
                cancelAndIgnoreRemainingEvents()
            }
            vm.onEvent(RecordingEvent.Pause) // stop the ticker
        }

    // ── Settings sync ────────────────────────────────────────────────────────

    @Test
    fun `gpsOnRoad reflects gpsCorrect from settings`() = runTest(testDispatcher) {
        val vm = buildViewModel(settings = AppSettings(gpsCorrect = true))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("gpsOnRoad should be true when gpsCorrect=true", vm.uiState.value.gpsOnRoad)

        val vmOff = buildViewModel(settings = AppSettings(gpsCorrect = false))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse("gpsOnRoad should be false when gpsCorrect=false", vmOff.uiState.value.gpsOnRoad)
    }

    // ── RideDebugLogger wiring (B10) ─────────────────────────────────────────

    @Test
    fun `Start calls beginRide on logger`() = runTest(testDispatcher) {
        viewModel.onEvent(RecordingEvent.Start)
        // advanceUntilIdle() would hang with the infinite ticker loop; advance just enough.
        advanceTimeBy(200L)
        assertTrue("beginRide should be called on Start", fakeLogger.beginRideCalls.isNotEmpty())
        viewModel.onEvent(RecordingEvent.Pause)
    }

    @Test
    fun `Pause logs LIFECYCLE pause`() = runTest(testDispatcher) {
        viewModel.onEvent(RecordingEvent.Start)
        viewModel.onEvent(RecordingEvent.Pause)
        assertTrue(
            "LIFECYCLE pause log expected",
            fakeLogger.logCalls.any { it.first == "LIFECYCLE" && it.second.contains("pause") },
        )
    }

    @Test
    fun `Resume logs LIFECYCLE resume`() = runTest(testDispatcher) {
        viewModel.onEvent(RecordingEvent.Start)
        viewModel.onEvent(RecordingEvent.Pause)
        viewModel.onEvent(RecordingEvent.Resume)
        assertTrue(
            "LIFECYCLE resume log expected",
            fakeLogger.logCalls.any { it.first == "LIFECYCLE" && it.second.contains("resume") },
        )
        viewModel.onEvent(RecordingEvent.Pause)
    }

    @Test
    fun `Finish logs LIFECYCLE finish and calls endRide`() = runTest(testDispatcher) {
        viewModel.onEvent(RecordingEvent.Start)
        viewModel.onEvent(RecordingEvent.Finish)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(
            "LIFECYCLE finish log expected",
            fakeLogger.logCalls.any { it.first == "LIFECYCLE" && it.second.contains("finish") },
        )
        assertTrue("endRide should be called on Finish", fakeLogger.endRideCalls.isNotEmpty())
    }

    @Test
    fun `GPS samples are logged per location update`() = runTest(testDispatcher) {
        // Completing flow so the location collector finishes and runTest drains cleanly.
        val vm = buildViewModel(
            locationClient = FakeLocationClient(
                flowOf(
                    LocationSample(lat = 50.0, lng = 20.0, speedMps = 16.7, altitudeM = 200.0, bearingDeg = 0f, timeMs = 1000L),
                ),
            ),
            rideDebugLogger = fakeLogger,
        )
        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(200L)

        assertTrue(
            "GPS log expected",
            fakeLogger.logCalls.any { it.first == "GPS" },
        )
        vm.onEvent(RecordingEvent.Pause) // cancel ticker before runTest drains scheduler
    }

    @Test
    fun `lean readings are logged per sensor update`() = runTest(testDispatcher) {
        // A completing flow (not a never-completing SharedFlow): the lean collector
        // finishes after the emission, so runTest can drain the scheduler cleanly —
        // mirrors the passing ticker tests. Ending on Pause cancels the ticker.
        val vm = buildViewModel(
            leanSensorSource = FakeLeanSensorSource(flowOf(15.5)),
            rideDebugLogger = fakeLogger,
        )
        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(200L)

        assertTrue(
            "LEAN log expected",
            fakeLogger.logCalls.any { it.first == "LEAN" },
        )
        vm.onEvent(RecordingEvent.Pause) // cancel ticker before runTest drains scheduler
    }

    @Test
    fun `SecurityException in location flow logs ERROR`() = runTest(testDispatcher) {
        val securityFlow = flow<LocationSample> { throw SecurityException("denied") }
        val vm = buildViewModel(
            locationClient = FakeLocationClient(securityFlow),
            rideDebugLogger = fakeLogger,
        )
        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(200L)

        assertTrue(
            "ERROR log expected after SecurityException",
            fakeLogger.logCalls.any { it.first == "ERROR" },
        )
        vm.onEvent(RecordingEvent.Pause)
    }

    // ── B17 — route name composition ─────────────────────────────────────────

    @Test
    fun `Finish online with geocoder area — route name contains area`() = runTest(testDispatcher) {
        val repo = FakeRouteRepository()
        // Emit a couple of GPS fixes so the path is non-empty and geocoding is attempted.
        val locationFlow = kotlinx.coroutines.flow.flowOf(
            LocationSample(lat = 53.43, lng = 14.55, speedMps = 16.0, altitudeM = 10.0, bearingDeg = 90f, timeMs = 1_000_000L),
            LocationSample(lat = 53.44, lng = 14.56, speedMps = 18.0, altitudeM = 12.0, bearingDeg = 90f, timeMs = 1_001_000L),
        )
        val vm = buildViewModel(
            online = true,
            offline = false,
            routeRepository = repo,
            reverseGeocoder = FakeReverseGeocoder(result = "Szczecin"),
            locationClient = FakeLocationClient(locationFlow),
        )
        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(200L) // allow location flow to emit into engine
        vm.onEvent(RecordingEvent.Finish)
        testDispatcher.scheduler.advanceUntilIdle()

        val name = repo.saved.firstOrNull()?.name ?: ""
        assertTrue("name should contain area 'Szczecin'", name.contains("Szczecin"))
    }

    @Test
    fun `Finish offline — route name does not include area`() = runTest(testDispatcher) {
        val repo = FakeRouteRepository()
        val vm = buildViewModel(
            online = false,
            offline = false,
            routeRepository = repo,
            reverseGeocoder = FakeReverseGeocoder(result = "Szczecin"),
        )
        vm.onEvent(RecordingEvent.Start)
        vm.onEvent(RecordingEvent.Finish)
        testDispatcher.scheduler.advanceUntilIdle()

        val name = repo.saved.firstOrNull()?.name ?: ""
        assertFalse("offline route name should not include area", name.contains("Szczecin"))
        assertTrue("offline route name should be non-blank", name.isNotBlank())
    }

    @Test
    fun `Finish online geocoder returns null — falls back to ride label only`() = runTest(testDispatcher) {
        val repo = FakeRouteRepository()
        val vm = buildViewModel(
            online = true,
            offline = false,
            routeRepository = repo,
            reverseGeocoder = FakeReverseGeocoder(result = null),
        )
        vm.onEvent(RecordingEvent.Start)
        vm.onEvent(RecordingEvent.Finish)
        testDispatcher.scheduler.advanceUntilIdle()

        val name = repo.saved.firstOrNull()?.name ?: ""
        assertTrue("null-geocoder route name should be non-blank", name.isNotBlank())
        assertFalse("null-geocoder route name should not contain en-dash separator", name.contains("–"))
    }

    @Test
    fun `Finish online geocoder throws — falls back to ride label only`() = runTest(testDispatcher) {
        val repo = FakeRouteRepository()
        val vm = buildViewModel(
            online = true,
            offline = false,
            routeRepository = repo,
            reverseGeocoder = FakeReverseGeocoder(shouldThrow = true),
        )
        vm.onEvent(RecordingEvent.Start)
        vm.onEvent(RecordingEvent.Finish)
        testDispatcher.scheduler.advanceUntilIdle()

        val name = repo.saved.firstOrNull()?.name ?: ""
        assertTrue("throwing-geocoder route name should be non-blank", name.isNotBlank())
        assertFalse("throwing-geocoder route name should not contain en-dash separator", name.contains("–"))
    }

    // ── Fill-to-full (E4) ────────────────────────────────────────────────────

    @Test
    fun `doStart threads current bike tankCapacityL into engine`() = runTest(testDispatcher) {
        val bike = Bike(
            id = "bike-1",
            name = "Test Bike",
            year = 2020,
            plate = "AB1234",
            status = BikeStatus.ACTIVE,
            tankCapacityL = 17.0,
            consumptionLper100km = 5.0,
        )
        val vm = buildViewModel(
            settings = AppSettings(currentBikeId = "bike-1"),
            bikeRepository = FakeBikeRepository(bikes = listOf(bike)),
        )
        // Advance time to allow init-block coroutines (combine flow) to run.
        advanceTimeBy(200L)

        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(200L)

        // Tank capacity should be present in metrics because the bike has it configured.
        assertNotNull(
            "tankCapacityL should be non-null when bike has it configured",
            vm.uiState.value.metrics.tankCapacityL,
        )
        assertEquals(17.0, vm.uiState.value.metrics.tankCapacityL!!, 0.001)
        vm.onEvent(RecordingEvent.Pause)
    }

    @Test
    fun `ShowRefuelDialog event sets showRefuelDialog=true pre-filled with tank capacity`() = runTest(testDispatcher) {
        val bike = Bike(
            id = "bike-1",
            name = "Test Bike",
            year = 2020,
            plate = "AB1234",
            status = BikeStatus.ACTIVE,
            tankCapacityL = 17.0,
            consumptionLper100km = 5.0,
        )
        val vm = buildViewModel(
            settings = AppSettings(currentBikeId = "bike-1"),
            bikeRepository = FakeBikeRepository(bikes = listOf(bike)),
        )
        advanceTimeBy(200L)
        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(100L)

        vm.onEvent(RecordingEvent.ShowRefuelDialog)
        advanceTimeBy(100L)

        assertTrue("showRefuelDialog should be true", vm.uiState.value.showRefuelDialog)
        assertEquals(17.0, vm.uiState.value.refuelDialogLitres, 0.001)
        vm.onEvent(RecordingEvent.Pause)
    }

    @Test
    fun `ShowRefuelDialog is no-op when bike has no tank capacity`() = runTest(testDispatcher) {
        val bike = Bike(
            id = "bike-1",
            name = "Test Bike",
            year = 2020,
            plate = "AB1234",
            status = BikeStatus.ACTIVE,
            tankCapacityL = null,
            consumptionLper100km = 5.0,
        )
        val vm = buildViewModel(
            settings = AppSettings(currentBikeId = "bike-1"),
            bikeRepository = FakeBikeRepository(bikes = listOf(bike)),
        )
        advanceTimeBy(200L)
        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(100L)

        vm.onEvent(RecordingEvent.ShowRefuelDialog)
        advanceTimeBy(100L)

        assertFalse("showRefuelDialog should remain false when tank capacity not set", vm.uiState.value.showRefuelDialog)
        vm.onEvent(RecordingEvent.Pause)
    }

    @Test
    fun `ConfirmRefuel logs FUEL tag and buffers PendingRefuel`() = runTest(testDispatcher) {
        val bike = Bike(
            id = "bike-1",
            name = "Test Bike",
            year = 2020,
            plate = "AB1234",
            status = BikeStatus.ACTIVE,
            tankCapacityL = 17.0,
            consumptionLper100km = 5.0,
        )
        val store = FakeRecordingSessionStore()
        val vm = buildViewModel(
            settings = AppSettings(currentBikeId = "bike-1"),
            bikeRepository = FakeBikeRepository(bikes = listOf(bike)),
            rideDebugLogger = fakeLogger,
            sessionStore = store,
            fixedTimeMs = 42_000L,
        )
        advanceTimeBy(200L)
        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(100L)
        val savesBefore = store.saveCalls.size

        vm.onEvent(RecordingEvent.ConfirmRefuel(litres = 15.0, pricePerL = 7.50))
        advanceTimeBy(100L)

        // FUEL tag logged
        assertTrue(
            "FUEL log expected after ConfirmRefuel",
            fakeLogger.logCalls.any { it.first == "FUEL" && it.second.contains("refuel") },
        )
        // dialog dismissed
        assertFalse("showRefuelDialog should be false after confirm", vm.uiState.value.showRefuelDialog)
        // snapshot persisted with pending refuels
        assertTrue("ConfirmRefuel should persist snapshot", store.saveCalls.size > savesBefore)
        val latestSnap = store.saveCalls.last()
        assertEquals(1, latestSnap.pendingRefuels.size)
        assertEquals(15.0, latestSnap.pendingRefuels[0].litres, 0.001)
        assertEquals(7.50, latestSnap.pendingRefuels[0].pricePerL, 0.001)
        assertEquals(42_000L, latestSnap.pendingRefuels[0].epochMs)
        vm.onEvent(RecordingEvent.Pause)
    }

    @Test
    fun `ConfirmRefuel resets distanceSinceFullKm via engine fillToFull`() = runTest(testDispatcher) {
        val bike = Bike(
            id = "bike-1",
            name = "Test Bike",
            year = 2020,
            plate = "AB1234",
            status = BikeStatus.ACTIVE,
            tankCapacityL = 17.0,
            consumptionLper100km = 5.0,
        )
        val locationFlow = kotlinx.coroutines.flow.flowOf(
            LocationSample(lat = 0.0, lng = 0.0, speedMps = 10.0, altitudeM = 0.0, bearingDeg = 0f, timeMs = 1000L),
            LocationSample(lat = 1.0, lng = 0.0, speedMps = 10.0, altitudeM = 0.0, bearingDeg = 0f, timeMs = 2000L),
        )
        val vm = buildViewModel(
            settings = AppSettings(currentBikeId = "bike-1"),
            bikeRepository = FakeBikeRepository(bikes = listOf(bike)),
            locationClient = FakeLocationClient(locationFlow),
        )
        advanceTimeBy(200L)
        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(300L)

        val distanceBeforeFill = vm.uiState.value.metrics.distanceSinceFullKm
        assertTrue("distanceSinceFullKm should be > 0 after driving", distanceBeforeFill > 0.0)

        vm.onEvent(RecordingEvent.ConfirmRefuel(litres = 17.0, pricePerL = 7.0))
        advanceTimeBy(100L)

        assertEquals(0.0, vm.uiState.value.metrics.distanceSinceFullKm, 0.001)
        vm.onEvent(RecordingEvent.Pause)
    }

    @Test
    fun `on Finish buffered ConfirmRefuel events are persisted to RefuelRepository`() = runTest(testDispatcher) {
        val bike = Bike(
            id = "bike-1",
            name = "Test Bike",
            year = 2020,
            plate = "AB1234",
            status = BikeStatus.ACTIVE,
            tankCapacityL = 17.0,
            consumptionLper100km = 5.0,
        )
        val fakeRefuelRepo = FakeRefuelRepository()
        val vm = buildViewModel(
            settings = AppSettings(currentBikeId = "bike-1"),
            bikeRepository = FakeBikeRepository(bikes = listOf(bike)),
            refuelRepository = fakeRefuelRepo,
            fixedTimeMs = 99_000L,
        )
        advanceTimeBy(200L)
        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(100L)

        vm.onEvent(RecordingEvent.ConfirmRefuel(litres = 12.0, pricePerL = 6.80))
        vm.onEvent(RecordingEvent.ConfirmRefuel(litres = 15.5, pricePerL = 6.90))
        advanceTimeBy(100L)

        vm.onEvent(RecordingEvent.Finish)
        advanceTimeBy(500L)

        assertEquals(2, fakeRefuelRepo.added.size)
        assertEquals(12.0, fakeRefuelRepo.added[0].litres, 0.001)
        assertEquals(15.5, fakeRefuelRepo.added[1].litres, 0.001)
        // Both events share the saved route id
        val routeId = routeRepo.saved.firstOrNull()?.id
        assertNotNull("route should have been saved", routeId)
        fakeRefuelRepo.added.forEach { assertEquals(routeId, it.routeId) }
    }

    @Test
    fun `DismissRefuelDialog sets showRefuelDialog=false`() = runTest(testDispatcher) {
        val bike = Bike(
            id = "bike-1",
            name = "Test Bike",
            year = 2020,
            plate = "AB1234",
            status = BikeStatus.ACTIVE,
            tankCapacityL = 17.0,
            consumptionLper100km = 5.0,
        )
        val vm = buildViewModel(
            settings = AppSettings(currentBikeId = "bike-1"),
            bikeRepository = FakeBikeRepository(bikes = listOf(bike)),
        )
        advanceTimeBy(200L)
        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(100L)

        vm.onEvent(RecordingEvent.ShowRefuelDialog)
        advanceTimeBy(50L)
        assertTrue(vm.uiState.value.showRefuelDialog)

        vm.onEvent(RecordingEvent.DismissRefuelDialog)
        advanceTimeBy(50L)
        assertFalse("showRefuelDialog should be false after dismiss", vm.uiState.value.showRefuelDialog)
        vm.onEvent(RecordingEvent.Pause)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    // ── F2 — live compass + lean in Idle ────────────────────────────────────

    @Test
    fun `heading sensor updates liveHeadingDeg in Idle without starting recording`() =
        runTest(testDispatcher) {
            val vm = buildViewModel(
                headingSensorSource = FakeHeadingSensorSource(kotlinx.coroutines.flow.flowOf(135f)),
            )
            advanceTimeBy(200L)

            assertEquals(
                "liveHeadingDeg should be updated from sensor in Idle",
                RecordingPhase.Idle,
                vm.uiState.value.phase,
            )
            assertEquals(135f, vm.uiState.value.liveHeadingDeg ?: -1f, 0.01f)
        }

    @Test
    fun `lean sensor updates liveLeanDeg in Idle without calling engine`() =
        runTest(testDispatcher) {
            val vm = buildViewModel(
                leanSensorSource = FakeLeanSensorSource(kotlinx.coroutines.flow.flowOf(22.5)),
            )
            advanceTimeBy(200L)

            assertEquals(RecordingPhase.Idle, vm.uiState.value.phase)
            assertEquals(22.5, vm.uiState.value.liveLeanDeg ?: -1.0, 0.001)
            // Engine/metrics should not be affected in Idle
            assertEquals(0.0, vm.uiState.value.metrics.currentLeanDeg, 0.0)
        }

    @Test
    fun `lean sensor reaches engine while Recording`() = runTest(testDispatcher) {
        val vm = buildViewModel(
            leanSensorSource = FakeLeanSensorSource(kotlinx.coroutines.flow.flowOf(30.0)),
        )
        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(200L)

        assertEquals(RecordingPhase.Recording, vm.uiState.value.phase)
        // Engine should have received the lean angle → currentLeanDeg updated
        assertEquals(30.0, vm.uiState.value.metrics.currentLeanDeg, 0.001)
        vm.onEvent(RecordingEvent.Pause)
    }

    @Test
    fun `heading sensor does not affect engine metrics`() = runTest(testDispatcher) {
        val vm = buildViewModel(
            headingSensorSource = FakeHeadingSensorSource(kotlinx.coroutines.flow.flowOf(180f)),
        )
        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(200L)

        // headingDeg in metrics comes from GPS (engine), not from headingSensorSource
        assertEquals(0f, vm.uiState.value.metrics.headingDeg, 0.01f)
        vm.onEvent(RecordingEvent.Pause)
    }

    @Test
    fun `lean sensor logs LEAN tag only while Recording not in Idle`() =
        runTest(testDispatcher) {
            val logger = FakeRideDebugLogger()
            val vm = buildViewModel(
                leanSensorSource = FakeLeanSensorSource(kotlinx.coroutines.flow.flowOf(10.0)),
                rideDebugLogger = logger,
            )
            // Do NOT start recording — remains Idle
            advanceTimeBy(200L)

            assertFalse(
                "LEAN tag should not be logged in Idle",
                logger.logCalls.any { it.first == "LEAN" },
            )
        }

    // ── G2 — fuel price per litre + currency in uiState ─────────────────────

    @Test
    fun `fuelPricePerL is populated from current bike when price is configured`() =
        runTest(testDispatcher) {
            val bike = Bike(
                id = "bike-fuel",
                name = "MT-07",
                year = 2022,
                plate = "WA 001",
                status = BikeStatus.ACTIVE,
                fuelPricePerL = 6.89,
            )
            val vm = buildViewModel(
                settings = AppSettings(currentBikeId = "bike-fuel"),
                bikeRepository = FakeBikeRepository(bikes = listOf(bike)),
            )
            advanceTimeBy(200L)

            assertEquals(
                "fuelPricePerL should be 6.89 when bike has it configured",
                6.89,
                vm.uiState.value.fuelPricePerL ?: -1.0,
                0.001,
            )
        }

    @Test
    fun `fuelPricePerL is null when current bike has no price configured`() =
        runTest(testDispatcher) {
            val bike = Bike(
                id = "bike-no-price",
                name = "CB500F",
                year = 2019,
                plate = "KR 001",
                status = BikeStatus.ACTIVE,
                fuelPricePerL = null,
            )
            val vm = buildViewModel(
                settings = AppSettings(currentBikeId = "bike-no-price"),
                bikeRepository = FakeBikeRepository(bikes = listOf(bike)),
            )
            advanceTimeBy(200L)

            assertTrue(
                "fuelPricePerL should be null when bike has no price",
                vm.uiState.value.fuelPricePerL == null,
            )
        }

    @Test
    fun `currency is populated from settings`() = runTest(testDispatcher) {
        val vm = buildViewModel(
            settings = AppSettings(currency = "EUR"),
        )
        advanceTimeBy(200L)

        assertEquals("currency should reflect settings value", "EUR", vm.uiState.value.currency)
    }

    @Test
    fun `currency defaults to PLN when not overridden`() = runTest(testDispatcher) {
        val vm = buildViewModel(
            settings = AppSettings(), // default currency = "PLN"
        )
        advanceTimeBy(200L)

        assertEquals("default currency should be PLN", "PLN", vm.uiState.value.currency)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildViewModel(
        online: Boolean = true,
        offline: Boolean = false,
        offlineOnly: Boolean = false,
        settings: AppSettings = AppSettings(offline = offline, offlineOnly = offlineOnly),
        routeRepository: RouteRepository = routeRepo,
        fixedTimeMs: Long = 1_000_000L,
        locationClient: LocationClient = FakeLocationClient(),
        leanSensorSource: LeanSensorSource = FakeLeanSensorSource(),
        headingSensorSource: HeadingSensorSource = FakeHeadingSensorSource(),
        rideDebugLogger: RideDebugLogger = fakeLogger,
        reverseGeocoder: ReverseGeocoder = FakeReverseGeocoder(),
        stringResolver: StringResolver = FakeStringResolver(),
        sessionStore: RecordingSessionStore = FakeRecordingSessionStore(),
        bikeRepository: BikeRepository = FakeBikeRepository(),
        refuelRepository: RefuelRepository = FakeRefuelRepository(),
    ) = RecordingViewModel(
        locationClient = locationClient,
        leanSensorSource = leanSensorSource,
        headingSensorSource = headingSensorSource,
        routeRepository = routeRepository,
        syncRepository = syncRepo,
        settingsSource = FakeSettingsSource(settings),
        bikeRepository = bikeRepository,
        networkMonitor = FakeNetworkMonitor(isOnline = online),
        timeProvider = FakeTimeProvider(fixedTimeMs),
        carBridge = com.mototracker.car.CarRecordingBridge(),
        rideDebugLogger = rideDebugLogger,
        reverseGeocoder = reverseGeocoder,
        stringResolver = stringResolver,
        sessionStore = sessionStore,
        refuelRepository = refuelRepository,
    )
}
