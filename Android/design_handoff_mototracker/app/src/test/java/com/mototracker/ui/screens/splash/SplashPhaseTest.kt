package com.mototracker.ui.screens.splash

import com.mototracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SplashStatus.labelFor] and [SplashGate.shouldDismiss].
 *
 * No Android framework is needed: [SplashStatus] returns plain Int constants and
 * [SplashGate] is pure arithmetic — both are fully exercisable in a JVM test.
 */
class SplashPhaseTest {

    // ── SplashStatus.labelFor ─────────────────────────────────────────────────

    @Test
    fun `labelFor INITIALIZING returns splash_status_initializing`() {
        assertEquals(R.string.splash_status_initializing, SplashStatus.labelFor(SplashPhase.INITIALIZING))
    }

    @Test
    fun `labelFor MIGRATING_DB returns splash_status_migrating_db`() {
        assertEquals(R.string.splash_status_migrating_db, SplashStatus.labelFor(SplashPhase.MIGRATING_DB))
    }

    @Test
    fun `labelFor LOADING_ROUTES returns splash_status_loading_routes`() {
        assertEquals(R.string.splash_status_loading_routes, SplashStatus.labelFor(SplashPhase.LOADING_ROUTES))
    }

    @Test
    fun `labelFor READY returns splash_status_loading_routes`() {
        assertEquals(R.string.splash_status_loading_routes, SplashStatus.labelFor(SplashPhase.READY))
    }

    @Test
    fun `labelFor covers every SplashPhase value`() {
        // Ensures no enum entry was silently missed by the when-expression.
        SplashPhase.values().forEach { phase ->
            val id = SplashStatus.labelFor(phase)
            assertTrue("labelFor($phase) returned 0 — missing branch?", id != 0)
        }
    }

    // ── SplashGate.shouldDismiss ──────────────────────────────────────────────

    @Test
    fun `not ready and animation not complete — do not dismiss`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = false, animationComplete = false,
                elapsedMs = 0L, maxDurationMs = 4_000L,
            )
        )
    }

    @Test
    fun `ready but animation not complete — do not dismiss`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = true, animationComplete = false,
                elapsedMs = 1_500L, maxDurationMs = 4_000L,
            )
        )
    }

    @Test
    fun `animation complete but not ready — do not dismiss`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = false, animationComplete = true,
                elapsedMs = 1_500L, maxDurationMs = 4_000L,
            )
        )
    }

    @Test
    fun `ready and animationComplete — dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = true, animationComplete = true,
                elapsedMs = 0L, maxDurationMs = 4_000L,
            )
        )
    }

    @Test
    fun `ready and animationComplete past TOTAL_MS — dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = true, animationComplete = true,
                elapsedMs = 2_500L, maxDurationMs = 4_000L,
            )
        )
    }

    @Test
    fun `not ready before max duration — do not dismiss`() {
        assertFalse(
            SplashGate.shouldDismiss(
                ready = false, animationComplete = false,
                elapsedMs = 3_999L, maxDurationMs = 4_000L,
            )
        )
    }

    @Test
    fun `not ready exactly at max duration — force dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = false, animationComplete = false,
                elapsedMs = 4_000L, maxDurationMs = 4_000L,
            )
        )
    }

    @Test
    fun `not ready past max duration — force dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = false, animationComplete = false,
                elapsedMs = 5_000L, maxDurationMs = 4_000L,
            )
        )
    }

    @Test
    fun `ready past max duration — dismiss`() {
        assertTrue(
            SplashGate.shouldDismiss(
                ready = true, animationComplete = true,
                elapsedMs = 6_000L, maxDurationMs = 4_000L,
            )
        )
    }

    @Test
    fun `default max constant matches expected value`() {
        assertEquals(4_000L, SplashGate.DEFAULT_MAX_MS)
    }

    @Test
    fun `shouldDismiss uses default maxDurationMs when not specified`() {
        // With defaults: only animationComplete+ready dismisses, max at 4_000 ms.
        assertFalse(SplashGate.shouldDismiss(ready = true, animationComplete = false, elapsedMs = 3_999L))
        assertTrue(SplashGate.shouldDismiss(ready = true, animationComplete = true, elapsedMs = 0L))
        assertFalse(SplashGate.shouldDismiss(ready = false, animationComplete = false, elapsedMs = 3_999L))
        assertTrue(SplashGate.shouldDismiss(ready = false, animationComplete = false, elapsedMs = 4_000L))
    }
}
