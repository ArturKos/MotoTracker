package com.mototracker.data.repository

import app.cash.turbine.test
import com.mototracker.core.time.TimeProvider
import com.mototracker.data.local.dao.CorrectionQueueDao
import com.mototracker.data.local.dao.RouteDao
import com.mototracker.data.local.entity.CorrectionQueueEntity
import com.mototracker.data.local.entity.CorrectionQueueState
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.local.entity.RouteEntity
import com.mototracker.data.network.CorrectionOutcome
import com.mototracker.data.network.GpsCorrectionClient
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.network.TrackPoint
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.domain.SyncRetryPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ---------------------------------------------------------------------------
// Fakes — prefixed with "Corr" to avoid name clashes with fakes in SyncRepositoryImplTest
// ---------------------------------------------------------------------------

private class CorrFakeTimeProvider(private var time: Long = 1_000L) : TimeProvider {
    override fun nowEpochMs(): Long = time
    fun advanceMs(ms: Long) { time += ms }
}

private class CorrFakeNetworkMonitor(initialOnline: Boolean = true) : NetworkMonitor {
    private val _isOnline = MutableStateFlow(initialOnline)
    override val isOnline: Flow<Boolean> = _isOnline
    fun setOnline(online: Boolean) { _isOnline.value = online }
}

private class CorrFakeAppSettingsSource(
    initialSettings: AppSettings = AppSettings(),
) : AppSettingsSource {
    private val _settings = MutableStateFlow(initialSettings)
    override val settings: Flow<AppSettings> = _settings
    fun emit(settings: AppSettings) { _settings.value = settings }
}

private class FakeGpsCorrectionClient : GpsCorrectionClient {
    val calls = mutableListOf<Pair<String, List<TrackPoint>>>()
    private var nextResult: Result<CorrectionOutcome> =
        Result.success(CorrectionOutcome.Matched(emptyList(), 0.9, 0.9))

    fun setResult(outcome: CorrectionOutcome) { nextResult = Result.success(outcome) }
    fun setFailure(error: Throwable = RuntimeException("osrm error")) { nextResult = Result.failure(error) }

    override suspend fun match(osrmBaseUrl: String, points: List<TrackPoint>): Result<CorrectionOutcome> {
        calls.add(osrmBaseUrl to points)
        return nextResult
    }
}

private class FakeCorrectionQueueDao : CorrectionQueueDao {
    private val _entries = mutableListOf<CorrectionQueueEntity>()
    private val _pendingFlow = MutableStateFlow<List<CorrectionQueueEntity>>(emptyList())
    private val _countFlow = MutableStateFlow(0)
    private var _nextId = 1L

    override suspend fun upsert(entity: CorrectionQueueEntity) {
        val withId = if (entity.id == 0L) entity.copy(id = _nextId++) else entity
        val idx = _entries.indexOfFirst { it.id == withId.id }
        if (idx >= 0) _entries[idx] = withId else _entries.add(withId)
        refresh()
    }

    override suspend fun delete(entity: CorrectionQueueEntity) {
        _entries.removeAll { it.id == entity.id }
        refresh()
    }

    override fun getPending(): Flow<List<CorrectionQueueEntity>> = _pendingFlow

    override suspend fun pruneDone() {
        _entries.removeAll { it.state == CorrectionQueueState.DONE }
        refresh()
    }

    override fun getPendingCount(): Flow<Int> = _countFlow

    override suspend fun findByRouteId(routeId: String): CorrectionQueueEntity? =
        _entries.firstOrNull { it.routeId == routeId }

    override suspend fun getPendingSnapshot(): List<CorrectionQueueEntity> =
        _entries.filter { it.state != CorrectionQueueState.DONE }
            .sortedWith(compareBy(nullsFirst()) { it.nextRetryEpochMs })

    private fun refresh() {
        val nonDone = _entries.filter { it.state != CorrectionQueueState.DONE }
        _pendingFlow.value = nonDone.sortedWith(compareBy(nullsFirst()) { it.nextRetryEpochMs })
        _countFlow.value = nonDone.size
    }

    fun find(routeId: String): CorrectionQueueEntity? = _entries.firstOrNull { it.routeId == routeId }
}

private class CorrFakeRouteDao : RouteDao {
    private val _routes = mutableMapOf<String, RouteEntity>()
    private val _allFlow = MutableStateFlow<List<RouteEntity>>(emptyList())

    override suspend fun upsert(entity: RouteEntity) {
        _routes[entity.id] = entity
        _allFlow.value = _routes.values.toList()
    }

