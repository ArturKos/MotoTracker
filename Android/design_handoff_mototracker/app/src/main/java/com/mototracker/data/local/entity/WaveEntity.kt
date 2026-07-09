package com.mototracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a Bluetooth "wave" — a brief proximity greeting
 * exchanged between riders when they pass each other.
 *
 * Waves can optionally be linked to a [RouteEntity] if the user was actively
 * recording when the wave occurred. The FK uses SET_NULL so deleting a route
 * does not remove its associated waves.
 *
 * Mirrors the wave state shape from README §State (line 145).
 *
 * @param id        UUID string (client-generated).
 * @param nick      The other rider's display nickname.
 * @param bikeName  The other rider's bike model name.
 * @param place     Human-readable location label where the wave occurred.
 * @param timeLabel Formatted time string shown in the UI, e.g. "14:32".
 * @param routeId   Optional FK to the [RouteEntity] that was active during this wave.
 */
@Entity(
    tableName = "waves",
    foreignKeys = [ForeignKey(
        entity = RouteEntity::class,
        parentColumns = ["id"],
        childColumns = ["routeId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("routeId")],
)
data class WaveEntity(
    @PrimaryKey val id: String,
    val nick: String,
    val bikeName: String,
    val place: String,
    val timeLabel: String,
    val routeId: String?,
)
