package com.mototracker.data.repository

import com.mototracker.data.model.Bike
import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for motorcycles.
 *
 * Returns a live [Flow] so the routes list and any other consumer react to
 * bike additions, renames, or status changes without polling.
 */
interface BikeRepository {

    /**
     * Returns a live stream of all bikes; order is unspecified.
     */
    fun observeAll(): Flow<List<Bike>>

    /**
     * Inserts or replaces the given [bike] in local storage.
     *
     * Safe to call with a freshly generated UUID to create a new bike, or with
     * an existing UUID to update it.
     *
     * @param bike The bike to persist.
     */
    suspend fun addBike(bike: Bike)
}
