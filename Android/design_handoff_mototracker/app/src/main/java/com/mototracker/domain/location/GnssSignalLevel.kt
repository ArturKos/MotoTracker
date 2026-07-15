package com.mototracker.domain.location

/**
 * Coarse signal-quality classification derived from the GNSS satellite-in-fix count.
 *
 * Used to colour-code the GPS chip on the Recording screen (K7):
 * - [NONE] → red (no satellites; no fix possible)
 * - [ACQUIRING] → amber (1–3 satellites; fix is unreliable)
 * - [FIXED] → accent/speed colour (4+ satellites; usable position fix)
 */
enum class GnssSignalLevel {
    NONE,
    ACQUIRING,
    FIXED;

    companion object {

        /**
         * Classifies [count] into a [GnssSignalLevel] band.
         *
         * @param count Number of satellites currently used in the position fix
         *              ([com.mototracker.data.location.GnssSatelliteCount.usedInFix]).
         *              Values ≤ 0 → [NONE]; 1–3 → [ACQUIRING]; ≥ 4 → [FIXED].
         */
        fun fromSatelliteCount(count: Int): GnssSignalLevel = when {
            count <= 0 -> NONE
            count <= 3 -> ACQUIRING
            else -> FIXED
        }
    }
}
