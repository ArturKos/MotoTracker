package com.mototracker.ui.permissions

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PermissionRequirements.permissionsFor].
 *
 * sdkInt is injected so tests run on the JVM without a device or Robolectric.
 * Covers the documented SDK thresholds (26/30/31/33) for each [AppFeaturePermission].
 */
class PermissionRequirementsTest {

    // ── LOCATION ─────────────────────────────────────────────────────────────

    @Test
    fun `LOCATION always returns ACCESS_FINE_LOCATION on SDK 26`() {
        val perms = PermissionRequirements.permissionsFor(AppFeaturePermission.LOCATION, sdkInt = 26)
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), perms)
    }

    @Test
    fun `LOCATION always returns ACCESS_FINE_LOCATION on SDK 33`() {
        val perms = PermissionRequirements.permissionsFor(AppFeaturePermission.LOCATION, sdkInt = 33)
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), perms)
    }

    // ── NOTIFICATIONS ────────────────────────────────────────────────────────

    @Test
    fun `NOTIFICATIONS returns empty list on SDK 26`() {
        val perms = PermissionRequirements.permissionsFor(AppFeaturePermission.NOTIFICATIONS, sdkInt = 26)
        assertTrue("Expected empty list for SDK 26", perms.isEmpty())
    }

    @Test
    fun `NOTIFICATIONS returns empty list on SDK 30`() {
        val perms = PermissionRequirements.permissionsFor(AppFeaturePermission.NOTIFICATIONS, sdkInt = 30)
        assertTrue("Expected empty list for SDK 30", perms.isEmpty())
    }

    @Test
    fun `NOTIFICATIONS returns empty list on SDK 32`() {
        val perms = PermissionRequirements.permissionsFor(AppFeaturePermission.NOTIFICATIONS, sdkInt = 32)
        assertTrue("Expected empty list for SDK 32", perms.isEmpty())
    }

    @Test
    fun `NOTIFICATIONS returns POST_NOTIFICATIONS on SDK 33`() {
        val perms = PermissionRequirements.permissionsFor(AppFeaturePermission.NOTIFICATIONS, sdkInt = 33)
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), perms)
    }

    @Test
    fun `NOTIFICATIONS returns POST_NOTIFICATIONS on SDK 34`() {
        val perms = PermissionRequirements.permissionsFor(AppFeaturePermission.NOTIFICATIONS, sdkInt = 34)
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), perms)
    }

    // ── BLUETOOTH_WAVES (legacy: SDK < 31) ───────────────────────────────────

    @Test
    fun `BLUETOOTH_WAVES returns legacy perms on SDK 26`() {
        val perms = PermissionRequirements.permissionsFor(AppFeaturePermission.BLUETOOTH_WAVES, sdkInt = 26)
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            perms,
        )
    }

    @Test
    fun `BLUETOOTH_WAVES returns legacy perms on SDK 30`() {
        val perms = PermissionRequirements.permissionsFor(AppFeaturePermission.BLUETOOTH_WAVES, sdkInt = 30)
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            perms,
        )
    }

    // ── BLUETOOTH_WAVES (modern: SDK >= 31) ──────────────────────────────────

    @Test
    fun `BLUETOOTH_WAVES returns modern perms on SDK 31`() {
        val perms = PermissionRequirements.permissionsFor(AppFeaturePermission.BLUETOOTH_WAVES, sdkInt = 31)
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            ),
            perms,
        )
    }

    @Test
    fun `BLUETOOTH_WAVES returns modern perms on SDK 33`() {
        val perms = PermissionRequirements.permissionsFor(AppFeaturePermission.BLUETOOTH_WAVES, sdkInt = 33)
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            ),
            perms,
        )
    }

    @Test
    fun `BLUETOOTH_WAVES does NOT include legacy perms on SDK 31`() {
        val perms = PermissionRequirements.permissionsFor(AppFeaturePermission.BLUETOOTH_WAVES, sdkInt = 31)
        assertTrue(Manifest.permission.BLUETOOTH !in perms)
        assertTrue(Manifest.permission.BLUETOOTH_ADMIN !in perms)
    }

    @Test
    fun `BLUETOOTH_WAVES does NOT include modern perms on SDK 30`() {
        val perms = PermissionRequirements.permissionsFor(AppFeaturePermission.BLUETOOTH_WAVES, sdkInt = 30)
        assertTrue(Manifest.permission.BLUETOOTH_SCAN !in perms)
        assertTrue(Manifest.permission.BLUETOOTH_ADVERTISE !in perms)
        assertTrue(Manifest.permission.BLUETOOTH_CONNECT !in perms)
    }
}
