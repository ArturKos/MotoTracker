package com.mototracker.ui.screens.settings

import app.cash.turbine.test
import com.mototracker.data.diagnostics.RideLogShareIntentFactory
import com.mototracker.data.diagnostics.RideLogStore
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Calendar

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class FakeSettingsStore(
    initial: AppSettings = AppSettings(),
) : SettingsStore {
    private val _flow = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = _flow

    var lastBikeId: String? = "UNCHANGED"
    var lastOffline: Boolean? = null
    var lastAutoSync: Boolean? = null
    var lastOfflineOnly: Boolean? = null
    var lastGpsCorrect: Boolean? = null
    var lastTheme: String? = null
    var lastAccent: String? = null
    var lastLang: String? = null
    var lastUnits: String? = null
    var lastServerAddress: String? = null
    var lastAutoPause: Boolean? = null
    var lastKeepScreenOn: Boolean? = null
    var lastAndroidAutoEnabled: Boolean? = null
    var lastBcName: String? = null
    var lastBcPhone: String? = null
    var lastBcOrigin: String? = null
    var lastBcSocial: String? = null

    fun emit(s: AppSettings) { _flow.value = s }

    override suspend fun setOffline(value: Boolean) { lastOffline = value; _flow.value = _flow.value.copy(offline = value) }
    override suspend fun setAutoSync(value: Boolean) { lastAutoSync = value; _flow.value = _flow.value.copy(autoSync = value) }
    override suspend fun setOfflineOnly(value: Boolean) { lastOfflineOnly = value; _flow.value = _flow.value.copy(offlineOnly = value) }
    override suspend fun setGpsCorrect(value: Boolean) { lastGpsCorrect = value; _flow.value = _flow.value.copy(gpsCorrect = value) }
    override suspend fun setCurrentBikeId(bikeId: String?) { lastBikeId = bikeId; _flow.value = _flow.value.copy(currentBikeId = bikeId) }
    override suspend fun setServerAddress(address: String) { lastServerAddress = address; _flow.value = _flow.value.copy(serverAddress = address) }
    override suspend fun setUnits(units: String) { lastUnits = units; _flow.value = _flow.value.copy(units = units) }
    override suspend fun setTheme(theme: String) { lastTheme = theme; _flow.value = _flow.value.copy(theme = theme) }
    override suspend fun setAccent(accent: String) { lastAccent = accent; _flow.value = _flow.value.copy(accent = accent) }
    override suspend fun setLang(lang: String) { lastLang = lang; _flow.value = _flow.value.copy(lang = lang) }
    override suspend fun setAutoPause(value: Boolean) { lastAutoPause = value; _flow.value = _flow.value.copy(autoPause = value) }
    override suspend fun setKeepScreenOn(value: Boolean) { lastKeepScreenOn = value; _flow.value = _flow.value.copy(keepScreenOn = value) }
    override suspend fun setAndroidAutoEnabled(value: Boolean) { lastAndroidAutoEnabled = value; _flow.value = _flow.value.copy(androidAutoEnabled = value) }
    override suspend fun setBcName(name: String) { lastBcName = name; _flow.value = _flow.value.copy(bcName = name) }
    override suspend fun setBcPhone(phone: String) { lastBcPhone = phone; _flow.value = _flow.value.copy(bcPhone = phone) }
    override suspend fun setBcOrigin(origin: String) { lastBcOrigin = origin; _flow.value = _flow.value.copy(bcOrigin = origin) }
    override suspend fun setBcSocial(social: String) { lastBcSocial = social; _flow.value = _flow.value.copy(bcSocial = social) }
    override suspend fun setDebugLoggingEnabled(value: Boolean) { _flow.value = _flow.value.copy(debugLoggingEnabled = value) }
}

private class FakeBikeRepository : BikeRepository {
    private val _flow = MutableStateFlow<List<Bike>>(emptyList())
    val addedBikes = mutableListOf<Bike>()

    fun emit(bikes: List<Bike>) { _flow.value = bikes }
    override fun observeAll(): Flow<List<Bike>> = _flow
    override suspend fun addBike(bike: Bike) {
        addedBikes += bike
        // Upsert: replace entry with the same id, append if new.
        _flow.value = _flow.value.filter { it.id != bike.id } + bike
    }
}

private class FakeRouteRepository : RouteRepository {
    private val _flow = MutableStateFlow<List<Route>>(emptyList())

    fun emit(routes: List<Route>) { _flow.value = routes }
    override suspend fun save(route: Route) { _flow.value = _flow.value + route }
    override fun observeAll(): Flow<List<Route>> = _flow
    override suspend fun getById(id: String): Route? = _flow.value.find { it.id == id }
}

