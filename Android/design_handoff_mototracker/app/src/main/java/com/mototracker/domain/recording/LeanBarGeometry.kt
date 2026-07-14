package com.mototracker.domain.recording

/**
 * Pure geometry helpers for the lean-angle tilt bar.
 *
 * All functions are free of Android dependencies and fully unit-testable.
 *
 * Sign convention (matches [LeanAngleCalculator]): positive degrees = lean RIGHT,
 * negative degrees = lean LEFT.
 */
object LeanBarGeometry {

    /**
     * Normalises a signed lean angle to a fill fraction in [-1.0, 1.0].
     *
     * Negative values represent a lean to the left; positive values represent a lean to
     * the right. The result is clamped to [-1, 1] when [deg] exceeds [maxScaleDeg].
     *
     * @param deg         Signed lean angle in degrees (+right, -left).
     * @param maxScaleDeg Full-scale degrees; must be > 0.
     * @return Fill fraction in [-1.0, 1.0]; negative = left, positive = right.
     */
    fun fillFraction(deg: Double, maxScaleDeg: Double): Double {
        require(maxScaleDeg > 0.0) { "maxScaleDeg must be > 0" }
        return (deg / maxScaleDeg).coerceIn(-1.0, 1.0)
    }

    /**
     * Returns the bar-fraction positions (0.0–1.0 from the left edge) for the max-left
     * and max-right ghost markers.
     *
     * The center of the bar is 0.5. Left markers are below 0.5; right markers are above.
     *
     * @param leftMagnitudeDeg  Non-negative max-left magnitude in degrees.
     * @param rightMagnitudeDeg Non-negative max-right magnitude in degrees.
     * @param maxScaleDeg       Full-scale degrees; must be > 0.
     * @return Pair of (leftMarkerFraction, rightMarkerFraction) each in [0.0, 1.0].
     */
    fun markerFractions(
        leftMagnitudeDeg: Double,
        rightMagnitudeDeg: Double,
        maxScaleDeg: Double,
    ): Pair<Double, Double> {
        require(maxScaleDeg > 0.0) { "maxScaleDeg must be > 0" }
        val leftFrac = 0.5 - (leftMagnitudeDeg / maxScaleDeg).coerceIn(0.0, 1.0) * 0.5
        val rightFrac = 0.5 + (rightMagnitudeDeg / maxScaleDeg).coerceIn(0.0, 1.0) * 0.5
        return leftFrac to rightFrac
    }
}
