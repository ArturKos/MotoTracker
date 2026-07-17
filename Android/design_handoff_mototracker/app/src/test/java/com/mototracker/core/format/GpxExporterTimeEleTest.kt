package com.mototracker.core.format

import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.Route
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for N1 GPX enrichment: `<ele>` and `<time>` elements inside `<trkpt>`.
 *
 * Covers requirement (d): enriched tracks emit `<time>` + `<ele>` per point;
 * legacy lat/lng-only tracks produce bare `<trkpt lat="…" lon="…"/>` elements.
 */
class GpxExporterTimeEleTest {

    private fun makeRoute(pathJson: String?) = Route(
        id = "test-id",
        name = "Test Ride",
        dateEpochMs = 1_700_000_000_000L,
        bikeId = null,
        km = 0.0,
        durSec = 0L,
        avg = 0.0,
        max = 0.0,
        lean = 0.0,
        elev = 0.0,
        fuel = 0.0,
        synced = true,
        wxJson = null,
        pathJson = pathJson,
        speedJson = null,
        elevProfileJson = null,
        notes = null,
        correctionStatus = CorrectionStatus.NONE,
        maxLeanLeftDeg = 0.0,
        maxLeanRightDeg = 0.0,
    )

    // ── Enriched points — ele + time emitted ─────────────────────────────────

    @Test
    fun `toGpx emits ele element when ele is non-zero`() {
        val pathJson = """[{"lat":50.0,"lng":19.0,"ele":250.5,"t":1700000001000}]"""
        val gpx = GpxExporter.toGpx(makeRoute(pathJson))
        assertTrue("should contain <ele>", gpx.contains("<ele>250.5</ele>"))
    }

    @Test
    fun `toGpx emits time element in ISO-8601 UTC when t is present`() {
        // t = 1_700_000_001_000 ms → 2023-11-14T22:13:21Z
        val pathJson = """[{"lat":50.0,"lng":19.0,"ele":100.0,"t":1700000001000}]"""
        val gpx = GpxExporter.toGpx(makeRoute(pathJson))
        assertTrue("should contain <time> with ISO-8601 UTC", gpx.contains("<time>2023-11-14T22:13:21Z</time>"))
    }

    @Test
    fun `toGpx non-self-closing trkpt when children present`() {
        val pathJson = """[{"lat":50.0,"lng":19.0,"ele":100.0,"t":1700000001000}]"""
        val gpx = GpxExporter.toGpx(makeRoute(pathJson))
        assertTrue("should contain closing </trkpt>", gpx.contains("</trkpt>"))
        assertFalse("self-closing trkpt should not appear when children present", gpx.contains("""lat="50.0" lon="19.0"/>"""))
    }

    @Test
    fun `toGpx emits ele and time for multiple enriched points`() {
        val pathJson = """[
            {"lat":50.0,"lng":19.0,"ele":200.0,"t":1700000001000},
            {"lat":50.1,"lng":19.1,"ele":210.0,"t":1700000002000}
        ]"""
        val gpx = GpxExporter.toGpx(makeRoute(pathJson))
        val trkptCount = gpx.split("<trkpt").size - 1
        val eleCount = gpx.split("<ele>").size - 1
        val timeCount = gpx.split("<time>").size - 1

        // 2 trkpts; metadata also has a <time> so timeCount = 3 (2 trkpt + 1 metadata)
        assertTrue("should have 2 trkpt elements", trkptCount == 2)
        assertTrue("should have 2 <ele> elements", eleCount == 2)
        // metadata <time> + 2 trkpt <time>
        assertTrue("should have trkpt <time> elements", timeCount >= 2)
    }

    // ── Legacy points — bare trkpt self-closing ───────────────────────────────

    @Test
    fun `toGpx emits self-closing trkpt for legacy points without ele or t`() {
        val pathJson = """[{"lat":50.0612,"lng":19.9372},{"lat":50.063,"lng":19.939}]"""
        val gpx = GpxExporter.toGpx(makeRoute(pathJson))
        assertTrue("legacy trkpt should be self-closing", gpx.contains("""lat="50.0612" lon="19.9372"/>"""))
        assertFalse("should not contain <ele> for legacy points", gpx.contains("<ele>"))
    }

    @Test
    fun `toGpx does not emit ele when ele is zero`() {
        val pathJson = """[{"lat":50.0,"lng":19.0,"ele":0.0,"t":1700000001000}]"""
        val gpx = GpxExporter.toGpx(makeRoute(pathJson))
        assertFalse("should not emit <ele> when ele=0.0", gpx.contains("<ele>"))
        // But time should still be present
        assertTrue("should still emit <time>", gpx.contains("<time>2023-11-14T22:13:21Z</time>"))
    }

    @Test
    fun `toGpx does not emit time when t is absent`() {
        val pathJson = """[{"lat":50.0,"lng":19.0,"ele":150.0}]"""
        val gpx = GpxExporter.toGpx(makeRoute(pathJson))
        assertTrue("should emit <ele>", gpx.contains("<ele>150.0</ele>"))
        // Only the metadata <time> should appear, not a trkpt <time>
        val timeCount = gpx.split("<time>").size - 1
        // metadata has exactly one <time>; no trkpt <time>
        assertTrue("should have only metadata <time>", timeCount == 1)
    }

    // ── Legacy backward compat via GpxExporter ────────────────────────────────

    @Test
    fun `toGpx produces well-formed GPX for legacy pathJson without ele or t`() {
        val pathJson = """[{"lat":50.0,"lng":19.0},{"lat":50.1,"lng":19.1}]"""
        val gpx = GpxExporter.toGpx(makeRoute(pathJson))
        assertTrue("should contain <trkpt", gpx.contains("<trkpt"))
        assertTrue("should contain lat attribute", gpx.contains("""lat="50.0""""))
        assertFalse("should not contain <ele>", gpx.contains("<ele>"))
    }

    @Test
    fun `toGpx never throws for enriched or legacy or null pathJson`() {
        val cases = listOf(
            null,
            "[]",
            """[{"lat":50.0,"lng":19.0}]""",
            """[{"lat":50.0,"lng":19.0,"ele":100.0}]""",
            """[{"lat":50.0,"lng":19.0,"t":1700000001000}]""",
            """[{"lat":50.0,"lng":19.0,"ele":150.0,"t":1700000001000}]""",
        )
        cases.forEach { pathJson ->
            try {
                GpxExporter.toGpx(makeRoute(pathJson))
            } catch (e: Exception) {
                throw AssertionError("toGpx threw for pathJson=$pathJson: $e")
            }
        }
    }

    @Test
    fun `toGpx emits only time when t present but ele is zero`() {
        val pathJson = """[{"lat":50.0,"lng":19.0,"ele":0.0,"t":1700000001000}]"""
        val gpx = GpxExporter.toGpx(makeRoute(pathJson))
        assertFalse("should not emit <ele> when ele=0.0", gpx.contains("<ele>"))
        assertTrue("should emit <time> when t is present", gpx.contains("<time>2023-11-14T22:13:21Z</time>"))
        assertTrue("should emit closing </trkpt>", gpx.contains("</trkpt>"))
    }
}
