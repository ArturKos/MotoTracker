package com.mototracker.data.model.mapper

import com.mototracker.data.local.entity.WaveEntity
import com.mototracker.data.model.Wave

/** Converts a [WaveEntity] to its domain representation. */
fun WaveEntity.toDomain(): Wave = Wave(
    id = id,
    nick = nick,
    bikeName = bikeName,
    place = place,
    timeLabel = timeLabel,
    routeId = routeId,
)

/** Converts a [Wave] domain model to its Room entity counterpart. */
fun Wave.toEntity(): WaveEntity = WaveEntity(
    id = id,
    nick = nick,
    bikeName = bikeName,
    place = place,
    timeLabel = timeLabel,
    routeId = routeId,
)
