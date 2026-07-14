package com.mototracker.data.repository

import com.mototracker.data.local.dao.RefuelEventDao
import com.mototracker.data.local.entity.RefuelEventEntity
import com.mototracker.domain.fuel.RefuelEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * [RefuelRepository] backed by [RefuelEventDao] (Room / SQLite).
 *
 * Maps between [RefuelEventEntity] (data layer) and [RefuelEvent] (domain layer).
 * All IO operations run on whatever dispatcher the caller uses; callers are expected
 * to switch to [kotlinx.coroutines.Dispatchers.IO] where appropriate.
 *
 * @param dao Room DAO for refuel event rows.
 */
class RefuelRepositoryImpl @Inject constructor(
    private val dao: RefuelEventDao,
) : RefuelRepository {

    override suspend fun addRefuel(
        routeId: String,
        epochMs: Long,
        litres: Double,
        pricePerL: Double,
    ) {
        dao.insert(
            RefuelEventEntity(
                routeId = routeId,
                epochMs = epochMs,
                litres = litres,
                pricePerL = pricePerL,
            ),
        )
    }

    override fun observeRefuels(routeId: String): Flow<List<RefuelEvent>> =
        dao.observeForRoute(routeId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun deleteRefuel(id: Long) {
        dao.deleteById(id)
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun RefuelEventEntity.toDomain() = RefuelEvent(
        id = id,
        routeId = routeId,
        epochMs = epochMs,
        litres = litres,
        pricePerL = pricePerL,
    )
}
