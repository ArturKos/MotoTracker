package com.mototracker.domain.fuel

import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RefuelRepository
import com.mototracker.data.repository.RouteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class FakeBikeRepo : BikeRepository {
    private val _bikes = MutableStateFlow<List<Bike>>(emptyList())
    val savedBikes = mutableListOf<Bike>()

    fun setBikes(vararg bikes: Bike) { _bikes.value = bikes.toList() }
    override fun observeAll(): Flow<List<Bike>> = _bikes
    override suspend fun addBike(bike: Bike) {
        savedBikes += bike
        _bikes.value = _bikes.value.filter { it.id != bike.id } + bike
    }
    override suspend fun deleteAll() { _bikes.value = emptyList() }
}

private class FakeRouteRepo : RouteRepository {
    private val _summaries = MutableStateFlow<List<RouteSummaryModel>>(emptyList())

    fun setSummaries(vararg s: RouteSummaryModel) { _summaries.value = s.toList() }
    override fun observeSummaries(): Flow<List<RouteSummaryModel>> = _summaries
    override suspend fun save(route: com.mototracker.data.model.Route) {}
    override suspend fun getById(id: String) = null
    override fun observeById(id: String): Flow<com.mototracker.data.model.Route?> =
        MutableStateFlow(null)
    override suspend fun clearCorrectedTrace(id: String) {}
    override suspend fun rename(id: String, name: String) {}
    override suspend fun setBike(routeId: String, bikeId: String?) {}
    override suspend fun deleteAll() {}
}

private class FakeRefuelRepo : RefuelRepository {
    private val _allForBike = MutableStateFlow<List<RefuelEvent>>(emptyList())

    fun setRefuelsForBike(vararg events: RefuelEvent) { _allForBike.value = events.toList() }
    override suspend fun addRefuel(routeId: String, epochMs: Long, litres: Double, pricePerL: Double) {}
    override fun observeRefuels(routeId: String): Flow<List<RefuelEvent>> = MutableStateFlow(emptyList())
    override suspend fun deleteRefuel(id: Long) {}
    override fun observeAllForBike(bikeId: String): Flow<List<RefuelEvent>> = _allForBike
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun bike(
    id: String = "b1",
    consumptionLper100km: Double? = null,
    autoUpdate: Boolean = true,
) = Bike(
    id = id,
    name = "Test Bike",
    year = 2020,
    plate = "TS 1",
    status = BikeStatus.ACTIVE,
    consumptionLper100km = consumptionLper100km,
    autoUpdateConsumption = autoUpdate,
)

private fun summary(id: String, bikeId: String?, km: Double, dateMs: Long = 0L) =
    RouteSummaryModel(
        id = id,
        name = id,
        dateEpochMs = dateMs,
        bikeId = bikeId,
        km = km,
        durSec = 3600,
        avg = 60.0,
        max = 100.0,
        lean = 10.0,
        elev = 0.0,
        fuel = 0.0,
        synced = true,
        thumbnailPathD = null,
        correctionStatus = CorrectionStatus.NONE,
        confidence = null,
    )

private fun refuel(id: Long, routeId: String, litres: Double) =
    RefuelEvent(id = id, routeId = routeId, epochMs = 0L, litres = litres, pricePerL = 1.0)

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Unit tests for [AutoUpdateBikeConsumptionUseCase].
 *
 * All tests are pure JUnit — no Android runtime required.
 */
class AutoUpdateBikeConsumptionUseCaseTest {

    private lateinit var bikeRepo: FakeBikeRepo
    private lateinit var routeRepo: FakeRouteRepo
    private lateinit var refuelRepo: FakeRefuelRepo
    private lateinit var useCase: AutoUpdateBikeConsumptionUseCase

    @Before
    fun setUp() {
        bikeRepo = FakeBikeRepo()
        routeRepo = FakeRouteRepo()
        refuelRepo = FakeRefuelRepo()
        useCase = AutoUpdateBikeConsumptionUseCase(bikeRepo, routeRepo, refuelRepo)
    }

