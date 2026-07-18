package com.mototracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mototracker.data.local.entity.FuelAdjustmentEventEntity
import com.mototracker.data.repository.FuelAdjustmentRepositoryImpl
import com.mototracker.domain.fuel.FuelAdjustmentMode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DAO + repository round-trip tests for [FuelAdjustmentRepositoryImpl] using an in-memory
 * Room database on the Robolectric JVM runtime.
 *
 * Covers: insert → observeForBike, insert → latestForBike, ordering, multi-bike isolation,
 * and off-ride (routeId = null) corrections.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FuelAdjustmentRepositoryTest {

    private lateinit var db: MotoDatabase
    private lateinit var repo: FuelAdjustmentRepositoryImpl

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MotoDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = FuelAdjustmentRepositoryImpl(db.fuelAdjustmentEventDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    // ── insert → observeForBike ───────────────────────────────────────────────

    @Test
    fun `insert and observeForBike round-trip emits the inserted event`() = runTest {
        repo.addAdjustment(
            bikeId = "bike-1",
            routeId = null,
            epochMs = 1_000L,
            mode = FuelAdjustmentMode.SET_ABSOLUTE,
            litres = 12.5,
        )

        repo.observeForBike("bike-1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            val event = list[0]
            assertEquals("bike-1", event.bikeId)
            assertNull("off-ride correction has no routeId", event.routeId)
            assertEquals(FuelAdjustmentMode.SET_ABSOLUTE, event.mode)
            assertEquals(12.5, event.litres, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeForBike returns events ordered chronologically ascending`() = runTest {
        repo.addAdjustment("bike-1", null, 3_000L, FuelAdjustmentMode.DELTA, -2.0)
        repo.addAdjustment("bike-1", null, 1_000L, FuelAdjustmentMode.SET_ABSOLUTE, 18.0)
        repo.addAdjustment("bike-1", null, 2_000L, FuelAdjustmentMode.DELTA, -1.0)

        repo.observeForBike("bike-1").test {
            val list = awaitItem()
            assertEquals(3, list.size)
            assertEquals(1_000L, list[0].epochMs)
            assertEquals(2_000L, list[1].epochMs)
            assertEquals(3_000L, list[2].epochMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeForBike does not return events from other bikes`() = runTest {
        repo.addAdjustment("bike-1", null, 1_000L, FuelAdjustmentMode.SET_ABSOLUTE, 10.0)
        repo.addAdjustment("bike-2", null, 2_000L, FuelAdjustmentMode.DELTA, -3.0)

        repo.observeForBike("bike-1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("bike-1", list[0].bikeId)
            cancelAndIgnoreRemainingEvents()
        }

        repo.observeForBike("bike-2").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("bike-2", list[0].bikeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── insert → latestForBike ────────────────────────────────────────────────

    @Test
    fun `latestForBike returns null when no events exist`() = runTest {
        val result = repo.latestForBike("bike-none")
        assertNull("should be null for unknown bike", result)
    }

    @Test
    fun `latestForBike returns the most recent event by epochMs`() = runTest {
        repo.addAdjustment("bike-1", null, 1_000L, FuelAdjustmentMode.SET_ABSOLUTE, 18.0)
        repo.addAdjustment("bike-1", null, 3_000L, FuelAdjustmentMode.DELTA, -2.5)
        repo.addAdjustment("bike-1", null, 2_000L, FuelAdjustmentMode.DELTA, -1.0)

        val latest = repo.latestForBike("bike-1")
        assertNotNull(latest)
        assertEquals(3_000L, latest!!.epochMs)
        assertEquals(FuelAdjustmentMode.DELTA, latest.mode)
        assertEquals(-2.5, latest.litres, 0.001)
    }

    @Test
    fun `latestForBike returns correct event when multiple bikes exist`() = runTest {
        repo.addAdjustment("bike-1", null, 5_000L, FuelAdjustmentMode.SET_ABSOLUTE, 15.0)
        repo.addAdjustment("bike-2", null, 9_000L, FuelAdjustmentMode.DELTA, -4.0)

        val b1 = repo.latestForBike("bike-1")
        val b2 = repo.latestForBike("bike-2")

        assertNotNull(b1)
        assertEquals(15.0, b1!!.litres, 0.001)
        assertNotNull(b2)
        assertEquals(-4.0, b2!!.litres, 0.001)
    }

    // ── in-ride (routeId not null) ────────────────────────────────────────────

    @Test
    fun `in-ride correction with routeId is persisted and readable`() = runTest {
        repo.addAdjustment(
            bikeId = "bike-1",
            routeId = "route-abc",
            epochMs = 4_000L,
            mode = FuelAdjustmentMode.SET_ABSOLUTE,
            litres = 8.0,
        )

        repo.observeForBike("bike-1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("route-abc", list[0].routeId)
            assertEquals(8.0, list[0].litres, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── DAO direct round-trip ─────────────────────────────────────────────────

    @Test
    fun `dao insert returns positive auto-generated id`() = runTest {
        val dao = db.fuelAdjustmentEventDao()
        val id = dao.insert(
            FuelAdjustmentEventEntity(
                bikeId = "bike-x",
                routeId = null,
                epochMs = 1_000L,
                mode = "SET_ABSOLUTE",
                litres = 20.0,
            ),
        )
        assertTrue("auto-generated id must be > 0", id > 0L)
    }

    @Test
    fun `observeForBike emits empty list before any insert`() = runTest {
        repo.observeForBike("bike-empty").test {
            val list = awaitItem()
            assertTrue("list should be empty initially", list.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new insert triggers a fresh emission from observeForBike`() = runTest {
        repo.observeForBike("bike-live").test {
            val initial = awaitItem()
            assertTrue(initial.isEmpty())

            repo.addAdjustment("bike-live", null, 1_000L, FuelAdjustmentMode.SET_ABSOLUTE, 14.0)

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals(14.0, updated[0].litres, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
