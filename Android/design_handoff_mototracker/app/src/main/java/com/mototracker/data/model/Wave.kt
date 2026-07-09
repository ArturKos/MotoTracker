package com.mototracker.data.model

/**
 * Pure domain model representing a Bluetooth "wave" greeting between riders.
 *
 * @param id        UUID string.
 * @param nick      The other rider's display nickname.
 * @param bikeName  The other rider's bike model name.
 * @param place     Human-readable location label where the wave occurred.
 * @param timeLabel Formatted time string, e.g. "14:32".
 * @param routeId   FK to the active route at the time of the wave; null if not recording.
 */
data class Wave(
    val id: String,
    val nick: String,
    val bikeName: String,
    val place: String,
    val timeLabel: String,
    val routeId: String?,
)
