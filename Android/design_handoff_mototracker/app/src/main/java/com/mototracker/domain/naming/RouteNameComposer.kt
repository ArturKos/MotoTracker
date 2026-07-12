package com.mototracker.domain.naming

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Time-of-day classification used to pick a localized ride label. */
enum class PartOfDay { MORNING, AFTERNOON, EVENING, NIGHT }

/**
 * Pure, Android-free utility for composing a human-readable default route name.
 *
 * No Android dependencies — fully unit-testable on the JVM.
 */
object RouteNameComposer {

    /**
     * Returns the [PartOfDay] for [startEpochMs] in [zone].
     *
     * Hour-of-day boundaries (inclusive–inclusive):
     * - MORNING   05:00–11:59
     * - AFTERNOON 12:00–17:59
     * - EVENING   18:00–21:59
     * - NIGHT     22:00–04:59
     *
     * @param startEpochMs Epoch milliseconds of the ride start.
     * @param zone         Time zone to interpret the timestamp in (default: system default).
     */
    fun partOfDay(startEpochMs: Long, zone: ZoneId = ZoneId.systemDefault()): PartOfDay {
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startEpochMs), zone).hour
        return when (hour) {
            in 5..11  -> PartOfDay.MORNING
            in 12..17 -> PartOfDay.AFTERNOON
            in 18..21 -> PartOfDay.EVENING
            else      -> PartOfDay.NIGHT
        }
    }

    /**
     * Composes the final route name from a pre-resolved ride label and a geographic area.
     *
     * The [template] is an Android-style format string passed by the caller so this object
     * stays free of Android resource dependencies. Convention: `%1$s` = area, `%2$s` = rideLabel,
     * e.g. `"%1$s – %2$s"` produces `"Szczecin – popołudniowa przejażdżka"`.
     *
     * @param rideLabel Localized time-of-day label resolved by the caller.
     * @param area      Geographic area name from reverse geocoding, or `null`/blank.
     * @param template  Android `String.format` template; `%1$s` = area, `%2$s` = rideLabel.
     * @return `"<area> – <rideLabel>"` when [area] is non-blank; [rideLabel] otherwise.
     */
    fun compose(rideLabel: String, area: String?, template: String): String =
        if (area.isNullOrBlank()) rideLabel
        else String.format(template, area, rideLabel)

    /**
     * Returns the most-frequent non-null, non-blank element of [areas].
     *
     * Ties are resolved by first-seen order (the element that appears first in the list wins).
     * Returns `null` when [areas] contains no valid entries.
     *
     * @param areas Reverse-geocode results; may contain nulls and blanks.
     */
    fun dominantArea(areas: List<String?>): String? {
        val valid = areas.filter { !it.isNullOrBlank() }.map { it!! }
        if (valid.isEmpty()) return null
        val counts = mutableMapOf<String, Int>()
        val firstSeen = mutableMapOf<String, Int>()
        valid.forEachIndexed { i, s ->
            counts[s] = (counts[s] ?: 0) + 1
            if (!firstSeen.containsKey(s)) firstSeen[s] = i
        }
        val maxCount = counts.values.max()
        return counts.entries
            .filter { it.value == maxCount }
            .minByOrNull { firstSeen[it.key]!! }
            ?.key
    }
}
