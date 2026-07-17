package com.mototracker.di

import com.mototracker.data.battery.AndroidBatteryOptimizationChecker
import com.mototracker.domain.battery.BatteryOptimizationChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding [AndroidBatteryOptimizationChecker] as the [BatteryOptimizationChecker] singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BatteryModule {

    /** Binds [AndroidBatteryOptimizationChecker] as the [BatteryOptimizationChecker] singleton. */
    @Binds
    @Singleton
    abstract fun bindBatteryOptimizationChecker(
        impl: AndroidBatteryOptimizationChecker,
    ): BatteryOptimizationChecker
}
