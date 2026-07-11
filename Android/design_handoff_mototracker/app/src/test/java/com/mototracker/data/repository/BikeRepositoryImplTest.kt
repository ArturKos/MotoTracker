package com.mototracker.data.repository

import app.cash.turbine.test
import com.mototracker.data.local.dao.BikeDao
import com.mototracker.data.local.entity.BikeEntity
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.model.Bike
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fake BikeDao
// ─────────────────────────────────────────────────────────────────────────────

private class FakeBikeDao : BikeDao {
    private val store = mutableMapOf<String, BikeEntity>()
    private val allFlow = MutableStateFlow<List<BikeEntity>>(emptyList())
    val upsertCalls = mutableListOf<BikeEntity>()

    override suspend fun upsert(entity: BikeEntity) {
        upsertCalls += entity
        store[entity.id] = entity
        allFlow.value = store.values.toList()
    }

    override suspend fun delete(entity: BikeEntity) {
        store.remove(entity.id)
        allFlow.value = store.values.toList()
    }

    override fun getAll(): Flow<List<BikeEntity>> = allFlow

    override suspend fun getById(id: String): BikeEntity? = store[id]
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

class BikeRepositoryImplTest {

    private lateinit var dao: FakeBikeDao
    private lateinit var repo: BikeRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeBikeDao()
        repo = BikeRepositoryImpl(dao)
    }

    /** [BikeRepository.addBike] must delegate to [BikeDao.upsert] with the correct entity. */
    @Test
    fun `addBike upserts the entity with matching fields`() = runTest {
        val bike = Bike(id = "b-42", name = "Yamaha MT-07", year = 2020, plate = "WA 1234", status = BikeStatus.ACTIVE)
        repo.addBike(bike)

        val upserted = dao.upsertCalls.single()
        assertEquals("b-42", upserted.id)
        assertEquals("Yamaha MT-07", upserted.name)
        assertEquals(2020, upserted.year)
        assertEquals("WA 1234", upserted.plate)
        assertEquals(BikeStatus.ACTIVE, upserted.status)
    }

    /** Adding a bike with SOLD status preserves the status in the entity. */
    @Test
    fun `addBike preserves SOLD status`() = runTest {
        val bike = Bike(id = "b-sold", name = "Old Honda", year = 2015, plate = "XX 999", status = BikeStatus.SOLD)
        repo.addBike(bike)

        assertEquals(BikeStatus.SOLD, dao.upsertCalls.single().status)
    }

    /** The live [observeAll] flow emits the added bike after [addBike]. */
    @Test
    fun `observeAll emits bike after addBike`() = runTest {
        val bike = Bike(id = "b1", name = "CB 500", year = 2022, plate = "KR 1", status = BikeStatus.ACTIVE)
        repo.observeAll().test {
            assertEquals(emptyList<Bike>(), awaitItem())
            repo.addBike(bike)
            val emitted = awaitItem()
            assertEquals(1, emitted.size)
            assertEquals("b1", emitted[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Upserting a bike with the same ID replaces the existing row. */
    @Test
    fun `addBike replaces existing bike with same id`() = runTest {
        val original = Bike("b1", "Old Name", 2019, "AA 1", BikeStatus.ACTIVE)
        val updated = Bike("b1", "New Name", 2021, "BB 2", BikeStatus.SOLD)

        repo.addBike(original)
        repo.addBike(updated)

        assertEquals(2, dao.upsertCalls.size)
        val last = dao.upsertCalls.last()
        assertEquals("New Name", last.name)
        assertEquals(BikeStatus.SOLD, last.status)
    }
}
