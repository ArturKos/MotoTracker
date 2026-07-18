package com.mototracker.data.bluetooth

/**
 * Lightweight per-shortId throttle that suppresses raw BLE scan duplicates within a
 * short time window.
 *
 * BLE low-latency scanning can report the same peer dozens of times per second.
 * [SightingThrottle] keeps only the first sighting within [windowMs] (default 3 s)
 * and drops the rest so that [EncounterTracker] is not flooded with redundant data.
 *
 * The window is intentionally short (seconds, not minutes); gap-based encounter splitting
 * is handled upstream by [EncounterTracker].
 *
 * No Android dependencies — fully JVM-testable.
 *
 * @param windowMs Throttle window in milliseconds. Sightings of the same [shortId]
 *                 within this window after the last accepted one are suppressed.
 *                 Default: 3 000 ms.
 */
class SightingThrottle(private val windowMs: Long = 3_000L) {

    private val lastAcceptedMs = mutableMapOf<String, Long>()

    /**
     * Returns `true` and records [nowMs] when [shortId] is seen for the first time
     * or when [windowMs] has elapsed since the last accepted sighting.
     *
     * Returns `false` when [shortId] re-appears within the throttle window.
     *
     * @param shortId 4-character rider identifier from the scanned BLE payload.
     * @param nowMs   Current elapsed-realtime timestamp in milliseconds.
     */
    fun accept(shortId: String, nowMs: Long): Boolean {
        val last = lastAcceptedMs[shortId]
        if (last == null || nowMs - last >= windowMs) {
            lastAcceptedMs[shortId] = nowMs
            return true
        }
        return false
    }
}
