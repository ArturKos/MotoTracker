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
    fun `not ready before min duration — do not dismiss`() {
        assertFalse(SplashGate.shouldDismiss(ready = false, elapsedMs = 0L, minDurationMs = 700L, maxDurationMs = 4000L))
    }

    @Test
    fun `not ready at min duration — do not dismiss`() {
        assertFalse(SplashGate.shouldDismiss(ready = false, elapsedMs = 700L, minDurationMs = 700L, maxDurationMs = 4000L))
    }

    @Test
    fun `ready before min duration — do not dismiss`() {
        assertFalse(SplashGate.shouldDismiss(ready = true, elapsedMs = 500L, minDurationMs = 700L, maxDurationMs = 4000L))
    }

    @Test
    fun `ready exactly at min duration — dismiss`() {
        assertTrue(SplashGate.shouldDismiss(ready = true, elapsedMs = 700L, minDurationMs = 700L, maxDurationMs = 4000L))
    }

    @Test
    fun `ready after min duration — dismiss`() {
        assertTrue(SplashGate.shouldDismiss(ready = true, elapsedMs = 1500L, minDurationMs = 700L, maxDurationMs = 4000L))
    }

    @Test
    fun `not ready before max duration — do not dismiss`() {
        assertFalse(SplashGate.shouldDismiss(ready = false, elapsedMs = 3999L, minDurationMs = 700L, maxDurationMs = 4000L))
    }

    @Test
    fun `not ready exactly at max duration — force dismiss`() {
        assertTrue(SplashGate.shouldDismiss(ready = false, elapsedMs = 4000L, minDurationMs = 700L, maxDurationMs = 4000L))
    }

    @Test
    fun `not ready past max duration — force dismiss`() {
        assertTrue(SplashGate.shouldDismiss(ready = false, elapsedMs = 5000L, minDurationMs = 700L, maxDurationMs = 4000L))
    }

    @Test
    fun `ready past max duration — dismiss`() {
        assertTrue(SplashGate.shouldDismiss(ready = true, elapsedMs = 6000L, minDurationMs = 700L, maxDurationMs = 4000L))
    }

    @Test
    fun `default min and max constants match expected values`() {
        assertEquals(700L, SplashGate.DEFAULT_MIN_MS)
        assertEquals(4_000L, SplashGate.DEFAULT_MAX_MS)
    }

    @Test
    fun `shouldDismiss uses defaults when not specified`() {
        assertFalse(SplashGate.shouldDismiss(ready = true, elapsedMs = 699L))
        assertTrue(SplashGate.shouldDismiss(ready = true, elapsedMs = 700L))
        assertFalse(SplashGate.shouldDismiss(ready = false, elapsedMs = 3999L))
        assertTrue(SplashGate.shouldDismiss(ready = false, elapsedMs = 4000L))
    }
}
