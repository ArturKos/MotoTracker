package com.mototracker.data.local.entity

/**
 * Lifecycle status of a [BikeEntity].
 *
 * Used as a Room-mapped enum stored as its [name] string.
 */
enum class BikeStatus {
    /** Motorcycle is currently owned and actively used. */
    ACTIVE,

    /** Motorcycle has been sold and is retained only for historical route data. */
    SOLD,
}
