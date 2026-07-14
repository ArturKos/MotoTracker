package com.mototracker.ui.screens.bikedetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.model.mapper.toRouteSummaryModel
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
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
import org.junit.Assert.assertNull
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
    override fun observeSummaries(): Flow<List<RouteSummaryModel>> =
        _flow.map { list -> list.map { it.toRouteSummaryModel() } }
    override suspend fun getById(id: String): Route? = _flow.value.find { it.id == id }
    override fun observeById(id: String): Flow<Route?> = MutableStateFlow(_flow.value.find { it.id == id })
    override suspend fun clearCorrectedTrace(id: String) {}
    override suspend fun rename(id: String, name: String) {}
    override suspend fun setBike(routeId: String, bikeId: String?) {}
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

    fun emit(s: AppSettings) { _flow.value = s }

    override val settings: Flow<AppSettings> = _flow
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun makeBike(
    id: String = "bike-1",
    name: String = "Yamaha MT-07",
    status: BikeStatus = BikeStatus.ACTIVE,
    fuelPricePerL: Double? = null,
) = Bike(
    id = id,
    name = name,
    year = 2021,
    plate = "WA 12345",
    status = status,
    fuelPricePerL = fuelPricePerL,
)

private fun makeRoute(
    id: String = "r1",
    bikeId: String? = "bike-1",
    km: Double = 100.0,
    durSec: Long = 3600L,
    max: Double = 120.0,
    fuel: Double = 5.0,
    dateEpochMs: Long = 1_700_000_000_000L,
) = Route(
    id = id,
    name = "Route $id",
    dateEpochMs = dateEpochMs,
    bikeId = bikeId,
    km = km,
    durSec = durSec,
    avg = km / (durSec / 3600.0),
    max = max,
    lean = 15.0,
    elev = 100.0,
    fuel = fuel,
    synced = false,
    wxJson = null,
    pathJson = null,
    speedJson = null,
    elevProfileJson = null,
    notes = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class BikeDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var routeRepo: FakeRouteRepository
    private lateinit var bikeRepo: FakeBikeRepository
    private lateinit var settings: FakeSettingsSource
    private lateinit var viewModel: BikeDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        routeRepo = FakeRouteRepository()
        bikeRepo = FakeBikeRepository()
        settings = FakeSettingsSource()
        viewModel = BikeDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bikeId" to "bike-1")),
            routeRepository = routeRepo,
            bikeRepository = bikeRepo,
            settingsSource = settings,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Bike not found ────────────────────────────────────────────────────────

    @Test
    fun `unknown bikeId produces empty non-loading state`() = runTest {
        bikeRepo.emit(emptyList())
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals("", state.bikeName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── State mapping ─────────────────────────────────────────────────────────

    @Test
    fun `bike name is mapped to state`() = runTest {
        bikeRepo.emit(listOf(makeBike(name = "Honda CBR")))
        viewModel.uiState.test {
            assertEquals("Honda CBR", awaitItem().bikeName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sold flag is true when bike status is SOLD`() = runTest {
        bikeRepo.emit(listOf(makeBike(status = BikeStatus.SOLD)))
        viewModel.uiState.test {
            assertTrue(awaitItem().isSold)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sold flag is false when bike status is ACTIVE`() = runTest {
        bikeRepo.emit(listOf(makeBike(status = BikeStatus.ACTIVE)))
        viewModel.uiState.test {
            assertFalse(awaitItem().isSold)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Empty bike (no routes) ────────────────────────────────────────────────

    @Test
    fun `empty routes produces zero stats and empty list`() = runTest {
        bikeRepo.emit(listOf(makeBike()))
        routeRepo.emit(emptyList())
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("0", state.rideCountDisplay)
            assertEquals("0.0 km", state.totalDistanceDisplay)
            assertEquals("0:00", state.totalTimeDisplay)
            assertNull(state.totalCostDisplay)
            assertTrue(state.routes.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Ride count and distance ───────────────────────────────────────────────

    @Test
    fun `ride count and distance reflect routes assigned to the bike`() = runTest {
        bikeRepo.emit(listOf(makeBike()))
        routeRepo.emit(listOf(
            makeRoute("r1", km = 100.0),
            makeRoute("r2", km = 50.0),
            makeRoute("r3", bikeId = "other-bike", km = 999.0),
        ))
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("2", state.rideCountDisplay)
            assertEquals("150.0 km", state.totalDistanceDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Total time ────────────────────────────────────────────────────────────

    @Test
    fun `total time is formatted as h_mm from summed durSec`() = runTest {
        bikeRepo.emit(listOf(makeBike()))
        routeRepo.emit(listOf(
            makeRoute("r1", durSec = 3600L),   // 1h
            makeRoute("r2", durSec = 1800L),   // 0h30m
        ))
        viewModel.uiState.test {
            assertEquals("1:30", awaitItem().totalTimeDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Cost present vs absent ────────────────────────────────────────────────

    @Test
    fun `totalCostDisplay is null when bike has no fuel price`() = runTest {
        bikeRepo.emit(listOf(makeBike(fuelPricePerL = null)))
        routeRepo.emit(listOf(makeRoute(fuel = 10.0)))
        viewModel.uiState.test {
            assertNull(awaitItem().totalCostDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totalCostDisplay is formatted when bike has fuel price`() = runTest {
        bikeRepo.emit(listOf(makeBike(fuelPricePerL = 6.0)))
        routeRepo.emit(listOf(
            makeRoute("r1", fuel = 5.0),
            makeRoute("r2", fuel = 5.0),
        ))
        settings.emit(AppSettings(currency = "PLN"))
        viewModel.uiState.test {
            // 10 L × 6.0 PLN/L = 60.0 PLN
            assertEquals("60.00 PLN", awaitItem().totalCostDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Unit toggle: metric vs imperial ──────────────────────────────────────

    @Test
    fun `distance is in km for metric units`() = runTest {
        bikeRepo.emit(listOf(makeBike()))
        routeRepo.emit(listOf(makeRoute(km = 100.0)))
        settings.emit(AppSettings(units = "metric"))
        viewModel.uiState.test {
            assertTrue(awaitItem().totalDistanceDisplay.endsWith("km"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `distance is in mi for imperial units`() = runTest {
        bikeRepo.emit(listOf(makeBike()))
        routeRepo.emit(listOf(makeRoute(km = 100.0)))
        settings.emit(AppSettings(units = "imperial"))
        viewModel.uiState.test {
            assertTrue(awaitItem().totalDistanceDisplay.endsWith("mi"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `speed is in mph for imperial units`() = runTest {
        bikeRepo.emit(listOf(makeBike()))
        routeRepo.emit(listOf(makeRoute(max = 120.0)))
        settings.emit(AppSettings(units = "imperial"))
        viewModel.uiState.test {
            assertTrue(awaitItem().topSpeedDisplay.endsWith("mph"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Routes list ───────────────────────────────────────────────────────────

    @Test
    fun `routes list is sorted by date descending`() = runTest {
        bikeRepo.emit(listOf(makeBike()))
        routeRepo.emit(listOf(
            makeRoute("r1", dateEpochMs = 1_000L),
            makeRoute("r2", dateEpochMs = 3_000L),
            makeRoute("r3", dateEpochMs = 2_000L),
        ))
        viewModel.uiState.test {
            val ids = awaitItem().routes.map { it.id }
            assertEquals(listOf("r2", "r3", "r1"), ids)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `routes list excludes routes assigned to other bikes`() = runTest {
        bikeRepo.emit(listOf(makeBike()))
        routeRepo.emit(listOf(
            makeRoute("r1", bikeId = "bike-1"),
            makeRoute("r2", bikeId = "other-bike"),
            makeRoute("r3", bikeId = null),
        ))
        viewModel.uiState.test {
            val ids = awaitItem().routes.map { it.id }
            assertEquals(listOf("r1"), ids)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
