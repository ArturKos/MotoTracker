package com.mototracker.ui.screens.record

import app.cash.turbine.test
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
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.sensor.LeanSensorSource
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.domain.recording.LocationSample
import com.mototracker.domain.recording.RecordingEngine
import com.mototracker.domain.recording.RecordingEngineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class FakeSessionStore(
    initialSnapshot: ActiveSessionSnapshot? = null,
) : RecordingSessionStore {
    val saveCalls = mutableListOf<ActiveSessionSnapshot>()
    var clearCount = 0
    private val _flow = MutableStateFlow(initialSnapshot)

    override val snapshot: Flow<ActiveSessionSnapshot?> = _flow
    override suspend fun save(s: ActiveSessionSnapshot) { saveCalls += s; _flow.value = s }
    override suspend fun clear() { clearCount++; _flow.value = null }
}

private class FakeResumeLocationClient : LocationClient {
    override fun locationUpdates(intervalMs: Long): Flow<LocationSample> = flow {}
}

private class FakeResumeLeanSource : LeanSensorSource {
    override val leanAngles: Flow<Double> = flow {}
}

private class FakeResumeRouteRepository : RouteRepository {
    val saved = mutableListOf<Route>()
    private val allFlow = MutableStateFlow<List<Route>>(emptyList())
    override suspend fun save(route: Route) { saved += route; allFlow.value = saved.toList() }
    override fun observeAll(): Flow<List<Route>> = allFlow
    override suspend fun getById(id: String): Route? = saved.find { it.id == id }
    override fun observeById(id: String): Flow<Route?> = MutableStateFlow(saved.find { it.id == id })
    override suspend fun clearCorrectedTrace(id: String) {}
    override suspend fun deleteAll() { saved.clear(); allFlow.value = emptyList() }
    override suspend fun rename(id: String, name: String) {}
    override suspend fun setBike(routeId: String, bikeId: String?) {}
}

private class FakeResumeSyncRepository : SyncRepository {
    override val pendingCount: Flow<Int> = MutableStateFlow(0)
    override suspend fun enqueue(routeId: String) {}
    override suspend fun syncNow(): Int = 0
    override fun start(scope: CoroutineScope) = Unit
}

private class FakeResumeSettingsSource(
    settings: AppSettings = AppSettings(),
) : AppSettingsSource {
    override val settings: Flow<AppSettings> = MutableStateFlow(settings)
}

private class FakeResumeNetworkMonitor : NetworkMonitor {
    override val isOnline: Flow<Boolean> = MutableStateFlow(true)
}

private class FakeResumeTimeProvider : TimeProvider {
    override fun nowEpochMs(): Long = 1_000_000L
}

private class FakeResumeLogger : RideDebugLogger {
    override fun beginRide() {}
    override fun endRide() {}
    override fun log(tag: String, message: String) {}
}

private class FakeResumeGeocoder : ReverseGeocoder {
    override suspend fun areaName(lat: Double, lng: Double): String? = null
}

private class FakeResumeStringResolver : StringResolver {
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

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Builds a non-empty [RecordingEngineState] with two GPS fixes for testing. */
private fun testEngineState(): RecordingEngineState {
    val engine = RecordingEngine()
    engine.onLocation(LocationSample(52.0, 21.0, 10.0, 100.0, 90f, 1000L))
    engine.onLocation(LocationSample(52.01, 21.01, 12.0, 105.0, 92f, 2000L))
    engine.onLean(20.0)
    engine.tick(30L)
    return engine.exportState()
}

private fun testSnapshot(paused: Boolean = false) = ActiveSessionSnapshot(
    engineState = testEngineState(),
    recordingStartMs = 1_700_000_000L,
    bikeId = "bike-test",
    paused = paused,
)

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModelResumeTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm(
        store: RecordingSessionStore = FakeSessionStore(),
        routeRepo: FakeResumeRouteRepository = FakeResumeRouteRepository(),
    ) = RecordingViewModel(
        locationClient = FakeResumeLocationClient(),
        leanSensorSource = FakeResumeLeanSource(),
        routeRepository = routeRepo,
        syncRepository = FakeResumeSyncRepository(),
        settingsSource = FakeResumeSettingsSource(),
        networkMonitor = FakeResumeNetworkMonitor(),
        timeProvider = FakeResumeTimeProvider(),
        carBridge = CarRecordingBridge(),
        rideDebugLogger = FakeResumeLogger(),
        reverseGeocoder = FakeResumeGeocoder(),
        stringResolver = FakeResumeStringResolver(),
        sessionStore = store,
    )

