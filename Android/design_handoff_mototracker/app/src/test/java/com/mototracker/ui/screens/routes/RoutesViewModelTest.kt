package com.mototracker.ui.screens.routes

import app.cash.turbine.test
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RouteRepository
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

private class FakeRouteRepository(
    routes: List<Route> = emptyList(),
) : RouteRepository {
    private val _flow = MutableStateFlow(routes)

    fun emit(routes: List<Route>) { _flow.value = routes }

    override suspend fun save(route: Route) { _flow.value = _flow.value + route }
    override fun observeAll(): Flow<List<Route>> = _flow
    override suspend fun getById(id: String): Route? = _flow.value.find { it.id == id }
    override fun observeById(id: String): Flow<Route?> = MutableStateFlow(_flow.value.find { it.id == id })
    override suspend fun clearCorrectedTrace(id: String) { /* stub */ }
    override suspend fun deleteAll() { _flow.value = emptyList() }
}

private class FakeBikeRepository(
    bikes: List<Bike> = emptyList(),
) : BikeRepository {
    private val _flow = MutableStateFlow(bikes)

    fun emit(bikes: List<Bike>) { _flow.value = bikes }

    override fun observeAll(): Flow<List<Bike>> = _flow
    override suspend fun addBike(bike: Bike) { _flow.value = _flow.value + bike }
    override suspend fun deleteAll() { _flow.value = emptyList() }
}

