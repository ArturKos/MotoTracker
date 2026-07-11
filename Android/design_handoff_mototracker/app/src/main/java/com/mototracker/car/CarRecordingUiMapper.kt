package com.mototracker.car

import com.mototracker.core.format.UnitFormatter
import com.mototracker.domain.recording.RecordingMetrics
import com.mototracker.ui.screens.record.RecordingPhase
import com.mototracker.ui.state.Units
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure, Android-free mapper that converts recording domain state into
 * [CarDashboardState] for the Android Auto glanceable screen.
 *
 * Has no Android/Context dependencies — all inputs are plain domain/Kotlin types,
 * making it fully unit-testable on the JVM without any Android framework.
 */
object CarRecordingUiMapper {

    private val LOCALE = Locale.US

    /**
     * Maps live recording state to a [CarDashboardState] ready for display.
     *
     * @param metrics Current live metrics snapshot from [com.mototracker.domain.recording.RecordingEngine].
     * @param phase   Current recording lifecycle phase.
     * @param units   Measurement system preference (metric or imperial).
     * @return        Pre-formatted state for the Car App Library template renderer.
     */
    fun map(
        metrics: RecordingMetrics,
        phase: RecordingPhase,
        units: Units,
    ): CarDashboardState {
        val speedValue = when (units) {
            Units.METRIC -> metrics.currentSpeedKmh.roundToInt().toString()
            Units.IMPERIAL -> (metrics.currentSpeedKmh * 0.621371).roundToInt().toString()
        }

        val distanceValue = when (units) {
            Units.METRIC -> String.format(LOCALE, "%.1f", metrics.distanceKm)
            Units.IMPERIAL -> String.format(LOCALE, "%.1f", metrics.distanceKm * 0.621371)
        }

        val altitudeValue = when (units) {
            Units.METRIC -> metrics.altitudeM.roundToInt().toString()
            Units.IMPERIAL -> (metrics.altitudeM * 3.28084).roundToInt().toString()
        }

        val actions = when (phase) {
            RecordingPhase.Idle -> listOf(CarAction.Start)
            RecordingPhase.Recording -> listOf(CarAction.Pause, CarAction.Stop)
            RecordingPhase.Paused -> listOf(CarAction.Resume, CarAction.Stop)
        }

        return CarDashboardState(
            speedText = speedValue,
            speedUnit = UnitFormatter.speedUnitLabel(units),
            timeText = UnitFormatter.formatHms(metrics.durationSec),
            distanceText = distanceValue,
            distanceUnit = UnitFormatter.distanceUnitLabel(units),
            leanText = "${metrics.currentLeanDeg.roundToInt()}°",
            altitudeText = altitudeValue,
            altitudeUnit = UnitFormatter.altitudeUnitLabel(units),
            actions = actions,
        )
    }
}
