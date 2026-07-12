package com.mototracker.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.model.Wave
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.GpsCorrectionRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.repository.WaveRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class FakeRouteRepository(stored: Route? = null) : RouteRepository {
    private val _flow = MutableStateFlow(stored)

    /** Push a new route value (used by tests to simulate DB updates). */
    fun emit(route: Route?) { _flow.value = route }

    override suspend fun save(route: Route) { _flow.value = route }
    override fun observeAll(): Flow<List<Route>> = _flow.map { listOfNotNull(it) }
    override suspend fun getById(id: String): Route? = _flow.value?.takeIf { it.id == id }

    /** Live stream — re-emits whenever [emit] changes the stored route. */
    override fun observeById(id: String): Flow<Route?> = _flow.map { if (it?.id == id) it else null }

    /**
     * Clears the corrected trace fields on the stored route, mirroring what Room does.
     */
    override suspend fun clearCorrectedTrace(id: String) {
        val r = _flow.value?.takeIf { it.id == id } ?: return
        _flow.value = r.copy(
            correctedPathJson = null,
            correctionStatus = CorrectionStatus.NONE,
            confidence = null,
        )
    }
    override suspend fun deleteAll() { _flow.value = null }
}

private class FakeBikeRepository(bikes: List<Bike> = emptyList()) : BikeRepository {
    private val _flow = MutableStateFlow(bikes)
    fun emit(bikes: List<Bike>) { _flow.value = bikes }
    override fun observeAll(): Flow<List<Bike>> = _flow
    override suspend fun addBike(bike: Bike) { _flow.value = _flow.value + bike }
    override suspend fun deleteAll() { _flow.value = emptyList() }
}

private class FakeWaveRepository(waves: List<Wave> = emptyList()) : WaveRepository {
    private val _flow = MutableStateFlow(waves)
    fun emit(waves: List<Wave>) { _flow.value = waves }
    override fun observeForRoute(routeId: String): Flow<List<Wave>> = _flow
    override fun observeAll(): Flow<List<Wave>> = _flow
}

private class FakeSettingsSource(settings: AppSettings = AppSettings()) : AppSettingsSource {
    private val _flow = MutableStateFlow(settings)
    fun emit(settings: AppSettings) { _flow.value = settings }
    override val settings: Flow<AppSettings> = _flow
}

private class FakeSyncRepository : SyncRepository {
    val enqueuedIds = mutableListOf<String>()
    override val pendingCount: Flow<Int> = MutableStateFlow(0)
    override suspend fun enqueue(routeId: String) { enqueuedIds += routeId }
    override suspend fun syncNow(): Int = 0
    override fun start(scope: CoroutineScope) { /* no-op */ }
}

private class FakeGpsCorrectionRepository : GpsCorrectionRepository {
    val enqueuedIds = mutableListOf<String>()
    var correctNowCallCount = 0

    override val pendingCount: Flow<Int> = MutableStateFlow(0)

    override suspend fun enqueue(routeId: String) { enqueuedIds += routeId }

    override suspend fun correctNow(): Int {
        correctNowCallCount++
        return 0
    }