    // ── Startup detection ────────────────────────────────────────────────────

    @Test
    fun `init with stored snapshot exposes resumableSession and stays Idle`() = runTest(testDispatcher) {
        val snap = testSnapshot()
        val store = FakeSessionStore(initialSnapshot = snap)
        val vm = buildVm(store = store)

        // Let the init coroutine that reads the snapshot run.
        testDispatcher.scheduler.runCurrent()

        assertNotNull("resumableSession should be non-null", vm.uiState.value.resumableSession)
        assertEquals(RecordingPhase.Idle, vm.uiState.value.phase)
    }

    @Test
    fun `init with no stored snapshot leaves resumableSession null`() = runTest(testDispatcher) {
        val vm = buildVm(store = FakeSessionStore(initialSnapshot = null))
        testDispatcher.scheduler.runCurrent()

        assertNull(vm.uiState.value.resumableSession)
        assertEquals(RecordingPhase.Idle, vm.uiState.value.phase)
    }

    // ── ResumeSession ────────────────────────────────────────────────────────

    @Test
    fun `ResumeSession sets phase to Paused and clears resumableSession`() = runTest(testDispatcher) {
        val snap = testSnapshot()
        val vm = buildVm(store = FakeSessionStore(initialSnapshot = snap))
        testDispatcher.scheduler.runCurrent()

        vm.onEvent(RecordingEvent.ResumeSession)

        assertEquals(RecordingPhase.Paused, vm.uiState.value.phase)
        assertNull(vm.uiState.value.resumableSession)
    }

    @Test
    fun `ResumeSession restores metrics from the snapshot`() = runTest(testDispatcher) {
        val snap = testSnapshot()
        val vm = buildVm(store = FakeSessionStore(initialSnapshot = snap))
        testDispatcher.scheduler.runCurrent()

        vm.onEvent(RecordingEvent.ResumeSession)

        val metrics = vm.uiState.value.metrics
        assertEquals(snap.engineState.distanceKm, metrics.distanceKm, 0.0001)
        assertEquals(snap.engineState.durationSec, metrics.durationSec)
        assertEquals(snap.engineState.maxSpeedKmh, metrics.maxSpeedKmh, 0.001)
        assertEquals(snap.engineState.maxLeanDeg, metrics.maxLeanDeg, 0.0)
    }

    @Test
    fun `ResumeSession rebuilds trackPoints from engineState pathPoints`() = runTest(testDispatcher) {
        val snap = testSnapshot()
        val vm = buildVm(store = FakeSessionStore(initialSnapshot = snap))
        testDispatcher.scheduler.runCurrent()

        vm.onEvent(RecordingEvent.ResumeSession)

        val trackPoints = vm.uiState.value.trackPoints
        assertEquals(snap.engineState.pathPoints.size, trackPoints.size)
        assertEquals(snap.engineState.pathPoints[0].first, trackPoints[0].lat, 0.0)
        assertEquals(snap.engineState.pathPoints[0].second, trackPoints[0].lon, 0.0)
    }

    @Test
    fun `ResumeSession does not start ticker — phase stays Paused after time passes`() =
        runTest(testDispatcher) {
            val snap = testSnapshot()
            val vm = buildVm(store = FakeSessionStore(initialSnapshot = snap))
            testDispatcher.scheduler.runCurrent()

            vm.onEvent(RecordingEvent.ResumeSession)
            val durationAfterResume = vm.uiState.value.metrics.durationSec

            advanceTimeBy(3_000L) // ticker should NOT be running in Paused

            assertEquals(
                "durationSec should not advance while Paused",
                durationAfterResume,
                vm.uiState.value.metrics.durationSec,
            )
        }

    @Test
    fun `ResumeSession followed by Resume starts ticker`() = runTest(testDispatcher) {
        val snap = testSnapshot()
        val vm = buildVm(store = FakeSessionStore(initialSnapshot = snap))
        testDispatcher.scheduler.runCurrent()

        vm.onEvent(RecordingEvent.ResumeSession)
        val before = vm.uiState.value.metrics.durationSec

        vm.onEvent(RecordingEvent.Resume)
        advanceTimeBy(2_100L)

        assertTrue(
            "durationSec should advance after Resume",
            vm.uiState.value.metrics.durationSec > before,
        )
        vm.onEvent(RecordingEvent.Pause) // clean up ticker
    }

    // ── DiscardSession ───────────────────────────────────────────────────────

