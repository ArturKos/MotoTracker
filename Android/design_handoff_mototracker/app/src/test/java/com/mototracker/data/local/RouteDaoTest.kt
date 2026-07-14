package com.mototracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
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
 * DAO round-trip tests for [com.mototracker.data.local.dao.RouteDao] using an
 * in-memory Room database on the Robolectric JVM runtime.
 *
 * The list query is [com.mototracker.data.local.dao.RouteDao.observeSummaries], which projects
 * only scalar columns into [com.mototracker.data.model.RouteSummaryModel] (no trace blobs), so
 * assertions here inspect summary fields rather than the full [RouteEntity].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RouteDaoTest {

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

    private fun route(id: String, dateEpochMs: Long = 0L) = RouteEntity(
        id = id,
        name = "Route $id",
        dateEpochMs = dateEpochMs,
        bikeId = null,
        km = 100.0,
        durSec = 3600L,
        avg = 80.0,
        max = 140.0,
        lean = 30.0,
        elev = 500.0,
        fuel = 5.0,
        synced = false,
        wxJson = null,
        speedJson = null,
        elevProfileJson = null,
        notes = null,
    )

    @Test
    fun `upsert inserts a route and observeSummaries emits it`() = runTest {
        db.routeDao().upsert(route("r1", dateEpochMs = 1000L))

        db.routeDao().observeSummaries().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("r1", list[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeSummaries returns routes ordered by date descending`() = runTest {
        db.routeDao().upsert(route("r1", dateEpochMs = 1000L))
        db.routeDao().upsert(route("r2", dateEpochMs = 2000L))

        db.routeDao().observeSummaries().test {
            val ids = awaitItem().map { it.id }
            assertEquals(listOf("r2", "r1"), ids)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSynced updates the synced flag`() = runTest {
        db.routeDao().upsert(route("r1"))
        db.routeDao().setSynced("r1", true)

        db.routeDao().observeSummaries().test {
            val list = awaitItem()
            assertEquals(true, list[0].synced)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete removes the route`() = runTest {
        val r = route("r1")
        db.routeDao().upsert(r)
        db.routeDao().delete(r)

        db.routeDao().observeSummaries().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
