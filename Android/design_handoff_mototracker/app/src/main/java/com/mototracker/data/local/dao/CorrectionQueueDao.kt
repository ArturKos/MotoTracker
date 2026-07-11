package com.mototracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.mototracker.data.local.entity.CorrectionQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [CorrectionQueueEntity] rows.
 *
 * Mirrors [SyncQueueDao] but targets the GPS-correction pipeline. The correction
 * drainer observes [getPending] and transitions states as it contacts the OSRM service.
 */
@Dao
interface CorrectionQueueDao {

    /**
     * Inserts a new queue entry or replaces an existing row with the same primary key.
     *
     * @param entity The correction queue entry to persist.
     */
    @Upsert
    suspend fun upsert(entity: CorrectionQueueEntity)

    /**
     * Removes the given queue entry from the database.
     *
     * @param entity The entry to remove (matched by primary key).
     */
    @Delete
    suspend fun delete(entity: CorrectionQueueEntity)

    /**
     * Returns a live stream of all actionable (non-DONE) queue entries, ordered by
     * [CorrectionQueueEntity.nextRetryEpochMs] ascending (nulls first = PENDING).
     */
    @Query(
        """
        SELECT * FROM correction_queue
        WHERE state != 'DONE'
        ORDER BY nextRetryEpochMs ASC
        """,
    )
    fun getPending(): Flow<List<CorrectionQueueEntity>>

    /**
     * Removes all DONE entries, freeing space after a successful correction batch.
     */
    @Query("DELETE FROM correction_queue WHERE state = 'DONE'")
    suspend fun pruneDone()

    /**
     * Returns the number of non-DONE entries — used for any pending-count badge.
     */
    @Query("SELECT COUNT(*) FROM correction_queue WHERE state != 'DONE'")
    fun getPendingCount(): Flow<Int>

    /**
     * Returns the first queue entry for [routeId], or null if none exists.
     *
     * @param routeId The route UUID to look up.
     */
    @Query("SELECT * FROM correction_queue WHERE routeId = :routeId LIMIT 1")
    suspend fun findByRouteId(routeId: String): CorrectionQueueEntity?

    /**
     * Returns a one-shot snapshot of all actionable (non-DONE) entries, ordered by
     * [CorrectionQueueEntity.nextRetryEpochMs] ascending (nulls first).
     *
     * Use this instead of [getPending] inside the drain loop to avoid collecting an
     * infinite Flow for a one-shot read.
     */
    @Query(
        """
        SELECT * FROM correction_queue
        WHERE state != 'DONE'
        ORDER BY nextRetryEpochMs ASC
        """,
    )
    suspend fun getPendingSnapshot(): List<CorrectionQueueEntity>
}
