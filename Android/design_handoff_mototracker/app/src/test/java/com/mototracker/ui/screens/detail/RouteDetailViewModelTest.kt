package com.mototracker.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.model.Wave
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.WaveRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

private class FakeRouteRepository(private var stored: Route? = null) : RouteRepository {
    override suspend fun save(route: Route) { stored = route }
    override fun observeAll(): Flow<List<Route>> = MutableStateFlow(listOfNotNull(stored))
    override suspend fun getById(id: String): Route? = if (stored?.id == id) stored else null
}

private class FakeBikeRepository(bikes: List<Bike> = emptyList()) : BikeRepository {
    private val _flow = MutableStateFlow(bikes)
    fun emit(bikes: List<Bike>) { _flow.value = bikes }
    override fun observeAll(): Flow<List<Bike>> = _flow
    override suspend fun addBike(bike: Bike) { _flow.value = _flow.value + bike }
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

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

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
): Route = Route(
    id = id, name = name, dateEpochMs = 1_700_000_000_000L,
    bikeId = bikeId, km = km, durSec = durSec, avg = avg, max = max,
    lean = lean, elev = elev, fuel = fuel, synced = synced,
    wxJson = wxJson, pathJson = null, speedJson = speedJson,
    elevProfileJson = elevProfileJson, notes = null,
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
    ): RouteDetailViewModel = RouteDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("routeId" to routeId)),
        routeRepository = FakeRouteRepository(stored = route),
        bikeRepository = FakeBikeRepository(bikes),
        waveRepository = FakeWaveRepository(waves),
        settingsSource = FakeSettingsSource(settings),
    )

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
            // skip loading state if it exists
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

    // ── helper ───────────────────────────────────────────────────────────────

    /** Skips the initial [RouteDetailUiState.loading] state if present. */
    private suspend fun app.cash.turbine.TurbineTestContext<RouteDetailUiState>.skipToLoaded(): RouteDetailUiState {
        var state = awaitItem()
        while (state.loading) state = awaitItem()
        return state
    }
}
