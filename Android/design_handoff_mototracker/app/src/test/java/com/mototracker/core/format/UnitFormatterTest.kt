package com.mototracker.core.format

import com.mototracker.ui.state.Units
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitFormatterTest {

    // --- Labels ---

    @Test
    fun `speedUnitLabel metric`() = assertEquals("km/h", UnitFormatter.speedUnitLabel(Units.METRIC))

    @Test
    fun `speedUnitLabel imperial`() = assertEquals("mph", UnitFormatter.speedUnitLabel(Units.IMPERIAL))

    @Test
    fun `distanceUnitLabel metric`() = assertEquals("km", UnitFormatter.distanceUnitLabel(Units.METRIC))

    @Test
    fun `distanceUnitLabel imperial`() = assertEquals("mi", UnitFormatter.distanceUnitLabel(Units.IMPERIAL))

    @Test
    fun `altitudeUnitLabel metric`() = assertEquals("m", UnitFormatter.altitudeUnitLabel(Units.METRIC))

    @Test
    fun `altitudeUnitLabel imperial`() = assertEquals("ft", UnitFormatter.altitudeUnitLabel(Units.IMPERIAL))

    // --- formatSpeed ---

    @Test
    fun `formatSpeed metric identity`() {
        assertEquals("120 km/h", UnitFormatter.formatSpeed(120.0, Units.METRIC))
    }

    @Test
    fun `formatSpeed rounds 99_9 to 100`() {
        // toInt() would give 99; roundToInt() must give 100 (matches prototype Math.round)
        assertEquals("100 km/h", UnitFormatter.formatSpeed(99.9, Units.METRIC))
    }

    @Test
    fun `formatSpeed rounds half up`() {
        // 99.5 rounds up to 100
        assertEquals("100 km/h", UnitFormatter.formatSpeed(99.5, Units.METRIC))
    }

    @Test
    fun `formatSpeed imperial conversion`() {
        // 100 km/h * 0.621371 = 62.1371 → rounds to 62 mph
        assertEquals("62 mph", UnitFormatter.formatSpeed(100.0, Units.IMPERIAL))
    }

    @Test
    fun `formatSpeed zero`() {
        assertEquals("0 km/h", UnitFormatter.formatSpeed(0.0, Units.METRIC))
        assertEquals("0 mph", UnitFormatter.formatSpeed(0.0, Units.IMPERIAL))
    }

    // --- formatDistance ---

    @Test
    fun `formatDistance metric identity`() {
        assertEquals("128.4 km", UnitFormatter.formatDistance(128.4, Units.METRIC))
    }

    @Test
    fun `formatDistance metric rounds to 1 decimal`() {
        assertEquals("10.6 km", UnitFormatter.formatDistance(10.55, Units.METRIC))
    }

    @Test
    fun `formatDistance imperial conversion`() {
        // 100 km * 0.621371 = 62.1371 → "62.1 mi"
        assertEquals("62.1 mi", UnitFormatter.formatDistance(100.0, Units.IMPERIAL))
    }

    @Test
    fun `formatDistance zero`() {
        assertEquals("0.0 km", UnitFormatter.formatDistance(0.0, Units.METRIC))
        assertEquals("0.0 mi", UnitFormatter.formatDistance(0.0, Units.IMPERIAL))
    }

    // --- formatAltitude ---

    @Test
    fun `formatAltitude metric identity`() {
        assertEquals("1840 m", UnitFormatter.formatAltitude(1840.0, Units.METRIC))
    }

    @Test
    fun `formatAltitude imperial conversion`() {
        // 1000 m * 3.28084 = 3280.84 → rounds to 3281 ft
        assertEquals("3281 ft", UnitFormatter.formatAltitude(1000.0, Units.IMPERIAL))
    }

    @Test
    fun `formatAltitude zero`() {
        assertEquals("0 m", UnitFormatter.formatAltitude(0.0, Units.METRIC))
        assertEquals("0 ft", UnitFormatter.formatAltitude(0.0, Units.IMPERIAL))
    }

    // --- formatHms ---

    @Test
    fun `formatHms zero seconds`() {
        assertEquals("0:00", UnitFormatter.formatHms(0L))
    }

    @Test
    fun `formatHms seconds only`() {
        assertEquals("0:33", UnitFormatter.formatHms(33L))
    }

    @Test
    fun `formatHms minutes and seconds without hours`() {
        assertEquals("7:03", UnitFormatter.formatHms(423L))
    }

    @Test
    fun `formatHms exactly one hour`() {
        assertEquals("1:00:00", UnitFormatter.formatHms(3600L))
    }

    @Test
    fun `formatHms hours minutes seconds`() {
        // 7653 s = 2h 7m 33s
        assertEquals("2:07:33", UnitFormatter.formatHms(7653L))
    }

    @Test
    fun `formatHms pads single-digit minutes and seconds when hours present`() {
        // 3661 s = 1h 1m 1s
        assertEquals("1:01:01", UnitFormatter.formatHms(3661L))
    }

    @Test
    fun `formatHms negative input treated as zero`() {
        assertEquals("0:00", UnitFormatter.formatHms(-1L))
    }
}
