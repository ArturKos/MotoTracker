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

    override suspend fun getById(id: String): Route? =
        routeDao.getById(id)?.toDomain()

    /**
     * Returns a live [kotlinx.coroutines.flow.Flow] of the route with [id], emitting `null`
     * when no matching row exists and re-emitting after every update to that row.
     */
    override fun observeById(id: String): Flow<Route?> =
        routeDao.observeById(id).map { it?.toDomain() }

    /**
     * Delegates to [RouteDao.clearCorrection] which nulls out [correctedPathJson] and
     * resets [correctionStatus] to NONE without touching the raw [pathJson].
     */
    override suspend fun clearCorrectedTrace(id: String) {
        routeDao.clearCorrection(id)
    }

    /**
     * Updates the display name of the route with [id] via a targeted SQL UPDATE.
     *
     * The [name] must already be trimmed by the caller.
     */
    override suspend fun rename(id: String, name: String) {
        routeDao.setName(id, name)
    }

    /** Removes every route row from the database; used for REPLACE-mode backup restore. */
    override suspend fun deleteAll() {
        routeDao.deleteAll()
    }
}
