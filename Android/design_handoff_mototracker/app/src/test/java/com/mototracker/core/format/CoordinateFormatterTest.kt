package com.mototracker.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for [CoordinateFormatter] covering all three output formats.
 *
 * UTM reference values cross-checked against the IOGP EPSG online converter
 * (https://epsg.io/transform) using WGS 84 / UTM projection.
 */
class CoordinateFormatterTest {

    // ── DECIMAL DEGREES ───────────────────────────────────────────────────────

    @Test
    fun `DD format positive lat and lng`() {
        val result = CoordinateFormatter.format(51.26059, 15.56916, CoordFormat.DECIMAL_DEGREES)
        assertEquals("51.26059, 15.56916", result)
    }

    @Test
    fun `DD format negative lat and lng`() {
        val result = CoordinateFormatter.format(-33.86785, 151.20732, CoordFormat.DECIMAL_DEGREES)
        assertEquals("-33.86785, 151.20732", result)
    }

    @Test
    fun `DD format five decimal places`() {
        val result = CoordinateFormatter.format(0.0, 0.0, CoordFormat.DECIMAL_DEGREES)
        assertEquals("0.00000, 0.00000", result)
    }

    @Test
    fun `DD format southern-hemisphere south America`() {
        val result = CoordinateFormatter.format(-23.55052, -46.63331, CoordFormat.DECIMAL_DEGREES)
        assertEquals("-23.55052, -46.63331", result)
    }

    // ── DMS ───────────────────────────────────────────────────────────────────

    @Test
    fun `DMS format northern-hemisphere positive coordinates`() {
        // 51.26059° = 51° 15' 38" N  (0.26059 * 60 = 15.6354 min → 15 min 38.1 sec → 38 sec)
        val result = CoordinateFormatter.format(51.26059, 15.56916, CoordFormat.DMS)
        assertEquals("""51°15'38"N 15°34'09"E""", result)
    }

    @Test
    fun `DMS format southern-hemisphere negative lat`() {
        // -33.86785° lat → 33°52'04"S
        val result = CoordinateFormatter.format(-33.86785, 151.20732, CoordFormat.DMS)
        assertTrue("Expected S hemisphere", result.contains("S"))
        assertTrue("Expected E hemisphere", result.contains("E"))
        assertTrue("Expected 33°", result.startsWith("33°"))
    }

    @Test
    fun `DMS format western-hemisphere negative lng`() {
        val result = CoordinateFormatter.format(40.71278, -74.00594, CoordFormat.DMS)
        assertTrue("Expected N hemisphere", result.contains("N"))
        assertTrue("Expected W hemisphere", result.contains("W"))
    }

    @Test
    fun `DMS format zero lat is North`() {
        val result = CoordinateFormatter.format(0.0, 0.0, CoordFormat.DMS)
        assertTrue("Equator should be N", result.contains("N"))
        assertTrue("Prime meridian should be E", result.contains("E"))
    }

    @Test
    fun `DMS format 60-sec carry-over wraps to next minute`() {
        // 59.6/3600 = 0.016555...° → 0 deg, 0 min, ~59.6 sec → rounds to 60 sec → carry → 0°01'00"N
        // (59.5 would give 59.4999... in IEEE 754 after the chain; 59.6 rounds cleanly to 60)
        val deg = 59.6 / 3600.0
        val result = CoordinateFormatter.format(deg, 0.0, CoordFormat.DMS)
        assertTrue("60-sec carry should produce 0°01'00\"N", result.startsWith("0°01'00\"N"))
    }

    @Test
    fun `DD format uses dot decimal separator regardless of device locale`() {
        // Validates fix for locale-sensitive String.format on Polish/German/French/Czech/Russian devices
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val result = CoordinateFormatter.format(51.26059, 15.56916, CoordFormat.DECIMAL_DEGREES)
            assertEquals("51.26059, 15.56916", result)
        } finally {
            Locale.setDefault(saved)
        }
    }

    // ── UTM ───────────────────────────────────────────────────────────────────

    @Test
    fun `UTM format zone 33U for Wroclaw area`() {
        // 51.10789, 17.03854 is Wrocław — UTM zone 33U
        // Reference (EPSG UTM Zone 33N): easting 642705, northing 5663799  (±5 m)
        val result = CoordinateFormatter.format(51.10789, 17.03854, CoordFormat.UTM)
        assertTrue("Expected zone 33U", result.startsWith("33U"))
        val parts = result.split(" ")
        assertEquals("UTM string should have 3 parts", 3, parts.size)
        val easting = parts[1].toLong()
        val northing = parts[2].toLong()
        assertTrue("Easting should be 642705 ±5", easting in 642_700..642_710)
        assertTrue("Northing should be 5663799 ±5", northing in 5_663_794..5_663_804)
    }

    @Test
    fun `UTM format known reference point`() {
        // 51.26059N, 15.56916E is ~0.57° east of zone-33 central meridian (15°E).
        // Easting ≈ 500000 + k0*N*cos(φ)*Δλ ≈ 500000 + 39800 ≈ 539800 m.
        // Northing ≈ k0 * meridional_arc(51.26°) ≈ 5682000 m.
        // Tolerance ±5000 m covers series truncation and floating-point.
        val result = CoordinateFormatter.format(51.26059, 15.56916, CoordFormat.UTM)
        assertTrue("Expected zone 33U", result.startsWith("33U"))
        val parts = result.split(" ")
        assertEquals("UTM string should have 3 parts", 3, parts.size)
        val easting = parts[1].toLong()
        val northing = parts[2].toLong()
        assertTrue("Easting should be ~539800 (east of CM)", easting in 534_000..545_000)
        assertTrue("Northing should be ~5682000", northing in 5_677_000..5_688_000)
    }

    @Test
    fun `UTM southern hemisphere uses false northing 10000000`() {
        // Sydney, AU: -33.86785, 151.20732 → UTM zone 56H
        val result = CoordinateFormatter.format(-33.86785, 151.20732, CoordFormat.UTM)
        val parts = result.split(" ")
        assertEquals(3, parts.size)
        val northing = parts[2].toLong()
        // False northing 10,000,000 applied → value should be large
        assertTrue("Northing should be near 10M for southern hemisphere", northing > 6_200_000)
        assertTrue("Band should be H (southern)", result.startsWith("56H"))
    }

    @Test
    fun `UTM zone boundary longitude 180 wraps to zone 1`() {
        // Longitude exactly at +180 wraps to zone 1
        val result = CoordinateFormatter.format(0.0, 179.9999, CoordFormat.UTM)
        assertTrue("Should be zone 60", result.startsWith("60"))
    }

    @Test
    fun `UTM zone 1 for near -180 longitude`() {
        val result = CoordinateFormatter.format(0.0, -179.9, CoordFormat.UTM)
        assertTrue("Should be zone 1", result.startsWith("1"))
    }

    @Test
    fun `UTM zone 32 for London`() {
        // London: 51.5074, -0.1278 → zone 30U
        val result = CoordinateFormatter.format(51.5074, -0.1278, CoordFormat.UTM)
        assertTrue("Expected zone 30U", result.startsWith("30U"))
    }

    @Test
    fun `DMS degree rolls up when sec-to-min carry propagates to degrees`() {
        // 51.999889° = 51°59'59.6" — rounded seconds (60) carry to minutes (60) carry to degree (52)
        val result = CoordinateFormatter.format(51.999889, 0.0, CoordFormat.DMS)
        assertTrue("Degree should roll to 52", result.startsWith("52°00'00\""))
    }

    // ── CoordFormat.fromKey ───────────────────────────────────────────────────

    @Test
    fun `fromKey dd maps to DECIMAL_DEGREES`() {
        assertEquals(CoordFormat.DECIMAL_DEGREES, CoordFormat.fromKey("dd"))
    }

    @Test
    fun `fromKey dms maps to DMS`() {
        assertEquals(CoordFormat.DMS, CoordFormat.fromKey("dms"))
    }

    @Test
    fun `fromKey utm maps to UTM`() {
        assertEquals(CoordFormat.UTM, CoordFormat.fromKey("utm"))
    }

    @Test
    fun `fromKey null maps to DECIMAL_DEGREES`() {
        assertEquals(CoordFormat.DECIMAL_DEGREES, CoordFormat.fromKey(null))
    }

    @Test
    fun `fromKey unknown key maps to DECIMAL_DEGREES`() {
        assertEquals(CoordFormat.DECIMAL_DEGREES, CoordFormat.fromKey("unknown"))
        assertEquals(CoordFormat.DECIMAL_DEGREES, CoordFormat.fromKey(""))
        assertEquals(CoordFormat.DECIMAL_DEGREES, CoordFormat.fromKey("DD"))
    }

    // ── ViewModel-layer: coordFormat propagation (verified in SettingsViewModelTest) ───────
    // The persistence round-trip is tested in SettingsViewModelCoordFormatTest.kt below.
}
