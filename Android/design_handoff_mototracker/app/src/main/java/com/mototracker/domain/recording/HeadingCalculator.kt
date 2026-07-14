package com.mototracker.domain.recording

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure, Android-free utility that computes a tilt-compensated magnetic heading.
 *
 * The algorithm replicates what `android.hardware.SensorManager.getRotationMatrix` +
 * `getOrientation` do internally, expressed entirely as cross-product / dot-product
 * arithmetic so it runs on the JVM without any Android dependency.
 *
 * Coordinate convention: the gravity vector is in the sensor (device) frame, as
 * reported by `TYPE_GRAVITY` or `TYPE_ACCELEROMETER`.  The geomagnetic vector is in
 * the same sensor frame, as reported by `TYPE_MAGNETIC_FIELD`.
 *
 * Reference axes for "North" and "East" are the horizontal projections that eliminate
 * the phone's pitch and roll, producing a heading that equals the GPS bearing when the
 * device is held flat and aligns with the magnetic North when tilted.
 */
object HeadingCalculator {

    private const val MIN_NORM = 1e-6f

    /**
     * Computes the tilt-compensated magnetic azimuth from raw sensor vectors.
     *
     * Steps:
     * 1. East  = normalize(geomagnetic × gravity)  — horizontal East direction
     * 2. Down  = normalize(gravity)                 — down reference
     * 3. North = normalize(Down × East)             — horizontal North direction
     * 4. azimuth = atan2(East.y, North.y)           — bearing of device +Y from North
     *
     * @param gravity     3-element gravity vector in the sensor frame (m/s²), from
     *                    `TYPE_GRAVITY` (preferred) or `TYPE_ACCELEROMETER`.
     * @param geomagnetic 3-element geomagnetic field vector in the sensor frame (µT),
     *                    from `TYPE_MAGNETIC_FIELD`.
     * @return Azimuth in degrees in the range [0, 360), where 0 = North, 90 = East,
     *         180 = South, 270 = West; or `null` when the input is degenerate (vectors
     *         are nearly collinear or either has near-zero magnitude).
     */
    fun azimuthDegrees(gravity: FloatArray, geomagnetic: FloatArray): Float? {
        // Step 1 — East = normalize(geomagnetic × gravity)
        val ex = geomagnetic[1] * gravity[2] - geomagnetic[2] * gravity[1]
        val ey = geomagnetic[2] * gravity[0] - geomagnetic[0] * gravity[2]
        val ez = geomagnetic[0] * gravity[1] - geomagnetic[1] * gravity[0]
        val eNorm = sqrt(ex * ex + ey * ey + ez * ez)
        if (eNorm < MIN_NORM) return null  // vectors collinear / one is zero
        val eNx = ex / eNorm
        val eNy = ey / eNorm
        val eNz = ez / eNorm

        // Step 2 — Down = normalize(gravity)
        val gNorm = sqrt(
            gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2],
        )
        if (gNorm < MIN_NORM) return null
        val ax = gravity[0] / gNorm
        val ay = gravity[1] / gNorm
        val az = gravity[2] / gNorm

        // Step 3 — North = normalize(Down × East)
        val nx = ay * eNz - az * eNy
        val ny = az * eNx - ax * eNz
        val nz = ax * eNy - ay * eNx
        val nNorm = sqrt(nx * nx + ny * ny + nz * nz)
        if (nNorm < MIN_NORM) return null
        val nNy = ny / nNorm

        // Step 4 — azimuth = atan2(East.y, North.y), normalised to [0, 360)
        val azimuthDeg = Math.toDegrees(atan2(eNy.toDouble(), nNy.toDouble())).toFloat()
        return (azimuthDeg + 360f) % 360f
    }
}
