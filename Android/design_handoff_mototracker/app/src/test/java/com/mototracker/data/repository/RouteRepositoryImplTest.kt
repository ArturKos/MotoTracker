package com.mototracker.data.repository

import app.cash.turbine.test
import com.mototracker.data.local.dao.RouteDao
import com.mototracker.data.local.entity.RouteEntity
import com.mototracker.data.model.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fake RouteDao
// ─────────────────────────────────────────────────────────────────────────────

private class FakeRouteDaoImpl : RouteDao {
    private val store = mutableMapOf<String, RouteEntity>()
    private val allFlow = MutableStateFlow<List<RouteEntity>>(emptyList())
    val upsertCalls = mutableListOf<RouteEntity>()

    override suspend fun upsert(entity: RouteEntity) {
        upsertCalls += entity
        store[entity.id] = entity
        allFlow.value = store.values.sortedByDescending { it.dateEpochMs }
    }

    override suspend fun delete(entity: RouteEntity) {
        store.remove(entity.id)
        allFlow.value = store.values.sortedByDescending { it.dateEpochMs }
    }

    override fun getAll(): Flow<List<RouteEntity>> = allFlow

    override suspend fun getById(id: String): RouteEntity? = store[id]

    override suspend fun setSynced(id: String, synced: Boolean) {
        store[id]?.let { store[id] = it.copy(synced = synced) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

class RouteRepositoryImplTest {

    private lateinit var dao: FakeRouteDaoImpl
    private lateinit var repo: RouteRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeRouteDaoImpl()
        repo = RouteRepositoryImpl(dao)
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    fun `save calls upsert on dao with mapped entity`() = runTest {
        val route = buildRoute(id = "route-1", km = 42.5)
        repo.save(route)

        assertEquals(1, dao.upsertCalls.size)
        val entity = dao.upsertCalls.first()
        assertEquals("route-1", entity.id)
        assertEquals(42.5, entity.km, 0.001)
    }

    @Test
    fun `save maps all domain fields to entity`() = runTest {
        val route = buildRoute(
            id = "abc",
            name = "Morning Ride",
            dateEpochMs = 1_700_000_000L,
            bikeId = "bike-1",
            km = 100.0,
            durSec = 3600L,
            avg = 80.0,
            max = 140.0,
            lean = 35.0,
            elev = 500.0,
            fuel = 5.5,
            synced = false,
            wxJson = "{\"temp\":20}",
            pathJson = "[]",
            speedJson = "[]",
            elevProfileJson = "[]",
            notes = "Great ride",
        )
        repo.save(route)

        val entity = dao.upsertCalls.first()
        assertEquals("abc", entity.id)
        assertEquals("Morning Ride", entity.name)
        assertEquals(1_700_000_000L, entity.dateEpochMs)
        assertEquals("bike-1", entity.bikeId)
        assertEquals(100.0, entity.km, 0.001)
        assertEquals(3600L, entity.durSec)
        assertEquals(80.0, entity.avg, 0.001)
        assertEquals(140.0, entity.max, 0.001)
        assertEquals(35.0, entity.lean, 0.001)
        assertEquals(500.0, entity.elev, 0.001)
        assertEquals(5.5, entity.fuel, 0.001)
        assertFalse(entity.synced)
        assertEquals("{\"temp\":20}", entity.wxJson)
        assertEquals("[]", entity.pathJson)
        assertEquals("[]", entity.speedJson)
        assertEquals("[]", entity.elevProfileJson)
        assertEquals("Great ride", entity.notes)
    }

    @Test
    fun `save second route with same id replaces the first`() = runTest {
        repo.save(buildRoute(id = "same-id", km = 10.0))
        repo.save(buildRoute(id = "same-id", km = 20.0))

        assertEquals(2, dao.upsertCalls.size)
        assertEquals(20.0, dao.upsertCalls.last().km, 0.001)
    }

    @Test
    fun `null optional fields are preserved`() = runTest {
        val route = buildRoute(bikeId = null, wxJson = null, notes = null)
        repo.save(route)

        val entity = dao.upsertCalls.first()
        assertNull(entity.bikeId)
        assertNull(entity.wxJson)
        assertNull(entity.notes)
    }

    // ── observeAll ───────────────────────────────────────────────────────────

    @Test
    fun `observeAll emits empty list initially`() = runTest {
        repo.observeAll().test {
            assertEquals(emptyList<Route>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAll emits routes after save`() = runTest {
        repo.observeAll().test {
            awaitItem() // empty initial

            repo.save(buildRoute(id = "r1", km = 55.0))
            val routes = awaitItem()

            assertEquals(1, routes.size)
            assertEquals("r1", routes.first().id)
            assertEquals(55.0, routes.first().km, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAll maps entity fields to domain model`() = runTest {
        repo.observeAll().test {
            awaitItem() // empty
            repo.save(
                buildRoute(
                    id = "abc",
                    name = "Test",
                    km = 12.3,
                    avg = 60.0,
                    max = 120.0,
                    lean = 30.0,
                    synced = false,
                )
            )
            val routes = awaitItem()
            val route = routes.first()
            assertEquals("abc", route.id)
            assertEquals("Test", route.name)
            assertEquals(12.3, route.km, 0.001)
            assertEquals(60.0, route.avg, 0.001)
            assertEquals(120.0, route.max, 0.001)
            assertEquals(30.0, route.lean, 0.001)
            assertFalse(route.synced)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAll emits updated list after multiple saves`() = runTest {
        repo.observeAll().test {
            awaitItem() // empty
            repo.save(buildRoute(id = "a"))
            awaitItem() // [a]
            repo.save(buildRoute(id = "b"))
            val routes = awaitItem()
            assertEquals(2, routes.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── getById ──────────────────────────────────────────────────────────────

    @Test
    fun `getById returns null when route does not exist`() = runTest {
        assertNull(repo.getById("nonexistent"))
    }

    @Test
    fun `getById returns route after save`() = runTest {
        repo.save(buildRoute(id = "found", name = "Night Ride", km = 77.0))
        val result = repo.getById("found")
        assertEquals("found", result?.id)
        assertEquals("Night Ride", result?.name)
        assertEquals(77.0, result?.km ?: 0.0, 0.001)
    }

    @Test
    fun `getById returns null for a different id than what was saved`() = runTest {
        repo.save(buildRoute(id = "alpha"))
        assertNull(repo.getById("beta"))
    }

    @Test
    fun `getById maps all fields correctly`() = runTest {
        val route = buildRoute(
            id = "m1",
            name = "Dawn Run",
            dateEpochMs = 1_710_000_000L,
            bikeId = "bike-x",
            km = 55.5,
            durSec = 7200L,
            avg = 55.0,
            max = 130.0,
            lean = 40.0,
            elev = 900.0,
            fuel = 4.2,
            synced = true,
            wxJson = """{"temp":18}""",
            notes = "Misty",
        )
        repo.save(route)
        val got = repo.getById("m1")!!
        assertEquals("Dawn Run", got.name)
        assertEquals(1_710_000_000L, got.dateEpochMs)
        assertEquals("bike-x", got.bikeId)
        assertEquals(55.5, got.km, 0.001)
        assertEquals(7200L, got.durSec)
        assertEquals(55.0, got.avg, 0.001)
        assertEquals(130.0, got.max, 0.001)
        assertEquals(40.0, got.lean, 0.001)
        assertEquals(900.0, got.elev, 0.001)
        assertEquals(4.2, got.fuel, 0.001)
        assertTrue(got.synced)
        assertEquals("""{"temp":18}""", got.wxJson)
        assertEquals("Misty", got.notes)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildRoute(
        id: String = "route-0",
        name: String = "",
        dateEpochMs: Long = 0L,
        bikeId: String? = null,
        km: Double = 0.0,
        durSec: Long = 0L,
        avg: Double = 0.0,
        max: Double = 0.0,
        lean: Double = 0.0,
        elev: Double = 0.0,
        fuel: Double = 0.0,
        synced: Boolean = false,
        wxJson: String? = null,
        pathJson: String? = "[]",
        speedJson: String? = "[]",
        elevProfileJson: String? = "[]",
        notes: String? = null,
    ) = Route(
        id = id,
        name = name,
        dateEpochMs = dateEpochMs,
        bikeId = bikeId,
        km = km,
        durSec = durSec,
        avg = avg,
        max = max,
        lean = lean,
        elev = elev,
        fuel = fuel,
        synced = synced,
        wxJson = wxJson,
        pathJson = pathJson,
        speedJson = speedJson,
        elevProfileJson = elevProfileJson,
        notes = notes,
    )
}
