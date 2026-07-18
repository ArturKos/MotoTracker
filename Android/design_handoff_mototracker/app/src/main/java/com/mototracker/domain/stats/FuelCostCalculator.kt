package com.mototracker.domain.stats

import com.mototracker.data.model.RouteSummaryModel

/**
 * Calculates fleet-wide fuel consumption and total cost from a list of route summaries.
 *
 * Per-route price resolution: [RouteSummaryModel.fuelPricePerL] takes precedence over the
 * bike's default price ([bikePriceById]). A route contributes to [FuelTotals.totalCost] only
 * when at least one price source resolves to a non-null value; [totalCost] is null when NO
 * route has a resolvable price.
 */
object FuelCostCalculator {

    /**
     * Computes total fuel consumed and total fuel cost across all [summaries].
     *
     * @param summaries    Lightweight route projections; only [RouteSummaryModel.fuel],
     *                     [RouteSummaryModel.fuelPricePerL], and [RouteSummaryModel.bikeId]
     *                     are consumed.
     * @param bikePriceById Map of bikeId → fuelPricePerL for the fallback price lookup.
     *                      Entries with a null value are treated as "no price configured".
     * @return [FuelTotals] with the aggregated result.
     */
    fun compute(
        summaries: List<RouteSummaryModel>,
        bikePriceById: Map<String, Double?>,
    ): FuelTotals {
        val totalFuelL = summaries.sumOf { it.fuel }

        var costSum = 0.0
        var hasAnyCost = false

        for (route in summaries) {
            val price = route.fuelPricePerL ?: route.bikeId?.let { bikePriceById[it] }
            if (price != null) {
                costSum += route.fuel * price
                hasAnyCost = true
            }
        }

        return FuelTotals(
            totalFuelL = totalFuelL,
            totalCost = if (hasAnyCost) costSum else null,
        )
    }
}

/**
 * Result of [FuelCostCalculator.compute].
 *
 * @param totalFuelL Total fuel consumed in litres across all routes.
 * @param totalCost  Total fuel cost; null when no route has a resolvable price.
 */
data class FuelTotals(
    val totalFuelL: Double,
    val totalCost: Double?,
)
