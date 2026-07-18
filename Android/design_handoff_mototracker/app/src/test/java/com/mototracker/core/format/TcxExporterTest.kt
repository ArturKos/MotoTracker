package com.mototracker.core.format

import com.mototracker.data.model.Route
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

class TcxExporterTest {

    // ── Locale isolation ─────────────────────────────────────────────────────

    private lateinit var defaultLocale: Locale

    @Before fun saveLocale() { defaultLocale = Locale.getDefault() }
    @After  fun restoreLocale() { Locale.setDefault(defaultLocale) }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeRoute(
        id: String = "abc12345-6789",
        name: String = "Alpine Tour",
        dateEpochMs: Long = 1_700_000_000_000L,
        durSec: Long = 3600L,
        km: Double = 50.0,
        max: Double = 120.0,
        pathJson: String? = null,
    ): Route = Route(
        id = id, name = name, dateEpochMs = dateEpochMs,
        bikeId = null, km = km, durSec = durSec, avg = 50.0, max = max,
        lean = 0.0, elev = 0.0, fuel = 0.0, synced = true,
        wxJson = null, pathJson = pathJson, speedJson = null,
        elevProfileJson = null, notes = null,
    )

    // Two points ~111 m apart (1 arc-second of latitude ≈ 30.9 m per 0.001°)
    // Precise haversine for (50.0, 19.0) → (50.001, 19.0) ≈ 111.32 m
    private val twoPointJson = """[
        {"lat":50.000,"lng":19.000,"ele":250.0,"t":1700000000000},
        {"lat":50.001,"lng":19.000,"ele":260.0,"t":1700000060000}
    ]""".trimIndent()

    private val threePointJson = """[
        {"lat":50.0612,"lng":19.9372,"ele":200.0,"t":1700000000000},
        {"lat":50.0620,"lng":19.9380,"ele":210.0,"t":1700000030000},
        {"lat":50.0630,"lng":19.9390,"ele":220.0,"t":1700000060000}
    ]""".trimIndent()

    private val legacyNoTimestampJson = """[
        {"lat":50.0,"lng":20.0,"ele":300.0},
        {"lat":50.001,"lng":20.001,"ele":310.0},
        {"lat":50.002,"lng":20.002,"ele":320.0}
    ]""".trimIndent()

    // ── Root structure ────────────────────────────────────────────────────────

    @Test
    fun `toTcx produces valid XML declaration`() {
        val tcx = TcxExporter.toTcx(makeRoute())
        assertTrue(tcx.contains("""<?xml version="1.0" encoding="UTF-8"?>"""))
    }

    @Test
    fun `toTcx root element is TrainingCenterDatabase with correct namespace`() {
        val tcx = TcxExporter.toTcx(makeRoute())
        assertTrue(tcx.contains("""<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">"""))
        assertTrue(tcx.contains("</TrainingCenterDatabase>"))
    }

    @Test
    fun `toTcx contains Activities and Activity Sport Other`() {
        val tcx = TcxExporter.toTcx(makeRoute())
        assertTrue(tcx.contains("<Activities>"))
        assertTrue(tcx.contains("""<Activity Sport="Other">"""))
        assertTrue(tcx.contains("</Activity>"))
        assertTrue(tcx.contains("</Activities>"))
    }

    @Test
    fun `toTcx contains exactly one Lap element`() {
        val tcx = TcxExporter.toTcx(makeRoute())
        val lapCount = tcx.split("<Lap ").size - 1
        assertEquals("expected exactly one Lap", 1, lapCount)
    }

    @Test
    fun `toTcx Lap StartTime and Id match route dateEpochMs`() {
        // epoch 1_700_000_000_000 ms = 2023-11-14T22:13:20Z
        val tcx = TcxExporter.toTcx(makeRoute(dateEpochMs = 1_700_000_000_000L))
        assertTrue(tcx.contains("<Id>2023-11-14T22:13:20Z</Id>"))
        assertTrue(tcx.contains("""<Lap StartTime="2023-11-14T22:13:20Z">"""))
    }

    // ── Lap summary values ────────────────────────────────────────────────────

    @Test
    fun `toTcx TotalTimeSeconds matches route durSec`() {
        val tcx = TcxExporter.toTcx(makeRoute(durSec = 3600L))
        assertTrue(tcx.contains("<TotalTimeSeconds>3600</TotalTimeSeconds>"))
    }

