package com.mototracker.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure logic tests for the U1 migration truth-table.
 *
 * [SettingsDataStore] reads the new keys first and falls back to the legacy
 * DataStore keys when they are absent. The migration formulas are:
 *
 * ```
 * noInternet = NO_INTERNET ?: OFFLINE_ONLY ?: false
 * syncEnabled = SYNC_ENABLED ?: ((AUTO_SYNC ?: true) && !(OFFLINE ?: false))
 * ```
 *
 * Since the DataStore key-to-value mapping is injected at runtime we test the
 * *pure formula* here — no Android runtime required.
 */
class SettingsDataStoreMigrationTest {

    // ── noInternet migration ──────────────────────────────────────────────────

    /** Reproduces the formula: `NO_INTERNET ?: OFFLINE_ONLY ?: false`. */
    private fun migrateNoInternet(
        noInternet: Boolean?,
        offlineOnly: Boolean?,
    ): Boolean = noInternet ?: offlineOnly ?: false

    @Test fun `noInternet — new key present true`() =
        assertEquals(true, migrateNoInternet(noInternet = true, offlineOnly = null))

    @Test fun `noInternet — new key present false`() =
        assertEquals(false, migrateNoInternet(noInternet = false, offlineOnly = true))

    @Test fun `noInternet — new key absent, offlineOnly true`() =
        assertEquals(true, migrateNoInternet(noInternet = null, offlineOnly = true))

    @Test fun `noInternet — new key absent, offlineOnly false`() =
        assertEquals(false, migrateNoInternet(noInternet = null, offlineOnly = false))

    @Test fun `noInternet — both absent defaults to false`() =
        assertEquals(false, migrateNoInternet(noInternet = null, offlineOnly = null))

    // ── syncEnabled migration ─────────────────────────────────────────────────

    /**
     * Reproduces: `SYNC_ENABLED ?: ((AUTO_SYNC ?: true) && !(OFFLINE ?: false))`.
     */
    private fun migrateSyncEnabled(
        syncEnabled: Boolean?,
        autoSync: Boolean?,
        offline: Boolean?,
    ): Boolean = syncEnabled ?: ((autoSync ?: true) && !(offline ?: false))

    @Test fun `syncEnabled — new key present true`() =
        assertEquals(true, migrateSyncEnabled(syncEnabled = true, autoSync = null, offline = null))

    @Test fun `syncEnabled — new key present false`() =
        assertEquals(false, migrateSyncEnabled(syncEnabled = false, autoSync = true, offline = false))

    @Test fun `syncEnabled — legacy autoSync=true offline=false → true`() =
        assertEquals(true, migrateSyncEnabled(syncEnabled = null, autoSync = true, offline = false))

    @Test fun `syncEnabled — legacy autoSync=true offline=true → false (offline blocks)`() =
        assertEquals(false, migrateSyncEnabled(syncEnabled = null, autoSync = true, offline = true))

    @Test fun `syncEnabled — legacy autoSync=false offline=false → false (sync disabled)`() =
        assertEquals(false, migrateSyncEnabled(syncEnabled = null, autoSync = false, offline = false))

    @Test fun `syncEnabled — legacy autoSync=false offline=true → false`() =
        assertEquals(false, migrateSyncEnabled(syncEnabled = null, autoSync = false, offline = true))

    @Test fun `syncEnabled — all legacy absent defaults to true`() =
        assertEquals(true, migrateSyncEnabled(syncEnabled = null, autoSync = null, offline = null))

    @Test fun `syncEnabled — legacy offline=true all others absent → false`() =
        assertEquals(false, migrateSyncEnabled(syncEnabled = null, autoSync = null, offline = true))
}
