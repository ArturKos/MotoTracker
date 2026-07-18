package com.mototracker.domain.recording

import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteResumeSeedTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeRoute(
        id: String = "route-1",
        name: String = "Morning Ride",
        km: Double = 123.4,
        durSec: Long = 7200L,
        max: Double = 150.0,
        lean: Double = 32.0,
        maxLeanLeftDeg: Double = 28.0,
        maxLeanRightDeg: Double = 32.0,
        elev: Double = 450.0,
        pathJson: String? = null,
        speedJson: String? = null,
        elevProfileJson: String? = null,
        dateEpochMs: Long = 1_700_000_000_000L,
    ) = Route(
        id = id, name = name, dateEpochMs = dateEpochMs,
        bikeId = null, km = km, durSec = durSec, avg = km / (durSec / 3600.0),
        max = max, lean = lean, elev = elev, fuel = 6.5, synced = true,
        wxJson = null, pathJson = pathJson, speedJson = speedJson,
        elevProfileJson = elevProfileJson, notes = null,
        correctionStatus = CorrectionStatus.NONE,
        maxLeanLeftDeg = maxLeanLeftDeg, maxLeanRightDeg = maxLeanRightDeg,
    )

    // ── scalar metrics ────────────────────────────────────────────────────────

    @Test
    fun `fromRoute maps distance from km`() {
        val route = makeRoute(km = 99.9)
        val state = RouteResumeSeed.fromRoute(route)
        assertEquals(99.9, state.distanceKm, 0.001)
    }

    @Test
    fun `fromRoute maps duration from durSec`() {
        val route = makeRoute(durSec = 5400L)
        val state = RouteResumeSeed.fromRoute(route)
        assertEquals(5400L, state.durationSec)
    }

    @Test
    fun `fromRoute sets movingSec equal to durSec (best-effort)`() {
        val route = makeRoute(durSec = 3600L)
        val state = RouteResumeSeed.fromRoute(route)
        assertEquals(3600L, state.movingSec)
    }

    @Test
    fun `fromRoute maps maxSpeedKmh from max`() {
        val route = makeRoute(max = 180.0)
        val state = RouteResumeSeed.fromRoute(route)
        assertEquals(180.0, state.maxSpeedKmh, 0.001)
    }

    @Test
    fun `fromRoute maps maxLeanDeg from lean`() {
        val route = makeRoute(lean = 38.0)
        val state = RouteResumeSeed.fromRoute(route)
        assertEquals(38.0, state.maxLeanDeg, 0.001)
    }

    @Test
    fun `fromRoute maps maxLeanLeftDeg and maxLeanRightDeg`() {
        val route = makeRoute(maxLeanLeftDeg = 27.5, maxLeanRightDeg = 34.0)
        val state = RouteResumeSeed.fromRoute(route)
        assertEquals(27.5, state.maxLeanLeftDeg, 0.001)
        assertEquals(34.0, state.maxLeanRightDeg, 0.001)
    }

    @Test
    fun `fromRoute maps elevGainM from elev`() {
        val route = makeRoute(elev = 600.0)
        val state = RouteResumeSeed.fromRoute(route)
        assertEquals(600.0, state.elevGainM, 0.001)
    }

    @Test
    fun `fromRoute resets currentSpeedKmh to zero`() {
        val state = RouteResumeSeed.fromRoute(makeRoute())
        assertEquals(0.0, state.currentSpeedKmh, 0.0)
    }

    @Test
    fun `fromRoute resets headingDeg to zero`() {
        val state = RouteResumeSeed.fromRoute(makeRoute())
        assertEquals(0f, state.headingDeg, 0.0f)
    }

    @Test
    fun `fromRoute sets anchor fields to zero`() {
        val state = RouteResumeSeed.fromRoute(makeRoute())
        assertEquals(0.0, state.anchorKm, 0.0)
        assertEquals(0.0, state.anchorLitres, 0.0)
    }

    @Test
    fun `fromRoute leaves fuel model fields at defaults`() {
        val state = RouteResumeSeed.fromRoute(makeRoute())
        assertEquals(5.0, state.sessionFuelLper100km, 0.001)
        assertNull(state.tankCapacityL)
    }

    // ── path parsing ──────────────────────────────────────────────────────────

    @Test
    fun `fromRoute parses pathJson into pathPoints`() {
        val route = makeRoute(
            pathJson = """[{"lat":52.1,"lng":21.1},{"lat":52.2,"lng":21.2}]""",
        )
        val state = RouteResumeSeed.fromRoute(route)
        assertEquals(2, state.pathPoints.size)
        assertEquals(52.1, state.pathPoints[0].lat, 0.0001)
        assertEquals(21.1, state.pathPoints[0].lng, 0.0001)
        assertEquals(52.2, state.pathPoints[1].lat, 0.0001)
        assertEquals(21.2, state.pathPoints[1].lng, 0.0001)
    }

    @Test
    fun `fromRoute seeds prevLat and prevLng from last path point`() {
        val route = makeRoute(
            pathJson = """[{"lat":50.0,"lng":20.0},{"lat":50.5,"lng":20.5}]""",
        )
        val state = RouteResumeSeed.fromRoute(route)
        assertEquals(50.5, state.prevLat!!, 0.0001)
        assertEquals(20.5, state.prevLng!!, 0.0001)
    }

    @Test
    fun `fromRoute produces null prevLat and prevLng when pathJson is null`() {
        val state = RouteResumeSeed.fromRoute(makeRoute(pathJson = null))
        assertNull(state.prevLat)
        assertNull(state.prevLng)
        assertTrue(state.pathPoints.isEmpty())
    }

    @Test
    fun `fromRoute produces null prevLat and prevLng when pathJson is empty array`() {
        val state = RouteResumeSeed.fromRoute(makeRoute(pathJson = "[]"))
        assertNull(state.prevLat)
        assertNull(state.prevLng)
    }

    // ── speed parsing ─────────────────────────────────────────────────────────

    @Test
    fun `fromRoute parses speedJson into speedOverTime`() {
        val route = makeRoute(
            speedJson = """[{"t":0,"v":0.0},{"t":10,"v":60.5}]""",
        )
        val state = RouteResumeSeed.fromRoute(route)
        assertEquals(2, state.speedOverTime.size)
        assertEquals(0L, state.speedOverTime[0].first)
        assertEquals(0.0, state.speedOverTime[0].second, 0.001)
        assertEquals(10L, state.speedOverTime[1].first)
        assertEquals(60.5, state.speedOverTime[1].second, 0.001)
    }

    @Test
    fun `fromRoute produces empty speedOverTime when speedJson is null`() {
        val state = RouteResumeSeed.fromRoute(makeRoute(speedJson = null))
        assertTrue(state.speedOverTime.isEmpty())
    }

    // ── elevation parsing ─────────────────────────────────────────────────────

    @Test
    fun `fromRoute parses elevProfileJson into elevOverDist`() {
        val route = makeRoute(
            elevProfileJson = """[{"d":0.0,"a":100.0},{"d":5.0,"a":250.0}]""",
        )
        val state = RouteResumeSeed.fromRoute(route)
        assertEquals(2, state.elevOverDist.size)
        assertEquals(0.0, state.elevOverDist[0].first, 0.001)
        assertEquals(100.0, state.elevOverDist[0].second, 0.001)
        assertEquals(5.0, state.elevOverDist[1].first, 0.001)
        assertEquals(250.0, state.elevOverDist[1].second, 0.001)
    }

    @Test
    fun `fromRoute seeds prevAlt and altitudeM from last elevation entry`() {
        val route = makeRoute(
            elevProfileJson = """[{"d":0.0,"a":100.0},{"d":5.0,"a":320.0}]""",
        )
        val state = RouteResumeSeed.fromRoute(route)
        assertEquals(320.0, state.prevAlt!!, 0.001)
        assertEquals(320.0, state.altitudeM, 0.001)
    }

    @Test
    fun `fromRoute produces null prevAlt and zero altitudeM when elevProfileJson is null`() {
        val state = RouteResumeSeed.fromRoute(makeRoute(elevProfileJson = null))
        assertNull(state.prevAlt)
        assertEquals(0.0, state.altitudeM, 0.0)
        assertTrue(state.elevOverDist.isEmpty())
    }

    // ── combined empty-path route ─────────────────────────────────────────────

    @Test
    fun `fromRoute with all JSON null produces empty lists and null prev pointers`() {
        val route = makeRoute(pathJson = null, speedJson = null, elevProfileJson = null)
        val state = RouteResumeSeed.fromRoute(route)

        assertTrue(state.pathPoints.isEmpty())
        assertTrue(state.speedOverTime.isEmpty())
        assertTrue(state.elevOverDist.isEmpty())
        assertNull(state.prevLat)
        assertNull(state.prevLng)
        assertNull(state.prevAlt)
        assertEquals(0.0, state.altitudeM, 0.0)
    }
}
