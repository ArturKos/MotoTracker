package com.mototracker.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Manages the queue of routes awaiting OSRM GPS road-correction.
 *
 * Mirrors the structure of [SyncRepository] but targets the OSRM map-matching
 * pipeline instead of the GPStrack upload pipeline.
 */
interface GpsCorrectionRepository {

    /** Live count of queue entries that are not yet DONE. */
    val pendingCount: Flow<Int>

    /**
     * Enqueues [routeId] for road-correction, resetting any previous FAILED/DONE
     * state so the route is re-processed on the next drain cycle.
     *
     * @param routeId UUID of the route to correct.
     */
    suspend fun enqueue(routeId: String)

    /**
     * Immediately processes all due queue entries, regardless of the auto-drain setting.
     *
     * Returns 0 when `noInternet` is true (all network access blocked).
     *
     * @return Number of routes successfully corrected (quality-gate ACCEPT) in this call.
     */
    suspend fun correctNow(): Int

    /**
     * Launches the background auto-drain coroutines in [scope].
     *
     * The drain fires on start, whenever network connectivity changes, and whenever
     * settings change. It is gated on LAN reachability (transport failure counts as
     * offline) and the `noInternet` master flag.
     *
     * @param scope Coroutine scope that owns the launched jobs.
     */
    fun start(scope: CoroutineScope)
}
