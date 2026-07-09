package com.mototracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an entry in the outbound sync queue.
 *
 * Each entry tracks a single [RouteEntity] that needs to be uploaded to the
 * GPStrack server. The retry state machine transitions:
 * PENDING → IN_PROGRESS → DONE (success) or FAILED (error, will retry).
 *
 * @param id                   Auto-generated surrogate key.
 * @param routeId              FK to the [RouteEntity] to be synced.
 * @param state                Current processing state.
 * @param attemptCount         Total number of upload attempts made so far.
 * @param lastAttemptEpochMs   Epoch ms of the most recent attempt; null if never tried.
 * @param nextRetryEpochMs     Earliest epoch ms at which the next attempt should run; null for PENDING/DONE.
 * @param lastError            Error message from the last failed attempt; null if no error.
 */
@Entity(
    tableName = "sync_queue",
    foreignKeys = [ForeignKey(
        entity = RouteEntity::class,
        parentColumns = ["id"],
        childColumns = ["routeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("routeId")],
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: String,
    val state: SyncQueueState,
    val attemptCount: Int,
    val lastAttemptEpochMs: Long?,
    val nextRetryEpochMs: Long?,
    val lastError: String?,
)
