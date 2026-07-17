package com.mototracker.domain.recording

/**
 * Splits a list of [TrackPoint] values into continuous GPS segments.
 *
 * A new segment begins whenever two adjacent **timestamped** points are separated by more than
 * [RecordingEngine.GPS_GAP_SEC] seconds. Points whose [TrackPoint.t] is `null` (legacy tracks
 * recorded before per-point timestamps were introduced in N1) never trigger a split — such a
 * route is always returned as a single segment, preserving rendering for existing stored routes.
 *
 * Typical use: feed the result into an OSM map renderer to draw one [org.osmdroid.views.overlay.Polyline]
 * per segment so that a GPS dropout does not produce a false straight line across the gap on the map.
 */
object TrackSegmenter {

    /**
     * Splits [points] into continuous sub-lists, starting a new segment whenever two adjacent
     * points both have a non-null [TrackPoint.t] and the elapsed time between them exceeds [gapSec].
     *
     * Rules:
     * - An empty list returns an empty list.
     * - A single-point list returns one segment containing that point.
     * - When no adjacent pair triggers a split the entire list is returned as a single segment.
     * - Adjacent points where either [TrackPoint.t] is `null` are never split (legacy route compat).
     *
     * @param points Ordered GPS track points to segment.
     * @param gapSec Elapsed-seconds threshold above which a new segment begins.
     *               Defaults to [RecordingEngine.GPS_GAP_SEC] (20 s).
     * @return List of non-empty segments; each segment is a contiguous sub-list of [points].
     */
    fun split(
        points: List<TrackPoint>,
        gapSec: Double = RecordingEngine.GPS_GAP_SEC,
    ): List<List<TrackPoint>> {
        if (points.isEmpty()) return emptyList()

        val segments = mutableListOf<List<TrackPoint>>()
        var current = mutableListOf(points[0])

        for (i in 1 until points.size) {
            val prevT = points[i - 1].t
            val currT = points[i].t
            val elapsedSec = if (prevT != null && currT != null) {
                (currT - prevT) / 1000.0
            } else {
                0.0 // null-t points never cause a split
            }
            if (elapsedSec > gapSec) {
                segments += current
                current = mutableListOf(points[i])
            } else {
                current += points[i]
            }
        }
        segments += current
        return segments
    }
}
