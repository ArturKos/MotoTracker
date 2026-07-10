package com.mototracker.ui.screens.stats

/**
 * Immutable UI state for the Statistics screen.
 *
 * All display strings are pre-formatted so the Composable remains a pure renderer.
 *
 * @param totalDistanceDisplay  Formatted total distance across all rides, e.g. `"1 234.5 km"`.
 * @param distanceUnitLabel     Unit label for distance, e.g. `"km"` or `"mi"`.
 * @param timeInSaddleDisplay   Total ride time formatted as `"h:mm"`, e.g. `"11:38"`.
 * @param ridesCount            Total number of saved routes.
 * @param topSpeedDisplay       Formatted all-time top speed, e.g. `"175 km/h"`.
 * @param speedUnitLabel        Unit label for speed, e.g. `"km/h"` or `"mph"`.
 * @param monthBars             Up to 6 consecutive calendar months ending at the newest route.
 * @param yearLabel             4-digit year of the newest route shown in the bar-chart header,
 *                              or an empty string when there are no routes.
 * @param style                 Riding-style summary fractions and display strings.
 */
data class StatsUiState(
    val totalDistanceDisplay: String = "0.0 km",
    val distanceUnitLabel: String = "km",
    val timeInSaddleDisplay: String = "0:00",
    val ridesCount: Int = 0,
    val topSpeedDisplay: String = "0 km/h",
    val speedUnitLabel: String = "km/h",
    val monthBars: List<MonthBarUi> = emptyList(),
    val yearLabel: String = "",
    val style: RidingStyleUi = RidingStyleUi(),
)

/**
 * Data for a single bar in the Distance / month chart.
 *
 * @param monthLabel    Localised short month name, e.g. `"Jan"` or `"Sty"`.
 * @param kmDisplay     Formatted distance for the month, e.g. `"324.5 km"`.
 * @param heightFraction Normalised bar height in `[0f, 1f]` where `1f` = tallest bar.
 */
data class MonthBarUi(
    val monthLabel: String,
    val kmDisplay: String,
    val heightFraction: Float,
)

/**
 * Riding-style summary for the three progress bars shown below the bar chart.
 *
 * All `*Fraction` fields are clamped to `[0f, 1f]`:
 * - avgLean:   60 ° full scale  → 38° ≈ 0.63
 * - avgSpeed: 100 km/h full scale → 48 km/h ≈ 0.48
 * - totalClimb: 8 000 m full scale → 5 920 m ≈ 0.74
 *
 * @param avgLeanDisplay     Rounded average lean angle, e.g. `"38°"`.
 * @param avgLeanFraction    avgLean / 60.0, clamped to [0, 1].
 * @param avgSpeedDisplay    Formatted average speed, e.g. `"48 km/h"`.
 * @param avgSpeedFraction   avgSpeed (km/h) / 100.0, clamped to [0, 1].
 * @param totalClimbDisplay  Formatted total elevation gain, e.g. `"5 920 m"`.
 * @param totalClimbFraction totalClimb (m) / 8000.0, clamped to [0, 1].
 */
data class RidingStyleUi(
    val avgLeanDisplay: String = "0°",
    val avgLeanFraction: Float = 0f,
    val avgSpeedDisplay: String = "0 km/h",
    val avgSpeedFraction: Float = 0f,
    val totalClimbDisplay: String = "0 m",
    val totalClimbFraction: Float = 0f,
)
