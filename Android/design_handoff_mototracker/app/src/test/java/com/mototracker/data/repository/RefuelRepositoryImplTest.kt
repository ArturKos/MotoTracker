package com.mototracker.data.repository

import app.cash.turbine.test
import com.mototracker.data.local.dao.RefuelEventDao
import com.mototracker.data.local.entity.RefuelEventEntity
import com.mototracker.domain.fuel.RefuelEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [RefuelRepositoryImpl] — verifies entity↔domain mapping and delegation to the DAO.
 *
 * Uses an in-memory fake DAO so no Room/Robolectric runtime is required.
 */
class RefuelRepositoryImplTest {

    // ── Fake DAO ─────────────────────────────────────────────────────────────

    private class FakeRefuelEventDao : RefuelEventDao {
        private var nextId = 1L
        private val store = mutableListOf<RefuelEventEntity>()
        private val _flow = MutableStateFlow<List<RefuelEventEntity>>(emptyList())

        override suspend fun insert(entity: RefuelEventEntity): Long {
            val id = nextId++
            val withId = entity.copy(id = id)
            store += withId
            _flow.value = store.toList()
            return id
        }

        override fun observeForRoute(routeId: String): Flow<List<RefuelEventEntity>> =
            _flow.map { list -> list.filter { it.routeId == routeId } }

        override suspend fun deleteById(id: Long) {
            store.removeAll { it.id == id }
            _flow.value = store.toList()
        }

        override suspend fun deleteForRoute(routeId: String) {
            store.removeAll { it.routeId == routeId }
            _flow.value = store.toList()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val dao = FakeRefuelEventDao()
    private val repo = RefuelRepositoryImpl(dao)

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `addRefuel persists entity and observeRefuels emits mapped domain object`() = runTest {
        repo.addRefuel(routeId = "route-1", epochMs = 5_000L, litres = 18.5, pricePerL = 7.50)

        repo.observeRefuels("route-1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            val e = list[0]
            assertEquals("route-1", e.routeId)
            assertEquals(5_000L, e.epochMs)
            assertEquals(18.5, e.litres, 0.001)
            assertEquals(7.50, e.pricePerL, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `entity to domain mapping preserves all fields`() = runTest {
        repo.addRefuel(routeId = "r2", epochMs = 12_345L, litres = 30.0, pricePerL = 6.25)

        repo.observeRefuels("r2").test {
            val domain: RefuelEvent = awaitItem().single()
            assertEquals("r2", domain.routeId)
            assertEquals(12_345L, domain.epochMs)
            assertEquals(30.0, domain.litres, 0.001)
            assertEquals(6.25, domain.pricePerL, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteRefuel removes the event by id`() = runTest {
        repo.addRefuel(routeId = "route-1", epochMs = 1_000L, litres = 10.0, pricePerL = 5.0)

        var savedId: Long = -1L
        repo.observeRefuels("route-1").test {
            savedId = awaitItem().single().id
            cancelAndIgnoreRemainingEvents()
        }

        repo.deleteRefuel(savedId)

        repo.observeRefuels("route-1").test {
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeRefuels only returns events for the given routeId`() = runTest {
        repo.addRefuel(routeId = "route-A", epochMs = 1L, litres = 5.0, pricePerL = 7.0)
        repo.addRefuel(routeId = "route-B", epochMs = 2L, litres = 8.0, pricePerL = 6.5)

        repo.observeRefuels("route-A").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("route-A", list[0].routeId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
