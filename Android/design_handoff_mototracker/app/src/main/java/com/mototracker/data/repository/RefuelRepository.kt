package com.mototracker.data.repository

import com.mototracker.domain.fuel.RefuelEvent
import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for the per-route refuel event ledger.
 *
 * Implementations map [RefuelEvent] domain objects to
 * [com.mototracker.data.local.entity.RefuelEventEntity] rows in Room.
 */
interface RefuelRepository {

    /**
     * Persists a new refuel event for the given route.
     *
     * @param routeId   UUID of the parent route.
     * @param epochMs   Wall-clock time of the event in milliseconds since epoch.
     * @param litres    Volume of fuel added in litres.
     * @param pricePerL Price per litre at the time of the event.
     */
    suspend fun addRefuel(routeId: String, epochMs: Long, litres: Double, pricePerL: Double)

    /**
     * Returns a live stream of all refuel events for [routeId], ordered chronologically.
     *
     * Re-emits whenever a row is inserted or deleted.
     *
     * @param routeId UUID of the parent route.
     */
    fun observeRefuels(routeId: String): Flow<List<RefuelEvent>>

    /**
     * Permanently deletes the refuel event with the given [id].
     *
     * @param id Primary key of the event to remove.
     */
    suspend fun deleteRefuel(id: Long)
}
