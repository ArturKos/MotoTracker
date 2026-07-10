package com.mototracker.ui.screens.detail

import com.mototracker.core.format.WeatherUi

/**
 * Single value + unit for one stat tile on the route-detail screen.
 *
 * @param value Formatted numeric string, e.g. `"128.4"`.
 * @param unit  Unit label string, e.g. `"km"` or `"km/h"`.
 */
data class StatTileUi(val value: String, val unit: String)

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
 * @param meetings          List of Bluetooth wave meetups recorded on this route.
 * @param meetingsNone      `true` when [meetings] is empty.
 * @param queued            `true` when the route has not yet been synced (drives "Send" button).
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
    val weather: WeatherUi = WeatherUi(offline = true, tempDisplay = "—", humDisplay = "—", rainLabel = "—"),
    val speedStroke: String = "",
    val speedFill: String = "",
    val elevStroke: String = "",
    val elevFill: String = "",
    val elevGainLabel: String = "",
    val thumbnailPathD: String = "",
    val meetings: List<MeetingUi> = emptyList(),
    val meetingsNone: Boolean = true,
    val queued: Boolean = false,
)
