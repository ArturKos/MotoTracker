package com.mototracker.core.format

import java.util.Locale

/**
 * Stateless utilities for copying GPS coordinates to the system clipboard.
 *
 * All functions are pure (no Android framework imports) so the entire object is
 * unit-testable on the JVM without a device or emulator.
 */
object CoordinateClipboard {

    private const val LABEL_DD  = "DD"
    private const val LABEL_DMS = "DMS"
    private const val LABEL_UTM = "UTM"

    /**
     * Builds a multi-line clipboard string showing [lat]/[lng] in all three
     * supported coordinate formats, each on its own line with a short label prefix.
     *
     * Format:
     * ```
     * DD  <decimal degrees>
     * DMS <degrees minutes seconds>
     * UTM <zone easting northing>
     * ```
     *
     * @param lat WGS-84 latitude in decimal degrees (−90..90).
     * @param lng WGS-84 longitude in decimal degrees (−180..180).
     * @return Three-line string; each line starts with the format abbreviation followed by a space.
     */
    fun clipboardText(lat: Double, lng: Double): String {
        val dd  = CoordinateFormatter.format(lat, lng, CoordFormat.DECIMAL_DEGREES)
        val dms = CoordinateFormatter.format(lat, lng, CoordFormat.DMS)
        val utm = CoordinateFormatter.format(lat, lng, CoordFormat.UTM)
        return "$LABEL_DD $dd\n$LABEL_DMS $dms\n$LABEL_UTM $utm"
    }

    /**
     * Builds a Google Maps search URL for the given coordinates.
     *
     * The decimal separator is always `'.'` regardless of the device locale,
     * because the URL must be machine-parseable.
     *
     * @param lat WGS-84 latitude in decimal degrees.
     * @param lng WGS-84 longitude in decimal degrees.
     * @return A `https://www.google.com/maps/search/` URL string.
     */
    fun mapsUrl(lat: Double, lng: Double): String {
        val latStr = String.format(Locale.US, "%.6f", lat)
        val lngStr = String.format(Locale.US, "%.6f", lng)
        return "https://www.google.com/maps/search/?api=1&query=$latStr,$lngStr"
    }
}
