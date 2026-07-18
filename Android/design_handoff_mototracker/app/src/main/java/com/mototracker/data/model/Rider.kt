package com.mototracker.data.model

/**
 * Domain model representing a known BLE peer rider.
 *
 * Mirrors [com.mototracker.data.local.entity.RiderEntity] at the domain boundary —
 * one row per [shortId]. Constructed by [com.mototracker.data.repository.RiderRepositoryImpl]
 * from the Room entity and exposed to ViewModels via [com.mototracker.data.repository.RiderRepository].
 *
 * @param shortId    BLE device identifier (4 uppercase alphanumeric chars).
 * @param nick       Display nickname from the rider's BLE broadcast payload.
 * @param bike       Bike model name from the rider's BLE broadcast payload.
 * @param lastSeenMs Wall-clock timestamp (System.currentTimeMillis()) of the most recent sighting.
 * @param inGroup    Whether this rider is currently in the active group.
 */
data class Rider(
    val shortId: String,
    val nick: String,
    val bike: String,
    val lastSeenMs: Long,
    val inGroup: Boolean,
)
