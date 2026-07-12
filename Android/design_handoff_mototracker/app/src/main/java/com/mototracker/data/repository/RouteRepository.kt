package com.mototracker.data.repository

import com.mototracker.data.model.Route
import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for recorded routes.
 *
 * All writes are suspend functions; reads return [Flow] so the UI reacts to new routes
 * saved during or after recording.
 */
interface RouteRepository {

    /**
     * Persists [route] to local storage, inserting or replacing any row with the same [Route.id].
     *
     * @param route The route to save.
     */
    suspend fun save(route: Route)

    /**
     * Returns a live stream of all routes ordered by [Route.dateEpochMs] descending.
     */
    fun observeAll(): Flow<List<Route>>

    /**
     * Returns the route with the given [id], or `null` if no such route exists.
     *
     * @param id The route UUID to look up.
     */
    suspend fun getById(id: String): Route?

    /**
     * Returns a live [kotlinx.coroutines.flow.Flow] of the route with [id], emitting `null`
     * when no such route exists. Re-emits whenever the underlying row changes (e.g. after
     * GPS correction updates [Route.correctedPathJson] or [Route.correctionStatus]).
     *
     * @param id The route UUID to observe.
     */
    fun observeById(id: String): Flow<Route?>

    /**
     * Removes the road-snapped trace for route [id] and resets its correction status to NONE.
     *
     * The raw GPS trace ([Route.pathJson]) is the permanent source of truth and is **never**
     * modified by this call.
     *
     * @param id The route UUID to clear.
     */
    suspend fun clearCorrectedTrace(id: String)
}
