package com.mototracker.domain.fuel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [FuelAdjustmentCalculator].
 *
 * All tests use plain JUnit — no Android runtime or Robolectric needed.
 */
class FuelAdjustmentCalculatorTest {

    // ── SET_ABSOLUTE ─────────────────────────────────────────────────────────

    @Test
    fun `SET_ABSOLUTE returns the supplied value when within tank range`() {
        val result = FuelAdjustmentCalculator.newAnchorLitres(
            mode = FuelAdjustmentMode.SET_ABSOLUTE,
            value = 10.0,
            currentRemainingL = 5.0,
            tankCapacityL = 20.0,
        )
        assertEquals(10.0, result, 0.0001)
    }

    @Test
    fun `SET_ABSOLUTE clamps to zero when value is negative`() {
        val result = FuelAdjustmentCalculator.newAnchorLitres(
            mode = FuelAdjustmentMode.SET_ABSOLUTE,
            value = -3.0,
            currentRemainingL = 5.0,
            tankCapacityL = 20.0,
        )
        assertEquals(0.0, result, 0.0001)
    }

    @Test
    fun `SET_ABSOLUTE clamps to tank capacity when value exceeds it`() {
        val result = FuelAdjustmentCalculator.newAnchorLitres(
            mode = FuelAdjustmentMode.SET_ABSOLUTE,
            value = 25.0,
            currentRemainingL = 5.0,
            tankCapacityL = 20.0,
        )
        assertEquals(20.0, result, 0.0001)
    }

    @Test
    fun `SET_ABSOLUTE at exact tank capacity is accepted`() {
        val result = FuelAdjustmentCalculator.newAnchorLitres(
            mode = FuelAdjustmentMode.SET_ABSOLUTE,
            value = 20.0,
            currentRemainingL = 5.0,
            tankCapacityL = 20.0,
        )
        assertEquals(20.0, result, 0.0001)
    }

    // ── DELTA ─────────────────────────────────────────────────────────────────

    @Test
    fun `DELTA adds positive value to current remaining`() {
        val result = FuelAdjustmentCalculator.newAnchorLitres(
            mode = FuelAdjustmentMode.DELTA,
            value = 3.0,
            currentRemainingL = 10.0,
            tankCapacityL = 20.0,
        )
        assertEquals(13.0, result, 0.0001)
    }

    @Test
    fun `DELTA subtracts negative value from current remaining`() {
        val result = FuelAdjustmentCalculator.newAnchorLitres(
            mode = FuelAdjustmentMode.DELTA,
            value = -4.0,
            currentRemainingL = 10.0,
            tankCapacityL = 20.0,
        )
        assertEquals(6.0, result, 0.0001)
    }

    @Test
    fun `DELTA clamps to zero when result would be negative`() {
        val result = FuelAdjustmentCalculator.newAnchorLitres(
            mode = FuelAdjustmentMode.DELTA,
            value = -15.0,
            currentRemainingL = 10.0,
            tankCapacityL = 20.0,
        )
        assertEquals(0.0, result, 0.0001)
    }

    @Test
    fun `DELTA clamps to tank capacity when result would overflow`() {
        val result = FuelAdjustmentCalculator.newAnchorLitres(
            mode = FuelAdjustmentMode.DELTA,
            value = 15.0,
            currentRemainingL = 10.0,
            tankCapacityL = 20.0,
        )
        assertEquals(20.0, result, 0.0001)
    }

    // ── Non-finite guard ─────────────────────────────────────────────────────

    @Test
    fun `non-finite value returns clamped currentRemaining`() {
        val result = FuelAdjustmentCalculator.newAnchorLitres(
            mode = FuelAdjustmentMode.SET_ABSOLUTE,
            value = Double.NaN,
            currentRemainingL = 8.0,
            tankCapacityL = 20.0,
        )
        assertEquals(8.0, result, 0.0001)
    }

    @Test
    fun `positive infinity value is guarded — returns currentRemaining clamped to tank`() {
        val result = FuelAdjustmentCalculator.newAnchorLitres(
            mode = FuelAdjustmentMode.DELTA,
            value = Double.POSITIVE_INFINITY,
            currentRemainingL = 8.0,
            tankCapacityL = 20.0,
        )
        assertEquals(8.0, result, 0.0001)
    }

    @Test
    fun `negative infinity value falls through guard and returns currentRemaining`() {
        val result = FuelAdjustmentCalculator.newAnchorLitres(
            mode = FuelAdjustmentMode.DELTA,
            value = Double.NEGATIVE_INFINITY,
            currentRemainingL = 8.0,
            tankCapacityL = 20.0,
        )
        // NEGATIVE_INFINITY is non-finite → guard returns 8.0.coerceIn(0.0, 20.0) = 8.0
        assertEquals(8.0, result, 0.0001)
    }
}
