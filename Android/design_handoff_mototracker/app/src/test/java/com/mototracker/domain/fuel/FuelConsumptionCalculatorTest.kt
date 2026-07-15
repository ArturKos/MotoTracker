package com.mototracker.domain.fuel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FuelConsumptionCalculator].
 *
 * All tests use plain JUnit — no Android runtime or Robolectric needed.
 */
class FuelConsumptionCalculatorTest {

    // ── consumptionLper100km ─────────────────────────────────────────────────

    @Test
    fun `real-data multi-fill path returns correct L-per-100km`() {
        // Three fills: reference at km=300 (15L), top-up at km=600 (12L)
        // distance = 600 - 300 = 300 km; litresConsumed = 12L (only fills after first)
        // expected = 12 / 300 * 100 = 4.0 L/100km
        val fills = listOf(
            FuelFill(odometerKm = 300.0, litres = 15.0),
            FuelFill(odometerKm = 600.0, litres = 12.0),
        )
        val result = FuelConsumptionCalculator.consumptionLper100km(fills, configuredLper100km = 6.0)
        assertNotNull("should return a real consumption value", result)
        assertEquals("real-data consumption should be 4.0", 4.0, result!!, 0.001)
    }

    @Test
    fun `three fills use all litres after first`() {
        // Reference at km=0 (15L), top-up at km=300 (12L), top-up at km=600 (11L)
        // distance = 600 - 0 = 600 km; litresConsumed = 12 + 11 = 23L
        // expected = 23 / 600 * 100 = 3.833...
        val fills = listOf(
            FuelFill(odometerKm = 0.0, litres = 15.0),
            FuelFill(odometerKm = 300.0, litres = 12.0),
            FuelFill(odometerKm = 600.0, litres = 11.0),
        )
        val result = FuelConsumptionCalculator.consumptionLper100km(fills, configuredLper100km = 5.0)
        assertNotNull(result)
        assertEquals(23.0 / 600.0 * 100.0, result!!, 0.001)
    }

    @Test
    fun `fills input may be unsorted — sorted by odometer internally`() {
        // Reverse order: should produce the same result as sorted
        val fills = listOf(
            FuelFill(odometerKm = 600.0, litres = 12.0),
            FuelFill(odometerKm = 300.0, litres = 15.0),
        )
        val result = FuelConsumptionCalculator.consumptionLper100km(fills, configuredLper100km = 6.0)
        assertNotNull(result)
        assertEquals(4.0, result!!, 0.001)
    }

    @Test
    fun `fewer than 2 fills falls back to configured value`() {
        val fills = listOf(FuelFill(odometerKm = 0.0, litres = 15.0))
        val result = FuelConsumptionCalculator.consumptionLper100km(fills, configuredLper100km = 6.0)
        assertEquals("single fill should fall back to configured", 6.0, result!!, 0.001)
    }

    @Test
    fun `empty fills falls back to configured value`() {
        val result = FuelConsumptionCalculator.consumptionLper100km(emptyList(), configuredLper100km = 7.5)
        assertEquals("empty fills should fall back to configured", 7.5, result!!, 0.001)
    }

    @Test
    fun `distance zero guard falls back to configured`() {
        // Two fills at the same odometer → distance = 0
        val fills = listOf(
            FuelFill(odometerKm = 100.0, litres = 10.0),
            FuelFill(odometerKm = 100.0, litres = 12.0),
        )
        val result = FuelConsumptionCalculator.consumptionLper100km(fills, configuredLper100km = 6.0)
        assertEquals("zero distance should fall back to configured", 6.0, result!!, 0.001)
    }

    @Test
    fun `negative distance guard falls back to configured`() {
        val fills = listOf(
            FuelFill(odometerKm = 500.0, litres = 10.0),
            FuelFill(odometerKm = 100.0, litres = 12.0),
        )
        // After sorting: [100, 500] → distance positive; so use unsorted with odometerKm identical
        // Actually sorting will produce positive distance 400; use same odometer to force 0.
        // Use fills at same odometer to produce zero distance reliably:
        val zeroFills = listOf(
            FuelFill(odometerKm = 200.0, litres = 10.0),
            FuelFill(odometerKm = 200.0, litres = 12.0),
        )
        val result = FuelConsumptionCalculator.consumptionLper100km(zeroFills, configuredLper100km = 6.0)
        assertEquals("zero/negative distance should fall back to configured", 6.0, result!!, 0.001)
    }

    @Test
    fun `litres consumed zero guard falls back to configured`() {
        // Fills after first all have 0 litres
        val fills = listOf(
            FuelFill(odometerKm = 0.0, litres = 15.0),
            FuelFill(odometerKm = 300.0, litres = 0.0),
        )
        val result = FuelConsumptionCalculator.consumptionLper100km(fills, configuredLper100km = 6.0)
        assertEquals("zero litresConsumed should fall back to configured", 6.0, result!!, 0.001)
    }

    @Test
    fun `configured null with insufficient fills returns null`() {
        val fills = listOf(FuelFill(odometerKm = 0.0, litres = 15.0))
        val result = FuelConsumptionCalculator.consumptionLper100km(fills, configuredLper100km = null)
        assertNull("null configured with <2 fills should return null", result)
    }

