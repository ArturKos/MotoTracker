package com.mototracker.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [TrackGeometry] — pure JVM, no Android runtime needed. */
class TrackGeometryTest {

    // ── parsePathJson ────────────────────────────────────────────────────────

    @Test
    fun `parsePathJson returns empty for null`() {
        assertTrue(TrackGeometry.parsePathJson(null).isEmpty())
    }

    @Test
    fun `parsePathJson returns empty for blank string`() {
        assertTrue(TrackGeometry.parsePathJson("").isEmpty())
        assertTrue(TrackGeometry.parsePathJson("   ").isEmpty())
    }

    @Test
    fun `parsePathJson returns empty for malformed JSON`() {
        assertTrue(TrackGeometry.parsePathJson("not json").isEmpty())
        assertTrue(TrackGeometry.parsePathJson("{\"lat\":1}").isEmpty())
        assertTrue(TrackGeometry.parsePathJson("[{\"x\":1}]").isEmpty())
    }

    @Test
    fun `parsePathJson returns empty for empty JSON array`() {
        assertTrue(TrackGeometry.parsePathJson("[]").isEmpty())
    }

    @Test
    fun `parsePathJson parses single point correctly`() {
        val result = TrackGeometry.parsePathJson("""[{"lat":52.23,"lng":21.01}]""")
        assertEquals(1, result.size)
        assertEquals(52.23, result[0].lat, 1e-9)
        assertEquals(21.01, result[0].lon, 1e-9)
    }

    @Test
    fun `parsePathJson preserves coordinate order for multiple points`() {
        val json = """[
            {"lat":52.0,"lng":21.0},
            {"lat":52.5,"lng":21.5},
            {"lat":53.0,"lng":22.0}
        ]"""
        val result = TrackGeometry.parsePathJson(json)
        assertEquals(3, result.size)
        assertEquals(52.0, result[0].lat, 1e-9)
        assertEquals(21.0, result[0].lon, 1e-9)
        assertEquals(52.5, result[1].lat, 1e-9)
        assertEquals(21.5, result[1].lon, 1e-9)
        assertEquals(53.0, result[2].lat, 1e-9)
        assertEquals(22.0, result[2].lon, 1e-9)
    }

    @Test
    fun `parsePathJson handles negative coordinates`() {
        val json = """[{"lat":-33.87,"lng":151.21},{"lat":-34.0,"lng":150.9}]"""
        val result = TrackGeometry.parsePathJson(json)
        assertEquals(2, result.size)
        assertEquals(-33.87, result[0].lat, 1e-9)
        assertEquals(151.21, result[0].lon, 1e-9)
    }

    // ── bounds ───────────────────────────────────────────────────────────────

    @Test
    fun `bounds returns null for empty list`() {
        assertNull(TrackGeometry.bounds(emptyList()))
    }

    @Test
    fun `bounds returns non-null for single point`() {
        val b = TrackGeometry.bounds(listOf(GeoCoord(52.0, 21.0)))
        assertNotNull(b)
    }

    @Test
    fun `bounds single point is non-degenerate`() {
        val b = TrackGeometry.bounds(listOf(GeoCoord(52.0, 21.0)))!!
        assertTrue("north > south", b.north > b.south)
        assertTrue("east > west", b.east > b.west)
    }

    @Test
    fun `bounds north is greater than south`() {
        val points = listOf(
            GeoCoord(50.0, 14.0),
            GeoCoord(54.0, 18.0),
            GeoCoord(52.0, 16.0),
        )
        val b = TrackGeometry.bounds(points)!!
        assertTrue("north >= south", b.north >= b.south)
    }

    @Test
    fun `bounds east is greater than west`() {
        val points = listOf(
            GeoCoord(50.0, 14.0),
            GeoCoord(54.0, 18.0),
        )
        val b = TrackGeometry.bounds(points)!!
        assertTrue("east >= west", b.east >= b.west)
    }

    @Test
    fun `bounds correctly identifies min and max values`() {
        val points = listOf(
            GeoCoord(50.0, 14.0),
            GeoCoord(54.0, 18.0),
        )
        val b = TrackGeometry.bounds(points)!!
        // north must be >= 54.0 (with padding), south <= 50.0
        assertTrue("north > 54.0", b.north > 54.0)
        assertTrue("south < 50.0", b.south < 50.0)
        assertTrue("east > 18.0", b.east > 18.0)
        assertTrue("west < 14.0", b.west < 14.0)
    }

    @Test
    fun `bounds applies padding so track fits inside viewport`() {
        val latSpan = 4.0 // 54 - 50
        val lonSpan = 4.0 // 18 - 14
        val expectedLatPad = latSpan * 0.10
        val expectedLonPad = lonSpan * 0.10
        val points = listOf(GeoCoord(50.0, 14.0), GeoCoord(54.0, 18.0))
        val b = TrackGeometry.bounds(points)!!
        assertEquals(54.0 + expectedLatPad, b.north, 1e-9)
        assertEquals(50.0 - expectedLatPad, b.south, 1e-9)
        assertEquals(18.0 + expectedLonPad, b.east, 1e-9)
        assertEquals(14.0 - expectedLonPad, b.west, 1e-9)
    }

    // ── startPoint / endPoint ────────────────────────────────────────────────

    @Test
    fun `startPoint returns null for empty list`() {
        assertNull(TrackGeometry.startPoint(emptyList()))
    }

    @Test
    fun `endPoint returns null for empty list`() {
        assertNull(TrackGeometry.endPoint(emptyList()))
    }

    @Test
    fun `startPoint returns first coordinate`() {
        val points = listOf(GeoCoord(1.0, 2.0), GeoCoord(3.0, 4.0), GeoCoord(5.0, 6.0))
        assertEquals(GeoCoord(1.0, 2.0), TrackGeometry.startPoint(points))
    }

    @Test
    fun `endPoint returns last coordinate`() {
        val points = listOf(GeoCoord(1.0, 2.0), GeoCoord(3.0, 4.0), GeoCoord(5.0, 6.0))
        assertEquals(GeoCoord(5.0, 6.0), TrackGeometry.endPoint(points))
    }

    @Test
    fun `startPoint and endPoint are the same for a single-point list`() {
        val single = listOf(GeoCoord(48.0, 2.0))
        assertEquals(TrackGeometry.startPoint(single), TrackGeometry.endPoint(single))
    }
}
