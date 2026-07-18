package com.mototracker.domain.fuel

/**
 * Domain model for a single fuel-level correction event.
 *
 * Unlike a [RefuelEvent] (which feeds the consumption ledger), a [FuelAdjustmentEvent]
 * only re-anchors the live remaining-fuel baseline and is intentionally excluded from
 * [FuelConsumptionCalculator] (R1 — consumption ledger isolation).
 *
 * @param id       Persisted primary key (0 when not yet saved).
 * @param bikeId   UUID of the bike this correction applies to.
 * @param routeId  UUID of the active route at correction time, or null for off-ride corrections.
 * @param epochMs  Wall-clock time of the correction in milliseconds since epoch.
 * @param mode     Whether the correction sets an absolute level or applies a signed delta.
 * @param litres   The correction value: absolute litres for [FuelAdjustmentMode.SET_ABSOLUTE],
 *                 signed delta litres for [FuelAdjustmentMode.DELTA].
 */
data class FuelAdjustmentEvent(
    val id: Long,
    val bikeId: String,
    val routeId: String?,
    val epochMs: Long,
    val mode: FuelAdjustmentMode,
    val litres: Double,
)
