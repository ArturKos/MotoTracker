package com.mototracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a known BLE peer rider encountered at least once.
 *
 * One row per [shortId]. The row is created on first sighting and updated on every
 * subsequent sighting so the rider table serves as a live directory of encountered
 * riders. The [inGroup] flag is managed exclusively via
 * [com.mototracker.data.local.dao.RiderDao.setInGroup] and is intentionally preserved
 * across sighting updates — it may only be changed by explicit user action (X2).
 *
 * @param shortId    BLE device identifier (4 uppercase alphanumeric chars). Primary key.
 * @param nick       Display nickname from the rider's BLE broadcast payload.
 * @param bike       Bike model name from the rider's BLE broadcast payload.
 * @param lastSeenMs Wall-clock timestamp (System.currentTimeMillis()) of the most recent sighting.
 * @param inGroup    Whether this rider is currently in the active group. Defaults to false.
 *                   Not overwritten by sighting updates.
 */
@Entity(tableName = "rider")
data class RiderEntity(
    @PrimaryKey val shortId: String,
    val nick: String,
    val bike: String,
    val lastSeenMs: Long,
    val inGroup: Boolean = false,
)
