package com.mototracker.data.repository

import com.mototracker.data.model.Bike
import kotlinx.coroutines.flow.Flow

/**
 * Read-only persistence contract for motorcycles.
 *
 * Returns a live [Flow] so the routes list and any other consumer react to
 * bike additions, renames, or status changes without polling.
 */
interface BikeRepository {

    /**
     * Returns a live stream of all bikes; order is unspecified.
     */
    fun observeAll(): Flow<List<Bike>>
}
