package com.mototracker.domain.stats

import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.RouteSummaryModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BikeStatsCalculatorTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun bike(
        id: String = "bike-1",
        fuelPricePerL: Double? = null,
    ) = Bike(
        id = id,
        name = "Yamaha MT-07",
        year = 2021,
        plate = "WA 12345",
        status = BikeStatus.ACTIVE,
        fuelPricePerL = fuelPricePerL,
    )

    private fun summary(
        id: String = "r1",
        bikeId: String? = "bike-1",
        km: Double = 100.0,
        durSec: Long = 3600L,
        max: Double = 120.0,
        fuel: Double = 5.0,
    ) = RouteSummaryModel(
        id = id,
        name = "Route $id",
        dateEpochMs = 1_700_000_000_000L,
        bikeId = bikeId,
        km = km,
        durSec = durSec,
        avg = km / (durSec / 3600.0),
        max = max,
        lean = 15.0,
        elev = 100.0,
        fuel = fuel,
        synced = false,
        thumbnailPathD = null,
        correctionStatus = CorrectionStatus.NONE,
        confidence = null,
    )

    // ── Empty input ───────────────────────────────────────────────────────────

    @Test
    fun `empty summaries returns zeroed BikeStats`() {
        val result = BikeStatsCalculator.compute(emptyList(), bike())
        assertEquals(0, result.rideCount)
        assertEquals(0.0, result.totalDistanceKm, 0.0)
        assertEquals(0L, result.totalElapsedSec)
        assertEquals(0.0, result.totalFuelL, 0.0)
        assertNull(result.totalCostOrNull)
        assertEquals(0.0, result.longestRideKm, 0.0)
        assertEquals(0.0, result.topSpeedKmh, 0.0)
        assertTrue(result.routeIds.isEmpty())
    }

    // ── bikeId filtering ─────────────────────────────────────────────────────

    @Test
    fun `summaries not matching bike id are excluded`() {
        val summaries = listOf(
            summary(id = "r1", bikeId = "bike-1", km = 100.0),
            summary(id = "r2", bikeId = "bike-2", km = 200.0),
            summary(id = "r3", bikeId = null,     km = 300.0),
        )
        val result = BikeStatsCalculator.compute(summaries, bike(id = "bike-1"))
        assertEquals(1, result.rideCount)
        assertEquals(100.0, result.totalDistanceKm, 0.001)
        assertEquals(listOf("r1"), result.routeIds)
    }

    @Test
    fun `returns zeroed stats when no summaries match bike`() {
        val summaries = listOf(
            summary(id = "r1", bikeId = "other-bike"),
        )
        val result = BikeStatsCalculator.compute(summaries, bike(id = "bike-1"))
        assertEquals(0, result.rideCount)
        assertTrue(result.routeIds.isEmpty())
    }

    // ── Totals ────────────────────────────────────────────────────────────────

    @Test
    fun `totals are summed correctly across multiple routes`() {
        val summaries = listOf(
            summary(id = "r1", km = 100.0, durSec = 3600L, fuel = 5.0),
            summary(id = "r2", km = 200.0, durSec = 7200L, fuel = 10.0),
        )
        val result = BikeStatsCalculator.compute(summaries, bike())
        assertEquals(2, result.rideCount)
        assertEquals(300.0, result.totalDistanceKm, 0.001)
        assertEquals(10800L, result.totalElapsedSec)
        assertEquals(15.0, result.totalFuelL, 0.001)
    }

    // ── Cost present and absent ───────────────────────────────────────────────

    @Test
    fun `total cost is null when bike has no fuel price`() {
        val result = BikeStatsCalculator.compute(
            listOf(summary(fuel = 10.0)),
            bike(fuelPricePerL = null),
        )
        assertNull(result.totalCostOrNull)
    }

    @Test
    fun `total cost is computed using bike default price`() {
        val result = BikeStatsCalculator.compute(
            listOf(
                summary(id = "r1", fuel = 5.0),
                summary(id = "r2", fuel = 5.0),
            ),
            bike(fuelPricePerL = 6.0),
        )
        // 10.0 L × 6.0 PLN/L = 60.0
        assertEquals(60.0, result.totalCostOrNull!!, 0.001)
    }

    // ── Longest ride ──────────────────────────────────────────────────────────

    @Test
    fun `longest ride is the max km among matching routes`() {
        val summaries = listOf(
            summary(id = "r1", km = 50.0),
            summary(id = "r2", km = 200.0),
            summary(id = "r3", km = 120.0),
        )
        val result = BikeStatsCalculator.compute(summaries, bike())
        assertEquals(200.0, result.longestRideKm, 0.001)
    }

    // ── Top speed ─────────────────────────────────────────────────────────────

    @Test
    fun `top speed is the max max-speed among matching routes`() {
        val summaries = listOf(
            summary(id = "r1", max = 100.0),
            summary(id = "r2", max = 180.0),
            summary(id = "r3", max = 140.0),
        )
        val result = BikeStatsCalculator.compute(summaries, bike())
        assertEquals(180.0, result.topSpeedKmh, 0.001)
    }

    // ── routeIds order ────────────────────────────────────────────────────────

    @Test
    fun `routeIds preserves input list order`() {
        val summaries = listOf(
            summary(id = "r3"),
            summary(id = "r1"),
            summary(id = "r2"),
        )
        val result = BikeStatsCalculator.compute(summaries, bike())
        assertEquals(listOf("r3", "r1", "r2"), result.routeIds)
    }

    // ── Single route ──────────────────────────────────────────────────────────

    @Test
    fun `single matching route produces correct stats`() {
        val summaries = listOf(
            summary(id = "r1", km = 50.0, durSec = 1800L, max = 90.0, fuel = 2.5),
        )
        val result = BikeStatsCalculator.compute(summaries, bike(fuelPricePerL = 8.0))
        assertEquals(1, result.rideCount)
        assertEquals(50.0, result.totalDistanceKm, 0.001)
        assertEquals(1800L, result.totalElapsedSec)
        assertEquals(2.5, result.totalFuelL, 0.001)
        assertEquals(20.0, result.totalCostOrNull!!, 0.001)
        assertEquals(50.0, result.longestRideKm, 0.001)
        assertEquals(90.0, result.topSpeedKmh, 0.001)
        assertEquals(listOf("r1"), result.routeIds)
    }
}
