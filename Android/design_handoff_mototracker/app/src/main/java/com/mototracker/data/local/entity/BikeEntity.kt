package com.mototracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a motorcycle owned or previously owned by the user.
 *
 * Mirrors the `Bike` section of the README state model (§State line 142).
 *
 * @param id                       UUID string uniquely identifying the bike (generated client-side).
 * @param name                     Display name, e.g. "Yamaha MT-07".
 * @param year                     Model year.
 * @param plate                    Registration plate, e.g. "WA 12345".
 * @param status                   Active or sold — stored as enum name string via [Converters].
 * @param tankCapacityL            Fuel tank capacity in litres; null when not set by the user.
 * @param fuelPricePerL            Fuel price per litre (in the user's currency); null when not set.
 * @param consumptionLper100km     Average fuel consumption in L/100km; null when not set.
 *                                 Used as the recording engine's per-session consumption constant.
 * @param autoUpdateConsumption    Stored as INTEGER 0/1. When non-zero, [consumptionLper100km]
 *                                 is refreshed from the refuel ledger (H4/K2) after each refuel.
 */
@Entity(tableName = "bikes")
data class BikeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val year: Int,
    val plate: String,
    val status: BikeStatus,
    val tankCapacityL: Double? = null,
    val fuelPricePerL: Double? = null,
    val consumptionLper100km: Double? = null,
    val autoUpdateConsumption: Boolean = false,
)
