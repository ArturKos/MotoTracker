package com.mototracker.ui.screens.detail

/**
 * Which GPS track layer the Route Detail screen is currently displaying on the map.
 *
 * - [RAW] shows the original unprocessed GPS coordinates ([com.mototracker.data.model.Route.pathJson]).
 * - [CORRECTED] shows the road-snapped OSRM output ([com.mototracker.data.model.Route.correctedPathJson]).
 *
 * The [CORRECTED] option is only selectable when a corrected trace exists; otherwise the screen
 * falls back to [RAW].
 */
enum class TrackView { RAW, CORRECTED }
