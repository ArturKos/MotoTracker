package com.mototracker.domain.recording

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure, Android-free smoother that removes low-speed GPS jitter from a track at **render time**.
 *
 * Design decisions worth preserving:
 * - The raw stored trace is **never rewritten**. Odometer calculations always run on the full
 *   original sequence, so shortening the visible path never shortens recorded distance.
 * - Smoothing runs before [TrackSegmenter.split] so that a legitimate GPS-reacquire jump
 *   (> 60 m) passes the 3 m gate and is still visible to the segmenter.
 * - The algorithm is a **minimum-move gate**: a point is kept only when it is more than
 *   [minMoveM] metres from the last kept point. The first and last points are always kept —
 *   the first because there is no previous point to compare against, the last because truncating
 *   the tail would misrepresent the endpoint of the ride.
 */
object TrackSmoother {

    /** Default minimum displacement in metres a point must exceed to be kept. */
    const val MIN_POINT_MOVE_M = 3.0

    /**
     * Returns a smoothed copy of [points] using a minimum-move gate.
     *
     * Algorithm:
     * 1. Always keep `points[0]` (no predecessor to compare).
     * 2. For each subsequent point, compute its haversine distance from the last **kept** point.
     *    Keep the point only when that distance exceeds [minMoveM].
     * 3. Always append `points.last()` if it was not already kept in step 2 — the tail is
     *    **never truncated** regardless of distance.
     *
     * Special cases: empty → empty; single-element → same single-element list.
     * All fields of kept [TrackPoint]s (lat, lng, ele, t) are preserved unchanged.
     *
     * @param points  Ordered GPS track points to smooth.
     * @param minMoveM Minimum haversine distance in metres a point must exceed relative to the
     *                 last kept point to be retained. Defaults to [MIN_POINT_MOVE_M] (3.0 m).
     * @return Smoothed list; first and last elements are identical to the originals.
     */
    fun smooth(points: List<TrackPoint>, minMoveM: Double = MIN_POINT_MOVE_M): List<TrackPoint> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return points

        val kept = mutableListOf(points[0])
        for (i in 1 until points.size) {
            val pt = points[i]
            val last = kept.last()
            if (haversineMeters(last.lat, last.lng, pt.lat, pt.lng) > minMoveM) {
                kept.add(pt)
            }
        }
        // Guarantee the final original point is always present.
        if (kept.last() !== points.last()) {
            kept.add(points.last())
        }
        return kept
    }

    /** Haversine great-circle distance between two WGS-84 coordinates, in metres. */
    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }
}
