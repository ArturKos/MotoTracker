package com.mototracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a single fuel-level correction event.
 *
 * Persists off-ride and in-ride baseline corrections separately from the refuel
 * ledger so [com.mototracker.domain.fuel.FuelConsumptionCalculator] is never
 * contaminated by correction rows (R1 — consumption ledger isolation).
 *
 * [routeId] is stored as a plain nullable column with no foreign key so that
 * deleting a route does not cascade-delete the correction history and off-ride
 * corrections (routeId = null) are preserved unconditionally.
 *
 * @param id       Auto-generated primary key.
 * @param bikeId   UUID of the bike this correction applies to (indexed).
 * @param routeId  UUID of the active route at correction time; null for off-ride corrections.
 * @param epochMs  Wall-clock time of the correction in milliseconds since epoch.
 * @param mode     Serialised [com.mototracker.domain.fuel.FuelAdjustmentMode] ("SET_ABSOLUTE"|"DELTA").
 * @param litres   Correction value in litres (absolute level or signed delta).
 */
@Entity(
    tableName = "fuel_adjustment_event",
    indices = [Index("bikeId")],
)
data class FuelAdjustmentEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bikeId: String,
    val routeId: String?,
    val epochMs: Long,
    val mode: String,
    val litres: Double,
)
