package com.mototracker.domain.stats

import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.RouteSummaryModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelCostCalculatorTest {

    private fun summary(
        id: String = "r1",
        fuel: Double,
        fuelPricePerL: Double? = null,
        bikeId: String? = null,
    ): RouteSummaryModel = RouteSummaryModel(
        id = id,
        name = "Route $id",
        dateEpochMs = 0L,
        bikeId = bikeId,
        km = 100.0,
        durSec = 3600L,
        avg = 60.0,
        max = 100.0,
        lean = 10.0,
        elev = 100.0,
        fuel = fuel,
        synced = false,
        thumbnailPathD = null,
        correctionStatus = CorrectionStatus.NONE,
        confidence = null,
        fuelPricePerL = fuelPricePerL,
    )

    @Test
    fun `total fuel is sum of all route fuel values`() {
        val summaries = listOf(
            summary(id = "r1", fuel = 5.0),
            summary(id = "r2", fuel = 3.0),
            summary(id = "r3", fuel = 7.5),
        )
        val result = FuelCostCalculator.compute(summaries, emptyMap())
        assertEquals(15.5, result.totalFuelL, 0.001)
    }

    @Test
    fun `total fuel is zero for empty list`() {
        val result = FuelCostCalculator.compute(emptyList(), emptyMap())
        assertEquals(0.0, result.totalFuelL, 0.0)
        assertNull(result.totalCost)
    }

    @Test
    fun `per-route price takes precedence over bike fallback`() {
        val summaries = listOf(
            summary(id = "r1", fuel = 10.0, fuelPricePerL = 2.0, bikeId = "b1"),
        )
        val bikePrices = mapOf("b1" to 5.0)
        val result = FuelCostCalculator.compute(summaries, bikePrices)
        // Should use route price 2.0, not bike price 5.0
        assertEquals(20.0, result.totalCost!!, 0.001)
    }

    @Test
    fun `bike price fallback is used when route price is null`() {
        val summaries = listOf(
            summary(id = "r1", fuel = 10.0, fuelPricePerL = null, bikeId = "b1"),
        )
        val bikePrices = mapOf("b1" to 3.0)
        val result = FuelCostCalculator.compute(summaries, bikePrices)
        assertEquals(30.0, result.totalCost!!, 0.001)
    }

    @Test
    fun `total cost is null when no route has a resolvable price`() {
        val summaries = listOf(
            summary(id = "r1", fuel = 5.0, fuelPricePerL = null, bikeId = null),
            summary(id = "r2", fuel = 3.0, fuelPricePerL = null, bikeId = "b1"),
        )
        val bikePrices = mapOf("b1" to null)
        val result = FuelCostCalculator.compute(summaries, bikePrices)
        assertNull(result.totalCost)
    }

    @Test
    fun `mixed routes — only routes with resolvable prices contribute to cost`() {
        val summaries = listOf(
            summary(id = "r1", fuel = 5.0, fuelPricePerL = 2.0, bikeId = null),
            summary(id = "r2", fuel = 3.0, fuelPricePerL = null, bikeId = null), // no price
            summary(id = "r3", fuel = 4.0, fuelPricePerL = null, bikeId = "b1"),
        )
        val bikePrices = mapOf("b1" to 1.5)
        val result = FuelCostCalculator.compute(summaries, bikePrices)
        // r1: 5 * 2.0 = 10.0; r3: 4 * 1.5 = 6.0 → total 16.0
        assertEquals(16.0, result.totalCost!!, 0.001)
        assertEquals(12.0, result.totalFuelL, 0.001)
    }

    @Test
    fun `route without bikeId uses route price only`() {
        val summaries = listOf(
            summary(id = "r1", fuel = 6.0, fuelPricePerL = 1.8, bikeId = null),
        )
        val result = FuelCostCalculator.compute(summaries, emptyMap())
        assertEquals(10.8, result.totalCost!!, 0.001)
    }
}
