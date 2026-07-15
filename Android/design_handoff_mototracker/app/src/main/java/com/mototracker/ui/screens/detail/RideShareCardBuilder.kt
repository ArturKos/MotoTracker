package com.mototracker.ui.screens.detail

import com.mototracker.domain.share.RideShareCard

/**
 * Pure, Android-free builder that maps a [RouteDetailUiState] snapshot into a [RideShareCard].
 *
 * Lives in the UI layer because it depends on [RouteDetailUiState], which is a UI-layer type.
 * Has no side effects and no Android framework dependencies, so it is fully exercisable
 * in plain JVM unit tests.
 */
object RideShareCardBuilder {

    /**
     * Produces a [RideShareCard] from the given [state] snapshot.
     *
     * All display strings are taken verbatim from the state — the ViewModel has already
     * formatted them for the locale and unit preference.
     *
     * @param state A loaded [RouteDetailUiState] (must not be in loading or not-found condition).
     * @return A fully populated [RideShareCard] ready for card rendering.
     */
    fun from(state: RouteDetailUiState): RideShareCard = RideShareCard(
        title = state.name,
        dateDisplay = state.dateDisplay,
        distanceDisplay = "${state.distanceTile.value} ${state.distanceTile.unit}",
        durationDisplay = state.durationTile.value,
        maxSpeedDisplay = "${state.maxTile.value} ${state.maxTile.unit}",
        bikeName = state.bikeName,
        thumbnailPathD = state.thumbnailPathD,
    )
}
