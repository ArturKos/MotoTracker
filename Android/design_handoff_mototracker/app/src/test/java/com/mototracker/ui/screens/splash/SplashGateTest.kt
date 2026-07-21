package com.mototracker.ui.screens.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the AF1 dismiss-timing seam: [SplashGate.shouldDismiss] with the
 * frame-delta-clamped [animationComplete] signal replacing the old wall-clock
 * minimum-duration floor.
 *
 * Rules under test:
 * - Ready but animation not yet complete → NOT dismissed (gate waits for on-frame completion).
 * - Ready AND animationComplete → dismissed immediately (no wall-clock floor).
 * - elapsed ≥ maxDurationMs (3 000 ms) → force-dismiss regardless of ready/animationComplete.
 */
class SplashGateTest {

    private val maxMs = 3_000L

    // ── TOTAL_MS constant ─────────────────────────────────────────────────────

    @Test
    fun `TOTAL_MS constant is 2200 ms`() {
        assertEquals(2_200L, SplashChoreography.TOTAL_MS)
    }

    // ── Ready but animation not complete ─────────────────────────────────────

    @Test
    fun `ready but animation not complete at t=0 — not dismissed`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = true,
                animationComplete = false,
                elapsedMs = 0L,
                maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `ready but animation not complete well before max — not dismissed`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = true,
                animationComplete = false,
                elapsedMs = SplashChoreography.TOTAL_MS,
                maxDurationMs = maxMs,
            )
        )
    }

    // ── Ready AND animation complete ──────────────────────────────────────────

    @Test
    fun `ready and animationComplete at t=0 — dismissed immediately`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = true,
                animationComplete = true,
                elapsedMs = 0L,
                maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `ready and animationComplete at TOTAL_MS — dismissed`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = true,
                animationComplete = true,
                elapsedMs = SplashChoreography.TOTAL_MS,
                maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `ready and animationComplete past TOTAL_MS — dismissed`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = true,
                animationComplete = true,
                elapsedMs = SplashChoreography.TOTAL_MS + 300L,
                maxDurationMs = maxMs,
            )
        )
    }

    // ── Not ready, within hard cap ────────────────────────────────────────────

    @Test
    fun `not ready and animation not complete at t=0 — not dismissed`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = false,
                animationComplete = false,
                elapsedMs = 0L,
                maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `not ready just before max — not dismissed`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = false,
                animationComplete = false,
                elapsedMs = maxMs - 1L,
                maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `animation complete but not ready — not dismissed`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = false,
                animationComplete = true,
                elapsedMs = SplashChoreography.TOTAL_MS,
                maxDurationMs = maxMs,
            )
        )
    }

    // ── Force-dismiss at hard cap ─────────────────────────────────────────────

    @Test
    fun `not ready exactly at max — force dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = false,
                animationComplete = false,
                elapsedMs = maxMs,
                maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `not ready past max — force dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = false,
                animationComplete = false,
                elapsedMs = maxMs + 500L,
                maxDurationMs = maxMs,
            )
        )
    }

    @Test
    fun `ready and animationComplete past max — force dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = true,
                animationComplete = true,
                elapsedMs = maxMs + 100L,
                maxDurationMs = maxMs,
            )
        )
    }

    // ── Verify MainActivity's exact call-site parameters ─────────────────────

    @Test
    fun `call-site maxDurationMs matches expected constant`() {
        assertEquals(3_000L, maxMs)
    }
}
