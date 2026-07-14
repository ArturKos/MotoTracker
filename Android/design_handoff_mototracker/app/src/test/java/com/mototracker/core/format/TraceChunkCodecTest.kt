package com.mototracker.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceChunkCodecTest {

    // ── split — edge cases ────────────────────────────────────────────────────

    @Test
    fun `split null returns empty list`() {
        assertEquals(emptyList<String>(), TraceChunkCodec.split(null))
    }

    @Test
    fun `split blank string returns empty list`() {
        assertEquals(emptyList<String>(), TraceChunkCodec.split("   "))
    }

    @Test
    fun `split empty json array returns empty list`() {
        assertEquals(emptyList<String>(), TraceChunkCodec.split("[]"))
    }

    @Test
    fun `split malformed json returns empty list`() {
        assertEquals(emptyList<String>(), TraceChunkCodec.split("not-json"))
    }

    // ── split — single chunk ──────────────────────────────────────────────────

    @Test
    fun `split array smaller than chunk size yields one chunk`() {
        val json = buildJsonArray(10)
        val chunks = TraceChunkCodec.split(json)
        assertEquals(1, chunks.size)
    }

    @Test
    fun `split array equal to chunk size yields one chunk`() {
        val json = buildJsonArray(TraceChunkCodec.CHUNK_SIZE)
        val chunks = TraceChunkCodec.split(json)
        assertEquals(1, chunks.size)
    }

    // ── split — boundary ─────────────────────────────────────────────────────

    @Test
    fun `split array one over chunk size yields two chunks`() {
        val json = buildJsonArray(TraceChunkCodec.CHUNK_SIZE + 1)
        val chunks = TraceChunkCodec.split(json)
        assertEquals(2, chunks.size)
    }

    @Test
    fun `split array two chunks exactly yields two chunks`() {
        val json = buildJsonArray(TraceChunkCodec.CHUNK_SIZE * 2)
        val chunks = TraceChunkCodec.split(json)
        assertEquals(2, chunks.size)
    }

    @Test
    fun `split three chunks correctly`() {
        val json = buildJsonArray(TraceChunkCodec.CHUNK_SIZE * 2 + 1)
        val chunks = TraceChunkCodec.split(json)
        assertEquals(3, chunks.size)
    }

    // ── join — edge cases ─────────────────────────────────────────────────────

    @Test
    fun `join empty list returns null`() {
        assertNull(TraceChunkCodec.join(emptyList()))
    }

    // ── round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `round-trip single small array preserves element count`() {
        val json = buildJsonArray(5)
        val joined = TraceChunkCodec.join(TraceChunkCodec.split(json))
        val count = org.json.JSONArray(joined!!).length()
        assertEquals(5, count)
    }

    @Test
    fun `round-trip exactly chunk-sized array preserves element count`() {
        val n = TraceChunkCodec.CHUNK_SIZE
        val joined = TraceChunkCodec.join(TraceChunkCodec.split(buildJsonArray(n)))
        assertEquals(n, org.json.JSONArray(joined!!).length())
    }

    @Test
    fun `round-trip multi-chunk array preserves element count`() {
        val n = TraceChunkCodec.CHUNK_SIZE * 3 + 7
        val joined = TraceChunkCodec.join(TraceChunkCodec.split(buildJsonArray(n)))
        assertEquals(n, org.json.JSONArray(joined!!).length())
    }

    @Test
    fun `round-trip preserves element values`() {
        val json = """[{"lat":50.0,"lng":20.0},{"lat":51.0,"lng":21.0}]"""
        val joined = TraceChunkCodec.join(TraceChunkCodec.split(json))!!
        val arr = org.json.JSONArray(joined)
        assertEquals(2, arr.length())
        assertEquals(50.0, arr.getJSONObject(0).getDouble("lat"), 0.0001)
        assertEquals(20.0, arr.getJSONObject(0).getDouble("lng"), 0.0001)
        assertEquals(51.0, arr.getJSONObject(1).getDouble("lat"), 0.0001)
        assertEquals(21.0, arr.getJSONObject(1).getDouble("lng"), 0.0001)
    }

    @Test
    fun `round-trip order is preserved for multi-chunk array`() {
        val n = TraceChunkCodec.CHUNK_SIZE + 50
        val arr = org.json.JSONArray()
        for (i in 0 until n) arr.put(org.json.JSONObject().put("i", i))
        val json = arr.toString()

        val joined = TraceChunkCodec.join(TraceChunkCodec.split(json))!!
        val result = org.json.JSONArray(joined)
        for (i in 0 until n) {
            assertEquals(i, result.getJSONObject(i).getInt("i"))
        }
    }

    @Test
    fun `each chunk is a valid JSON array`() {
        val n = TraceChunkCodec.CHUNK_SIZE + 3
        val chunks = TraceChunkCodec.split(buildJsonArray(n))
        for (chunk in chunks) {
            val parsed = org.json.JSONArray(chunk) // throws if invalid
            assertTrue(parsed.length() > 0)
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun buildJsonArray(size: Int): String {
        val arr = org.json.JSONArray()
        for (i in 0 until size) arr.put(org.json.JSONObject().put("lat", i.toDouble()).put("lng", i.toDouble()))
        return arr.toString()
    }
}
