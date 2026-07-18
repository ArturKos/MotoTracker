package com.mototracker.ui.permissions

import android.Manifest

/**
 * Pure helpers mapping [AppFeaturePermission] to the Manifest permission strings required on a
 * given SDK version.  All functions are stateless and have no Android UI dependency, making them
 * straightforward to unit-test with any injected SDK level.
 */
object PermissionRequirements {

    /**
     * Returns the [android.Manifest.permission] strings required for [feature] on a device
     * running [sdkInt].
     *
     * [sdkInt] is injected rather than read from [android.os.Build.VERSION] so the function
     * can be exercised with arbitrary API levels without a device or Robolectric.
     *
     * @param feature  The app feature whose permission requirements are requested.
     * @param sdkInt   Device API level (pass [android.os.Build.VERSION.SDK_INT] in production).
     * @return Possibly-empty list of Manifest permission strings; never null.
     */
    fun permissionsFor(feature: AppFeaturePermission, sdkInt: Int): List<String> = when (feature) {
        AppFeaturePermission.LOCATION ->
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)

        AppFeaturePermission.NOTIFICATIONS ->
            if (sdkInt >= 33 /* TIRAMISU */) listOf(Manifest.permission.POST_NOTIFICATIONS)
            else emptyList()

        AppFeaturePermission.BLUETOOTH_WAVES ->
            if (sdkInt >= 31 /* S */) listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
            else listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )

        AppFeaturePermission.CONTACTS ->
            listOf(Manifest.permission.READ_CONTACTS)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permission gate — pure result type and resolver
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Outcome of a permission gate check performed by [PermissionGate.resolve].
 */
sealed class PermissionGateResult {
    /** All required permissions are already granted — the gated action can proceed immediately. */
    data object Granted : PermissionGateResult()

    /**
     * At least one required permission is missing; the system dialog must be shown before the
     * gated action can proceed.
     *
     * @property missing Subset of the required list that is not present in the granted set.
     */
    data class NeedsRequest(val missing: List<String>) : PermissionGateResult()
}

/**
 * Pure resolver that decides whether an action can proceed or needs a permission request.
 */
object PermissionGate {

    /**
     * Compares [required] against [granted] and returns:
     * - [PermissionGateResult.Granted] if every string in [required] is present in [granted].
     * - [PermissionGateResult.NeedsRequest] with the subset of [required] not in [granted].
     *
     * @param required Manifest permission strings the feature needs to operate.
     * @param granted  Manifest permission strings currently held by the app process.
     */
    fun resolve(required: List<String>, granted: Set<String>): PermissionGateResult {
        val missing = required.filter { it !in granted }
        return if (missing.isEmpty()) PermissionGateResult.Granted
        else PermissionGateResult.NeedsRequest(missing)
    }
}
