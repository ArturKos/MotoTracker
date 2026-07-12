package com.mototracker.data.repository

import com.mototracker.data.local.dao.BikeDao
import com.mototracker.data.model.Bike
import com.mototracker.data.model.mapper.toDomain
import com.mototracker.data.model.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [BikeRepository].
 *
 * Maps [com.mototracker.data.local.entity.BikeEntity] rows to domain [Bike] objects
 * via the existing [com.mototracker.data.model.mapper.toDomain] extension.
 *
 * @param bikeDao DAO providing the Room queries.
 */
@Singleton
class BikeRepositoryImpl @Inject constructor(
    private val bikeDao: BikeDao,
) : BikeRepository {

    override fun observeAll(): Flow<List<Bike>> =
        bikeDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun addBike(bike: Bike) {
        bikeDao.upsert(bike.toEntity())
    }

    /** Removes every bike row from the database; used for REPLACE-mode backup restore. */
    override suspend fun deleteAll() {
        bikeDao.deleteAll()
    }
}