    override suspend fun delete(entity: RouteEntity) {
        _routes.remove(entity.id)
        _allFlow.value = _routes.values.toList()
    }

    override fun getAll(): Flow<List<RouteEntity>> = _allFlow
    override suspend fun getById(id: String): RouteEntity? = _routes[id]
    override fun observeById(id: String): Flow<RouteEntity?> = MutableStateFlow(_routes[id])

    override suspend fun setSynced(id: String, synced: Boolean) {
        _routes[id]?.let { _routes[id] = it.copy(synced = synced) }
        _allFlow.value = _routes.values.toList()
    }

    override suspend fun clearCorrection(id: String) {
        _routes[id]?.let { _routes[id] = it.copy(correctedPathJson = null, correctionStatus = com.mototracker.data.local.entity.CorrectionStatus.NONE, confidence = null) }
        _allFlow.value = _routes.values.toList()
    }

    override suspend fun setName(id: String, name: String) {
        _routes[id]?.let { _routes[id] = it.copy(name = name) }
        _allFlow.value = _routes.values.toList()
    }

    override suspend fun deleteAll() {
        _routes.clear()
        _allFlow.value = emptyList()
    }

    fun find(id: String): RouteEntity? = _routes[id]
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private val BASE_CORR_SETTINGS = AppSettings(
    offlineOnly = false,
    osrmBaseUrl = "http://osrm.test:5001",
)

private const val SAMPLE_PATH_JSON = """[{"lat":50.0,"lng":20.0},{"lat":50.001,"lng":20.001}]"""

private fun corrRouteEntity(id: String, pathJson: String? = SAMPLE_PATH_JSON) = RouteEntity(
    id = id, name = "Route $id", dateEpochMs = 0L, bikeId = null,
    km = 10.0, durSec = 3600L, avg = 10.0, max = 20.0, lean = 5.0,
    elev = 100.0, fuel = 1.0, synced = false,
    wxJson = null, pathJson = pathJson, speedJson = null, elevProfileJson = null, notes = null,
)

private fun corrPendingEntry(routeId: String, nextRetryMs: Long? = null, attemptCount: Int = 0) =
    CorrectionQueueEntity(
        id = 0L,
        routeId = routeId,
        state = CorrectionQueueState.PENDING,
        attemptCount = attemptCount,
        lastAttemptEpochMs = null,
        nextRetryEpochMs = nextRetryMs,
        lastError = null,
    )

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class GpsCorrectionRepositoryImplTest {

    private lateinit var queueDao: FakeCorrectionQueueDao
    private lateinit var routeDao: CorrFakeRouteDao
    private lateinit var settingsSource: CorrFakeAppSettingsSource
    private lateinit var client: FakeGpsCorrectionClient
    private lateinit var networkMonitor: CorrFakeNetworkMonitor
    private lateinit var timeProvider: CorrFakeTimeProvider
    private lateinit var repo: GpsCorrectionRepositoryImpl

    @Before
    fun setUp() {
        queueDao = FakeCorrectionQueueDao()
        routeDao = CorrFakeRouteDao()
        settingsSource = CorrFakeAppSettingsSource(BASE_CORR_SETTINGS)
        client = FakeGpsCorrectionClient()
        networkMonitor = CorrFakeNetworkMonitor(initialOnline = true)
        timeProvider = CorrFakeTimeProvider(time = 10_000L)
        repo = GpsCorrectionRepositoryImpl(
            correctionQueueDao = queueDao,
            routeDao = routeDao,
            settingsSource = settingsSource,
            client = client,
            networkMonitor = networkMonitor,
            timeProvider = timeProvider,
        )
    }

    // ── enqueue ─────────────────────────────────────────────────────────────

    @Test
    fun `enqueue inserts a PENDING entry`() = runTest {
        repo.enqueue("r1")

        val entry = queueDao.find("r1")
        assertNotNull(entry)
        assertEquals(CorrectionQueueState.PENDING, entry!!.state)
        assertEquals(0, entry.attemptCount)
        assertNull(entry.nextRetryEpochMs)
    }

    @Test
    fun `enqueue second time resets entry to PENDING`() = runTest {
        repo.enqueue("r1")
        val existing = queueDao.find("r1")!!
        queueDao.upsert(existing.copy(state = CorrectionQueueState.FAILED, attemptCount = 3))

        repo.enqueue("r1")

        val entry = queueDao.find("r1")!!
        assertEquals(CorrectionQueueState.PENDING, entry.state)
        assertEquals(0, entry.attemptCount)
    }

    // ── pendingCount ─────────────────────────────────────────────────────────

    @Test
    fun `pendingCount reflects enqueue`() = runTest {
        repo.pendingCount.test {
            assertEquals(0, awaitItem())
            repo.enqueue("r1")
            assertEquals(1, awaitItem())
            repo.enqueue("r2")
            assertEquals(2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── success / ACCEPT path ────────────────────────────────────────────────

    @Test
    fun `ACCEPT outcome writes correctedPathJson and sets correctionStatus DONE`() = runTest {
        client.setResult(
            CorrectionOutcome.Matched(
                points = listOf(TrackPoint(50.001, 20.001)),
                confidence = 0.9,
                matchedFraction = 0.9,
            ),
        )
        routeDao.upsert(corrRouteEntity("r1"))
        repo.enqueue("r1")

        val count = repo.correctNow()

        assertEquals(1, count)
        val route = routeDao.find("r1")!!
        assertNotNull(route.correctedPathJson)
        assertEquals(CorrectionStatus.DONE, route.correctionStatus)
        assertEquals(0.9, route.confidence!!, 0.001)
        assertNull(queueDao.find("r1"))
    }

    @Test
    fun `pathJson is never mutated on ACCEPT`() = runTest {
        client.setResult(
            CorrectionOutcome.Matched(listOf(TrackPoint(50.001, 20.001)), 0.9, 0.9),
        )
        routeDao.upsert(corrRouteEntity("r1"))
        repo.enqueue("r1")

        repo.correctNow()

        assertEquals(SAMPLE_PATH_JSON, routeDao.find("r1")!!.pathJson)
    }

    @Test
    fun `correctNow passes configured osrmBaseUrl to client`() = runTest {
        client.setResult(CorrectionOutcome.Matched(listOf(TrackPoint(50.0, 20.0)), 0.9, 0.9))
        routeDao.upsert(corrRouteEntity("r1"))
        repo.enqueue("r1")

        repo.correctNow()

        assertEquals("http://osrm.test:5001", client.calls.first().first)
    }

    // ── LOW_CONFIDENCE path ───────────────────────────────────────────────────

    @Test
    fun `LOW_CONFIDENCE keeps raw trace and sets correctionStatus LOW_CONFIDENCE`() = runTest {
        client.setResult(
            CorrectionOutcome.Matched(
                points = listOf(TrackPoint(50.0, 20.0)),
                confidence = 0.4,   // below DEFAULT 0.6
                matchedFraction = 0.9,
            ),
        )
        routeDao.upsert(corrRouteEntity("r1"))
        repo.enqueue("r1")

        val count = repo.correctNow()

        assertEquals(0, count)
        val route = routeDao.find("r1")!!
        assertNull("correctedPathJson should remain null on LOW_CONFIDENCE", route.correctedPathJson)
        assertEquals(CorrectionStatus.LOW_CONFIDENCE, route.correctionStatus)
        assertEquals(0.4, route.confidence!!, 0.001)
        assertEquals(SAMPLE_PATH_JSON, route.pathJson)
    }

    @Test
    fun `LOW_CONFIDENCE queue entry is pruned`() = runTest {
        client.setResult(
            CorrectionOutcome.Matched(listOf(TrackPoint(50.0, 20.0)), 0.3, 0.3),
        )
        routeDao.upsert(corrRouteEntity("r1"))
        repo.enqueue("r1")

        repo.correctNow()

        assertNull(queueDao.find("r1"))
    }

    // ── REJECT (NoMatch) path ────────────────────────────────────────────────

    @Test
    fun `NoMatch keeps raw trace and sets correctionStatus NONE`() = runTest {
        client.setResult(CorrectionOutcome.NoMatch)
        routeDao.upsert(corrRouteEntity("r1"))
        repo.enqueue("r1")

        val count = repo.correctNow()

        assertEquals(0, count)
        val route = routeDao.find("r1")!!
        assertNull(route.correctedPathJson)
        assertEquals(CorrectionStatus.NONE, route.correctionStatus)
        assertEquals(SAMPLE_PATH_JSON, route.pathJson)
    }

    // ── failure / retry ───────────────────────────────────────────────────────

    @Test
    fun `failure sets FAILED and records backoff timestamp`() = runTest {
        client.setFailure(RuntimeException("timeout"))
        routeDao.upsert(corrRouteEntity("r1"))
        repo.enqueue("r1")

        val count = repo.correctNow()

        assertEquals(0, count)
        val entry = queueDao.find("r1")!!
        assertEquals(CorrectionQueueState.FAILED, entry.state)
        assertEquals(1, entry.attemptCount)
        assertEquals(10_000L, entry.lastAttemptEpochMs)
        assertEquals("timeout", entry.lastError)
        val expectedDelay = SyncRetryPolicy.nextRetryDelayMs(1)
        assertEquals(10_000L + expectedDelay, entry.nextRetryEpochMs)
    }

    @Test
    fun `pathJson is never mutated on failure`() = runTest {
        client.setFailure()
        routeDao.upsert(corrRouteEntity("r1"))
        repo.enqueue("r1")

        repo.correctNow()

        assertEquals(SAMPLE_PATH_JSON, routeDao.find("r1")!!.pathJson)
    }

    @Test
    fun `second failure doubles the backoff`() = runTest {
        client.setFailure()
        routeDao.upsert(corrRouteEntity("r1"))
        repo.enqueue("r1")

        repo.correctNow()
        val after1 = queueDao.find("r1")!!
        queueDao.upsert(after1.copy(state = CorrectionQueueState.PENDING, nextRetryEpochMs = null))

        timeProvider.advanceMs(1_000L)
        repo.correctNow()

        val after2 = queueDao.find("r1")!!
        assertEquals(2, after2.attemptCount)
        val expectedDelay = SyncRetryPolicy.nextRetryDelayMs(2)
        assertEquals(11_000L + expectedDelay, after2.nextRetryEpochMs)
    }

    // ── offlineOnly gating ────────────────────────────────────────────────────

    @Test
    fun `correctNow returns 0 and makes no calls when offlineOnly is true`() = runTest {
        settingsSource.emit(BASE_CORR_SETTINGS.copy(offlineOnly = true))
        routeDao.upsert(corrRouteEntity("r1"))
        repo.enqueue("r1")

        val count = repo.correctNow()

        assertEquals(0, count)
        assertTrue(client.calls.isEmpty())
    }

    // ── future nextRetryEpochMs ───────────────────────────────────────────────

    @Test
    fun `entry with future nextRetryEpochMs is skipped`() = runTest {
        routeDao.upsert(corrRouteEntity("r1"))
        queueDao.upsert(corrPendingEntry("r1", nextRetryMs = 99_000L))

        val count = repo.correctNow()

        assertEquals(0, count)
        assertTrue(client.calls.isEmpty())
    }

    // ── missing route ─────────────────────────────────────────────────────────

    @Test
    fun `entry is removed when backing route is missing`() = runTest {
        repo.enqueue("ghost-route")

        val count = repo.correctNow()

        assertEquals(0, count)
        assertNull(queueDao.find("ghost-route"))
        assertTrue(client.calls.isEmpty())
    }

    // ── auto-drain ────────────────────────────────────────────────────────────

    @Test
    fun `auto-drain processes entry when online`() = runTest {
        client.setResult(CorrectionOutcome.Matched(listOf(TrackPoint(50.0, 20.0)), 0.9, 0.9))
        routeDao.upsert(corrRouteEntity("r1"))
        repo.enqueue("r1")

        repo.start(backgroundScope)
        advanceUntilIdle()

        assertEquals(1, client.calls.size)
        assertEquals(CorrectionStatus.DONE, routeDao.find("r1")!!.correctionStatus)
        assertNull(queueDao.find("r1"))
    }

    @Test
    fun `auto-drain does not run when network is offline`() = runTest {
        networkMonitor.setOnline(false)
        routeDao.upsert(corrRouteEntity("r1"))
        repo.enqueue("r1")

        repo.start(backgroundScope)
        advanceUntilIdle()

        assertTrue(client.calls.isEmpty())
        assertNotNull(queueDao.find("r1"))
    }

    @Test
    fun `auto-drain does not run when offlineOnly is true`() = runTest {
        settingsSource.emit(BASE_CORR_SETTINGS.copy(offlineOnly = true))
        routeDao.upsert(corrRouteEntity("r1"))
        repo.enqueue("r1")

        repo.start(backgroundScope)
        advanceUntilIdle()

        assertTrue(client.calls.isEmpty())
        assertNotNull(queueDao.find("r1"))
    }

    @Test
    fun `auto-drain triggers when network comes back online`() = runTest {
        networkMonitor.setOnline(false)
        client.setResult(CorrectionOutcome.Matched(listOf(TrackPoint(50.0, 20.0)), 0.9, 0.9))
        routeDao.upsert(corrRouteEntity("r1"))
        repo.enqueue("r1")

        repo.start(backgroundScope)
        advanceUntilIdle()
        assertTrue(client.calls.isEmpty())

        networkMonitor.setOnline(true)
        advanceUntilIdle()

        assertEquals(1, client.calls.size)
    }
}
