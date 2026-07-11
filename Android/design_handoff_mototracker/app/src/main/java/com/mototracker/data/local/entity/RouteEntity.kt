package com.mototracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a recorded motorcycle route.
 *
 * All complex sub-objects (GPS track, speed profile, elevation profile) are
 * serialised to JSON strings to keep the schema flat. The [wxJson] field stores
 * the raw weather API response at the time of recording.
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
 * @param pathJson           Serialised list of raw GPS coordinates ([{lat,lng}] format); immutable source of truth.
 * @param speedJson          Serialised speed-over-time array.
 * @param elevProfileJson    Serialised elevation-over-distance array.
 * @param notes              Free-text notes added by the user.
 * @param correctedPathJson  Road-snapped GPS trace from OSRM map-matching ([{lat,lng}] format); null until [correctionStatus] is [CorrectionStatus.DONE].
 * @param correctionStatus   Current GPS-correction pipeline state.
 * @param confidence         OSRM matching confidence score (0–1); null when not yet attempted.
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
    val pathJson: String?,
    val speedJson: String?,
    val elevProfileJson: String?,
    val notes: String?,
    val correctedPathJson: String? = null,
    val correctionStatus: CorrectionStatus = CorrectionStatus.NONE,
    val confidence: Double? = null,
)
