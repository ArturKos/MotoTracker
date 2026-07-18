package com.mototracker.ui.screens.record

import com.mototracker.R
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.resource.StringResolver
import com.mototracker.core.time.TimeProvider
import com.mototracker.data.diagnostics.RideDebugLogger
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.location.GnssSatelliteCount
import com.mototracker.data.location.GnssStatusClient
import com.mototracker.data.location.LocationClient
import com.mototracker.data.location.ReverseGeocoder
import com.mototracker.data.location.RideLocationCollector
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.model.mapper.toRouteSummaryModel
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.network.WeatherClient
import com.mototracker.data.network.WeatherSnapshot
import com.mototracker.data.recording.ActiveSessionSnapshot
import com.mototracker.data.recording.RecordingSessionStore
import com.mototracker.data.recording.ResumeRouteBus
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.FuelAdjustmentRepository
import com.mototracker.data.repository.RefuelRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.sensor.HeadingSensorSource
import com.mototracker.data.sensor.LeanSensorSource
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.data.settings.SettingsStore
import com.mototracker.domain.battery.BatteryOptimizationChecker
import com.mototracker.domain.fuel.AutoUpdateBikeConsumptionUseCase
import com.mototracker.domain.fuel.FuelAdjustmentEvent
import com.mototracker.domain.fuel.FuelAdjustmentMode
import com.mototracker.domain.fuel.RefuelEvent
import com.mototracker.domain.recording.LocationSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Local fakes (R1-specific)
// ─────────────────────────────────────────────────────────────────────────────

