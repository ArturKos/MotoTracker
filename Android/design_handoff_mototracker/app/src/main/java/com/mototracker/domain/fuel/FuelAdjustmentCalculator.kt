package com.mototracker.domain.fuel

/**
 * Pure, Android-free calculator for fuel-level anchor corrections.
 *
 * Converts a [FuelAdjustmentMode] + value pair into the new anchor litres,
 * clamped to [0, tankCapacityL]. Non-finite inputs are guarded and return the
 * unchanged [currentRemainingL].
 */
object FuelAdjustmentCalculator {

    /**
     * Computes the new anchor litres after a rider correction.
     *
     * @param mode             Whether the correction is absolute or a signed delta.
     * @param value            The correction value in litres.
     * @param currentRemainingL Current estimated remaining fuel in litres.
     * @param tankCapacityL    Configured tank capacity in litres (used as the upper clamp bound).
     * @return New anchor litres clamped to [0.0, [tankCapacityL]].
     */
    fun newAnchorLitres(
        mode: FuelAdjustmentMode,
        value: Double,
        currentRemainingL: Double,
        tankCapacityL: Double,
    ): Double {
        if (!value.isFinite() || !currentRemainingL.isFinite() || !tankCapacityL.isFinite()) {
            return currentRemainingL.coerceIn(0.0, tankCapacityL)
        }
        val raw = when (mode) {
            FuelAdjustmentMode.SET_ABSOLUTE -> value
            FuelAdjustmentMode.DELTA -> currentRemainingL + value
        }
        return raw.coerceIn(0.0, tankCapacityL)
    }
}
