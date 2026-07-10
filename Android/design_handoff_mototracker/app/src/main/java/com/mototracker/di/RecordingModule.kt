package com.mototracker.di

import com.mototracker.data.location.FusedLocationClientImpl
import com.mototracker.data.location.LocationClient
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.BikeRepositoryImpl
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.RouteRepositoryImpl
import com.mototracker.data.sensor.LeanSensorSource
import com.mototracker.data.sensor.SensorManagerLeanSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module wiring recording-layer interfaces to their concrete implementations.
 *
 * All bindings are singletons so the same [LocationClient] / [LeanSensorSource] /
 * [RouteRepository] / [BikeRepository] instance is shared between ViewModels and
 * any other callsite.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecordingModule {

    /** Binds [FusedLocationClientImpl] as the [LocationClient] singleton. */
    @Binds
    @Singleton
    abstract fun bindLocationClient(impl: FusedLocationClientImpl): LocationClient

    /** Binds [SensorManagerLeanSource] as the [LeanSensorSource] singleton. */
    @Binds
    @Singleton
    abstract fun bindLeanSensorSource(impl: SensorManagerLeanSource): LeanSensorSource

    /** Binds [RouteRepositoryImpl] as the [RouteRepository] singleton. */
    @Binds
    @Singleton
    abstract fun bindRouteRepository(impl: RouteRepositoryImpl): RouteRepository

    /** Binds [BikeRepositoryImpl] as the [BikeRepository] singleton. */
    @Binds
    @Singleton
    abstract fun bindBikeRepository(impl: BikeRepositoryImpl): BikeRepository
}
