package com.mototracker.data.repository

import app.cash.turbine.test
import com.mototracker.data.local.dao.RiderDao
import com.mototracker.data.local.entity.RiderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RiderRepositoryImpl].
 *
 * Verifies entity→domain mapping via [observeAll] and that [setInGroup] delegates correctly
 * to the DAO. Uses an in-memory [FakeRiderDao] instead of a real Room database.
 */
class RiderRepositoryImplTest {

    private lateinit var fakeDao: FakeRiderDao
    private lateinit var repo: RiderRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeRiderDao()
        repo = RiderRepositoryImpl(fakeDao)
    }

    @Test
    fun `observeAll maps entity to domain Rider`() = runTest {
        val entity = RiderEntity(
            shortId = "A1B2",
            nick = "Alice",
            bike = "CB500",
            lastSeenMs = 123_456L,
            inGroup = true,
        )
        fakeDao.emit(listOf(entity))

        repo.observeAll().test {
            val riders = awaitItem()
            assertEquals(1, riders.size)
            val r = riders[0]
            assertEquals("A1B2", r.shortId)
            assertEquals("Alice", r.nick)
            assertEquals("CB500", r.bike)
            assertEquals(123_456L, r.lastSeenMs)
            assertTrue(r.inGroup)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAll reflects updates emitted by dao`() = runTest {
        fakeDao.emit(emptyList())

        repo.observeAll().test {
            // initial empty emission
            assertEquals(0, awaitItem().size)

            // add a rider
            fakeDao.emit(listOf(RiderEntity("ZZ99", "Bob", "R1", 1_000L, inGroup = false)))
            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("ZZ99", updated[0].shortId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setInGroup delegates to dao`() = runTest {
        repo.setInGroup("A1B2", true)
        assertEquals("A1B2", fakeDao.lastSetInGroupId)
        assertTrue(fakeDao.lastSetInGroupValue!!)

        repo.setInGroup("A1B2", false)
        assertFalse(fakeDao.lastSetInGroupValue!!)
    }

    @Test
    fun `inGroup false is mapped correctly`() = runTest {
        val entity = RiderEntity("XX11", "Charlie", "MT07", 5_000L, inGroup = false)
        fakeDao.emit(listOf(entity))

        repo.observeAll().test {
            val riders = awaitItem()
            assertFalse(riders[0].inGroup)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fake DAO
// ─────────────────────────────────────────────────────────────────────────────

private class FakeRiderDao : RiderDao {
    private val _flow = MutableStateFlow<List<RiderEntity>>(emptyList())

    var lastSetInGroupId: String? = null
    var lastSetInGroupValue: Boolean? = null

    fun emit(entities: List<RiderEntity>) { _flow.value = entities }

    override fun getAll(): Flow<List<RiderEntity>> = _flow

    override suspend fun setInGroup(shortId: String, inGroup: Boolean) {
        lastSetInGroupId = shortId
        lastSetInGroupValue = inGroup
        _flow.value = _flow.value.map {
            if (it.shortId == shortId) it.copy(inGroup = inGroup) else it
        }
    }

    override suspend fun upsertSighting(shortId: String, nick: String, bike: String, lastSeenMs: Long) {
        val existing = _flow.value.find { it.shortId == shortId }
        if (existing == null) {
            _flow.value = _flow.value + RiderEntity(shortId, nick, bike, lastSeenMs, inGroup = false)
        } else {
            _flow.value = _flow.value.map {
                if (it.shortId == shortId) it.copy(nick = nick, bike = bike, lastSeenMs = lastSeenMs) else it
            }
        }
    }

    override suspend fun insertIgnore(shortId: String, nick: String, bike: String, lastSeenMs: Long) {}
    override suspend fun updateSighting(shortId: String, nick: String, bike: String, lastSeenMs: Long) {}
    override suspend fun get(shortId: String): RiderEntity? = _flow.value.find { it.shortId == shortId }
}
