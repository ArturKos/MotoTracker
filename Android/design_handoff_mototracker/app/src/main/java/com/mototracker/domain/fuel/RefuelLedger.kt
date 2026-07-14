package com.mototracker.domain.fuel

/**
 * Pure, Android-free ledger calculations for a list of [RefuelEvent]s.
 *
 * All functions are stateless and have no Android dependencies, making them fully
 * unit-testable with plain JUnit without Robolectric.
 */
object RefuelLedger {

    /**
     * Computes the fuel cost for a single refuel event.
     *
     * @param e The refuel event.
     * @return [RefuelEvent.litres] × [RefuelEvent.pricePerL].
     */
    fun costOf(e: RefuelEvent): Double = e.litres * e.pricePerL

    /**
     * Sums the litres across all [events].
     *
     * Returns 0.0 for an empty list.
     *
     * @param events The list of refuel events to aggregate.
     */
    fun totalLitres(events: List<RefuelEvent>): Double = events.sumOf { it.litres }

    /**
     * Sums the cost ([costOf]) across all [events].
     *
     * Returns 0.0 for an empty list.
     *
     * @param events The list of refuel events to aggregate.
     */
    fun totalCost(events: List<RefuelEvent>): Double = events.sumOf { costOf(it) }
}
