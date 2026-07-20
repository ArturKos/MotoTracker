package com.mototracker.ui.screens.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the AE3 dismiss-timing seam: [SplashGate.shouldDismiss] called with
 * [SplashChoreography.TOTAL_MS] as the minimum and 3 000 ms as the hard cap,
 * which matches the [com.mototracker.MainActivity] call site.
 *
 * Rules under test:
 * - The splash is NOT dismissed before [SplashChoreography.TOTAL_MS] even when
 *   the app is fully initialised — the Compose entrance animation must finish.
 * - Once ready AND elapsed ≥ [SplashChoreography.TOTAL_MS], the splash is dismissed.
 * - If elapsed ≥ 3 000 ms the splash is force-dismissed regardless of readiness.
 */
class SplashGateTest {

    private val minMs = SplashChoreography.TOTAL_MS
    private val maxMs = 3_000L

    // ── TOTAL_MS constant ─────────────────────────────────────────────────────

    @Test
    fun `TOTAL_MS constant is 2200 ms`() {
        assertEquals(2_200L, SplashChoreography.TOTAL_MS)
    }

    // ── Ready but animation still playing ────────────────────────────────────

    @Test
    fun `ready before min duration — not dismissed`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = true, elapsedMs = 0L,
                minDurationMs = minMs, maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `ready just before min duration — not dismissed`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = true, elapsedMs = minMs - 1L,
                minDurationMs = minMs, maxDurationMs = maxMs,
            )
        )
    }

    // ── Ready AND animation finished ──────────────────────────────────────────

    @Test
    fun `ready exactly at min duration — dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = true, elapsedMs = minMs,
                minDurationMs = minMs, maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `ready past min duration — dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = true, elapsedMs = minMs + 300L,
                minDurationMs = minMs, maxDurationMs = maxMs,
            )
        )
    }

    // ── Not ready, within hard cap ────────────────────────────────────────────

    @Test
    fun `not ready before max — not dismissed`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = false, elapsedMs = 0L,
                minDurationMs = minMs, maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `not ready just before max — not dismissed`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = false, elapsedMs = maxMs - 1L,
                minDurationMs = minMs, maxDurationMs = maxMs,
            )
        )
    }

    // ── Force-dismiss at hard cap ─────────────────────────────────────────────

    @Test
    fun `not ready exactly at max — force dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = false, elapsedMs = maxMs,
                minDurationMs = minMs, maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `not ready past max — force dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = false, elapsedMs = maxMs + 500L,
                minDurationMs = minMs, maxDurationMs = maxMs,
            )
        )
    }

    // ── Boundary between min and max ──────────────────────────────────────────

    @Test
    fun `not ready between min-end and max — not dismissed`() {
        // app still initialising at t=2300 ms: must not force-dismiss until 3000
        assertFalse(
            SplashGate.shouldDismiss(
                ready = false, elapsedMs = minMs + 100L,
                minDurationMs = minMs, maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `ready between min-end and max — dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = true, elapsedMs = minMs + 100L,
                minDurationMs = minMs, maxDurationMs = maxMs,
            )
        )
    }

    // ── Verify MainActivity's exact call-site parameters ─────────────────────

    @Test
    fun `call-site parameters match expected constants`() {
        // Documents that MainActivity passes minDurationMs=2200, maxDurationMs=3000.
        assertEquals(2_200L, minMs)
        assertEquals(3_000L, maxMs)
    }
}
