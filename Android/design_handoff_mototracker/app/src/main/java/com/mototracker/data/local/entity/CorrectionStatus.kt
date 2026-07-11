package com.mototracker.data.local.entity

/**
 * GPS road-correction state for a [RouteEntity].
 *
 * Stored as its [name] string via Room TypeConverter.
 */
enum class CorrectionStatus {
    /** No correction has been attempted. */
    NONE,

    /** Queued for map-matching but not yet processed. */
    QUEUED,

    /** Matching succeeded but confidence/match-fraction was below the acceptance threshold. */
    LOW_CONFIDENCE,

    /** Map-matching accepted; [RouteEntity.correctedPathJson] holds the snapped trace. */
    DONE,
}
