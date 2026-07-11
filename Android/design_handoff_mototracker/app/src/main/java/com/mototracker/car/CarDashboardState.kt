package com.mototracker.car

/**
 * Action buttons available on the Android Auto recording screen.
 *
 * The set shown depends on [com.mototracker.ui.screens.record.RecordingPhase]:
 * - Idle → [Start]
 * - Recording → [Pause], [Stop]
 * - Paused → [Resume], [Stop]
 */
enum class CarAction { Start, Pause, Resume, Stop }

/**
 * Immutable state snapshot for the Android Auto glanceable recording screen.
 *
 * All values are pre-formatted strings ready for display in Car App Library
 * template rows — no further unit conversion or formatting needed by the Screen.
 *
 * @param speedText      Numeric speed value, e.g. `"120"` or `"75"`.
 * @param speedUnit      Unit label, e.g. `"km/h"` or `"mph"`.
 * @param timeText       Elapsed ride time, e.g. `"1:23:45"` or `"7:03"`.
 * @param distanceText   Numeric distance value, e.g. `"128.4"` or `"79.8"`.
 * @param distanceUnit   Unit label, e.g. `"km"` or `"mi"`.
 * @param leanText       Current lean angle, e.g. `"32°"`.
 * @param altitudeText   Numeric altitude value, e.g. `"1840"` or `"6037"`.
 * @param altitudeUnit   Unit label, e.g. `"m"` or `"ft"`.
 * @param actions        Ordered list of action buttons to show, max 2.
 */
data class CarDashboardState(
    val speedText: String,
    val speedUnit: String,
    val timeText: String,
    val distanceText: String,
    val distanceUnit: String,
    val leanText: String,
    val altitudeText: String,
    val altitudeUnit: String,
    val actions: List<CarAction>,
)
