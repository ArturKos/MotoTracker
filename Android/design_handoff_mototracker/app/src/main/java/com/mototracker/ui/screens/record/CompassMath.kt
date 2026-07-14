package com.mototracker.ui.screens.record

/**
 * Pure heading-math utilities for the analog compass dial.
 *
 * All functions are stateless and free of Android or Compose dependencies so they
 * can be unit-tested directly on the JVM without a device or Robolectric.
 */
object CompassMath {

    /**
     * Normalises [deg] into the half-open interval [0f, 360f).
     *
     * Handles negatives, values ≥ 360, and [Float.NaN] (mapped to 0f).
     *
     * Examples:
     * - `-90`  → `270`
     * - `370`  → `10`
     * - `360`  → `0`
     * - `NaN`  → `0`
     */
    fun normalizeHeading(deg: Float): Float {
        if (deg.isNaN()) return 0f
        return ((deg % 360f) + 360f) % 360f
    }

    /**
     * 8-point compass rose, one point every 45°.
     *
     * N covers [337.5, 360) ∪ [0, 22.5); each subsequent point covers a 45° sector
     * centred on its nominal bearing.
     */
    enum class Cardinal { N, NE, E, SE, S, SW, W, NW }

    /**
     * Returns the 8-point [Cardinal] direction for [deg].
     *
     * Uses [normalizeHeading] internally so any raw GPS bearing (including negatives)
     * is accepted without pre-processing.
     */
    fun cardinal(deg: Float): Cardinal {
        val h = normalizeHeading(deg)
        return when {
            h < 22.5f  -> Cardinal.N
            h < 67.5f  -> Cardinal.NE
            h < 112.5f -> Cardinal.E
            h < 157.5f -> Cardinal.SE
            h < 202.5f -> Cardinal.S
            h < 247.5f -> Cardinal.SW
            h < 292.5f -> Cardinal.W
            h < 337.5f -> Cardinal.NW
            else       -> Cardinal.N  // [337.5, 360) wraps back to N
        }
    }
}
