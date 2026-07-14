package com.mototracker.domain.stats

import com.mototracker.data.model.Bike
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.domain.fuel.FuelCostCalculator

/**
 * Aggregate ride statistics for a single motorcycle across all routes assigned to it.
 *
 * @param rideCount         Number of routes assigned to this bike.
 * @param totalDistanceKm   Sum of all route distances in kilometres.
 * @param totalElapsedSec   Sum of all route durations in seconds (elapsed time; see [BikeStatsCalculator]).
 * @param totalFuelL        Sum of estimated fuel consumed in litres.
 * @param totalCostOrNull   Aggregate fuel cost computed as `totalFuelL × bike.fuelPricePerL`; null
 *                          when the bike has no configured fuel price. Note: per-route price
 *                          overrides are intentionally NOT re-summed here — they are surfaced
 *                          only on the Route detail screen. This figure always uses the bike's
 *                          default price applied to the [totalFuelL] total.
 * @param longestRideKm     Distance of the longest single route; 0.0 when no routes match.
 * @param topSpeedKmh       Highest maximum speed recorded across all routes; 0.0 when no routes match.
 * @param routeIds          IDs of all matching routes in input-list order.
 */
data class BikeStats(
    val rideCount: Int,
    val totalDistanceKm: Double,
    val totalElapsedSec: Long,
    val totalFuelL: Double,
    val totalCostOrNull: Double?,
    val longestRideKm: Double,
    val topSpeedKmh: Double,
    val routeIds: List<String>,
)

/**
 * Stateless calculator that derives [BikeStats] for a single [Bike] from all available
 * [RouteSummaryModel]s.
 *
 * Implemented as a Kotlin [object] — no mutable state, no Android dependencies —
 * mirroring [PersonalRecordsCalculator].
 */
object BikeStatsCalculator {

    /**
     * Computes aggregate [BikeStats] for [bike] by filtering [summaries] to those whose
     * [RouteSummaryModel.bikeId] matches [Bike.id].
     *
     * Returns a zeroed [BikeStats] (rideCount 0, all 0.0/0L, totalCostOrNull null,
     * empty routeIds) when no summaries match [bike].
     *
     * Cost note: [BikeStats.totalCostOrNull] is computed as
     * `FuelCostCalculator.cost(totalFuelL, bike.fuelPricePerL!!)` using the **bike's**
     * configured default price. Per-route price overrides are surfaced only on the
     * Route detail screen and are intentionally excluded here.
     *
     * @param summaries All route summaries in any order.
     * @param bike      The motorcycle to compute stats for.
     * @return Aggregate statistics for [bike].
     */
    fun compute(summaries: List<RouteSummaryModel>, bike: Bike): BikeStats {
        val matching = summaries.filter { it.bikeId == bike.id }
        if (matching.isEmpty()) {
            return BikeStats(
                rideCount = 0,
                totalDistanceKm = 0.0,
                totalElapsedSec = 0L,
                totalFuelL = 0.0,
                totalCostOrNull = null,
                longestRideKm = 0.0,
                topSpeedKmh = 0.0,
                routeIds = emptyList(),
            )
        }
        val totalFuelL = matching.sumOf { it.fuel }
        val totalCostOrNull = bike.fuelPricePerL?.let { price ->
            FuelCostCalculator.cost(totalFuelL, price)
        }
        return BikeStats(
            rideCount = matching.size,
            totalDistanceKm = matching.sumOf { it.km },
            totalElapsedSec = matching.sumOf { it.durSec },
            totalFuelL = totalFuelL,
            totalCostOrNull = totalCostOrNull,
            longestRideKm = matching.maxOf { it.km },
            topSpeedKmh = matching.maxOf { it.max },
            routeIds = matching.map { it.id },
        )
    }
}
