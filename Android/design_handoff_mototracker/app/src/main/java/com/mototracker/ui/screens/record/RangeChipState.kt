package com.mototracker.ui.screens.record

import kotlin.math.roundToInt

/**
 * Describes the display state of the driving-range chip in the Recording screen chip row.
 *
 * Use [rangeChipState] to derive an instance from the raw [remainingRangeKm] value
 * produced by the fuel model.
 */
sealed class RangeChipState {
    /** Range cannot be computed yet — no tank size, consumption, or refuel data available. */
    object Estimating : RangeChipState()

    /**
     * Range is available and can be shown.
     *
     * @param km Remaining range rounded to the nearest whole kilometre.
     */
    data class Value(val km: Int) : RangeChipState()
}

/**
 * Derives the [RangeChipState] from a raw remaining-range value.
 *
 * Returns [RangeChipState.Estimating] when [remainingRangeKm] is null, NaN, infinite, or
 * negative; returns [RangeChipState.Value] with a rounded km integer otherwise.
 *
 * This function is pure and Android-free — it is safe to call from JVM unit tests.
 *
 * @param remainingRangeKm Raw remaining-range kilometres from [RecordingMetrics], or null.
 * @return The chip display state.
 */
fun rangeChipState(remainingRangeKm: Double?): RangeChipState {
    if (remainingRangeKm == null) return RangeChipState.Estimating
    if (!remainingRangeKm.isFinite()) return RangeChipState.Estimating
    if (remainingRangeKm < 0.0) return RangeChipState.Estimating
    return RangeChipState.Value(km = remainingRangeKm.roundToInt())
}
