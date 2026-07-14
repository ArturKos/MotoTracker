package com.mototracker.data.repository

import com.mototracker.core.format.TraceChunkCodec
import com.mototracker.core.format.TraceDownsampler
import com.mototracker.core.format.RouteThumbnail
import com.mototracker.data.local.dao.RouteDao
import com.mototracker.data.local.dao.RouteTraceChunkDao
import com.mototracker.data.local.entity.RouteTraceChunkEntity
import com.mototracker.data.model.Route
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.model.mapper.toDomain
import com.mototracker.data.model.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [RouteRepository].
 *
 * GPS traces are stored out-of-row in [RouteTraceChunkDao] so that no single CursorWindow
 * load can exceed the 2 MB Android limit, regardless of ride length. A downsampled
 * (~120 pts) SVG path string ([com.mototracker.data.local.entity.RouteEntity.thumbnailPathD])
 * is precomputed at [save] time and stored inline for instant rendering in list cards.
 *
 * @param routeDao       DAO for the main routes table.
 * @param traceChunkDao  DAO for out-of-row trace chunk storage.
 */
@Singleton
class RouteRepositoryImpl @Inject constructor(
    private val routeDao: RouteDao,
    private val traceChunkDao: RouteTraceChunkDao,
) : RouteRepository {

    override suspend fun save(route: Route) {
        val thumbnailPathD = computeThumbnailPathD(route.pathJson)
        routeDao.upsert(route.toEntity().copy(thumbnailPathD = thumbnailPathD))

        val rawChunks = TraceChunkCodec.split(route.pathJson).mapIndexed { seq, chunkJson ->
            RouteTraceChunkEntity(route.id, KIND_RAW, seq, chunkJson)
        }
        traceChunkDao.replace(route.id, KIND_RAW, rawChunks)

        if (route.correctedPathJson != null) {
            val corrChunks = TraceChunkCodec.split(route.correctedPathJson).mapIndexed { seq, chunkJson ->
                RouteTraceChunkEntity(route.id, KIND_CORRECTED, seq, chunkJson)
            }
            traceChunkDao.replace(route.id, KIND_CORRECTED, corrChunks)
        }
    }

    override fun observeSummaries(): Flow<List<RouteSummaryModel>> =
        routeDao.observeSummaries()

    override suspend fun getById(id: String): Route? {
        val entity = routeDao.getById(id) ?: return null
        val pathJson = assembleTrace(id, KIND_RAW)
        val correctedPathJson = assembleTrace(id, KIND_CORRECTED)
        return entity.toDomain(pathJson = pathJson, correctedPathJson = correctedPathJson)
    }

    override fun observeById(id: String): Flow<Route?> =
        routeDao.observeById(id).flatMapLatest { entity ->
            if (entity == null) {
                flowOf(null)
            } else {
                flow {
                    val pathJson = assembleTrace(id, KIND_RAW)
                    val correctedPathJson = assembleTrace(id, KIND_CORRECTED)
                    emit(entity.toDomain(pathJson = pathJson, correctedPathJson = correctedPathJson))
                }
            }
        }

    override suspend fun clearCorrectedTrace(id: String) {
        routeDao.clearCorrection(id)
        traceChunkDao.deleteFor(id, KIND_CORRECTED)
    }

    override suspend fun rename(id: String, name: String) {
        routeDao.setName(id, name)
    }

    override suspend fun setBike(routeId: String, bikeId: String?) {
        routeDao.setBike(routeId, bikeId)
    }

    override suspend fun deleteAll() {
        routeDao.deleteAll()
    }

    override suspend fun setFuel(routeId: String, fuelL: Double) {
        routeDao.setFuel(routeId, fuelL)
    }

    override suspend fun setFuelPrice(routeId: String, pricePerL: Double?) {
        routeDao.setFuelPrice(routeId, pricePerL)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun assembleTrace(routeId: String, kind: String): String? {
        val chunks = traceChunkDao.getChunks(routeId, kind)
        return TraceChunkCodec.join(chunks.map { it.chunkJson })
    }

    private fun computeThumbnailPathD(pathJson: String?): String? {
        if (pathJson.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(pathJson)
            val points = List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                obj.getDouble("lat") to obj.getDouble("lng")
            }
            val downsampled = TraceDownsampler.downsample(points, THUMBNAIL_MAX_POINTS)
            RouteThumbnail.buildPathDFromPoints(downsampled).ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val KIND_RAW = "RAW"
        private const val KIND_CORRECTED = "CORRECTED"
        private const val THUMBNAIL_MAX_POINTS = 120
    }
}
