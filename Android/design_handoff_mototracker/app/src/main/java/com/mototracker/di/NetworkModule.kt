package com.mototracker.di

import com.mototracker.core.time.SystemTimeProvider
import com.mototracker.core.time.TimeProvider
import com.mototracker.data.network.AndroidNetworkMonitor
import com.mototracker.data.network.GpStrackClient
import com.mototracker.data.network.HttpGpStrackClient
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.repository.SyncRepositoryImpl
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.data.settings.SettingsDataStore
import com.mototracker.data.settings.WritableSettingsSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module wiring network and sync interfaces to their concrete implementations.
 *
 * All bindings are scoped to [SingletonComponent] so exactly one instance exists per process.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    /** Binds [HttpGpStrackClient] as the [GpStrackClient] singleton. */
    @Binds
    @Singleton
    abstract fun bindGpStrackClient(impl: HttpGpStrackClient): GpStrackClient

    /** Binds [AndroidNetworkMonitor] as the [NetworkMonitor] singleton. */
    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: AndroidNetworkMonitor): NetworkMonitor

    /** Binds [SystemTimeProvider] as the [TimeProvider] singleton. */
    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    /** Binds [SyncRepositoryImpl] as the [SyncRepository] singleton. */
    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    /**
     * Exposes [SettingsDataStore] through the [AppSettingsSource] interface so that
     * the sync layer does not depend on DataStore classes directly.
     */
    @Binds
    @Singleton
    abstract fun bindAppSettingsSource(impl: SettingsDataStore): AppSettingsSource

    /**
     * Exposes [SettingsDataStore] through [WritableSettingsSource] so the login layer
     * can persist the server address without depending on DataStore directly.
     */
    @Binds
    @Singleton
    abstract fun bindWritableSettingsSource(impl: SettingsDataStore): WritableSettingsSource
}
