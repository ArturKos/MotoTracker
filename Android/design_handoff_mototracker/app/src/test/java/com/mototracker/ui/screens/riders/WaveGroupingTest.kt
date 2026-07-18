package com.mototracker.ui.screens.riders

import com.mototracker.data.model.Wave
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// ── helpers ───────────────────────────────────────────────────────────────────

private fun wave(
    id: String = "w1",
    shortId: String = "",
    firstSeenMs: Long = 0L,
    lastSeenMs: Long = 0L,
    nick: String = "Nick",
    bikeName: String = "Bike",
    place: String = "Place",
    timeLabel: String = "12:00",
) = Wave(
    id = id,
    nick = nick,
    bikeName = bikeName,
    place = place,
    timeLabel = timeLabel,
    routeId = null,
    shortId = shortId,
    firstSeenMs = firstSeenMs,
    lastSeenMs = lastSeenMs,
)

private val THRESHOLD = 120_000L // 2 minutes

// ─────────────────────────────────────────────────────────────────────────────
// WaveGrouping tests
// ─────────────────────────────────────────────────────────────────────────────

class WaveGroupingTest {

    // ── GRUPO section ─────────────────────────────────────────────────────────

    @Test
    fun `group member wave appears in GRUPO with RODE_TOGETHER and correct duration`() {
        val w = wave(shortId = "ABCD", firstSeenMs = 1_000L, lastSeenMs = 601_000L)
        val sections = WaveGrouping.group(listOf(w), inGroupShortIds = setOf("ABCD"), THRESHOLD)
        assertEquals(1, sections.group.size)
        assertTrue(sections.meetups.isEmpty())
        val entry = sections.group.first()
        assertEquals(WaveKind.RODE_TOGETHER, entry.kind)
        assertEquals(600_000L, entry.durationMs)
    }

    @Test
    fun `multiple waves for same group shortId collapse into one GRUPO entry`() {
        val w1 = wave(id = "w1", shortId = "ABCD", firstSeenMs = 1_000L, lastSeenMs = 200_000L)
        val w2 = wave(id = "w2", shortId = "ABCD", firstSeenMs = 1_000L, lastSeenMs = 700_000L)
        val sections = WaveGrouping.group(listOf(w1, w2), inGroupShortIds = setOf("ABCD"), THRESHOLD)
        assertEquals(1, sections.group.size)
        // picks the one with the highest lastSeenMs
        assertEquals(699_000L, sections.group.first().durationMs)
    }

    // ── SPOTKANIA section — ordering ──────────────────────────────────────────

    @Test
    fun `stranger with 3 encounters yields 3 SPOTKANIA rows`() {
        val waves = listOf(
            wave(id = "w1", shortId = "WXYZ", firstSeenMs = 100_000L, lastSeenMs = 110_000L),
            wave(id = "w2", shortId = "WXYZ", firstSeenMs = 200_000L, lastSeenMs = 210_000L),
            wave(id = "w3", shortId = "WXYZ", firstSeenMs = 300_000L, lastSeenMs = 310_000L),
        )
        val sections = WaveGrouping.group(waves, inGroupShortIds = emptySet(), THRESHOLD)
        assertTrue(sections.group.isEmpty())
        assertEquals(3, sections.meetups.size)
    }

    @Test
    fun `SPOTKANIA rows are ordered newest-first by firstSeenMs`() {
        val waves = listOf(
            wave(id = "w1", shortId = "A001", firstSeenMs = 100_000L, lastSeenMs = 110_000L, place = "P1"),
            wave(id = "w2", shortId = "A002", firstSeenMs = 300_000L, lastSeenMs = 310_000L, place = "P2"),
            wave(id = "w3", shortId = "A003", firstSeenMs = 200_000L, lastSeenMs = 210_000L, place = "P3"),
        )
        val sections = WaveGrouping.group(waves, inGroupShortIds = emptySet(), THRESHOLD)
        // w2 (300k) > w3 (200k) > w1 (100k)
        assertEquals(listOf("P2", "P3", "P1"), sections.meetups.map { it.place })
    }

    // ── SPOTKANIA — kind classification ──────────────────────────────────────

    @Test
    fun `short encounter below threshold is classified as PASS`() {
        val w = wave(shortId = "WXYZ", firstSeenMs = 1_000L, lastSeenMs = 60_000L) // 59 s < 2 min
        val sections = WaveGrouping.group(listOf(w), emptySet(), THRESHOLD)
        assertEquals(WaveKind.PASS, sections.meetups.first().kind)
    }

