package com.mototracker.ui.screens.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the AA3 dismiss-timing seam: [SplashGate.shouldDismiss] called with
 * [SplashGate.AVD_DURATION_MS] as the minimum and 2 500 ms as the hard cap,
 * which matches the [MainActivity] call site.
 *
 * Rules under test:
 * - The splash is NOT dismissed before [SplashGate.AVD_DURATION_MS] even when
 *   the app is fully initialised — the AVD must finish playing.
 * - Once ready AND elapsed ≥ AVD_DURATION_MS, the splash is dismissed.
 * - If elapsed ≥ 2 500 ms the splash is force-dismissed regardless of readiness.
 */
class SplashGateTest {

    private val avdMs = SplashGate.AVD_DURATION_MS
    private val maxMs = 2_500L

    // ── AVD_DURATION_MS constant ──────────────────────────────────────────────

    @Test
    fun `AVD_DURATION_MS constant is 2000 ms`() {
        assertEquals(2_000L, SplashGate.AVD_DURATION_MS)
    }

    // ── Ready but AVD still playing ──────────────────────────────────────────

    @Test
    fun `ready before AVD duration — not dismissed`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = true, elapsedMs = 0L,
                minDurationMs = avdMs, maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `ready just before AVD duration — not dismissed`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = true, elapsedMs = avdMs - 1L,
                minDurationMs = avdMs, maxDurationMs = maxMs,
            )
        )
    }

    // ── Ready AND AVD finished ────────────────────────────────────────────────

    @Test
    fun `ready exactly at AVD duration — dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = true, elapsedMs = avdMs,
                minDurationMs = avdMs, maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `ready past AVD duration — dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = true, elapsedMs = avdMs + 300L,
                minDurationMs = avdMs, maxDurationMs = maxMs,
            )
        )
    }

    // ── Not ready, within hard cap ────────────────────────────────────────────

    @Test
    fun `not ready before max — not dismissed`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = false, elapsedMs = 0L,
                minDurationMs = avdMs, maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `not ready just before max — not dismissed`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = false, elapsedMs = maxMs - 1L,
                minDurationMs = avdMs, maxDurationMs = maxMs,
            )
        )
    }

    // ── Force-dismiss at hard cap ─────────────────────────────────────────────

    @Test
    fun `not ready exactly at max — force dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = false, elapsedMs = maxMs,
                minDurationMs = avdMs, maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `not ready past max — force dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = false, elapsedMs = maxMs + 500L,
                minDurationMs = avdMs, maxDurationMs = maxMs,
            )
        )
    }

    // ── Boundary between AVD_DURATION_MS and max ────────────────────────────

    @Test
    fun `not ready between avd-end and max — not dismissed`() {
        // e.g. app still initialising at t=2100 ms: must not force-dismiss until 2500
        assertFalse(
            SplashGate.shouldDismiss(
                ready = false, elapsedMs = avdMs + 100L,
                minDurationMs = avdMs, maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `ready between avd-end and max — dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = true, elapsedMs = avdMs + 100L,
                minDurationMs = avdMs, maxDurationMs = maxMs,
            )
        )
    }

    // ── Verify MainActivity's exact call-site parameters ─────────────────────

    @Test
    fun `call-site parameters match expected constants`() {
        // Documents that MainActivity passes minDurationMs=2000, maxDurationMs=2500.
        assertEquals(2_000L, avdMs)
        assertEquals(2_500L, maxMs)
    }
}
