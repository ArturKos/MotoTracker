package com.mototracker.core.format

import org.json.JSONArray
import kotlin.math.roundToInt

/**
 * Pure utility for generating SVG-style polyline `points` strings from a route's
 * serialised JSON telemetry arrays.
 *
 * All output is expressed in a **320×90 viewBox**. Callers scale to screen pixels
 * via a Canvas transform.
 */
object ChartPolyline {

    private const val VIEW_W = 320.0
    private const val VIEW_H = 90.0
    private const val PADDING = 8.0

    /**
     * Pre-formatted pair of polyline `points` strings for a single chart series.
     *
     * @param stroke Space-separated `x,y` pairs tracing the data line.
     * @param fill   Stroke points plus two extra corners closing the area down to the
     *               baseline (y = [VIEW_H]), suitable for a translucent fill polygon.
     */
    data class ChartPoints(val stroke: String, val fill: String)

    /**
     * Produces [ChartPoints] for a speed-over-time series.
     *
     * @param speedJson Serialised `[{"t": seconds, "v": kmh}, …]`, or null.
     * @return Polyline strings in a 320×90 viewBox, or `ChartPoints("", "")` when
     *         [speedJson] is null/blank/malformed or contains fewer than 2 data points.
     */
    fun speedPoints(speedJson: String?): ChartPoints {
        if (speedJson.isNullOrBlank()) return empty()
        val pts = parseSpeedJson(speedJson) ?: return empty()
        if (pts.size < 2) return empty()
        return buildPoints(pts)
    }

    /**
     * Produces [ChartPoints] for an elevation-over-distance profile.
     *
     * @param elevProfileJson Serialised `[{"d": km, "a": metres}, …]`, or null.
     * @return Polyline strings in a 320×90 viewBox, or `ChartPoints("", "")` when
     *         [elevProfileJson] is null/blank/malformed or contains fewer than 2 data points.
     */
    fun elevPoints(elevProfileJson: String?): ChartPoints {
        if (elevProfileJson.isNullOrBlank()) return empty()
        val pts = parseElevJson(elevProfileJson) ?: return empty()
        if (pts.size < 2) return empty()
        return buildPoints(pts)
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private fun empty() = ChartPoints("", "")

    private fun parseSpeedJson(json: String): List<Pair<Double, Double>>? = try {
        val arr = JSONArray(json)
        List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            obj.getDouble("t") to obj.getDouble("v")
        }
    } catch (_: Exception) { null }

    private fun parseElevJson(json: String): List<Pair<Double, Double>>? = try {
        val arr = JSONArray(json)
        List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            obj.getDouble("d") to obj.getDouble("a")
        }
    } catch (_: Exception) { null }

    private fun buildPoints(pts: List<Pair<Double, Double>>): ChartPoints {
        val drawW = VIEW_W - 2 * PADDING
        val drawH = VIEW_H - 2 * PADDING

        val minX = pts.minOf { it.first }
        val maxX = pts.maxOf { it.first }
        val minY = pts.minOf { it.second }
        val maxY = pts.maxOf { it.second }

        val xRange = maxX - minX
        val yRange = maxY - minY

        fun projectX(x: Double): Int =
            if (xRange == 0.0) (PADDING + drawW / 2).roundToInt()
            else (PADDING + (x - minX) / xRange * drawW).roundToInt()

        fun projectY(y: Double): Int =
            if (yRange == 0.0) (PADDING + drawH / 2).roundToInt()
            else (PADDING + (maxY - y) / yRange * drawH).roundToInt()

        val stroke = pts.joinToString(" ") { (x, y) -> "${projectX(x)},${projectY(y)}" }

        val firstX = projectX(pts.first().first)
        val lastX = projectX(pts.last().first)
        val baseline = VIEW_H.roundToInt()
        val fill = "$stroke $lastX,$baseline $firstX,$baseline"

        return ChartPoints(stroke, fill)
    }
}
