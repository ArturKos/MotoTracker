package com.mototracker.domain.fuel

/**
 * Colour band for the fuel-range indicator on the Recording screen.
 *
 * - [GREEN]   — Plenty of fuel; fraction > 50 % and range ≥ 100 km.
 * - [YELLOW]  — Moderate; fraction 20–50 % (inclusive) and range ≥ 100 km.
 * - [RED]     — Low; fraction < 20 % OR absolute range < 100 km (small-tank override).
 * - [UNKNOWN] — Cannot determine; tank or consumption data missing or invalid.
 */
enum class FuelRangeColor { GREEN, YELLOW, RED, UNKNOWN }

/**
 * Pure domain object that maps (remainingFraction, remainingRangeKm) → [FuelRangeColor].
 *
 * ### Colour bands
 * | Condition                                   | Result  |
 * |---------------------------------------------|---------|
 * | Either input null / NaN / infinite           | UNKNOWN |
 * | remainingRangeKm < 100.0                    | RED     |
 * | remainingFraction < 0.20                    | RED     |
 * | remainingFraction ≤ 0.50                    | YELLOW  |
 * | remainingFraction > 0.50                    | GREEN   |
 *
 * The 100 km absolute reserve check is evaluated before the fraction check so
 * small-tank bikes (e.g. fraction 0.6 but only 80 km range) are shown RED.
 * When [RecordingEngine] sets remainingRangeKm = null (consumption ≤ 0), the
 * null path returns UNKNOWN automatically — no special casing needed here.
 */
object FuelRangeIndicator {

    /**
     * Returns the [FuelRangeColor] for the given fuel state.
     *
     * @param remainingFraction Fuel remaining as a fraction of tank capacity (0.0–1.0);
     *                          null when tank capacity is not configured.
     * @param remainingRangeKm  Estimated remaining range in kilometres;
     *                          null when consumption is not configured.
     */
    fun colorFor(remainingFraction: Double?, remainingRangeKm: Double?): FuelRangeColor {
        if (remainingFraction == null || remainingRangeKm == null) return FuelRangeColor.UNKNOWN
        if (!remainingFraction.isFinite() || !remainingRangeKm.isFinite()) return FuelRangeColor.UNKNOWN
        if (remainingRangeKm < 100.0) return FuelRangeColor.RED
        if (remainingFraction < 0.20) return FuelRangeColor.RED
        if (remainingFraction <= 0.50) return FuelRangeColor.YELLOW
        return FuelRangeColor.GREEN
    }
}