    @Test
    fun `toTcx DistanceMeters in Lap matches route km converted to metres`() {
        // 50.0 km = 50000.0 m
        val tcx = TcxExporter.toTcx(makeRoute(km = 50.0))
        assertTrue(tcx.contains("<DistanceMeters>50000.0</DistanceMeters>"))
    }

    @Test
    fun `toTcx MaximumSpeed converts km-h to m-s`() {
        // 72.0 km/h = 20.0 m/s
        val tcx = TcxExporter.toTcx(makeRoute(max = 72.0))
        assertTrue(tcx.contains("<MaximumSpeed>20.0000</MaximumSpeed>"))
    }

    @Test
    fun `toTcx contains Intensity Active and TriggerMethod Manual`() {
        val tcx = TcxExporter.toTcx(makeRoute())
        assertTrue(tcx.contains("<Intensity>Active</Intensity>"))
        assertTrue(tcx.contains("<TriggerMethod>Manual</TriggerMethod>"))
    }

    // ── Track points ──────────────────────────────────────────────────────────

    @Test
    fun `toTcx produces correct Trackpoint count for three-point path`() {
        val tcx = TcxExporter.toTcx(makeRoute(pathJson = threePointJson))
        val count = tcx.split("<Trackpoint>").size - 1
        assertEquals(3, count)
    }

    @Test
    fun `toTcx each Trackpoint has Time element`() {
        val tcx = TcxExporter.toTcx(makeRoute(pathJson = threePointJson))
        val timeCount = tcx.split("<Time>").size - 1
        assertEquals("each Trackpoint must have a Time", 3, timeCount)
    }

    @Test
    fun `toTcx each Trackpoint has Position with lat and lng`() {
        val tcx = TcxExporter.toTcx(makeRoute(pathJson = threePointJson))
        val posCount = tcx.split("<Position>").size - 1
        assertEquals(3, posCount)
        assertTrue(tcx.contains("<LatitudeDegrees>50.0612</LatitudeDegrees>"))
        assertTrue(tcx.contains("<LongitudeDegrees>19.9372</LongitudeDegrees>"))
    }

    @Test
    fun `toTcx AltitudeMeters present when ele non-zero`() {
        val tcx = TcxExporter.toTcx(makeRoute(pathJson = twoPointJson))
        assertTrue(tcx.contains("<AltitudeMeters>250.0</AltitudeMeters>"))
        assertTrue(tcx.contains("<AltitudeMeters>260.0</AltitudeMeters>"))
    }

    @Test
    fun `toTcx AltitudeMeters absent when ele is zero`() {
        val zeroEleJson = """[{"lat":50.0,"lng":19.0,"ele":0.0,"t":1700000000000}]"""
        val tcx = TcxExporter.toTcx(makeRoute(pathJson = zeroEleJson))
        assertFalse(tcx.contains("<AltitudeMeters>"))
    }

    @Test
    fun `toTcx AltitudeMeters absent when ele field missing`() {
        val noEleJson = """[{"lat":50.0,"lng":19.0,"t":1700000000000}]"""
        val tcx = TcxExporter.toTcx(makeRoute(pathJson = noEleJson))
        assertFalse(tcx.contains("<AltitudeMeters>"))
    }

    // ── Cumulative DistanceMeters ─────────────────────────────────────────────

    @Test
    fun `toTcx first Trackpoint cumulative DistanceMeters is zero`() {
        val tcx = TcxExporter.toTcx(makeRoute(pathJson = twoPointJson))
        // First trackpoint must have <DistanceMeters>0.0</DistanceMeters>
        assertTrue(tcx.contains("<DistanceMeters>0.0</DistanceMeters>"))
    }

    @Test
    fun `toTcx cumulative DistanceMeters is monotonically non-decreasing`() {
        val tcx = TcxExporter.toTcx(makeRoute(pathJson = threePointJson))
        val distances = Regex("<DistanceMeters>(.*?)</DistanceMeters>")
            .findAll(tcx)
            .drop(1) // skip the Lap-level one which is total km
            .map { it.groupValues[1].toDouble() }
            .toList()
        assertEquals("expected 3 per-point DistanceMeters", 3, distances.size)
        for (i in 1 until distances.size) {
            assertTrue(
                "distances must be non-decreasing at index $i: ${distances[i - 1]} > ${distances[i]}",
                distances[i] >= distances[i - 1],
            )
        }
    }

