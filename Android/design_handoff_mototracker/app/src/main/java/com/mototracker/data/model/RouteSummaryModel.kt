package com.mototracker.data.model

import com.mototracker.data.local.entity.CorrectionStatus

/**
 * Lightweight domain summary of a recorded route, excluding large trace blobs.
 *
 * Used by list and statistics screens that only need scalar metrics and the precomputed
 * [thumbnailPathD] string. The full [Route] (including GPS trace) is only loaded when
 * a detail screen or export operation requests it via `RouteRepository.getById`.
 *
 * @param id                UUID string.
 * @param name              User-editable route name.
 * @param dateEpochMs       Recording start time in milliseconds since epoch.
 * @param bikeId            FK to the bike used on this route; null if not set.
 * @param km                Total distance in kilometres.
 * @param durSec            Duration in seconds.
 * @param avg               Average speed in km/h.
 * @param max               Maximum speed in km/h.
 * @param lean              Maximum lean angle in degrees.
 * @param elev              Total elevation gain in metres.
 * @param fuel              Estimated fuel consumed in litres.
 * @param synced            Whether successfully uploaded to the GPStrack server.
 * @param thumbnailPathD    Precomputed SVG path `d` string for the mini route preview; null if not yet computed.
 * @param correctionStatus  Current GPS-correction pipeline state.
 * @param confidence        OSRM matching confidence score (0–1); null when not yet attempted.
 */
data class RouteSummaryModel(
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
    val thumbnailPathD: String?,
    val correctionStatus: CorrectionStatus,
    val confidence: Double?,
)
