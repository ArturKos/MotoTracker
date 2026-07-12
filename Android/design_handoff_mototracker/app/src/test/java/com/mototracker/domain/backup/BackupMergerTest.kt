package com.mototracker.domain.backup

import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergerTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun route(id: String, name: String = "Route $id") = Route(
        id = id, name = name, dateEpochMs = 1_700_000_000_000L,
        bikeId = null, km = 10.0, durSec = 600L, avg = 60.0, max = 90.0, lean = 20.0,
        elev = 100.0, fuel = 2.0, synced = false,
        wxJson = null, pathJson = null, speedJson = null, elevProfileJson = null,
        notes = null, correctedPathJson = null,
        correctionStatus = CorrectionStatus.NONE, confidence = null,
    )

    private fun bike(id: String, name: String = "Bike $id") = Bike(
        id = id, name = name, year = 2020, plate = "XX $id", status = BikeStatus.ACTIVE,
    )

    private val defaultSettings = AppSettings()
    private val importedSettings = AppSettings(theme = "light", lang = "en", units = "imperial")

    // ── MERGE mode ────────────────────────────────────────────────────────────

    @Test
    fun `MERGE keeps existing routes not in import`() {
        val existing = listOf(route("r1"), route("r2"))
        val imported = BackupData(1, listOf(route("r3")), emptyList(), importedSettings)

        val result = BackupMerger.merge(existing, emptyList(), defaultSettings, imported, RestoreMode.MERGE)

        val ids = result.routes.map { it.id }.toSet()
        assertTrue("r1 should be kept", "r1" in ids)
        assertTrue("r2 should be kept", "r2" in ids)
        assertTrue("r3 should be added", "r3" in ids)
    }

    @Test
    fun `MERGE imported wins on UUID collision`() {
        val existing = listOf(route("r1", name = "Old name"))
        val imported = BackupData(1, listOf(route("r1", name = "New name")), emptyList(), importedSettings)

        val result = BackupMerger.merge(existing, emptyList(), defaultSettings, imported, RestoreMode.MERGE)

        assertEquals(1, result.routes.size)
        assertEquals("New name", result.routes.single().name)
    }

    @Test
    fun `MERGE counts added and updated routes correctly`() {
        val existing = listOf(route("r1"), route("r2"))
        val imported = BackupData(1, listOf(route("r1", "Updated"), route("r3")), emptyList(), importedSettings)

        val result = BackupMerger.merge(existing, emptyList(), defaultSettings, imported, RestoreMode.MERGE)

        assertEquals(1, result.updatedRoutes)
        assertEquals(1, result.addedRoutes)
    }

    @Test
    fun `MERGE counts added and updated bikes correctly`() {
        val existing = listOf(bike("b1"), bike("b2"))
        val imported = BackupData(1, emptyList(), listOf(bike("b1", "Updated"), bike("b3")), importedSettings)

        val result = BackupMerger.merge(emptyList(), existing, defaultSettings, imported, RestoreMode.MERGE)

        assertEquals(1, result.updatedBikes)
        assertEquals(1, result.addedBikes)
    }

    @Test
    fun `MERGE leaves settings null (unchanged)`() {
        val imported = BackupData(1, emptyList(), emptyList(), importedSettings)
        val result = BackupMerger.merge(emptyList(), emptyList(), defaultSettings, imported, RestoreMode.MERGE)
        assertNull("MERGE must not apply settings", result.settings)
    }

    @Test
    fun `MERGE with empty existing and empty import produces empty result`() {
        val imported = BackupData(1, emptyList(), emptyList(), importedSettings)
        val result = BackupMerger.merge(emptyList(), emptyList(), defaultSettings, imported, RestoreMode.MERGE)
        assertTrue(result.routes.isEmpty())
        assertTrue(result.bikes.isEmpty())
        assertEquals(0, result.addedRoutes)
        assertEquals(0, result.updatedRoutes)
    }

    // ── REPLACE mode ──────────────────────────────────────────────────────────

    @Test
    fun `REPLACE discards existing routes not in import`() {
        val existing = listOf(route("r1"), route("r2"))
        val imported = BackupData(1, listOf(route("r3")), emptyList(), importedSettings)

        val result = BackupMerger.merge(existing, emptyList(), defaultSettings, imported, RestoreMode.REPLACE)

        assertEquals(1, result.routes.size)
        assertEquals("r3", result.routes.single().id)
    }

    @Test
    fun `REPLACE discards existing bikes not in import`() {
        val existing = listOf(bike("b1"), bike("b2"))
        val imported = BackupData(1, emptyList(), listOf(bike("b99")), importedSettings)

        val result = BackupMerger.merge(emptyList(), existing, defaultSettings, imported, RestoreMode.REPLACE)

        assertEquals(1, result.bikes.size)
        assertEquals("b99", result.bikes.single().id)
    }

    @Test
    fun `REPLACE applies imported settings`() {
        val imported = BackupData(1, emptyList(), emptyList(), importedSettings)
        val result = BackupMerger.merge(emptyList(), emptyList(), defaultSettings, imported, RestoreMode.REPLACE)
        assertNotNull("REPLACE must return imported settings", result.settings)
        assertEquals("light", result.settings!!.theme)
        assertEquals("en", result.settings!!.lang)
        assertEquals("imperial", result.settings!!.units)
    }

    @Test
    fun `REPLACE with empty import produces empty routes and bikes`() {
        val existing = listOf(route("r1"), route("r2"))
        val imported = BackupData(1, emptyList(), emptyList(), importedSettings)

        val result = BackupMerger.merge(existing, emptyList(), defaultSettings, imported, RestoreMode.REPLACE)

        assertTrue(result.routes.isEmpty())
        assertEquals(0, result.addedRoutes)
        assertEquals(0, result.updatedRoutes)
    }

    @Test
    fun `REPLACE counts all imported items as added`() {
        val imported = BackupData(1, listOf(route("r1"), route("r2")), listOf(bike("b1")), importedSettings)
        val result = BackupMerger.merge(emptyList(), emptyList(), defaultSettings, imported, RestoreMode.REPLACE)
        assertEquals(2, result.addedRoutes)
        assertEquals(0, result.updatedRoutes)
        assertEquals(1, result.addedBikes)
        assertEquals(0, result.updatedBikes)
    }
}
