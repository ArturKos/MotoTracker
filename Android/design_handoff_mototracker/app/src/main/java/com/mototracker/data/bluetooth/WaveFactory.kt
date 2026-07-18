package com.mototracker.data.bluetooth

import com.mototracker.data.model.Wave

/**
 * Maps a [DiscoveredRider] from a BLE scan to the domain [Wave] model so it can be
 * persisted via the existing Wave DAO and surfaced on the Riders screen.
 *
 * This object is intentionally pure (no Android or IO dependencies) so it is fully
 * covered by JVM unit tests.
 */
object WaveFactory {

    /**
     * Constructs a [Wave] from a scanned [DiscoveredRider] and caller-provided context.
     *
     * @param rider       The decoded rider information from the BLE advertisement.
     * @param place       Human-readable location label at the time of the wave (empty string
     *                    when a reverse-geocode result is not yet available).
     * @param timeLabel   Formatted clock string shown in the UI, e.g. "14:32".
     * @param routeId     UUID of the active route at the time of the wave; null if not recording.
     * @param id          Pre-generated UUID string to use as the wave's primary key.
     * @param firstSeenMs Wall-clock timestamp (ms) when the encounter opened.
     * @param lastSeenMs  Wall-clock timestamp (ms) of the most recent sighting so far.
     * @return A fully populated [Wave] ready for persistence.
     */
    fun toWave(
        rider: DiscoveredRider,
        place: String,
        timeLabel: String,
        routeId: String?,
        id: String,
        firstSeenMs: Long = 0L,
        lastSeenMs: Long = 0L,
    ): Wave = Wave(
        id = id,
        nick = rider.nick,
        bikeName = rider.bike,
        place = place,
        timeLabel = timeLabel,
        routeId = routeId,
        shortId = rider.shortId,
        firstSeenMs = firstSeenMs,
        lastSeenMs = lastSeenMs,
    )
}
