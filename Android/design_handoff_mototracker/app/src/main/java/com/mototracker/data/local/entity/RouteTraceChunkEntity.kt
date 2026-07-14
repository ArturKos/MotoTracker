package com.mototracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Room entity storing a single ordered fragment of a GPS trace.
 *
 * Large traces (≥ 35 000 points) overflow Android's 2 MB CursorWindow when stored
 * inline in the `routes` row. This table stores each trace as a sequence of JSON-array
 * chunks that Room reads incrementally, so no single window load can exceed the limit.
 *
 * @param routeId   FK referencing [RouteEntity.id]; cascades on delete.
 * @param kind      `"RAW"` for the original GPS trace or `"CORRECTED"` for the OSRM-snapped trace.
 * @param seq       Zero-based sequence index within the [kind] group; chunks must be joined in ascending order.
 * @param chunkJson JSON-array fragment — a serialised sub-array of at most [com.mototracker.core.format.TraceChunkCodec.CHUNK_SIZE] elements.
 */
@Entity(
    tableName = "route_trace_chunk",
    primaryKeys = ["routeId", "kind", "seq"],
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
data class RouteTraceChunkEntity(
    val routeId: String,
    val kind: String,
    val seq: Int,
    val chunkJson: String,
)
