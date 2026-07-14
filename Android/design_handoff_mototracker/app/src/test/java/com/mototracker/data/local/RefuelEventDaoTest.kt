package com.mototracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mototracker.data.local.entity.RefuelEventEntity
import com.mototracker.data.local.entity.RouteEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DAO round-trip tests for [com.mototracker.data.local.dao.RefuelEventDao] using an
 * in-memory Room database on the Robolectric JVM runtime (mirrors [RouteDaoTest]).
 *
 * Also verifies CASCADE delete when the parent [RouteEntity] is deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RefuelEventDaoTest {

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

    private fun route(id: String = "r1") = RouteEntity(
        id = id,
        name = "Route $id",
        dateEpochMs = 1_000L,
        bikeId = null,
        km = 50.0,
        durSec = 3600L,
        avg = 60.0,
        max = 120.0,
        lean = 20.0,
        elev = 200.0,
        fuel = 3.0,
        synced = false,
        wxJson = null,
        speedJson = null,
        elevProfileJson = null,
        notes = null,
    )

    private fun event(routeId: String = "r1", epochMs: Long = 1_000L, litres: Double = 15.0, pricePerL: Double = 7.5) =
        RefuelEventEntity(routeId = routeId, epochMs = epochMs, litres = litres, pricePerL = pricePerL)

    // ── insert + observe ──────────────────────────────────────────────────────

    @Test
    fun `insert returns a positive id and observeForRoute emits the event`() = runTest {
        db.routeDao().upsert(route())
        val id = db.refuelEventDao().insert(event())

        assertTrue("auto-generated id must be > 0", id > 0)

        db.refuelEventDao().observeForRoute("r1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(id, list[0].id)
            assertEquals(15.0, list[0].litres, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeForRoute returns events ordered by epochMs ascending`() = runTest {
        db.routeDao().upsert(route())
        db.refuelEventDao().insert(event(epochMs = 3_000L))
        db.refuelEventDao().insert(event(epochMs = 1_000L))
        db.refuelEventDao().insert(event(epochMs = 2_000L))

        db.refuelEventDao().observeForRoute("r1").test {
            val list = awaitItem()
            assertEquals(3, list.size)
            assertEquals(1_000L, list[0].epochMs)
            assertEquals(2_000L, list[1].epochMs)
            assertEquals(3_000L, list[2].epochMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── deleteById ────────────────────────────────────────────────────────────

    @Test
    fun `deleteById removes the matching event`() = runTest {
        db.routeDao().upsert(route())
        val id1 = db.refuelEventDao().insert(event(epochMs = 1_000L))
        val id2 = db.refuelEventDao().insert(event(epochMs = 2_000L))

        db.refuelEventDao().deleteById(id1)

        db.refuelEventDao().observeForRoute("r1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(id2, list[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── CASCADE delete ────────────────────────────────────────────────────────

    @Test
    fun `deleting parent route cascades to refuel events`() = runTest {
        db.routeDao().upsert(route())
        db.refuelEventDao().insert(event())
        db.refuelEventDao().insert(event(epochMs = 2_000L))

        db.routeDao().delete(route())

        db.refuelEventDao().observeForRoute("r1").test {
            val list = awaitItem()
            assertEquals(0, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── deleteForRoute ────────────────────────────────────────────────────────

    @Test
    fun `deleteForRoute removes all events for the given route`() = runTest {
        db.routeDao().upsert(route("r1"))
        db.routeDao().upsert(route("r2"))
        db.refuelEventDao().insert(event(routeId = "r1", epochMs = 1_000L))
        db.refuelEventDao().insert(event(routeId = "r2", epochMs = 2_000L))

        db.refuelEventDao().deleteForRoute("r1")

        db.refuelEventDao().observeForRoute("r1").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        // r2 events are untouched
        db.refuelEventDao().observeForRoute("r2").test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
