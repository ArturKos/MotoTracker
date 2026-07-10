package com.mototracker.ui.screens.record

import com.mototracker.domain.recording.RecordingMetrics

/** Recording session lifecycle phase. */
enum class RecordingPhase { Idle, Recording, Paused }

/**
 * Weather conditions captured at recording time.
 *
 * @param tempC  Temperature in Celsius.
 * @param humPct Relative humidity 0–100.
 * @param rain   Whether precipitation is occurring.
 */
data class WeatherInfo(val tempC: Int, val humPct: Int, val rain: Boolean)

/**
 * Full UI state for the Recording screen.
 *
 * Drives all Compose rendering without any Android context in the data layer.
 *
 * @param phase          Current recording lifecycle phase.
 * @param metrics        Live cumulative metrics from [com.mototracker.domain.recording.RecordingEngine].
 * @param gpsSatCount    Number of GPS satellites in use; shown in the GPS chip.
 * @param gpsOnRoad      Whether GPS-to-road correction is active (from settings).
 * @param weather        Current weather, or null when offline / not yet fetched.
 */
data class RecordingUiState(
    val phase: RecordingPhase = RecordingPhase.Idle,
    val metrics: RecordingMetrics = RecordingMetrics(),
    val gpsSatCount: Int = 0,
    val gpsOnRoad: Boolean = false,
    val weather: WeatherInfo? = null,
)

/** One-shot events dispatched from the Recording screen to the ViewModel. */
sealed class RecordingEvent {
    /** User tapped "Start ride". */
    data object Start : RecordingEvent()
    /** User tapped "Pause". */
    data object Pause : RecordingEvent()
    /** User tapped "Resume". */
    data object Resume : RecordingEvent()
    /** User tapped "Finish". */
    data object Finish : RecordingEvent()
}

/** One-shot side-effects emitted by the ViewModel to the UI layer. */
sealed class RecordingEffect {
    /**
     * The route was saved successfully.
     *
     * @param offline `true` when the route was saved locally (no server upload was attempted).
     */
    data class Saved(val offline: Boolean) : RecordingEffect()

    /**
     * Navigate to the detail screen for the just-saved route.
     * Wired to actual navigation in B4; the screen may no-op until then.
     *
     * @param routeId UUID of the saved route.
     */
    data class NavigateToDetail(val routeId: String) : RecordingEffect()
}
