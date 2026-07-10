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
}
