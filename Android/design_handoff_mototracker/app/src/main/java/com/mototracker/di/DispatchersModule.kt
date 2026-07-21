package com.mototracker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * Qualifier for the [Dispatchers.IO] dispatcher, injected to keep collaborators testable.
 *
 * Classes that require IO-bound work should accept a [CoroutineDispatcher] annotated with
 * [IoDispatcher] rather than referencing [Dispatchers.IO] directly.  Test suites replace it
 * with [kotlinx.coroutines.test.UnconfinedTestDispatcher] so there is no thread-switch overhead
 * and coroutine ordering is deterministic.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Hilt module that provides dispatcher bindings for the app.
 *
 * Installed in [SingletonComponent] so a single [CoroutineDispatcher] instance is shared
 * across the process lifetime.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    /**
     * Provides [Dispatchers.IO] bound to the [IoDispatcher] qualifier.
     *
     * Consumers annotate their constructor parameter with [@IoDispatcher][IoDispatcher] to receive
     * this dispatcher; test fakes pass [kotlinx.coroutines.test.UnconfinedTestDispatcher] instead.
     */
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
