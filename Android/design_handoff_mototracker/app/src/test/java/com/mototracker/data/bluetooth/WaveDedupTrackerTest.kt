package com.mototracker.data.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WaveDedupTrackerTest {

    private lateinit var tracker: WaveDedupTracker

    @Before
    fun setUp() {
        // 10-second window for deterministic, fast tests
        tracker = WaveDedupTracker(windowMs = 10_000L)
    }

    // ── First sighting ────────────────────────────────────────────────────────

    @Test
    fun `first sighting of an id is accepted`() {
        assertTrue(tracker.accept("A1B2", nowMs = 0L))
    }

    @Test
    fun `first sighting of each id is accepted independently`() {
        assertTrue(tracker.accept("A1B2", nowMs = 0L))
        assertTrue(tracker.accept("C3D4", nowMs = 0L))
        assertTrue(tracker.accept("ZZZZ", nowMs = 0L))
    }

    // ── Dedup within window ───────────────────────────────────────────────────

    @Test
    fun `immediate repeat within window is rejected`() {
        tracker.accept("A1B2", nowMs = 0L)
        assertFalse(tracker.accept("A1B2", nowMs = 1_000L))
    }

    @Test
    fun `repeat just inside the window boundary is rejected`() {
        tracker.accept("A1B2", nowMs = 0L)
        assertFalse(tracker.accept("A1B2", nowMs = 9_999L))
    }

    @Test
    fun `repeat at exactly the window edge is still within window`() {
        tracker.accept("A1B2", nowMs = 0L)
        // nowMs - last = 9_999 < windowMs (10_000) → rejected
        assertFalse(tracker.accept("A1B2", nowMs = 9_999L))
    }

    // ── Accept after window ───────────────────────────────────────────────────

    @Test
    fun `repeat after window elapses is accepted`() {
        tracker.accept("A1B2", nowMs = 0L)
        assertTrue(tracker.accept("A1B2", nowMs = 10_000L))
    }

    @Test
    fun `repeat well after window is accepted`() {
        tracker.accept("A1B2", nowMs = 0L)
        assertTrue(tracker.accept("A1B2", nowMs = 60_000L))
    }

    @Test
    fun `acceptance resets the window for subsequent repeats`() {
        tracker.accept("A1B2", nowMs = 0L)
        tracker.accept("A1B2", nowMs = 10_000L)   // accepted → resets timer to 10_000
        assertFalse(tracker.accept("A1B2", nowMs = 15_000L))  // 5s since reset → reject
        assertTrue(tracker.accept("A1B2", nowMs = 20_000L))   // 10s since reset → accept
    }

    // ── Distinct ids are independent ──────────────────────────────────────────

    @Test
    fun `dedup of one id does not affect another id`() {
        tracker.accept("ID01", nowMs = 0L)
        tracker.accept("ID01", nowMs = 1_000L)   // reject (within window)

        // ID02 is independent; first sighting must be accepted
        assertTrue(tracker.accept("ID02", nowMs = 1_000L))
    }

    @Test
    fun `many ids tracked independently`() {
        val ids = (1..20).map { "ID%02d".format(it) }
        ids.forEach { assertTrue(tracker.accept(it, nowMs = 0L)) }
        // Immediate repeat of each should be rejected
        ids.forEach { assertFalse(tracker.accept(it, nowMs = 500L)) }
        // After window all should be accepted again
        ids.forEach { assertTrue(tracker.accept(it, nowMs = 10_000L)) }
    }

    // ── Default window ────────────────────────────────────────────────────────

    @Test
    fun `default window is five minutes`() {
        val defaultTracker = WaveDedupTracker()
        defaultTracker.accept("AA11", nowMs = 0L)
        assertFalse(defaultTracker.accept("AA11", nowMs = 299_999L))  // 4m 59.999s → reject
        assertTrue(defaultTracker.accept("AA11", nowMs = 300_000L))   // exactly 5m → accept
    }
}
