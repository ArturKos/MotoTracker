package com.mototracker.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStateTest {

    /** Resolves label using hardcoded test strings — no Android context required. */
    private fun label(state: SyncState): String = state.toDisplayLabel(
        offlineLabel = "OFFLINE",
        queuedLabel = { n -> "$n W KOLEJCE" },
        syncedLabel = "SYNC ✓",
    )

    @Test
    fun `Offline maps to offline label`() {
        assertEquals("OFFLINE", label(SyncState.Offline))
    }

    @Test
    fun `Queued with 0 items maps to 0 in queue`() {
        assertEquals("0 W KOLEJCE", label(SyncState.Queued(0)))
    }

    @Test
    fun `Queued with 3 items maps to 3 in queue`() {
        assertEquals("3 W KOLEJCE", label(SyncState.Queued(3)))
    }

    @Test
    fun `Synced maps to synced label`() {
        assertEquals("SYNC ✓", label(SyncState.Synced))
    }

    @Test
    fun `Queued with large count maps correctly`() {
        assertEquals("99 W KOLEJCE", label(SyncState.Queued(99)))
    }
}
