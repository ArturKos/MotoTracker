package com.mototracker.core.format

import com.mototracker.data.model.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxExporterTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeRoute(
        id: String = "abc12345-6789",
        name: String = "Alpine Tour",
        dateEpochMs: Long = 1_700_000_000_000L,
        pathJson: String? = null,
    ): Route = Route(
        id = id, name = name, dateEpochMs = dateEpochMs,
        bikeId = null, km = 0.0, durSec = 0L, avg = 0.0, max = 0.0,
        lean = 0.0, elev = 0.0, fuel = 0.0, synced = true,
        wxJson = null, pathJson = pathJson, speedJson = null,
        elevProfileJson = null, notes = null,
    )

    private val threePointJson = """[
        {"lat":50.0612,"lng":19.9372},
        {"lat":50.0620,"lng":19.9380},
        {"lat":50.0630,"lng":19.9390}
    ]""".trimIndent()

    // ── toGpx: structure & metadata ───────────────────────────────────────────

    @Test
    fun `toGpx produces valid GPX 1_1 declaration`() {
        val gpx = GpxExporter.toGpx(makeRoute())
        assertTrue(gpx.contains("""version="1.1""""))
        assertTrue(gpx.contains("""creator="MotoTracker""""))
        assertTrue(gpx.contains("""xmlns="http://www.topografix.com/GPX/1/1""""))
    }

    @Test
    fun `toGpx includes route name in metadata and trk name`() {
        val gpx = GpxExporter.toGpx(makeRoute(name = "Alpine Tour"))
        val nameCount = gpx.split("<name>Alpine Tour</name>").size - 1
        assertEquals("name should appear in both metadata and trk", 2, nameCount)
    }

    @Test
    fun `toGpx includes ISO-8601 UTC time in metadata`() {
        // epoch 1_700_000_000_000 ms = 2023-11-14T22:13:20Z
        val gpx = GpxExporter.toGpx(makeRoute(dateEpochMs = 1_700_000_000_000L))
        assertTrue(gpx.contains("<time>2023-11-14T22:13:20Z</time>"))
    }

    // ── toGpx: track points ───────────────────────────────────────────────────

    @Test
    fun `toGpx produces correct trkpt count for valid pathJson`() {
        val gpx = GpxExporter.toGpx(makeRoute(pathJson = threePointJson))
        val count = gpx.split("<trkpt").size - 1
        assertEquals(3, count)
    }

    @Test
    fun `toGpx first trkpt has correct lat and lon`() {
        val gpx = GpxExporter.toGpx(makeRoute(pathJson = threePointJson))
        assertTrue(gpx.contains("""lat="50.0612" lon="19.9372""""))
    }

    @Test
    fun `toGpx last trkpt has correct lat and lon`() {
        val gpx = GpxExporter.toGpx(makeRoute(pathJson = threePointJson))
        assertTrue(gpx.contains("""lat="50.063" lon="19.939""""))
    }

    // ── toGpx: empty/invalid pathJson ─────────────────────────────────────────

    @Test
    fun `toGpx with null pathJson produces well-formed doc with empty trkseg`() {
        val gpx = GpxExporter.toGpx(makeRoute(pathJson = null))
        assertWellFormed(gpx)
        assertEmptyTrkseg(gpx)
    }

    @Test
    fun `toGpx with blank pathJson produces well-formed doc with empty trkseg`() {
        val gpx = GpxExporter.toGpx(makeRoute(pathJson = "   "))
        assertWellFormed(gpx)
        assertEmptyTrkseg(gpx)
    }

    @Test
    fun `toGpx with malformed pathJson produces well-formed doc with empty trkseg`() {
        val gpx = GpxExporter.toGpx(makeRoute(pathJson = "not_json_at_all"))
        assertWellFormed(gpx)
        assertEmptyTrkseg(gpx)
    }

    @Test
    fun `toGpx never throws for any input`() {
        val cases = listOf(null, "", "  ", "[]", "{}", "not_json", "[{\"lat\":1}]")
        cases.forEach { pathJson ->
            try {
                GpxExporter.toGpx(makeRoute(pathJson = pathJson))
            } catch (e: Exception) {
                throw AssertionError("toGpx threw for pathJson=$pathJson: $e")
            }
        }
    }

    // ── toGpx: XML escaping ───────────────────────────────────────────────────

    @Test
    fun `toGpx escapes ampersand in route name`() {
        val gpx = GpxExporter.toGpx(makeRoute(name = "Ride & shine"))
        assertTrue(gpx.contains("Ride &amp; shine"))
        assertFalse(gpx.contains("Ride & shine"))
    }

    @Test
    fun `toGpx escapes lt and gt in route name`() {
        val gpx = GpxExporter.toGpx(makeRoute(name = "<b>Bold</b>"))
        assertTrue(gpx.contains("&lt;b&gt;Bold&lt;/b&gt;"))
    }

    // ── toGpx: blank name fallback ────────────────────────────────────────────

    @Test
    fun `toGpx uses route id when name is blank`() {
        val gpx = GpxExporter.toGpx(makeRoute(id = "abc12345", name = ""))
        assertTrue(gpx.contains("<name>abc12345</name>"))
    }

    // ── fileName ──────────────────────────────────────────────────────────────

    @Test
    fun `fileName ends with gpx extension`() {
        assertTrue(GpxExporter.fileName(makeRoute()).endsWith(".gpx"))
    }

    @Test
    fun `fileName slugifies route name`() {
        val name = GpxExporter.fileName(makeRoute(name = "Alpine Tour 2023"))
        assertTrue(name.startsWith("alpine-tour-2023"))
    }

    @Test
    fun `fileName includes route id prefix when name is blank`() {
        val route = makeRoute(id = "abc12345-6789", name = "")
        val name = GpxExporter.fileName(route)
        assertTrue(name.startsWith("abc12345"))
    }

    @Test
    fun `fileName handles special characters in name`() {
        val name = GpxExporter.fileName(makeRoute(name = "Ride & Shine!!!"))
        assertFalse(name.contains("&"))
        assertFalse(name.contains("!"))
        assertTrue(name.endsWith(".gpx"))
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun assertWellFormed(gpx: String) {
        assertTrue("Missing xml declaration", gpx.contains("<?xml"))
        assertTrue("Missing <gpx>", gpx.contains("<gpx "))
        assertTrue("Missing </gpx>", gpx.contains("</gpx>"))
        assertTrue("Missing <trkseg>", gpx.contains("<trkseg>"))
        assertTrue("Missing </trkseg>", gpx.contains("</trkseg>"))
    }

    private fun assertEmptyTrkseg(gpx: String) {
        assertFalse("Expected no <trkpt> elements", gpx.contains("<trkpt"))
    }
}
