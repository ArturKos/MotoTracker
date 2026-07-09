package com.mototracker.data.model

import com.mototracker.data.local.entity.SyncQueueState

/**
 * Pure domain model representing an entry in the outbound sync queue.
 *
 * @param id                   Surrogate key (matches [com.mototracker.data.local.entity.SyncQueueEntity.id]).
 * @param routeId              ID of the route to be uploaded.
 * @param state                Current processing state.
 * @param attemptCount         Number of upload attempts made so far.
 * @param lastAttemptEpochMs   Epoch ms of the last attempt; null if never tried.
 * @param nextRetryEpochMs     Earliest time to retry; null for PENDING/DONE entries.
 * @param lastError            Error message from the most recent failure; null if none.
 */
data class SyncItem(
    val id: Long,
    val routeId: String,
    val state: SyncQueueState,
    val attemptCount: Int,
    val lastAttemptEpochMs: Long?,
    val nextRetryEpochMs: Long?,
    val lastError: String?,
)
