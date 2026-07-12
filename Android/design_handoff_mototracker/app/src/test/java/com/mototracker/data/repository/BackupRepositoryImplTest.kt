package com.mototracker.data.repository

import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.SettingsStore
import com.mototracker.domain.backup.BackupData
import com.mototracker.domain.backup.RestoreMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class FakeRouteRepo(initial: List<Route> = emptyList()) : RouteRepository {
    private val _flow = MutableStateFlow(initial)
    var deleteAllCalled = false

    override suspend fun save(route: Route) {
        val updated = _flow.value.filter { it.id != route.id } + route
        _flow.value = updated
    }

    override fun observeAll(): Flow<List<Route>> = _flow
    override suspend fun getById(id: String): Route? = _flow.value.find { it.id == id }
    override fun observeById(id: String): Flow<Route?> = MutableStateFlow(_flow.value.find { it.id == id })
    override suspend fun clearCorrectedTrace(id: String) {}
    override suspend fun rename(id: String, name: String) {}
    override suspend fun setBike(routeId: String, bikeId: String?) {}
    override suspend fun deleteAll() {
        deleteAllCalled = true
        _flow.value = emptyList()
    }
}

private class FakeBikeRepo(initial: List<Bike> = emptyList()) : BikeRepository {
    private val _flow = MutableStateFlow(initial)
    var deleteAllCalled = false

    override fun observeAll(): Flow<List<Bike>> = _flow
    override suspend fun addBike(bike: Bike) {
        val updated = _flow.value.filter { it.id != bike.id } + bike
        _flow.value = updated
    }
    override suspend fun deleteAll() {
        deleteAllCalled = true
        _flow.value = emptyList()
    }
}

