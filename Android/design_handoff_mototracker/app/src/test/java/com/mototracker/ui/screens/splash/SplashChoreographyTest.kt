package com.mototracker.ui.screens.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JUnit tests for [SplashChoreography].
 *
 * No Android framework or Robolectric dependency — all assertions target plain
 * Kotlin/JVM logic via [SplashChoreography.stateAt].
 */
class SplashChoreographyTest {

    private val eps = 0.001f

    // ── (1) stateAt(0) start states ──────────────────────────────────────────

    @Test
    fun `stateAt(0) bike starts off-screen, wheels not rotating, fade-in layers invisible, mountains opaque`() {
        val s = SplashChoreography.stateAt(0)
        assertEquals("bike.translateXFrac at t=0", -0.52f, s.bike.translateXFrac, eps)
        assertEquals("wheelRear.rotationDeg at t=0", 0f, s.wheelRear.rotationDeg, eps)
        assertEquals("wheelFront.rotationDeg at t=0", 0f, s.wheelFront.rotationDeg, eps)
        assertEquals("trail.alpha at t=0", 0f, s.trail.alpha, eps)
        assertEquals("pin.alpha at t=0", 0f, s.pin.alpha, eps)
        assertEquals("wordmark.alpha at t=0", 0f, s.wordmark.alpha, eps)
        assertEquals("mountains.alpha at t=0", 1f, s.mountains.alpha, eps)
    }

    // ── (2) Before each layer's window → start/invisible state ───────────────

    @Test
    fun `layers before their window hold start state`() {
        // Trail starts at 600 ms — sample just before
        val beforeTrail = SplashChoreography.stateAt(599)
        assertEquals("trail.alpha before window", 0f, beforeTrail.trail.alpha, eps)
        assertEquals("trail.scale before window", 0.96f, beforeTrail.trail.scale, eps)

        // Pin starts at 1200 ms
        val beforePin = SplashChoreography.stateAt(1199)
        assertEquals("pin.alpha before window", 0f, beforePin.pin.alpha, eps)
        assertEquals("pin.scale before window", 0f, beforePin.pin.scale, eps)
        assertEquals("pin.translateYPx before window", -24f, beforePin.pin.translateYPx, eps)

        // Wordmark starts at 1500 ms
        val beforeWordmark = SplashChoreography.stateAt(1499)
        assertEquals("wordmark.alpha before window", 0f, beforeWordmark.wordmark.alpha, eps)
        assertEquals("wordmark.scale before window", 0.9f, beforeWordmark.wordmark.scale, eps)

        // Bike/wheels: at t=0 already at start (window begins at 0)
        val atZero = SplashChoreography.stateAt(0)
        assertEquals("bike.translateXFrac at t=0", -0.52f, atZero.bike.translateXFrac, eps)
        assertEquals("wheelRear.rotationDeg at t=0", 0f, atZero.wheelRear.rotationDeg, eps)
    }

    // ── (3) Monotonically non-decreasing inside windows ──────────────────────

    @Test
    fun `trail alpha and scale are non-decreasing inside its window`() {
        val samples = (600..1300 step 50).map { SplashChoreography.stateAt(it.toLong()) }
        for (i in 1 until samples.size) {
            assertTrue("trail.alpha non-decreasing at step $i",
                samples[i].trail.alpha >= samples[i - 1].trail.alpha - eps)
            assertTrue("trail.scale non-decreasing at step $i",
                samples[i].trail.scale >= samples[i - 1].trail.scale - eps)
        }
    }

    @Test
    fun `pin alpha and scale are non-decreasing inside its window`() {
        val samples = (1200..1600 step 40).map { SplashChoreography.stateAt(it.toLong()) }
        for (i in 1 until samples.size) {
            assertTrue("pin.alpha non-decreasing at step $i",
                samples[i].pin.alpha >= samples[i - 1].pin.alpha - eps)
            assertTrue("pin.scale non-decreasing at step $i",
                samples[i].pin.scale >= samples[i - 1].pin.scale - eps)
        }
    }

    @Test
    fun `wordmark alpha and scale are non-decreasing inside its window`() {
        val samples = (1500..2200 step 70).map { SplashChoreography.stateAt(it.toLong()) }
        for (i in 1 until samples.size) {
            assertTrue("wordmark.alpha non-decreasing at step $i",
                samples[i].wordmark.alpha >= samples[i - 1].wordmark.alpha - eps)
            assertTrue("wordmark.scale non-decreasing at step $i",
                samples[i].wordmark.scale >= samples[i - 1].wordmark.scale - eps)
        }
    }

    @Test
    fun `bike translateXFrac increases toward zero across its window`() {
        val samples = (0..800 step 50).map { SplashChoreography.stateAt(it.toLong()) }
        for (i in 1 until samples.size) {
            assertTrue("bike.translateXFrac non-decreasing at step $i",
                samples[i].bike.translateXFrac >= samples[i - 1].bike.translateXFrac - eps)
        }
    }

