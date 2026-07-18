package com.mototracker.domain.recording

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Splits a list of [TrackPoint] values into continuous GPS segments.
 *
 * A new segment begins whenever two adjacent **timestamped** points are separated by more than
 * [RecordingEngine.GPS_GAP_SEC] seconds **and** the haversine distance between them exceeds
 * [RecordingEngine.REACQUIRE_DIST_M] metres. Requiring both conditions ensures a stationary
 * pause (large time gap, small distance) is never misclassified as a GPS dropout.
 *
 * Points whose [TrackPoint.t] is `null` (legacy tracks recorded before per-point timestamps
 * were introduced in N1) never trigger a split — such a route is always returned as a single
 * segment, preserving rendering for existing stored routes.
 *
 * Typical use: feed the result into an OSM map renderer to draw one
 * [org.osmdroid.views.overlay.Polyline] per segment so that a GPS dropout does not produce a
 * false straight line across the gap on the map.
 */
object TrackSegmenter {

    /**
     * Splits [points] into continuous sub-lists, starting a new segment whenever two adjacent
     * points both have a non-null [TrackPoint.t], the elapsed time between them exceeds [gapSec],
     * **and** the haversine distance between them exceeds [reacquireDistM] metres.
     *
     * Rules:
     * - An empty list returns an empty list.
     * - A single-point list returns one segment containing that point.
     * - When no adjacent pair triggers a split the entire list is returned as a single segment.
     * - Adjacent points where either [TrackPoint.t] is `null` are never split (legacy route compat).
     * - A pair that meets only one condition (time OR distance, not both) is NOT split — this
     *   prevents a stationary pause from being misclassified as a GPS dropout.
     *
     * @param points Ordered GPS track points to segment.
     * @param gapSec Elapsed-seconds threshold above which the time condition is met.
     *               Defaults to [RecordingEngine.GPS_GAP_SEC] (20 s).
     * @param reacquireDistM Haversine-distance threshold in metres above which the distance
     *                       condition is met. Defaults to [RecordingEngine.REACQUIRE_DIST_M] (60 m).
     * @return List of non-empty segments; each segment is a contiguous sub-list of [points].
     */
    fun split(
        points: List<TrackPoint>,
        gapSec: Double = RecordingEngine.GPS_GAP_SEC,
        reacquireDistM: Double = RecordingEngine.REACQUIRE_DIST_M,
    ): List<List<TrackPoint>> {
        if (points.isEmpty()) return emptyList()

        val segments = mutableListOf<List<TrackPoint>>()
        var current = mutableListOf(points[0])

        for (i in 1 until points.size) {
            val prevPt = points[i - 1]
            val currPt = points[i]
            val prevT = prevPt.t
            val currT = currPt.t
            val isSplit = if (prevT != null && currT != null) {
                val elapsedSec = (currT - prevT) / 1000.0
                val distM = haversineMeters(prevPt.lat, prevPt.lng, currPt.lat, currPt.lng)
                elapsedSec > gapSec && distM > reacquireDistM
            } else {
                false // null-t points never cause a split
            }
            if (isSplit) {
                segments += current
                current = mutableListOf(currPt)
            } else {
                current += currPt
            }
        }
        segments += current
        return segments
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
