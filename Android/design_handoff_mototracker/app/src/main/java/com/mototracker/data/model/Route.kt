package com.mototracker.data.model

/**
 * Pure domain model representing a recorded motorcycle route.
 *
 * JSON fields are kept as raw strings; parsing them into structured types is
 * the responsibility of screen-level mappers or use-cases.
 *
 * @param id               UUID string.
 * @param name             User-editable route name.
 * @param dateEpochMs      Recording start time in milliseconds since epoch.
 * @param bikeId           FK to the bike used on this route; null if not set.
 * @param km               Total distance in kilometres.
 * @param durSec           Duration in seconds.
 * @param avg              Average speed in km/h.
 * @param max              Maximum speed in km/h.
 * @param lean             Maximum lean angle in degrees.
 * @param elev             Total elevation gain in metres.
 * @param fuel             Estimated fuel consumed in litres.
 * @param synced           Whether successfully uploaded to the GPStrack server.
 * @param wxJson           Serialised weather object; null if offline at recording time.
 * @param pathJson         Serialised GPS coordinate list.
 * @param speedJson        Serialised speed-over-time array.
 * @param elevProfileJson  Serialised elevation-over-distance array.
 * @param notes            Free-text notes.
 */
data class Route(
    val id: String,
    val name: String,
    val dateEpochMs: Long,
    val bikeId: String?,
    val km: Double,
    val durSec: Long,
    val avg: Double,
    val max: Double,
    val lean: Double,
    val elev: Double,
    val fuel: Double,
    val synced: Boolean,
    val wxJson: String?,
    val pathJson: String?,
    val speedJson: String?,
    val elevProfileJson: String?,
    val notes: String?,
)
