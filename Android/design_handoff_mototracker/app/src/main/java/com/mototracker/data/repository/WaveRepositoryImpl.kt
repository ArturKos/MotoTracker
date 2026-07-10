package com.mototracker.data.repository

import com.mototracker.data.local.dao.WaveDao
import com.mototracker.data.model.Wave
import com.mototracker.data.model.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [WaveRepository].
 *
 * Maps [com.mototracker.data.local.entity.WaveEntity] rows to domain [Wave] objects
 * via the existing [com.mototracker.data.model.mapper.toDomain] extension.
 *
 * @param waveDao DAO providing the Room queries.
 */
@Singleton
class WaveRepositoryImpl @Inject constructor(
    private val waveDao: WaveDao,
) : WaveRepository {

    override fun observeForRoute(routeId: String): Flow<List<Wave>> =
        waveDao.getByRouteId(routeId).map { entities -> entities.map { it.toDomain() } }
}
