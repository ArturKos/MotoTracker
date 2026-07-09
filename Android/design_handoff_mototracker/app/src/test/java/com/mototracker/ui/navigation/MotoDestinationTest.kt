package com.mototracker.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotoDestinationTest {

    // ── bottomNavDestinations ────────────────────────────────────────────────

    @Test
    fun `bottomNavDestinations has exactly 5 items`() {
        assertEquals(5, bottomNavDestinations.size)
    }

    @Test
    fun `bottomNavDestinations order is Record Routes Riders Stats Settings`() {
        assertEquals(MotoDestination.RECORD,   bottomNavDestinations[0])
        assertEquals(MotoDestination.ROUTES,   bottomNavDestinations[1])
        assertEquals(MotoDestination.RIDERS,   bottomNavDestinations[2])
        assertEquals(MotoDestination.STATS,    bottomNavDestinations[3])
        assertEquals(MotoDestination.SETTINGS, bottomNavDestinations[4])
    }

    @Test
    fun `bottomNavDestinations does not contain LOGIN or ROUTE_DETAIL`() {
        assertFalse(MotoDestination.LOGIN in bottomNavDestinations)
        assertFalse(MotoDestination.ROUTE_DETAIL in bottomNavDestinations)
    }

    // ── showBottomBar ────────────────────────────────────────────────────────

    @Test
    fun `showBottomBar is true for all 5 main tab destinations`() {
        bottomNavDestinations.forEach { dest ->
            assertTrue("Expected showBottomBar=true for $dest", showBottomBar(dest))
        }
    }

    @Test
    fun `showBottomBar is false for LOGIN`() {
        assertFalse(showBottomBar(MotoDestination.LOGIN))
    }

    @Test
    fun `showBottomBar is false for ROUTE_DETAIL`() {
        assertFalse(showBottomBar(MotoDestination.ROUTE_DETAIL))
    }

    // ── showTopBar ───────────────────────────────────────────────────────────

    @Test
    fun `showTopBar is false for LOGIN`() {
        assertFalse(showTopBar(MotoDestination.LOGIN))
    }

    @Test
    fun `showTopBar is true for all non-LOGIN destinations`() {
        val nonLogin = listOf(
            MotoDestination.RECORD,
            MotoDestination.ROUTES,
            MotoDestination.RIDERS,
            MotoDestination.STATS,
            MotoDestination.SETTINGS,
            MotoDestination.ROUTE_DETAIL,
        )
        nonLogin.forEach { dest ->
            assertTrue("Expected showTopBar=true for $dest", showTopBar(dest))
        }
    }

    // ── showBackArrow ────────────────────────────────────────────────────────

    @Test
    fun `showBackArrow is true only for ROUTE_DETAIL`() {
        assertTrue(showBackArrow(MotoDestination.ROUTE_DETAIL))
    }

    @Test
    fun `showBackArrow is false for all other destinations`() {
        val others = listOf(
            MotoDestination.LOGIN,
            MotoDestination.RECORD,
            MotoDestination.ROUTES,
            MotoDestination.RIDERS,
            MotoDestination.STATS,
            MotoDestination.SETTINGS,
        )
        others.forEach { dest ->
            assertFalse("Expected showBackArrow=false for $dest", showBackArrow(dest))
        }
    }

    // ── fromRoute ────────────────────────────────────────────────────────────

    @Test
    fun `fromRoute resolves known routes`() {
        assertEquals(MotoDestination.LOGIN,        MotoDestination.fromRoute("login"))
        assertEquals(MotoDestination.RECORD,       MotoDestination.fromRoute("record"))
        assertEquals(MotoDestination.ROUTES,       MotoDestination.fromRoute("routes"))
        assertEquals(MotoDestination.RIDERS,       MotoDestination.fromRoute("riders"))
        assertEquals(MotoDestination.STATS,        MotoDestination.fromRoute("stats"))
        assertEquals(MotoDestination.SETTINGS,     MotoDestination.fromRoute("settings"))
        assertEquals(MotoDestination.ROUTE_DETAIL, MotoDestination.fromRoute("route_detail"))
    }

    @Test
    fun `fromRoute falls back to RECORD for unknown route`() {
        assertEquals(MotoDestination.RECORD, MotoDestination.fromRoute("unknown"))
        assertEquals(MotoDestination.RECORD, MotoDestination.fromRoute(null))
    }
}