    @Test
    fun `DiscardSession clears the store`() = runTest(testDispatcher) {
        val store = FakeSessionStore(initialSnapshot = testSnapshot())
        val vm = buildVm(store = store)
        testDispatcher.scheduler.runCurrent()

        vm.onEvent(RecordingEvent.DiscardSession)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("store.clear() should be called", store.clearCount >= 1)
    }

    @Test
    fun `DiscardSession clears resumableSession in UI state`() = runTest(testDispatcher) {
        val store = FakeSessionStore(initialSnapshot = testSnapshot())
        val vm = buildVm(store = store)
        testDispatcher.scheduler.runCurrent()

        vm.onEvent(RecordingEvent.DiscardSession)

        assertNull(vm.uiState.value.resumableSession)
    }

    @Test
    fun `DiscardSession keeps phase Idle`() = runTest(testDispatcher) {
        val vm = buildVm(store = FakeSessionStore(initialSnapshot = testSnapshot()))
        testDispatcher.scheduler.runCurrent()

        vm.onEvent(RecordingEvent.DiscardSession)

        assertEquals(RecordingPhase.Idle, vm.uiState.value.phase)
    }

    // ── doFinish clears store ────────────────────────────────────────────────

    @Test
    fun `Finish clears the session store after route is saved`() = runTest(testDispatcher) {
        val store = FakeSessionStore()
        val routeRepo = FakeResumeRouteRepository()
        val vm = buildVm(store = store, routeRepo = routeRepo)

        vm.onEvent(RecordingEvent.Start)
        advanceTimeBy(200L)
        vm.onEvent(RecordingEvent.Finish)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("route should be saved before clearing", routeRepo.saved.isNotEmpty())
        assertTrue("store.clear() should be called on Finish", store.clearCount >= 1)
    }

    @Test
    fun `Finish transitions to Idle after clearing store`() = runTest(testDispatcher) {
        val store = FakeSessionStore()
        val vm = buildVm(store = store)

        vm.onEvent(RecordingEvent.Start)
        vm.onEvent(RecordingEvent.Finish)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(RecordingPhase.Idle, vm.uiState.value.phase)
    }

    // ── Snapshot persistence ─────────────────────────────────────────────────

    @Test
    fun `doPause persists snapshot with paused=true`() = runTest(testDispatcher) {
        val store = FakeSessionStore()
        val vm = buildVm(store = store)

        vm.onEvent(RecordingEvent.Start)
        vm.onEvent(RecordingEvent.Pause)
        testDispatcher.scheduler.advanceUntilIdle()

        val lastSaved = store.saveCalls.lastOrNull { it.paused }
        assertNotNull("A snapshot with paused=true should be saved on Pause", lastSaved)
    }

    @Test
    fun `doResume persists snapshot with paused=false`() = runTest(testDispatcher) {
        val store = FakeSessionStore()
        val vm = buildVm(store = store)

        vm.onEvent(RecordingEvent.Start)
        vm.onEvent(RecordingEvent.Pause)
        testDispatcher.scheduler.advanceUntilIdle()
        val countAfterPause = store.saveCalls.size

        vm.onEvent(RecordingEvent.Resume)
        // Use runCurrent() rather than advanceUntilIdle(): the ticker's while(true)+delay(1s)
        // loop would make advanceUntilIdle() loop forever. runCurrent() runs only tasks that
        // are currently runnable (no delay), which includes the sessionStore.save() coroutine.
        testDispatcher.scheduler.runCurrent()

        val resumeSnapshot = store.saveCalls.subList(countAfterPause, store.saveCalls.size)
            .lastOrNull { !it.paused }
        assertNotNull("A snapshot with paused=false should be saved on Resume", resumeSnapshot)
        vm.onEvent(RecordingEvent.Pause) // stop ticker
    }

    // ── resumableSession exposed as Flow ─────────────────────────────────────

    @Test
    fun `uiState emits resumableSession when store has a snapshot on init`() =
        runTest(testDispatcher) {
            val snap = testSnapshot()
            val vm = buildVm(store = FakeSessionStore(initialSnapshot = snap))

            // Let init coroutines run AND dispatch the StateFlow update to collectors.
            // advanceTimeBy advances virtual time which both executes pending coroutines
            // and allows any resulting state-flow emissions to propagate.
            advanceTimeBy(1L)

            assertNotNull(
                "resumableSession should be non-null after init coroutine runs",
                vm.uiState.value.resumableSession,
            )
            assertEquals(RecordingPhase.Idle, vm.uiState.value.phase)
        }
}
