package com.mototracker.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JUnit tests for [deriveSyncState] — covers every branch in the decision tree.
 *
 * No Android runtime or Robolectric required because [deriveSyncState] has no
 * platform dependencies.
 */
class SyncStateDerivationTest {

    // ── Offline branch ───────────────────────────────────────────────────────

    @Test
    fun `offline when device has no network connection`() {
        val result = deriveSyncState(isOnline = false, offline = false, offlineOnly = false, pendingCount = 0)
        assertEquals(SyncState.Offline, result)
    }

    @Test
    fun `offline when user enabled offline mode`() {
        val result = deriveSyncState(isOnline = true, offline = true, offlineOnly = false, pendingCount = 0)
        assertEquals(SyncState.Offline, result)
    }

    @Test
    fun `offline when offlineOnly is enabled`() {
        val result = deriveSyncState(isOnline = true, offline = false, offlineOnly = true, pendingCount = 0)
        assertEquals(SyncState.Offline, result)
    }

    @Test
    fun `offline wins over queued when device has no network`() {
        val result = deriveSyncState(isOnline = false, offline = false, offlineOnly = false, pendingCount = 5)
        assertEquals(SyncState.Offline, result)
    }

    @Test
    fun `offline wins over queued when user offline flag is set`() {
        val result = deriveSyncState(isOnline = true, offline = true, offlineOnly = false, pendingCount = 3)
        assertEquals(SyncState.Offline, result)
    }

    @Test
    fun `offline wins over queued when offlineOnly flag is set`() {
        val result = deriveSyncState(isOnline = true, offline = false, offlineOnly = true, pendingCount = 7)
        assertEquals(SyncState.Offline, result)
    }

    // ── Queued branch ────────────────────────────────────────────────────────

    @Test
    fun `queued when online and pending count is positive`() {
        val result = deriveSyncState(isOnline = true, offline = false, offlineOnly = false, pendingCount = 1)
        assertEquals(SyncState.Queued(1), result)
    }

    @Test
    fun `queued count reflects exact pending count`() {
        val result = deriveSyncState(isOnline = true, offline = false, offlineOnly = false, pendingCount = 42)
        assertEquals(SyncState.Queued(42), result)
    }

    // ── Synced branch ────────────────────────────────────────────────────────

    @Test
    fun `synced when online with no pending routes and no offline flags`() {
        val result = deriveSyncState(isOnline = true, offline = false, offlineOnly = false, pendingCount = 0)
        assertEquals(SyncState.Synced, result)
    }
}
