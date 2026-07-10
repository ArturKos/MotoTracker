package com.mototracker.data.repository

import com.mototracker.data.model.Wave
import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for Bluetooth "wave" greetings between riders.
 *
 * Returns a [Flow] so the route-detail screen can react to newly received
 * waves without polling.
 */
interface WaveRepository {

    /**
     * Returns a live stream of all waves associated with [routeId].
     *
     * @param routeId The route UUID to filter by.
     */
    fun observeForRoute(routeId: String): Flow<List<Wave>>
}
