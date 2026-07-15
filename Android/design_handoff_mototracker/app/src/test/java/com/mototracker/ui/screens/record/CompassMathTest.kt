package com.mototracker.ui.screens.record

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassMathTest {

    // ─── normalizeHeading ────────────────────────────────────────────────────

    @Test
    fun normalizeHeading_zero() {
        assertEquals(0f, CompassMath.normalizeHeading(0f), 0.001f)
    }

    @Test
    fun normalizeHeading_positiveInRange() {
        assertEquals(90f, CompassMath.normalizeHeading(90f), 0.001f)
    }

    @Test
    fun normalizeHeading_negative() {
        assertEquals(270f, CompassMath.normalizeHeading(-90f), 0.001f)
    }

    @Test
    fun normalizeHeading_greaterThan360() {
        assertEquals(10f, CompassMath.normalizeHeading(370f), 0.001f)
    }

    @Test
    fun normalizeHeading_exactly360() {
        assertEquals(0f, CompassMath.normalizeHeading(360f), 0.001f)
    }

    @Test
    fun normalizeHeading_nan() {
        assertEquals(0f, CompassMath.normalizeHeading(Float.NaN), 0.001f)
    }

    @Test
    fun normalizeHeading_largeNegative() {
        // -370 = -360 - 10 → should normalize to 350
        assertEquals(350f, CompassMath.normalizeHeading(-370f), 0.001f)
    }

    @Test
    fun normalizeHeading_720() {
        assertEquals(0f, CompassMath.normalizeHeading(720f), 0.001f)
    }

    // ─── cardinal — 8-point centres ─────────────────────────────────────────

    @Test
    fun cardinal_north_center() {
        assertEquals(CompassMath.Cardinal.N, CompassMath.cardinal(0f))
    }

    @Test
    fun cardinal_ne_center() {
        assertEquals(CompassMath.Cardinal.NE, CompassMath.cardinal(45f))
    }

    @Test
    fun cardinal_east_center() {
        assertEquals(CompassMath.Cardinal.E, CompassMath.cardinal(90f))
    }

    @Test
    fun cardinal_se_center() {
        assertEquals(CompassMath.Cardinal.SE, CompassMath.cardinal(135f))
    }

    @Test
    fun cardinal_south_center() {
        assertEquals(CompassMath.Cardinal.S, CompassMath.cardinal(180f))
    }

    @Test
    fun cardinal_sw_center() {
        assertEquals(CompassMath.Cardinal.SW, CompassMath.cardinal(225f))
    }

    @Test
    fun cardinal_west_center() {
        assertEquals(CompassMath.Cardinal.W, CompassMath.cardinal(270f))
    }

    @Test
    fun cardinal_nw_center() {
        assertEquals(CompassMath.Cardinal.NW, CompassMath.cardinal(315f))
    }

    // ─── cardinal — boundary transitions (±0.1° around each 22.5° boundary) ─

    @Test
    fun cardinal_boundary_n_ne() {
        assertEquals(CompassMath.Cardinal.N,  CompassMath.cardinal(22.4f))
        assertEquals(CompassMath.Cardinal.NE, CompassMath.cardinal(22.6f))
    }

    @Test
    fun cardinal_boundary_ne_e() {
        assertEquals(CompassMath.Cardinal.NE, CompassMath.cardinal(67.4f))
        assertEquals(CompassMath.Cardinal.E,  CompassMath.cardinal(67.6f))
    }

    @Test
    fun cardinal_boundary_e_se() {
        assertEquals(CompassMath.Cardinal.E,  CompassMath.cardinal(112.4f))
        assertEquals(CompassMath.Cardinal.SE, CompassMath.cardinal(112.6f))
    }

    @Test
    fun cardinal_boundary_se_s() {
        assertEquals(CompassMath.Cardinal.SE, CompassMath.cardinal(157.4f))
        assertEquals(CompassMath.Cardinal.S,  CompassMath.cardinal(157.6f))
    }

    @Test
    fun cardinal_boundary_s_sw() {
        assertEquals(CompassMath.Cardinal.S,  CompassMath.cardinal(202.4f))
        assertEquals(CompassMath.Cardinal.SW, CompassMath.cardinal(202.6f))
    }

    @Test
    fun cardinal_boundary_sw_w() {
        assertEquals(CompassMath.Cardinal.SW, CompassMath.cardinal(247.4f))
        assertEquals(CompassMath.Cardinal.W,  CompassMath.cardinal(247.6f))
    }

    @Test
    fun cardinal_boundary_w_nw() {
        assertEquals(CompassMath.Cardinal.W,  CompassMath.cardinal(292.4f))
        assertEquals(CompassMath.Cardinal.NW, CompassMath.cardinal(292.6f))
    }

    @Test
    fun cardinal_boundary_nw_n() {
        assertEquals(CompassMath.Cardinal.NW, CompassMath.cardinal(337.4f))
        assertEquals(CompassMath.Cardinal.N,  CompassMath.cardinal(337.6f))
    }

    // ─── selectDisplayHeading — G3 regression guard ──────────────────────────

    @Test
    fun selectDisplayHeading_recording_withLive_returnsLive() {
        // G3: during Recording, magnetometer must win over GPS bearing.
        val result = CompassMath.selectDisplayHeading(
            phase = RecordingPhase.Recording,
            liveHeadingDeg = 45f,
            gpsHeadingDeg = 0f,  // GPS bearing 0° (stationary / slow — D6 freeze scenario)
        )
        assertEquals(45f, result, 0.001f)
    }

    @Test
    fun selectDisplayHeading_recording_nullLive_returnsGps() {
        val result = CompassMath.selectDisplayHeading(
            phase = RecordingPhase.Recording,
            liveHeadingDeg = null,
            gpsHeadingDeg = 90f,
        )
        assertEquals(90f, result, 0.001f)
    }

    @Test
    fun selectDisplayHeading_idle_withLive_returnsLive() {
        val result = CompassMath.selectDisplayHeading(
            phase = RecordingPhase.Idle,
            liveHeadingDeg = 270f,
            gpsHeadingDeg = 180f,
        )
        assertEquals(270f, result, 0.001f)
    }

    @Test
    fun selectDisplayHeading_paused_withLive_returnsLive() {
        val result = CompassMath.selectDisplayHeading(
            phase = RecordingPhase.Paused,
            liveHeadingDeg = 135f,
            gpsHeadingDeg = 0f,
        )
        assertEquals(135f, result, 0.001f)
    }

    @Test
    fun selectDisplayHeading_idle_nullLive_returnsGps() {
        val result = CompassMath.selectDisplayHeading(
            phase = RecordingPhase.Idle,
            liveHeadingDeg = null,
            gpsHeadingDeg = 30f,
        )
        assertEquals(30f, result, 0.001f)
    }

    @Test
    fun selectDisplayHeading_paused_nullLive_returnsGps() {
        val result = CompassMath.selectDisplayHeading(
            phase = RecordingPhase.Paused,
            liveHeadingDeg = null,
            gpsHeadingDeg = 200f,
        )
        assertEquals(200f, result, 0.001f)
    }

    // ─── needleEndpoint — cardinal directions ────────────────────────────────

    private val DELTA = 0.001f
    private val LENGTH = 100f

    @Test
    fun needleEndpoint_north_pointsUp() {
        val (dx, dy) = CompassMath.needleEndpoint(0f, LENGTH)
        assertEquals(0f, dx, DELTA)
        assertEquals(-LENGTH, dy, DELTA)
    }

    @Test
    fun needleEndpoint_east_pointsRight() {
        val (dx, dy) = CompassMath.needleEndpoint(90f, LENGTH)
        assertEquals(LENGTH, dx, DELTA)
        assertEquals(0f, dy, DELTA)
    }

    @Test
    fun needleEndpoint_south_pointsDown() {
        val (dx, dy) = CompassMath.needleEndpoint(180f, LENGTH)
        assertEquals(0f, dx, DELTA)
        assertEquals(LENGTH, dy, DELTA)
    }

    @Test
    fun needleEndpoint_west_pointsLeft() {
        val (dx, dy) = CompassMath.needleEndpoint(270f, LENGTH)
        assertEquals(-LENGTH, dx, DELTA)
        assertEquals(0f, dy, DELTA)
    }

    @Test
    fun needleEndpoint_negativeHeading_normalisedCorrectly() {
        // -90° normalises to 270° (West) → same as needleEndpoint(270°)
        val (dx, dy) = CompassMath.needleEndpoint(-90f, LENGTH)
        assertEquals(-LENGTH, dx, DELTA)
        assertEquals(0f, dy, DELTA)
    }

    @Test
    fun needleEndpoint_headingOver360_normalisedCorrectly() {
        // 450° normalises to 90° (East) → same as needleEndpoint(90°)
        val (dx, dy) = CompassMath.needleEndpoint(450f, LENGTH)
        assertEquals(LENGTH, dx, DELTA)
        assertEquals(0f, dy, DELTA)
    }

    @Test
    fun needleEndpoint_negativeLength_pointsOpposite() {
        // Tail at 0° heading with negative length should point south (dx=0, dy=+len)
        val (dx, dy) = CompassMath.needleEndpoint(0f, -LENGTH)
        assertEquals(0f, dx, DELTA)
        assertEquals(LENGTH, dy, DELTA)
    }

    @Test
    fun needleEndpoint_exactly360_sameAsZero() {
        val (dx0, dy0) = CompassMath.needleEndpoint(0f, LENGTH)
        val (dx360, dy360) = CompassMath.needleEndpoint(360f, LENGTH)
        assertEquals(dx0, dx360, DELTA)
        assertEquals(dy0, dy360, DELTA)
    }

    // ─── cardinal — edge cases ───────────────────────────────────────────────

    @Test
    fun cardinal_exactly_360_wraps_to_north() {
        assertEquals(CompassMath.Cardinal.N, CompassMath.cardinal(360f))
    }

    @Test
    fun cardinal_negative_heading() {
        // -90 normalises to 270 → West
        assertEquals(CompassMath.Cardinal.W, CompassMath.cardinal(-90f))
    }

    @Test
    fun cardinal_nan_heading() {
        // NaN normalises to 0 → North
        assertEquals(CompassMath.Cardinal.N, CompassMath.cardinal(Float.NaN))
    }
}
