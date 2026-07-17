package com.mototracker.data.battery

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.mototracker.domain.battery.BatteryOptimizationChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of [BatteryOptimizationChecker] backed by [PowerManager].
 *
 * Returns `true` unconditionally on API < 23 where [PowerManager.isIgnoringBatteryOptimizations]
 * is not available, so callers treat old devices as already exempt.
 *
 * @param context Application context used to retrieve [PowerManager].
 */
@Singleton
class AndroidBatteryOptimizationChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : BatteryOptimizationChecker {

    override fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
