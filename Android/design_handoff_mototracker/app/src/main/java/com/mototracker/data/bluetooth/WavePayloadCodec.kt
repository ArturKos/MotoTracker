package com.mototracker.data.bluetooth

import java.util.UUID

/**
 * Encodes and decodes [WavePayload] to/from a compact byte array suitable for
 * BLE advertisement service data (max 26 bytes including the 2-byte service UUID overhead).
 *
 * Wire format (big-endian offsets, all lengths in bytes):
 * ```
 * [version:1 = 0x01][shortIdLen:1][shortId:shortIdLen][nickLen:1][nick:nickLen][bike:remainder]
 * ```
 * Total size is always ≤ [MAX_PAYLOAD_BYTES]. If the combined nick and bike fields exceed the
 * available budget, nick is first truncated at a valid UTF-8 boundary, then bike gets
 * whatever space remains (which may be zero).
 */
object WavePayloadCodec {

    /** Maximum encoded payload size in bytes. */
    const val MAX_PAYLOAD_BYTES = 26

    /**
     * BLE service UUID that identifies MotoTracker wave advertisements.
     * All scanner filters use this UUID so only MotoTracker payloads are decoded.
     */
    @JvmField
    val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    private const val VERSION: Byte = 0x01

    /**
     * Encodes [payload] to a byte array of at most [MAX_PAYLOAD_BYTES].
     *
     * nick and bike are truncated at valid UTF-8 code-point boundaries if the total
     * would otherwise exceed the limit. shortId is always encoded in full (a 4-char
     * ASCII id never exceeds the available budget).
     *
     * @param payload The rider info to encode.
     * @return Encoded byte array, length ≤ [MAX_PAYLOAD_BYTES].
     */
    fun encode(payload: WavePayload): ByteArray {
        val shortIdBytes = payload.shortId.toByteArray(Charsets.UTF_8)
        val shortIdLen = shortIdBytes.size.coerceAtMost(255)
        // 3 overhead bytes: version byte + shortIdLen byte + nickLen byte
        val budget = (MAX_PAYLOAD_BYTES - 3 - shortIdLen).coerceAtLeast(0)
        val nickBytes = truncateUtf8(payload.nick, budget)
        val bikeBytes = truncateUtf8(payload.bike, (budget - nickBytes.size).coerceAtLeast(0))

        val out = ByteArray(1 + 1 + shortIdLen + 1 + nickBytes.size + bikeBytes.size)
        var pos = 0
        out[pos++] = VERSION
        out[pos++] = shortIdLen.toByte()
        shortIdBytes.copyInto(out, pos, 0, shortIdLen); pos += shortIdLen
        out[pos++] = nickBytes.size.toByte()
        nickBytes.copyInto(out, pos); pos += nickBytes.size
        bikeBytes.copyInto(out, pos)
        return out
    }

    /**
     * Decodes a byte array previously produced by [encode].
     *
     * Returns `null` when:
     * - the array is empty or has fewer than 2 bytes,
     * - the version byte is not `0x01`, or
     * - any length field overruns the array bounds.
     *
     * @param data Raw bytes from BLE advertisement service data.
     * @return Decoded [WavePayload], or `null` on any format error.
     */
    fun decode(data: ByteArray): WavePayload? {
        if (data.size < 2) return null
        if (data[0] != VERSION) return null

        val shortIdLen = data[1].toInt() and 0xFF
        var pos = 2
        if (pos + shortIdLen > data.size) return null
        val shortId = String(data, pos, shortIdLen, Charsets.UTF_8)
        pos += shortIdLen

        if (pos >= data.size) return null
        val nickLen = data[pos++].toInt() and 0xFF
        if (pos + nickLen > data.size) return null
        val nick = String(data, pos, nickLen, Charsets.UTF_8)
        pos += nickLen

        val bike = String(data, pos, data.size - pos, Charsets.UTF_8)
        return WavePayload(shortId = shortId, nick = nick, bike = bike)
    }

    /**
     * Returns the UTF-8 encoding of [str] truncated to at most [maxBytes] bytes,
     * always cutting at a valid code-point boundary so the result decodes cleanly.
     *
     * After backing up over trailing continuation bytes (10xxxxxx), the remaining
     * lead byte is kept only if its full sequence fits within [maxBytes]; otherwise
     * the lead is dropped so we never emit a partial code point.
     */
    private fun truncateUtf8(str: String, maxBytes: Int): ByteArray {
        if (maxBytes <= 0) return ByteArray(0)
        val bytes = str.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return bytes
        var len = maxBytes
        // Back up over any trailing UTF-8 continuation bytes (10xxxxxx).
        while (len > 0 && (bytes[len - 1].toInt() and 0xC0) == 0x80) len--
        // bytes[len-1] is now a lead byte or ASCII.
        if (len > 0) {
            val lead = bytes[len - 1].toInt() and 0xFF
            val seqLen = when {
                lead and 0x80 == 0 -> 1           // 0xxxxxxx  ASCII single byte
                lead and 0xE0 == 0xC0 -> 2        // 110xxxxx  2-byte lead
                lead and 0xF0 == 0xE0 -> 3        // 1110xxxx  3-byte lead
                lead and 0xF8 == 0xF0 -> 4        // 11110xxx  4-byte lead
                else -> 1                          // defensive fallback
            }
            val seqStart = len - 1
            if (seqStart + seqLen <= maxBytes) {
                // The complete sequence fits within the budget — keep it.
                len = seqStart + seqLen
            } else {
                // The sequence extends past the budget — drop the lead byte.
                len = seqStart
            }
        }
        return if (len > 0) bytes.copyOf(len) else ByteArray(0)
    }
}
