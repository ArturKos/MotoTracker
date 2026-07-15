package com.mototracker.domain.share

/**
 * Immutable domain model for a ride summary share card.
 *
 * All fields are pre-formatted strings ready for rendering onto the card image;
 * no further formatting or computation is needed by the renderer.
 *
 * @param title           Route display name, e.g. `"Alpine Tour"`.
 * @param dateDisplay     Locale-formatted recording date, e.g. `"Jul 10, 2026"`.
 * @param distanceDisplay Distance with unit, e.g. `"128.4 km"`.
 * @param durationDisplay Duration formatted as h:m:s, e.g. `"2:34:10"`.
 * @param maxSpeedDisplay Peak speed with unit, e.g. `"142 km/h"`.
 * @param bikeName        Motorcycle display name, e.g. `"Yamaha MT-07"`.
 * @param thumbnailPathD  SVG path `d` string for the route track thumbnail (320×200 viewBox),
 *                        as produced by [com.mototracker.core.format.RouteThumbnail.buildPathD].
 */
data class RideShareCard(
    val title: String,
    val dateDisplay: String,
    val distanceDisplay: String,
    val durationDisplay: String,
    val maxSpeedDisplay: String,
    val bikeName: String,
    val thumbnailPathD: String,
)
