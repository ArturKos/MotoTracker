package com.mototracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.mototracker.data.local.entity.RouteEntity
import com.mototracker.data.model.RouteSummaryModel
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [RouteEntity] rows.
 *
 * All mutations are suspend functions; queries that drive UI return [Flow] so screens
 * react immediately to new routes saved during or after recording.
 *
 * GPS trace data (pathJson / correctedPathJson) has been moved out-of-row into
 * [com.mototracker.data.local.entity.RouteTraceChunkEntity]. List queries therefore
 * return lightweight projections via [observeSummaries]; full-trace reads are assembled
 * in the repository layer by joining chunk rows.
 */
@Dao
interface RouteDao {

    /**
     * Inserts a new route or replaces an existing row with the same primary key.
     *
     * @param entity The route to persist.
     */
    @Upsert
    suspend fun upsert(entity: RouteEntity)

    /**
     * Removes the given route from the database.
     *
     * Cascades to any associated [com.mototracker.data.local.entity.SyncQueueEntity] and
     * [com.mototracker.data.local.entity.RouteTraceChunkEntity] entries.
     *
     * @param entity The route to remove (matched by primary key).
     */
    @Delete
    suspend fun delete(entity: RouteEntity)

    /**
     * Returns a live stream of lightweight route summaries ordered by recording date descending.
     *
     * Only scalar columns and the precomputed [RouteSummaryModel.thumbnailPathD] are
     * selected — no large JSON blobs — so this query is safe regardless of ride length.
     */
    @Query(
        """SELECT id, name, dateEpochMs, bikeId, km, durSec, avg, max, lean, elev, fuel,
                  synced, thumbnailPathD, correctionStatus, confidence
           FROM routes ORDER BY dateEpochMs DESC""",
    )
    fun observeSummaries(): Flow<List<RouteSummaryModel>>

    /**
     * Returns the route with the given [id], or `null` if not found.
     *
     * GPS trace fields are NOT included here; the repository assembles them from chunk rows.
     *
     * @param id The primary key to look up.
     */
    @Query("SELECT * FROM routes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RouteEntity?

    /**
     * Marks the route as synced (or un-synced) without a full upsert.
     *
     * @param id     The route primary key.
     * @param synced New sync status.
     */
    @Query("UPDATE routes SET synced = :synced WHERE id = :id")
    suspend fun setSynced(id: String, synced: Boolean)

    /**
     * Returns a live [Flow] of the route with [id], emitting `null` whenever
     * no matching row exists. Downstream collectors re-emit after any UPDATE to
     * the row (e.g. after correction status or confidence changes).
     *
     * @param id Route primary key to observe.
     */
    @Query("SELECT * FROM routes WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<RouteEntity?>

    /**
     * Resets the correction status and confidence for route [id] to their default values.
     *
     * Does NOT touch trace data; the caller ([com.mototracker.data.repository.RouteRepositoryImpl])
     * is responsible for deleting CORRECTED chunks separately via [RouteTraceChunkDao].
     *
     * @param id Route primary key.
     */
    @Query("UPDATE routes SET correctionStatus = 'NONE', confidence = NULL WHERE id = :id")
    suspend fun clearCorrection(id: String)

    /**
     * Updates the display name for the route with [id].
     *
     * @param id   Route primary key.
     * @param name New display name (already trimmed by the caller).
     */
    @Query("UPDATE routes SET name = :name WHERE id = :id")
    suspend fun setName(id: String, name: String)

    /**
     * Assigns [bikeId] to the route with [id] via a targeted SQL UPDATE.
     *
     * @param id     Route primary key.
     * @param bikeId UUID of the motorcycle to assign, or `null` to clear.
     */
    @Query("UPDATE routes SET bikeId = :bikeId WHERE id = :id")
    suspend fun setBike(id: String, bikeId: String?)

    /**
     * Deletes every row from the routes table.
     *
     * Used exclusively by [com.mototracker.data.repository.BackupRepositoryImpl] during a
     * [com.mototracker.domain.backup.RestoreMode.REPLACE] import to clear stale data before
     * inserting the imported set.
     */
    @Query("DELETE FROM routes")
    suspend fun deleteAll()

    /**
     * Updates [thumbnailPathD] for a single route row.
     *
     * Called during migration after computing the downsampled path from existing trace data.
     *
     * @param id             Route primary key.
     * @param thumbnailPathD Precomputed SVG path `d` string.
     */
    @Query("UPDATE routes SET thumbnailPathD = :thumbnailPathD WHERE id = :id")
    suspend fun setThumbnailPathD(id: String, thumbnailPathD: String?)
}