    @Test
    fun `toTcx cumulative DistanceMeters second point cross-checked against haversine`() {
        // (50.000, 19.000) → (50.001, 19.000): expect ≈ 111.32 m
        val tcx = TcxExporter.toTcx(makeRoute(pathJson = twoPointJson))
        // The Lap-level DistanceMeters is first; skip it and take the two per-point ones
        val distances = Regex("<DistanceMeters>(.*?)</DistanceMeters>")
            .findAll(tcx)
            .drop(1)
            .map { it.groupValues[1].toDouble() }
            .toList()
        assertEquals("expected 2 per-point distances for 2-point path", 2, distances.size)
        assertEquals(0.0, distances[0], 0.001)
        // Haversine reference: ~111.32 m ± 0.5 m tolerance
        assertTrue(
            "second point cumulative distance expected ~111.32 m, got ${distances[1]}",
            abs(distances[1] - 111.32) < 0.5,
        )
    }

    // ── Legacy points without timestamps ─────────────────────────────────────

    @Test
    fun `toTcx legacy points without t field get synthesized monotonic timestamps`() {
        val route = makeRoute(pathJson = legacyNoTimestampJson, dateEpochMs = 0L, durSec = 60L)
        val tcx = TcxExporter.toTcx(route)
        // Check that <Time> elements exist (one per trackpoint)
        val timeCount = tcx.split("<Time>").size - 1
        assertEquals("each Trackpoint must have a synthesized Time", 3, timeCount)
    }

    @Test
    fun `toTcx synthesized timestamps are monotonically non-decreasing`() {
        // 3 points, durSec=60, dateEpochMs=0 → times should be 0, 30000, 60000 ms
        val route = makeRoute(pathJson = legacyNoTimestampJson, dateEpochMs = 0L, durSec = 60L)
        val tcx = TcxExporter.toTcx(route)
        // The ISO-8601 times should be 1970-01-01T00:00:00Z, T00:00:30Z, T00:01:00Z
        assertTrue(tcx.contains("1970-01-01T00:00:00Z"))
        assertTrue(tcx.contains("1970-01-01T00:00:30Z"))
        assertTrue(tcx.contains("1970-01-01T00:01:00Z"))
    }

    // ── Null / blank / malformed pathJson ─────────────────────────────────────

    @Test
    fun `toTcx with null pathJson produces well-formed doc with empty Track`() {
        val tcx = TcxExporter.toTcx(makeRoute(pathJson = null))
        assertWellFormed(tcx)
        assertEmptyTrack(tcx)
    }

    @Test
    fun `toTcx with blank pathJson produces well-formed doc with empty Track`() {
        val tcx = TcxExporter.toTcx(makeRoute(pathJson = "   "))
        assertWellFormed(tcx)
        assertEmptyTrack(tcx)
    }

    @Test
    fun `toTcx with malformed pathJson produces well-formed doc with empty Track`() {
        val tcx = TcxExporter.toTcx(makeRoute(pathJson = "not_json_at_all"))
        assertWellFormed(tcx)
        assertEmptyTrack(tcx)
    }

    @Test
    fun `toTcx never throws for any input`() {
        val cases = listOf(null, "", "  ", "[]", "{}", "not_json", "[{\"lat\":1}]")
        cases.forEach { pathJson ->
            try {
                TcxExporter.toTcx(makeRoute(pathJson = pathJson))
            } catch (e: Exception) {
                throw AssertionError("toTcx threw for pathJson=$pathJson: $e")
            }
        }
    }

    // ── XML escaping ──────────────────────────────────────────────────────────

    @Test
    fun `toTcx escapes ampersand in route name in Notes element`() {
        val tcx = TcxExporter.toTcx(makeRoute(name = "Ride & Shine"))
        assertTrue("escaped ampersand must appear in Notes", tcx.contains("Ride &amp; Shine"))
        assertFalse("raw & must not appear in output", tcx.contains("Ride & Shine"))
    }

    @Test
    fun `toTcx escapes lt and gt in route name in Notes element`() {
        val tcx = TcxExporter.toTcx(makeRoute(name = "<script>"))
        assertTrue(tcx.contains("&lt;script&gt;"))
        assertFalse(tcx.contains("<script>"))
    }

    // ── XML element ordering ──────────────────────────────────────────────────

    @Test
    fun `toTcx Notes element appears after closing Lap tag per TCX XSD`() {
        val tcx = TcxExporter.toTcx(makeRoute(name = "Order Test"))
        val lapCloseIdx = tcx.indexOf("</Lap>")
        val notesIdx = tcx.indexOf("<Notes>")
        assertTrue("</Lap> must be present", lapCloseIdx >= 0)
        assertTrue("<Notes> must be present", notesIdx >= 0)
        assertTrue("<Notes> must appear AFTER </Lap>", notesIdx > lapCloseIdx)
    }

