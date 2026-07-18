package com.mototracker.ui.screens.riders

import com.mototracker.data.model.Wave

/**
 * Formats a BLE encounter duration in milliseconds into a human-readable label.
 *
 * Returns "Xh Ym" when hours > 0, otherwise "Ym". Zero or negative input returns "0m".
 */
object WaveDuration {
    /** @param durationMs Non-negative duration in milliseconds. */
    fun format(durationMs: Long): String {
        if (durationMs <= 0L) return "0m"
        val totalMinutes = durationMs / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
    }
}

/**
 * Partitions a flat list of [Wave] encounters into [WaveSections] for display.
 *
 * GRUPO contains one entry per group shortId (riders with [Wave.shortId] in [inGroupShortIds]).
 * SPOTKANIA contains one row per encounter for all other waves, ordered newest-first.
 */
object WaveGrouping {

    /**
     * Groups [waves] into GRUPO (group members) and SPOTKANIA (stranger meetups).
     *
     * - A wave whose [Wave.shortId] is in [inGroupShortIds] → GRUPO.
     *   One entry per shortId; duration = lastSeenMs − firstSeenMs; kind = RODE_TOGETHER.
     * - All others (including legacy rows where shortId is empty) → SPOTKANIA.
     *   One row per encounter, ordered newest-first by [Wave.firstSeenMs].
     *   Kind: LEGACY when firstSeenMs >= lastSeenMs (covers the 0/0 legacy sentinel);
     *         RODE_TOGETHER when duration >= [rodeTogetherThresholdMs];
     *         PASS otherwise.
     *
     * @param waves                   All BLE encounter rows from the repository.
     * @param inGroupShortIds         Short IDs of riders currently in the active group.
     * @param rodeTogetherThresholdMs Minimum positive duration (ms) to classify as RODE_TOGETHER.
     */
    fun group(
        waves: List<Wave>,
        inGroupShortIds: Set<String>,
        rodeTogetherThresholdMs: Long = 120_000L,
    ): WaveSections {
        val (groupWaves, meetupWaves) = waves.partition {
            it.shortId.isNotEmpty() && it.shortId in inGroupShortIds
        }

        val groupEntries = groupWaves
            .groupBy { it.shortId }
            .map { (_, waveList) ->
                val rep = waveList.maxByOrNull { it.lastSeenMs } ?: waveList.first()
                val durationMs = (rep.lastSeenMs - rep.firstSeenMs).coerceAtLeast(0L)
                WaveEntryUi(
                    nick = rep.nick,
                    bikeName = rep.bikeName,
                    place = rep.place,
                    timeLabel = rep.timeLabel,
                    kind = WaveKind.RODE_TOGETHER,
                    durationMs = durationMs,
                )
            }

        val meetupEntries = meetupWaves
            .sortedByDescending { it.firstSeenMs }
            .map { wave ->
                val isLegacy = wave.firstSeenMs >= wave.lastSeenMs
                val durationMs = if (isLegacy) 0L else (wave.lastSeenMs - wave.firstSeenMs).coerceAtLeast(0L)
                val kind = when {
                    isLegacy -> WaveKind.LEGACY
                    durationMs >= rodeTogetherThresholdMs -> WaveKind.RODE_TOGETHER
                    else -> WaveKind.PASS
                }
                WaveEntryUi(
                    nick = wave.nick,
                    bikeName = wave.bikeName,
                    place = wave.place,
                    timeLabel = wave.timeLabel,
                    kind = kind,
                    durationMs = durationMs,
                )
            }

        return WaveSections(group = groupEntries, meetups = meetupEntries)
    }
}
