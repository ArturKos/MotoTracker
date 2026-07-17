package com.mototracker.core.format

import com.mototracker.data.model.Route
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Pure serializer that converts a [Route] into a GPX 1.1 XML document string.
 *
 * No Android context is required — this object is fully testable on the JVM.
 *
 * The `pathJson` format expected is `[{"lat": …, "lng": …}, …]` (same as [RouteThumbnail]).
 * Null, blank, or malformed `pathJson` still produces a well-formed GPX document with an empty
 * `<trkseg>`. This object never throws.
 */
object GpxExporter {

    private val ISO_8601: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC)

    /**
     * Converts [route] into a valid GPX 1.1 XML document string.
     *
     * The `<trkseg>` contains one `<trkpt lat=… lon=…/>` per coordinate parsed from
     * [Route.pathJson]. If `pathJson` is null, blank, or malformed the segment is empty
     * but the document remains well-formed. This function never throws.
     *
     * @param route The route to serialise.
     * @return A well-formed GPX 1.1 XML string.
     */
    fun toGpx(route: Route): String {
        val safeName = escapeXml(route.name.ifBlank { route.id })
        val time = ISO_8601.format(Instant.ofEpochMilli(route.dateEpochMs))
        val points = parsePoints(route.pathJson)

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<gpx version="1.1" creator="MotoTracker" xmlns="http://www.topografix.com/GPX/1/1">""")
            appendLine("  <metadata>")
            appendLine("    <name>$safeName</name>")
            appendLine("    <time>$time</time>")
            appendLine("  </metadata>")
            appendLine("  <trk>")
            appendLine("    <name>$safeName</name>")
            appendLine("    <trkseg>")
            points.forEach { pt ->
                val hasChildren = pt.ele != 0.0 || pt.t != null
                if (hasChildren) {
                    appendLine("""      <trkpt lat="${pt.lat}" lon="${pt.lng}">""")
                    if (pt.ele != 0.0) appendLine("""        <ele>${pt.ele}</ele>""")
                    if (pt.t != null) appendLine("""        <time>${ISO_8601.format(Instant.ofEpochMilli(pt.t))}</time>""")
                    appendLine("""      </trkpt>""")
                } else {
                    appendLine("""      <trkpt lat="${pt.lat}" lon="${pt.lng}"/>""")
                }
            }
            appendLine("    </trkseg>")
            appendLine("  </trk>")
            append("</gpx>")
        }
    }

    /**
     * Returns a safe filename for the GPX export, e.g. `"alpine-tour-abc12345.gpx"`.
     *
     * The route name is slugified: lowercased, non-alphanumeric runs replaced with `-`,
     * leading/trailing dashes stripped. Falls back to the route ID alone when the name
     * is blank after slugification.
     *
     * @param route The route whose filename should be produced.
     * @return A filename string ending in `.gpx`.
     */
    fun fileName(route: Route): String {
        val slug = route.name
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        val base = if (slug.isEmpty()) route.id else "${slug}-${route.id.take(8)}"
        return "$base.gpx"
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Internal representation of a parsed GPX track point, including optional elevation and timestamp. */
    private data class GpxPoint(val lat: Double, val lng: Double, val ele: Double, val t: Long?)

    /**
     * Parses [pathJson] into an ordered list of [GpxPoint].
     *
     * Tolerant of legacy JSON without `ele`/`t` fields — missing `ele` defaults to 0.0 and
     * missing/null `t` defaults to null. Never throws.
     */
    private fun parsePoints(pathJson: String?): List<GpxPoint> {
        if (pathJson.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(pathJson)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                GpxPoint(
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

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
