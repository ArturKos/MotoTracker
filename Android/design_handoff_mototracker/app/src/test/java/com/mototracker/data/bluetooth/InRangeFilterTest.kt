package com.mototracker.data.bluetooth

import com.mototracker.data.model.Rider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [InRangeFilter].
 *
 * Verifies inclusion/exclusion rules, exact boundary behaviour, sort order, and empty-list safety.
 * No Android types needed — pure JVM.
 */
class InRangeFilterTest {

    private val windowMs = InRangeFilter.DEFAULT_WINDOW_MS

    private fun rider(shortId: String, lastSeenMs: Long) = Rider(
        shortId = shortId,
        nick = "Nick-$shortId",
        bike = "Bike-$shortId",
        lastSeenMs = lastSeenMs,
        inGroup = false,
    )

    // ── Inclusion / exclusion ────────────────────────────────────────────────

    @Test
    fun `rider seen within window is included`() {
        val nowMs = 100_000L
        val r = rider("A1B2", lastSeenMs = nowMs - windowMs + 1)
        val result = InRangeFilter.filter(listOf(r), nowMs)
        assertEquals(1, result.size)
        assertEquals("A1B2", result[0].shortId)
    }

    @Test
    fun `rider seen outside window is excluded`() {
        val nowMs = 100_000L
        val r = rider("A1B2", lastSeenMs = nowMs - windowMs - 1)
        val result = InRangeFilter.filter(listOf(r), nowMs)
        assertTrue("rider outside window must be excluded", result.isEmpty())
    }

    @Test
    fun `rider at exactly the boundary (lastSeenMs == nowMs - windowMs) is included`() {
        val nowMs = 100_000L
        val r = rider("BOUN", lastSeenMs = nowMs - windowMs)
        val result = InRangeFilter.filter(listOf(r), nowMs)
        assertEquals("rider at exact boundary must be included", 1, result.size)
    }

    // ── Sort order ───────────────────────────────────────────────────────────

    @Test
    fun `results are sorted most-recently-seen first`() {
        val nowMs = 100_000L
        val r1 = rider("AA11", lastSeenMs = nowMs - 5_000L)
        val r2 = rider("BB22", lastSeenMs = nowMs - 1_000L)
        val r3 = rider("CC33", lastSeenMs = nowMs - 10_000L)
        val result = InRangeFilter.filter(listOf(r1, r2, r3), nowMs)
        assertEquals(3, result.size)
        assertEquals("BB22", result[0].shortId)
        assertEquals("AA11", result[1].shortId)
        assertEquals("CC33", result[2].shortId)
    }

    // ── Empty list ───────────────────────────────────────────────────────────

    @Test
    fun `empty rider list returns empty result`() {
        val result = InRangeFilter.filter(emptyList(), nowMs = 100_000L)
        assertTrue(result.isEmpty())
    }

    // ── Mixed in/out ─────────────────────────────────────────────────────────

    @Test
    fun `only in-range riders are returned from a mixed list`() {
        val nowMs = 100_000L
        val inRange = rider("IN01", lastSeenMs = nowMs - 1_000L)
        val outOfRange = rider("OUT1", lastSeenMs = nowMs - windowMs - 1_000L)
        val result = InRangeFilter.filter(listOf(inRange, outOfRange), nowMs)
        assertEquals(1, result.size)
        assertEquals("IN01", result[0].shortId)
    }

    // ── Custom window ─────────────────────────────────────────────────────────

    @Test
    fun `custom window overrides default`() {
        val nowMs = 100_000L
        val customWindowMs = 5_000L
        val inRange = rider("IN01", lastSeenMs = nowMs - 4_000L)
        val outOfRange = rider("OUT1", lastSeenMs = nowMs - 6_000L)
        val result = InRangeFilter.filter(listOf(inRange, outOfRange), nowMs, customWindowMs)
        assertEquals(1, result.size)
        assertEquals("IN01", result[0].shortId)
    }
}
