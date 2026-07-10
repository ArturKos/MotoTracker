package com.mototracker.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [ChartPolyline.speedPoints] and [ChartPolyline.elevPoints]. */
class ChartPolylineTest {

    // ── null / blank / malformed → empty ─────────────────────────────────────

    @Test
    fun `speedPoints returns empty for null`() {
        val result = ChartPolyline.speedPoints(null)
        assertEquals("", result.stroke)
        assertEquals("", result.fill)
    }

    @Test
    fun `speedPoints returns empty for blank`() {
        val result = ChartPolyline.speedPoints("   ")
        assertEquals("", result.stroke)
        assertEquals("", result.fill)
    }

    @Test
    fun `speedPoints returns empty for malformed JSON`() {
        assertEquals("", ChartPolyline.speedPoints("not json").stroke)
        assertEquals("", ChartPolyline.speedPoints("{t:1}").stroke)
    }

    @Test
    fun `speedPoints returns empty for wrong field names`() {
        // keys x/y instead of t/v
        assertEquals("", ChartPolyline.speedPoints("""[{"x":0,"y":50},{"x":10,"y":80}]""").stroke)
    }

    @Test
    fun `speedPoints returns empty for single point`() {
        val result = ChartPolyline.speedPoints("""[{"t":0,"v":50}]""")
        assertEquals("", result.stroke)
    }

    @Test
    fun `elevPoints returns empty for null`() {
        val result = ChartPolyline.elevPoints(null)
        assertEquals("", result.stroke)
        assertEquals("", result.fill)
    }

    @Test
    fun `elevPoints returns empty for single point`() {
        assertEquals("", ChartPolyline.elevPoints("""[{"d":0,"a":100}]""").stroke)
    }

    // ── happy path ────────────────────────────────────────────────────────────

    @Test
    fun `speedPoints returns non-empty strings for valid two-point series`() {
        val result = ChartPolyline.speedPoints("""[{"t":0,"v":60},{"t":60,"v":120}]""")
        assertTrue(result.stroke.isNotEmpty())
        assertTrue(result.fill.isNotEmpty())
    }

    @Test
    fun `elevPoints returns non-empty strings for valid two-point series`() {
        val result = ChartPolyline.elevPoints("""[{"d":0,"a":200},{"d":10,"a":500}]""")
        assertTrue(result.stroke.isNotEmpty())
        assertTrue(result.fill.isNotEmpty())
    }

    @Test
    fun `speedPoints stroke has same number of points as input`() {
        val json = """[{"t":0,"v":60},{"t":30,"v":90},{"t":60,"v":120}]"""
        val stroke = ChartPolyline.speedPoints(json).stroke
        val count = stroke.trim().split(" ").size
        assertEquals(3, count)
    }

    @Test
    fun `elevPoints stroke has same number of points as input`() {
        val json = """[{"d":0,"a":100},{"d":5,"a":300},{"d":10,"a":200}]"""
        val stroke = ChartPolyline.elevPoints(json).stroke
        assertEquals(3, stroke.trim().split(" ").size)
    }

    @Test
    fun `fill adds two extra baseline corner points`() {
        val json = """[{"t":0,"v":60},{"t":30,"v":90},{"t":60,"v":120}]"""
        val result = ChartPolyline.speedPoints(json)
        val strokeCount = result.stroke.trim().split(" ").size
        val fillCount = result.fill.trim().split(" ").size
        assertEquals(strokeCount + 2, fillCount)
    }

    @Test
    fun `all stroke coordinates are within 320x90 viewBox`() {
        val json = """[{"t":0,"v":0},{"t":100,"v":200},{"t":200,"v":100}]"""
        val stroke = ChartPolyline.speedPoints(json).stroke
        stroke.trim().split(" ").forEach { pair ->
            val (xs, ys) = pair.split(",")
            val x = xs.toInt(); val y = ys.toInt()
            assertTrue("x=$x out of bounds", x in 0..320)
            assertTrue("y=$y out of bounds", y in 0..90)
        }
    }

    // ── flat series ───────────────────────────────────────────────────────────

    @Test
    fun `flat speed series maps all points to the vertical centre`() {
        val json = """[{"t":0,"v":100},{"t":60,"v":100},{"t":120,"v":100}]"""
        val stroke = ChartPolyline.speedPoints(json).stroke
        val ys = stroke.trim().split(" ").map { it.split(",")[1].toInt() }
        assertTrue("All y should be equal for flat series", ys.all { it == ys[0] })
    }

    @Test
    fun `fill baseline y equals VIEW_H (90)`() {
        val json = """[{"t":0,"v":50},{"t":60,"v":80}]"""
        val fill = ChartPolyline.speedPoints(json).fill
        val parts = fill.trim().split(" ")
        // Last two points are the baseline corners
        val y1 = parts[parts.size - 2].split(",")[1].toInt()
        val y2 = parts[parts.size - 1].split(",")[1].toInt()
        assertEquals(90, y1)
        assertEquals(90, y2)
    }

    // ── determinism ───────────────────────────────────────────────────────────

    @Test
    fun `speedPoints is deterministic for identical inputs`() {
        val json = """[{"t":0,"v":60},{"t":30,"v":120},{"t":60,"v":90}]"""
        assertEquals(ChartPolyline.speedPoints(json), ChartPolyline.speedPoints(json))
    }

    @Test
    fun `elevPoints is deterministic for identical inputs`() {
        val json = """[{"d":0,"a":300},{"d":5,"a":600},{"d":10,"a":450}]"""
        assertEquals(ChartPolyline.elevPoints(json), ChartPolyline.elevPoints(json))
    }
}