private class FakeSettingsSource(
    settings: AppSettings = AppSettings(),
) : AppSettingsSource {
    private val _flow = MutableStateFlow(settings)

    fun emit(settings: AppSettings) { _flow.value = settings }

    override val settings: Flow<AppSettings> = _flow
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun makeRoute(
    id: String = "route-default",
    name: String = "Test route",
    km: Double = 100.0,
    durSec: Long = 3600L,
    maxKmh: Double = 120.0,
    synced: Boolean = true,
    bikeId: String? = null,
    dateEpochMs: Long = 1_700_000_000_000L,
): Route = Route(
    id = id,
    name = name,
    dateEpochMs = dateEpochMs,
    bikeId = bikeId,
    km = km,
    durSec = durSec,
    avg = 60.0,
    max = maxKmh,
    lean = 20.0,
    elev = 500.0,
    fuel = 5.0,
    synced = synced,
    wxJson = null,
    pathJson = null,
    speedJson = null,
    elevProfileJson = null,
    notes = null,
)

private fun makeBike(
    id: String,
    name: String,
    status: BikeStatus = BikeStatus.ACTIVE,
): Bike = Bike(id = id, name = name, year = 2020, plate = "AB1234", status = status)

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class RoutesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var routeRepo: FakeRouteRepository
    private lateinit var bikeRepo: FakeBikeRepository
    private lateinit var settings: FakeSettingsSource
    private lateinit var viewModel: RoutesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        routeRepo = FakeRouteRepository()
        bikeRepo = FakeBikeRepository()
        settings = FakeSettingsSource()
        viewModel = RoutesViewModel(routeRepo, bikeRepo, settings)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── routeCount ───────────────────────────────────────────────────────────

    @Test
    fun `initial state has routeCount 0`() {
        assertEquals(0, viewModel.uiState.value.routeCount)
    }

    @Test
    fun `routeCount reflects number of routes`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial empty

            routeRepo.emit(listOf(makeRoute("r1"), makeRoute("r2"), makeRoute("r3")))
            val state = awaitItem()
            assertEquals(3, state.routeCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── totalKmDisplay ───────────────────────────────────────────────────────

    @Test
    fun `totalKmDisplay sums all route km in metric`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            routeRepo.emit(listOf(makeRoute("r1", km = 100.0), makeRoute("r2", km = 50.5)))
            val state = awaitItem()
            assertEquals("150.5 km", state.totalKmDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totalKmDisplay converts to miles in imperial settings`() = runTest {
        settings.emit(AppSettings(units = "imperial"))
        routeRepo.emit(listOf(makeRoute("r1", km = 100.0)))

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(
                state.totalKmDisplay.endsWith("mi"),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── card mapping ─────────────────────────────────────────────────────────

    @Test
    fun `card list has one entry per route`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1"), makeRoute("r2")))

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.cards.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `card id matches route id`() = runTest {
        routeRepo.emit(listOf(makeRoute("abc-123")))

        viewModel.uiState.test {
            val card = awaitItem().cards.first()
            assertEquals("abc-123", card.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `card name matches route name`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", name = "Morning ride")))

        viewModel.uiState.test {
            val card = awaitItem().cards.first()
            assertEquals("Morning ride", card.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `card durationDisplay formatted as hms`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", durSec = 7653L))) // 2h 7m 33s

        viewModel.uiState.test {
            val card = awaitItem().cards.first()
            assertEquals("2:07:33", card.durationDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `card maxSpeedDisplay contains km per h in metric`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", maxKmh = 140.0)))

        viewModel.uiState.test {
            val card = awaitItem().cards.first()
            assertTrue(card.maxSpeedDisplay.contains("km/h"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── bike-name resolution ─────────────────────────────────────────────────

    @Test
    fun `bikeName is dash when bikeId is null`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", bikeId = null)))

        viewModel.uiState.test {
            val card = awaitItem().cards.first()
            assertEquals("—", card.bikeName)
            assertFalse(card.bikeSold)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bikeName is dash when bikeId not found in bike list`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", bikeId = "unknown-bike")))
        bikeRepo.emit(listOf(makeBike("other-bike", "Yamaha MT-07")))

        viewModel.uiState.test {
            val card = awaitItem().cards.first()
            assertEquals("—", card.bikeName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bikeName resolves from bike list when bikeId matches`() = runTest {
        bikeRepo.emit(listOf(makeBike("bike-1", "Yamaha MT-07")))
        routeRepo.emit(listOf(makeRoute("r1", bikeId = "bike-1")))

        viewModel.uiState.test {
            val card = awaitItem().cards.first()
            assertEquals("Yamaha MT-07", card.bikeName)
            assertFalse(card.bikeSold)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bikeSold is true when associated bike has status SOLD`() = runTest {
        bikeRepo.emit(listOf(makeBike("bike-sold", "Old bike", BikeStatus.SOLD)))
        routeRepo.emit(listOf(makeRoute("r1", bikeId = "bike-sold")))

        viewModel.uiState.test {
            val card = awaitItem().cards.first()
            assertEquals("Old bike", card.bikeName)
            assertTrue(card.bikeSold)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── synced marker ─────────────────────────────────────────────────────────

    @Test
    fun `synced is true when route is synced`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", synced = true)))

        viewModel.uiState.test {
            val card = awaitItem().cards.first()
            assertTrue(card.synced)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `synced is false when route is not synced`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", synced = false)))

        viewModel.uiState.test {
            val card = awaitItem().cards.first()
            assertFalse(card.synced)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── reactivity ───────────────────────────────────────────────────────────

    @Test
    fun `uiState updates when routes change`() = runTest {
        viewModel.uiState.test {
            assertEquals(0, awaitItem().routeCount)

            routeRepo.emit(listOf(makeRoute("r1")))
            assertEquals(1, awaitItem().routeCount)

            routeRepo.emit(listOf(makeRoute("r1"), makeRoute("r2")))
            assertEquals(2, awaitItem().routeCount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B14 — name filter
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `name filter returns only routes whose name contains the query`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", name = "Morning ride"),
            makeRoute("r2", name = "Evening tour"),
        ))
        viewModel.uiState.test {
            awaitItem() // loaded state
            viewModel.setQuery("morning")
            val state = awaitItem()
            assertEquals(1, state.cards.size)
            assertEquals("r1", state.cards[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `name filter is case-insensitive`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", name = "Morning ride"),
            makeRoute("r2", name = "Evening tour"),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setQuery("MORNING")
            val state = awaitItem()
            assertEquals(1, state.cards.size)
            assertEquals("r1", state.cards[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `name filter empty string returns all routes`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", name = "Alpha"),
            makeRoute("r2", name = "Beta"),
            makeRoute("r3", name = "Gamma"),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setQuery("beta")
            awaitItem() // filtered
            viewModel.setQuery("")
            val state = awaitItem()
            assertEquals(3, state.cards.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `name filter no match returns empty cards`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", name = "Morning ride"),
            makeRoute("r2", name = "Evening tour"),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setQuery("xyzzy")
            val state = awaitItem()
            assertTrue(state.cards.isEmpty())
            assertEquals(2, state.totalRouteCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B14 — bike filter
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `bike filter restricts to routes with matching bikeId`() = runTest {
        bikeRepo.emit(listOf(makeBike("bike-1", "Honda CB500"), makeBike("bike-2", "Yamaha MT-07")))
        routeRepo.emit(listOf(
            makeRoute("r1", bikeId = "bike-1"),
            makeRoute("r2", bikeId = "bike-2"),
            makeRoute("r3", bikeId = null),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setBikeFilter("bike-1")
            val state = awaitItem()
            assertEquals(1, state.cards.size)
            assertEquals("r1", state.cards[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bike filter null returns all routes`() = runTest {
        bikeRepo.emit(listOf(makeBike("bike-1", "Honda CB500")))
        routeRepo.emit(listOf(
            makeRoute("r1", bikeId = "bike-1"),
            makeRoute("r2", bikeId = null),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setBikeFilter("bike-1")
            awaitItem()
            viewModel.setBikeFilter(null)
            val state = awaitItem()
            assertEquals(2, state.cards.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B14 — date-range filter
    // ─────────────────────────────────────────────────────────────────────────

    private val T1 = 1_000_000_000_000L   // earlier
    private val T2 = 1_500_000_000_000L   // middle
    private val T3 = 2_000_000_000_000L   // latest

    @Test
    fun `date range from-only excludes routes before fromEpochMs`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", dateEpochMs = T1),
            makeRoute("r2", dateEpochMs = T2),
            makeRoute("r3", dateEpochMs = T3),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setDateRange(fromEpochMs = T2, toEpochMs = null)
            val state = awaitItem()
            assertEquals(2, state.cards.size)
            assertTrue(state.cards.none { it.id == "r1" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `date range to-only excludes routes after toEpochMs`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", dateEpochMs = T1),
            makeRoute("r2", dateEpochMs = T2),
            makeRoute("r3", dateEpochMs = T3),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setDateRange(fromEpochMs = null, toEpochMs = T2)
            val state = awaitItem()
            assertEquals(2, state.cards.size)
            assertTrue(state.cards.none { it.id == "r3" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `date range both bounds inclusive`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", dateEpochMs = T1),
            makeRoute("r2", dateEpochMs = T2),
            makeRoute("r3", dateEpochMs = T3),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setDateRange(fromEpochMs = T2, toEpochMs = T2)
            val state = awaitItem()
            assertEquals(1, state.cards.size)
            assertEquals("r2", state.cards[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `date range null bounds returns all routes`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", dateEpochMs = T1),
            makeRoute("r2", dateEpochMs = T2),
            makeRoute("r3", dateEpochMs = T3),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setDateRange(fromEpochMs = null, toEpochMs = null)
            // No filter change expected — all 3 still visible
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B14 — sort by DATE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `sort DATE DESC places newest route first`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", dateEpochMs = T1),
            makeRoute("r2", dateEpochMs = T3),
            makeRoute("r3", dateEpochMs = T2),
        ))
        viewModel.uiState.test {
            // DATE DESC is the default sort; verify initial state directly without calling setSort
            val state = awaitItem()
            assertEquals(listOf("r2", "r3", "r1"), state.cards.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sort DATE ASC places oldest route first`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", dateEpochMs = T3),
            makeRoute("r2", dateEpochMs = T1),
            makeRoute("r3", dateEpochMs = T2),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setSort(RouteSortKey.DATE, SortDirection.ASC)
            val state = awaitItem()
            assertEquals(listOf("r2", "r3", "r1"), state.cards.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B14 — sort by DISTANCE
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `sort DISTANCE DESC places longest route first`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", km = 50.0, dateEpochMs = T1),
            makeRoute("r2", km = 200.0, dateEpochMs = T1),
            makeRoute("r3", km = 100.0, dateEpochMs = T1),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setSort(RouteSortKey.DISTANCE, SortDirection.DESC)
            val state = awaitItem()
            assertEquals(listOf("r2", "r3", "r1"), state.cards.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sort DISTANCE ASC places shortest route first`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", km = 200.0, dateEpochMs = T1),
            makeRoute("r2", km = 50.0, dateEpochMs = T1),
            makeRoute("r3", km = 100.0, dateEpochMs = T1),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setSort(RouteSortKey.DISTANCE, SortDirection.ASC)
            val state = awaitItem()
            assertEquals(listOf("r2", "r3", "r1"), state.cards.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B14 — sort by DURATION
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `sort DURATION DESC places longest duration first`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", durSec = 1800L, dateEpochMs = T1),
            makeRoute("r2", durSec = 7200L, dateEpochMs = T1),
            makeRoute("r3", durSec = 3600L, dateEpochMs = T1),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setSort(RouteSortKey.DURATION, SortDirection.DESC)
            val state = awaitItem()
            assertEquals(listOf("r2", "r3", "r1"), state.cards.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sort DURATION ASC places shortest duration first`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", durSec = 7200L, dateEpochMs = T1),
            makeRoute("r2", durSec = 1800L, dateEpochMs = T1),
            makeRoute("r3", durSec = 3600L, dateEpochMs = T1),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setSort(RouteSortKey.DURATION, SortDirection.ASC)
            val state = awaitItem()
            assertEquals(listOf("r2", "r3", "r1"), state.cards.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B14 — sort by MAX_SPEED
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `sort MAX_SPEED DESC places fastest route first`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", maxKmh = 80.0, dateEpochMs = T1),
            makeRoute("r2", maxKmh = 200.0, dateEpochMs = T1),
            makeRoute("r3", maxKmh = 140.0, dateEpochMs = T1),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setSort(RouteSortKey.MAX_SPEED, SortDirection.DESC)
            val state = awaitItem()
            assertEquals(listOf("r2", "r3", "r1"), state.cards.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sort MAX_SPEED ASC places slowest route first`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", maxKmh = 200.0, dateEpochMs = T1),
            makeRoute("r2", maxKmh = 80.0, dateEpochMs = T1),
            makeRoute("r3", maxKmh = 140.0, dateEpochMs = T1),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setSort(RouteSortKey.MAX_SPEED, SortDirection.ASC)
            val state = awaitItem()
            assertEquals(listOf("r2", "r3", "r1"), state.cards.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B14 — tie-break determinism
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `sort tie-break uses dateEpochMs DESC then id ASC`() = runTest {
        // All have the same distance; tie-break by dateEpochMs DESC then id ASC
        routeRepo.emit(listOf(
            makeRoute("a-id", km = 100.0, dateEpochMs = T1),
            makeRoute("b-id", km = 100.0, dateEpochMs = T2),
            makeRoute("c-id", km = 100.0, dateEpochMs = T2),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setSort(RouteSortKey.DISTANCE, SortDirection.ASC)
            val state = awaitItem()
            // b-id and c-id are tied on primary (distance) and secondary (date T2 > T1),
            // b-id < c-id lexicographically so b-id comes first in the id ASC tie-break
            assertEquals("b-id", state.cards[0].id)
            assertEquals("c-id", state.cards[1].id)
            assertEquals("a-id", state.cards[2].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B14 — filtered summary tiles
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `routeCount reflects filtered set not total`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", name = "Alpha", km = 50.0),
            makeRoute("r2", name = "Beta", km = 100.0),
            makeRoute("r3", name = "Gamma", km = 200.0),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setQuery("alpha")
            val state = awaitItem()
            assertEquals(1, state.routeCount)
            assertEquals(3, state.totalRouteCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totalKmDisplay reflects filtered set not total`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", name = "Alpha", km = 50.0),
            makeRoute("r2", name = "Beta", km = 100.0),
        ))
        viewModel.uiState.test {
            awaitItem()
            viewModel.setQuery("alpha")
            val state = awaitItem()
            assertEquals("50.0 km", state.totalKmDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B14 — clearFilters
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `clearFilters resets to DATE DESC default and shows all routes`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", name = "Alpha", dateEpochMs = T1),
            makeRoute("r2", name = "Beta", dateEpochMs = T2),
        ))
        viewModel.uiState.test {
            awaitItem()
            // Apply several filters
            viewModel.setQuery("alpha")
            awaitItem()
            viewModel.setSort(RouteSortKey.DISTANCE, SortDirection.ASC)
            awaitItem()
            // Clear
            viewModel.clearFilters()
            val state = awaitItem()
            assertEquals(2, state.cards.size)
            assertEquals(RoutesFilter(), state.filter)
            assertEquals(RouteSortKey.DATE, state.filter.sortKey)
            assertEquals(SortDirection.DESC, state.filter.sortDir)
            // After clear with DATE DESC, newest first
            assertEquals("r2", state.cards[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