    @Test
    fun `long encounter at or above threshold is classified as RODE_TOGETHER`() {
        val w = wave(shortId = "WXYZ", firstSeenMs = 1_000L, lastSeenMs = 121_001L) // > 2 min
        val sections = WaveGrouping.group(listOf(w), emptySet(), THRESHOLD)
        assertEquals(WaveKind.RODE_TOGETHER, sections.meetups.first().kind)
    }

    @Test
    fun `encounter exactly at threshold is classified as RODE_TOGETHER`() {
        val w = wave(shortId = "WXYZ", firstSeenMs = 0L, lastSeenMs = THRESHOLD) // exactly 2 min
        val sections = WaveGrouping.group(listOf(w), emptySet(), THRESHOLD)
        assertEquals(WaveKind.RODE_TOGETHER, sections.meetups.first().kind)
    }

    @Test
    fun `legacy row firstSeenMs=0 lastSeenMs=0 is classified as LEGACY with zero duration`() {
        val w = wave(shortId = "", firstSeenMs = 0L, lastSeenMs = 0L)
        val sections = WaveGrouping.group(listOf(w), emptySet(), THRESHOLD)
        assertEquals(WaveKind.LEGACY, sections.meetups.first().kind)
        assertEquals(0L, sections.meetups.first().durationMs)
    }

    @Test
    fun `legacy row with empty shortId lands in SPOTKANIA not GRUPO`() {
        val w = wave(shortId = "", firstSeenMs = 0L, lastSeenMs = 0L)
        val sections = WaveGrouping.group(listOf(w), inGroupShortIds = setOf(""), THRESHOLD)
        // Even if "" were in the set, shortId.isNotEmpty() guard prevents it entering GRUPO
        assertTrue(sections.group.isEmpty())
        assertEquals(1, sections.meetups.size)
    }

    @Test
    fun `inverted timestamps firstSeenMs greater than lastSeenMs treated as LEGACY`() {
        val w = wave(shortId = "WXYZ", firstSeenMs = 500L, lastSeenMs = 100L)
        val sections = WaveGrouping.group(listOf(w), emptySet(), THRESHOLD)
        assertEquals(WaveKind.LEGACY, sections.meetups.first().kind)
        assertEquals(0L, sections.meetups.first().durationMs)
    }

    @Test
    fun `group and stranger waves are partitioned correctly in one call`() {
        val groupWave = wave(id = "g1", shortId = "AAAA", firstSeenMs = 1_000L, lastSeenMs = 700_000L)
        val strangerWave = wave(id = "s1", shortId = "BBBB", firstSeenMs = 1_000L, lastSeenMs = 60_000L)
        val sections = WaveGrouping.group(listOf(groupWave, strangerWave), inGroupShortIds = setOf("AAAA"), THRESHOLD)
        assertEquals(1, sections.group.size)
        assertEquals(1, sections.meetups.size)
        assertEquals(WaveKind.RODE_TOGETHER, sections.group.first().kind)
        assertEquals(WaveKind.PASS, sections.meetups.first().kind)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WaveDuration format tests
// ─────────────────────────────────────────────────────────────────────────────

class WaveDurationTest {

    @Test
    fun `hours and minutes format — 8100000ms = 2h 15m`() {
        assertEquals("2h 15m", WaveDuration.format(8_100_000L))
    }

    @Test
    fun `minutes only — 300000ms = 5m`() {
        assertEquals("5m", WaveDuration.format(300_000L))
    }

    @Test
    fun `exactly one hour — 3600000ms = 1h 0m`() {
        assertEquals("1h 0m", WaveDuration.format(3_600_000L))
    }

    @Test
    fun `zero ms returns 0m`() {
        assertEquals("0m", WaveDuration.format(0L))
    }

    @Test
    fun `negative ms returns 0m`() {
        assertEquals("0m", WaveDuration.format(-1_000L))
    }

    @Test
    fun `59999ms under one minute rounds down to 0m`() {
        assertEquals("0m", WaveDuration.format(59_999L))
    }

    @Test
    fun `60000ms exactly is 1m`() {
        assertEquals("1m", WaveDuration.format(60_000L))
    }

    @Test
    fun `large value — 86400000ms = 24h 0m`() {
        assertEquals("24h 0m", WaveDuration.format(86_400_000L))
    }
}
