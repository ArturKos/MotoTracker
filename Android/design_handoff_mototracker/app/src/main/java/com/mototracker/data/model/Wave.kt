package com.mototracker.data.model

/**
 * Pure domain model representing one BLE encounter with another rider.
 *
 * A single encounter spans from [firstSeenMs] (when the BLE signal was first detected)
 * to [lastSeenMs] (the most recent sighting within the encounter gap). Each encounter
 * produces exactly one [Wave] row.
 *
 * @param id          UUID string.
 * @param nick        The other rider's display nickname.
 * @param bikeName    The other rider's bike model name.
 * @param place       Human-readable location label where the wave occurred.
 * @param timeLabel   Formatted clock string shown in the UI, e.g. "14:32".
 * @param routeId     FK to the active route at the time of the wave; null if not recording.
 * @param shortId     BLE device identifier of the peer (4 chars). Empty string for legacy rows.
 * @param firstSeenMs Wall-clock timestamp (ms) when this encounter opened. 0 for legacy rows.
 * @param lastSeenMs  Wall-clock timestamp (ms) of the most recent sighting. 0 for legacy rows.
 */
data class Wave(
    val id: String,
    val nick: String,
    val bikeName: String,
    val place: String,
    val timeLabel: String,
    val routeId: String?,
    val shortId: String = "",
    val firstSeenMs: Long = 0L,
    val lastSeenMs: Long = 0L,
)
