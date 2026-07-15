package com.mototracker.core.format

import org.json.JSONArray
import kotlin.math.roundToInt

/**
 * Utility for generating an SVG path `d` string from a route's JSON coordinate list.
 *
 * The path is intended for an SVG thumbnail rendered inside a 320×200 viewBox.
 */
object RouteThumbnail {

    private const val VIEW_W = 320.0
    private const val VIEW_H = 200.0
    private const val PADDING = 12.0

    /**
     * Parses a JSON coordinate array and returns an SVG path `d` string suitable
     * for a `<path d="…">` element in a 320×200 viewBox.
     *
     * The coordinate array format is `[{"lat": …, "lng": …}, …]`.
     * Longitude is mapped linearly to the x-axis; latitude is mapped to the y-axis
     * with the direction inverted so that north (higher lat) is at the top.
     *
     * Returns an empty string when [pathJson] is null, blank, malformed, or contains
     * fewer than 2 points — all of which make a path meaningless.
     *
     * @param pathJson Serialised JSON array of `{lat, lng}` objects, or null.
     * @return SVG path `d` string like `"M 12 45 L 30 60 L …"`, or `""`.
     */
    fun buildPathD(pathJson: String?): String {
        if (pathJson.isNullOrBlank()) return ""

        val points = try {
            val arr = JSONArray(pathJson)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                obj.getDouble("lat") to obj.getDouble("lng")
            }
        } catch (_: Exception) {
            return ""
        }

        return buildPathDFromPoints(points)
    }

    /**
     * Parses an SVG path `d` string as emitted by [buildPathDFromPoints] into an ordered list
     * of (x, y) pixel coordinates in the 320×200 viewBox.
     *
     * Understands the `M x y L x y …` format only (no curves, no relative coordinates).
     * Returns an empty list when [d] is blank, malformed, or contains fewer than 2 points —
     * all of which make a route polyline meaningless.
     *
     * @param d SVG path `d` string, e.g. `"M 12 45 L 30 60 L …"`, or blank.
     * @return Ordered list of (x, y) float pairs, or `emptyList()`.
     */
    fun parsePathD(d: String): List<Pair<Float, Float>> {
        if (d.isBlank()) return emptyList()
        return try {
            val tokens = d.trim().split(Regex("\\s+"))
            val points = mutableListOf<Pair<Float, Float>>()
            var i = 0
            while (i < tokens.size) {
                when (tokens[i]) {
                    "M", "L" -> {
                        if (i + 2 >= tokens.size) break
                        points.add(tokens[i + 1].toFloat() to tokens[i + 2].toFloat())
                        i += 3
                    }
                    else -> i++ // skip unrecognized token defensively
                }
            }
            if (points.size < 2) emptyList() else points
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Builds an SVG path `d` string directly from a pre-parsed list of (lat, lng) pairs.
     *
     * Prefer [buildPathD] when starting from raw JSON. Use this overload when the list is
     * already available (e.g. after downsampling via [TraceDownsampler]) to avoid double-parsing.
     *
     * @param points Ordered list of (latitude, longitude) pairs.
     * @return SVG path `d` string, or `""` when [points] has fewer than 2 entries.
     */
    fun buildPathDFromPoints(points: List<Pair<Double, Double>>): String {
        if (points.size < 2) return ""

        val minLat = points.minOf { it.first }
        val maxLat = points.maxOf { it.first }
        val minLng = points.minOf { it.second }
        val maxLng = points.maxOf { it.second }

        val latRange = maxLat - minLat
        val lngRange = maxLng - minLng

        val drawW = VIEW_W - 2 * PADDING
        val drawH = VIEW_H - 2 * PADDING

        fun project(lat: Double, lng: Double): Pair<Int, Int> {
            val x = if (lngRange == 0.0) PADDING + drawW / 2
                    else PADDING + (lng - minLng) / lngRange * drawW
            val y = if (latRange == 0.0) PADDING + drawH / 2
                    else PADDING + (maxLat - lat) / latRange * drawH
            return x.roundToInt() to y.roundToInt()
        }

        return buildString {
            points.forEachIndexed { idx, (lat, lng) ->
                val (x, y) = project(lat, lng)
                if (idx == 0) append("M $x $y") else append(" L $x $y")
            }
        }
    }
}
