package com.mototracker.core.format

import com.mototracker.ui.state.Units
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure, Android-free formatter for speed, distance, and altitude values.
 *
 * Uses a fixed [Locale.US] for number formatting so results are deterministic
 * in unit tests regardless of the host locale.
 */
object UnitFormatter {

    private const val KM_TO_MI = 0.621371
    private const val M_TO_FT = 3.28084
    private val LOCALE = Locale.US

    /** Returns the speed unit label for [units]. */
    fun speedUnitLabel(units: Units): String = when (units) {
        Units.METRIC -> "km/h"
        Units.IMPERIAL -> "mph"
    }

    /** Returns the distance unit label for [units]. */
    fun distanceUnitLabel(units: Units): String = when (units) {
        Units.METRIC -> "km"
        Units.IMPERIAL -> "mi"
    }

    /** Returns the altitude unit label for [units]. */
    fun altitudeUnitLabel(units: Units): String = when (units) {
        Units.METRIC -> "m"
        Units.IMPERIAL -> "ft"
    }

    /**
     * Formats [kmh] as a rounded integer speed value with its unit label.
     *
     * @param kmh Speed in kilometres per hour.
     * @param units Measurement system preference.
     * @return E.g. `"120 km/h"` or `"75 mph"`.
     */
    fun formatSpeed(kmh: Double, units: Units): String {
        val value = when (units) {
            Units.METRIC -> kmh
            Units.IMPERIAL -> kmh * KM_TO_MI
        }
        return "${value.roundToInt()} ${speedUnitLabel(units)}"
    }

    /**
     * Formats [km] as a one-decimal distance value with its unit label.
     *
     * @param km Distance in kilometres.
     * @param units Measurement system preference.
     * @return E.g. `"128.4 km"` or `"79.8 mi"`.
     */
    fun formatDistance(km: Double, units: Units): String {
        val value = when (units) {
            Units.METRIC -> km
            Units.IMPERIAL -> km * KM_TO_MI
        }
        return String.format(LOCALE, "%.1f %s", value, distanceUnitLabel(units))
    }

    /**
     * Formats [m] as a rounded integer altitude value with its unit label.
     *
     * @param m Altitude in metres above sea level.
     * @param units Measurement system preference.
     * @return E.g. `"1840 m"` or `"6037 ft"`.
     */
    fun formatAltitude(m: Double, units: Units): String {
        val value = when (units) {
            Units.METRIC -> m
            Units.IMPERIAL -> m * M_TO_FT
        }
        return "${value.roundToInt()} ${altitudeUnitLabel(units)}"
    }

    /**
     * Formats a duration in seconds as a compact `h:mm` string for the Stats screen totals.
     *
     * Hours are always shown; minutes are always two digits, e.g. `"11:38"`.
     * Negative values are clamped to zero.
     *
     * @param durSec Duration in seconds (non-negative).
     * @return E.g. `"11:38"` for 41 880 s, `"0:00"` for 0 s.
     */
    fun formatHm(durSec: Long): String {
        val totalSec = if (durSec < 0) 0L else durSec
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        return String.format(LOCALE, "%d:%02d", h, m)
    }

    /**
     * Formats a duration in seconds as a compact `h:mm:ss` string.
     *
     * Hours are omitted when zero; minutes and seconds are always two digits
     * when hours are present, e.g. `"2:07:03"`. When hours are absent the
     * format is `"m:ss"`, e.g. `"7:03"`.
     *
     * @param durSec Duration in seconds (non-negative).
     * @return E.g. `"2:07:33"` for 7653 s, `"7:03"` for 423 s.
     */
    fun formatHms(durSec: Long): String {
        val totalSec = if (durSec < 0) 0L else durSec
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(LOCALE, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(LOCALE, "%d:%02d", m, s)
        }
    }
}
