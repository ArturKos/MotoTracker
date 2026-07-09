package com.mototracker.data.model.mapper

import com.mototracker.data.local.entity.BikeEntity
import com.mototracker.data.model.Bike

/** Converts a [BikeEntity] to its domain representation. */
fun BikeEntity.toDomain(): Bike = Bike(
    id = id,
    name = name,
    year = year,
    plate = plate,
    status = status,
)

/** Converts a [Bike] domain model to its Room entity counterpart. */
fun Bike.toEntity(): BikeEntity = BikeEntity(
    id = id,
    name = name,
    year = year,
    plate = plate,
    status = status,
)