    @Test
    fun `flag ON with enough fills — persists computed L-100km`() = runTest {
        // Two routes: 300 km each; refuels at route-1 (15 L) and route-2 (12 L)
        // distance = 300+300 = 600 km; consumed = 15+12=27 L (both fills because first fill sets odometer)
        // Actually: fillsFromLedger puts route-1 fill at odometer 300 and route-2 fill at 600.
        // consumptionLper100km: litres after first = 12, distance = 600-300 = 300 → 4.0 L/100km
        bikeRepo.setBikes(bike(id = "b1", consumptionLper100km = null, autoUpdate = true))
        routeRepo.setSummaries(
            summary("r1", "b1", 300.0, dateMs = 1000L),
            summary("r2", "b1", 300.0, dateMs = 2000L),
        )
        refuelRepo.setRefuelsForBike(
            refuel(1, "r1", 15.0),
            refuel(2, "r2", 12.0),
        )

        useCase.run("b1")

        assertEquals(1, bikeRepo.savedBikes.size)
        assertEquals(4.0, bikeRepo.savedBikes.first().consumptionLper100km!!, 0.001)
    }

    @Test
    fun `flag ON with insufficient refuels — no persist`() = runTest {
        // Only one refuel event → ledger value null → no persist
        bikeRepo.setBikes(bike(id = "b1", consumptionLper100km = 5.0, autoUpdate = true))
        routeRepo.setSummaries(summary("r1", "b1", 200.0))
        refuelRepo.setRefuelsForBike(refuel(1, "r1", 10.0))

        useCase.run("b1")

        assertEquals("no persist when ledger is insufficient", 0, bikeRepo.savedBikes.size)
    }

    @Test
    fun `flag OFF — no-op even with a full ledger`() = runTest {
        bikeRepo.setBikes(bike(id = "b1", consumptionLper100km = 6.0, autoUpdate = false))
        routeRepo.setSummaries(
            summary("r1", "b1", 300.0, dateMs = 1000L),
            summary("r2", "b1", 300.0, dateMs = 2000L),
        )
        refuelRepo.setRefuelsForBike(
            refuel(1, "r1", 15.0),
            refuel(2, "r2", 12.0),
        )

        useCase.run("b1")

        assertEquals("no-op when autoUpdateConsumption is false", 0, bikeRepo.savedBikes.size)
    }

    @Test
    fun `computed value equals existing — no redundant write`() = runTest {
        // 4.0 L/100km computed; bike already has 4.0 stored → skip persist
        bikeRepo.setBikes(bike(id = "b1", consumptionLper100km = 4.0, autoUpdate = true))
        routeRepo.setSummaries(
            summary("r1", "b1", 300.0, dateMs = 1000L),
            summary("r2", "b1", 300.0, dateMs = 2000L),
        )
        refuelRepo.setRefuelsForBike(
            refuel(1, "r1", 15.0),
            refuel(2, "r2", 12.0),
        )

        useCase.run("b1")

        assertEquals("no write when value unchanged", 0, bikeRepo.savedBikes.size)
    }

    @Test
    fun `unknown bike id — no-op`() = runTest {
        bikeRepo.setBikes(bike(id = "b1", autoUpdate = true))

        useCase.run("unknown-id")

        assertEquals(0, bikeRepo.savedBikes.size)
    }

    @Test
    fun `flag stays unchanged after update`() = runTest {
        bikeRepo.setBikes(bike(id = "b1", consumptionLper100km = null, autoUpdate = true))
        routeRepo.setSummaries(
            summary("r1", "b1", 300.0, dateMs = 1000L),
            summary("r2", "b1", 300.0, dateMs = 2000L),
        )
        refuelRepo.setRefuelsForBike(
            refuel(1, "r1", 15.0),
            refuel(2, "r2", 12.0),
        )

        useCase.run("b1")

        assertEquals("autoUpdateConsumption flag preserved", true, bikeRepo.savedBikes.first().autoUpdateConsumption)
    }
}
