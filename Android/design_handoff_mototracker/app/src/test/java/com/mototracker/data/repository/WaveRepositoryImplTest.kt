package com.mototracker.data.repository

import app.cash.turbine.test
import com.mototracker.data.local.dao.WaveDao
import com.mototracker.data.local.entity.WaveEntity
import com.mototracker.data.model.Wave
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fake WaveDao
// ─────────────────────────────────────────────────────────────────────────────

private class FakeWaveDao : WaveDao {
    private val store = mutableMapOf<String, WaveEntity>()
    private val allFlow = MutableStateFlow<List<WaveEntity>>(emptyList())

    override suspend fun upsert(entity: WaveEntity) {
        store[entity.id] = entity
        allFlow.value = store.values.toList()
    }

    override suspend fun delete(entity: WaveEntity) {
        store.remove(entity.id)
        allFlow.value = store.values.toList()
    }

    override fun getAll(): Flow<List<WaveEntity>> = allFlow

    override fun getByRouteId(routeId: String): Flow<List<WaveEntity>> =
        MutableStateFlow(store.values.filter { it.routeId == routeId })

    override suspend fun updateLastSeen(id: String, lastSeenMs: Long) {
        store[id]?.let { store[id] = it.copy(lastSeenMs = lastSeenMs) }
        allFlow.value = store.values.toList()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

class WaveRepositoryImplTest {

    private lateinit var dao: FakeWaveDao
    private lateinit var repo: WaveRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeWaveDao()
        repo = WaveRepositoryImpl(dao)
    }

    @Test
    fun `observeForRoute emits empty list when no waves exist`() = runTest {
        repo.observeForRoute("route-1").test {
            assertEquals(emptyList<Wave>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeForRoute returns waves for the given routeId`() = runTest {
        dao.upsert(buildWaveEntity(id = "w1", routeId = "route-1"))
        dao.upsert(buildWaveEntity(id = "w2", routeId = "route-2"))

        repo.observeForRoute("route-1").test {
            val waves = awaitItem()
            assertEquals(1, waves.size)
            assertEquals("w1", waves.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeForRoute maps entity fields to domain Wave`() = runTest {
        dao.upsert(
            buildWaveEntity(
                id = "w99",
                routeId = "route-a",
                nick = "KTM_Mike",
                bikeName = "KTM Duke 390",
                place = "Wawel",
                timeLabel = "09:15",
            )
        )

        repo.observeForRoute("route-a").test {
            val wave = awaitItem().first()
            assertEquals("w99", wave.id)
            assertEquals("route-a", wave.routeId)
            assertEquals("KTM_Mike", wave.nick)
            assertEquals("KTM Duke 390", wave.bikeName)
            assertEquals("Wawel", wave.place)
            assertEquals("09:15", wave.timeLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeForRoute returns empty list for a routeId with no matching waves`() = runTest {
        dao.upsert(buildWaveEntity(id = "w1", routeId = "other-route"))

        repo.observeForRoute("missing-route").test {
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── observeAll ────────────────────────────────────────────────────────────

    @Test
    fun `observeAll emits empty list when no waves exist`() = runTest {
        repo.observeAll().test {
            assertEquals(emptyList<com.mototracker.data.model.Wave>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAll returns all waves regardless of routeId`() = runTest {
        dao.upsert(buildWaveEntity(id = "w1", routeId = "route-1"))
        dao.upsert(buildWaveEntity(id = "w2", routeId = "route-2"))
        dao.upsert(buildWaveEntity(id = "w3", routeId = null))

        repo.observeAll().test {
            val waves = awaitItem()
            assertEquals(3, waves.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAll maps entity fields to domain Wave`() = runTest {
        dao.upsert(
            buildWaveEntity(
                id = "w-all",
                routeId = "route-x",
                nick = "Rider",
                bikeName = "Ninja",
                place = "Zamek",
                timeLabel = "11:30",
            )
        )

        repo.observeAll().test {
            val wave = awaitItem().first()
            assertEquals("w-all", wave.id)
            assertEquals("Rider", wave.nick)
            assertEquals("Ninja", wave.bikeName)
            assertEquals("Zamek", wave.place)
            assertEquals("11:30", wave.timeLabel)
            assertEquals("route-x", wave.routeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAll re-emits after upsert`() = runTest {
        repo.observeAll().test {
            assertEquals(0, awaitItem().size)
            dao.upsert(buildWaveEntity(id = "w-new"))
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun buildWaveEntity(
        id: String = "wave-0",
        routeId: String? = "route-0",
        nick: String = "Rider",
        bikeName: String = "MT-07",
        place: String = "Rynek",
        timeLabel: String = "12:00",
    ) = WaveEntity(
        id = id,
        routeId = routeId,
        nick = nick,
        bikeName = bikeName,
        place = place,
        timeLabel = timeLabel,
    )
}