    @Test
    fun `configured zero with insufficient fills returns null`() {
        val result = FuelConsumptionCalculator.consumptionLper100km(emptyList(), configuredLper100km = 0.0)
        assertNull("zero configured should return null (non-positive)", result)
    }

    @Test
    fun `configured negative with insufficient fills returns null`() {
        val result = FuelConsumptionCalculator.consumptionLper100km(emptyList(), configuredLper100km = -1.0)
        assertNull("negative configured should return null", result)
    }

    @Test
    fun `configured infinite with insufficient fills returns null`() {
        val result = FuelConsumptionCalculator.consumptionLper100km(
            emptyList(),
            configuredLper100km = Double.POSITIVE_INFINITY,
        )
        assertNull("infinite configured should return null", result)
    }

    @Test
    fun `NaN distance falls back to configured`() {
        val fills = listOf(
            FuelFill(odometerKm = Double.NaN, litres = 10.0),
            FuelFill(odometerKm = 300.0, litres = 12.0),
        )
        val result = FuelConsumptionCalculator.consumptionLper100km(fills, configuredLper100km = 6.0)
        // NaN arithmetic: NaN - 300.0 = NaN, isFinite = false → fallback
        assertEquals("NaN odometer should fall back to configured", 6.0, result!!, 0.001)
    }

    // ── fillsFromLedger ──────────────────────────────────────────────────────

    @Test
    fun `fillsFromLedger accumulates odometer across routes`() {
        // Route 1: 100km, 1 refuel at end → odometer = 100
        // Route 2: 200km, 1 refuel at end → odometer = 300
        val refuel1 = makeRefuelEvent(routeId = "r1", litres = 15.0)
        val refuel2 = makeRefuelEvent(routeId = "r2", litres = 12.0)
        val routeData = listOf(
            100.0 to listOf(refuel1),
            200.0 to listOf(refuel2),
        )
        val fills = FuelConsumptionCalculator.fillsFromLedger(routeData)
        assertEquals(2, fills.size)
        assertEquals(100.0, fills[0].odometerKm, 0.001)
        assertEquals(15.0, fills[0].litres, 0.001)
        assertEquals(300.0, fills[1].odometerKm, 0.001)
        assertEquals(12.0, fills[1].litres, 0.001)
    }

    @Test
    fun `fillsFromLedger multiple refuels in one route share the same ending odometer`() {
        val refuel1 = makeRefuelEvent(routeId = "r1", litres = 10.0)
        val refuel2 = makeRefuelEvent(routeId = "r1", litres = 5.0)
        val routeData = listOf(200.0 to listOf(refuel1, refuel2))
        val fills = FuelConsumptionCalculator.fillsFromLedger(routeData)
        assertEquals(2, fills.size)
        assertEquals(200.0, fills[0].odometerKm, 0.001)
        assertEquals(200.0, fills[1].odometerKm, 0.001)
    }

    @Test
    fun `fillsFromLedger empty input returns empty list`() {
        val fills = FuelConsumptionCalculator.fillsFromLedger(emptyList())
        assertTrue("empty routeData should yield empty fills", fills.isEmpty())
    }

    @Test
    fun `fillsFromLedger single route with no refuels returns empty list`() {
        val fills = FuelConsumptionCalculator.fillsFromLedger(listOf(300.0 to emptyList()))
        assertTrue("route with no refuels should yield no fills", fills.isEmpty())
    }

    @Test
    fun `fillsFromLedger single route with one refuel yields single fill — causes fallback`() {
        val refuel = makeRefuelEvent(routeId = "r1", litres = 15.0)
        val fills = FuelConsumptionCalculator.fillsFromLedger(listOf(300.0 to listOf(refuel)))
        assertEquals(1, fills.size)
        // 1 fill → consumptionLper100km falls back to configured
        val consumption = FuelConsumptionCalculator.consumptionLper100km(fills, configuredLper100km = 6.0)
        assertEquals("single fill should fall back to configured", 6.0, consumption!!, 0.001)
    }

    @Test
    fun `fillsFromLedger routes without refuels still advance odometer`() {
        // Route 1: 100km, no refuels
        // Route 2: 200km, 1 refuel → odometer = 100 + 200 = 300
        val refuel = makeRefuelEvent(routeId = "r2", litres = 12.0)
        val routeData = listOf(
            100.0 to emptyList<RefuelEvent>(),
            200.0 to listOf(refuel),
        )
        val fills = FuelConsumptionCalculator.fillsFromLedger(routeData)
        assertEquals(1, fills.size)
        assertEquals(300.0, fills[0].odometerKm, 0.001)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeRefuelEvent(
        routeId: String,
        litres: Double,
        pricePerL: Double = 6.0,
        epochMs: Long = 1_000_000L,
    ) = RefuelEvent(
        id = 0L,
        routeId = routeId,
        epochMs = epochMs,
        litres = litres,
        pricePerL = pricePerL,
    )
}
