package com.mototracker.domain.fuel

/**
 * One full-tank refuel event placed on a cumulative odometer.
 *
 * Every G5 refuel event is treated as a full-tank fill, matching the
 * tank-capacity-prefilled G5 model (the rider tops back up after each ride).
 *
 * @param odometerKm Cumulative odometer reading at the time of the refuel (km).
 * @param litres     Volume of fuel added in litres.
 */
data class FuelFill(
    val odometerKm: Double,
    val litres: Double,
)

/**
 * Computes real-world fuel consumption from the per-route refuel ledger.
 *
 * All functions are pure and Android-free; testable with plain JUnit without Robolectric.
 */
object FuelConsumptionCalculator {

    /**
     * Computes average fuel consumption in L/100 km using the tank-to-tank method.
     *
     * Algorithm: sort [fills] by [FuelFill.odometerKm]; `distance = last.odometerKm − first.odometerKm`;
     * `litresConsumed = Σ litres of all fills after the first` (fuel added to top back up = fuel burned
     * since the previous fill); `result = litresConsumed / distance × 100`.
     *
     * Falls back to [configuredLper100km] when:
     * - [fills] has fewer than 2 entries (insufficient ledger history)
     * - computed distance ≤ 0 or non-finite
     * - computed litresConsumed ≤ 0
     *
     * Returns `null` when both the ledger and [configuredLper100km] are unusable
     * (null, ≤ 0, or non-finite). Never divides by zero.
     *
     * @param fills               Refuel history; may be in any order (sorted internally).
     * @param configuredLper100km Bike-settings fallback; null when not configured.
     * @return Average consumption in L/100 km, or null if undeterminable.
     */
    fun consumptionLper100km(
        fills: List<FuelFill>,
        configuredLper100km: Double?,
    ): Double? {
        fun fallback(): Double? = configuredLper100km?.takeIf { it > 0 && it.isFinite() }

        if (fills.size < 2) return fallback()

        val sorted = fills.sortedBy { it.odometerKm }
        val distance = sorted.last().odometerKm - sorted.first().odometerKm
        val litresConsumed = sorted.drop(1).sumOf { it.litres }

        if (distance <= 0 || !distance.isFinite() || litresConsumed <= 0) return fallback()

        return litresConsumed / distance * 100.0
    }

    /**
     * Builds an odometer-ordered [FuelFill] list from a bike's chronological route history.
     *
     * Iterates [routeData] in the supplied order, accumulates each route's distance into a
     * running odometer, and places each route's refuel events at that route's ending odometer.
     * This is a documented approximation: refuels are logged at ride end, not mid-ride.
     *
     * An empty or single-fill result yields fewer than 2 fills; callers should fall back
     * via [consumptionLper100km].
     *
     * @param routeData Chronologically ordered `(distanceKm, refuels)` pairs, one per route.
     * @return All refuel events on a cumulative odometer, in chronological route order.
     */
    fun fillsFromLedger(
        routeData: List<Pair<Double, List<RefuelEvent>>>,
    ): List<FuelFill> {
        var odometerKm = 0.0
        val fills = mutableListOf<FuelFill>()
        for ((distanceKm, refuels) in routeData) {
            odometerKm += distanceKm
            for (refuel in refuels) {
                fills.add(FuelFill(odometerKm = odometerKm, litres = refuel.litres))
            }
        }
        return fills
    }
}
