package com.mototracker.domain.recording

import com.mototracker.domain.fuel.FuelAdjustmentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RecordingEngine.applyFuelCorrection] (R1).
 *
 * Verifies that the anchor-based fuel model correctly re-anchors the remaining-fuel
 * estimate in response to SET_ABSOLUTE and DELTA corrections, and that the correction
 * is a no-op when the engine has no tank capacity configured.
 */
class RecordingEngineFuelCorrectionTest {

    private lateinit var engine: RecordingEngine

    @Before
    fun setUp() {
        engine = RecordingEngine(fuelLper100km = 5.0)
    }

    // ── applyFuelCorrection no-op when no tank ───────────────────────────────

    @Test
    fun `applyFuelCorrection is no-op when tankCapacityL is null`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = null)
        engine.applyFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 10.0)
        // With no tank, fuel readout should stay null.
        assertNull(engine.snapshot().remainingFuelL)
    }

    // ── SET_ABSOLUTE ─────────────────────────────────────────────────────────

    @Test
    fun `SET_ABSOLUTE sets remaining fuel to the supplied value`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 20.0)
        engine.applyFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 12.0)
        assertEquals(12.0, engine.snapshot().remainingFuelL ?: -1.0, 0.01)
    }

    @Test
    fun `SET_ABSOLUTE to zero drains tank`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 20.0)
        engine.applyFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 0.0)
        assertEquals(0.0, engine.snapshot().remainingFuelL ?: -1.0, 0.01)
    }

    @Test
    fun `SET_ABSOLUTE negative value clamps to zero`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 20.0)
        engine.applyFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, -5.0)
        assertEquals(0.0, engine.snapshot().remainingFuelL ?: -1.0, 0.01)
    }

    @Test
    fun `SET_ABSOLUTE above capacity clamps to capacity`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 20.0)
        engine.applyFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 25.0)
        assertEquals(20.0, engine.snapshot().remainingFuelL ?: -1.0, 0.01)
    }

    // ── DELTA ─────────────────────────────────────────────────────────────────

    @Test
    fun `DELTA correction adds to current remaining`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 20.0)
        // First SET_ABSOLUTE to known state (15L remaining)
        engine.applyFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 15.0)
        // Then DELTA +3
        engine.applyFuelCorrection(FuelAdjustmentMode.DELTA, 3.0)
        assertEquals(18.0, engine.snapshot().remainingFuelL ?: -1.0, 0.01)
    }

    @Test
    fun `DELTA correction subtracts from current remaining`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 20.0)
        engine.applyFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 15.0)
        engine.applyFuelCorrection(FuelAdjustmentMode.DELTA, -5.0)
        assertEquals(10.0, engine.snapshot().remainingFuelL ?: -1.0, 0.01)
    }

    @Test
    fun `DELTA that would go negative clamps to zero`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 20.0)
        engine.applyFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 5.0)
        engine.applyFuelCorrection(FuelAdjustmentMode.DELTA, -10.0)
        assertEquals(0.0, engine.snapshot().remainingFuelL ?: -1.0, 0.01)
    }

    // ── Anchor re-anchor after distance accumulation ─────────────────────────

    @Test
    fun `anchor is re-set at current distance so subsequent consumption is correct`() {
        engine.reset(fuelLper100km = 10.0, tankCapacityL = 20.0)
        // Drive 100 km (lat 52.0 → 52.0+delta)
        val delta100km = 100.0 / 111.195
        engine.onLocation(locationSample(lat = 52.0, lng = 21.0, speedMps = 28.0))
        engine.onLocation(locationSample(lat = 52.0 + delta100km, lng = 21.0, speedMps = 28.0))
        val remainingAfter100km = engine.snapshot().remainingFuelL ?: -1.0
        assertEquals(10.0, remainingAfter100km, 0.5)

        // Correct to 15L remaining; re-anchor is at distanceKm ≈ 100
        engine.applyFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 15.0)
        assertEquals(15.0, engine.snapshot().remainingFuelL ?: -1.0, 0.01)

        // Drive another 100 km continuing from 52.0+delta → 52.0+2*delta
        engine.onLocation(locationSample(lat = 52.0 + 2 * delta100km, lng = 21.0, speedMps = 28.0))
        val remainingAfter200km = engine.snapshot().remainingFuelL ?: -1.0
        // 100 km at 10 L/100 km = 10L consumed → 15 - 10 = 5L
        assertEquals(5.0, remainingAfter200km, 0.5)
    }

    // ── lowFuel flag reflects corrected level ─────────────────────────────────

    @Test
    fun `SET_ABSOLUTE below LOW_FUEL_FRACTION threshold sets lowFuel true`() {
        // 20L tank; threshold = 0.15 * 20 = 3.0L
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 20.0)
        engine.applyFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 2.0)
        assertTrue(
            "lowFuel should be true when remaining (2L) ≤ threshold (3L)",
            engine.snapshot().lowFuel,
        )
    }

    @Test
    fun `SET_ABSOLUTE above LOW_FUEL_FRACTION threshold sets lowFuel false`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 20.0)
        engine.applyFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 5.0)
        assertFalse(
            "lowFuel should be false when remaining (5L) > threshold (3L)",
            engine.snapshot().lowFuel,
        )
    }

    @Test
    fun `lowFuel transitions from true to false after correction raises level`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 20.0)
        // First bring it below threshold
        engine.applyFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 2.0)
        assertTrue("precondition: lowFuel should be true at 2L", engine.snapshot().lowFuel)
        // Then raise it above threshold
        engine.applyFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 10.0)
        assertFalse("lowFuel should be false after raising level to 10L", engine.snapshot().lowFuel)
    }

    // ── fillToFull still works after correction ───────────────────────────────

    @Test
    fun `fillToFull after correction restores to full tank`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 20.0)
        engine.applyFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 8.0)
        assertEquals(8.0, engine.snapshot().remainingFuelL ?: -1.0, 0.01)
        engine.fillToFull()
        assertEquals(20.0, engine.snapshot().remainingFuelL ?: -1.0, 0.01)
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * Drives the engine by [km] kilometres using a single long straight-line fix.
     *
     * Approximates via lat/lng deltas; 1° latitude ≈ 111.195 km.
     */
    private fun driveKm(engine: RecordingEngine, km: Double) {
        val deltaLat = km / 111.195
        engine.onLocation(locationSample(lat = 52.0, lng = 21.0, speedMps = 28.0))
        engine.onLocation(locationSample(lat = 52.0 + deltaLat, lng = 21.0, speedMps = 28.0))
    }

    private fun locationSample(lat: Double, lng: Double, speedMps: Double = 0.0): LocationSample =
        LocationSample(
            lat = lat,
            lng = lng,
            altitudeM = 100.0,
            speedMps = speedMps,
            bearingDeg = 0f,
            // timeMs = 0 bypasses the outlier gate (dtSec = 0 → gate skipped), matching
            // the convention used by the existing RecordingEngineTest helpers.
            timeMs = 0L,
        )
}
