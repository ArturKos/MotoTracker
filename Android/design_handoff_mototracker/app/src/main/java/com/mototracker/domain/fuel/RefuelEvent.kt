package com.mototracker.domain.fuel

/**
 * Domain model for a single refuel event.
 *
 * Maps 1:1 to [com.mototracker.data.local.entity.RefuelEventEntity] but is
 * Android-free so domain logic can be tested without a Room database.
 *
 * @param id         Persisted primary key (0 when not yet saved).
 * @param routeId    UUID of the route this event belongs to.
 * @param epochMs    Wall-clock time of the refuel in milliseconds since epoch.
 * @param litres     Volume added in litres.
 * @param pricePerL  Price per litre in the user's currency at the time of the event.
 */
data class RefuelEvent(
    val id: Long,
    val routeId: String,
    val epochMs: Long,
    val litres: Double,
    val pricePerL: Double,
)
