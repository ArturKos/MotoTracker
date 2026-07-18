package com.mototracker.data.bluetooth

/**
 * Presence-continuity sessioniser for BLE rider sightings.
 *
 * Tracks one open "encounter" per [shortId]. An encounter begins on the first sighting
 * (or when the gap since the last sighting exceeds [gapMs]) and extends on every
 * subsequent sighting within [gapMs]. The caller supplies [gapMs] per call so that
 * group members (whose gap is [Long.MAX_VALUE]) never produce a second encounter even
 * after a long radio blackout.
 *
 * All state is in-memory; the caller is responsible for thread safety when invoked
 * from concurrent BLE scan callbacks. No Android dependencies — fully JVM-testable.
 */
class EncounterTracker {

    /** Per-shortId timestamps for the currently open encounter. */
    private data class EncounterState(val firstSeenMs: Long, val lastSeenMs: Long)

    private val open = mutableMapOf<String, EncounterState>()

    /**
     * Records a sighting of [shortId] at [nowMs] and returns whether this sighting
     * starts a brand-new encounter or extends the existing one.
     *
     * @param shortId 4-character rider identifier extracted from the BLE payload.
     * @param nowMs   Current wall-clock time in milliseconds (System.currentTimeMillis()).
     * @param gapMs   Maximum gap between sightings that still counts as the same encounter.
     *                Pass [Long.MAX_VALUE] for in-group members (encounter never splits).
     * @return [EncounterEvent.Started] when a new encounter opens, [EncounterEvent.Extended]
     *         when the existing encounter is continued.
     */
    fun onSighting(shortId: String, nowMs: Long, gapMs: Long): EncounterEvent {
        val existing = open[shortId]
        return if (existing == null || nowMs - existing.lastSeenMs > gapMs) {
            open[shortId] = EncounterState(firstSeenMs = nowMs, lastSeenMs = nowMs)
            EncounterEvent.Started(shortId = shortId, atMs = nowMs)
        } else {
            open[shortId] = existing.copy(lastSeenMs = nowMs)
            EncounterEvent.Extended(shortId = shortId, atMs = nowMs)
        }
    }

    /** Removes all in-memory encounter state (call at end of a recording session). */
    fun reset() = open.clear()
}

/**
 * The outcome of a single [EncounterTracker.onSighting] call.
 *
 * @property shortId The rider identifier that triggered the event.
 * @property atMs    Wall-clock timestamp when the event was recorded.
 */
sealed class EncounterEvent {

    abstract val shortId: String
    abstract val atMs: Long

    /** A new encounter just opened for [shortId] (first sighting or gap exceeded). */
    data class Started(override val shortId: String, override val atMs: Long) : EncounterEvent()

    /** The existing encounter for [shortId] was extended by a fresh sighting. */
    data class Extended(override val shortId: String, override val atMs: Long) : EncounterEvent()
}