private class FCFakeLocationCollector : RideLocationCollector(
    locationClient = object : LocationClient {
        override fun locationUpdates(intervalMs: Long): Flow<LocationSample> = flow {}
    },
    gnssStatusClient = object : GnssStatusClient {
        override fun satelliteCounts(): Flow<GnssSatelliteCount> = flow {}
    },
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
) {
    private val _flow = MutableSharedFlow<LocationSample>(
        replay = 0, extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _satFlow = MutableSharedFlow<GnssSatelliteCount>(
        replay = 1, extraBufferCapacity = 0, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val samples: SharedFlow<LocationSample> = _flow.asSharedFlow()
    override val satelliteCounts: SharedFlow<GnssSatelliteCount> = _satFlow.asSharedFlow()
    override fun start(intervalMs: Long) {}
    override fun stop() {}
    override fun startGnss() {}
    override fun stopGnss() {}
}

private class FCFakeRouteRepository : RouteRepository {
    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    override suspend fun save(route: Route) { _routes.value = _routes.value + route }
    override fun observeSummaries(): Flow<List<RouteSummaryModel>> =
        _routes.map { list -> list.map { it.toRouteSummaryModel() } }
    override suspend fun getById(id: String): Route? = _routes.value.find { it.id == id }
    override fun observeById(id: String): Flow<Route?> = MutableStateFlow(null)
    override suspend fun clearCorrectedTrace(id: String) {}
    override suspend fun deleteAll() {}
    override suspend fun rename(id: String, name: String) {}
    override suspend fun setBike(routeId: String, bikeId: String?) {}
}

private class FCFakeBikeRepository(
    private val bikes: List<Bike> = emptyList(),
) : BikeRepository {
    override fun observeAll(): Flow<List<Bike>> = MutableStateFlow(bikes)
    override suspend fun addBike(bike: Bike) {}
    override suspend fun deleteAll() {}
}

private class FCFakeSyncRepository : SyncRepository {
    override val pendingCount: Flow<Int> = MutableStateFlow(0)
    override suspend fun enqueue(routeId: String) {}
    override suspend fun syncNow(): Int = 0
    override fun start(scope: CoroutineScope) = Unit
}

private class FCFakeRefuelRepository : RefuelRepository {
    override suspend fun addRefuel(routeId: String, epochMs: Long, litres: Double, pricePerL: Double) {}
    override fun observeRefuels(routeId: String): Flow<List<RefuelEvent>> = MutableStateFlow(emptyList())
    override suspend fun deleteRefuel(id: Long) {}
    override fun observeAllForBike(bikeId: String): Flow<List<RefuelEvent>> = MutableStateFlow(emptyList())
}

/** Tracking [FuelAdjustmentRepository] that records all [addAdjustment] calls. */
private class FCFakeFuelAdjustmentRepository : FuelAdjustmentRepository {
    val adjustments = mutableListOf<FuelAdjustmentEvent>()
    override suspend fun addAdjustment(
        bikeId: String,
        routeId: String?,
        epochMs: Long,
        mode: FuelAdjustmentMode,
        litres: Double,
    ) {
        adjustments += FuelAdjustmentEvent(
            id = (adjustments.size + 1).toLong(),
            bikeId = bikeId,
            routeId = routeId,
            epochMs = epochMs,
            mode = mode,
            litres = litres,
        )
    }
    override fun observeForBike(bikeId: String): Flow<List<FuelAdjustmentEvent>> = MutableStateFlow(emptyList())
    override suspend fun latestForBike(bikeId: String): FuelAdjustmentEvent? = null
}

private class FCFakeSessionStore : RecordingSessionStore {
    override val snapshot: Flow<ActiveSessionSnapshot?> = MutableStateFlow(null)
    override suspend fun save(s: ActiveSessionSnapshot) {}
    override suspend fun clear() {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingViewModelFuelCorrectionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fuelAdjustmentRepo: FCFakeFuelAdjustmentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fuelAdjustmentRepo = FCFakeFuelAdjustmentRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── ShowFuelCorrectionDialog ──────────────────────────────────────────────

    @Test
    fun `ShowFuelCorrectionDialog sets showFuelCorrectionDialog to true in Idle`() = runTest(testDispatcher) {
        val vm = buildVm(withTank = true)
        // Idle phase — no ride started; dialog should still open
        vm.onEvent(RecordingEvent.ShowFuelCorrectionDialog)
        advanceTimeBy(100L)

        assertTrue(
            "showFuelCorrectionDialog should be true after ShowFuelCorrectionDialog",
            vm.uiState.value.showFuelCorrectionDialog,
        )
    }

    @Test
    fun `ShowFuelCorrectionDialog works without a tank configured`() = runTest(testDispatcher) {
        val vm = buildVm(withTank = false)
        vm.onEvent(RecordingEvent.ShowFuelCorrectionDialog)
        advanceTimeBy(100L)

        assertTrue(vm.uiState.value.showFuelCorrectionDialog)
    }

    // ── DismissFuelCorrectionDialog ───────────────────────────────────────────

    @Test
    fun `DismissFuelCorrectionDialog closes the dialog`() = runTest(testDispatcher) {
        val vm = buildVm(withTank = true)
        vm.onEvent(RecordingEvent.ShowFuelCorrectionDialog)
        advanceTimeBy(100L)
        assertTrue(vm.uiState.value.showFuelCorrectionDialog)

        vm.onEvent(RecordingEvent.DismissFuelCorrectionDialog)
        advanceTimeBy(100L)
        assertFalse(vm.uiState.value.showFuelCorrectionDialog)
    }

    // ── ConfirmFuelCorrection ─────────────────────────────────────────────────

    @Test
    fun `ConfirmFuelCorrection closes the dialog`() = runTest(testDispatcher) {
        val vm = buildVm(withTank = true, bikeId = "bike-1")
        // Let settings subscription run so currentBikeId is populated
        advanceTimeBy(100L)

        vm.onEvent(RecordingEvent.ShowFuelCorrectionDialog)
        advanceTimeBy(100L)
        vm.onEvent(RecordingEvent.ConfirmFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 10.0))
        advanceTimeBy(100L)

        assertFalse(
            "dialog should be closed after ConfirmFuelCorrection",
            vm.uiState.value.showFuelCorrectionDialog,
        )
    }

    @Test
    fun `ConfirmFuelCorrection persists event to repository`() = runTest(testDispatcher) {
        val vm = buildVm(withTank = true, bikeId = "bike-1")
        advanceTimeBy(100L) // let settings subscription populate currentBikeId

        vm.onEvent(RecordingEvent.ConfirmFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 12.0))
        advanceTimeBy(100L)

        assertEquals(
            "one adjustment event should be persisted",
            1,
            fuelAdjustmentRepo.adjustments.size,
        )
        val saved = fuelAdjustmentRepo.adjustments.first()
        assertEquals(FuelAdjustmentMode.SET_ABSOLUTE, saved.mode)
        assertEquals(12.0, saved.litres, 0.001)
        assertEquals("bike-1", saved.bikeId)
    }

    @Test
    fun `ConfirmFuelCorrection DELTA persists with correct mode and value`() = runTest(testDispatcher) {
        val vm = buildVm(withTank = true, bikeId = "bike-2")
        advanceTimeBy(100L)

        vm.onEvent(RecordingEvent.ConfirmFuelCorrection(FuelAdjustmentMode.DELTA, -3.5))
        advanceTimeBy(100L)

        val saved = fuelAdjustmentRepo.adjustments.firstOrNull()
        assertTrue("adjustment should be saved", saved != null)
        assertEquals(FuelAdjustmentMode.DELTA, saved!!.mode)
        assertEquals(-3.5, saved.litres, 0.001)
    }

    @Test
    fun `ConfirmFuelCorrection no-op when bikeId is null`() = runTest(testDispatcher) {
        val vm = buildVm(withTank = true, bikeId = null)
        advanceTimeBy(100L)

        vm.onEvent(RecordingEvent.ConfirmFuelCorrection(FuelAdjustmentMode.SET_ABSOLUTE, 10.0))
        advanceTimeBy(100L)

        assertEquals(
            "no adjustment should be persisted when bikeId is null",
            0,
            fuelAdjustmentRepo.adjustments.size,
        )
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun buildVm(
        withTank: Boolean = false,
        bikeId: String? = null,
    ): RecordingViewModel {
        val tankL = if (withTank) 20.0 else null
        val bikes = if (bikeId != null) listOf(
            Bike(
                id = bikeId,
                name = "Test Bike",
                year = 2020,
                plate = "TEST",
                status = BikeStatus.ACTIVE,
                tankCapacityL = tankL,
            ),
        ) else emptyList()
        val settings = AppSettings(currentBikeId = bikeId)
        val routeRepo = FCFakeRouteRepository()
        val bikeRepo = FCFakeBikeRepository(bikes)
        val refuelRepo = FCFakeRefuelRepository()
        return RecordingViewModel(
            rideLocationCollector = FCFakeLocationCollector(),
            leanSensorSource = object : LeanSensorSource { override val leanAngles: Flow<Double> = flow {} },
            headingSensorSource = object : HeadingSensorSource { override val headings: Flow<Float> = flow {} },
            routeRepository = routeRepo,
            syncRepository = FCFakeSyncRepository(),
            settingsSource = object : AppSettingsSource {
                override val settings: Flow<AppSettings> = MutableStateFlow(settings)
            },
            bikeRepository = bikeRepo,
            networkMonitor = object : NetworkMonitor {
                override val isOnline: Flow<Boolean> = MutableStateFlow(true)
            },
            timeProvider = object : TimeProvider { override fun nowEpochMs(): Long = 1_000_000L },
            carBridge = CarRecordingBridge(),
            rideDebugLogger = object : RideDebugLogger {
                override fun beginRide() {}
                override fun endRide() {}
                override fun log(tag: String, message: String) {}
            },
            reverseGeocoder = object : ReverseGeocoder {
                override suspend fun areaName(lat: Double, lng: Double): String? = null
            },
            stringResolver = object : StringResolver {
                override fun getString(resId: Int): String = when (resId) {
                    R.string.route_name_ride_morning   -> "morning ride"
                    R.string.route_name_ride_afternoon -> "afternoon ride"
                    R.string.route_name_ride_evening   -> "evening ride"
                    R.string.route_name_ride_night     -> "night ride"
                    R.string.route_name_with_area      -> "%1\$s – %2\$s"
                    else -> "stub"
                }
                override fun getString(resId: Int, vararg args: Any): String = getString(resId)
            },
            sessionStore = FCFakeSessionStore(),
            refuelRepository = refuelRepo,
            fuelAdjustmentRepository = fuelAdjustmentRepo,
            resumeRouteBus = object : ResumeRouteBus {
                override val requests: Flow<String> = flow {}
                override suspend fun request(routeId: String) {}
            },
            autoUpdateBikeConsumptionUseCase = AutoUpdateBikeConsumptionUseCase(
                bikeRepository = bikeRepo,
                routeRepository = routeRepo,
                refuelRepository = refuelRepo,
            ),
            weatherClient = object : WeatherClient {
                override suspend fun fetch(lat: Double, lon: Double): Result<WeatherSnapshot> =
                    Result.success(WeatherSnapshot(tempC = 20, humidity = 60, rain = false))
            },
            batteryOptimizationChecker = object : BatteryOptimizationChecker {
                override fun isIgnoringBatteryOptimizations() = true
            },
            settingsStore = object : SettingsStore {
                override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())
                override suspend fun setNoInternet(value: Boolean) {}
                override suspend fun setSyncEnabled(value: Boolean) {}
                override suspend fun setGpsCorrect(value: Boolean) {}
                override suspend fun setCurrentBikeId(bikeId: String?) {}
                override suspend fun setUnits(units: String) {}
                override suspend fun setTheme(theme: String) {}
                override suspend fun setAccent(accent: String) {}
                override suspend fun setLang(lang: String) {}
                override suspend fun setAutoPause(value: Boolean) {}
                override suspend fun setKeepScreenOn(value: Boolean) {}
                override suspend fun setAndroidAutoEnabled(value: Boolean) {}
                override suspend fun setBcName(name: String) {}
                override suspend fun setBcPhone(phone: String) {}
                override suspend fun setBcOrigin(origin: String) {}
                override suspend fun setBcSocial(social: String) {}
                override suspend fun setDebugLoggingEnabled(value: Boolean) {}
                override suspend fun setServerAddress(address: String) {}
                override suspend fun setBatteryPromptDismissed(value: Boolean) {}
                override suspend fun setCoordFormat(format: String) {}
            },
            riderRepository = FakeRiderRepository(),
        )
    }
}
