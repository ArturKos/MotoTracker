package com.mototracker.ui.screens.record

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeChipStateTest {

    // ── null / non-finite / negative → Estimating ────────────────────────────

    @Test
    fun `null returns Estimating`() {
        assertEquals(RangeChipState.Estimating, rangeChipState(null))
    }

    @Test
    fun `NaN returns Estimating`() {
        assertEquals(RangeChipState.Estimating, rangeChipState(Double.NaN))
    }

    @Test
    fun `positive infinity returns Estimating`() {
        assertEquals(RangeChipState.Estimating, rangeChipState(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `negative infinity returns Estimating`() {
        assertEquals(RangeChipState.Estimating, rangeChipState(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `negative value returns Estimating`() {
        assertEquals(RangeChipState.Estimating, rangeChipState(-0.1))
    }

    @Test
    fun `zero returns Value with km 0`() {
        assertEquals(RangeChipState.Value(km = 0), rangeChipState(0.0))
    }

    // ── rounding ──────────────────────────────────────────────────────────────

    @Test
    fun `exact integer value is preserved`() {
        assertEquals(RangeChipState.Value(km = 120), rangeChipState(120.0))
    }

    @Test
    fun `value rounds up at half`() {
        assertEquals(RangeChipState.Value(km = 121), rangeChipState(120.5))
    }

    @Test
    fun `value rounds down below half`() {
        assertEquals(RangeChipState.Value(km = 120), rangeChipState(120.4))
    }

    @Test
    fun `small positive value rounds to 1`() {
        assertEquals(RangeChipState.Value(km = 1), rangeChipState(0.7))
    }

    @Test
    fun `large value rounds correctly`() {
        assertEquals(RangeChipState.Value(km = 999), rangeChipState(999.3))
    }
}
