package com.mototracker.di

import com.mototracker.data.repository.FuelAdjustmentRepository
import com.mototracker.data.repository.FuelAdjustmentRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding [FuelAdjustmentRepositoryImpl] as the [FuelAdjustmentRepository] singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FuelAdjustmentModule {

    /** Binds [FuelAdjustmentRepositoryImpl] as the [FuelAdjustmentRepository] singleton (R1). */
    @Binds
    @Singleton
    abstract fun bindFuelAdjustmentRepository(impl: FuelAdjustmentRepositoryImpl): FuelAdjustmentRepository
}
