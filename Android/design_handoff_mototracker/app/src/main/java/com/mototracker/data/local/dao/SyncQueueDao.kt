package com.mototracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.mototracker.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [SyncQueueEntity] rows.
 *
 * The sync drainer (A7) observes [getPending] and transitions states as
 * it attempts uploads. Entries in state [com.mototracker.data.local.entity.SyncQueueState.DONE]
 * are excluded and should be periodically pruned.
 */
@Dao
interface SyncQueueDao {

    /**
     * Inserts a new queue entry or replaces an existing row with the same primary key.
     *
     * @param entity The sync queue entry to persist.
     */
    @Upsert
    suspend fun upsert(entity: SyncQueueEntity)

    /**
     * Removes the given queue entry from the database.
     *
     * @param entity The entry to remove (matched by primary key).
     */
    @Delete
    suspend fun delete(entity: SyncQueueEntity)

    /**
     * Returns a live stream of all actionable (non-DONE) queue entries, ordered by
     * [SyncQueueEntity.nextRetryEpochMs] ascending so the earliest-due entry is first.
     *
     * NULL [SyncQueueEntity.nextRetryEpochMs] values sort first (PENDING entries).
     */
    @Query(
        """
        SELECT * FROM sync_queue
        WHERE state != 'DONE'
        ORDER BY nextRetryEpochMs ASC
        """
    )
    fun getPending(): Flow<List<SyncQueueEntity>>

    /**
     * Removes all entries whose state is DONE, freeing space after a successful sync batch.
     */
    @Query("DELETE FROM sync_queue WHERE state = 'DONE'")
    suspend fun pruneDone()

    /**
     * Returns the number of non-DONE entries — used for the sync chip counter.
     */
    @Query("SELECT COUNT(*) FROM sync_queue WHERE state != 'DONE'")
    fun getPendingCount(): Flow<Int>
}
