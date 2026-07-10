package com.mototracker.data.repository

import com.mototracker.data.model.Wave
import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for Bluetooth "wave" greetings between riders.
 *
 * Returns [Flow]s so screens can react to newly received waves without polling.
 */
interface WaveRepository {

    /**
     * Returns a live stream of all waves associated with [routeId].
     *
     * @param routeId The route UUID to filter by.
     */
    fun observeForRoute(routeId: String): Flow<List<Wave>>

    /**
     * Returns a live stream of **all** waves across all routes.
     *
     * Used by the Riders screen to show received waves regardless of route.
     * The database is empty until BT scanning is real (🔬).
     */
    fun observeAll(): Flow<List<Wave>>
}
