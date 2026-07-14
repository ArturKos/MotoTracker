package com.mototracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mototracker.data.local.entity.RouteTraceChunkEntity

/**
 * Data Access Object for [RouteTraceChunkEntity] rows.
 *
 * Chunks are always written atomically via [replace] (delete-then-insert in a single
 * transaction) so a partial write never leaves the table in an inconsistent state.
 * Reads use [getChunks] which returns rows in ascending [RouteTraceChunkEntity.seq]
 * order so the caller can concatenate them directly without sorting.
 */
@Dao
interface RouteTraceChunkDao {

    /**
     * Atomically replaces all chunks for ([routeId], [kind]) with [chunks].
     *
     * @param routeId Route primary key.
     * @param kind    `"RAW"` or `"CORRECTED"`.
     * @param chunks  Ordered list of chunk entities; may be empty to clear the trace.
     */
    @Transaction
    suspend fun replace(routeId: String, kind: String, chunks: List<RouteTraceChunkEntity>) {
        deleteFor(routeId, kind)
        if (chunks.isNotEmpty()) insertAll(chunks)
    }

    /**
     * Returns all chunks for ([routeId], [kind]) ordered by [RouteTraceChunkEntity.seq] ascending.
     *
     * Room reads this result set incrementally so no single CursorWindow load can overflow,
     * even for very large traces.
     *
     * @param routeId Route primary key.
     * @param kind    `"RAW"` or `"CORRECTED"`.
     */
    @Query("SELECT * FROM route_trace_chunk WHERE routeId = :routeId AND kind = :kind ORDER BY seq ASC")
    suspend fun getChunks(routeId: String, kind: String): List<RouteTraceChunkEntity>

    /**
     * Deletes all chunks for ([routeId], [kind]).
     *
     * Called internally by [replace]; also called directly by
     * [com.mototracker.data.repository.RouteRepositoryImpl.clearCorrectedTrace] to discard
     * only the CORRECTED trace while leaving RAW chunks intact.
     *
     * @param routeId Route primary key.
     * @param kind    `"RAW"` or `"CORRECTED"`.
     */
    @Query("DELETE FROM route_trace_chunk WHERE routeId = :routeId AND kind = :kind")
    suspend fun deleteFor(routeId: String, kind: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<RouteTraceChunkEntity>)
}
