package com.mototracker.core.format

import org.json.JSONArray

/**
 * Lossless chunked serialisation / deserialisation of JSON-array GPS traces.
 *
 * Used to store large traces out-of-row in [com.mototracker.data.local.entity.RouteTraceChunkEntity]
 * so no single Room cursor window overflows even on arbitrarily long rides.
 *
 * All functions are pure (no side effects, no Android dependencies) and operate on
 * raw JSON strings whose elements may be any JSON type (objects, numbers, etc.).
 */
object TraceChunkCodec {

    /** Maximum number of JSON-array elements per chunk. At ~35 bytes/point, 2000 pts ≈ 70 KB — safely below the 2 MB window. */
    const val CHUNK_SIZE = 2000

    /**
     * Splits [json] (a JSON array) into sequential fragments of at most [chunkSize] elements.
     *
     * Each returned string is itself a valid JSON array. The concatenation of all returned
     * fragments is element-for-element equal to the original array.
     *
     * @param json      Serialised JSON array (`[…]`), or `null` / blank.
     * @param chunkSize Maximum number of array elements per chunk.
     * @return List of JSON-array strings in order; empty when [json] is null, blank, or unparseable.
     */
    fun split(json: String?, chunkSize: Int = CHUNK_SIZE): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val n = arr.length()
            if (n == 0) return emptyList()
            val result = mutableListOf<String>()
            var start = 0
            while (start < n) {
                val end = minOf(start + chunkSize, n)
                val chunk = JSONArray()
                for (i in start until end) chunk.put(arr.get(i))
                result.add(chunk.toString())
                start = end
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Joins a list of JSON-array fragment strings back into a single JSON array.
     *
     * @param chunks Ordered list of JSON-array strings previously produced by [split].
     * @return Reassembled JSON array string, or `null` when [chunks] is empty.
     */
    fun join(chunks: List<String>): String? {
        if (chunks.isEmpty()) return null
        return try {
            val result = JSONArray()
            for (chunkJson in chunks) {
                val arr = JSONArray(chunkJson)
                for (i in 0 until arr.length()) result.put(arr.get(i))
            }
            result.toString()
        } catch (_: Exception) {
            null
        }
    }
}
