package com.mototracker.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Manages the outbound sync queue that delivers locally recorded routes to the GPStrack server.
 *
 * Callers add routes via [enqueue]; the repository drains them automatically when online
 * (via [start]) or on demand via [syncNow].
 */
interface SyncRepository {

    /**
     * Live count of pending (non-DONE) entries in the sync queue.
     * Used to drive the badge/chip in the UI.
     */
    val pendingCount: Flow<Int>

    /**
     * Adds a new PENDING entry for [routeId] to the sync queue.
     *
     * Safe to call at any time, including when the app is offline.
     *
     * @param routeId UUID of the route to be synced.
     */
    suspend fun enqueue(routeId: String)

    /**
     * Manually drains all due sync-queue entries, regardless of the `syncEnabled` setting.
     *
     * Returns immediately with 0 if `noInternet` is true (all network access blocked).
     *
     * @return The number of routes successfully uploaded in this call.
     */
    suspend fun syncNow(): Int

    /**
     * Starts the background auto-drain coroutine inside [scope].
     *
     * The coroutine watches network connectivity and app settings; it drains due queue entries
     * whenever `isOnline && !noInternet && syncEnabled`.
     *
     * Call once per process, typically from the Application class or a singleton component.
     *
     * @param scope Coroutine scope that owns the background loop's lifetime.
     */
    fun start(scope: CoroutineScope)
}
