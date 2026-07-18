package com.mototracker.data.repository

import com.mototracker.domain.fuel.FuelAdjustmentEvent
import com.mototracker.domain.fuel.FuelAdjustmentMode
import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for fuel-level correction events (R1).
 *
 * Intentionally separate from [RefuelRepository] to enforce R1 consumption-ledger
 * isolation: [FuelAdjustmentEvent] rows must never reach
 * [com.mototracker.domain.fuel.FuelConsumptionCalculator].
 */
interface FuelAdjustmentRepository {

    /**
     * Persists a new fuel-level correction event.
     *
     * @param bikeId   UUID of the bike being corrected.
     * @param routeId  UUID of the active route, or null for an off-ride correction.
     * @param epochMs  Wall-clock time of the correction in milliseconds since epoch.
     * @param mode     Whether the value is an absolute level or a signed delta.
     * @param litres   Correction value in litres.
     */
    suspend fun addAdjustment(
        bikeId: String,
        routeId: String?,
        epochMs: Long,
        mode: FuelAdjustmentMode,
        litres: Double,
    )

    /**
     * Returns a live stream of all correction events for [bikeId], ordered
     * chronologically ascending.
     *
     * @param bikeId UUID of the bike.
     */
    fun observeForBike(bikeId: String): Flow<List<FuelAdjustmentEvent>>

    /**
     * Returns the most-recent correction event for [bikeId], or null when none exists.
     *
     * Used by [com.mototracker.ui.screens.bikedetail.BikeDetailViewModel] to pre-fill
     * the off-ride correction dialog with the current estimated remaining fuel.
     *
     * @param bikeId UUID of the bike.
     */
    suspend fun latestForBike(bikeId: String): FuelAdjustmentEvent?
}
