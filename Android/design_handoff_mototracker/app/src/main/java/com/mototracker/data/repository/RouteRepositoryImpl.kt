package com.mototracker.data.repository

import com.mototracker.data.local.dao.RouteDao
import com.mototracker.data.model.Route
import com.mototracker.data.model.mapper.toDomain
import com.mototracker.data.model.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [RouteRepository].
 *
 * Maps between the domain [Route] model and [com.mototracker.data.local.entity.RouteEntity]
 * via the existing [com.mototracker.data.model.mapper.RouteMapper] extension functions.
 *
 * @param routeDao DAO providing the Room queries.
 */
@Singleton
class RouteRepositoryImpl @Inject constructor(
    private val routeDao: RouteDao,
) : RouteRepository {

    override suspend fun save(route: Route) {
        routeDao.upsert(route.toEntity())
    }

    override fun observeAll(): Flow<List<Route>> =
        routeDao.getAll().map { entities -> entities.map { it.toDomain() } }
}
