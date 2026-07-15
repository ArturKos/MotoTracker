package com.mototracker.data.location

/**
 * Snapshot of GNSS satellite availability at a point in time.
 *
 * @param usedInFix Number of satellites contributing to the current position fix.
 * @param total     Total number of satellites visible to the receiver.
 */
data class GnssSatelliteCount(
    val usedInFix: Int,
    val total: Int,
)

/**
 * Pure helper for deriving a [GnssSatelliteCount] from raw [android.location.GnssStatus] data.
 *
 * Extracted as a testable seam because [android.location.GnssStatus] cannot be constructed
 * in JVM unit tests.
 */
object GnssSatelliteCounter {

    /**
     * Builds a [GnssSatelliteCount] by iterating satellite indices [0, total).
     *
     * @param total      Value returned by [android.location.GnssStatus.getSatelliteCount].
     *                   Values ≤ 0 produce [GnssSatelliteCount] of (0, 0).
     * @param usedInFix  Predicate: returns `true` when the satellite at the given index
     *                   is contributing to the current position fix.
     */
    fun count(total: Int, usedInFix: (Int) -> Boolean): GnssSatelliteCount {
        if (total <= 0) return GnssSatelliteCount(usedInFix = 0, total = 0)
        var used = 0
        for (i in 0 until total) {
            if (usedInFix(i)) used++
        }
        return GnssSatelliteCount(usedInFix = used, total = total)
    }
}
