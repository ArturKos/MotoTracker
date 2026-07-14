package com.mototracker.domain.fuel

/**
 * Pure, Android-free calculator for fuel cost estimates.
 *
 * All functions are stateless and have no Android dependencies, making them fully
 * unit-testable with plain JUnit.
 */
object FuelCostCalculator {

    /**
     * Computes the total fuel cost for a route.
     *
     * @param fuelL     Fuel consumed in litres.
     * @param pricePerL Price per litre in the user's chosen currency.
     * @return Total cost (fuelL × pricePerL); non-negative when both inputs are non-negative.
     */
    fun cost(fuelL: Double, pricePerL: Double): Double = fuelL * pricePerL

    /**
     * Resolves the effective fuel price from a per-route override and a bike default.
     *
     * The route override takes precedence; falls back to the bike price when the route
     * price is null. Returns null when both are null (price is unknown).
     *
     * @param routePrice  Per-route price override; null means "no override set".
     * @param bikePrice   The bike's default fuel price; null means "not configured".
     * @return The effective price per litre, or null when no price information is available.
     */
    fun effectivePricePerL(routePrice: Double?, bikePrice: Double?): Double? =
        routePrice ?: bikePrice
}
