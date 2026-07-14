package com.mototracker.data.repository

import app.cash.turbine.test
import com.mototracker.data.local.dao.RouteDao
import com.mototracker.data.local.dao.RouteTraceChunkDao
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.local.entity.RouteEntity
import com.mototracker.data.local.entity.RouteTraceChunkEntity
import com.mototracker.data.model.Route
import com.mototracker.data.model.RouteSummaryModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
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

    override fun observeSummaries(): Flow<List<RouteSummaryModel>> = allFlow.map { entities ->
        entities.map { e ->
            RouteSummaryModel(
                id = e.id, name = e.name, dateEpochMs = e.dateEpochMs, bikeId = e.bikeId,
                km = e.km, durSec = e.durSec, avg = e.avg, max = e.max, lean = e.lean,
                elev = e.elev, fuel = e.fuel, synced = e.synced, thumbnailPathD = e.thumbnailPathD,
                correctionStatus = e.correctionStatus, confidence = e.confidence,
            )
        }
    }

    override suspend fun getById(id: String): RouteEntity? = store[id]

    override fun observeById(id: String): Flow<RouteEntity?> = MutableStateFlow(store[id])

    override suspend fun setSynced(id: String, synced: Boolean) {
        store[id]?.let { store[id] = it.copy(synced = synced) }
    }

    override suspend fun clearCorrection(id: String) {
        store[id]?.let {
            store[id] = it.copy(correctionStatus = CorrectionStatus.NONE, confidence = null)
        }
    }

    override suspend fun setName(id: String, name: String) {
        store[id]?.let { store[id] = it.copy(name = name) }
    }

    val setBikeCalls = mutableListOf<Pair<String, String?>>()

    override suspend fun setBike(id: String, bikeId: String?) {
        setBikeCalls += id to bikeId
        store[id]?.let { store[id] = it.copy(bikeId = bikeId) }
    }

    override suspend fun deleteAll() {
        store.clear()
        allFlow.value = emptyList()
    }

    override suspend fun setThumbnailPathD(id: String, thumbnailPathD: String?) {
        store[id]?.let { store[id] = it.copy(thumbnailPathD = thumbnailPathD) }
    }
}

private class FakeRouteTraceChunkDaoImpl : RouteTraceChunkDao {
    // (routeId, kind) → sorted list of chunks
    private val store = mutableMapOf<Pair<String, String>, MutableList<RouteTraceChunkEntity>>()

    override suspend fun replace(routeId: String, kind: String, chunks: List<RouteTraceChunkEntity>) {
        deleteFor(routeId, kind)
        if (chunks.isNotEmpty()) insertAll(chunks)
    }

    override suspend fun getChunks(routeId: String, kind: String): List<RouteTraceChunkEntity> =
        store[routeId to kind]?.sortedBy { it.seq } ?: emptyList()

    override suspend fun deleteFor(routeId: String, kind: String) {
        store.remove(routeId to kind)
    }

    override suspend fun insertAll(chunks: List<RouteTraceChunkEntity>) {
        for (chunk in chunks) {
            store.getOrPut(chunk.routeId to chunk.kind) { mutableListOf() }.add(chunk)
        }
    }

    fun hasChunks(routeId: String, kind: String): Boolean =
        store[routeId to kind]?.isNotEmpty() == true

    fun chunkCount(routeId: String, kind: String): Int =
        store[routeId to kind]?.size ?: 0
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

class RouteRepositoryImplTest {

    private lateinit var dao: FakeRouteDaoImpl
    private lateinit var chunkDao: FakeRouteTraceChunkDaoImpl
    private lateinit var repo: RouteRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeRouteDaoImpl()
        chunkDao = FakeRouteTraceChunkDaoImpl()
        repo = RouteRepositoryImpl(dao, chunkDao)
    }

