package com.mototracker.ui.screens.detail

import com.mototracker.core.format.WeatherUi
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.ui.map.GeoCoord

/**
 * Single value + unit for one stat tile on the route-detail screen.
 *
 * @param value Formatted numeric string, e.g. `"128.4"`.
 * @param unit  Unit label string, e.g. `"km"` or `"km/h"`.
 */
data class StatTileUi(val value: String, val unit: String)

/**
 * One motorcycle entry in the "change bike" picker.
 *
 * Sold bikes appear only when they are the currently-assigned bike on the route
 * (so the existing selection is always representable in the picker).
 *
 * @param id   UUID of the motorcycle ([com.mototracker.data.model.Bike.id]).
 * @param name Display name, e.g. `"Yamaha MT-07"`.
 * @param sold `true` when the bike has [com.mototracker.data.local.entity.BikeStatus.SOLD] status.
 */
data class BikePickerItemUi(val id: String, val name: String, val sold: Boolean)

/**
 * One Bluetooth "wave" meetup entry on the route-detail screen.
 *
 * @param initials  First two letters of [who] in uppercase, used for the avatar circle.
 * @param who       The other rider's display nickname.
 * @param bikeName  The other rider's bike model.
 * @param place     Human-readable location label where the wave occurred.
 * @param timeLabel Formatted time string, e.g. `"14:32"`.
 */
data class MeetingUi(
    val initials: String,
    val who: String,
    val bikeName: String,
    val place: String,
    val timeLabel: String,
)

/**
 * Immutable UI state for the route-detail screen.
 *
 * All display strings are pre-formatted by [RouteDetailViewModel] so the Composable
 * acts as a pure renderer with no computation.
 *
 * @param loading           `true` while data is being loaded.
 * @param routeNotFound     `true` when no route with the given ID exists in local storage.
 * @param name              Route display name; falls back to the first 8 chars of the route ID.
 * @param dateDisplay       Locale-formatted recording date, e.g. `"Jul 10, 2026"`.
 * @param bikeName          Display name of the associated motorcycle, or `"—"` if unknown.
 * @param bikeSold          `true` when the associated bike has been sold.
 * @param distanceTile      Formatted total distance stat tile.
 * @param durationTile      Formatted ride duration stat tile (h:mm:ss).
 * @param avgTile           Formatted average speed stat tile.
 * @param maxTile           Formatted maximum speed stat tile.
 * @param leanTile          Formatted maximum lean angle stat tile.
 * @param fuelTile          Formatted estimated fuel consumption stat tile.
 * @param weather           Parsed weather snapshot (or offline placeholder).
 * @param speedStroke       SVG-style polyline `points` for the speed chart line (320×90 viewBox).
 * @param speedFill         Closed polyline `points` for the translucent speed chart fill.
 * @param elevStroke        SVG-style polyline `points` for the elevation profile line.
 * @param elevFill          Closed polyline `points` for the translucent elevation fill.
 * @param elevGainLabel     Formatted total elevation gain, e.g. `"↑ 840 m"`.
 * @param thumbnailPathD    SVG path `d` string for the route track thumbnail (320×200 viewBox).
 * @param trackPoints              Ordered GPS coordinates for the currently selected track view;
 *                                 switches between raw and corrected depending on [selectedTrackView].
 *                                 Used by [com.mototracker.ui.map.OsmTrackMap] to draw the polyline.
 * @param meetings                 List of Bluetooth wave meetups recorded on this route.
 * @param meetingsNone             `true` when [meetings] is empty.
 * @param queued                   `true` when the route has not yet been synced (drives "Send" button).
 * @param hasCorrectedTrace        `true` when a road-snapped trace is available for this route.
 * @param correctionStatus         Current GPS-correction pipeline state.
 * @param correctionStatusLabelRes String resource ID for the human-readable correction status label
 *                                 (e.g. "W kolejce", "Niska pewność", "Skorygowano"), or `null` when
 *                                 [correctionStatus] is [CorrectionStatus.NONE].
 * @param confidenceLabel          Formatted OSRM matching-confidence string (e.g. `"87%"`), or empty
 *                                 when confidence data is not available.
 * @param selectedTrackView        Which track layer is currently displayed on the map.
 * @param currentBikeId            The [com.mototracker.data.model.Bike.id] currently assigned to
 *                                 this route, or `null` when no bike is assigned. Mirrors
 *                                 [com.mototracker.data.model.Route.bikeId] so the picker can
 *                                 highlight the active selection.
 * @param assignableBikes          Bikes available in the "change bike" picker. Includes all
 *                                 ACTIVE bikes plus the currently-assigned bike even if it is
 *                                 SOLD, so the current selection is always representable.
 */
data class RouteDetailUiState(
    val loading: Boolean = true,
    val routeNotFound: Boolean = false,
    val name: String = "",
    val dateDisplay: String = "",
    val bikeName: String = "—",
    val bikeSold: Boolean = false,
    val distanceTile: StatTileUi = StatTileUi("", "km"),
    val durationTile: StatTileUi = StatTileUi("", "h:m:s"),
    val avgTile: StatTileUi = StatTileUi("", "km/h"),
    val maxTile: StatTileUi = StatTileUi("", "km/h"),
    val leanTile: StatTileUi = StatTileUi("", "°"),
    val fuelTile: StatTileUi = StatTileUi("", "L"),
    val weather: WeatherUi = WeatherUi(offline = true, tempDisplay = "—", humDisplay = "—", rain = null),
    val speedStroke: String = "",
    val speedFill: String = "",
    val elevStroke: String = "",
    val elevFill: String = "",
    val elevGainLabel: String = "",
    val thumbnailPathD: String = "",
    val trackPoints: List<GeoCoord> = emptyList(),
    val meetings: List<MeetingUi> = emptyList(),
    val meetingsNone: Boolean = true,
    val queued: Boolean = false,
    val hasCorrectedTrace: Boolean = false,
    val correctionStatus: CorrectionStatus = CorrectionStatus.NONE,
    val correctionStatusLabelRes: Int? = null,
    val confidenceLabel: String = "",
    val selectedTrackView: TrackView = TrackView.RAW,
    val currentBikeId: String? = null,
    val assignableBikes: List<BikePickerItemUi> = emptyList(),
)
