package com.mototracker.ui.navigation

import com.mototracker.ui.screens.record.RecordingPhase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Truth-table tests for the pure nav-lock gating helpers [isRecordingLocked]
 * and [bottomNavItemEnabled] (D7).
 */
class NavLockTest {

    // ── isRecordingLocked ────────────────────────────────────────────────────

    @Test
    fun `isRecordingLocked returns false for Idle`() {
        assertFalse(isRecordingLocked(RecordingPhase.Idle))
    }

    @Test
    fun `isRecordingLocked returns true for Recording`() {
        assertTrue(isRecordingLocked(RecordingPhase.Recording))
    }

    @Test
    fun `isRecordingLocked returns true for Paused`() {
        assertTrue(isRecordingLocked(RecordingPhase.Paused))
    }

    // ── bottomNavItemEnabled — recordingActive = false ───────────────────────

    @Test
    fun `all destinations enabled when recording inactive`() {
        val dests = listOf(
            MotoDestination.RECORD,
            MotoDestination.ROUTES,
            MotoDestination.RIDERS,
            MotoDestination.STATS,
            MotoDestination.SETTINGS,
            MotoDestination.LOGIN,
            MotoDestination.ROUTE_DETAIL,
        )
        dests.forEach { dest ->
            assertTrue(
                "Expected $dest enabled when recordingActive=false",
                bottomNavItemEnabled(dest, recordingActive = false),
            )
        }
    }

    // ── bottomNavItemEnabled — recordingActive = true ────────────────────────

    @Test
    fun `RECORD tab enabled when recording active`() {
        assertTrue(bottomNavItemEnabled(MotoDestination.RECORD, recordingActive = true))
    }

    @Test
    fun `ROUTES tab disabled when recording active`() {
        assertFalse(bottomNavItemEnabled(MotoDestination.ROUTES, recordingActive = true))
    }

    @Test
    fun `RIDERS tab disabled when recording active`() {
        assertFalse(bottomNavItemEnabled(MotoDestination.RIDERS, recordingActive = true))
    }

    @Test
    fun `STATS tab disabled when recording active`() {
        assertFalse(bottomNavItemEnabled(MotoDestination.STATS, recordingActive = true))
    }

    @Test
    fun `SETTINGS tab disabled when recording active`() {
        assertFalse(bottomNavItemEnabled(MotoDestination.SETTINGS, recordingActive = true))
    }

    @Test
    fun `LOGIN destination disabled when recording active`() {
        assertFalse(bottomNavItemEnabled(MotoDestination.LOGIN, recordingActive = true))
    }

    @Test
    fun `ROUTE_DETAIL destination disabled when recording active`() {
        assertFalse(bottomNavItemEnabled(MotoDestination.ROUTE_DETAIL, recordingActive = true))
    }

    // ── Cross-product: every phase × every bottom-nav destination ────────────

    @Test
    fun `only RECORD enabled for every non-Idle phase`() {
        val nonIdlePhases = listOf(RecordingPhase.Recording, RecordingPhase.Paused)
        val bottomNavDests = listOf(
            MotoDestination.RECORD,
            MotoDestination.ROUTES,
            MotoDestination.RIDERS,
            MotoDestination.STATS,
            MotoDestination.SETTINGS,
        )
        nonIdlePhases.forEach { phase ->
            val locked = isRecordingLocked(phase)
            bottomNavDests.forEach { dest ->
                val enabled = bottomNavItemEnabled(dest, recordingActive = locked)
                if (dest == MotoDestination.RECORD) {
                    assertTrue("Expected RECORD enabled for phase=$phase", enabled)
                } else {
                    assertFalse("Expected $dest disabled for phase=$phase", enabled)
                }
            }
        }
    }
}