    @Test
    fun `wheel rotation increases linearly across its window`() {
        val samples = (0..800 step 50).map { SplashChoreography.stateAt(it.toLong()) }
        for (i in 1 until samples.size) {
            assertTrue("wheelRear.rotationDeg non-decreasing at step $i",
                samples[i].wheelRear.rotationDeg >= samples[i - 1].wheelRear.rotationDeg - eps)
            assertTrue("wheelFront.rotationDeg non-decreasing at step $i",
                samples[i].wheelFront.rotationDeg >= samples[i - 1].wheelFront.rotationDeg - eps)
        }
    }

    // ── (4) After window (window_end and TOTAL_MS) → locked at end state ─────

    @Test
    fun `all layers locked at end state after their windows close`() {
        val atEnd = SplashChoreography.stateAt(SplashChoreography.TOTAL_MS)
        assertEquals("bike.translateXFrac locked at 0", 0f, atEnd.bike.translateXFrac, eps)
        assertEquals("wheelRear.rotationDeg locked at 720", 720f, atEnd.wheelRear.rotationDeg, eps)
        assertEquals("wheelFront.rotationDeg locked at 720", 720f, atEnd.wheelFront.rotationDeg, eps)
        assertEquals("trail.alpha locked at 1", 1f, atEnd.trail.alpha, eps)
        assertEquals("trail.scale locked at 1", 1f, atEnd.trail.scale, eps)
        assertEquals("pin.alpha locked at 1", 1f, atEnd.pin.alpha, eps)
        assertEquals("pin.translateYPx locked at 0", 0f, atEnd.pin.translateYPx, eps)
        assertEquals("pin.scale locked at 1", 1f, atEnd.pin.scale, eps)
        assertEquals("wordmark.alpha locked at 1", 1f, atEnd.wordmark.alpha, eps)
        assertEquals("wordmark.scale locked at 1", 1f, atEnd.wordmark.scale, eps)

        // Sample just past the bike window end (800 ms) to confirm lock
        val atBikeEnd = SplashChoreography.stateAt(800)
        assertEquals("bike.translateXFrac at window end", 0f, atBikeEnd.bike.translateXFrac, eps)
        assertEquals("wheelRear.rotationDeg at window end", 720f, atBikeEnd.wheelRear.rotationDeg, eps)
    }

    // ── (5) Clamping: stateAt(-500) == stateAt(0) and over == TOTAL_MS ───────

    @Test
    fun `negative elapsed clamps to zero`() {
        val atNeg = SplashChoreography.stateAt(-500)
        val atZero = SplashChoreography.stateAt(0)
        assertEquals("clamped neg bike.translateXFrac", atZero.bike.translateXFrac, atNeg.bike.translateXFrac, eps)
        assertEquals("clamped neg trail.alpha", atZero.trail.alpha, atNeg.trail.alpha, eps)
        assertEquals("clamped neg mountains.alpha", atZero.mountains.alpha, atNeg.mountains.alpha, eps)
        assertEquals("clamped neg wheelRear.rotationDeg", atZero.wheelRear.rotationDeg, atNeg.wheelRear.rotationDeg, eps)
    }

    @Test
    fun `elapsed beyond TOTAL_MS clamps to TOTAL_MS`() {
        val atOver = SplashChoreography.stateAt(SplashChoreography.TOTAL_MS + 500)
        val atTotal = SplashChoreography.stateAt(SplashChoreography.TOTAL_MS)
        assertEquals("clamped over bike.translateXFrac", atTotal.bike.translateXFrac, atOver.bike.translateXFrac, eps)
        assertEquals("clamped over wordmark.alpha", atTotal.wordmark.alpha, atOver.wordmark.alpha, eps)
        assertEquals("clamped over wheelRear.rotationDeg", atTotal.wheelRear.rotationDeg, atOver.wheelRear.rotationDeg, eps)
    }

    // ── (6) Mountains always opaque ───────────────────────────────────────────

    @Test
    fun `mountains alpha is 1f at t=0, mid, and TOTAL_MS`() {
        listOf(0L, SplashChoreography.TOTAL_MS / 2, SplashChoreography.TOTAL_MS).forEach { t ->
            assertEquals("mountains.alpha at $t ms", 1f, SplashChoreography.stateAt(t).mountains.alpha, eps)
        }
    }

    // ── (7) clampDelta ────────────────────────────────────────────────────────

    @Test
    fun `clampDelta zero delta returns 0`() {
        assertEquals(0L, SplashChoreography.clampDelta(1000L, 1000L))
    }

