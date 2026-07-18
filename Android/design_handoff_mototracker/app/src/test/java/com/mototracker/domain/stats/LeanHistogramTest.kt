package com.mototracker.domain.stats

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeanHistogramTest {

    // ── bucketIndex ───────────────────────────────────────────────────────────

    @Test
    fun `0 degrees maps to bucket 0`() {
        assertEquals(0, LeanHistogram.bucketIndex(0.0))
    }

    @Test
    fun `9_9 degrees maps to bucket 0`() {
        assertEquals(0, LeanHistogram.bucketIndex(9.9))
    }

    @Test
    fun `exactly 10 degrees maps to bucket 1`() {
        assertEquals(1, LeanHistogram.bucketIndex(10.0))
    }

    @Test
    fun `19_9 degrees maps to bucket 1`() {
        assertEquals(1, LeanHistogram.bucketIndex(19.9))
    }

    @Test
    fun `exactly 20 degrees maps to bucket 2`() {
        assertEquals(2, LeanHistogram.bucketIndex(20.0))
    }

    @Test
    fun `exactly 30 degrees maps to bucket 3`() {
        assertEquals(3, LeanHistogram.bucketIndex(30.0))
    }

    @Test
    fun `exactly 40 degrees maps to bucket 4`() {
        assertEquals(4, LeanHistogram.bucketIndex(40.0))
    }

    @Test
    fun `large angle maps to last bucket`() {
        assertEquals(4, LeanHistogram.bucketIndex(89.9))
    }

    // ── encode / decode round-trip ─────────────────────────────────────────────

    @Test
    fun `encode decode round-trip preserves values`() {
        val counts = intArrayOf(12, 4, 3, 1, 0)
        val json = LeanHistogram.encode(counts)
        val decoded = LeanHistogram.decode(json)!!
        assertArrayEquals(counts, decoded)
    }

    @Test
    fun `encode produces expected compact JSON`() {
        val json = LeanHistogram.encode(intArrayOf(12, 4, 3, 1, 0))
        assertEquals("[12,4,3,1,0]", json)
    }

    @Test
    fun `decode null returns null`() {
        assertNull(LeanHistogram.decode(null))
    }

    @Test
    fun `decode blank string returns null`() {
        assertNull(LeanHistogram.decode(""))
        assertNull(LeanHistogram.decode("   "))
    }

    @Test
    fun `decode malformed JSON returns null`() {
        assertNull(LeanHistogram.decode("not json"))
        assertNull(LeanHistogram.decode("{wrong:true}"))
    }

    @Test
    fun `decode wrong-length array returns null`() {
        assertNull(LeanHistogram.decode("[1,2,3]"))
        assertNull(LeanHistogram.decode("[1,2,3,4,5,6]"))
    }

    // ── aggregate ─────────────────────────────────────────────────────────────

    @Test
    fun `aggregate empty list returns all-zeros`() {
        val result = LeanHistogram.aggregate(emptyList())
        assertArrayEquals(IntArray(5), result)
    }

    @Test
    fun `aggregate single histogram returns same values`() {
        val counts = intArrayOf(5, 3, 2, 1, 0)
        val result = LeanHistogram.aggregate(listOf(counts))
        assertArrayEquals(counts, result)
    }

    @Test
    fun `aggregate two histograms sums element-wise`() {
        val a = intArrayOf(10, 5, 3, 1, 0)
        val b = intArrayOf(2, 4, 6, 8, 0)
        val result = LeanHistogram.aggregate(listOf(a, b))
        assertArrayEquals(intArrayOf(12, 9, 9, 9, 0), result)
    }

    @Test
    fun `aggregate multiple histograms element-wise`() {
        val histograms = listOf(
            intArrayOf(1, 0, 0, 0, 0),
            intArrayOf(0, 2, 0, 0, 0),
            intArrayOf(0, 0, 3, 0, 0),
        )
        val result = LeanHistogram.aggregate(histograms)
        assertArrayEquals(intArrayOf(1, 2, 3, 0, 0), result)
    }
}
