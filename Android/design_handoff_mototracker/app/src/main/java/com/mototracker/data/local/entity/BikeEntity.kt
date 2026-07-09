package com.mototracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a motorcycle owned or previously owned by the user.
 *
 * Mirrors the `Bike` section of the README state model (§State line 142).
 *
 * @param id     UUID string uniquely identifying the bike (generated client-side).
 * @param name   Display name, e.g. "Yamaha MT-07".
 * @param year   Model year.
 * @param plate  Registration plate, e.g. "WA 12345".
 * @param status Active or sold — stored as enum name string via [Converters].
 */
@Entity(tableName = "bikes")
data class BikeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val year: Int,
    val plate: String,
    val status: BikeStatus,
)
