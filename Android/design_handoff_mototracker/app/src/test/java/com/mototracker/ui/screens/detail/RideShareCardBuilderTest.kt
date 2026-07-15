package com.mototracker.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [RideShareCardBuilder] mapping from [RouteDetailUiState].
 */
class RideShareCardBuilderTest {

    private val sampleState = RouteDetailUiState(
        loading = false,
        routeNotFound = false,
        name = "Alpine Tour",
        dateDisplay = "Jul 10, 2026",
        bikeName = "Yamaha MT-07",
        distanceTile = StatTileUi(value = "128.4", unit = "km"),
        durationTile = StatTileUi(value = "2:34:10", unit = "h:m:s"),
        maxTile = StatTileUi(value = "142", unit = "km/h"),
        thumbnailPathD = "M 12 45 L 30 60 L 80 40",
    )

    @Test
    fun `title maps from name`() {
        val card = RideShareCardBuilder.from(sampleState)
        assertEquals("Alpine Tour", card.title)
    }

    @Test
    fun `dateDisplay maps from dateDisplay`() {
        val card = RideShareCardBuilder.from(sampleState)
        assertEquals("Jul 10, 2026", card.dateDisplay)
    }

    @Test
    fun `distanceDisplay combines value and unit with space`() {
        val card = RideShareCardBuilder.from(sampleState)
        assertEquals("128.4 km", card.distanceDisplay)
    }

    @Test
    fun `durationDisplay maps from durationTile value only`() {
        val card = RideShareCardBuilder.from(sampleState)
        assertEquals("2:34:10", card.durationDisplay)
    }

    @Test
    fun `maxSpeedDisplay combines value and unit with space`() {
        val card = RideShareCardBuilder.from(sampleState)
        assertEquals("142 km/h", card.maxSpeedDisplay)
    }

    @Test
    fun `bikeName maps from bikeName`() {
        val card = RideShareCardBuilder.from(sampleState)
        assertEquals("Yamaha MT-07", card.bikeName)
    }

    @Test
    fun `thumbnailPathD maps from thumbnailPathD`() {
        val card = RideShareCardBuilder.from(sampleState)
        assertEquals("M 12 45 L 30 60 L 80 40", card.thumbnailPathD)
    }

    @Test
    fun `blank name produces empty title`() {
        val card = RideShareCardBuilder.from(sampleState.copy(name = ""))
        assertEquals("", card.title)
    }

    @Test
    fun `empty thumbnailPathD is preserved`() {
        val card = RideShareCardBuilder.from(sampleState.copy(thumbnailPathD = ""))
        assertEquals("", card.thumbnailPathD)
    }
}
