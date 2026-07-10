package com.mototracker.data.repository

import app.cash.turbine.test
import com.mototracker.data.local.dao.GroupDao
import com.mototracker.data.local.entity.GroupMemberEntity
import com.mototracker.data.model.GroupMember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fake GroupDao
// ─────────────────────────────────────────────────────────────────────────────

private class FakeGroupDao : GroupDao {
    private val store = mutableMapOf<String, GroupMemberEntity>()
    private val allFlow = MutableStateFlow<List<GroupMemberEntity>>(emptyList())

    val upsertedEntities get(): List<GroupMemberEntity> = store.values.toList()

    override suspend fun upsert(entity: GroupMemberEntity) {
        store[entity.id] = entity
        allFlow.value = store.values.toList()
    }

    override suspend fun delete(entity: GroupMemberEntity) {
        store.remove(entity.id)
        allFlow.value = store.values.toList()
    }

    override fun getAll(): Flow<List<GroupMemberEntity>> = allFlow
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

class GroupRepositoryImplTest {

    private lateinit var dao: FakeGroupDao
    private lateinit var repo: GroupRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeGroupDao()
        repo = GroupRepositoryImpl(dao)
    }

    // ── observeGroup ──────────────────────────────────────────────────────────

    @Test
    fun `observeGroup emits empty list when no members`() = runTest {
        repo.observeGroup().test {
            assertEquals(emptyList<GroupMember>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeGroup maps entity fields to domain GroupMember`() = runTest {
        dao.upsert(GroupMemberEntity(id = "id-1", name = "Alice", phone = "+48111", bikeName = "MT-07"))
        repo.observeGroup().test {
            val member = awaitItem().first()
            assertEquals("id-1", member.id)
            assertEquals("Alice", member.name)
            assertEquals("+48111", member.phone)
            assertEquals("MT-07", member.bikeName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeGroup re-emits after upsert`() = runTest {
        repo.observeGroup().test {
            assertEquals(0, awaitItem().size)
            dao.upsert(GroupMemberEntity(id = "x", name = "Bob", phone = "123", bikeName = ""))
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── addByPhone ────────────────────────────────────────────────────────────

    @Test
    fun `addByPhone upserts a new member with phone as name`() = runTest {
        repo.addByPhone("+48600000001")
        val entities = dao.upsertedEntities
        assertEquals(1, entities.size)
        val entity = entities.first()
        assertEquals("+48600000001", entity.phone)
        assertEquals("+48600000001", entity.name)
        assertEquals("", entity.bikeName)
    }

    @Test
    fun `addByPhone generates unique id for each call`() = runTest {
        repo.addByPhone("phone-1")
        repo.addByPhone("phone-2")
        val ids = dao.upsertedEntities.map { it.id }
        assertEquals(2, ids.size)
        assertFalse("IDs should be unique", ids[0] == ids[1])
    }

    @Test
    fun `addByPhone makes new member appear in observeGroup flow`() = runTest {
        repo.observeGroup().test {
            assertEquals(0, awaitItem().size)
            repo.addByPhone("+48500000002")
            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("+48500000002", updated.first().phone)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addByPhone multiple times accumulates members`() = runTest {
        repo.addByPhone("aaa")
        repo.addByPhone("bbb")
        repo.addByPhone("ccc")
        repo.observeGroup().test {
            assertEquals(3, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── mapping round-trip ────────────────────────────────────────────────────

    @Test
    fun `entity with all fields maps correctly to domain`() = runTest {
        dao.upsert(GroupMemberEntity(id = "uuid-99", name = "Rider", phone = "+1234", bikeName = "R1"))
        repo.observeGroup().test {
            val m = awaitItem().first()
            assertEquals("uuid-99", m.id)
            assertEquals("Rider", m.name)
            assertEquals("+1234", m.phone)
            assertEquals("R1", m.bikeName)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