    @Test
    fun `clampDelta negative delta clamps to 0`() {
        assertEquals(0L, SplashChoreography.clampDelta(1000L, 500L))
    }

    @Test
    fun `clampDelta normal delta within max passes through unchanged`() {
        assertEquals(16L, SplashChoreography.clampDelta(1000L, 1016L))
    }

    @Test
    fun `clampDelta delta exactly at max passes through unchanged`() {
        assertEquals(32L, SplashChoreography.clampDelta(1000L, 1032L))
    }

    @Test
    fun `clampDelta delta exceeding max clamps to maxMs`() {
        assertEquals(32L, SplashChoreography.clampDelta(1000L, 1500L))
    }

    @Test
    fun `clampDelta custom maxMs is respected`() {
        assertEquals(16L, SplashChoreography.clampDelta(0L, 100L, maxMs = 16L))
    }

    // ── (8) stateAt(TOTAL_MS) full end state for every layer ─────────────────

    @Test
    fun `stateAt(TOTAL_MS) returns full end state for every layer`() {
        val s = SplashChoreography.stateAt(SplashChoreography.TOTAL_MS)
        assertEquals("bike.alpha", 1f, s.bike.alpha, eps)
        assertEquals("bike.translateXFrac", 0f, s.bike.translateXFrac, eps)
        assertEquals("bike.scale", 1f, s.bike.scale, eps)
        assertEquals("wheelRear.rotationDeg", 720f, s.wheelRear.rotationDeg, eps)
        assertEquals("wheelRear.translateXFrac", 0f, s.wheelRear.translateXFrac, eps)
        assertEquals("wheelFront.rotationDeg", 720f, s.wheelFront.rotationDeg, eps)
        assertEquals("wheelFront.translateXFrac", 0f, s.wheelFront.translateXFrac, eps)
        assertEquals("trail.alpha", 1f, s.trail.alpha, eps)
        assertEquals("trail.scale", 1f, s.trail.scale, eps)
        assertEquals("pin.alpha", 1f, s.pin.alpha, eps)
        assertEquals("pin.translateYPx", 0f, s.pin.translateYPx, eps)
        assertEquals("pin.scale", 1f, s.pin.scale, eps)
        assertEquals("wordmark.alpha", 1f, s.wordmark.alpha, eps)
        assertEquals("wordmark.scale", 1f, s.wordmark.scale, eps)
        assertEquals("mountains.alpha", 1f, s.mountains.alpha, eps)
    }

    // ── (9) AF3 settle constants and seam ─────────────────────────────────────

    @Test
    fun `SETTLE_MS is within the required bounds 1 to 200`() {
        assertTrue(
            "SETTLE_MS must be > 0 and ≤ 200, was ${SplashChoreography.SETTLE_MS}",
            SplashChoreography.SETTLE_MS in 1L..200L,
        )
    }

    @Test
    fun `totalWithSettleMs equals TOTAL_MS plus SETTLE_MS`() {
        assertEquals(
            SplashChoreography.TOTAL_MS + SplashChoreography.SETTLE_MS,
            SplashChoreography.totalWithSettleMs(),
        )
    }

    @Test
    fun `stateAt(totalWithSettleMs) equals stateAt(TOTAL_MS) — end-state clamp holds during settle`() {
        val atSettle = SplashChoreography.stateAt(SplashChoreography.totalWithSettleMs())
        val atTotal = SplashChoreography.stateAt(SplashChoreography.TOTAL_MS)
        assertEquals("bike.translateXFrac clamped", atTotal.bike.translateXFrac, atSettle.bike.translateXFrac, eps)
        assertEquals("wheelRear.rotationDeg clamped", atTotal.wheelRear.rotationDeg, atSettle.wheelRear.rotationDeg, eps)
        assertEquals("wheelFront.rotationDeg clamped", atTotal.wheelFront.rotationDeg, atSettle.wheelFront.rotationDeg, eps)
        assertEquals("trail.alpha clamped", atTotal.trail.alpha, atSettle.trail.alpha, eps)
        assertEquals("trail.scale clamped", atTotal.trail.scale, atSettle.trail.scale, eps)
        assertEquals("pin.alpha clamped", atTotal.pin.alpha, atSettle.pin.alpha, eps)
        assertEquals("pin.translateYPx clamped", atTotal.pin.translateYPx, atSettle.pin.translateYPx, eps)
        assertEquals("pin.scale clamped", atTotal.pin.scale, atSettle.pin.scale, eps)
        assertEquals("wordmark.alpha clamped", atTotal.wordmark.alpha, atSettle.wordmark.alpha, eps)
        assertEquals("wordmark.scale clamped", atTotal.wordmark.scale, atSettle.wordmark.scale, eps)
        assertEquals("mountains.alpha clamped", atTotal.mountains.alpha, atSettle.mountains.alpha, eps)
    }
}
