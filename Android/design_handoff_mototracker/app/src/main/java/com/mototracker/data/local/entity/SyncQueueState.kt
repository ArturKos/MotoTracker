package com.mototracker.data.local.entity

/**
 * Processing state of a [SyncQueueEntity] entry.
 *
 * Stored as its [name] string via Room TypeConverter.
 */
enum class SyncQueueState {
    /** Waiting for the next sync attempt. */
    PENDING,

    /** Currently being sent to the server (prevents concurrent duplicate sends). */
    IN_PROGRESS,

    /** Last attempt failed; will be retried after [SyncQueueEntity.nextRetryEpochMs]. */
    FAILED,

    /** Successfully delivered to the server; safe to remove from the queue. */
    DONE,
}
