package com.mototracker.ui.screens.routes

/**
 * Sort key for the Routes list.
 *
 * Controls the primary field used when ordering routes.
 */
enum class RouteSortKey {
    /** Sort by recording date ([Route.dateEpochMs]). */
    DATE,
    /** Sort by total distance ([Route.km]). */
    DISTANCE,
    /** Sort by ride duration ([Route.durSec]). */
    DURATION,
    /** Sort by maximum recorded speed ([Route.max]). */
    MAX_SPEED,
}

/**
 * Sort direction for the Routes list.
 *
 * Default is [DESC] so the most recent route appears first.
 */
enum class SortDirection {
    /** Ascending order (smallest / oldest first). */
    ASC,
    /** Descending order (largest / newest first). */
    DESC,
}

/**
 * Filter and sort criteria applied in-memory to the Routes list.
 *
 * @param query        Name substring to match (case-insensitive, trimmed); empty = no name filter.
 * @param bikeId       Restrict to routes with this bike ID; `null` = all bikes.
 * @param fromEpochMs  Inclusive lower bound on [Route.dateEpochMs]; `null` = open-ended.
 * @param toEpochMs    Inclusive upper bound on [Route.dateEpochMs]; `null` = open-ended.
 * @param sortKey      Primary sort field; defaults to [RouteSortKey.DATE].
 * @param sortDir      Sort direction; defaults to [SortDirection.DESC] (newest first).
 */
data class RoutesFilter(
    val query: String = "",
    val bikeId: String? = null,
    val fromEpochMs: Long? = null,
    val toEpochMs: Long? = null,
    val sortKey: RouteSortKey = RouteSortKey.DATE,
    val sortDir: SortDirection = SortDirection.DESC,
)

/**
 * A bike entry for the filter dropdown.
 *
 * @param id    Bike UUID.
 * @param name  User-visible display name of the motorcycle.
 */
data class BikeFilterOption(val id: String, val name: String)

/**
 * UI state for the Routes list screen.
 *
 * [routeCount] and [totalKmDisplay] reflect the *filtered* set.
 * [totalRouteCount] carries the unfiltered total, enabling an "X of Y" hint.
 *
 * @param routeCount        Number of routes after applying [filter].
 * @param totalRouteCount   Total number of routes before any filtering.
 * @param totalKmDisplay    Formatted total distance over the filtered routes, e.g. `"1 234.5 km"`.
 * @param distanceUnitLabel Unit label for distance, e.g. `"km"` or `"mi"`.
 * @param cards             Filtered and sorted route card view-models.
 * @param filter            Currently active filter / sort criteria.
 * @param availableBikes    Options for the bike filter dropdown.
 */
data class RoutesUiState(
    val routeCount: Int = 0,
    val totalRouteCount: Int = 0,
    val totalKmDisplay: String = "0.0 km",
    val distanceUnitLabel: String = "km",
    val cards: List<RouteCardUi> = emptyList(),
    val filter: RoutesFilter = RoutesFilter(),
    val availableBikes: List<BikeFilterOption> = emptyList(),
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