    @Test
    fun `toTcx Lap element follows Id directly with no Notes between them`() {
        val tcx = TcxExporter.toTcx(makeRoute(name = "Order Test"))
        val idCloseIdx = tcx.indexOf("</Id>")
        val lapOpenIdx = tcx.indexOf("<Lap ")
        val notesBeforeLap = tcx.indexOf("<Notes>").let { it in (idCloseIdx + 1) until lapOpenIdx }
        assertFalse("<Notes> must NOT appear between </Id> and <Lap>", notesBeforeLap)
    }

    // ── fileName ──────────────────────────────────────────────────────────────

    @Test
    fun `fileName ends with tcx extension`() {
        assertTrue(TcxExporter.fileName(makeRoute()).endsWith(".tcx"))
    }

    @Test
    fun `fileName slugifies route name`() {
        val name = TcxExporter.fileName(makeRoute(name = "Alpine Tour 2023"))
        assertTrue(name.startsWith("alpine-tour-2023"))
    }

    @Test
    fun `fileName includes route id prefix when name is blank`() {
        val route = makeRoute(id = "abc12345-6789", name = "")
        val name = TcxExporter.fileName(route)
        assertTrue(name.startsWith("abc12345"))
    }

    @Test
    fun `fileName handles special characters in name`() {
        val name = TcxExporter.fileName(makeRoute(name = "Ride & Shine!!!"))
        assertFalse(name.contains("&"))
        assertFalse(name.contains("!"))
        assertTrue(name.endsWith(".tcx"))
    }

    // ── Locale-safety: numeric fields must always use '.' decimal separator ───

    @Test
    fun `toTcx numeric fields use dot decimal separator under comma-decimal locale`() {
        Locale.setDefault(Locale.GERMANY)   // uses ',' as decimal separator
        // 50.0 km → DistanceMeters 50000.0; 72.0 km/h → MaximumSpeed 20.0000
        val tcx = TcxExporter.toTcx(makeRoute(km = 50.0, max = 72.0, pathJson = twoPointJson))
        // Lap-level DistanceMeters must be "50000.0" not "50000,0"
        assertTrue(
            "Lap DistanceMeters must contain dot decimal under GERMANY locale",
            tcx.contains("<DistanceMeters>50000.0</DistanceMeters>")
        )
        assertFalse(
            "Lap DistanceMeters must NOT contain comma decimal under GERMANY locale",
            tcx.contains("<DistanceMeters>50000,0</DistanceMeters>")
        )
        // MaximumSpeed must be "20.0000" not "20,0000"
        assertTrue(
            "MaximumSpeed must contain dot decimal under GERMANY locale",
            tcx.contains("<MaximumSpeed>20.0000</MaximumSpeed>")
        )
        assertFalse(
            "MaximumSpeed must NOT contain comma decimal under GERMANY locale",
            tcx.contains("<MaximumSpeed>20,0000</MaximumSpeed>")
        )
        // Per-point DistanceMeters: first point must be "0.0"
        assertTrue(
            "Per-point DistanceMeters must use dot decimal under GERMANY locale",
            tcx.contains("<DistanceMeters>0.0</DistanceMeters>")
        )
    }

    @Test
    fun `toTcx numeric fields use dot decimal separator under Polish locale`() {
        Locale.setDefault(Locale.forLanguageTag("pl"))
        val tcx = TcxExporter.toTcx(makeRoute(km = 100.0, max = 90.0))
        // 100.0 km → 100000.0 m; 90.0 km/h → 25.0000 m/s
        assertTrue(
            "Lap DistanceMeters must use dot under Polish locale",
            tcx.contains("<DistanceMeters>100000.0</DistanceMeters>")
        )
        assertTrue(
            "MaximumSpeed must use dot under Polish locale",
            tcx.contains("<MaximumSpeed>25.0000</MaximumSpeed>")
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun assertWellFormed(tcx: String) {
        assertTrue("Missing xml declaration", tcx.contains("<?xml"))
        assertTrue("Missing <TrainingCenterDatabase>", tcx.contains("<TrainingCenterDatabase"))
        assertTrue("Missing </TrainingCenterDatabase>", tcx.contains("</TrainingCenterDatabase>"))
        assertTrue("Missing <Track>", tcx.contains("<Track>"))
        assertTrue("Missing </Track>", tcx.contains("</Track>"))
    }

    private fun assertEmptyTrack(tcx: String) {
        assertFalse("Expected no <Trackpoint> elements", tcx.contains("<Trackpoint>"))
    }
}
