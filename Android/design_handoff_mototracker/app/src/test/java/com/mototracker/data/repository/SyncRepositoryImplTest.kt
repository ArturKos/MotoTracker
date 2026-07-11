package com.mototracker.data.repository

import app.cash.turbine.test
import com.mototracker.core.time.TimeProvider
import com.mototracker.data.local.dao.RouteDao
import com.mototracker.data.local.dao.SyncQueueDao
import com.mototracker.data.local.entity.RouteEntity
import com.mototracker.data.local.entity.SyncQueueEntity
import com.mototracker.data.local.entity.SyncQueueState
import com.mototracker.data.model.Route
import com.mototracker.data.network.GpStrackClient
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.domain.SyncRetryPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

private class FakeTimeProvider(private var time: Long = 1_000L) : TimeProvider {
    override fun nowEpochMs(): Long = time
    fun advanceMs(ms: Long) { time += ms }
}

private class FakeNetworkMonitor(initialOnline: Boolean = true) : NetworkMonitor {
    private val _isOnline = MutableStateFlow(initialOnline)
    override val isOnline: Flow<Boolean> = _isOnline
    fun setOnline(online: Boolean) { _isOnline.value = online }
}

private class FakeAppSettingsSource(
    initialSettings: AppSettings = AppSettings(),
) : AppSettingsSource {
    private val _settings = MutableStateFlow(initialSettings)
    override val settings: Flow<AppSettings> = _settings
    fun emit(settings: AppSettings) { _settings.value = settings }
}

private class FakeGpStrackClient : GpStrackClient {
    val calls = mutableListOf<Pair<String, Route>>()
    private var nextResult: Result<Unit> = Result.success(Unit)

    fun setFailure(error: Throwable = RuntimeException("upload error")) {
        nextResult = Result.failure(error)
    }
    fun setSuccess() { nextResult = Result.success(Unit) }

    override suspend fun login(serverAddress: String, email: String, password: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun uploadRoute(serverAddress: String, route: Route): Result<Unit> {
        println("DEBUG uploadRoute called: $serverAddress, ${route.id}")
        calls.add(serverAddress to route)
        return nextResult
    }
}

private class FakeSyncQueueDao : SyncQueueDao {
    private val _entries = mutableListOf<SyncQueueEntity>()
    private val _pendingFlow = MutableStateFlow<List<SyncQueueEntity>>(emptyList())
    private val _countFlow = MutableStateFlow(0)
    private var _nextId = 1L

    override suspend fun upsert(entity: SyncQueueEntity) {
        val withId = if (entity.id == 0L) entity.copy(id = _nextId++) else entity
        val idx = _entries.indexOfFirst { it.id == withId.id }
        if (idx >= 0) _entries[idx] = withId else _entries.add(withId)
        refresh()
    }

    override suspend fun delete(entity: SyncQueueEntity) {
        _entries.removeAll { it.id == entity.id }
        refresh()
    }

    override fun getPending(): Flow<List<SyncQueueEntity>> = _pendingFlow

    override suspend fun pruneDone() {
        _entries.removeAll { it.state == SyncQueueState.DONE }
        refresh()
    }

    override fun getPendingCount(): Flow<Int> = _countFlow

    private fun refresh() {
        val nonDone = _entries.filter { it.state != SyncQueueState.DONE }
        _pendingFlow.value = nonDone.sortedWith(
            compareBy(nullsFirst()) { it.nextRetryEpochMs }
        )
        _countFlow.value = nonDone.size
    }

    override suspend fun findByRouteId(routeId: String): SyncQueueEntity? =
        _entries.firstOrNull { it.routeId == routeId }

    override suspend fun getPendingSnapshot(): List<SyncQueueEntity> {
        val result = _entries.filter { it.state != SyncQueueState.DONE }
            .sortedWith(compareBy(nullsFirst()) { it.nextRetryEpochMs })
        println("DEBUG getPendingSnapshot: ${result.size} entries: ${result.map { "${it.routeId}/${it.state}" }}")
        return result
    }

    /** Test helper: returns all stored entries regardless of state. */
    fun all(): List<SyncQueueEntity> = _entries.toList()

    /** Test helper: returns the first entry for [routeId]. */
    fun find(routeId: String): SyncQueueEntity? = _entries.firstOrNull { it.routeId == routeId }
}

private class FakeRouteDao : RouteDao {
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

