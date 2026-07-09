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
}