    override fun start(scope: CoroutineScope) { /* no-op */ }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private const val RAW_PATH = """[{"lat":50.0,"lng":20.0},{"lat":50.1,"lng":20.1}]"""
private const val CORRECTED_PATH = """[{"lat":50.05,"lng":20.05},{"lat":50.15,"lng":20.15}]"""

private fun makeRoute(
    id: String = "route-1",
    name: String = "Morning Ride",
    km: Double = 100.0,
    durSec: Long = 3600L,
    avg: Double = 80.0,
    max: Double = 140.0,
    lean: Double = 25.0,
    elev: Double = 500.0,
    fuel: Double = 5.0,
    synced: Boolean = true,
    bikeId: String? = null,
    wxJson: String? = null,
    speedJson: String? = null,
    elevProfileJson: String? = null,
    pathJson: String? = null,
    correctedPathJson: String? = null,
    correctionStatus: CorrectionStatus = CorrectionStatus.NONE,
    confidence: Double? = null,
): Route = Route(
    id = id, name = name, dateEpochMs = 1_700_000_000_000L,
    bikeId = bikeId, km = km, durSec = durSec, avg = avg, max = max,
    lean = lean, elev = elev, fuel = fuel, synced = synced,
    wxJson = wxJson, pathJson = pathJson, speedJson = speedJson,
    elevProfileJson = elevProfileJson, notes = null,
    correctedPathJson = correctedPathJson,
    correctionStatus = correctionStatus,
    confidence = confidence,
)

private fun makeBike(
    id: String, name: String, status: BikeStatus = BikeStatus.ACTIVE,
): Bike = Bike(id = id, name = name, year = 2020, plate = "AB1234", status = status)

private fun makeWave(
    id: String = "w1", routeId: String = "route-1",
    nick: String = "Alice", bikeName: String = "R1", place: String = "Market", timeLabel: String = "14:00",
): Wave = Wave(id = id, nick = nick, bikeName = bikeName, place = place, timeLabel = timeLabel, routeId = routeId)

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class RouteDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeSyncRepo = FakeSyncRepository()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun buildVm(
        routeId: String = "route-1",
        route: Route? = makeRoute(id = routeId),
        bikes: List<Bike> = emptyList(),
        waves: List<Wave> = emptyList(),
        settings: AppSettings = AppSettings(),
        syncRepo: FakeSyncRepository = fakeSyncRepo,
        correctionRepo: FakeGpsCorrectionRepository = FakeGpsCorrectionRepository(),
        fakeRouteRepo: FakeRouteRepository? = null,
    ): RouteDetailViewModel {
        val routeRepo = fakeRouteRepo ?: FakeRouteRepository(stored = route)
        return RouteDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("routeId" to routeId)),
            routeRepository = routeRepo,
            bikeRepository = FakeBikeRepository(bikes),
            waveRepository = FakeWaveRepository(waves),
            settingsSource = FakeSettingsSource(settings),
            syncRepository = syncRepo,
            gpsCorrectionRepository = correctionRepo,
        )
    }

    // ── initial loading state ─────────────────────────────────────────────────

    @Test
    fun `initial state has loading=true`() {
        val vm = buildVm()
        assertTrue(vm.uiState.value.loading)
    }

    // ── route not found ───────────────────────────────────────────────────────

    @Test
    fun `routeNotFound is true when no route with given id exists`() = runTest {
        val vm = buildVm(routeId = "nonexistent", route = null)
        vm.uiState.test {
            val state = awaitItem()
            val finalState = if (state.loading) awaitItem() else state
            assertTrue(finalState.routeNotFound)
            assertFalse(finalState.loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── route found: basic fields ─────────────────────────────────────────────

    @Test
    fun `name is route name when non-blank`() = runTest {
        val vm = buildVm(route = makeRoute(id = "route-1", name = "Alpine Tour"))
        vm.uiState.test {
            val state = skipToLoaded()
            assertEquals("Alpine Tour", state.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `name falls back to first 8 chars of id when route name is blank`() = runTest {
        val vm = buildVm(routeId = "abc12345-extra", route = makeRoute(id = "abc12345-extra", name = ""))
        vm.uiState.test {
            val state = skipToLoaded()
            assertEquals("abc12345", state.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dateDisplay is non-blank for a valid epoch`() = runTest {
        val vm = buildVm()
        vm.uiState.test {
            val state = skipToLoaded()
            assertTrue(state.dateDisplay.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── stat tiles ────────────────────────────────────────────────────────────

    @Test
    fun `distanceTile value matches route km`() = runTest {
        val vm = buildVm(route = makeRoute(km = 128.4))
        vm.uiState.test {
            val state = skipToLoaded()
            assertEquals("128.4", state.distanceTile.value)
            assertEquals("km", state.distanceTile.unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `durationTile value is formatted as hms`() = runTest {
        val vm = buildVm(route = makeRoute(durSec = 7653L)) // 2h 7m 33s
        vm.uiState.test {
            val state = skipToLoaded()
            assertEquals("2:07:33", state.durationTile.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `leanTile value is rounded integer`() = runTest {
        val vm = buildVm(route = makeRoute(lean = 32.7))
        vm.uiState.test {
            val state = skipToLoaded()
            assertEquals("33", state.leanTile.value)
            assertEquals("°", state.leanTile.unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fuelTile shows one decimal place`() = runTest {
        val vm = buildVm(route = makeRoute(fuel = 4.3))
        vm.uiState.test {
            val state = skipToLoaded()
            assertEquals("4.3", state.fuelTile.value)
            assertEquals("L", state.fuelTile.unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── queued flag ───────────────────────────────────────────────────────────

    @Test
    fun `queued is false when route is synced`() = runTest {
        val vm = buildVm(route = makeRoute(synced = true))
        vm.uiState.test {
            assertFalse(skipToLoaded().queued)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `queued is true when route is not synced`() = runTest {
        val vm = buildVm(route = makeRoute(synced = false))
        vm.uiState.test {
            assertTrue(skipToLoaded().queued)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── bike resolution ───────────────────────────────────────────────────────

    @Test
    fun `bikeName is dash when bikeId is null`() = runTest {
        val vm = buildVm(route = makeRoute(bikeId = null))
        vm.uiState.test {
            assertEquals("—", skipToLoaded().bikeName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bikeName resolves from bike list`() = runTest {
        val vm = buildVm(
            route = makeRoute(bikeId = "bike-1"),
            bikes = listOf(makeBike("bike-1", "Yamaha MT-07")),
        )
        vm.uiState.test {
            assertEquals("Yamaha MT-07", skipToLoaded().bikeName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bikeSold is true for SOLD bike`() = runTest {
        val vm = buildVm(
            route = makeRoute(bikeId = "b-old"),
            bikes = listOf(makeBike("b-old", "Old Bike", BikeStatus.SOLD)),
        )
        vm.uiState.test {
            assertTrue(skipToLoaded().bikeSold)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── weather ───────────────────────────────────────────────────────────────

    @Test
    fun `weather is offline when wxJson is null`() = runTest {
        val vm = buildVm(route = makeRoute(wxJson = null))
        vm.uiState.test {
            assertTrue(skipToLoaded().weather.offline)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `weather is online with correct fields when wxJson is valid`() = runTest {
        val vm = buildVm(route = makeRoute(wxJson = """{"temp":22,"hum":60,"rain":false}"""))
        vm.uiState.test {
            val weather = skipToLoaded().weather
            assertFalse(weather.offline)
            assertEquals("22°C", weather.tempDisplay)
            assertEquals("60%", weather.humDisplay)
            assertEquals(false, weather.rain)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── meetups ───────────────────────────────────────────────────────────────

    @Test
    fun `meetingsNone is true when no waves`() = runTest {
        val vm = buildVm(waves = emptyList())
        vm.uiState.test {
            assertTrue(skipToLoaded().meetingsNone)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `meetingsNone is false when waves exist`() = runTest {
        val vm = buildVm(waves = listOf(makeWave()))
        vm.uiState.test {
            assertFalse(skipToLoaded().meetingsNone)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `meetings maps wave fields to MeetingUi`() = runTest {
        val vm = buildVm(
            waves = listOf(makeWave(nick = "Jan", bikeName = "MT-09", place = "Rynek", timeLabel = "10:30")),
        )
        vm.uiState.test {
            val meeting = skipToLoaded().meetings.first()
            assertEquals("JA", meeting.initials)
            assertEquals("Jan", meeting.who)
            assertEquals("MT-09", meeting.bikeName)
            assertEquals("Rynek", meeting.place)
            assertEquals("10:30", meeting.timeLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── chart data ────────────────────────────────────────────────────────────

    @Test
    fun `speedStroke is non-empty for valid speedJson`() = runTest {
        val json = """[{"t":0,"v":60},{"t":60,"v":120}]"""
        val vm = buildVm(route = makeRoute(speedJson = json))
        vm.uiState.test {
            assertTrue(skipToLoaded().speedStroke.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `speedStroke is empty for null speedJson`() = runTest {
        val vm = buildVm(route = makeRoute(speedJson = null))
        vm.uiState.test {
            assertTrue(skipToLoaded().speedStroke.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `elevStroke is non-empty for valid elevProfileJson`() = runTest {
        val json = """[{"d":0,"a":200},{"d":10,"a":500}]"""
        val vm = buildVm(route = makeRoute(elevProfileJson = json))
        vm.uiState.test {
            assertTrue(skipToLoaded().elevStroke.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── exportGpx event ───────────────────────────────────────────────────────

    @Test
    fun `exportGpx emits GpxSaved with non-blank content and expected fileName`() = runTest {
        val route = makeRoute(id = "route-abc123", name = "Morning Ride")
        val vm = buildVm(routeId = route.id, route = route)
        vm.uiState.test {
            skipToLoaded()
            cancelAndIgnoreRemainingEvents()
        }
        vm.events.test {
            vm.exportGpx()
            val event = awaitItem()
            assertTrue("Event must be GpxSaved", event is RouteDetailEvent.GpxSaved)
            val gpxEvent = event as RouteDetailEvent.GpxSaved
            assertTrue("GPX content must not be blank", gpxEvent.content.isNotBlank())
            assertTrue("GPX content must contain <gpx", gpxEvent.content.contains("<gpx"))
            assertTrue("fileName must end with .gpx", gpxEvent.fileName.endsWith(".gpx"))
            assertTrue("fileName must contain route name slug", gpxEvent.fileName.contains("morning-ride"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exportGpx is no-op when route not loaded`() = runTest {
        val vm = buildVm(routeId = "nonexistent", route = null)
        vm.uiState.test {
            val state = awaitItem()
            if (state.loading) awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        vm.events.test {
            vm.exportGpx()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── shareRoute event ──────────────────────────────────────────────────────

    @Test
    fun `shareRoute emits LinkCopied with route url`() = runTest {
        val route = makeRoute(id = "route-xyz")
        val vm = buildVm(routeId = route.id, route = route)
        vm.uiState.test {
            skipToLoaded()
            cancelAndIgnoreRemainingEvents()
        }
        vm.events.test {
            vm.shareRoute()
            val event = awaitItem()
            assertTrue(event is RouteDetailEvent.LinkCopied)
            val linkEvent = event as RouteDetailEvent.LinkCopied
            assertTrue("URL must contain routeId", linkEvent.url.contains("route-xyz"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── sendToServer event ────────────────────────────────────────────────────

    @Test
    fun `sendToServer calls syncRepository enqueue with routeId and emits ServerSent`() = runTest {
        val route = makeRoute(id = "route-sync-me")
        val fakeSyncRepository = FakeSyncRepository()
        val vm = buildVm(routeId = route.id, route = route, syncRepo = fakeSyncRepository)
        vm.uiState.test {
            skipToLoaded()
            cancelAndIgnoreRemainingEvents()
        }
        vm.events.test {
            vm.sendToServer()
            val event = awaitItem()
            assertTrue(event is RouteDetailEvent.ServerSent)
            assertEquals(1, fakeSyncRepository.enqueuedIds.size)
            assertEquals("route-sync-me", fakeSyncRepository.enqueuedIds.first())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B13 GPS-correction UX tests
    // ─────────────────────────────────────────────────────────────────────────

    // (a) selectTrackView toggles displayed trackPoints between raw and corrected

    @Test
    fun `selectTrackView RAW shows raw trackPoints when corrected trace exists`() = runTest {
        val route = makeRoute(
            id = "route-1",
            pathJson = RAW_PATH,
            correctedPathJson = CORRECTED_PATH,
            correctionStatus = CorrectionStatus.DONE,
        )
        val vm = buildVm(routeId = route.id, route = route)
        vm.uiState.test {
            skipToLoaded() // defaults to CORRECTED since correctedPathJson present
            vm.selectTrackView(TrackView.RAW)
            val state = awaitItem()
            assertEquals(TrackView.RAW, state.selectedTrackView)
            // RAW path starts at lat 50.0
            assertEquals(2, state.trackPoints.size)
            assertEquals(50.0, state.trackPoints[0].lat, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectTrackView CORRECTED shows corrected trackPoints`() = runTest {
        val route = makeRoute(
            id = "route-1",
            pathJson = RAW_PATH,
            correctedPathJson = CORRECTED_PATH,
            correctionStatus = CorrectionStatus.DONE,
        )
        val vm = buildVm(routeId = route.id, route = route)
        vm.uiState.test {
            skipToLoaded() // defaults to CORRECTED
            vm.selectTrackView(TrackView.RAW)
            awaitItem() // consume the RAW switch
            vm.selectTrackView(TrackView.CORRECTED)
            val state = awaitItem()
            assertEquals(TrackView.CORRECTED, state.selectedTrackView)
            // CORRECTED path starts at lat 50.05
            assertEquals(2, state.trackPoints.size)
            assertEquals(50.05, state.trackPoints[0].lat, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // (b) default selection is Corrected when correctedPathJson present, Raw otherwise

    @Test
    fun `default selectedTrackView is CORRECTED when correctedPathJson is present`() = runTest {
        val route = makeRoute(
            id = "route-1",
            pathJson = RAW_PATH,
            correctedPathJson = CORRECTED_PATH,
            correctionStatus = CorrectionStatus.DONE,
        )
        val vm = buildVm(routeId = route.id, route = route)
        vm.uiState.test {
            val state = skipToLoaded()
            assertEquals(TrackView.CORRECTED, state.selectedTrackView)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default selectedTrackView is RAW when no corrected trace exists`() = runTest {
        val route = makeRoute(id = "route-1", pathJson = RAW_PATH)
        val vm = buildVm(routeId = route.id, route = route)
        vm.uiState.test {
            val state = skipToLoaded()
            assertEquals(TrackView.RAW, state.selectedTrackView)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // (c) correctNow() calls enqueue then correctNow on the fake correction repo

    @Test
    fun `correctNow enqueues route and calls correctNow on correction repository`() = runTest {
        val route = makeRoute(id = "route-correct-me")
        val fakeCorrectionRepo = FakeGpsCorrectionRepository()
        val vm = buildVm(routeId = route.id, route = route, correctionRepo = fakeCorrectionRepo)
        vm.uiState.test {
            skipToLoaded()
            cancelAndIgnoreRemainingEvents()
        }
        vm.events.test {
            vm.correctNow()
            val event = awaitItem()
            assertTrue("Event must be CorrectionQueued", event is RouteDetailEvent.CorrectionQueued)
            assertEquals(1, fakeCorrectionRepo.enqueuedIds.size)
            assertEquals("route-correct-me", fakeCorrectionRepo.enqueuedIds.first())
            assertEquals(1, fakeCorrectionRepo.correctNowCallCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // (d) deleteCorrectedTrace() clears corrected geometry, resets selection to Raw, leaves raw intact

    @Test
    fun `deleteCorrectedTrace clears corrected trace and resets to RAW view`() = runTest {
        val route = makeRoute(
            id = "route-1",
            pathJson = RAW_PATH,
            correctedPathJson = CORRECTED_PATH,
            correctionStatus = CorrectionStatus.DONE,
            confidence = 0.9,
        )
        val fakeRepo = FakeRouteRepository(stored = route)
        val vm = buildVm(routeId = route.id, fakeRouteRepo = fakeRepo)

        vm.uiState.test {
            val loaded = skipToLoaded()
            assertTrue(loaded.hasCorrectedTrace)
            assertEquals(TrackView.CORRECTED, loaded.selectedTrackView)

            vm.deleteCorrectedTrace()
            val afterDelete = awaitItem()

            assertEquals(TrackView.RAW, afterDelete.selectedTrackView)
            assertFalse(afterDelete.hasCorrectedTrace)
            assertEquals(CorrectionStatus.NONE, afterDelete.correctionStatus)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteCorrectedTrace leaves raw trackPoints intact`() = runTest {
        val route = makeRoute(
            id = "route-1",
            pathJson = RAW_PATH,
            correctedPathJson = CORRECTED_PATH,
            correctionStatus = CorrectionStatus.DONE,
        )
        val fakeRepo = FakeRouteRepository(stored = route)
        val vm = buildVm(routeId = route.id, fakeRouteRepo = fakeRepo)

        vm.uiState.test {
            skipToLoaded()
            vm.deleteCorrectedTrace()
            val afterDelete = awaitItem()

            // After deletion, showing RAW track — 2 points at lat 50.0 and 50.1
            assertEquals(2, afterDelete.trackPoints.size)
            assertEquals(50.0, afterDelete.trackPoints[0].lat, 0.001)
            assertEquals(50.1, afterDelete.trackPoints[1].lat, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // (e) correctionStatus / confidence surfaced in UiState

    @Test
    fun `correctionStatus DONE is surfaced in uiState with non-null labelRes and confidence label`() = runTest {
        val route = makeRoute(
            id = "route-1",
            correctedPathJson = CORRECTED_PATH,
            correctionStatus = CorrectionStatus.DONE,
            confidence = 0.92,
        )
        val vm = buildVm(routeId = route.id, route = route)
        vm.uiState.test {
            val state = skipToLoaded()
            assertEquals(CorrectionStatus.DONE, state.correctionStatus)
            assertNotNull("correctionStatusLabelRes must be non-null for DONE", state.correctionStatusLabelRes)
            assertTrue("confidenceLabel must be non-empty", state.confidenceLabel.isNotEmpty())
            assertTrue("confidenceLabel must contain '%'", state.confidenceLabel.contains("%"))
            assertTrue(state.hasCorrectedTrace)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `correctionStatus QUEUED surfaces label with no confidence`() = runTest {
        val route = makeRoute(
            id = "route-1",
            correctionStatus = CorrectionStatus.QUEUED,
            confidence = null,
        )
        val vm = buildVm(routeId = route.id, route = route)
        vm.uiState.test {
            val state = skipToLoaded()
            assertEquals(CorrectionStatus.QUEUED, state.correctionStatus)
            assertNotNull(state.correctionStatusLabelRes)
            assertTrue("confidenceLabel must be empty when confidence is null", state.confidenceLabel.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `correctionStatus LOW_CONFIDENCE surfaces confidence percentage label`() = runTest {
        val route = makeRoute(
            id = "route-1",
            correctionStatus = CorrectionStatus.LOW_CONFIDENCE,
            confidence = 0.45,
        )
        val vm = buildVm(routeId = route.id, route = route)
        vm.uiState.test {
            val state = skipToLoaded()
            assertEquals(CorrectionStatus.LOW_CONFIDENCE, state.correctionStatus)
            assertNotNull(state.correctionStatusLabelRes)
            assertTrue("confidenceLabel must contain '%'", state.confidenceLabel.contains("%"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `correctionStatus NONE has null correctionStatusLabelRes`() = runTest {
        val route = makeRoute(id = "route-1", correctionStatus = CorrectionStatus.NONE)
        val vm = buildVm(routeId = route.id, route = route)
        vm.uiState.test {
            val state = skipToLoaded()
            assertEquals(CorrectionStatus.NONE, state.correctionStatus)
            assertNull(state.correctionStatusLabelRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── live reactivity — route observe updates re-render ────────────────────

    @Test
    fun `uiState updates live when correctedPathJson is written to repository`() = runTest {
        val initial = makeRoute(id = "route-1", pathJson = RAW_PATH)
        val fakeRepo = FakeRouteRepository(stored = initial)
        val vm = buildVm(routeId = initial.id, fakeRouteRepo = fakeRepo)

        vm.uiState.test {
            val before = skipToLoaded()
            assertFalse(before.hasCorrectedTrace)
            assertEquals(TrackView.RAW, before.selectedTrackView)

            fakeRepo.emit(initial.copy(
                correctedPathJson = CORRECTED_PATH,
                correctionStatus = CorrectionStatus.DONE,
                confidence = 0.95,
            ))
            val after = awaitItem()

            assertTrue(after.hasCorrectedTrace)
            assertEquals(TrackView.CORRECTED, after.selectedTrackView)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── helper ───────────────────────────────────────────────────────────────

    /** Skips the initial [RouteDetailUiState.loading] state if present. */
    private suspend fun app.cash.turbine.TurbineTestContext<RouteDetailUiState>.skipToLoaded(): RouteDetailUiState {
        var state = awaitItem()
        while (state.loading) state = awaitItem()
        return state
    }
}