private class FakeSettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
    private val _flow = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = _flow

    val appliedSettings = mutableListOf<Pair<String, Any?>>()

    override suspend fun setOffline(value: Boolean) { appliedSettings += "offline" to value; _flow.value = _flow.value.copy(offline = value) }
    override suspend fun setAutoSync(value: Boolean) { appliedSettings += "autoSync" to value; _flow.value = _flow.value.copy(autoSync = value) }
    override suspend fun setOfflineOnly(value: Boolean) { appliedSettings += "offlineOnly" to value; _flow.value = _flow.value.copy(offlineOnly = value) }
    override suspend fun setGpsCorrect(value: Boolean) { appliedSettings += "gpsCorrect" to value; _flow.value = _flow.value.copy(gpsCorrect = value) }
    override suspend fun setCurrentBikeId(bikeId: String?) { appliedSettings += "currentBikeId" to bikeId; _flow.value = _flow.value.copy(currentBikeId = bikeId) }
    override suspend fun setUnits(units: String) { appliedSettings += "units" to units; _flow.value = _flow.value.copy(units = units) }
    override suspend fun setTheme(theme: String) { appliedSettings += "theme" to theme; _flow.value = _flow.value.copy(theme = theme) }
    override suspend fun setAccent(accent: String) { appliedSettings += "accent" to accent; _flow.value = _flow.value.copy(accent = accent) }
    override suspend fun setLang(lang: String) { appliedSettings += "lang" to lang; _flow.value = _flow.value.copy(lang = lang) }
    override suspend fun setAutoPause(value: Boolean) { appliedSettings += "autoPause" to value; _flow.value = _flow.value.copy(autoPause = value) }
    override suspend fun setKeepScreenOn(value: Boolean) { appliedSettings += "keepScreenOn" to value; _flow.value = _flow.value.copy(keepScreenOn = value) }
    override suspend fun setAndroidAutoEnabled(value: Boolean) { appliedSettings += "androidAutoEnabled" to value; _flow.value = _flow.value.copy(androidAutoEnabled = value) }
    override suspend fun setBcName(name: String) { appliedSettings += "bcName" to name; _flow.value = _flow.value.copy(bcName = name) }
    override suspend fun setBcPhone(phone: String) { appliedSettings += "bcPhone" to phone; _flow.value = _flow.value.copy(bcPhone = phone) }
    override suspend fun setBcOrigin(origin: String) { appliedSettings += "bcOrigin" to origin; _flow.value = _flow.value.copy(bcOrigin = origin) }
    override suspend fun setBcSocial(social: String) { appliedSettings += "bcSocial" to social; _flow.value = _flow.value.copy(bcSocial = social) }
    override suspend fun setDebugLoggingEnabled(value: Boolean) { appliedSettings += "debugLoggingEnabled" to value; _flow.value = _flow.value.copy(debugLoggingEnabled = value) }
    override suspend fun setServerAddress(address: String) { appliedSettings += "serverAddress" to address; _flow.value = _flow.value.copy(serverAddress = address) }
    override suspend fun setOsrmBaseUrl(url: String) { appliedSettings += "osrmBaseUrl" to url; _flow.value = _flow.value.copy(osrmBaseUrl = url) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun route(id: String) = Route(
    id = id, name = "Route $id", dateEpochMs = 1_700_000_000_000L,
    bikeId = null, km = 10.0, durSec = 600L, avg = 60.0, max = 90.0, lean = 20.0,
    elev = 100.0, fuel = 2.0, synced = false,
    wxJson = null, pathJson = null, speedJson = null, elevProfileJson = null,
    notes = null, correctedPathJson = null,
    correctionStatus = CorrectionStatus.NONE, confidence = null,
)

private fun bike(id: String) = Bike(
    id = id, name = "Bike $id", year = 2020, plate = "XX $id", status = BikeStatus.ACTIVE,
)

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

class BackupRepositoryImplTest {

    private lateinit var routeRepo: FakeRouteRepo
    private lateinit var bikeRepo: FakeBikeRepo
    private lateinit var settingsStore: FakeSettingsStore
    private lateinit var repo: BackupRepositoryImpl

    @Before
    fun setUp() {
        routeRepo = FakeRouteRepo()
        bikeRepo = FakeBikeRepo()
        settingsStore = FakeSettingsStore()
        repo = BackupRepositoryImpl(routeRepo, bikeRepo, settingsStore)
    }

    // ── exportBackup ──────────────────────────────────────────────────────────

    @Test
    fun `exportBackup succeeds and includes routes, bikes, and settings`() = runTest {
        routeRepo = FakeRouteRepo(listOf(route("r1")))
        bikeRepo = FakeBikeRepo(listOf(bike("b1")))
        settingsStore = FakeSettingsStore(AppSettings(theme = "grid"))
        repo = BackupRepositoryImpl(routeRepo, bikeRepo, settingsStore)

        val result = repo.exportBackup()
        assertTrue(result.isSuccess)
        val json = result.getOrThrow()
        assertTrue(json.contains("\"r1\""))
        assertTrue(json.contains("\"b1\""))
        assertTrue(json.contains("\"grid\""))
    }

    @Test
    fun `exportBackup with empty data succeeds`() = runTest {
        val result = repo.exportBackup()
        assertTrue(result.isSuccess)
        val decoded = com.mototracker.domain.backup.BackupSerializer.decode(result.getOrThrow()).getOrThrow()
        assertTrue(decoded.routes.isEmpty())
        assertTrue(decoded.bikes.isEmpty())
    }

    // ── importBackup — MERGE ──────────────────────────────────────────────────

    @Test
    fun `importBackup MERGE upserts imported routes without deleting existing`() = runTest {
        routeRepo = FakeRouteRepo(listOf(route("existing")))
        repo = BackupRepositoryImpl(routeRepo, bikeRepo, settingsStore)

        val importedData = BackupData(1, listOf(route("imported")), emptyList(), AppSettings())
        val json = com.mototracker.domain.backup.BackupSerializer.encode(importedData)

        val result = repo.importBackup(json, RestoreMode.MERGE)
        assertTrue(result.isSuccess)
        assertFalse("deleteAll should NOT be called for MERGE", routeRepo.deleteAllCalled)

        val ids = routeRepo.observeAll().first().map { it.id }.toSet()
        // Both existing and imported are present
        assertTrue("existing" in ids)
        assertTrue("imported" in ids)
    }

    @Test
    fun `importBackup MERGE does not call deleteAll on RouteRepository`() = runTest {
        val json = com.mototracker.domain.backup.BackupSerializer.encode(
            BackupData(1, listOf(route("r1")), emptyList(), AppSettings())
        )
        repo.importBackup(json, RestoreMode.MERGE)
        assertFalse(routeRepo.deleteAllCalled)
        assertFalse(bikeRepo.deleteAllCalled)
    }

    @Test
    fun `importBackup MERGE does not apply imported settings`() = runTest {
        val importedSettings = AppSettings(theme = "grid", lang = "en")
        val json = com.mototracker.domain.backup.BackupSerializer.encode(
            BackupData(1, emptyList(), emptyList(), importedSettings)
        )
        repo.importBackup(json, RestoreMode.MERGE)
        // No settings setter should have been called
        assertTrue("Settings must not be applied on MERGE", settingsStore.appliedSettings.isEmpty())
    }

    @Test
    fun `importBackup MERGE returns correct summary counts`() = runTest {
        routeRepo = FakeRouteRepo(listOf(route("r1")))
        bikeRepo = FakeBikeRepo(listOf(bike("b1")))
        repo = BackupRepositoryImpl(routeRepo, bikeRepo, settingsStore)

        // Import: r1 (collision=updated), r2 (new), b1 (collision=updated), b3 (new)
        val imported = BackupData(1, listOf(route("r1"), route("r2")), listOf(bike("b1"), bike("b3")), AppSettings())
        val json = com.mototracker.domain.backup.BackupSerializer.encode(imported)

        val summary = repo.importBackup(json, RestoreMode.MERGE).getOrThrow()
        assertEquals(1, summary.addedRoutes)
        assertEquals(1, summary.updatedRoutes)
        assertEquals(1, summary.addedBikes)
        assertEquals(1, summary.updatedBikes)
    }

    // ── importBackup — REPLACE ────────────────────────────────────────────────

    @Test
    fun `importBackup REPLACE calls deleteAll on RouteRepository and BikeRepository`() = runTest {
        val json = com.mototracker.domain.backup.BackupSerializer.encode(
            BackupData(1, listOf(route("r1")), listOf(bike("b1")), AppSettings())
        )
        repo.importBackup(json, RestoreMode.REPLACE)
        assertTrue("deleteAll must be called on RouteRepo for REPLACE", routeRepo.deleteAllCalled)
        assertTrue("deleteAll must be called on BikeRepo for REPLACE", bikeRepo.deleteAllCalled)
    }

    @Test
    fun `importBackup REPLACE applies imported settings`() = runTest {
        val importedSettings = AppSettings(theme = "grid", lang = "en", units = "imperial")
        val json = com.mototracker.domain.backup.BackupSerializer.encode(
            BackupData(1, emptyList(), emptyList(), importedSettings)
        )
        repo.importBackup(json, RestoreMode.REPLACE)
        val appliedKeys = settingsStore.appliedSettings.map { it.first }.toSet()
        assertTrue("theme should be set", "theme" in appliedKeys)
        assertTrue("lang should be set", "lang" in appliedKeys)
        assertTrue("units should be set", "units" in appliedKeys)
    }

    @Test
    fun `importBackup REPLACE inserts all imported routes`() = runTest {
        routeRepo = FakeRouteRepo(listOf(route("old1"), route("old2")))
        repo = BackupRepositoryImpl(routeRepo, bikeRepo, settingsStore)

        val json = com.mototracker.domain.backup.BackupSerializer.encode(
            BackupData(1, listOf(route("new1")), emptyList(), AppSettings())
        )
        repo.importBackup(json, RestoreMode.REPLACE)

        // After deleteAll + save(new1), only new1 should be present
        val routes = routeRepo.observeAll().first()
        assertEquals(1, routes.size)
        assertEquals("new1", routes.single().id)
    }

    // ── decode failure propagation ────────────────────────────────────────────

    @Test
    fun `importBackup returns failure on malformed JSON`() = runTest {
        val result = repo.importBackup("not-json", RestoreMode.MERGE)
        assertTrue(result.isFailure)
    }

    @Test
    fun `importBackup returns failure on newer schema version`() = runTest {
        val json = """{"schemaVersion":${BackupData.CURRENT_SCHEMA_VERSION + 1},"routes":[],"bikes":[],"settings":{}}"""
        val result = repo.importBackup(json, RestoreMode.MERGE)
        assertTrue(result.isFailure)
    }
}
