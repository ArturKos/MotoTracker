package com.mototracker.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JUnit tests for [deriveSyncState] — covers every branch in the decision tree (U1).
 *
 * No Android runtime or Robolectric required because [deriveSyncState] has no
 * platform dependencies.
 */
class SyncStateDerivationTest {

    // ── Offline branch ───────────────────────────────────────────────────────

    @Test
    fun `offline when device has no network connection`() {
        val result = deriveSyncState(isOnline = false, noInternet = false, pendingCount = 0)
        assertEquals(SyncState.Offline, result)
    }

    @Test
    fun `offline when noInternet master switch is enabled`() {
        val result = deriveSyncState(isOnline = true, noInternet = true, pendingCount = 0)
        assertEquals(SyncState.Offline, result)
    }

    @Test
    fun `offline wins over queued when device has no network`() {
        val result = deriveSyncState(isOnline = false, noInternet = false, pendingCount = 5)
        assertEquals(SyncState.Offline, result)
    }

    @Test
    fun `offline wins over queued when noInternet flag is set`() {
        val result = deriveSyncState(isOnline = true, noInternet = true, pendingCount = 7)
        assertEquals(SyncState.Offline, result)
    }

    // ── Queued branch ────────────────────────────────────────────────────────

    @Test
    fun `queued when online and pending count is positive`() {
        val result = deriveSyncState(isOnline = true, noInternet = false, pendingCount = 1)
        assertEquals(SyncState.Queued(1), result)
    }

    @Test
    fun `queued count reflects exact pending count`() {
        val result = deriveSyncState(isOnline = true, noInternet = false, pendingCount = 42)
        assertEquals(SyncState.Queued(42), result)
    }

    // ── Synced branch ────────────────────────────────────────────────────────

    @Test
    fun `synced when online with no pending routes and noInternet false`() {
        val result = deriveSyncState(isOnline = true, noInternet = false, pendingCount = 0)
        assertEquals(SyncState.Synced, result)
    }
}
