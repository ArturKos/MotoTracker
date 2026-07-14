package com.mototracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a recorded motorcycle route.
 *
 * GPS trace data (raw and corrected) is stored out-of-row in [RouteTraceChunkEntity] to
 * avoid CursorWindow overflow on long rides. Only small scalar fields and the precomputed
 * [thumbnailPathD] live here; detail-only JSON columns (speed, elevation, weather, notes)
 * are retained because they are much smaller and only loaded when viewing a single route.
 *
 * Mirrors the route state shape from README §State (line 143).
 *
 * @param id                 UUID string (client-generated).
 * @param name               User-editable route name.
 * @param dateEpochMs        Recording start time in milliseconds since epoch.
 * @param bikeId             FK reference to [BikeEntity.id]; nullable for routes recorded without a selected bike.
 * @param km                 Total distance in kilometres.
 * @param durSec             Duration in seconds.
 * @param avg                Average speed in km/h.
 * @param max                Maximum speed in km/h.
 * @param lean               Maximum lean angle in degrees.
 * @param elev               Total elevation gain in metres.
 * @param fuel               Estimated fuel consumed in litres.
 * @param synced             Whether this route has been uploaded to the GPStrack server.
 * @param wxJson             Serialised weather object at recording time (null if offline).
 * @param speedJson          Serialised speed-over-time array.
 * @param elevProfileJson    Serialised elevation-over-distance array.
 * @param notes              Free-text notes added by the user.
 * @param thumbnailPathD     Precomputed SVG path `d` string built from a downsampled (~120 pts) raw trace; bounded length.
 * @param correctionStatus   Current GPS-correction pipeline state.
 * @param confidence         OSRM matching confidence score (0–1); null when not yet attempted.
 * @param fuelPricePerL      Per-route fuel price override (user's currency/L); null → fall back to
 *                           the assigned bike's [BikeEntity.fuelPricePerL].
 */
@Entity(
    tableName = "routes",
    foreignKeys = [ForeignKey(
        entity = BikeEntity::class,
        parentColumns = ["id"],
        childColumns = ["bikeId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("bikeId")],
)
data class RouteEntity(
    @PrimaryKey val id: String,
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
    val speedJson: String?,
    val elevProfileJson: String?,
    val notes: String?,
    val thumbnailPathD: String? = null,
    val correctionStatus: CorrectionStatus = CorrectionStatus.NONE,
    val confidence: Double? = null,
    val fuelPricePerL: Double? = null,
)
