package com.mototracker.di

import com.mototracker.core.resource.ContextStringResolver
import com.mototracker.core.resource.StringResolver
import com.mototracker.data.bluetooth.AndroidBleWaveSource
import com.mototracker.data.bluetooth.BleWaveSource
import com.mototracker.data.location.AndroidReverseGeocoder
import com.mototracker.data.location.FusedLocationClientImpl
import com.mototracker.data.location.LocationClient
import com.mototracker.data.location.ReverseGeocoder
import com.mototracker.data.location.RideLocationCollector
import com.mototracker.data.recording.ChannelResumeRouteBus
import com.mototracker.data.recording.DataStoreRecordingSessionStore
import com.mototracker.data.recording.RecordingSessionStore
import com.mototracker.data.recording.ResumeRouteBus
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.BikeRepositoryImpl
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.RouteRepositoryImpl
import com.mototracker.data.repository.WaveRepository
import com.mototracker.data.repository.WaveRepositoryImpl
import com.mototracker.data.sensor.HeadingSensorSource
import com.mototracker.data.sensor.LeanSensorSource
import com.mototracker.data.sensor.SensorManagerHeadingSource
import com.mototracker.data.sensor.SensorManagerLeanSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Hilt module wiring recording-layer interfaces to their concrete implementations.
 *
 * All bindings are singletons so the same [LocationClient] / [LeanSensorSource] /
 * [RouteRepository] / [BikeRepository] / [WaveRepository] instance is shared between
 * ViewModels and any other callsite.
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

    /** Binds [SensorManagerHeadingSource] as the [HeadingSensorSource] singleton. */
    @Binds
    @Singleton
    abstract fun bindHeadingSensorSource(impl: SensorManagerHeadingSource): HeadingSensorSource

    /** Binds [RouteRepositoryImpl] as the [RouteRepository] singleton. */
    @Binds
    @Singleton
    abstract fun bindRouteRepository(impl: RouteRepositoryImpl): RouteRepository

    /** Binds [BikeRepositoryImpl] as the [BikeRepository] singleton. */
    @Binds
    @Singleton
    abstract fun bindBikeRepository(impl: BikeRepositoryImpl): BikeRepository

    /** Binds [WaveRepositoryImpl] as the [WaveRepository] singleton. */
    @Binds
    @Singleton
    abstract fun bindWaveRepository(impl: WaveRepositoryImpl): WaveRepository

    /** Binds [AndroidReverseGeocoder] as the [ReverseGeocoder] singleton. */
    @Binds
    @Singleton
    abstract fun bindReverseGeocoder(impl: AndroidReverseGeocoder): ReverseGeocoder

    /** Binds [ContextStringResolver] as the [StringResolver] singleton. */
    @Binds
    @Singleton
    abstract fun bindStringResolver(impl: ContextStringResolver): StringResolver

    /** Binds [DataStoreRecordingSessionStore] as the [RecordingSessionStore] singleton (B20). */
    @Binds
    @Singleton
    abstract fun bindRecordingSessionStore(impl: DataStoreRecordingSessionStore): RecordingSessionStore

    /** Binds [AndroidBleWaveSource] as the [BleWaveSource] singleton (B21). */
    @Binds
    @Singleton
    abstract fun bindBleWaveSource(impl: AndroidBleWaveSource): BleWaveSource

    /** Binds [ChannelResumeRouteBus] as the [ResumeRouteBus] singleton (J5). */
    @Binds
    @Singleton
    abstract fun bindResumeRouteBus(impl: ChannelResumeRouteBus): ResumeRouteBus

    companion object {

        /**
         * Provides [RideLocationCollector] as a process-lived singleton.
         *
         * The scope is backed by [SupervisorJob] + [Dispatchers.Default] so GPS acquisition
         * outlives any individual ViewModel or Activity and survives screen-off / Doze when
         * the service holds a wake lock.
         */
        @Provides
        @Singleton
        fun provideRideLocationCollector(
            locationClient: LocationClient,
        ): RideLocationCollector = RideLocationCollector(
            locationClient = locationClient,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}
