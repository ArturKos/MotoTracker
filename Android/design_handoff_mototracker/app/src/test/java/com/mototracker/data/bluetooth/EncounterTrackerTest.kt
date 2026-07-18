package com.mototracker.data.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [EncounterTracker].
 *
 * Covers encounter opening, continuation, gap-based splitting, inGroup infinite-gap,
 * and exact boundary behaviour. All timestamps are synthetic wall-clock milliseconds.
 */
class EncounterTrackerTest {

    private lateinit var tracker: EncounterTracker

    @Before
    fun setUp() {
        tracker = EncounterTracker()
    }

    // ── First sighting ──────────────────────────────────────────────────────────

    @Test
    fun `first sighting of a shortId starts a new encounter`() {
        val event = tracker.onSighting("A1B2", nowMs = 1_000L, gapMs = 10 * 60_000L)
        assertTrue("first sighting must be Started", event is EncounterEvent.Started)
        assertEquals("A1B2", event.shortId)
        assertEquals(1_000L, event.atMs)
    }

    // ── Companion sub-gap — exactly 1 encounter ─────────────────────────────────

    @Test
    fun `companion with dropouts within gap produces exactly 1 Started event`() {
        val gapMs = 10 * 60_000L
        var startedCount = 0

        // t=0 first sighting
        tracker.onSighting("COMP", nowMs = 0L, gapMs = gapMs).also {
            if (it is EncounterEvent.Started) startedCount++
        }
        // t=4 min — within gap
        tracker.onSighting("COMP", nowMs = 4 * 60_000L, gapMs = gapMs).also {
            if (it is EncounterEvent.Started) startedCount++
        }
        // t=9 min — still within gap (9 < 10)
        tracker.onSighting("COMP", nowMs = 9 * 60_000L, gapMs = gapMs).also {
            if (it is EncounterEvent.Started) startedCount++
        }

        assertEquals("companion within gap must produce exactly 1 Started event", 1, startedCount)
    }

    @Test
    fun `sightings within gap return Extended`() {
        val gapMs = 10 * 60_000L
        tracker.onSighting("COMP", nowMs = 0L, gapMs = gapMs)
        val ext = tracker.onSighting("COMP", nowMs = 5 * 60_000L, gapMs = gapMs)
        assertTrue("within-gap sighting must be Extended", ext is EncounterEvent.Extended)
        assertEquals("COMP", ext.shortId)
        assertEquals(5 * 60_000L, ext.atMs)
    }

    // ── Stranger with >gap absence → N Started events ───────────────────────────

    @Test
    fun `stranger seen twice with gap exceeded produces 2 Started events`() {
        val gapMs = 10 * 60_000L
        val e1 = tracker.onSighting("STRG", nowMs = 0L, gapMs = gapMs)
        // Gap: 11 min > 10 min → new encounter
        val e2 = tracker.onSighting("STRG", nowMs = 11 * 60_000L, gapMs = gapMs)

        assertTrue("first sighting must be Started", e1 is EncounterEvent.Started)
        assertTrue("post-gap sighting must be Started", e2 is EncounterEvent.Started)
    }

    @Test
    fun `multiple gap-exceeding absences produce N Started events`() {
        val gapMs = 10 * 60_000L
        val times = listOf(0L, 11 * 60_000L, 22 * 60_000L, 33 * 60_000L)
        val events = times.map { tracker.onSighting("STRG", it, gapMs) }
        val startedCount = events.count { it is EncounterEvent.Started }
        assertEquals("each gap-exceeding visit is a new encounter", 4, startedCount)
    }

    // ── inGroup (gap = Long.MAX_VALUE) → 1 encounter across long absences ───────

    @Test
    fun `inGroup rider with Long MAX_VALUE gap never splits`() {
        val gapMs = Long.MAX_VALUE
        var startedCount = 0

        listOf(0L, 60 * 60_000L, 5 * 60 * 60_000L, 24 * 60 * 60_000L).forEach { t ->
            val event = tracker.onSighting("GRP1", nowMs = t, gapMs = gapMs)
            if (event is EncounterEvent.Started) startedCount++
        }

        assertEquals("inGroup rider must produce exactly 1 Started across any absences", 1, startedCount)
    }

    // ── Boundary: gap exactly == threshold ──────────────────────────────────────

    @Test
    fun `sighting at exactly gap threshold (nowMs - lastSeen == gapMs) is Extended not Started`() {
        // Rule: > gapMs → new encounter; == gapMs → still extended
        val gapMs = 10 * 60_000L
        tracker.onSighting("BOUN", nowMs = 0L, gapMs = gapMs)
        val event = tracker.onSighting("BOUN", nowMs = gapMs, gapMs = gapMs)
        assertTrue(
            "sighting at exactly the gap threshold must be Extended (not Started)",
            event is EncounterEvent.Extended,
        )
    }

    @Test
    fun `sighting one ms beyond gap threshold starts new encounter`() {
        val gapMs = 10 * 60_000L
        tracker.onSighting("BOUN", nowMs = 0L, gapMs = gapMs)
        val event = tracker.onSighting("BOUN", nowMs = gapMs + 1L, gapMs = gapMs)
        assertTrue(
            "sighting one ms past the gap must be Started",
            event is EncounterEvent.Started,
        )
    }

    // ── Multiple shortIds are independent ────────────────────────────────────────

    @Test
    fun `encounters for different shortIds are independent`() {
        val gapMs = 10 * 60_000L
        val e1 = tracker.onSighting("AA11", nowMs = 0L, gapMs = gapMs)
        val e2 = tracker.onSighting("BB22", nowMs = 0L, gapMs = gapMs)
        assertTrue(e1 is EncounterEvent.Started)
        assertTrue(e2 is EncounterEvent.Started)

        // AA11 re-appears within gap → Extended; BB22 stays independent
        val e3 = tracker.onSighting("AA11", nowMs = 5 * 60_000L, gapMs = gapMs)
        val e4 = tracker.onSighting("BB22", nowMs = 15 * 60_000L, gapMs = gapMs)
        assertTrue("AA11 within gap must be Extended", e3 is EncounterEvent.Extended)
        assertTrue("BB22 with gap exceeded must be Started", e4 is EncounterEvent.Started)
    }

    // ── reset() clears state ─────────────────────────────────────────────────────

    @Test
    fun `reset clears all encounters so next sighting starts fresh`() {
        val gapMs = 10 * 60_000L
        tracker.onSighting("R1", nowMs = 0L, gapMs = gapMs)
        tracker.reset()
        val event = tracker.onSighting("R1", nowMs = 1_000L, gapMs = gapMs)
        assertTrue("after reset a sighting must be Started", event is EncounterEvent.Started)
    }
}
