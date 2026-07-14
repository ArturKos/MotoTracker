package com.mototracker.data.model

import com.mototracker.data.local.entity.BikeStatus

/**
 * Pure domain model representing a motorcycle.
 *
 * Free of Room annotations — safe to use in ViewModels and domain logic.
 *
 * @param id                    UUID string uniquely identifying the bike.
 * @param name                  Display name, e.g. "Yamaha MT-07".
 * @param year                  Model year.
 * @param plate                 Registration plate.
 * @param status                Active or sold lifecycle status.
 * @param tankCapacityL         Fuel tank capacity in litres; null when not configured.
 * @param fuelPricePerL         Fuel price per litre (in the user's currency); null when not configured.
 * @param consumptionLper100km  Average fuel consumption in L/100km; null when not configured.
 */
data class Bike(
    val id: String,
    val name: String,
    val year: Int,
    val plate: String,
    val status: BikeStatus,
    val tankCapacityL: Double? = null,
    val fuelPricePerL: Double? = null,
    val consumptionLper100km: Double? = null,
)
