package com.mototracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.mototracker.data.local.entity.RouteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [RouteEntity] rows.
 *
 * All mutations are suspend functions; queries return [Flow] so the UI
 * reacts to new routes saved during or after recording.
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
     * Cascades to any associated [com.mototracker.data.local.entity.SyncQueueEntity] entries.
     *
     * @param entity The route to remove (matched by primary key).
     */
    @Delete
    suspend fun delete(entity: RouteEntity)

    /**
     * Returns a live stream of all routes ordered by recording date descending.
     */
    @Query("SELECT * FROM routes ORDER BY dateEpochMs DESC")
    fun getAll(): Flow<List<RouteEntity>>

    /**
     * Returns the route with the given [id], or `null` if not found.
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
     * Clears the road-corrected trace for route [id], resetting [RouteEntity.correctionStatus]
     * to `'NONE'` and nulling out [RouteEntity.correctedPathJson] and [RouteEntity.confidence].
     *
     * The raw [RouteEntity.pathJson] is **never** modified — it is the permanent source of truth.
     *
     * @param id Route primary key.
     */
    @Query("UPDATE routes SET correctedPathJson = NULL, correctionStatus = 'NONE', confidence = NULL WHERE id = :id")
    suspend fun clearCorrection(id: String)

    /**
     * Updates the display name for the route with [id].
     *
     * Called by [com.mototracker.data.repository.RouteRepositoryImpl.rename].
     *
     * @param id   Route primary key.
     * @param name New display name (already trimmed by the caller).
     */
    @Query("UPDATE routes SET name = :name WHERE id = :id")
    suspend fun setName(id: String, name: String)

    /**
     * Assigns [bikeId] to the route with [id] via a targeted SQL UPDATE.
     *
     * [bikeId] is nullable — passing `null` clears the bike association without
     * touching any other column (raw trace, correctionStatus, etc.).
     * No Room migration is required because the [com.mototracker.data.local.entity.RouteEntity.bikeId]
     * column already exists.
     *
     * Called by [com.mototracker.data.repository.RouteRepositoryImpl.setBike].
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
}
