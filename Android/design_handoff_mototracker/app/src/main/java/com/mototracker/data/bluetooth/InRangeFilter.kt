package com.mototracker.data.bluetooth

import com.mototracker.data.model.Rider

/**
 * Pure filter that derives the "currently in BLE range" subset from the persisted rider list.
 *
 * "In range" means the rider's [Rider.lastSeenMs] is within [windowMs] of [nowMs] (inclusive
 * boundary: `lastSeenMs >= nowMs - windowMs`). This matches the semantics of
 * [com.mototracker.data.local.dao.RiderDao.upsertSighting], which stamps [Rider.lastSeenMs]
 * on every BLE discovery, so a freshly-sighted rider always qualifies.
 *
 * The result is sorted most-recently-seen first.
 *
 * No Android types — fully testable in the JVM unit-test harness.
 */
object InRangeFilter {

    /** Default recency window: 30 seconds. Riders not seen within this window are considered out of range. */
    const val DEFAULT_WINDOW_MS = 30_000L

    /**
     * Returns riders from [riders] whose [Rider.lastSeenMs] satisfies `lastSeenMs >= nowMs - windowMs`,
     * sorted most-recently-seen first.
     *
     * @param riders   Full rider directory snapshot.
     * @param nowMs    Current wall-clock time in milliseconds (System.currentTimeMillis()).
     * @param windowMs Recency window in milliseconds; defaults to [DEFAULT_WINDOW_MS].
     * @return Filtered and sorted list of in-range riders.
     */
    fun filter(
        riders: List<Rider>,
        nowMs: Long,
        windowMs: Long = DEFAULT_WINDOW_MS,
    ): List<Rider> {
        val cutoff = nowMs - windowMs
        return riders
            .filter { it.lastSeenMs >= cutoff }
            .sortedByDescending { it.lastSeenMs }
    }
}
