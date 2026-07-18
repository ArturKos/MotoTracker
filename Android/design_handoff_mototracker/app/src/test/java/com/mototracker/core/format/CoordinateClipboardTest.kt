package com.mototracker.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for [CoordinateClipboard].
 *
 * All tests run on the JVM with no Android framework dependencies.
 */
class CoordinateClipboardTest {

    private val refLat = 51.26059
    private val refLng = 15.56916

    // ── clipboardText ─────────────────────────────────────────────────────────

    @Test
    fun `clipboardText returns exactly 3 lines`() {
        val result = CoordinateClipboard.clipboardText(refLat, refLng)
        val lines = result.lines()
        assertEquals("expected 3 lines", 3, lines.size)
    }

    @Test
    fun `clipboardText first line starts with DD and matches formatter`() {
        val result = CoordinateClipboard.clipboardText(refLat, refLng)
        val line = result.lines()[0]
        assertTrue("line should start with 'DD '", line.startsWith("DD "))
        val expected = CoordinateFormatter.format(refLat, refLng, CoordFormat.DECIMAL_DEGREES)
        assertEquals("DD $expected", line)
    }

    @Test
    fun `clipboardText second line starts with DMS and matches formatter`() {
        val result = CoordinateClipboard.clipboardText(refLat, refLng)
        val line = result.lines()[1]
        assertTrue("line should start with 'DMS '", line.startsWith("DMS "))
        val expected = CoordinateFormatter.format(refLat, refLng, CoordFormat.DMS)
        assertEquals("DMS $expected", line)
    }

    @Test
    fun `clipboardText third line starts with UTM and matches formatter`() {
        val result = CoordinateClipboard.clipboardText(refLat, refLng)
        val line = result.lines()[2]
        assertTrue("line should start with 'UTM '", line.startsWith("UTM "))
        val expected = CoordinateFormatter.format(refLat, refLng, CoordFormat.UTM)
        assertEquals("UTM $expected", line)
    }

    // ── mapsUrl ───────────────────────────────────────────────────────────────

    @Test
    fun `mapsUrl builds exact expected URL`() {
        val url = CoordinateClipboard.mapsUrl(refLat, refLng)
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=51.260590,15.569160",
            url,
        )
    }

    @Test
    fun `mapsUrl uses dot decimal separator under comma locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("pl", "PL"))
            val url = CoordinateClipboard.mapsUrl(refLat, refLng)
            assertTrue("URL must not contain a comma in the numeric part", !url.contains("51,") && !url.contains("15,"))
            assertTrue("URL must contain dot decimal in lat", url.contains("51."))
            assertTrue("URL must contain dot decimal in lng", url.contains("15."))
        } finally {
            Locale.setDefault(original)
        }
    }
}
