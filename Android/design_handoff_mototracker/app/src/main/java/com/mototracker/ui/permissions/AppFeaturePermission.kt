package com.mototracker.ui.permissions

/**
 * Features that require runtime permissions in MotoTracker.
 *
 * Each variant maps to a distinct set of Manifest permission strings returned by
 * [PermissionRequirements.permissionsFor].  Use these values as input to
 * [rememberFeaturePermission] to obtain a Compose-aware [FeaturePermissionHandle].
 */
enum class AppFeaturePermission {
    /** GPS-based ride recording — requires [android.Manifest.permission.ACCESS_FINE_LOCATION]. */
    LOCATION,

    /**
     * Foreground service notifications — requires [android.Manifest.permission.POST_NOTIFICATIONS]
     * on API 33 (TIRAMISU)+; resolves to an empty list on older SDKs.
     */
    NOTIFICATIONS,

    /**
     * Bluetooth proximity waves (advertise + scan nearby riders) — requires the modern
     * `BLUETOOTH_SCAN / ADVERTISE / CONNECT` triple on API 31 (S)+, or the legacy
     * `BLUETOOTH / BLUETOOTH_ADMIN + ACCESS_FINE_LOCATION` pair on older SDKs.
     */
    BLUETOOTH_WAVES,
}
