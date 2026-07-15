package com.mototracker.domain.fuel

import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RefuelRepository
import com.mototracker.data.repository.RouteRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Refreshes a bike's stored [com.mototracker.data.model.Bike.consumptionLper100km] from
 * its refuel ledger (H4/K2).
 *
 * Only runs when [com.mototracker.data.model.Bike.autoUpdateConsumption] is `true`.
 * No-op otherwise, or when the bike is not found, or when the ledger has fewer than 2
 * fill events (insufficient history for a real computation).
 *
 * Idempotent: skips the persist call when the computed value matches the stored value.
 *
 * @param bikeRepository   Source and sink for motorcycle records.
 * @param routeRepository  Source of route summaries (for per-bike distance history).
 * @param refuelRepository Source of per-route refuel events.
 */
class AutoUpdateBikeConsumptionUseCase @Inject constructor(
    private val bikeRepository: BikeRepository,
    private val routeRepository: RouteRepository,
    private val refuelRepository: RefuelRepository,
) {

    /**
     * Computes the consumption from the ledger for [bikeId] and persists it if the result
     * is a real ledger figure that differs from the currently stored value.
     *
     * @param bikeId UUID of the bike to update.
     */
    suspend fun run(bikeId: String) {
        val bike = bikeRepository.observeAll().first().find { it.id == bikeId } ?: return
        if (!bike.autoUpdateConsumption) return

        val summaries = routeRepository.observeSummaries().first()
            .filter { it.bikeId == bikeId }
            .sortedBy { it.dateEpochMs }

        val allRefuels = refuelRepository.observeAllForBike(bikeId).first()
        val refuelsByRouteId = allRefuels.groupBy { it.routeId }

        val routeData = summaries.map { summary ->
            Pair(summary.km, refuelsByRouteId[summary.id] ?: emptyList())
        }

        val fills = FuelConsumptionCalculator.fillsFromLedger(routeData)
        // null fallback forces the result to be purely ledger-derived (≥2 fills required)
        val ledgerValue = FuelConsumptionCalculator.consumptionLper100km(fills, configuredLper100km = null)

        if (ledgerValue != null && ledgerValue != bike.consumptionLper100km) {
            bikeRepository.addBike(bike.copy(consumptionLper100km = ledgerValue))
        }
    }
}
