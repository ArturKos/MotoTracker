package com.mototracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an entry in the OSRM GPS-correction queue.
 *
 * Each entry tracks a single [RouteEntity] pending map-matching. The retry
 * state machine mirrors [SyncQueueEntity]: PENDING → IN_PROGRESS → DONE (success)
 * or FAILED (error, will retry with exponential back-off).
 *
 * @param id                   Auto-generated surrogate key.
 * @param routeId              FK to the [RouteEntity] to be corrected.
 * @param state                Current processing state.
 * @param attemptCount         Total number of match attempts made so far.
 * @param lastAttemptEpochMs   Epoch ms of the most recent attempt; null if never tried.
 * @param nextRetryEpochMs     Earliest epoch ms at which the next attempt should run; null for PENDING/DONE.
 * @param lastError            Error message from the last failed attempt; null if no error.
 */
@Entity(
    tableName = "correction_queue",
    foreignKeys = [ForeignKey(
        entity = RouteEntity::class,
        parentColumns = ["id"],
        childColumns = ["routeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("routeId")],
)
data class CorrectionQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: String,
    val state: CorrectionQueueState,
    val attemptCount: Int,
    val lastAttemptEpochMs: Long?,
    val nextRetryEpochMs: Long?,
    val lastError: String?,
)
