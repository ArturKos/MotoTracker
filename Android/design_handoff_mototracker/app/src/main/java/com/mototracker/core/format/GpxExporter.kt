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
            points.forEach { (lat, lon) ->
                appendLine("""      <trkpt lat="$lat" lon="$lon"/>""")
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

    private fun parsePoints(pathJson: String?): List<Pair<Double, Double>> {
        if (pathJson.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(pathJson)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                obj.getDouble("lat") to obj.getDouble("lng")
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
