package com.mototracker.domain.fuel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelCostCalculatorTest {

    // ── cost() ────────────────────────────────────────────────────────────────

    @Test
    fun `cost returns fuel times price`() {
        assertEquals(31.20, FuelCostCalculator.cost(fuelL = 12.0, pricePerL = 2.60), 0.001)
    }

    @Test
    fun `cost with zero fuel returns zero`() {
        assertEquals(0.0, FuelCostCalculator.cost(fuelL = 0.0, pricePerL = 2.0), 0.0)
    }

    @Test
    fun `cost with zero price returns zero`() {
        assertEquals(0.0, FuelCostCalculator.cost(fuelL = 10.0, pricePerL = 0.0), 0.0)
    }

    @Test
    fun `cost accumulates correctly for typical ride`() {
        // 50 km * 6 L/100km = 3 L; 3 L * 5.80 PLN = 17.40
        assertEquals(17.40, FuelCostCalculator.cost(fuelL = 3.0, pricePerL = 5.80), 0.001)
    }

    // ── effectivePricePerL() ──────────────────────────────────────────────────

    @Test
    fun `effectivePricePerL prefers route override over bike default`() {
        val result = FuelCostCalculator.effectivePricePerL(routePrice = 3.0, bikePrice = 2.0)
        assertEquals(3.0, result!!, 0.0)
    }

    @Test
    fun `effectivePricePerL falls back to bike price when route is null`() {
        val result = FuelCostCalculator.effectivePricePerL(routePrice = null, bikePrice = 2.5)
        assertEquals(2.5, result!!, 0.0)
    }

    @Test
    fun `effectivePricePerL returns null when both are null`() {
        assertNull(FuelCostCalculator.effectivePricePerL(routePrice = null, bikePrice = null))
    }

    @Test
    fun `effectivePricePerL returns route price when bike price is null`() {
        val result = FuelCostCalculator.effectivePricePerL(routePrice = 4.0, bikePrice = null)
        assertEquals(4.0, result!!, 0.0)
    }
}
