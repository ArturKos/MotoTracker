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

    /**
     * Reading the phone's contacts — required to populate SMS location-sharing recipients (Y1).
     * Requires [android.Manifest.permission.READ_CONTACTS] on all supported SDK versions.
     */
    CONTACTS,

    /**
     * Sending SMS messages — required for periodic GPS location SMS during a ride (Y2).
     * Requires [android.Manifest.permission.SEND_SMS] on all supported SDK versions.
     *
     * Requested at the recording entry point only when SMS sharing is enabled in settings.
     * Denial is non-blocking: recording proceeds normally; the SMS feature stays silent.
     */
    SEND_SMS,
}
