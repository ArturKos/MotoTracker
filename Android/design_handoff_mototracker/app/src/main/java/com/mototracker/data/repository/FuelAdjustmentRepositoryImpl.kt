package com.mototracker.data.repository

import com.mototracker.data.local.dao.FuelAdjustmentEventDao
import com.mototracker.data.local.entity.FuelAdjustmentEventEntity
import com.mototracker.domain.fuel.FuelAdjustmentEvent
import com.mototracker.domain.fuel.FuelAdjustmentMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * [FuelAdjustmentRepository] backed by [FuelAdjustmentEventDao] (Room / SQLite).
 *
 * Maps between [FuelAdjustmentEventEntity] (data layer) and [FuelAdjustmentEvent]
 * (domain layer). All IO operations run on whatever dispatcher the caller uses;
 * callers are expected to switch to [kotlinx.coroutines.Dispatchers.IO] where appropriate.
 *
 * @param dao Room DAO for fuel-adjustment event rows.
 */
class FuelAdjustmentRepositoryImpl @Inject constructor(
    private val dao: FuelAdjustmentEventDao,
) : FuelAdjustmentRepository {

    override suspend fun addAdjustment(
        bikeId: String,
        routeId: String?,
        epochMs: Long,
        mode: FuelAdjustmentMode,
        litres: Double,
    ) {
        dao.insert(
            FuelAdjustmentEventEntity(
                bikeId = bikeId,
                routeId = routeId,
                epochMs = epochMs,
                mode = mode.name,
                litres = litres,
            ),
        )
    }

    override fun observeForBike(bikeId: String): Flow<List<FuelAdjustmentEvent>> =
        dao.observeForBike(bikeId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun latestForBike(bikeId: String): FuelAdjustmentEvent? =
        dao.latestForBike(bikeId)?.toDomain()

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun FuelAdjustmentEventEntity.toDomain() = FuelAdjustmentEvent(
        id = id,
        bikeId = bikeId,
        routeId = routeId,
        epochMs = epochMs,
        mode = runCatching { FuelAdjustmentMode.valueOf(mode) }.getOrDefault(FuelAdjustmentMode.SET_ABSOLUTE),
        litres = litres,
    )
}
