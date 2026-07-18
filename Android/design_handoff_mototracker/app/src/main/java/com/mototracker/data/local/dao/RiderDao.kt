package com.mototracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.mototracker.data.local.entity.RiderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [RiderEntity] rows.
 *
 * All writes that touch [RiderEntity.inGroup] must go through [setInGroup]; sighting
 * updates via [upsertSighting] intentionally skip the inGroup column so user-set
 * group membership is never clobbered by a raw BLE sighting.
 */
@Dao
interface RiderDao {

    /**
     * Inserts a new rider row if [shortId] is not yet known, otherwise updates only
     * the display fields and [lastSeenMs] — preserving the existing [RiderEntity.inGroup] value.
     *
     * Implemented as INSERT OR IGNORE + UPDATE so the inGroup column is never touched
     * during a sighting update.
     *
     * @param shortId    BLE device identifier.
     * @param nick       Display nickname from the broadcast payload.
     * @param bike       Bike model from the broadcast payload.
     * @param lastSeenMs Current wall-clock timestamp in milliseconds.
     */
    @Transaction
    suspend fun upsertSighting(shortId: String, nick: String, bike: String, lastSeenMs: Long) {
        insertIgnore(shortId, nick, bike, lastSeenMs)
        updateSighting(shortId, nick, bike, lastSeenMs)
    }

    @Query(
        "INSERT OR IGNORE INTO rider (shortId, nick, bike, lastSeenMs, inGroup) VALUES (:shortId, :nick, :bike, :lastSeenMs, 0)",
    )
    suspend fun insertIgnore(shortId: String, nick: String, bike: String, lastSeenMs: Long)

    @Query(
        "UPDATE rider SET nick = :nick, bike = :bike, lastSeenMs = :lastSeenMs WHERE shortId = :shortId",
    )
    suspend fun updateSighting(shortId: String, nick: String, bike: String, lastSeenMs: Long)

    /**
     * Returns the rider row for [shortId], or null if not yet known.
     *
     * @param shortId BLE device identifier.
     */
    @Query("SELECT * FROM rider WHERE shortId = :shortId")
    suspend fun get(shortId: String): RiderEntity?

    /**
     * Returns a live stream of all known riders, ordered by most recently seen first.
     * Used by the Riders screen (X2).
     */
    @Query("SELECT * FROM rider ORDER BY lastSeenMs DESC")
    fun getAll(): Flow<List<RiderEntity>>

    /**
     * Sets the [RiderEntity.inGroup] flag for [shortId].
     *
     * Called from the group editor (X2). Does not affect any other column.
     *
     * @param shortId BLE device identifier.
     * @param inGroup The new group-membership state.
     */
    @Query("UPDATE rider SET inGroup = :inGroup WHERE shortId = :shortId")
    suspend fun setInGroup(shortId: String, inGroup: Boolean)
}
