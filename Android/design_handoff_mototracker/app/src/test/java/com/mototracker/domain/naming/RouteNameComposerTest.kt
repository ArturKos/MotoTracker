package com.mototracker.domain.naming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class RouteNameComposerTest {

    // ── partOfDay boundaries ──────────────────────────────────────────────────

    @Test
    fun `04-59 is NIGHT`() = assertPod(hour = 4, minute = 59, expected = PartOfDay.NIGHT)

    @Test
    fun `05-00 is MORNING`() = assertPod(hour = 5, minute = 0, expected = PartOfDay.MORNING)

    @Test
    fun `11-59 is MORNING`() = assertPod(hour = 11, minute = 59, expected = PartOfDay.MORNING)

    @Test
    fun `12-00 is AFTERNOON`() = assertPod(hour = 12, minute = 0, expected = PartOfDay.AFTERNOON)

    @Test
    fun `17-59 is AFTERNOON`() = assertPod(hour = 17, minute = 59, expected = PartOfDay.AFTERNOON)

    @Test
    fun `18-00 is EVENING`() = assertPod(hour = 18, minute = 0, expected = PartOfDay.EVENING)

    @Test
    fun `21-59 is EVENING`() = assertPod(hour = 21, minute = 59, expected = PartOfDay.EVENING)

    @Test
    fun `22-00 is NIGHT`() = assertPod(hour = 22, minute = 0, expected = PartOfDay.NIGHT)

    @Test
    fun `00-00 is NIGHT`() = assertPod(hour = 0, minute = 0, expected = PartOfDay.NIGHT)

    @Test
    fun `23-59 is NIGHT`() = assertPod(hour = 23, minute = 59, expected = PartOfDay.NIGHT)

    // ── compose ───────────────────────────────────────────────────────────────

    @Test
    fun `compose with non-blank area returns area-label template`() {
        val result = RouteNameComposer.compose(
            rideLabel = "afternoon ride",
            area = "Szczecin",
            template = "%1\$s – %2\$s",
        )
        assertEquals("Szczecin – afternoon ride", result)
    }

    @Test
    fun `compose with null area returns rideLabel only`() {
        val result = RouteNameComposer.compose(
            rideLabel = "morning ride",
            area = null,
            template = "%1\$s – %2\$s",
        )
        assertEquals("morning ride", result)
    }

    @Test
    fun `compose with blank area returns rideLabel only`() {
        val result = RouteNameComposer.compose(
            rideLabel = "evening ride",
            area = "   ",
            template = "%1\$s – %2\$s",
        )
        assertEquals("evening ride", result)
    }

    @Test
    fun `compose with empty area returns rideLabel only`() {
        val result = RouteNameComposer.compose(
            rideLabel = "night ride",
            area = "",
            template = "%1\$s – %2\$s",
        )
        assertEquals("night ride", result)
    }

    // ── dominantArea ──────────────────────────────────────────────────────────

    @Test
    fun `dominantArea returns most frequent area`() {
        val result = RouteNameComposer.dominantArea(listOf("Szczecin", "Warsaw", "Szczecin"))
        assertEquals("Szczecin", result)
    }

    @Test
    fun `dominantArea resolves tie by first-seen order`() {
        val result = RouteNameComposer.dominantArea(listOf("Warsaw", "Szczecin", "Warsaw", "Szczecin"))
        assertEquals("Warsaw", result)
    }

    @Test
    fun `dominantArea returns null when all entries are null`() {
        assertNull(RouteNameComposer.dominantArea(listOf(null, null)))
    }

    @Test
    fun `dominantArea ignores null and blank entries`() {
        val result = RouteNameComposer.dominantArea(listOf(null, "", "Kraków", null, "Kraków"))
        assertEquals("Kraków", result)
    }

    @Test
    fun `dominantArea returns null for empty list`() {
        assertNull(RouteNameComposer.dominantArea(emptyList()))
    }

    @Test
    fun `dominantArea returns single element when list has one valid entry`() {
        val result = RouteNameComposer.dominantArea(listOf(null, "Gdańsk", null))
        assertEquals("Gdańsk", result)
    }

    // ── helper ────────────────────────────────────────────────────────────────

    /**
     * Builds an epoch-ms timestamp at [hour]:[minute] UTC, then asserts [RouteNameComposer.partOfDay]
     * returns [expected] in UTC.
     */
    private fun assertPod(hour: Int, minute: Int, expected: PartOfDay) {
        // 2024-01-01 at the given hour/minute in UTC
        val epochMs = java.time.LocalDateTime.of(2024, 1, 1, hour, minute)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        assertEquals(expected, RouteNameComposer.partOfDay(epochMs, ZoneOffset.UTC))
    }
}
