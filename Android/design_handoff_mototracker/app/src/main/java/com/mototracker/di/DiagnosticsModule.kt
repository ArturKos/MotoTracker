package com.mototracker.di

import com.mototracker.data.diagnostics.FileRideDebugLogger
import com.mototracker.data.diagnostics.RideDebugLogger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that wires the [RideDebugLogger] interface to its singleton implementation.
 *
 * [FileRideDebugLogger] is constructed via its `@Inject` secondary constructor;
 * the [RideDebugLogger] binding lets call-sites (and future [RecordingViewModel] in B10)
 * depend on the interface without coupling to the file-writing implementation.
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
}