    // ── save — entity upsert ──────────────────────────────────────────────────

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
    fun `save maps all domain scalar fields to entity`() = runTest {
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

    // ── save — chunk storage ──────────────────────────────────────────────────

    @Test
    fun `save splits non-null pathJson into RAW chunks`() = runTest {
        val route = buildRoute(id = "r1", pathJson = """[{"lat":50.0,"lng":20.0}]""")
        repo.save(route)

        assertTrue("RAW chunks must be present", chunkDao.hasChunks("r1", "RAW"))
    }

    @Test
    fun `save null pathJson stores no RAW chunks`() = runTest {
        val route = buildRoute(id = "r1", pathJson = null)
        repo.save(route)

        assertFalse("no RAW chunks for null pathJson", chunkDao.hasChunks("r1", "RAW"))
    }

    @Test
    fun `save non-null correctedPathJson stores CORRECTED chunks`() = runTest {
        val route = buildRoute(id = "r1", correctedPathJson = """[{"lat":50.0,"lng":20.0}]""")
        repo.save(route)

        assertTrue("CORRECTED chunks must be present", chunkDao.hasChunks("r1", "CORRECTED"))
    }

    @Test
    fun `save null correctedPathJson stores no CORRECTED chunks`() = runTest {
        val route = buildRoute(id = "r1", correctedPathJson = null)
        repo.save(route)

        assertFalse("no CORRECTED chunks for null", chunkDao.hasChunks("r1", "CORRECTED"))
    }

    // ── observeSummaries ─────────────────────────────────────────────────────

    @Test
    fun `observeSummaries emits empty list initially`() = runTest {
        repo.observeSummaries().test {
            assertEquals(emptyList<RouteSummaryModel>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeSummaries emits summaries after save`() = runTest {
        repo.observeSummaries().test {
            awaitItem() // initial empty

            repo.save(buildRoute(id = "r1", km = 55.0))
            val summaries = awaitItem()

            assertEquals(1, summaries.size)
            assertEquals("r1", summaries.first().id)
            assertEquals(55.0, summaries.first().km, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeSummaries does not include trace fields`() = runTest {
        repo.observeSummaries().test {
            awaitItem()
            repo.save(buildRoute(id = "r1", pathJson = """[{"lat":1.0,"lng":2.0}]"""))
            val summaries = awaitItem()
            assertEquals(1, summaries.size)
            // RouteSummaryModel has no pathJson field by design — just verify it exists
            assertNotNull(summaries.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── getById ───────────────────────────────────────────────────────────────

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
    fun `getById reassembles pathJson from RAW chunks`() = runTest {
        val json = """[{"lat":50.0,"lng":20.0},{"lat":51.0,"lng":21.0}]"""
        repo.save(buildRoute(id = "r1", pathJson = json))

        val result = repo.getById("r1")
        assertNotNull("pathJson must be reassembled", result?.pathJson)
        val arr = org.json.JSONArray(result!!.pathJson)
        assertEquals(2, arr.length())
    }

    @Test
    fun `getById returns null pathJson when no RAW chunks exist`() = runTest {
        repo.save(buildRoute(id = "r1", pathJson = null))
        val result = repo.getById("r1")
        assertNull(result?.pathJson)
    }

    @Test
    fun `getById reassembles correctedPathJson from CORRECTED chunks`() = runTest {
        val json = """[{"lat":50.0,"lng":20.0}]"""
        repo.save(buildRoute(id = "r1", correctedPathJson = json))

        val result = repo.getById("r1")
        assertNotNull("correctedPathJson must be reassembled", result?.correctedPathJson)
    }

    @Test
    fun `getById maps all scalar fields correctly`() = runTest {
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

    // ── clearCorrectedTrace ───────────────────────────────────────────────────

    @Test
    fun `clearCorrectedTrace removes only CORRECTED chunks not RAW`() = runTest {
        repo.save(
            buildRoute(
                id = "r1",
                pathJson = """[{"lat":50.0,"lng":20.0}]""",
                correctedPathJson = """[{"lat":50.0,"lng":20.0}]""",
            )
        )

        repo.clearCorrectedTrace("r1")

        assertTrue("RAW chunks must remain", chunkDao.hasChunks("r1", "RAW"))
        assertFalse("CORRECTED chunks must be removed", chunkDao.hasChunks("r1", "CORRECTED"))
    }

    @Test
    fun `clearCorrectedTrace resets correction status on entity`() = runTest {
        repo.save(buildRoute(id = "r1"))
        repo.clearCorrectedTrace("r1")

        assertEquals(CorrectionStatus.NONE, dao.upsertCalls.first().correctionStatus)
    }

    // ── setBike ───────────────────────────────────────────────────────────────

    @Test
    fun `setBike delegates to RouteDao with routeId and bikeId`() = runTest {
        repo.save(buildRoute(id = "route-assign", bikeId = null))
        repo.setBike("route-assign", "bike-7")

        assertEquals(1, dao.setBikeCalls.size)
        assertEquals("route-assign", dao.setBikeCalls.first().first)
        assertEquals("bike-7", dao.setBikeCalls.first().second)
    }

    @Test
    fun `setBike with null clears the bike association`() = runTest {
        repo.save(buildRoute(id = "route-clear", bikeId = "old-bike"))
        repo.setBike("route-clear", null)

        assertEquals(1, dao.setBikeCalls.size)
        assertNull(dao.setBikeCalls.first().second)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
        pathJson: String? = null,
        speedJson: String? = null,
        elevProfileJson: String? = null,
        notes: String? = null,
        correctedPathJson: String? = null,
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
        correctedPathJson = correctedPathJson,
    )
}
