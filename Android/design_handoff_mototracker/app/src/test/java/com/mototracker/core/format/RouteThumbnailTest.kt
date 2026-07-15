package com.mototracker.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [RouteThumbnail.buildPathD] and [RouteThumbnail.parsePathD]. */
class RouteThumbnailTest {

    // ── null / blank / malformed ─────────────────────────────────────────────

    @Test
    fun `buildPathD returns empty string for null input`() {
        assertEquals("", RouteThumbnail.buildPathD(null))
    }

    @Test
    fun `buildPathD returns empty string for blank input`() {
        assertEquals("", RouteThumbnail.buildPathD("   "))
        assertEquals("", RouteThumbnail.buildPathD(""))
    }

    @Test
    fun `buildPathD returns empty string for malformed JSON`() {
        assertEquals("", RouteThumbnail.buildPathD("not json"))
        assertEquals("", RouteThumbnail.buildPathD("{lat:1}"))
        assertEquals("", RouteThumbnail.buildPathD("[{\"x\":1}]"))
    }

    @Test
    fun `buildPathD returns empty string for a single point`() {
        assertEquals("", RouteThumbnail.buildPathD("""[{"lat":52.2,"lng":21.0}]"""))
    }

    // ── multi-point ──────────────────────────────────────────────────────────

    @Test
    fun `buildPathD starts with M for two-point path`() {
        val result = RouteThumbnail.buildPathD(
            """[{"lat":52.0,"lng":21.0},{"lat":53.0,"lng":22.0}]"""
        )
        assertTrue("Expected path to start with M, got: $result", result.startsWith("M "))
        assertTrue("Expected path to contain L, got: $result", result.contains(" L "))
    }

    @Test
    fun `buildPathD produces M for first point and L for subsequent points`() {
        val json = """[
            {"lat":52.0,"lng":21.0},
            {"lat":52.5,"lng":21.5},
            {"lat":53.0,"lng":22.0}
        ]""".trimIndent()
        val result = RouteThumbnail.buildPathD(json)
        val parts = result.split(" ")
        assertEquals("M", parts[0])
        // Third token is the L command for the second point
        assertEquals("L", parts[3])
        // Sixth token is the L command for the third point
        assertEquals("L", parts[6])
    }

    @Test
    fun `buildPathD maps equal-latitude points to vertical centre`() {
        // All points have same lat → y should all be the centre of the viewBox
        val json = """[
            {"lat":52.0,"lng":21.0},
            {"lat":52.0,"lng":22.0}
        ]""".trimIndent()
        val result = RouteThumbnail.buildPathD(json)
        // Both y values should be equal (centred)
        val tokens = result.split(" ")
        // tokens: M x1 y1 L x2 y2
        val y1 = tokens[2].toInt()
        val y2 = tokens[5].toInt()
        assertEquals(y1, y2)
    }

    @Test
    fun `buildPathD maps equal-longitude points to horizontal centre`() {
        val json = """[
            {"lat":52.0,"lng":21.0},
            {"lat":53.0,"lng":21.0}
        ]""".trimIndent()
        val result = RouteThumbnail.buildPathD(json)
        val tokens = result.split(" ")
        val x1 = tokens[1].toInt()
        val x2 = tokens[4].toInt()
        assertEquals(x1, x2)
    }

    @Test
    fun `buildPathD projected coordinates are within viewBox bounds`() {
        val json = """[
            {"lat":49.0,"lng":14.0},
            {"lat":50.0,"lng":15.0},
            {"lat":51.0,"lng":16.0},
            {"lat":52.0,"lng":17.0}
        ]""".trimIndent()
        val result = RouteThumbnail.buildPathD(json)
        // Extract all numeric tokens (x and y alternating after M/L commands)
        val nums = result.split(" ").filter { it[0].isDigit() || it[0] == '-' }.map { it.toInt() }
        nums.forEachIndexed { i, v ->
            val max = if (i % 2 == 0) 320 else 200
            assertTrue("Coordinate $v out of viewBox (max $max)", v in 0..max)
        }
    }

    @Test
    fun `buildPathD is deterministic for identical inputs`() {
        val json = """[{"lat":52.1,"lng":21.1},{"lat":52.5,"lng":21.5},{"lat":52.9,"lng":21.9}]"""
        assertEquals(RouteThumbnail.buildPathD(json), RouteThumbnail.buildPathD(json))
    }

    // ── parsePathD ───────────────────────────────────────────────────────────

    @Test
    fun `parsePathD returns empty list for blank input`() {
        assertTrue(RouteThumbnail.parsePathD("").isEmpty())
        assertTrue(RouteThumbnail.parsePathD("   ").isEmpty())
    }

    @Test
    fun `parsePathD returns empty list for single M-only path (less than 2 points)`() {
        assertTrue(RouteThumbnail.parsePathD("M 10 20").isEmpty())
    }

    @Test
    fun `parsePathD returns empty list for malformed tokens`() {
        assertTrue(RouteThumbnail.parsePathD("M notanumber 20 L 30 40").isEmpty())
        assertTrue(RouteThumbnail.parsePathD("X 10 20 Y 30 40").isEmpty())
    }

    @Test
    fun `parsePathD returns empty list for path with only one coordinate pair`() {
        // Only M but no L means only 1 point → less than 2
        assertTrue(RouteThumbnail.parsePathD("M 12 45").isEmpty())
    }

    @Test
    fun `parsePathD valid two-point path returns correct point count and coordinates`() {
        val points = RouteThumbnail.parsePathD("M 12 45 L 100 80")
        assertEquals(2, points.size)
        assertEquals(12f, points[0].first)
        assertEquals(45f, points[0].second)
        assertEquals(100f, points[1].first)
        assertEquals(80f, points[1].second)
    }

    @Test
    fun `parsePathD valid multi-point path returns all points`() {
        val pathD = "M 12 100 L 50 80 L 100 60 L 150 90 L 200 40 L 250 70 L 308 110"
        val points = RouteThumbnail.parsePathD(pathD)
        assertEquals(7, points.size)
        // First point
        assertEquals(12f, points.first().first)
        assertEquals(100f, points.first().second)
        // Last point
        assertEquals(308f, points.last().first)
        assertEquals(110f, points.last().second)
    }

    @Test
    fun `parsePathD round-trips through buildPathDFromPoints`() {
        val latLng = listOf(
            49.0 to 14.0,
            50.0 to 15.0,
            51.0 to 16.0,
            52.0 to 17.0,
        )
        val pathD = RouteThumbnail.buildPathDFromPoints(latLng)
        val points = RouteThumbnail.parsePathD(pathD)
        assertEquals(4, points.size)
        // All x/y values should be within the 320×200 viewBox
        points.forEach { (x, y) ->
            assertTrue("x=$x out of [0,320]", x in 0f..320f)
            assertTrue("y=$y out of [0,200]", y in 0f..200f)
        }
    }
}
