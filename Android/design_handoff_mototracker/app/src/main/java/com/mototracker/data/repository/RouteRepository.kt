package com.mototracker.data.repository

import com.mototracker.data.model.Route
import com.mototracker.data.model.RouteSummaryModel
import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for recorded routes.
 *
 * List views should use [observeSummaries] (scalar columns only — no trace blobs) to avoid
 * CursorWindow overflow on long rides. Detail views and exports should call [getById] or
 * [observeById] which reassemble the full GPS trace from out-of-row chunks.
 *
 * All writes are suspend functions; reads return [Flow] so the UI reacts to new routes
 * saved during or after recording.
 */
interface RouteRepository {

    /**
     * Persists [route] to local storage, inserting or replacing any row with the same [Route.id].
     *
     * Splits [Route.pathJson] and [Route.correctedPathJson] into chunks stored in
     * [com.mototracker.data.local.entity.RouteTraceChunkEntity] and computes a bounded
     * [com.mototracker.data.local.entity.RouteEntity.thumbnailPathD] from the downsampled raw trace.
     *
     * @param route The route to save.
     */
    suspend fun save(route: Route)

    /**
     * Returns a live stream of lightweight route summaries ordered by [RouteSummaryModel.dateEpochMs] descending.
     *
     * Only scalar columns and the precomputed thumbnail path are included — no GPS trace blobs.
     * This stream is safe to collect on screens that display a list of potentially hundreds of routes.
     */
    fun observeSummaries(): Flow<List<RouteSummaryModel>>

    /**
     * Returns the route with the given [id], or `null` if no such route exists.
     *
     * The full GPS trace ([Route.pathJson] and [Route.correctedPathJson]) is reassembled
     * from chunk rows and included in the result.
     *
     * @param id The route UUID to look up.
     */
    suspend fun getById(id: String): Route?

    /**
     * Returns a live [kotlinx.coroutines.flow.Flow] of the route with [id], emitting `null`
     * when no such route exists. Re-emits whenever the underlying row changes (e.g. after
     * GPS correction updates [Route.correctionStatus] or [Route.confidence]).
     *
     * The full GPS trace is reassembled from chunk rows on each emission.
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

    /**
     * Updates the display name of the route with [id].
     *
     * The name must already be trimmed by the caller. No-op when no matching route exists.
     *
     * @param id   Route UUID.
     * @param name New display name.
     */
    suspend fun rename(id: String, name: String)

    /**
     * Assigns [bikeId] to the route with [routeId].
     *
     * Performs a targeted UPDATE — raw trace and all other fields are untouched.
     * No-op when no matching route exists. [bikeId] may be `null` to clear the
     * bike association.
     *
     * @param routeId Route UUID.
     * @param bikeId  UUID of the motorcycle to assign, or `null` to clear.
     */
    suspend fun setBike(routeId: String, bikeId: String?)

    /**
     * Deletes every route from local storage.
     *
     * Called during a [com.mototracker.domain.backup.RestoreMode.REPLACE] import to remove
     * all existing data before inserting the imported set.
     */
    suspend fun deleteAll()

    /**
     * Updates the estimated fuel consumed for route [routeId].
     *
     * Performs a targeted UPDATE — raw trace and all other fields are untouched.
     * Default no-op; override in implementations and test fakes that need to verify persistence.
     *
     * @param routeId Route UUID.
     * @param fuelL   New fuel amount in litres.
     */
    suspend fun setFuel(routeId: String, fuelL: Double) {}

    /**
     * Sets or clears the per-route fuel price override for route [routeId].
     *
     * Null clears the override so the bike's default price is used instead.
     * Default no-op; override in implementations and test fakes that need to verify persistence.
     *
     * @param routeId   Route UUID.
     * @param pricePerL New price per litre, or null to remove the override.
     */
    suspend fun setFuelPrice(routeId: String, pricePerL: Double?) {}

    /**
     * Permanently deletes the route with [id] and all associated data.
     *
     * **Cascade behaviour (FK ON DELETE):**
     * - `route_trace_chunks` → CASCADE (raw + corrected chunks removed)
     * - `sync_queue`         → CASCADE (pending sync items removed)
     * - `correction_queue`   → CASCADE (pending correction items removed)
     * - `waves`              → SET NULL (wave records lose their route association)
     *
     * No-op when no route with the given [id] exists. Default no-op body matches
     * [setFuel]/[setFuelPrice] convention so test fakes keep compiling without override.
     *
     * @param id Route UUID to delete.
     */
    suspend fun deleteRoute(id: String) {}
}
