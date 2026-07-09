package com.mototracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mototracker.data.local.entity.RouteEntity
import com.mototracker.data.local.entity.SyncQueueEntity
import com.mototracker.data.local.entity.SyncQueueState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DAO round-trip tests for [com.mototracker.data.local.dao.SyncQueueDao] using an
 * in-memory Room database on the Robolectric JVM runtime.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncQueueDaoTest {

    private lateinit var db: MotoDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MotoDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun route(id: String) = RouteEntity(
        id = id, name = "Route", dateEpochMs = 0L, bikeId = null,
        km = 0.0, durSec = 0L, avg = 0.0, max = 0.0, lean = 0.0,
        elev = 0.0, fuel = 0.0, synced = false,
        wxJson = null, pathJson = null, speedJson = null, elevProfileJson = null, notes = null,
    )

    private fun queueEntry(routeId: String, state: SyncQueueState, retryMs: Long? = null) =
        SyncQueueEntity(
            id = 0,
            routeId = routeId,
            state = state,
            attemptCount = 0,
            lastAttemptEpochMs = null,
            nextRetryEpochMs = retryMs,
            lastError = null,
        )

    @Test
    fun `getPending excludes DONE entries`() = runTest {
        db.routeDao().upsert(route("r1"))
        db.routeDao().upsert(route("r2"))

        db.syncQueueDao().upsert(queueEntry("r1", SyncQueueState.PENDING))
        db.syncQueueDao().upsert(queueEntry("r2", SyncQueueState.DONE))

        db.syncQueueDao().getPending().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("r1", list[0].routeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getPending orders by nextRetryEpochMs ascending, nulls first`() = runTest {
        db.routeDao().upsert(route("r1"))
        db.routeDao().upsert(route("r2"))
        db.routeDao().upsert(route("r3"))

        db.syncQueueDao().upsert(queueEntry("r1", SyncQueueState.FAILED, retryMs = 2000L))
        db.syncQueueDao().upsert(queueEntry("r2", SyncQueueState.PENDING, retryMs = null))
        db.syncQueueDao().upsert(queueEntry("r3", SyncQueueState.FAILED, retryMs = 1000L))

        db.syncQueueDao().getPending().test {
            val ids = awaitItem().map { it.routeId }
            // null first, then ascending
            assertEquals(listOf("r2", "r3", "r1"), ids)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert updates an existing entry state`() = runTest {
        db.routeDao().upsert(route("r1"))
        val entry = queueEntry("r1", SyncQueueState.PENDING)
        db.syncQueueDao().upsert(entry)

        val saved = db.syncQueueDao().getPending().first()[0]
        val updated = saved.copy(state = SyncQueueState.FAILED, attemptCount = 1, lastError = "timeout")
        db.syncQueueDao().upsert(updated)

        db.syncQueueDao().getPending().test {
            val list = awaitItem()
            assertEquals(SyncQueueState.FAILED, list[0].state)
            assertEquals(1, list[0].attemptCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pruneDone removes only DONE entries`() = runTest {
        db.routeDao().upsert(route("r1"))
        db.routeDao().upsert(route("r2"))

        db.syncQueueDao().upsert(queueEntry("r1", SyncQueueState.DONE))
        db.syncQueueDao().upsert(queueEntry("r2", SyncQueueState.PENDING))

        db.syncQueueDao().pruneDone()

        db.syncQueueDao().getPending().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("r2", list[0].routeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cascade delete removes queue entry when route is deleted`() = runTest {
        db.routeDao().upsert(route("r1"))
        db.syncQueueDao().upsert(queueEntry("r1", SyncQueueState.PENDING))

        db.routeDao().delete(route("r1"))

        db.syncQueueDao().getPending().test {
            assertEquals(emptyList<SyncQueueEntity>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
