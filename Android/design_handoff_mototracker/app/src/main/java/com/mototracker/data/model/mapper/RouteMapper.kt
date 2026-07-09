package com.mototracker.data.model.mapper

import com.mototracker.data.local.entity.RouteEntity
import com.mototracker.data.model.Route

/** Converts a [RouteEntity] to its domain representation. */
fun RouteEntity.toDomain(): Route = Route(
    id = id,
    name = name,
    dateEpochMs = dateEpochMs,
    bikeId = bikeId,
    km = km,
    durSec = durSec,
    avg = avg,
    max = max,
    lean = lean,
    elev = elev,
    fuel = fuel,
    synced = synced,
    wxJson = wxJson,
    pathJson = pathJson,
    speedJson = speedJson,
    elevProfileJson = elevProfileJson,
    notes = notes,
)

/** Converts a [Route] domain model to its Room entity counterpart. */
fun Route.toEntity(): RouteEntity = RouteEntity(
    id = id,
    name = name,
    dateEpochMs = dateEpochMs,
    bikeId = bikeId,
    km = km,
    durSec = durSec,
    avg = avg,
    max = max,
    lean = lean,
    elev = elev,
    fuel = fuel,
    synced = synced,
    wxJson = wxJson,
    pathJson = pathJson,
    speedJson = speedJson,
    elevProfileJson = elevProfileJson,
    notes = notes,
)
