package com.mototracker.data.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WavePayloadCodecTest {

    // ── Round-trip equality ───────────────────────────────────────────────────

    @Test
    fun `round-trip ASCII payload preserves all fields exactly`() {
        val payload = WavePayload(shortId = "AB12", nick = "Rider", bike = "MT-07")
        val encoded = WavePayloadCodec.encode(payload)
        val decoded = WavePayloadCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(payload, decoded)
    }

    @Test
    fun `round-trip with empty nick and bike`() {
        val payload = WavePayload(shortId = "XY99", nick = "", bike = "")
        val encoded = WavePayloadCodec.encode(payload)
        val decoded = WavePayloadCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(payload, decoded)
    }

    @Test
    fun `round-trip with UTF-8 multibyte nick and bike`() {
        val payload = WavePayload(shortId = "ΑΒΓΔ", nick = "Józef", bike = "Ямаха")
        val encoded = WavePayloadCodec.encode(payload)
        assertTrue("Encoded size ≤ MAX_PAYLOAD_BYTES", encoded.size <= WavePayloadCodec.MAX_PAYLOAD_BYTES)
        val decoded = WavePayloadCodec.decode(encoded)
        assertNotNull(decoded)
        // Decoded shortId, nick, bike must start with the encoded content (may be truncated)
        assertNotNull(decoded!!.shortId)
    }

    // ── Truncation ────────────────────────────────────────────────────────────

    @Test
    fun `oversized nick and bike are truncated to MAX_PAYLOAD_BYTES`() {
        val longNick = "N".repeat(100)
        val longBike = "B".repeat(100)
        val payload = WavePayload(shortId = "ABCD", nick = longNick, bike = longBike)
        val encoded = WavePayloadCodec.encode(payload)
        assertTrue("Encoded must be ≤ ${WavePayloadCodec.MAX_PAYLOAD_BYTES} bytes",
            encoded.size <= WavePayloadCodec.MAX_PAYLOAD_BYTES)
    }

    @Test
    fun `oversized payload decodes to non-null with truncated fields`() {
        val payload = WavePayload(shortId = "ABCD", nick = "N".repeat(100), bike = "B".repeat(100))
        val encoded = WavePayloadCodec.encode(payload)
        val decoded = WavePayloadCodec.decode(encoded)
        assertNotNull("Truncated payload must still decode", decoded)
        assertEquals("shortId unchanged", "ABCD", decoded!!.shortId)
        // Nick gets first priority; bike may be empty when nick fills the budget
        assertTrue("Decoded nick starts with 'N'", decoded.nick.startsWith("N"))
    }

    @Test
    fun `nick fills budget when bike would overflow`() {
        // shortIdLen=4, overhead=3, budget=19; nick of exactly 19 bytes leaves 0 for bike
        val shortId = "ABCD"
        val nick = "N".repeat(19)
        val bike = "B".repeat(50)
        val payload = WavePayload(shortId = shortId, nick = nick, bike = bike)
        val encoded = WavePayloadCodec.encode(payload)
        assertTrue(encoded.size <= WavePayloadCodec.MAX_PAYLOAD_BYTES)
        val decoded = WavePayloadCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(nick, decoded!!.nick)
        assertEquals("", decoded.bike)
    }

    @Test
    fun `utf8 multibyte truncation does not split code points`() {
        // Fill with 2-byte UTF-8 chars (α = 0xCEAB); check decoded round-trips cleanly
        val nick = "α".repeat(20)  // each 'α' is 2 bytes → 40 bytes raw
        val payload = WavePayload(shortId = "ABCD", nick = nick, bike = "")
        val encoded = WavePayloadCodec.encode(payload)
        assertTrue(encoded.size <= WavePayloadCodec.MAX_PAYLOAD_BYTES)
        val decoded = WavePayloadCodec.decode(encoded)
        assertNotNull(decoded)
        // Decoded nick should be a valid string of α characters, never half a char
        assertTrue("Decoded nick should consist of α chars only",
            decoded!!.nick.all { it == 'α' })
    }

    @Test
    fun `2-byte Cyrillic char at exact budget boundary round-trips`() {
        // shortId = 21 ASCII bytes → budget for nick+bike = 26 − 3 − 21 = 2 bytes.
        // Cyrillic 'А' (U+0410) encodes as 2 bytes (0xD0 0x90).
        // nick = "АX" (3 bytes raw) must be truncated to "А" (2 bytes), not empty.
        // This is the boundary that the previous truncateUtf8 implementation got wrong
        // by unconditionally dropping the lead byte when continuations were backed over.
        val shortId = "AAAAAAAAAAAAAAAAAAAAA" // 21 ASCII chars = 21 bytes
        val payload = WavePayload(shortId = shortId, nick = "АX", bike = "")
        val encoded = WavePayloadCodec.encode(payload)
        assertTrue("Encoded size ≤ MAX_PAYLOAD_BYTES", encoded.size <= WavePayloadCodec.MAX_PAYLOAD_BYTES)
        val decoded = WavePayloadCodec.decode(encoded)
        assertNotNull("Truncated Cyrillic payload must decode", decoded)
        assertEquals("Cyrillic lead byte kept when sequence fits in budget", "А", decoded!!.nick)
    }

    // ── Malformed inputs return null ──────────────────────────────────────────

    @Test
    fun `empty array returns null`() {
        assertNull(WavePayloadCodec.decode(ByteArray(0)))
    }

    @Test
    fun `single byte array returns null`() {
        assertNull(WavePayloadCodec.decode(byteArrayOf(0x01)))
    }

    @Test
    fun `wrong version byte returns null`() {
        // Version 0x02 is unknown
        assertNull(WavePayloadCodec.decode(byteArrayOf(0x02, 0x00, 0x00)))
    }

    @Test
    fun `shortIdLen overruns array returns null`() {
        // shortIdLen = 10 but only 3 bytes follow (positions 2..4)
        val data = byteArrayOf(0x01, 0x0A, 0x00, 0x00, 0x00)
        assertNull(WavePayloadCodec.decode(data))
    }

    @Test
    fun `nickLen overruns array returns null`() {
        // shortIdLen=0, nickLen=20 but no bytes follow for nick
        val data = byteArrayOf(0x01, 0x00, 0x14)  // 0x14 = 20
        assertNull(WavePayloadCodec.decode(data))
    }

    @Test
    fun `missing nickLen byte returns null`() {
        // [version, shortIdLen=0] — no nickLen byte present
        val data = byteArrayOf(0x01, 0x00)
        assertNull(WavePayloadCodec.decode(data))
    }

    // ── Encoding sanity ───────────────────────────────────────────────────────

    @Test
    fun `version byte is 0x01`() {
        val encoded = WavePayloadCodec.encode(WavePayload("AB12", "X", "Y"))
        assertEquals(0x01.toByte(), encoded[0])
    }

    @Test
    fun `encode respects MAX_PAYLOAD_BYTES for maximum-length ascii inputs`() {
        val payload = WavePayload(
            shortId = "ABCD",
            nick = "A".repeat(50),
            bike = "B".repeat(50),
        )
        val encoded = WavePayloadCodec.encode(payload)
        assertTrue(encoded.size <= WavePayloadCodec.MAX_PAYLOAD_BYTES)
    }
}
