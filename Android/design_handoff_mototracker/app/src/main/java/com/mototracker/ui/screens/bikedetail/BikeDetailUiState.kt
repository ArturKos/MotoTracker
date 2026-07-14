package com.mototracker.ui.screens.bikedetail

/**
 * A single route row shown in the bike-detail route list.
 *
 * @param id               Route UUID.
 * @param name             User-facing route name.
 * @param dateDisplay      Formatted date string (locale-aware short format).
 * @param distanceDisplay  Formatted distance with unit label.
 */
data class BikeRouteRowUi(
    val id: String,
    val name: String,
    val dateDisplay: String,
    val distanceDisplay: String,
)

/**
 * Immutable UI state for the Bike Detail screen.
 *
 * All display values are pre-formatted by [BikeDetailViewModel] so the Composable
 * remains a pure renderer with no formatting logic.
 *
 * @param bikeName             Display name of the motorcycle (header).
 * @param isSold               Whether the bike's status is SOLD; drives the sold badge.
 * @param rideCountDisplay     Formatted ride count string (plain integer).
 * @param totalDistanceDisplay Formatted total distance with unit label.
 * @param totalTimeDisplay     Formatted total elapsed time (h:mm).
 * @param totalFuelDisplay     Formatted total fuel in litres (1 decimal).
 * @param totalCostDisplay     Formatted aggregate fuel cost with currency; null → tile hidden.
 * @param longestRideDisplay   Formatted longest-ride distance with unit label.
 * @param topSpeedDisplay      Formatted top speed with unit label.
 * @param routes               Bike's routes sorted by date descending.
 * @param isLoading            True while the data stream has not emitted its first value.
 */
data class BikeDetailUiState(
    val bikeName: String = "",
    val isSold: Boolean = false,
    val rideCountDisplay: String = "0",
    val totalDistanceDisplay: String = "0.0 km",
    val totalTimeDisplay: String = "0:00",
    val totalFuelDisplay: String = "0.0 L",
    val totalCostDisplay: String? = null,
    val longestRideDisplay: String = "0.0 km",
    val topSpeedDisplay: String = "0 km/h",
    val routes: List<BikeRouteRowUi> = emptyList(),
    val isLoading: Boolean = true,
)
