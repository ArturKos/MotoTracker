package com.mototracker.domain.stats

import org.json.JSONArray
import org.json.JSONException

/**
 * Lean-angle histogram utilities for the Statistics screen.
 *
 * Buckets are defined by absolute lean angle in degrees:
 * - Bucket 0: 0–10°
 * - Bucket 1: 10–20°
 * - Bucket 2: 20–30°
 * - Bucket 3: 30–40°
 * - Bucket 4: 40°+
 *
 * The histogram records time-in-band in seconds (one unit per elapsed second from [RecordingEngine.tick]).
 */
object LeanHistogram {

    /** Upper exclusive boundary for each bucket except the last; the last bucket is open-ended. */
    val BUCKET_UPPER_BOUNDS: IntArray = intArrayOf(10, 20, 30, 40)

    /** Total number of lean buckets. */
    const val BUCKET_COUNT = 5

    /**
     * Returns the bucket index (0–4) for an absolute lean angle [absLeanDeg].
     *
     * Boundary values fall into the higher bucket (e.g. exactly 10° → bucket 1).
     *
     * @param absLeanDeg Non-negative lean angle in degrees.
     * @return Index in [0, [BUCKET_COUNT]).
     */
    fun bucketIndex(absLeanDeg: Double): Int {
        for (i in BUCKET_UPPER_BOUNDS.indices) {
            if (absLeanDeg < BUCKET_UPPER_BOUNDS[i]) return i
        }
        return BUCKET_COUNT - 1
    }

    /**
     * Encodes a [counts] array (length [BUCKET_COUNT]) to a compact JSON array string.
     *
     * Example: `[12,4,3,1,0]`
     *
     * @param counts Per-bucket time-in-seconds counts; must have exactly [BUCKET_COUNT] elements.
     * @return JSON array string.
     */
    fun encode(counts: IntArray): String {
        val arr = JSONArray()
        for (c in counts) arr.put(c)
        return arr.toString()
    }

    /**
     * Decodes a JSON array string back to an [IntArray] of length [BUCKET_COUNT].
     *
     * Returns null when [json] is null, blank, malformed, or has a length other than [BUCKET_COUNT].
     * Designed to gracefully handle legacy routes that have no histogram stored.
     *
     * @param json Encoded histogram string, or null/blank.
     * @return Decoded array or null.
     */
    fun decode(json: String?): IntArray? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            if (arr.length() != BUCKET_COUNT) return null
            IntArray(BUCKET_COUNT) { arr.getInt(it) }
        } catch (_: JSONException) {
            null
        }
    }

    /**
     * Aggregates multiple per-route histograms into a single element-wise sum.
     *
     * Each array in [perRoute] must have exactly [BUCKET_COUNT] elements.
     *
     * @param perRoute List of decoded per-route count arrays.
     * @return Aggregated [IntArray] of length [BUCKET_COUNT]; all-zeros if [perRoute] is empty.
     */
    fun aggregate(perRoute: List<IntArray>): IntArray {
        val result = IntArray(BUCKET_COUNT)
        for (counts in perRoute) {
            for (i in 0 until BUCKET_COUNT) {
                result[i] += counts[i]
            }
        }
        return result
    }
}
