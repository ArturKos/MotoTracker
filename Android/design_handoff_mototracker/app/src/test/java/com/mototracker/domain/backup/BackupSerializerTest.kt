package com.mototracker.domain.backup

import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializerTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun route(
        id: String = "r1",
        bikeId: String? = "b1",
        wxJson: String? = """{"temp":20}""",
        pathJson: String? = """[{"lat":1.0,"lng":2.0}]""",
        speedJson: String? = "[10,20]",
        elevProfileJson: String? = "[100,110]",
        notes: String? = "Test note",
        correctedPathJson: String? = """[{"lat":1.0,"lng":2.1}]""",
        correctionStatus: CorrectionStatus = CorrectionStatus.DONE,
        confidence: Double? = 0.95,
    ) = Route(
        id = id,
        name = "Morning ride",
        dateEpochMs = 1_700_000_000_000L,
        bikeId = bikeId,
        km = 123.45,
        durSec = 3600L,
        avg = 80.0,
        max = 120.0,
        lean = 35.0,
        elev = 500.0,
        fuel = 8.5,
        synced = true,
        wxJson = wxJson,
        pathJson = pathJson,
        speedJson = speedJson,
        elevProfileJson = elevProfileJson,
        notes = notes,
        correctedPathJson = correctedPathJson,
        correctionStatus = correctionStatus,
        confidence = confidence,
    )

    private fun bike(id: String = "b1", status: BikeStatus = BikeStatus.ACTIVE) = Bike(
        id = id,
        name = "Yamaha MT-07",
        year = 2021,
        plate = "WA 12345",
        status = status,
    )

    private fun settings() = AppSettings(
        offline = true,
        autoSync = false,
        offlineOnly = true,
        gpsCorrect = false,
        currentBikeId = "b1",
        serverAddress = "http://example.com",
        units = "imperial",
        theme = "light",
        accent = "#FF5C38",
        lang = "en",
        autoPause = false,
        keepScreenOn = true,
        androidAutoEnabled = true,
        bcName = "Jan",
        bcPhone = "+48123456789",
        bcOrigin = "Warsaw",
        bcSocial = "@jan",
        debugLoggingEnabled = true,
        osrmBaseUrl = "http://osrm.example.com",
    )

    // ── Round-trip — all fields ───────────────────────────────────────────────

    @Test
    fun `encode then decode preserves all Route fields`() {
        val original = route()
        val data = BackupData(BackupData.CURRENT_SCHEMA_VERSION, listOf(original), emptyList(), AppSettings())
        val decoded = BackupSerializer.decode(BackupSerializer.encode(data)).getOrThrow()
        val r = decoded.routes.single()

        assertEquals(original.id, r.id)
        assertEquals(original.name, r.name)
        assertEquals(original.dateEpochMs, r.dateEpochMs)
        assertEquals(original.bikeId, r.bikeId)
        assertEquals(original.km, r.km, 0.001)
        assertEquals(original.durSec, r.durSec)
        assertEquals(original.avg, r.avg, 0.001)
        assertEquals(original.max, r.max, 0.001)
        assertEquals(original.lean, r.lean, 0.001)
        assertEquals(original.elev, r.elev, 0.001)
        assertEquals(original.fuel, r.fuel, 0.001)
        assertEquals(original.synced, r.synced)
        assertEquals(original.wxJson, r.wxJson)
        assertEquals(original.pathJson, r.pathJson)
        assertEquals(original.speedJson, r.speedJson)
        assertEquals(original.elevProfileJson, r.elevProfileJson)
        assertEquals(original.notes, r.notes)
        assertEquals(original.correctedPathJson, r.correctedPathJson)
        assertEquals(original.correctionStatus, r.correctionStatus)
        assertEquals(original.confidence!!, r.confidence!!, 0.0001)
    }

    @Test
    fun `encode then decode preserves null nullable Route fields`() {
        val original = route(bikeId = null, wxJson = null, pathJson = null, speedJson = null,
            elevProfileJson = null, notes = null, correctedPathJson = null, confidence = null,
            correctionStatus = CorrectionStatus.NONE)
        val data = BackupData(BackupData.CURRENT_SCHEMA_VERSION, listOf(original), emptyList(), AppSettings())
        val decoded = BackupSerializer.decode(BackupSerializer.encode(data)).getOrThrow()
        val r = decoded.routes.single()

        assertNull(r.bikeId)
        assertNull(r.wxJson)
        assertNull(r.pathJson)
        assertNull(r.speedJson)
        assertNull(r.elevProfileJson)
        assertNull(r.notes)
        assertNull(r.correctedPathJson)
        assertNull(r.confidence)
        assertEquals(CorrectionStatus.NONE, r.correctionStatus)
    }

    @Test
    fun `encode then decode preserves all Bike fields including SOLD status`() {
        val original = bike(status = BikeStatus.SOLD)
        val data = BackupData(BackupData.CURRENT_SCHEMA_VERSION, emptyList(), listOf(original), AppSettings())
        val decoded = BackupSerializer.decode(BackupSerializer.encode(data)).getOrThrow()
        val b = decoded.bikes.single()

        assertEquals(original.id, b.id)
        assertEquals(original.name, b.name)
        assertEquals(original.year, b.year)
        assertEquals(original.plate, b.plate)
        assertEquals(BikeStatus.SOLD, b.status)
    }

    @Test
    fun `encode then decode preserves all 20 AppSettings fields`() {
        val original = settings()
        val data = BackupData(BackupData.CURRENT_SCHEMA_VERSION, emptyList(), emptyList(), original)
        val decoded = BackupSerializer.decode(BackupSerializer.encode(data)).getOrThrow()
        val s = decoded.settings

        assertEquals(original.offline, s.offline)
        assertEquals(original.autoSync, s.autoSync)
        assertEquals(original.offlineOnly, s.offlineOnly)
        assertEquals(original.gpsCorrect, s.gpsCorrect)
        assertEquals(original.currentBikeId, s.currentBikeId)
        assertEquals(original.serverAddress, s.serverAddress)
        assertEquals(original.units, s.units)
        assertEquals(original.theme, s.theme)
        assertEquals(original.accent, s.accent)
        assertEquals(original.lang, s.lang)
        assertEquals(original.autoPause, s.autoPause)
        assertEquals(original.keepScreenOn, s.keepScreenOn)
        assertEquals(original.androidAutoEnabled, s.androidAutoEnabled)
        assertEquals(original.bcName, s.bcName)
        assertEquals(original.bcPhone, s.bcPhone)
        assertEquals(original.bcOrigin, s.bcOrigin)
        assertEquals(original.bcSocial, s.bcSocial)
        assertEquals(original.debugLoggingEnabled, s.debugLoggingEnabled)
        assertEquals(original.osrmBaseUrl, s.osrmBaseUrl)
    }

    @Test
    fun `encode then decode preserves null currentBikeId in settings`() {
        val original = AppSettings(currentBikeId = null)
        val data = BackupData(BackupData.CURRENT_SCHEMA_VERSION, emptyList(), emptyList(), original)
        val decoded = BackupSerializer.decode(BackupSerializer.encode(data)).getOrThrow()
        assertNull(decoded.settings.currentBikeId)
    }

    @Test
    fun `encode then decode preserves all CorrectionStatus enum values`() {
        for (status in CorrectionStatus.entries) {
            val r = route(correctionStatus = status, correctedPathJson = if (status == CorrectionStatus.DONE) "[{}]" else null)
            val data = BackupData(BackupData.CURRENT_SCHEMA_VERSION, listOf(r), emptyList(), AppSettings())
            val decoded = BackupSerializer.decode(BackupSerializer.encode(data)).getOrThrow()
            assertEquals(status, decoded.routes.single().correctionStatus)
        }
    }

    // ── decode failure cases ──────────────────────────────────────────────────

    @Test
    fun `decode returns failure on malformed JSON`() {
        val result = BackupSerializer.decode("not-json-at-all")
        assertTrue(result.isFailure)
    }

    @Test
    fun `decode returns failure on empty string`() {
        val result = BackupSerializer.decode("")
        assertTrue(result.isFailure)
    }

    @Test
    fun `decode returns failure when schemaVersion key is missing`() {
        val json = """{"routes":[],"bikes":[],"settings":{}}"""
        val result = BackupSerializer.decode(json)
        assertTrue(result.isFailure)
    }

    @Test
    fun `decode returns failure when routes key is missing`() {
        val json = """{"schemaVersion":1,"bikes":[],"settings":{}}"""
        val result = BackupSerializer.decode(json)
        assertTrue(result.isFailure)
    }

    @Test
    fun `decode returns failure when bikes key is missing`() {
        val json = """{"schemaVersion":1,"routes":[],"settings":{}}"""
        val result = BackupSerializer.decode(json)
        assertTrue(result.isFailure)
    }

    @Test
    fun `decode returns failure when settings key is missing`() {
        val json = """{"schemaVersion":1,"routes":[],"bikes":[]}"""
        val result = BackupSerializer.decode(json)
        assertTrue(result.isFailure)
    }

    @Test
    fun `decode returns failure when schemaVersion is newer than current`() {
        val json = """{"schemaVersion":${BackupData.CURRENT_SCHEMA_VERSION + 1},"routes":[],"bikes":[],"settings":{}}"""
        val result = BackupSerializer.decode(json)
        assertTrue(result.isFailure)
    }

    // ── Unknown enum names fall back to safe defaults ─────────────────────────

    @Test
    fun `unknown CorrectionStatus name falls back to NONE`() {
        val json = BackupSerializer.encode(
            BackupData(BackupData.CURRENT_SCHEMA_VERSION, listOf(route()), emptyList(), AppSettings())
        ).replace("\"DONE\"", "\"UNKNOWN_STATUS\"")
        val decoded = BackupSerializer.decode(json).getOrThrow()
        assertEquals(CorrectionStatus.NONE, decoded.routes.single().correctionStatus)
    }

    @Test
    fun `unknown BikeStatus name falls back to ACTIVE`() {
        val json = BackupSerializer.encode(
            BackupData(BackupData.CURRENT_SCHEMA_VERSION, emptyList(), listOf(bike(status = BikeStatus.SOLD)), AppSettings())
        ).replace("\"SOLD\"", "\"GONE\"")
        val decoded = BackupSerializer.decode(json).getOrThrow()
        assertEquals(BikeStatus.ACTIVE, decoded.bikes.single().status)
    }

    // ── Schema version ────────────────────────────────────────────────────────

    @Test
    fun `decode succeeds on current schema version`() {
        val data = BackupData(BackupData.CURRENT_SCHEMA_VERSION, emptyList(), emptyList(), AppSettings())
        val result = BackupSerializer.decode(BackupSerializer.encode(data))
        assertTrue(result.isSuccess)
        assertEquals(BackupData.CURRENT_SCHEMA_VERSION, result.getOrThrow().schemaVersion)
    }
}