private class FakeSyncRepository : SyncRepository {
    private val _count = MutableStateFlow(0)
    var syncNowCalls = 0

    override val pendingCount: Flow<Int> = _count
    override suspend fun enqueue(routeId: String) {}
    override suspend fun syncNow(): Int { syncNowCalls++; return 0 }
    override fun start(scope: CoroutineScope) {}
}

private class FakeRideLogStore(
    private var bytesValue: Long = 0L,
    private var latestFile: File? = null,
) : RideLogStore {
    var clearCalls = 0

    override fun latestLog(): File? = latestFile
    override fun totalBytes(): Long = bytesValue

    override fun clear(): Int {
        clearCalls++
        val count = if (latestFile != null) 1 else 0
        latestFile = null
        bytesValue = 0L
        return count
    }

    fun setBytes(bytes: Long) { bytesValue = bytes }
    fun setLatestFile(file: File?) { latestFile = file }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun makeBike(
    id: String = "b1",
    name: String = "Yamaha MT-07",
    year: Int = 2020,
    plate: String = "WA 1234",
    status: BikeStatus = BikeStatus.ACTIVE,
) = Bike(id = id, name = name, year = year, plate = plate, status = status)

private fun makeRoute(
    id: String = "r1",
    name: String = "Route $id",
    km: Double = 100.0,
    synced: Boolean = true,
    dateEpochMs: Long = System.currentTimeMillis(),
) = Route(
    id = id,
    name = name,
    dateEpochMs = dateEpochMs,
    bikeId = null,
    km = km,
    durSec = 3600L,
    avg = 60.0,
    max = 120.0,
    lean = 20.0,
    elev = 500.0,
    fuel = 5.0,
    synced = synced,
    wxJson = null,
    pathJson = null,
    speedJson = null,
    elevProfileJson = null,
    notes = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var store: FakeSettingsStore
    private lateinit var bikeRepo: FakeBikeRepository
    private lateinit var routeRepo: FakeRouteRepository
    private lateinit var syncRepo: FakeSyncRepository
    private lateinit var logStore: FakeRideLogStore
    private lateinit var vm: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        store = FakeSettingsStore()
        bikeRepo = FakeBikeRepository()
        routeRepo = FakeRouteRepository()
        syncRepo = FakeSyncRepository()
        logStore = FakeRideLogStore()
        vm = SettingsViewModel(store, bikeRepo, routeRepo, syncRepo, logStore, RideLogShareIntentFactory())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Default state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state reflects AppSettings defaults`() {
        val s = vm.uiState.value
        assertEquals("cockpit", s.theme)
        assertEquals("metric", s.units)
        assertEquals("pl", s.language)
        assertFalse(s.offline)
        assertTrue(s.autoSync)
        assertTrue(s.autoPause)
        assertFalse(s.keepScreenOn)
        assertFalse(s.androidAutoEnabled)
        assertTrue(s.pendingRoutes.isEmpty())
        assertTrue(s.bikes.isEmpty())
    }

    // ── Bike section ──────────────────────────────────────────────────────────

    @Test
    fun `bikes are mapped with yearPlate format`() = runTest {
        bikeRepo.emit(listOf(makeBike(id = "b1", name = "MT-07", year = 2020, plate = "WA 1234")))
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.bikes.size)
            assertEquals("2020 · WA 1234", state.bikes[0].yearPlate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isCurrent flag set for currentBikeId bike`() = runTest {
        store.emit(AppSettings(currentBikeId = "b2"))
        bikeRepo.emit(listOf(makeBike("b1"), makeBike("b2")))
        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.bikes.first { it.id == "b1" }.isCurrent)
            assertTrue(state.bikes.first { it.id == "b2" }.isCurrent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sold bike has SOLD status in BikeUi`() = runTest {
        bikeRepo.emit(listOf(makeBike("b1", status = BikeStatus.SOLD)))
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(BikeStatus.SOLD, state.bikes[0].status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Sync queue section ────────────────────────────────────────────────────

    @Test
    fun `pendingRoutes shows only unsynced routes`() = runTest {
        routeRepo.emit(listOf(
            makeRoute("r1", synced = true),
            makeRoute("r2", synced = false),
            makeRoute("r3", synced = false),
        ))
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.pendingRoutes.size)
            assertTrue(state.pendingRoutes.none { it.routeId == "r1" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pendingRoutes is empty when all routes are synced`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", synced = true), makeRoute("r2", synced = true)))
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.pendingRoutes.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SyncQueueItemUi carries route id and name`() = runTest {
        routeRepo.emit(listOf(makeRoute("r42", name = "Mountain run", synced = false)))
        vm.uiState.test {
            val item = awaitItem().pendingRoutes[0]
            assertEquals("r42", item.routeId)
            assertEquals("Mountain run", item.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pendingRoutes km formatted according to units setting`() = runTest {
        store.emit(AppSettings(units = "imperial"))
        routeRepo.emit(listOf(makeRoute("r1", km = 100.0, synced = false)))
        vm.uiState.test {
            val item = awaitItem().pendingRoutes[0]
            assertTrue("expected miles", item.kmDisplay.endsWith("mi"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Broadcast section auto-fields ─────────────────────────────────────────

    @Test
    fun `bcBikeDisplay shows name and year of current bike`() = runTest {
        store.emit(AppSettings(currentBikeId = "b1"))
        bikeRepo.emit(listOf(makeBike("b1", name = "Yamaha MT-07", year = 2020)))
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("Yamaha MT-07 2020", state.bcBikeDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bcBikeDisplay is empty when no bike selected`() = runTest {
        store.emit(AppSettings(currentBikeId = null))
        bikeRepo.emit(listOf(makeBike("b1")))
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("", state.bcBikeDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bcTotalDisplay sums all routes km`() = runTest {
        routeRepo.emit(listOf(makeRoute("r1", km = 50.0), makeRoute("r2", km = 100.5)))
        vm.uiState.test {
            val state = awaitItem()
            // 150.5 km metric
            assertTrue(state.bcTotalDisplay.contains("150.5"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Intent → store delegation ─────────────────────────────────────────────

    @Test
    fun `selectBike calls store setCurrentBikeId`() = runTest {
        vm.selectBike("bike-xyz")
        assertEquals("bike-xyz", store.lastBikeId)
    }

    @Test
    fun `setTheme persists theme key`() = runTest {
        vm.setTheme("grid")
        assertEquals("grid", store.lastTheme)
    }

    @Test
    fun `setAccent persists accent hex`() = runTest {
        vm.setAccent("#FF5C38")
        assertEquals("#FF5C38", store.lastAccent)
    }

    @Test
    fun `setLanguage persists lang tag`() = runTest {
        vm.setLanguage("de")
        assertEquals("de", store.lastLang)
    }

    @Test
    fun `setUnits persists units key`() = runTest {
        vm.setUnits("imperial")
        assertEquals("imperial", store.lastUnits)
    }

    @Test
    fun `setServerAddress persists address`() = runTest {
        vm.setServerAddress("http://example.com")
        assertEquals("http://example.com", store.lastServerAddress)
    }

    @Test
    fun `setOffline persists flag`() = runTest {
        vm.setOffline(true)
        assertEquals(true, store.lastOffline)
    }

    @Test
    fun `setAutoSync persists flag`() = runTest {
        vm.setAutoSync(false)
        assertEquals(false, store.lastAutoSync)
    }

    @Test
    fun `setOfflineOnly persists flag`() = runTest {
        vm.setOfflineOnly(true)
        assertEquals(true, store.lastOfflineOnly)
    }

    @Test
    fun `setGpsCorrect persists flag`() = runTest {
        vm.setGpsCorrect(false)
        assertEquals(false, store.lastGpsCorrect)
    }

    @Test
    fun `setAndroidAutoEnabled persists flag`() = runTest {
        vm.setAndroidAutoEnabled(true)
        assertEquals(true, store.lastAndroidAutoEnabled)
    }

    @Test
    fun `setAutoPause persists flag`() = runTest {
        vm.setAutoPause(false)
        assertEquals(false, store.lastAutoPause)
    }

    @Test
    fun `setKeepScreenOn persists flag`() = runTest {
        vm.setKeepScreenOn(true)
        assertEquals(true, store.lastKeepScreenOn)
    }

    @Test
    fun `saveBroadcastProfile persists all four fields`() = runTest {
        vm.saveBroadcastProfile("Rider", "+48100200300", "Warsaw", "@rider")
        assertEquals("Rider", store.lastBcName)
        assertEquals("+48100200300", store.lastBcPhone)
        assertEquals("Warsaw", store.lastBcOrigin)
        assertEquals("@rider", store.lastBcSocial)
    }

    @Test
    fun `syncNow delegates to syncRepository`() = runTest {
        vm.syncNow()
        assertEquals(1, syncRepo.syncNowCalls)
    }

    // ── addBike intent → bikeRepository ──────────────────────────────────────

    @Test
    fun `addBike creates a bike with ACTIVE status`() = runTest {
        vm.addBike("Honda CBR", 2022, "KR 5678")
        val added = bikeRepo.addedBikes.single()
        assertEquals("Honda CBR", added.name)
        assertEquals(2022, added.year)
        assertEquals("KR 5678", added.plate)
        assertEquals(BikeStatus.ACTIVE, added.status)
    }

    @Test
    fun `addBike generates unique UUIDs for each call`() = runTest {
        vm.addBike("A", 2020, "AA1")
        vm.addBike("B", 2021, "BB2")
        assertEquals(2, bikeRepo.addedBikes.size)
        val ids = bikeRepo.addedBikes.map { it.id }.toSet()
        assertEquals(2, ids.size)
    }

    @Test
    fun `addBike persists given status`() = runTest {
        vm.addBike("Honda CBR", 2022, "KR 5678", BikeStatus.SOLD)
        val added = bikeRepo.addedBikes.last()
        assertEquals(BikeStatus.SOLD, added.status)
    }

    @Test
    fun `addBike no-ops on blank name`() = runTest {
        val sizeBefore = bikeRepo.addedBikes.size
        vm.addBike("  ", 2022, "KR 5678", BikeStatus.ACTIVE)
        assertEquals(sizeBefore, bikeRepo.addedBikes.size)
    }

    @Test
    fun `addBike no-ops on invalid year`() = runTest {
        val sizeBefore = bikeRepo.addedBikes.size
        vm.addBike("Honda CBR", 0, "KR 5678", BikeStatus.ACTIVE)
        assertEquals(sizeBefore, bikeRepo.addedBikes.size)
    }

    // ── updateBike intent ─────────────────────────────────────────────────────

    @Test
    fun `updateBike upserts with the same id and new fields`() = runTest {
        bikeRepo.emit(listOf(makeBike("b1", name = "Old name", year = 2020, plate = "OLD 1")))
        vm.updateBike("b1", "New name", 2023, "NEW 2", BikeStatus.SOLD)
        val updated = bikeRepo.addedBikes.last()
        assertEquals("b1", updated.id)
        assertEquals("New name", updated.name)
        assertEquals(2023, updated.year)
        assertEquals("NEW 2", updated.plate)
        assertEquals(BikeStatus.SOLD, updated.status)
    }

    @Test
    fun `updateBike upserted bike appears in observeAll`() = runTest {
        bikeRepo.emit(listOf(makeBike("b1", name = "Old name")))
        vm.updateBike("b1", "New name", 2023, "NEW 2", BikeStatus.ACTIVE)
        vm.uiState.test {
            val state = awaitItem()
            val found = state.bikes.find { it.id == "b1" }
            requireNotNull(found)
            assertEquals("New name", found.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateBike no-ops on blank name`() = runTest {
        val sizeBefore = bikeRepo.addedBikes.size
        vm.updateBike("b1", "  ", 2020, "WA 1234", BikeStatus.ACTIVE)
        assertEquals(sizeBefore, bikeRepo.addedBikes.size)
    }

    @Test
    fun `updateBike no-ops on invalid year`() = runTest {
        val sizeBefore = bikeRepo.addedBikes.size
        vm.updateBike("b1", "Honda CBR", 1800, "WA 1234", BikeStatus.ACTIVE)
        assertEquals(sizeBefore, bikeRepo.addedBikes.size)
    }

    // ── BikeFormValidation (pure unit tests) ──────────────────────────────────

    @Test
    fun `BikeFormValidation returns NameBlank for empty name`() {
        assertEquals(BikeFormResult.NameBlank, BikeFormValidation.validate("", "2022", "WA 1234"))
    }

    @Test
    fun `BikeFormValidation returns NameBlank for whitespace-only name`() {
        assertEquals(BikeFormResult.NameBlank, BikeFormValidation.validate("   ", "2022", "WA 1234"))
    }

    @Test
    fun `BikeFormValidation returns YearInvalid for non-numeric year`() {
        assertEquals(BikeFormResult.YearInvalid, BikeFormValidation.validate("Honda CBR", "abc", "WA 1234"))
    }

    @Test
    fun `BikeFormValidation returns YearInvalid for year below range`() {
        assertEquals(BikeFormResult.YearInvalid, BikeFormValidation.validate("Honda CBR", "1899", "WA 1234"))
    }

    @Test
    fun `BikeFormValidation returns YearInvalid for year above range`() {
        assertEquals(BikeFormResult.YearInvalid, BikeFormValidation.validate("Honda CBR", "2031", "WA 1234"))
    }

    @Test
    fun `BikeFormValidation returns YearInvalid for zero year`() {
        assertEquals(BikeFormResult.YearInvalid, BikeFormValidation.validate("Honda CBR", "0", "WA 1234"))
    }

    @Test
    fun `BikeFormValidation returns Valid for correct inputs`() {
        val result = BikeFormValidation.validate("  Honda CBR  ", "2022", "  WA 1234  ")
        assertTrue(result is BikeFormResult.Valid)
        val valid = result as BikeFormResult.Valid
        assertEquals("Honda CBR", valid.name)
        assertEquals(2022, valid.year)
        assertEquals("WA 1234", valid.plate)
    }

    @Test
    fun `BikeFormValidation Valid trims name and plate`() {
        val result = BikeFormValidation.validate("  MT-07  ", "2020", "  WA 07  ")
        assertTrue(result is BikeFormResult.Valid)
        val valid = result as BikeFormResult.Valid
        assertEquals("MT-07", valid.name)
        assertEquals("WA 07", valid.plate)
    }

    @Test
    fun `BikeFormValidation allows blank plate`() {
        val result = BikeFormValidation.validate("MT-07", "2020", "")
        assertTrue(result is BikeFormResult.Valid)
    }

    // ── Diagnostics (B10) ─────────────────────────────────────────────────────

    @Test
    fun `setDebugLogging persists value to settings store`() = runTest {
        vm.setDebugLogging(true)
        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state.debugLoggingEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDebugLogging false persists false to settings store`() = runTest {
        store.emit(AppSettings(debugLoggingEnabled = true))
        vm.setDebugLogging(false)
        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.debugLoggingEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rideLogUsedBytes reflects store totalBytes on init`() = runTest {
        logStore.setBytes(2048L)
        val localVm = SettingsViewModel(store, bikeRepo, routeRepo, syncRepo, logStore, RideLogShareIntentFactory())
        // The init block loads bytes on Dispatchers.IO; intermediate combine emissions may have 0L.
        // Drain until we find the non-zero value emitted after the init block completes.
        localVm.uiState.test {
            var state = awaitItem()
            while (state.rideLogUsedBytes == 0L) { state = awaitItem() }
            assertEquals(2048L, state.rideLogUsedBytes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearRideLogs calls store clear and updates rideLogUsedBytes to 0`() = runTest {
        logStore.setBytes(4096L)
        val localVm = SettingsViewModel(store, bikeRepo, routeRepo, syncRepo, logStore, RideLogShareIntentFactory())
        // The init block loads bytes on Dispatchers.IO; intermediate combine emissions may have 0L.
        // Drain until we see the init-loaded state (4096L) before testing clear.
        localVm.uiState.test {
            var initialState = awaitItem()
            while (initialState.rideLogUsedBytes == 0L) { initialState = awaitItem() }
            assertEquals(4096L, initialState.rideLogUsedBytes)

            localVm.clearRideLogs()
            val updatedState = awaitItem()
            assertEquals(0L, updatedState.rideLogUsedBytes)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, logStore.clearCalls)
    }

    @Test
    fun `getShareTargetFile returns null when no log exists`() {
        logStore.setLatestFile(null)
        assertNull(vm.getShareTargetFile())
    }

    @Test
    fun `getShareTargetFile returns latest log file from store`() {
        val fakeFile = File("/tmp/ride-test.log")
        logStore.setLatestFile(fakeFile)
        assertEquals(fakeFile, vm.getShareTargetFile())
    }

    // ── Settings state reflected in uiState ───────────────────────────────────

    @Test
    fun `updated settings emit new state`() = runTest {
        store.emit(AppSettings(theme = "light", units = "imperial", offlineOnly = true))
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("light", state.theme)
            assertEquals("imperial", state.units)
            assertTrue(state.offlineOnly)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `broadcast profile fields propagated from settings`() = runTest {
        store.emit(AppSettings(bcName = "SpeedRider", bcPhone = "+1234", bcOrigin = "Kraków", bcSocial = "@sr"))
        vm.uiState.test {
            val state = awaitItem()
            assertEquals("SpeedRider", state.bcName)
            assertEquals("+1234", state.bcPhone)
            assertEquals("Kraków", state.bcOrigin)
            assertEquals("@sr", state.bcSocial)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new boolean settings propagated to state`() = runTest {
        store.emit(AppSettings(autoPause = false, keepScreenOn = true, androidAutoEnabled = true))
        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.autoPause)
            assertTrue(state.keepScreenOn)
            assertTrue(state.androidAutoEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
