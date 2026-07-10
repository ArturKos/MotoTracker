package com.mototracker.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [RouteThumbnail.buildPathD]. */
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
}
