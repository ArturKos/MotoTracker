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
}

private class FakeBikeRepository(
    bikes: List<Bike> = emptyList(),
) : BikeRepository {
    private val _flow = MutableStateFlow(bikes)

    fun emit(bikes: List<Bike>) { _flow.value = bikes }

    override fun observeAll(): Flow<List<Bike>> = _flow
    override suspend fun addBike(bike: Bike) { _flow.value = _flow.value + bike }
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
}
