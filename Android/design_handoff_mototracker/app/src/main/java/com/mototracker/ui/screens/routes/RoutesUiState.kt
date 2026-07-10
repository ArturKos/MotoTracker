package com.mototracker.ui.screens.routes

/**
 * UI state for the Routes list screen.
 *
 * @param routeCount         Total number of saved routes.
 * @param totalKmDisplay     Formatted total distance across all routes, e.g. `"1 234.5 km"`.
 * @param distanceUnitLabel  Unit label for distance, e.g. `"km"` or `"mi"`.
 * @param cards              Ordered list of route card view-models (newest first).
 */
data class RoutesUiState(
    val routeCount: Int = 0,
    val totalKmDisplay: String = "0.0 km",
    val distanceUnitLabel: String = "km",
    val cards: List<RouteCardUi> = emptyList(),
)

/**
 * View-model for a single route card in the Routes list.
 *
 * All display strings are pre-formatted so the Composable stays a pure renderer.
 *
 * @param id                 Route UUID — used as the navigation argument.
 * @param name               User-visible route name.
 * @param dateDisplay        Short locale-formatted date string, e.g. `"Jul 10, 2026"`.
 * @param bikeName           Display name of the associated motorcycle, or `"—"` if unknown.
 * @param bikeSold           `true` when the associated bike has been sold.
 * @param distanceDisplay    Formatted distance, e.g. `"128.4 km"`.
 * @param distanceUnitLabel  Unit label carried separately for column alignment.
 * @param durationDisplay    Formatted ride time, e.g. `"2:07:33"`.
 * @param maxSpeedDisplay    Formatted maximum speed, e.g. `"142 km/h"`.
 * @param thumbnailPathD     SVG path `d` string for the mini route map; empty = show placeholder.
 * @param synced             `true` → show sync check-mark; `false` → show QUEUE tag.
 */
data class RouteCardUi(
    val id: String,
    val name: String,
    val dateDisplay: String,
    val bikeName: String,
    val bikeSold: Boolean,
    val distanceDisplay: String,
    val distanceUnitLabel: String,
    val durationDisplay: String,
    val maxSpeedDisplay: String,
    val thumbnailPathD: String,
    val synced: Boolean,
)
