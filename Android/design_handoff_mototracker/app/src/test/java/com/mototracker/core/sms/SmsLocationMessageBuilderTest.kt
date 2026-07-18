package com.mototracker.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsLocationMessageBuilderTest {

    private val aliceBob = listOf(
        SmsRecipient("Alice", "+48100000001"),
        SmsRecipient("Bob", "+48100000002"),
    )

    // ── Empty recipients ──────────────────────────────────────────────────────

    @Test
    fun `empty recipients list returns empty output`() {
        val result = SmsLocationMessageBuilder.build(emptyList(), 52.0, 21.0, "template %s")
        assertTrue(result.isEmpty())
    }

    // ── Multiple recipients ───────────────────────────────────────────────────

    @Test
    fun `one message is produced per recipient`() {
        val result = SmsLocationMessageBuilder.build(aliceBob, 52.0, 21.0, "Hello %s")
        assertEquals(2, result.size)
    }

    @Test
    fun `phone numbers are routed to the correct recipients`() {
        val result = SmsLocationMessageBuilder.build(aliceBob, 52.0, 21.0, "Hi %s")
        assertEquals("+48100000001", result[0].number)
        assertEquals("+48100000002", result[1].number)
    }

    // ── %s substitution ───────────────────────────────────────────────────────

    @Test
    fun `percent-s placeholder is replaced by the Google Maps URL`() {
        val result = SmsLocationMessageBuilder.build(
            listOf(SmsRecipient("X", "+1")),
            50.0, 20.0,
            "MotoTracker: jestem tu %s",
        )
        val expectedUrl = "https://www.google.com/maps/search/?api=1&query=50.000000,20.000000"
        assertEquals("MotoTracker: jestem tu $expectedUrl", result[0].text)
    }

    @Test
    fun `all messages share the same body text`() {
        val result = SmsLocationMessageBuilder.build(aliceBob, 52.0, 21.0, "Hi %s")
        assertEquals(result[0].text, result[1].text)
    }

    // ── No placeholder — URL appended ─────────────────────────────────────────

    @Test
    fun `template without percent-s has URL appended after a space`() {
        val result = SmsLocationMessageBuilder.build(
            listOf(SmsRecipient("X", "+1")),
            50.0, 20.0,
            "No placeholder here",
        )
        val expectedUrl = "https://www.google.com/maps/search/?api=1&query=50.000000,20.000000"
        assertEquals("No placeholder here $expectedUrl", result[0].text)
    }

    @Test
    fun `empty template with no placeholder results in URL preceded by a space`() {
        val result = SmsLocationMessageBuilder.build(
            listOf(SmsRecipient("X", "+1")),
            0.0, 0.0,
            "",
        )
        val expectedUrl = "https://www.google.com/maps/search/?api=1&query=0.000000,0.000000"
        assertEquals(" $expectedUrl", result[0].text)
    }

    // ── Correct Google Maps URL via CoordinateClipboard ───────────────────────

    @Test
    fun `URL uses dot decimal separator regardless of locale`() {
        val result = SmsLocationMessageBuilder.build(
            listOf(SmsRecipient("X", "+1")),
            52.123456, 21.654321,
            "%s",
        )
        assertTrue(result[0].text.contains("query=52.123456,21.654321"))
    }

    @Test
    fun `URL is a google maps search URL`() {
        val result = SmsLocationMessageBuilder.build(
            listOf(SmsRecipient("X", "+1")),
            10.0, 20.0,
            "%s",
        )
        assertTrue(result[0].text.startsWith("https://www.google.com/maps/search/"))
    }

    @Test
    fun `URL contains api=1 parameter`() {
        val result = SmsLocationMessageBuilder.build(
            listOf(SmsRecipient("X", "+1")),
            10.0, 20.0,
            "%s",
        )
        assertTrue(result[0].text.contains("api=1"))
    }

    // ── Coordinate precision ──────────────────────────────────────────────────

    @Test
    fun `coordinates are formatted with 6 decimal places`() {
        val result = SmsLocationMessageBuilder.build(
            listOf(SmsRecipient("X", "+1")),
            1.0, 2.0,
            "%s",
        )
        assertTrue(result[0].text.contains("query=1.000000,2.000000"))
    }
}
