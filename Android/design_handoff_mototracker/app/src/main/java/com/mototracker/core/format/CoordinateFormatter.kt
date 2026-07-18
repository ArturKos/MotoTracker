package com.mototracker.core.format

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/**
 * Display format for geographic coordinates.
 *
 * @property DECIMAL_DEGREES Decimal degrees, e.g. `51.26059, 15.56916`.
 * @property DMS             Degrees, minutes, seconds with hemisphere, e.g. `51°15'38"N 15°34'09"E`.
 * @property UTM             Universal Transverse Mercator, e.g. `33U 508900 5680230`.
 */
enum class CoordFormat {
    DECIMAL_DEGREES,
    DMS,
    UTM,
}

/**
 * Stateless coordinate formatter.  All conversions are pure WGS-84 math — no Android
 * framework dependencies — so the object is fully unit-testable on the JVM.
 */
object CoordinateFormatter {

    // WGS-84 ellipsoid parameters
    private const val A = 6_378_137.0          // semi-major axis (m)
    private const val F = 1.0 / 298.257_223_563 // flattening
    private const val K0 = 0.9996              // central-scale factor
    private const val FALSE_EASTING = 500_000.0

    private val E2 = 2 * F - F * F            // eccentricity²
    private val E4 = E2 * E2
    private val E6 = E4 * E2
    private val N_RATIO = (1 - Math.sqrt(1 - E2)) / (1 + Math.sqrt(1 - E2))

    // MGRS latitude band letters (C..X, no I, no O), each covers 8° starting at −80°
    private val LAT_BANDS = "CDEFGHJKLMNPQRSTUVWX"

    /**
     * Formats [lat] / [lng] (WGS-84 decimal degrees) according to [format].
     *
     * @param lat Latitude in decimal degrees (−90..90).
     * @param lng Longitude in decimal degrees (−180..180).
     * @param format The desired output format.
     * @return A human-readable coordinate string.
     */
    fun format(lat: Double, lng: Double, format: CoordFormat): String = when (format) {
        CoordFormat.DECIMAL_DEGREES -> formatDD(lat, lng)
        CoordFormat.DMS             -> formatDMS(lat, lng)
        CoordFormat.UTM             -> formatUTM(lat, lng)
    }

    // ── Decimal Degrees ───────────────────────────────────────────────────────

    private fun formatDD(lat: Double, lng: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lng)

    // ── DMS ───────────────────────────────────────────────────────────────────

    private fun formatDMS(lat: Double, lng: Double): String {
        val latStr = dmsComponent(lat, positive = "N", negative = "S")
        val lngStr = dmsComponent(lng, positive = "E", negative = "W")
        return "$latStr $lngStr"
    }

    /**
     * Converts a single decimal-degree value to a DMS string with hemisphere letter.
     * Handles negative values and 60-second / 60-minute carry-over.
     * Seconds are rounded (not truncated) to avoid floating-point under-count.
     */
    private fun dmsComponent(deg: Double, positive: String, negative: String): String {
        val hemisphere = if (deg >= 0) positive else negative
        val absDeg = abs(deg)
        var d = floor(absDeg).toInt()
        val minFrac = (absDeg - d) * 60.0
        var m = floor(minFrac).toInt()
        var s = Math.round((minFrac - m) * 60.0).toInt()
        // carry-over from rounding: seconds → minutes → degrees
        if (s >= 60) { s -= 60; m += 1 }
        if (m >= 60) { m -= 60; d += 1 }
        return String.format(Locale.US, "%d°%02d'%02d\"%s", d, m, s, hemisphere)
    }

    // ── UTM ───────────────────────────────────────────────────────────────────

    /**
     * Converts WGS-84 lat/lng to a UTM string using the standard Transverse Mercator series.
     * Returns e.g. `33U 508900 5680230`.
     */
    private fun formatUTM(lat: Double, lng: Double): String {
        val zone = utmZone(lng)
        val band = utmBand(lat)
        val (easting, northing) = tmProject(lat, lng, zone)
        val falseNorthing = if (lat < 0) 10_000_000.0 else 0.0
        val n = (northing + falseNorthing).toLong()
        val e = easting.toLong()
        return "$zone$band $e $n"
    }

    /** UTM zone number: 1..60, computed from longitude. */
    private fun utmZone(lng: Double): Int {
        val normalized = ((lng + 180.0) % 360.0 + 360.0) % 360.0 // 0..360
        return (normalized / 6.0).toInt() + 1
    }

    /** MGRS latitude band letter for the given latitude. */
    private fun utmBand(lat: Double): Char {
        val clamped = lat.coerceIn(-80.0, 84.0)
        val idx = ((clamped + 80.0) / 8.0).toInt().coerceIn(0, LAT_BANDS.lastIndex)
        return LAT_BANDS[idx]
    }

    /**
     * Transverse Mercator projection (standard UTM series).
     *
     * @param lat  Geodetic latitude (decimal degrees).
     * @param lng  Longitude (decimal degrees).
     * @param zone UTM zone number (used to compute the central meridian).
     * @return Pair(easting, northing) relative to the false origin.
     */
    private fun tmProject(lat: Double, lng: Double, zone: Int): Pair<Double, Double> {
        val phi = Math.toRadians(lat)
        val lambda = Math.toRadians(lng)
        val lambda0 = Math.toRadians((zone - 1) * 6.0 - 180.0 + 3.0) // central meridian

        val sinPhi = Math.sin(phi)
        val cosPhi = Math.cos(phi)
        val tanPhi = Math.tan(phi)

        val N = A / Math.sqrt(1 - E2 * sinPhi * sinPhi)
        val T = tanPhi * tanPhi
        val C = (E2 / (1 - E2)) * cosPhi * cosPhi
        val A_ = cosPhi * (lambda - lambda0)

        // Meridional arc M
        val M = A * (
            (1 - E2 / 4 - 3 * E4 / 64 - 5 * E6 / 256) * phi
            - (3 * E2 / 8 + 3 * E4 / 32 + 45 * E6 / 1024) * Math.sin(2 * phi)
            + (15 * E4 / 256 + 45 * E6 / 1024) * Math.sin(4 * phi)
            - (35 * E6 / 3072) * Math.sin(6 * phi)
        )

        val easting = K0 * N * (
            A_
            + (1 - T + C) * A_ * A_ * A_ / 6
            + (5 - 18 * T + T * T + 72 * C - 58 * (E2 / (1 - E2))) * A_ * A_ * A_ * A_ * A_ / 120
        ) + FALSE_EASTING

        val northing = K0 * (
            M
            + N * tanPhi * (
                A_ * A_ / 2
                + (5 - T + 9 * C + 4 * C * C) * A_ * A_ * A_ * A_ / 24
                + (61 - 58 * T + T * T + 600 * C - 330 * (E2 / (1 - E2))) * A_ * A_ * A_ * A_ * A_ * A_ / 720
            )
        )

        return Pair(easting, northing)
    }
}
