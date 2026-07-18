package com.mototracker.data.repository

import com.mototracker.data.local.dao.RiderDao
import com.mototracker.data.local.entity.RiderEntity
import com.mototracker.data.model.Rider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [RiderRepository].
 *
 * Maps [RiderEntity] rows to domain [Rider] objects inline; newest-first ordering
 * is delegated to [RiderDao.getAll]'s ORDER BY clause.
 *
 * @param riderDao DAO providing the Room queries.
 */
@Singleton
class RiderRepositoryImpl @Inject constructor(
    private val riderDao: RiderDao,
) : RiderRepository {

    override fun observeAll(): Flow<List<Rider>> =
        riderDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun setInGroup(shortId: String, inGroup: Boolean) {
        riderDao.setInGroup(shortId, inGroup)
    }
}

private fun RiderEntity.toDomain() = Rider(
    shortId = shortId,
    nick = nick,
    bike = bike,
    lastSeenMs = lastSeenMs,
    inGroup = inGroup,
)
