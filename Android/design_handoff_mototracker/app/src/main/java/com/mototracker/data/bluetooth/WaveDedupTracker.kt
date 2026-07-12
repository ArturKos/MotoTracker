package com.mototracker.data.bluetooth

/**
 * Deduplicates repeated BLE scan hits from the same [shortId] within a sliding time window.
 *
 * A rider who is alongside for many seconds will repeatedly appear in scan results. This
 * tracker gates those duplicates so only the first sighting (and any re-appearance after the
 * window has elapsed) produces a new wave record. Different `shortId` values are tracked
 * independently.
 *
 * This class is intentionally pure (no Android dependencies) so it can be verified by
 * JVM unit tests. The caller is responsible for thread safety when invoked from concurrent
 * BLE scan callbacks.
 *
 * @param windowMs Debounce window in milliseconds. The same [shortId] is suppressed for
 *                 this duration after the first accepted sighting. Default: 5 minutes.
 */
class WaveDedupTracker(private val windowMs: Long = 5 * 60_000L) {

    private val lastAcceptedMs = mutableMapOf<String, Long>()

    /**
     * Returns `true` and records [nowMs] when [shortId] is seen for the first time,
     * or when the [windowMs] window has elapsed since the last accepted sighting.
     *
     * Returns `false` when [shortId] re-appears within the window (duplicate suppressed).
     *
     * @param shortId The 4-character device identifier from the scanned payload.
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
