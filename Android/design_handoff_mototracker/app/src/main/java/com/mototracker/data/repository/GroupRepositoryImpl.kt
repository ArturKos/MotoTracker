package com.mototracker.data.repository

import com.mototracker.data.local.dao.GroupDao
import com.mototracker.data.model.GroupMember
import com.mototracker.data.model.mapper.toDomain
import com.mototracker.data.model.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [GroupRepository].
 *
 * Maps [com.mototracker.data.local.entity.GroupMemberEntity] rows to domain
 * [GroupMember] objects via the existing mapper extensions.
 *
 * @param groupDao DAO providing the Room queries.
 */
@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
) : GroupRepository {

    override fun observeGroup(): Flow<List<GroupMember>> =
        groupDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addByPhone(phone: String) {
        val member = GroupMember(
            id = UUID.randomUUID().toString(),
            name = phone,
            phone = phone,
            bikeName = "",
        )
        groupDao.upsert(member.toEntity())
    }
}
