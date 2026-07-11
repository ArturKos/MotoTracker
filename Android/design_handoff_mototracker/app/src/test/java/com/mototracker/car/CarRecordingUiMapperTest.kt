package com.mototracker.car

import com.mototracker.domain.recording.RecordingMetrics
import com.mototracker.ui.screens.record.RecordingPhase
import com.mototracker.ui.state.Units
import org.junit.Assert.assertEquals
import org.junit.Test

class CarRecordingUiMapperTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun metrics(
        speedKmh: Double = 0.0,
        distanceKm: Double = 0.0,
        durationSec: Long = 0L,
        leanDeg: Double = 0.0,
        altitudeM: Double = 0.0,
    ) = RecordingMetrics(
        currentSpeedKmh = speedKmh,
        distanceKm = distanceKm,
        durationSec = durationSec,
        currentLeanDeg = leanDeg,
        altitudeM = altitudeM,
    )

    private fun map(
        m: RecordingMetrics,
        phase: RecordingPhase = RecordingPhase.Idle,
        units: Units = Units.METRIC,
    ) = CarRecordingUiMapper.map(m, phase, units)

    // ── Speed ────────────────────────────────────────────────────────────────

    @Test
    fun `speed metric identity`() {
        val state = map(metrics(speedKmh = 120.0), units = Units.METRIC)
        assertEquals("120", state.speedText)
        assertEquals("km/h", state.speedUnit)
    }

    @Test
    fun `speed metric rounds half up`() {
        val state = map(metrics(speedKmh = 99.5), units = Units.METRIC)
        assertEquals("100", state.speedText)
    }

    @Test
    fun `speed imperial conversion`() {
        // 100 km/h * 0.621371 = 62.1371 → rounds to 62
        val state = map(metrics(speedKmh = 100.0), units = Units.IMPERIAL)
        assertEquals("62", state.speedText)
        assertEquals("mph", state.speedUnit)
    }

    @Test
    fun `speed zero`() {
        val state = map(metrics(speedKmh = 0.0), units = Units.METRIC)
        assertEquals("0", state.speedText)
    }

    // ── Distance ─────────────────────────────────────────────────────────────

    @Test
    fun `distance metric one decimal`() {
        val state = map(metrics(distanceKm = 128.4), units = Units.METRIC)
        assertEquals("128.4", state.distanceText)
        assertEquals("km", state.distanceUnit)
    }

    @Test
    fun `distance imperial conversion`() {
        // 100 km * 0.621371 = 62.1371 → "62.1"
        val state = map(metrics(distanceKm = 100.0), units = Units.IMPERIAL)
        assertEquals("62.1", state.distanceText)
        assertEquals("mi", state.distanceUnit)
    }

    @Test
    fun `distance zero metric`() {
        val state = map(metrics(distanceKm = 0.0), units = Units.METRIC)
        assertEquals("0.0", state.distanceText)
    }

    // ── Altitude ─────────────────────────────────────────────────────────────

    @Test
    fun `altitude metric identity`() {
        val state = map(metrics(altitudeM = 1840.0), units = Units.METRIC)
        assertEquals("1840", state.altitudeText)
        assertEquals("m", state.altitudeUnit)
    }

    @Test
    fun `altitude imperial conversion`() {
        // 1000 m * 3.28084 = 3280.84 → rounds to 3281
        val state = map(metrics(altitudeM = 1000.0), units = Units.IMPERIAL)
        assertEquals("3281", state.altitudeText)
        assertEquals("ft", state.altitudeUnit)
    }

    @Test
    fun `altitude zero`() {
        val state = map(metrics(altitudeM = 0.0), units = Units.METRIC)
        assertEquals("0", state.altitudeText)
    }

    // ── Time ─────────────────────────────────────────────────────────────────

    @Test
    fun `time zero`() {
        val state = map(metrics(durationSec = 0L))
        assertEquals("0:00", state.timeText)
    }

    @Test
    fun `time minutes and seconds`() {
        // 423 s = 7m 3s
        val state = map(metrics(durationSec = 423L))
        assertEquals("7:03", state.timeText)
    }

    @Test
    fun `time hours minutes seconds`() {
        // 7653 s = 2h 7m 33s
        val state = map(metrics(durationSec = 7653L))
        assertEquals("2:07:33", state.timeText)
    }

    // ── Lean ─────────────────────────────────────────────────────────────────

    @Test
    fun `lean rounded with degree symbol`() {
        val state = map(metrics(leanDeg = 32.7))
        assertEquals("33°", state.leanText)
    }

    @Test
    fun `lean zero`() {
        val state = map(metrics(leanDeg = 0.0))
        assertEquals("0°", state.leanText)
    }

    @Test
    fun `lean negative rounds correctly`() {
        val state = map(metrics(leanDeg = -14.5))
        // kotlin roundToInt: -14.5 → -14 (rounds towards positive infinity, i.e. "half up")
        // Actually Kotlin's roundToInt uses HALF_UP semantics which rounds -14.5 → -14
        assertEquals("-14°", state.leanText)
    }

    // ── Phase → actions ──────────────────────────────────────────────────────

    @Test
    fun `Idle phase produces Start action only`() {
        val state = map(metrics(), phase = RecordingPhase.Idle)
        assertEquals(listOf(CarAction.Start), state.actions)
    }

    @Test
    fun `Recording phase produces Pause and Stop actions`() {
        val state = map(metrics(), phase = RecordingPhase.Recording)
        assertEquals(listOf(CarAction.Pause, CarAction.Stop), state.actions)
    }

    @Test
    fun `Paused phase produces Resume and Stop actions`() {
        val state = map(metrics(), phase = RecordingPhase.Paused)
        assertEquals(listOf(CarAction.Resume, CarAction.Stop), state.actions)
    }
}
