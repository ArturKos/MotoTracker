package com.mototracker.data.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SightingThrottle].
 *
 * Verifies that the first sighting is accepted, within-window duplicates are suppressed,
 * and sightings after the window elapses are accepted again.
 */
class SightingThrottleTest {

    private lateinit var throttle: SightingThrottle

    @Before
    fun setUp() {
        // 3-second window for deterministic tests
        throttle = SightingThrottle(windowMs = 3_000L)
    }

    // ── First sighting ────────────────────────────────────────────────────────

    @Test
    fun `first sighting is accepted`() {
        assertTrue(throttle.accept("A1B2", nowMs = 0L))
    }

    @Test
    fun `first sightings of distinct ids are all accepted`() {
        assertTrue(throttle.accept("A1B2", nowMs = 0L))
        assertTrue(throttle.accept("C3D4", nowMs = 0L))
        assertTrue(throttle.accept("ZZZZ", nowMs = 0L))
    }

    // ── Within-window suppression ─────────────────────────────────────────────

    @Test
    fun `immediate repeat within window is suppressed`() {
        throttle.accept("A1B2", nowMs = 0L)
        assertFalse(throttle.accept("A1B2", nowMs = 500L))
    }

    @Test
    fun `repeat just inside window boundary is suppressed`() {
        throttle.accept("A1B2", nowMs = 0L)
        assertFalse(throttle.accept("A1B2", nowMs = 2_999L))
    }

    @Test
    fun `repeat at exactly window ms is suppressed`() {
        // nowMs - last = 2_999 < windowMs (3_000) → suppressed
        throttle.accept("A1B2", nowMs = 0L)
        assertFalse("sighting at window - 1 ms must be suppressed", throttle.accept("A1B2", nowMs = 2_999L))
    }

    // ── After-window acceptance ───────────────────────────────────────────────

    @Test
    fun `sighting after window elapses is accepted`() {
        throttle.accept("A1B2", nowMs = 0L)
        assertTrue(throttle.accept("A1B2", nowMs = 3_000L))
    }

    @Test
    fun `sighting well after window is accepted`() {
        throttle.accept("A1B2", nowMs = 0L)
        assertTrue(throttle.accept("A1B2", nowMs = 30_000L))
    }

    @Test
    fun `acceptance resets the window for subsequent calls`() {
        throttle.accept("A1B2", nowMs = 0L)
        throttle.accept("A1B2", nowMs = 3_000L)   // accepted → resets to 3_000
        assertFalse(throttle.accept("A1B2", nowMs = 5_000L))  // 2s since reset → suppressed
        assertTrue(throttle.accept("A1B2", nowMs = 6_000L))   // 3s since reset → accepted
    }

    // ── Ids are independent ───────────────────────────────────────────────────

    @Test
    fun `throttle of one id does not affect another id`() {
        throttle.accept("ID01", nowMs = 0L)
        throttle.accept("ID01", nowMs = 1_000L) // suppressed

        assertTrue("ID02 first sighting must be accepted", throttle.accept("ID02", nowMs = 1_000L))
    }

    // ── Default window ────────────────────────────────────────────────────────

    @Test
    fun `default window is 3 seconds`() {
        val defaultThrottle = SightingThrottle()
        defaultThrottle.accept("AA11", nowMs = 0L)
        assertFalse(defaultThrottle.accept("AA11", nowMs = 2_999L))
        assertTrue(defaultThrottle.accept("AA11", nowMs = 3_000L))
    }
}
