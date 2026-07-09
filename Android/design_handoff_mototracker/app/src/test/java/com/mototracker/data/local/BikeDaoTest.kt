package com.mototracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mototracker.data.local.entity.BikeEntity
import com.mototracker.data.local.entity.BikeStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DAO round-trip tests for [com.mototracker.data.local.dao.BikeDao] using an
 * in-memory Room database on the Robolectric JVM runtime.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BikeDaoTest {

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

    @Test
    fun `upsert inserts a bike and getAll emits it`() = runTest {
        val bike = BikeEntity("b1", "Yamaha MT-07", 2022, "WA 12345", BikeStatus.ACTIVE)

        db.bikeDao().upsert(bike)

        db.bikeDao().getAll().test {
            assertEquals(listOf(bike), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upsert replaces existing bike with same id`() = runTest {
        val original = BikeEntity("b1", "Yamaha MT-07", 2022, "WA 12345", BikeStatus.ACTIVE)
        val updated = original.copy(name = "Yamaha MT-09", status = BikeStatus.SOLD)

        db.bikeDao().upsert(original)
        db.bikeDao().upsert(updated)

        db.bikeDao().getAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(updated, list[0])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete removes the bike`() = runTest {
        val bike = BikeEntity("b1", "Yamaha MT-07", 2022, "WA 12345", BikeStatus.ACTIVE)

        db.bikeDao().upsert(bike)
        db.bikeDao().delete(bike)

        db.bikeDao().getAll().test {
            assertEquals(emptyList<BikeEntity>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getById returns null for unknown id`() = runTest {
        assertNull(db.bikeDao().getById("nonexistent"))
    }

    @Test
    fun `getById returns the correct bike`() = runTest {
        val bike = BikeEntity("b2", "Honda CB500", 2021, "KR 99999", BikeStatus.ACTIVE)
        db.bikeDao().upsert(bike)

        assertEquals(bike, db.bikeDao().getById("b2"))
    }
}
