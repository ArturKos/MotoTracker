package com.mototracker.data.bluetooth

/**
 * Pure helper that resolves the encounter-gap threshold for a single BLE sighting.
 *
 * Extracted so the decision is unit-testable without instantiating [com.mototracker.service.RecordingService].
 *
 * Rules:
 * - If the rider [isInGroup] **and** [groupTreatedSeparately] is `true`, return [Long.MAX_VALUE]
 *   so the rider is always part of one continuous encounter.
 * - Otherwise return [encounterGapMinutes] × 60 000 ms (ordinary gap).
 */
object EncounterGap {

    /**
     * Resolves the encounter gap in milliseconds for one sighting.
     *
     * @param isInGroup              Whether the rider is marked as a group member.
     * @param groupTreatedSeparately Master toggle: when `true`, group members get infinite gap.
     * @param encounterGapMinutes    Configured gap threshold in minutes for ordinary encounters.
     * @return Gap in milliseconds.
     */
    fun resolve(
        isInGroup: Boolean,
        groupTreatedSeparately: Boolean,
        encounterGapMinutes: Int,
    ): Long = if (isInGroup && groupTreatedSeparately) Long.MAX_VALUE
               else encounterGapMinutes * 60_000L
}
