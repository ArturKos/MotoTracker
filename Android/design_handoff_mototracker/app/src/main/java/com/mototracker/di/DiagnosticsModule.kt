package com.mototracker.di

import com.mototracker.data.diagnostics.FileRideDebugLogger
import com.mototracker.data.diagnostics.FileRideLogStore
import com.mototracker.data.diagnostics.RideDebugLogger
import com.mototracker.data.diagnostics.RideLogStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that wires diagnostics interfaces to their singleton implementations.
 *
 * [FileRideDebugLogger] and [FileRideLogStore] are constructed via their `@Inject`
 * secondary constructors; the interface bindings let call-sites depend on the
 * abstraction without coupling to the file-writing implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {

    /**
     * Binds [FileRideDebugLogger] as the singleton [RideDebugLogger].
     *
     * @param impl Concrete implementation provided by Hilt via its `@Inject` constructor.
     */
    @Binds
    @Singleton
    abstract fun bindRideDebugLogger(impl: FileRideDebugLogger): RideDebugLogger

    /**
     * Binds [FileRideLogStore] as the singleton [RideLogStore].
     *
     * @param impl Concrete implementation provided by Hilt via its `@Inject` constructor.
     */
    @Binds
    @Singleton
    abstract fun bindRideLogStore(impl: FileRideLogStore): RideLogStore
}
