package com.mototracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mototracker.data.local.entity.RouteEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DAO round-trip tests for [com.mototracker.data.local.dao.RouteDao] using an
 * in-memory Room database on the Robolectric JVM runtime.
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
        pathJson = null,
        speedJson = null,
        elevProfileJson = null,
        notes = null,
    )

    @Test
    fun `upsert inserts a route and getAll emits it`() = runTest {
        val r = route("r1", dateEpochMs = 1000L)

        db.routeDao().upsert(r)

        db.routeDao().getAll().test {
            assertEquals(listOf(r), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAll returns routes ordered by date descending`() = runTest {
        val older = route("r1", dateEpochMs = 1000L)
        val newer = route("r2", dateEpochMs = 2000L)

        db.routeDao().upsert(older)
        db.routeDao().upsert(newer)

        db.routeDao().getAll().test {
            val list = awaitItem()
            assertEquals(listOf(newer, older), list)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setSynced updates the synced flag`() = runTest {
        val r = route("r1")
        db.routeDao().upsert(r)
        db.routeDao().setSynced("r1", true)

        db.routeDao().getAll().test {
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

        db.routeDao().getAll().test {
            assertEquals(emptyList<RouteEntity>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
