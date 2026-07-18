package com.mototracker.core.format

import com.mototracker.data.model.Route
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.atan2

/**
 * Pure serializer that converts a [Route] into a Garmin Training Center Database v2 (TCX) XML
 * document string.
 *
 * No Android context is required — this object is fully testable on the JVM.
 *
 * The `pathJson` format expected is `[{"lat": …, "lng": …, "ele": …, "t": …}]`
 * (same shape as [GpxExporter]). Missing `ele` defaults to 0.0; missing/null `t` triggers
 * timestamp synthesis (see [synthesizeTimestamps] for details). Null, blank, or malformed
 * `pathJson` still produces a well-formed TCX document with an empty `<Track>`. This object
 * never throws.
 *
 * FIT (Flexible and Interoperable Data Transfer) export is deferred — a clean binary FIT
 * encoder would require the official Garmin FIT SDK or a full binary codec; shipping a
 * half-finished encoder would produce invalid files. TCX covers the immediate need. Track
 * this as a follow-up: TODO(Q3): implement FIT export using the Garmin FIT SDK.
 */
object TcxExporter {

    private val ISO_8601: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC)

    /**
     * Converts [route] into a valid Garmin Training Center Database v2 (TCX) XML document string.
     *
     * Structure:
     * ```
     * <TrainingCenterDatabase>
     *   <Activities>
     *     <Activity Sport="Other">
     *       <Id>{ISO-8601 UTC of route.dateEpochMs}</Id>
     *       <Lap StartTime="…">
     *         <TotalTimeSeconds>…</TotalTimeSeconds>
     *         <DistanceMeters>…</DistanceMeters>
     *         <MaximumSpeed>…</MaximumSpeed>    (km/h → m/s)
     *         <Intensity>Active</Intensity>
     *         <TriggerMethod>Manual</TriggerMethod>
     *         <Track>
     *           <Trackpoint> … </Trackpoint>
     *         </Track>
     *       </Lap>
     *       <Notes>…</Notes>    (route name; placed after Lap per TCX Activity_t XSD)
     *     </Activity>
     *   </Activities>
     * </TrainingCenterDatabase>
     * ```
     *
     * Each `<Trackpoint>` contains:
     * - `<Time>` — always present; synthesized when source points lack a `t` field
     *   (see [synthesizeTimestamps]).
     * - `<Position>` with `<LatitudeDegrees>` and `<LongitudeDegrees>`.
     * - `<AltitudeMeters>` — present only when `ele != 0.0`.
     * - `<DistanceMeters>` — cumulative haversine distance from the first point (monotonic ≥ 0).
     *
     * @param route The route to serialise.
     * @return A well-formed TCX XML string.
     */
    fun toTcx(route: Route): String {
        val startTime = ISO_8601.format(Instant.ofEpochMilli(route.dateEpochMs))
        val safeName = escapeXml(route.name.ifBlank { route.id })
        val points = parsePoints(route.pathJson)
        val timestamps = synthesizeTimestamps(points, route.dateEpochMs, route.durSec)
        val cumulativeDistances = computeCumulativeDistances(points)

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">""")
            appendLine("  <Activities>")
            appendLine("""    <Activity Sport="Other">""")
            appendLine("      <Id>$startTime</Id>")
            appendLine("""      <Lap StartTime="$startTime">""")
            appendLine("        <TotalTimeSeconds>${route.durSec}</TotalTimeSeconds>")
            appendLine("        <DistanceMeters>${String.format(Locale.US, "%.1f", route.km * 1000.0)}</DistanceMeters>")
            appendLine("        <MaximumSpeed>${String.format(Locale.US, "%.4f", route.max / 3.6)}</MaximumSpeed>")
            appendLine("        <Intensity>Active</Intensity>")
            appendLine("        <TriggerMethod>Manual</TriggerMethod>")
            appendLine("        <Track>")
            points.forEachIndexed { i, pt ->
                val tStr = ISO_8601.format(Instant.ofEpochMilli(timestamps[i]))
                val cumDist = cumulativeDistances[i]
                appendLine("          <Trackpoint>")
                appendLine("            <Time>$tStr</Time>")
                appendLine("            <Position>")
                appendLine("              <LatitudeDegrees>${pt.lat}</LatitudeDegrees>")
                appendLine("              <LongitudeDegrees>${pt.lng}</LongitudeDegrees>")
                appendLine("            </Position>")
                if (pt.ele != 0.0) {
                    appendLine("            <AltitudeMeters>${pt.ele}</AltitudeMeters>")
                }
                appendLine("            <DistanceMeters>${String.format(Locale.US, "%.1f", cumDist)}</DistanceMeters>")
                appendLine("          </Trackpoint>")
            }
            appendLine("        </Track>")
            appendLine("      </Lap>")
            // Notes must follow Lap(s) per the TCX Activity_t XSD element ordering
            if (safeName.isNotBlank()) appendLine("      <Notes>$safeName</Notes>")
            appendLine("    </Activity>")
            appendLine("  </Activities>")
            append("</TrainingCenterDatabase>")
        }
    }

    /**
     * Returns a safe filename for the TCX export, e.g. `"alpine-tour-abc12345.tcx"`.
     *
     * Uses the same slugification logic as [GpxExporter.fileName] but with a `.tcx` extension.
     *
     * @param route The route whose filename should be produced.
     * @return A filename string ending in `.tcx`.
     */
    fun fileName(route: Route): String {
        val slug = route.name
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        val base = if (slug.isEmpty()) route.id else "${slug}-${route.id.take(8)}"
        return "$base.tcx"
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private data class TcxPoint(val lat: Double, val lng: Double, val ele: Double, val t: Long?)

    /**
     * Parses [pathJson] into an ordered list of [TcxPoint].
     *
     * Tolerant of legacy JSON without `ele`/`t` fields — missing `ele` defaults to 0.0 and
     * missing/null `t` defaults to null. Never throws.
     */
    private fun parsePoints(pathJson: String?): List<TcxPoint> {
        if (pathJson.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(pathJson)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                TcxPoint(
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
     * Returns timestamps for each point in [points].
     *
     * If a point has a non-null `t`, that value is used directly. If any point lacks a `t`
     * (legacy routes recorded before the per-point timestamp field was added in N1), all N
     * timestamps are synthesized by evenly distributing the route's time window
     * `[dateEpochMs, dateEpochMs + durSec*1000]` across the N points. This ensures every
     * `<Trackpoint>` satisfies the required `<Time>` element in the TCX schema, keeping
     * legacy routes valid.
     *
     * When [points] is empty, an empty list is returned.
     */
    private fun synthesizeTimestamps(points: List<TcxPoint>, dateEpochMs: Long, durSec: Long): List<Long> {
        if (points.isEmpty()) return emptyList()
        val allHaveTimestamps = points.all { it.t != null }
        return if (allHaveTimestamps) {
            points.map { it.t!! }
        } else {
            val n = points.size
            val endMs = dateEpochMs + durSec * 1000L
            if (n == 1) {
                listOf(dateEpochMs)
            } else {
                List(n) { i -> dateEpochMs + (endMs - dateEpochMs) * i / (n - 1) }
            }
        }
    }

    /**
     * Computes cumulative haversine distances in metres from the first point.
     *
     * The result is monotonically non-decreasing. Index 0 is always 0.0.
     * Returns an empty list when [points] is empty.
     */
    private fun computeCumulativeDistances(points: List<TcxPoint>): List<Double> {
        if (points.isEmpty()) return emptyList()
        val result = MutableList(points.size) { 0.0 }
        for (i in 1 until points.size) {
            result[i] = result[i - 1] + haversineMeters(
                lat1 = points[i - 1].lat, lng1 = points[i - 1].lng,
                lat2 = points[i].lat,     lng2 = points[i].lng,
            )
        }
        return result
    }

    /**
     * Haversine great-circle distance between two WGS-84 coordinates, in metres.
     */
    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLam = Math.toRadians(lng2 - lng1)
        val a = sin(dPhi / 2).let { it * it } + cos(phi1) * cos(phi2) * sin(dLam / 2).let { it * it }
        return r * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
