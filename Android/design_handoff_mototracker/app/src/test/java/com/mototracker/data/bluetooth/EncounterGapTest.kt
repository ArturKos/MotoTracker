package com.mototracker.data.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [EncounterGap].
 *
 * Verifies the three cases:
 *  1. inGroup && groupTreatedSeparately → [Long.MAX_VALUE]
 *  2. inGroup && !groupTreatedSeparately → ordinary gap
 *  3. !inGroup → ordinary gap
 */
class EncounterGapTest {

    @Test
    fun `inGroup and groupTreatedSeparately true returns Long MAX_VALUE`() {
        val gap = EncounterGap.resolve(
            isInGroup = true,
            groupTreatedSeparately = true,
            encounterGapMinutes = 10,
        )
        assertEquals(Long.MAX_VALUE, gap)
    }

    @Test
    fun `inGroup but groupTreatedSeparately false returns ordinary gap`() {
        val gap = EncounterGap.resolve(
            isInGroup = true,
            groupTreatedSeparately = false,
            encounterGapMinutes = 10,
        )
        assertEquals(10 * 60_000L, gap)
    }

    @Test
    fun `non-group rider returns ordinary gap regardless of groupTreatedSeparately`() {
        val gapOn = EncounterGap.resolve(
            isInGroup = false,
            groupTreatedSeparately = true,
            encounterGapMinutes = 15,
        )
        val gapOff = EncounterGap.resolve(
            isInGroup = false,
            groupTreatedSeparately = false,
            encounterGapMinutes = 15,
        )
        assertEquals(15 * 60_000L, gapOn)
        assertEquals(15 * 60_000L, gapOff)
    }

    @Test
    fun `encounterGapMinutes is respected in ordinary gap calculation`() {
        val gap = EncounterGap.resolve(
            isInGroup = false,
            groupTreatedSeparately = false,
            encounterGapMinutes = 5,
        )
        assertEquals(5 * 60_000L, gap)
    }
}
