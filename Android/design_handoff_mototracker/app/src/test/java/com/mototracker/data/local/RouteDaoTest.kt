package com.mototracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mototracker.data.local.entity.BikeEntity
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.local.entity.RouteEntity
import com.mototracker.data.model.Bike
import com.mototracker.domain.stats.BikeStatsCalculator
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 *
 * The bikeId round-trip cases (K1 regression) prove that a non-null [RouteEntity.bikeId] written
 * to Room is correctly projected back through the SQL SELECT into
 * [com.mototracker.data.model.RouteSummaryModel.bikeId].  A Room projection defect here would
 * cause the BikeDetail summary to appear blank on-device even when routes are assigned to a bike
 * — matching the K1 bug report exactly.
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a minimal [BikeEntity] so routes with a non-null [RouteEntity.bikeId] satisfy
     * the FK constraint enforced by the in-memory Room database (`PRAGMA foreign_keys = ON`).
     *
     * @param id Primary key for the bike row; must match [RouteEntity.bikeId] values used in tests.
     */
    private fun bike(id: String) = BikeEntity(
        id = id,
        name = "Bike $id",
        year = 2020,
        plate = "$id-PLATE",
        status = BikeStatus.ACTIVE,
    )

    private fun route(
        id: String,
        dateEpochMs: Long = 0L,
        bikeId: String? = null,
        km: Double = 100.0,
        durSec: Long = 3600L,
        max: Double = 140.0,
        fuel: Double = 5.0,
        thumbnailPathD: String? = null,
        correctionStatus: CorrectionStatus = CorrectionStatus.NONE,
        confidence: Double? = null,
    ) = RouteEntity(
        id = id,
        name = "Route $id",
        dateEpochMs = dateEpochMs,
        bikeId = bikeId,
        km = km,
        durSec = durSec,
        avg = 80.0,
        max = max,
        lean = 30.0,
        elev = 500.0,
        fuel = fuel,
        synced = false,
        wxJson = null,
        speedJson = null,
        elevProfileJson = null,
        notes = null,
        thumbnailPathD = thumbnailPathD,
        correctionStatus = correctionStatus,
        confidence = confidence,
    )

    // ── Existing CRUD / ordering tests ────────────────────────────────────────

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

    // ── K1 bikeId projection regression ──────────────────────────────────────

    /**
     * K1 regression: a single route with a non-null bikeId must come back from
     * [com.mototracker.data.local.dao.RouteDao.observeSummaries] with bikeId intact.
     *
     * Failure here means Room's column→constructor projection drops or nulls bikeId, which
     * would blank the on-device BikeDetail summary for every bike.
     */
    @Test
    fun `bikeId round-trip single non-null bikeId is preserved by observeSummaries`() = runTest {
        db.bikeDao().upsert(bike("bike-1"))
        db.routeDao().upsert(route("r1", bikeId = "bike-1"))

        db.routeDao().observeSummaries().test {
            assertEquals("bike-1", awaitItem()[0].bikeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * K1 regression: four routes with distinct bikeId values (bike-1, bike-1, other-bike, null)
     * must each project to the exact stored value.  Mirrors the on-device fixture described in
     * the bug report (a subset of routes assigned to one bike; others unassigned or on another).
     */
    @Test
    fun `bikeId round-trip mixed values all projected correctly`() = runTest {
        db.bikeDao().upsert(bike("bike-1"))
        db.bikeDao().upsert(bike("other-bike"))
        db.routeDao().upsert(route("r1", dateEpochMs = 4000L, bikeId = "bike-1"))
        db.routeDao().upsert(route("r2", dateEpochMs = 3000L, bikeId = "bike-1"))
        db.routeDao().upsert(route("r3", dateEpochMs = 2000L, bikeId = "other-bike"))
        db.routeDao().upsert(route("r4", dateEpochMs = 1000L, bikeId = null))

        db.routeDao().observeSummaries().test {
            // Results ordered DESC by dateEpochMs → r1, r2, r3, r4
            val items = awaitItem()
            assertEquals(4, items.size)
            assertEquals("bike-1",     items[0].bikeId) // r1
            assertEquals("bike-1",     items[1].bikeId) // r2
            assertEquals("other-bike", items[2].bikeId) // r3
            assertNull(items[3].bikeId)                 // r4
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Spot-checks the full set of projected scalar columns so that a missing or mis-aliased
     * SELECT column (e.g. a stale query that omits thumbnailPathD or maps correctionStatus
     * to the wrong field) fails here rather than silently on-device.
     */
    @Test
    fun `scalar column projection all summary fields round-trip through Room`() = runTest {
        db.bikeDao().upsert(bike("bike-1"))
        db.routeDao().upsert(
            route(
                id = "r1",
                dateEpochMs = 9_999L,
                bikeId = "bike-1",
                km = 42.5,
                durSec = 7_200L,
                max = 180.0,
                fuel = 3.7,
                thumbnailPathD = "M 0 0 L 10 5",
                correctionStatus = CorrectionStatus.DONE,
                confidence = 0.95,
            ),
        )

        db.routeDao().observeSummaries().test {
            val s = awaitItem()[0]
            assertEquals("r1",                  s.id)
            assertEquals(9_999L,                s.dateEpochMs)
            assertEquals("bike-1",              s.bikeId)
            assertEquals(42.5,                  s.km, 0.001)
            assertEquals(7_200L,                s.durSec)
            assertEquals(180.0,                 s.max, 0.001)
            assertEquals(3.7,                   s.fuel, 0.001)
            assertEquals("M 0 0 L 10 5",        s.thumbnailPathD)
            assertEquals(CorrectionStatus.DONE, s.correctionStatus)
            assertEquals(0.95,                  s.confidence!!, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * K1 end-to-end regression: feeds the REAL [com.mototracker.data.local.dao.RouteDao.observeSummaries]
     * output (not an in-memory fake) into [BikeStatsCalculator.compute] and verifies that only the
     * routes assigned to "bike-1" contribute to the aggregate.
     *
     * If bikeId is not projected from Room, every [com.mototracker.data.model.RouteSummaryModel.bikeId]
     * would be null, the filter inside [BikeStatsCalculator.compute] would produce zero results, and
     * this test would fail deterministically — reproducing the blank-summary symptom headlessly.
     */
    @Test
    fun `BikeStatsCalculator aggregation from real Room projection filters to the correct bike`() = runTest {
        db.bikeDao().upsert(bike("bike-1"))
        db.bikeDao().upsert(bike("other-bike"))
        db.routeDao().upsert(route("r1", dateEpochMs = 4000L, bikeId = "bike-1",     km = 80.0))
        db.routeDao().upsert(route("r2", dateEpochMs = 3000L, bikeId = "bike-1",     km = 60.0))
        db.routeDao().upsert(route("r3", dateEpochMs = 2000L, bikeId = "other-bike", km = 999.0))
        db.routeDao().upsert(route("r4", dateEpochMs = 1000L, bikeId = null,         km = 999.0))

        val targetBike = Bike(
            id = "bike-1",
            name = "Bike bike-1",
            year = 2020,
            plate = "bike-1-PLATE",
            status = BikeStatus.ACTIVE,
        )

        db.routeDao().observeSummaries().test {
            val summaries = awaitItem()
            val stats = BikeStatsCalculator.compute(summaries, targetBike)

            assertEquals(2,     stats.rideCount)
            assertEquals(140.0, stats.totalDistanceKm, 0.001) // 80 + 60 km
            assertEquals(2,     stats.routeIds.size)
            assertTrue(stats.routeIds.containsAll(listOf("r1", "r2")))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
