package com.mototracker.data.battery

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Factory for battery-optimization and OEM protected-apps intents.
 *
 * Firing intents requires an [android.app.Activity] context; this object only builds and
 * resolves them — actual launch is the caller's responsibility (🔬 on-device concern).
 */
object BatteryOptimizationIntents {

    /**
     * Returns an [Intent] for the standard battery-optimization exemption dialog.
     *
     * On API ≥ 23, this resolves to [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS]
     * with the package URI, which shows the system "Allow always-on background activity?" dialog.
     *
     * @param packageName The app's package name.
     */
    fun requestIgnoreIntent(packageName: String): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }

    /**
     * Resolves the first available OEM-specific protected-apps / auto-launch intent
     * for [packageName], falling back to [Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS].
     *
     * Covers Huawei EMUI (protected apps) and Xiaomi MIUI (auto-start) panels.
     * Never throws — each candidate is validated with [Context.resolveActivity]; unresolvable
     * candidates are skipped silently.
     *
     * @param context     Any context used only for activity resolution.
     * @param packageName The app's package name.
     * @return The first resolvable OEM intent, or the generic battery-settings fallback.
     */
    fun oemAutoLaunchIntent(context: Context, packageName: String): Intent {
        val candidates = listOf(
            // Huawei EMUI — Protected apps
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                )
            ),
            // Huawei EMUI (older) — Protected apps
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity",
                )
            ),
            // Xiaomi MIUI — Auto-start
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                )
            ),
        )
        for (candidate in candidates) {
            try {
                if (context.packageManager.resolveActivity(candidate, 0) != null) {
                    return candidate
                }
            } catch (_: Exception) {
                // resolveActivity may throw on restricted environments; skip candidate
            }
        }
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
}
