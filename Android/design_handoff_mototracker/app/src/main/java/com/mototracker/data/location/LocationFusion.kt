package com.mototracker.data.location

import com.mototracker.domain.recording.LocationSample

/**
 * Pure, dependency-free location fusion logic.
 *
 * Merges a GPS candidate and a network candidate into the single best available position.
 * Contains no Android types so it is fully unit-testable on the plain JVM.
 */
object LocationFusion {

    /**
     * Returns the best available position from two candidate samples.
     *
     * Selection rules (applied in order):
     * 1. Both null → null.
     * 2. Only one non-null → return it.
     * 3. GPS fresh **and** network stale → GPS.
     * 4. Network fresh **and** GPS stale → network.
     * 5. Both stale → smaller [LocationSample.accuracyM] wins; GPS wins ties
     *    (and when accuracyM is 0.0 / unknown, GPS is preferred).
     * 6. Both fresh → smaller [LocationSample.accuracyM] wins; GPS wins ties.
     *
     * @param latestGps     Most recent GPS fix, or null if none has arrived yet.
     * @param latestNetwork Most recent network fix, or null if none has arrived yet.
     * @param nowMs         Current wall-clock time in milliseconds since the Unix epoch.
     * @param gpsFreshMs    Maximum age (ms) for a fix to be considered fresh. Default 10 s.
     * @return The preferred [LocationSample], or null when both inputs are null.
     */
    fun prefer(
        latestGps: LocationSample?,
        latestNetwork: LocationSample?,
        nowMs: Long,
        gpsFreshMs: Long = 10_000L,
    ): LocationSample? {
        if (latestGps == null && latestNetwork == null) return null
        if (latestGps == null) return latestNetwork
        if (latestNetwork == null) return latestGps

        val gpsFresh = (nowMs - latestGps.timeMs) <= gpsFreshMs
        val networkFresh = (nowMs - latestNetwork.timeMs) <= gpsFreshMs

        return when {
            gpsFresh && !networkFresh -> latestGps
            !gpsFresh && networkFresh -> latestNetwork
            // both fresh or both stale: prefer smaller accuracyM; GPS wins on tie (<=)
            else -> if (latestGps.accuracyM <= latestNetwork.accuracyM) latestGps else latestNetwork
        }
    }
}
