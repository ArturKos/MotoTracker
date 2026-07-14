package com.mototracker.ui.screens.stats

import app.cash.turbine.test
import com.mototracker.data.model.Route
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.model.mapper.toRouteSummaryModel
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.domain.stats.Badge
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

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
    override suspend fun clearCorrectedTrace(id: String) { /* stub */ }
    override suspend fun rename(id: String, name: String) { /* stub */ }
    override suspend fun setBike(routeId: String, bikeId: String?) { /* stub */ }
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

private fun makeRoute(
    id: String = "r-default",
    km: Double = 100.0,
    durSec: Long = 3600L,
    avg: Double = 60.0,
    max: Double = 120.0,
    lean: Double = 20.0,
    elev: Double = 500.0,
    dateEpochMs: Long = epochForYearMonth(2026, Calendar.JUNE),
): Route = Route(
    id = id,
    name = "Route $id",
    dateEpochMs = dateEpochMs,
    bikeId = null,
    km = km,
    durSec = durSec,
    avg = avg,
    max = max,
    lean = lean,
    elev = elev,
    fuel = 5.0,
    synced = true,
    wxJson = null,
    pathJson = null,
    speedJson = null,
    elevProfileJson = null,
    notes = null,
)

/** Returns a deterministic epoch-ms for the 1st of [year]/[month] (Calendar.JANUARY = 0). */
private fun epochForYearMonth(year: Int, month: Int): Long {
    return Calendar.getInstance().apply {
        set(year, month, 1, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var routeRepo: FakeRouteRepository
    private lateinit var settings: FakeSettingsSource
    private lateinit var viewModel: StatsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        routeRepo = FakeRouteRepository()
        settings = FakeSettingsSource()
        viewModel = StatsViewModel(routeRepo, settings)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── empty-route state ────────────────────────────────────────────────────

    @Test
    fun `initial state has zeros and empty monthBars`() {
        val s = viewModel.uiState.value
        assertEquals(0, s.ridesCount)
        assertEquals("0.0 km", s.totalDistanceDisplay)
        assertEquals("0:00", s.timeInSaddleDisplay)
        assertEquals("0 km/h", s.topSpeedDisplay)
        assertTrue(s.monthBars.isEmpty())
        assertEquals("", s.yearLabel)
        assertEquals("0°", s.style.avgLeanDisplay)
        assertEquals(0f, s.style.avgLeanFraction)
        assertEquals(0f, s.style.avgSpeedFraction)
        assertEquals(0f, s.style.totalClimbFraction)
    }

    @Test
    fun `yearLabel is 4-digit year of newest route`() = runTest {
        val r1 = makeRoute("r1", dateEpochMs = epochForYearMonth(2025, Calendar.DECEMBER))
        val r2 = makeRoute("r2", dateEpochMs = epochForYearMonth(2026, Calendar.JUNE))
        routeRepo.emit(listOf(r1, r2))
        viewModel.uiState.test {
            assertEquals("2026", awaitItem().yearLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `yearLabel is empty when no routes`() = runTest {
        routeRepo.emit(emptyList())
        viewModel.uiState.test {
            assertEquals("", awaitItem().yearLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── totals / topSpeed / ridesCount ───────────────────────────────────────

    @Test
    fun `ridesCount reflects route list size`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1"), makeRoute("r2"), makeRoute("r3")))
        viewModel.uiState.test {
            assertEquals(3, awaitItem().ridesCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totalDistanceDisplay sums all km in metric`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", km = 100.0), makeRoute("r2", km = 50.5)))
        viewModel.uiState.test {
            assertEquals("150.5 km", awaitItem().totalDistanceDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `timeInSaddleDisplay sums duration formatted as h_mm`() = runTest {
        // 41 880 s = 11h 38m
        routeRepo.emit(listOf(makeRoute("r1", durSec = 41_880L)))
        viewModel.uiState.test {
            assertEquals("11:38", awaitItem().timeInSaddleDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `topSpeedDisplay is max speed across all routes in metric`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", max = 120.0), makeRoute("r2", max = 175.0)))
        viewModel.uiState.test {
            val s = awaitItem()
            assertEquals("175 km/h", s.topSpeedDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── metric vs imperial ───────────────────────────────────────────────────

    @Test
    fun `totalDistanceDisplay converts to miles in imperial`() = runTest {
        settings.emit(AppSettings(units = "imperial"))
        routeRepo.emit(listOf(makeRoute("r1", km = 100.0)))
        viewModel.uiState.test {
            val s = awaitItem()
            assertTrue(s.totalDistanceDisplay.endsWith("mi"))
            assertEquals("mi", s.distanceUnitLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `topSpeedDisplay converts to mph in imperial`() = runTest {
        settings.emit(AppSettings(units = "imperial"))
        routeRepo.emit(listOf(makeRoute("r1", max = 100.0)))
        viewModel.uiState.test {
            val s = awaitItem()
            assertTrue(s.topSpeedDisplay.endsWith("mph"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── month grouping + heightFraction ──────────────────────────────────────

    @Test
    fun `monthBars has 6 entries when routes present`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1")))
        viewModel.uiState.test {
            assertEquals(6, awaitItem().monthBars.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `monthBars tallest bar has heightFraction 1_0`() = runTest {
        // Two routes in different months: the one with more km should be the tallest.
        val r1 = makeRoute("r1", km = 200.0, dateEpochMs = epochForYearMonth(2026, Calendar.JUNE))
        val r2 = makeRoute("r2", km = 50.0, dateEpochMs = epochForYearMonth(2026, Calendar.MAY))
        routeRepo.emit(listOf(r1, r2))
        viewModel.uiState.test {
            val bars = awaitItem().monthBars
            // June bar (last slot) has km=200 → 0.18 + 1.0*0.82 = 1.0
            val juneFraction = bars.last().heightFraction
            assertEquals(1.0f, juneFraction, 0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `monthBars empty-month bar has heightFraction 0_18`() = runTest {
        // Only a route in the most-recent month; earlier months have 0 km → 0.18
        val r1 = makeRoute("r1", km = 200.0, dateEpochMs = epochForYearMonth(2026, Calendar.JUNE))
        routeRepo.emit(listOf(r1))
        viewModel.uiState.test {
            val bars = awaitItem().monthBars
            // All bars except last should have 0.18f
            for (bar in bars.dropLast(1)) {
                assertEquals(0.18f, bar.heightFraction, 0.001f)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `monthBars fractional bar follows formula 0_18 plus ratio times 0_82`() = runTest {
        val r1 = makeRoute("r1", km = 200.0, dateEpochMs = epochForYearMonth(2026, Calendar.JUNE))
        val r2 = makeRoute("r2", km = 100.0, dateEpochMs = epochForYearMonth(2026, Calendar.MAY))
        routeRepo.emit(listOf(r1, r2))
        viewModel.uiState.test {
            val bars = awaitItem().monthBars
            // May bar: km=100, maxKm=200 → 0.18 + 0.5*0.82 = 0.59
            val mayFraction = bars[bars.size - 2].heightFraction
            assertEquals(0.59f, mayFraction, 0.005f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── riding style fractions ───────────────────────────────────────────────

    @Test
    fun `avgLeanFraction of 38 degrees is approximately 0_63`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", lean = 38.0)))
        viewModel.uiState.test {
            val f = awaitItem().style.avgLeanFraction
            // 38 / 60 = 0.6333…
            assertEquals(0.633f, f, 0.005f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `avgLeanFraction clamped to 1 when lean exceeds 60 degrees`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", lean = 90.0)))
        viewModel.uiState.test {
            assertEquals(1.0f, awaitItem().style.avgLeanFraction, 0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `avgSpeedFraction of 48 kmh is approximately 0_48`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", avg = 48.0)))
        viewModel.uiState.test {
            val f = awaitItem().style.avgSpeedFraction
            assertEquals(0.48f, f, 0.005f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totalClimbFraction of 5920m is approximately 0_74`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", elev = 5920.0)))
        viewModel.uiState.test {
            val f = awaitItem().style.totalClimbFraction
            // 5920 / 8000 = 0.74
            assertEquals(0.74f, f, 0.005f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `totalClimbFraction clamped to 1 when climb exceeds 8000m`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", elev = 10_000.0)))
        viewModel.uiState.test {
            assertEquals(1.0f, awaitItem().style.totalClimbFraction, 0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `avgLeanDisplay shows rounded degrees symbol`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", lean = 38.4), makeRoute("r2", lean = 37.6)))
        viewModel.uiState.test {
            // average = 38.0 → "38°"
            assertEquals("38°", awaitItem().style.avgLeanDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── records — empty-route behaviour ─────────────────────────────────────

    @Test
    fun `records and badges are empty when no routes`() {
        val s = viewModel.uiState.value
        assertTrue(s.records.isEmpty())
        assertTrue(s.badges.isEmpty())
    }

    // ── records — populated state ────────────────────────────────────────────

    @Test
    fun `records list has 6 rows for non-empty route set`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1")))
        viewModel.uiState.test {
            assertEquals(6, awaitItem().records.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `records longestRide formatted as metric distance`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", km = 123.5)))
        viewModel.uiState.test {
            val recordRow = awaitItem().records.first()
            assertEquals("123.5 km", recordRow.valueDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `records longestRide formatted as imperial distance`() = runTest {
        settings.emit(AppSettings(units = "imperial"))
        routeRepo.emit(listOf(makeRoute("r1", km = 100.0)))
        viewModel.uiState.test {
            val recordRow = awaitItem().records.first()
            assertTrue(recordRow.valueDisplay.endsWith("mi"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `records topSpeed formatted in metric`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", max = 155.0)))
        viewModel.uiState.test {
            // records index 2 = rec_top_speed
            val topSpeedRow = awaitItem().records[2]
            assertEquals("155 km/h", topSpeedRow.valueDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `records topSpeed formatted in imperial`() = runTest {
        settings.emit(AppSettings(units = "imperial"))
        routeRepo.emit(listOf(makeRoute("r1", max = 160.0)))
        viewModel.uiState.test {
            val topSpeedRow = awaitItem().records[2]
            assertTrue(topSpeedRow.valueDisplay.endsWith("mph"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `records dayStreak valueDisplay is bare integer and unitRes is set`() = runTest {
        // Single route → streak = 1; the unit label lives in unitRes, not embedded in valueDisplay
        routeRepo.emit(listOf(makeRoute("r1")))
        viewModel.uiState.test {
            val streakRow = awaitItem().records[5]
            assertEquals("1", streakRow.valueDisplay)
            assertTrue("streak row must carry a unitRes for the days label", streakRow.unitRes != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── badges ───────────────────────────────────────────────────────────────

    @Test
    fun `FIRST_RIDE badge is present after first route`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1")))
        viewModel.uiState.test {
            val badges = awaitItem().badges.map { it.badge }
            assertTrue(Badge.FIRST_RIDE in badges)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `CENTURY badge is present when any route has 100 km`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", km = 100.0)))
        viewModel.uiState.test {
            val badges = awaitItem().badges.map { it.badge }
            assertTrue(Badge.CENTURY in badges)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SPEED_DEMON badge is present when max speed reaches 150 kmh`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", max = 150.0)))
        viewModel.uiState.test {
            val badges = awaitItem().badges.map { it.badge }
            assertTrue(Badge.SPEED_DEMON in badges)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `badges are empty when no routes`() = runTest {
        routeRepo.emit(emptyList())
        viewModel.uiState.test {
            assertTrue(awaitItem().badges.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each badge has a non-zero nameRes`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1")))
        viewModel.uiState.test {
            for (badgeUi in awaitItem().badges) {
                assertTrue("nameRes for ${badgeUi.badge} must be > 0", badgeUi.nameRes > 0)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
