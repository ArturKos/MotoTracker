package com.mototracker.domain.recording

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure, Android-free utility that converts a raw gravity-sensor vector to a lean angle.
 *
 * Assumes the phone is mounted on the handlebar with its Y-axis pointing up along the
 * handlebar tube. The gravity component along the X-axis reflects side-to-side lean.
 *
 * Clamped to ±60° — the practical physical limit for road riding.
 */
object LeanAngleCalculator {

    private const val MAX_LEAN_DEG = 60.0

    /**
     * Computes the motorcycle lean angle from a TYPE_GRAVITY sensor event.
     *
     * @param gravityX  Gravity vector X component (m/s²).
     * @param gravityY  Gravity vector Y component (m/s²).
     * @param gravityZ  Gravity vector Z component (m/s²).
     * @return Lean angle in degrees: 0 when level, positive leaning right, negative leaning left.
     *         Clamped to [−60°, +60°].
     */
    fun leanDegrees(gravityX: Double, gravityY: Double, gravityZ: Double): Double {
        val lean = Math.toDegrees(atan2(gravityX, sqrt(gravityY * gravityY + gravityZ * gravityZ)))
        return lean.coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
    }
}
