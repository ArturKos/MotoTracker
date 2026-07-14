package com.mototracker.data.model.mapper

import com.mototracker.data.local.entity.RouteEntity
import com.mototracker.data.model.Route
import com.mototracker.data.model.RouteSummaryModel

/**
 * Converts a [RouteEntity] to its domain [Route] representation.
 *
 * [pathJson] and [correctedPathJson] are supplied by the caller (assembled from
 * [com.mototracker.data.local.entity.RouteTraceChunkEntity] rows) rather than read
 * from the entity, because those columns were removed in DB version 3.
 *
 * @param pathJson           Reassembled raw GPS trace JSON, or `null`.
 * @param correctedPathJson  Reassembled OSRM-corrected trace JSON, or `null`.
 */
fun RouteEntity.toDomain(pathJson: String? = null, correctedPathJson: String? = null): Route = Route(
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
    correctedPathJson = correctedPathJson,
    correctionStatus = correctionStatus,
    confidence = confidence,
    fuelPricePerL = fuelPricePerL,
    maxLeanLeftDeg = maxLeanLeftDeg,
    maxLeanRightDeg = maxLeanRightDeg,
)

/**
 * Converts a [Route] domain model to its [RouteEntity] counterpart.
 *
 * [pathJson] and [correctedPathJson] are intentionally omitted — they are stored
 * out-of-row in [com.mototracker.data.local.entity.RouteTraceChunkEntity] and written
 * separately by [com.mototracker.data.repository.RouteRepositoryImpl.save].
 *
 * [thumbnailPathD] is not set here; the repository computes and stores it separately.
 */
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
    speedJson = speedJson,
    elevProfileJson = elevProfileJson,
    notes = notes,
    correctionStatus = correctionStatus,
    confidence = confidence,
    fuelPricePerL = fuelPricePerL,
    maxLeanLeftDeg = maxLeanLeftDeg,
    maxLeanRightDeg = maxLeanRightDeg,
)

/**
 * Converts a [Route] domain model to the lightweight [RouteSummaryModel].
 *
 * Used by in-memory fake repositories in tests so they can implement
 * [com.mototracker.data.repository.RouteRepository.observeSummaries] without a real DB.
 * The [RouteSummaryModel.thumbnailPathD] is always `null` here because [Route] does not
 * carry that precomputed field; tests that need a specific thumbnail value should construct
 * the [RouteSummaryModel] directly.
 */
fun Route.toRouteSummaryModel(): RouteSummaryModel = RouteSummaryModel(
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
    thumbnailPathD = null,
    correctionStatus = correctionStatus,
    confidence = confidence,
)
