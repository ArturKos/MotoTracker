package com.mototracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.mototracker.data.local.entity.WaveEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [WaveEntity] rows.
 */
@Dao
interface WaveDao {

    /**
     * Inserts a new wave or replaces an existing row with the same primary key.
     *
     * @param entity The wave to persist.
     */
    @Upsert
    suspend fun upsert(entity: WaveEntity)

    /**
     * Removes the given wave from the database.
     *
     * @param entity The wave to remove (matched by primary key).
     */
    @Delete
    suspend fun delete(entity: WaveEntity)

    /**
     * Returns a live stream of all waves.
     */
    @Query("SELECT * FROM waves")
    fun getAll(): Flow<List<WaveEntity>>

    /**
     * Returns a live stream of waves associated with a specific route.
     *
     * @param routeId The route primary key to filter by.
     */
    @Query("SELECT * FROM waves WHERE routeId = :routeId")
    fun getByRouteId(routeId: String): Flow<List<WaveEntity>>

    /**
     * Updates [lastSeenMs] for the encounter row identified by [id].
     *
     * Called by [com.mototracker.service.RecordingService] on each [EncounterEvent.Extended]
     * to keep the encounter's end-time accurate without altering any other field.
     *
     * @param id         Wave UUID (primary key).
     * @param lastSeenMs New wall-clock timestamp in milliseconds.
     */
    @Query("UPDATE waves SET lastSeenMs = :lastSeenMs WHERE id = :id")
    suspend fun updateLastSeen(id: String, lastSeenMs: Long)
}
