package com.mototracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single refuel event logged for a recorded route.
 *
 * Each row records the epoch time, volume, and price of one fill-up. Cascade-deletes
 * with the parent [RouteEntity] so orphaned rows never accumulate.
 *
 * @param id         Auto-generated primary key.
 * @param routeId    FK to [RouteEntity.id]; required, non-null.
 * @param epochMs    Wall-clock time of the refuel event in milliseconds since epoch.
 * @param litres     Volume of fuel added in litres.
 * @param pricePerL  Price per litre in the user's chosen currency at the time of the event.
 */
@Entity(
    tableName = "refuel_event",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routeId")],
)
data class RefuelEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: String,
    val epochMs: Long,
    val litres: Double,
    val pricePerL: Double,
)
