package com.mototracker.domain.fuel

/**
 * Determines how a [FuelAdjustmentEvent] re-anchors the remaining-fuel baseline.
 *
 * - [SET_ABSOLUTE]: the value is the new absolute remaining-fuel level in litres.
 * - [DELTA]: the value is a signed offset applied to the current remaining-fuel level.
 */
enum class FuelAdjustmentMode {
    /** Override the remaining fuel to an explicit absolute level (litres). */
    SET_ABSOLUTE,

    /** Shift the remaining fuel by a signed delta (positive = add, negative = remove). */
    DELTA,
}
