package com.mototracker.data.model

import com.mototracker.data.local.entity.BikeEntity
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.local.entity.GroupMemberEntity
import com.mototracker.data.local.entity.RouteEntity
import com.mototracker.data.local.entity.SyncQueueEntity
import com.mototracker.data.local.entity.SyncQueueState
import com.mototracker.data.local.entity.WaveEntity
import com.mototracker.data.model.mapper.toDomain
import com.mototracker.data.model.mapper.toEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JUnit tests for entity ↔ domain mapper round-trips.
 *
 * No Android runtime required — all classes are plain Kotlin.
 */
class MapperTest {

    // ── Bike ────────────────────────────────────────────────────────────────

    @Test
    fun `BikeEntity toDomain preserves all fields`() {
        val entity = BikeEntity("b1", "Yamaha MT-07", 2022, "WA 12345", BikeStatus.ACTIVE)
        val domain = entity.toDomain()
        assertEquals(entity.id, domain.id)
        assertEquals(entity.name, domain.name)
        assertEquals(entity.year, domain.year)
        assertEquals(entity.plate, domain.plate)
        assertEquals(entity.status, domain.status)
    }

    @Test
    fun `Bike toEntity is inverse of BikeEntity toDomain`() {
        val entity = BikeEntity("b1", "Yamaha MT-07", 2022, "WA 12345", BikeStatus.SOLD)
        assertEquals(entity, entity.toDomain().toEntity())
    }

    // ── Route ───────────────────────────────────────────────────────────────

    @Test
    fun `RouteEntity toDomain preserves all fields`() {
        val entity = RouteEntity(
            id = "r1", name = "Mountain run", dateEpochMs = 1_700_000_000L,
            bikeId = "b1", km = 234.5, durSec = 9000L, avg = 93.8, max = 155.0,
            lean = 42.0, elev = 1200.0, fuel = 12.3, synced = false,
            wxJson = """{"temp":20}""", speedJson = "[]",
            elevProfileJson = "[]", notes = "Great roads",
            maxLeanLeftDeg = 31.5, maxLeanRightDeg = 42.0,
        )
        val domain = entity.toDomain()
        assertEquals(entity.id, domain.id)
        assertEquals(entity.km, domain.km, 0.001)
        assertEquals(entity.wxJson, domain.wxJson)
        assertEquals(entity.notes, domain.notes)
        assertEquals(entity.maxLeanLeftDeg, domain.maxLeanLeftDeg, 0.001)
        assertEquals(entity.maxLeanRightDeg, domain.maxLeanRightDeg, 0.001)
    }

    @Test
    fun `Route toEntity is inverse of RouteEntity toDomain`() {
        val entity = RouteEntity(
            id = "r1", name = "Route", dateEpochMs = 0L, bikeId = null,
            km = 0.0, durSec = 0L, avg = 0.0, max = 0.0, lean = 0.0,
            elev = 0.0, fuel = 0.0, synced = true,
            wxJson = null, speedJson = null, elevProfileJson = null, notes = null,
            maxLeanLeftDeg = 18.0, maxLeanRightDeg = 22.5,
        )
        assertEquals(entity, entity.toDomain().toEntity())
    }

    @Test
    fun `RouteEntity toDomain defaults maxLeanLeftDeg and maxLeanRightDeg to zero`() {
        val entity = RouteEntity(
            id = "r9", name = "Old Ride", dateEpochMs = 0L, bikeId = null,
            km = 10.0, durSec = 600L, avg = 60.0, max = 80.0, lean = 5.0,
            elev = 0.0, fuel = 0.5, synced = true,
            wxJson = null, speedJson = null, elevProfileJson = null, notes = null,
        )
        val domain = entity.toDomain()
        assertEquals(0.0, domain.maxLeanLeftDeg, 0.0)
        assertEquals(0.0, domain.maxLeanRightDeg, 0.0)
    }

    // ── GroupMember ─────────────────────────────────────────────────────────

    @Test
    fun `GroupMemberEntity round-trip preserves all fields`() {
        val entity = GroupMemberEntity("g1", "Marek", "+48100200300", "Honda CB500")
        assertEquals(entity, entity.toDomain().toEntity())
    }

    // ── Wave ────────────────────────────────────────────────────────────────

    @Test
    fun `WaveEntity round-trip preserves all fields including null routeId`() {
        val entity = WaveEntity("w1", "Jurek", "Kawasaki Z900", "Zakopane", "14:32", routeId = null)
        assertEquals(entity, entity.toDomain().toEntity())
    }

    @Test
    fun `WaveEntity round-trip preserves non-null routeId`() {
        val entity = WaveEntity("w2", "Kasia", "BMW GS1200", "Kraków", "10:00", routeId = "r5")
        assertEquals(entity, entity.toDomain().toEntity())
    }

    // ── SyncItem ────────────────────────────────────────────────────────────

    @Test
    fun `SyncQueueEntity round-trip preserves all fields`() {
        val entity = SyncQueueEntity(
            id = 42L, routeId = "r1", state = SyncQueueState.FAILED,
            attemptCount = 3, lastAttemptEpochMs = 1_700_000_000L,
            nextRetryEpochMs = 1_700_003_600L, lastError = "connection refused",
        )
        assertEquals(entity, entity.toDomain().toEntity())
    }
}
