package com.mototracker.ui.permissions

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PermissionGate.resolve].
 *
 * Verifies the pure state→decision contract: Granted when all present, NeedsRequest with
 * exactly the missing subset otherwise.  No Android framework dependency needed.
 */
class PermissionGateTest {

    @Test
    fun `returns Granted when all required permissions are in granted set`() {
        val required = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val granted = setOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )

        val result = PermissionGate.resolve(required, granted)

        assertTrue("Expected Granted", result is PermissionGateResult.Granted)
    }

    @Test
    fun `returns Granted when required list is empty`() {
        val result = PermissionGate.resolve(emptyList(), emptySet())
        assertTrue("Expected Granted for empty required", result is PermissionGateResult.Granted)
    }

    @Test
    fun `returns Granted when required is empty but granted set is non-empty`() {
        val result = PermissionGate.resolve(
            emptyList(),
            setOf(Manifest.permission.ACCESS_FINE_LOCATION),
        )
        assertTrue(result is PermissionGateResult.Granted)
    }

    @Test
    fun `returns NeedsRequest with all required when granted set is empty`() {
        val required = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )

        val result = PermissionGate.resolve(required, emptySet())

        assertTrue("Expected NeedsRequest", result is PermissionGateResult.NeedsRequest)
        val missing = (result as PermissionGateResult.NeedsRequest).missing
        assertEquals(required, missing)
    }

    @Test
    fun `returns NeedsRequest containing only the missing permissions`() {
        val required = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val granted = setOf(Manifest.permission.ACCESS_FINE_LOCATION)

        val result = PermissionGate.resolve(required, granted)

        assertTrue(result is PermissionGateResult.NeedsRequest)
        val missing = (result as PermissionGateResult.NeedsRequest).missing
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), missing)
        assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in missing)
    }

    @Test
    fun `granted set is a superset — returns Granted`() {
        val required = listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val granted = setOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN, // extra — not required
        )

        val result = PermissionGate.resolve(required, granted)

        assertTrue("Expected Granted when granted is a superset", result is PermissionGateResult.Granted)
    }

    @Test
    fun `NeedsRequest missing list preserves order of required list`() {
        val required = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        val granted = setOf(Manifest.permission.BLUETOOTH_ADVERTISE)

        val result = PermissionGate.resolve(required, granted) as PermissionGateResult.NeedsRequest

        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            ),
            result.missing,
        )
    }

    @Test
    fun `single missing permission returns NeedsRequest with that one entry`() {
        val required = listOf(Manifest.permission.ACCESS_FINE_LOCATION)

        val result = PermissionGate.resolve(required, emptySet())

        assertTrue(result is PermissionGateResult.NeedsRequest)
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            (result as PermissionGateResult.NeedsRequest).missing,
        )
    }
}
