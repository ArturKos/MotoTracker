package com.mototracker.ui.screens.settings

import com.mototracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SettingsTab] enum — verifies the tab roster and resource bindings
 * introduced or modified by Z1 (dedicated Waves tab).
 */
class SettingsTabTest {

    @Test
    fun entries_containsWavesTab() {
        val names = SettingsTab.entries.map { it.name }
        assertTrue("SettingsTab.entries must contain WAVES", names.contains("WAVES"))
    }

    @Test
    fun waves_titleResIsSettingsTabWaves() {
        assertEquals(R.string.settings_tab_waves, SettingsTab.WAVES.titleRes)
    }

    @Test
    fun waves_isPositionedAfterServerSyncBeforeSystemDiagnostics() {
        val entries = SettingsTab.entries
        val syncIdx = entries.indexOf(SettingsTab.SERVER_SYNC)
        val wavesIdx = entries.indexOf(SettingsTab.WAVES)
        val diagIdx = entries.indexOf(SettingsTab.SYSTEM_DIAGNOSTICS)
        assertTrue("WAVES must come after SERVER_SYNC", wavesIdx > syncIdx)
        assertTrue("WAVES must come before SYSTEM_DIAGNOSTICS", wavesIdx < diagIdx)
    }
}
