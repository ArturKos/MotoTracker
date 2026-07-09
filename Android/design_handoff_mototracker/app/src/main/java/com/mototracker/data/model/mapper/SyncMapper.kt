package com.mototracker.data.model.mapper

import com.mototracker.data.local.entity.SyncQueueEntity
import com.mototracker.data.model.SyncItem

/** Converts a [SyncQueueEntity] to its domain representation. */
fun SyncQueueEntity.toDomain(): SyncItem = SyncItem(
    id = id,
    routeId = routeId,
    state = state,
    attemptCount = attemptCount,
    lastAttemptEpochMs = lastAttemptEpochMs,
    nextRetryEpochMs = nextRetryEpochMs,
    lastError = lastError,
)

/** Converts a [SyncItem] domain model to its Room entity counterpart. */
fun SyncItem.toEntity(): SyncQueueEntity = SyncQueueEntity(
    id = id,
    routeId = routeId,
    state = state,
    attemptCount = attemptCount,
    lastAttemptEpochMs = lastAttemptEpochMs,
    nextRetryEpochMs = nextRetryEpochMs,
    lastError = lastError,
)
