package com.mototracker.domain.fuel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [RefuelLedger] pure calculations.
 *
 * No Android runtime needed — plain JUnit without Robolectric.
 */
class RefuelLedgerTest {

    private fun event(id: Long = 1L, litres: Double, pricePerL: Double) =
        RefuelEvent(id = id, routeId = "r1", epochMs = 0L, litres = litres, pricePerL = pricePerL)

    // ── costOf ────────────────────────────────────────────────────────────────

    @Test
    fun `costOf returns litres times pricePerL`() {
        val e = event(litres = 20.0, pricePerL = 7.50)
        assertEquals(150.0, RefuelLedger.costOf(e), 0.001)
    }

    @Test
    fun `costOf is zero when litres is zero`() {
        val e = event(litres = 0.0, pricePerL = 7.50)
        assertEquals(0.0, RefuelLedger.costOf(e), 0.0)
    }

    @Test
    fun `costOf is zero when pricePerL is zero`() {
        val e = event(litres = 20.0, pricePerL = 0.0)
        assertEquals(0.0, RefuelLedger.costOf(e), 0.0)
    }

    // ── totalLitres ───────────────────────────────────────────────────────────

    @Test
    fun `totalLitres sums all events`() {
        val events = listOf(
            event(id = 1, litres = 15.0, pricePerL = 7.0),
            event(id = 2, litres = 20.5, pricePerL = 7.2),
        )
        assertEquals(35.5, RefuelLedger.totalLitres(events), 0.001)
    }

    @Test
    fun `totalLitres returns zero for empty list`() {
        assertEquals(0.0, RefuelLedger.totalLitres(emptyList()), 0.0)
    }

    // ── totalCost ─────────────────────────────────────────────────────────────

    @Test
    fun `totalCost sums costOf for all events`() {
        val events = listOf(
            event(id = 1, litres = 10.0, pricePerL = 5.0),   // 50.0
            event(id = 2, litres = 20.0, pricePerL = 6.0),   // 120.0
        )
        assertEquals(170.0, RefuelLedger.totalCost(events), 0.001)
    }

    @Test
    fun `totalCost returns zero for empty list`() {
        assertEquals(0.0, RefuelLedger.totalCost(emptyList()), 0.0)
    }
}
