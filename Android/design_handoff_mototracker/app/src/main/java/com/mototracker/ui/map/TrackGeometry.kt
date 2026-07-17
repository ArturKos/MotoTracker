package com.mototracker.ui.map

import com.mototracker.domain.recording.TrackPoint
import org.json.JSONArray

/**
 * A geographic coordinate expressed as latitude and longitude in decimal degrees.
 *
 * @param lat Latitude (−90 to +90).
 * @param lon Longitude (−180 to +180).
 */
data class GeoCoord(val lat: Double, val lon: Double)

/**
 * Axis-aligned geographic bounding box with padding applied.
 *
 * @param north Northern (maximum) latitude boundary.
 * @param south Southern (minimum) latitude boundary.
 * @param east  Eastern (maximum) longitude boundary.
 * @param west  Western (minimum) longitude boundary.
 */
data class MapBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)

/**
 * Pure-JVM geometry utilities for GPS track data.
 *
 * No Android or osmdroid imports — all functions are unit-testable without a device.
 */
object TrackGeometry {

    private const val PADDING_FACTOR = 0.10
    private const val SINGLE_POINT_SPAN = 0.005

    /**
     * Parses a JSON path string in `[{"lat":…,"lng":…}]` format into an ordered
     * list of [GeoCoord].
     *
     * @param pathJson Serialised JSON array of `{lat, lng}` objects, or null.
     * @return Ordered list of coordinates; empty when [pathJson] is null, blank, or malformed.
     */
    fun parsePathJson(pathJson: String?): List<GeoCoord> {
        if (pathJson.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(pathJson)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                GeoCoord(lat = obj.getDouble("lat"), lon = obj.getDouble("lng"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Parses a JSON path string into an ordered list of [TrackPoint], preserving all
     * available fields (lat, lng, ele, t).
     *
     * Tolerant of legacy JSON (`[{"lat":…,"lng":…}]`) — missing `ele` defaults to 0.0 and
     * missing/null `t` defaults to null, so old persisted tracks decode without error.
     *
     * @param pathJson Serialised JSON array of point objects, or null.
     * @return Ordered list of [TrackPoint]; empty when [pathJson] is null, blank, or malformed.
     */
    fun parsePathJsonFull(pathJson: String?): List<TrackPoint> {
        if (pathJson.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(pathJson)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                TrackPoint(
                    lat = obj.getDouble("lat"),
                    lng = obj.getDouble("lng"),
                    ele = obj.optDouble("ele", 0.0),
                    t = if (obj.has("t") && !obj.isNull("t")) obj.getLong("t") else null,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Computes a padded bounding box for [points].
     *
     * Returns `null` when [points] is empty. A single-point list produces a
     * non-degenerate box (span = [SINGLE_POINT_SPAN] degrees) so the map camera
     * has a meaningful region to display. Multi-point boxes include a 10% padding
     * on each side so the track never touches the viewport edge.
     *
     * @param points Ordered GPS track coordinates.
     * @return Padded [MapBounds], or `null` if [points] is empty.
     */
    fun bounds(points: List<GeoCoord>): MapBounds? {
        if (points.isEmpty()) return null

        var minLat = points[0].lat
        var maxLat = points[0].lat
        var minLon = points[0].lon
        var maxLon = points[0].lon

        for (p in points) {
            if (p.lat < minLat) minLat = p.lat
            if (p.lat > maxLat) maxLat = p.lat
            if (p.lon < minLon) minLon = p.lon
            if (p.lon > maxLon) maxLon = p.lon
        }

        val latSpan = if (maxLat - minLat == 0.0) SINGLE_POINT_SPAN else maxLat - minLat
        val lonSpan = if (maxLon - minLon == 0.0) SINGLE_POINT_SPAN else maxLon - minLon

        val latPad = latSpan * PADDING_FACTOR
        val lonPad = lonSpan * PADDING_FACTOR

        return MapBounds(
            north = maxLat + latPad,
            south = minLat - latPad,
            east = maxLon + lonPad,
            west = minLon - lonPad,
        )
    }

    /**
     * Returns the first point in [points], or `null` when [points] is empty.
     *
     * @param points Ordered GPS track coordinates.
     */
    fun startPoint(points: List<GeoCoord>): GeoCoord? = points.firstOrNull()

    /**
     * Returns the last point in [points], or `null` when [points] is empty.
     *
     * @param points Ordered GPS track coordinates.
     */
    fun endPoint(points: List<GeoCoord>): GeoCoord? = points.lastOrNull()
}
