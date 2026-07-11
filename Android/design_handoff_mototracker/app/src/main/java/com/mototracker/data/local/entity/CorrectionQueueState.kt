package com.mototracker.data.local.entity

/**
 * Processing state of a [CorrectionQueueEntity] entry.
 *
 * Stored as its [name] string via Room TypeConverter.
 */
enum class CorrectionQueueState {
    /** Waiting to be sent to the OSRM map-matching service. */
    PENDING,

    /** Currently in progress (prevents concurrent duplicate requests). */
    IN_PROGRESS,

    /** Last attempt failed; will be retried after [CorrectionQueueEntity.nextRetryEpochMs]. */
    FAILED,

    /** Successfully processed; safe to prune from the queue. */
    DONE,
}