    override suspend fun setSynced(id: String, synced: Boolean) {
        _routes[id]?.let { _routes[id] = it.copy(synced = synced) }
        _allFlow.value = _routes.values.toList()
    }

    fun find(id: String): RouteEntity? = _routes[id]
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun routeEntity(id: String) = RouteEntity(
    id = id, name = "Route $id", dateEpochMs = 0L, bikeId = null,
    km = 0.0, durSec = 0L, avg = 0.0, max = 0.0, lean = 0.0, elev = 0.0, fuel = 0.0,
    synced = false, wxJson = null, pathJson = null, speedJson = null, elevProfileJson = null,
    notes = null,
)

private fun pendingEntry(routeId: String, nextRetryMs: Long? = null, attemptCount: Int = 0) =
    SyncQueueEntity(
        id = 0L,
        routeId = routeId,
        state = SyncQueueState.PENDING,
        attemptCount = attemptCount,
        lastAttemptEpochMs = null,
        nextRetryEpochMs = nextRetryMs,
        lastError = null,
    )

private val onlineAutoSyncSettings = AppSettings(
    offline = false,
    autoSync = true,
    offlineOnly = false,
    serverAddress = "http://test-server/gpstrack",
)

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class SyncRepositoryImplTest {

    private lateinit var syncQueueDao: FakeSyncQueueDao
    private lateinit var routeDao: FakeRouteDao
    private lateinit var settingsSource: FakeAppSettingsSource
    private lateinit var client: FakeGpStrackClient
    private lateinit var networkMonitor: FakeNetworkMonitor
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var repo: SyncRepositoryImpl

    @Before
    fun setUp() {
        syncQueueDao = FakeSyncQueueDao()
        routeDao = FakeRouteDao()
        settingsSource = FakeAppSettingsSource(onlineAutoSyncSettings)
        client = FakeGpStrackClient()
        networkMonitor = FakeNetworkMonitor(initialOnline = true)
        timeProvider = FakeTimeProvider(time = 10_000L)
        repo = SyncRepositoryImpl(
            syncQueueDao = syncQueueDao,
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

        val entry = syncQueueDao.find("r1")
        assertNotNull(entry)
        assertEquals(SyncQueueState.PENDING, entry!!.state)
        assertEquals(0, entry.attemptCount)
        assertNull(entry.nextRetryEpochMs)
        assertNull(entry.lastError)
    }

    @Test
    fun `enqueue second time resets the entry to PENDING`() = runTest {
        repo.enqueue("r1")
        // Simulate a failed entry existing already
        val existing = syncQueueDao.find("r1")!!
        syncQueueDao.upsert(existing.copy(state = SyncQueueState.FAILED, attemptCount = 3))

        repo.enqueue("r1")

        val entry = syncQueueDao.find("r1")!!
        assertEquals(SyncQueueState.PENDING, entry.state)
        assertEquals(0, entry.attemptCount)
    }

    // ── pendingCount ─────────────────────────────────────────────────────────

    @Test
    fun `pendingCount reflects enqueue and successful sync`() = runTest {
        repo.pendingCount.test {
            assertEquals(0, awaitItem())

            routeDao.upsert(routeEntity("r1"))
            repo.enqueue("r1")
            assertEquals(1, awaitItem())

            routeDao.upsert(routeEntity("r2"))
            repo.enqueue("r2")
            assertEquals(2, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pendingCount drops to zero after successful syncNow`() = runTest {
        routeDao.upsert(routeEntity("r1"))
        repo.enqueue("r1")

        repo.pendingCount.test {
            assertEquals(1, awaitItem())

            repo.syncNow()
            assertEquals(0, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── successful upload ────────────────────────────────────────────────────

    @Test
    fun `syncNow marks route as synced and removes queue entry on success`() = runTest {
        routeDao.upsert(routeEntity("r1"))
        repo.enqueue("r1")

        val count = repo.syncNow()

        assertEquals(1, count)
        assertTrue(routeDao.find("r1")!!.synced)
        // After pruneDone the entry is removed
        assertNull(syncQueueDao.find("r1"))
        assertEquals(1, client.calls.size)
        assertEquals("r1", client.calls[0].second.id)
    }

    @Test
    fun `syncNow passes the configured serverAddress to the client`() = runTest {
        routeDao.upsert(routeEntity("r1"))
        repo.enqueue("r1")

        repo.syncNow()

        assertEquals("http://test-server/gpstrack", client.calls[0].first)
    }

    // ── failed upload ────────────────────────────────────────────────────────

    @Test
    fun `failed upload increments attemptCount and sets FAILED with backoff`() = runTest {
        client.setFailure(RuntimeException("timeout"))
        routeDao.upsert(routeEntity("r1"))
        repo.enqueue("r1")

        val count = repo.syncNow()

        assertEquals(0, count)
        val entry = syncQueueDao.find("r1")!!
        assertEquals(SyncQueueState.FAILED, entry.state)
        assertEquals(1, entry.attemptCount)
        assertEquals(10_000L, entry.lastAttemptEpochMs)
        assertEquals("timeout", entry.lastError)

        // nextRetry = now + SyncRetryPolicy.nextRetryDelayMs(1) = 10_000 + 60_000 = 70_000
        val expectedDelay = SyncRetryPolicy.nextRetryDelayMs(1)
        assertEquals(10_000L + expectedDelay, entry.nextRetryEpochMs)
    }

    @Test
    fun `second failure doubles the backoff delay`() = runTest {
        client.setFailure()
        routeDao.upsert(routeEntity("r1"))
        repo.enqueue("r1")

        // First failure
        repo.syncNow()
        val after1 = syncQueueDao.find("r1")!!
        assertEquals(1, after1.attemptCount)

        // Reset nextRetryEpochMs to null so it's due again
        syncQueueDao.upsert(after1.copy(state = SyncQueueState.PENDING, nextRetryEpochMs = null))

        timeProvider.advanceMs(1_000L) // now = 11_000

        // Second failure
        repo.syncNow()
        val after2 = syncQueueDao.find("r1")!!
        assertEquals(2, after2.attemptCount)

        val expectedDelay = SyncRetryPolicy.nextRetryDelayMs(2)
        assertEquals(11_000L + expectedDelay, after2.nextRetryEpochMs)
    }

    // ── entries not yet due are skipped ──────────────────────────────────────

    @Test
    fun `entry with future nextRetryEpochMs is skipped`() = runTest {
        routeDao.upsert(routeEntity("r1"))
        // Set nextRetry far in the future (now=10_000, nextRetry=99_000)
        syncQueueDao.upsert(pendingEntry("r1", nextRetryMs = 99_000L))

        val count = repo.syncNow()

        assertEquals(0, count)
        assertTrue(client.calls.isEmpty())
        assertEquals(SyncQueueState.PENDING, syncQueueDao.find("r1")!!.state)
    }

    @Test
    fun `entry with nextRetryEpochMs exactly equal to now is processed`() = runTest {
        routeDao.upsert(routeEntity("r1"))
        // nextRetry == now (10_000)
        syncQueueDao.upsert(pendingEntry("r1", nextRetryMs = 10_000L))

        val count = repo.syncNow()

        assertEquals(1, count)
    }

    @Test
    fun `entry with past nextRetryEpochMs is processed`() = runTest {
        routeDao.upsert(routeEntity("r1"))
        // nextRetry in the past
        syncQueueDao.upsert(pendingEntry("r1", nextRetryMs = 5_000L))

        val count = repo.syncNow()

        assertEquals(1, count)
    }

    // ── missing route ────────────────────────────────────────────────────────

    @Test
    fun `entry is removed when backing route is not found`() = runTest {
        // Enqueue without inserting the route
        repo.enqueue("missing-route")

        val count = repo.syncNow()

        assertEquals(0, count)
        assertNull(syncQueueDao.find("missing-route"))
        assertTrue(client.calls.isEmpty())
    }

    // ── syncNow respects offlineOnly ─────────────────────────────────────────

    @Test
    fun `syncNow returns 0 and makes no HTTP calls when offlineOnly is true`() = runTest {
        settingsSource.emit(onlineAutoSyncSettings.copy(offlineOnly = true))
        routeDao.upsert(routeEntity("r1"))
        repo.enqueue("r1")

        val count = repo.syncNow()

        assertEquals(0, count)
        assertTrue(client.calls.isEmpty())
        assertEquals(SyncQueueState.PENDING, syncQueueDao.find("r1")!!.state)
    }

    @Test
    fun `syncNow runs even when autoSync is false`() = runTest {
        settingsSource.emit(onlineAutoSyncSettings.copy(autoSync = false))
        routeDao.upsert(routeEntity("r1"))
        repo.enqueue("r1")

        val count = repo.syncNow()

        assertEquals(1, count)
    }

    // ── auto-drain gated OFF ─────────────────────────────────────────────────

    @Test
    fun `auto-drain does not run when offline flag is true`() = runTest {
        settingsSource.emit(onlineAutoSyncSettings.copy(offline = true))
        routeDao.upsert(routeEntity("r1"))
        repo.enqueue("r1")

        repo.start(backgroundScope)
        advanceUntilIdle()

        assertTrue(client.calls.isEmpty())
        assertNotNull(syncQueueDao.find("r1"))
    }

    @Test
    fun `auto-drain does not run when offlineOnly is true`() = runTest {
        settingsSource.emit(onlineAutoSyncSettings.copy(offlineOnly = true))
        routeDao.upsert(routeEntity("r1"))
        repo.enqueue("r1")

        repo.start(backgroundScope)
        advanceUntilIdle()

        assertTrue(client.calls.isEmpty())
        assertNotNull(syncQueueDao.find("r1"))
    }

    @Test
    fun `auto-drain does not run when autoSync is false`() = runTest {
        settingsSource.emit(onlineAutoSyncSettings.copy(autoSync = false))
        routeDao.upsert(routeEntity("r1"))
        repo.enqueue("r1")

        repo.start(backgroundScope)
        advanceUntilIdle()

        assertTrue(client.calls.isEmpty())
        assertNotNull(syncQueueDao.find("r1"))
    }

    @Test
    fun `auto-drain does not run when network is offline`() = runTest {
        networkMonitor.setOnline(false)
        routeDao.upsert(routeEntity("r1"))
        repo.enqueue("r1")

        repo.start(backgroundScope)
        advanceUntilIdle()

        assertTrue(client.calls.isEmpty())
        assertNotNull(syncQueueDao.find("r1"))
    }

    // ── auto-drain ON ────────────────────────────────────────────────────────

    @Test
    fun `auto-drain uploads pending entry when online and autoSync`() = runTest {
        routeDao.upsert(routeEntity("r1"))
        repo.enqueue("r1")

        repo.start(backgroundScope)
        advanceUntilIdle()

        assertEquals(1, client.calls.size)
        assertTrue(routeDao.find("r1")!!.synced)
        assertNull(syncQueueDao.find("r1"))
    }

    @Test
    fun `auto-drain triggers when network comes back online`() = runTest {
        networkMonitor.setOnline(false)
        routeDao.upsert(routeEntity("r1"))
        repo.enqueue("r1")

        repo.start(backgroundScope)
        advanceUntilIdle()

        // No upload yet
        assertTrue(client.calls.isEmpty())

        // Network comes back
        networkMonitor.setOnline(true)
        advanceUntilIdle()

        assertEquals(1, client.calls.size)
        assertTrue(routeDao.find("r1")!!.synced)
    }

    @Test
    fun `auto-drain skips entry with future nextRetryEpochMs`() = runTest {
        routeDao.upsert(routeEntity("r1"))
        syncQueueDao.upsert(pendingEntry("r1", nextRetryMs = 99_000L))

        repo.start(backgroundScope)
        advanceUntilIdle()

        assertTrue(client.calls.isEmpty())
        assertNotNull(syncQueueDao.find("r1"))
    }

    @Test
    fun `auto-drain processes multiple pending entries`() = runTest {
        routeDao.upsert(routeEntity("r1"))
        routeDao.upsert(routeEntity("r2"))
        routeDao.upsert(routeEntity("r3"))
        repo.enqueue("r1")
        repo.enqueue("r2")
        repo.enqueue("r3")

        repo.start(backgroundScope)
        advanceUntilIdle()

        assertEquals(3, client.calls.size)
        assertTrue(routeDao.find("r1")!!.synced)
        assertTrue(routeDao.find("r2")!!.synced)
        assertTrue(routeDao.find("r3")!!.synced)
        assertNull(syncQueueDao.find("r1"))
        assertNull(syncQueueDao.find("r2"))
        assertNull(syncQueueDao.find("r3"))
    }
}
