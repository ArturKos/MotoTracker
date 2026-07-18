package com.mototracker.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsRecipientCodecTest {

    // ── Round-trip basic ──────────────────────────────────────────────────────

    @Test
    fun `encode then decode single recipient round-trips`() {
        val original = listOf(SmsRecipient("Alice", "+48100000001"))
        val encoded = SmsRecipientCodec.encode(original)
        val decoded = SmsRecipientCodec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `encode then decode multiple recipients round-trips`() {
        val original = listOf(
            SmsRecipient("Alice", "+48100000001"),
            SmsRecipient("Bob", "+48100000002"),
            SmsRecipient("Carol", "+48100000003"),
        )
        val encoded = SmsRecipientCodec.encode(original)
        val decoded = SmsRecipientCodec.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `empty list encodes to empty string and decodes to empty list`() {
        val encoded = SmsRecipientCodec.encode(emptyList())
        assertTrue(encoded.isEmpty())
        val decoded = SmsRecipientCodec.decode(encoded)
        assertTrue(decoded.isEmpty())
    }

    // ── Delimiter-in-value ────────────────────────────────────────────────────

    @Test
    fun `pipe character in name survives round-trip`() {
        val original = listOf(SmsRecipient("Alice|Bob", "+48100000001"))
        val decoded = SmsRecipientCodec.decode(SmsRecipientCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `pipe character in number survives round-trip`() {
        val original = listOf(SmsRecipient("Alice", "+1|23"))
        val decoded = SmsRecipientCodec.decode(SmsRecipientCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `backslash in name survives round-trip`() {
        val original = listOf(SmsRecipient("C:\\User", "+1"))
        val decoded = SmsRecipientCodec.decode(SmsRecipientCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `backslash in number survives round-trip`() {
        val original = listOf(SmsRecipient("Alice", "+1\\2"))
        val decoded = SmsRecipientCodec.decode(SmsRecipientCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `newline in name survives round-trip`() {
        val original = listOf(SmsRecipient("Alice\nSmith", "+1"))
        val decoded = SmsRecipientCodec.decode(SmsRecipientCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `pipe and backslash combination in name survives round-trip`() {
        val original = listOf(SmsRecipient("A|B\\C", "+1"))
        val decoded = SmsRecipientCodec.decode(SmsRecipientCodec.encode(original))
        assertEquals(original, decoded)
    }

    // ── Malformed / empty input ───────────────────────────────────────────────

    @Test
    fun `blank string decodes to empty list`() {
        assertTrue(SmsRecipientCodec.decode("").isEmpty())
    }

    @Test
    fun `whitespace-only string decodes to empty list`() {
        assertTrue(SmsRecipientCodec.decode("   ").isEmpty())
    }

    @Test
    fun `row with no field separator is skipped`() {
        val result = SmsRecipientCodec.decode("no-separator-here")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `one valid and one malformed row yields only the valid recipient`() {
        val validRow = SmsRecipientCodec.encode(listOf(SmsRecipient("Alice", "+1")))
        val mixed = "$validRow\nmalformedrow"
        val result = SmsRecipientCodec.decode(mixed)
        assertEquals(1, result.size)
        assertEquals("Alice", result[0].name)
    }

    // ── Order preservation ────────────────────────────────────────────────────

    @Test
    fun `decode preserves insertion order`() {
        val original = listOf(
            SmsRecipient("Zebra", "+3"),
            SmsRecipient("Alpha", "+1"),
            SmsRecipient("Mango", "+2"),
        )
        val decoded = SmsRecipientCodec.decode(SmsRecipientCodec.encode(original))
        assertEquals("Zebra", decoded[0].name)
        assertEquals("Alpha", decoded[1].name)
        assertEquals("Mango", decoded[2].name)
    }

    // ── Empty name/number ─────────────────────────────────────────────────────

    @Test
    fun `empty name and number round-trip correctly`() {
        val original = listOf(SmsRecipient("", ""))
        val decoded = SmsRecipientCodec.decode(SmsRecipientCodec.encode(original))
        assertEquals(original, decoded)
    }
}
