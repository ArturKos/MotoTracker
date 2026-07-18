package com.mototracker.di

import com.mototracker.data.repository.FeedRepository
import com.mototracker.data.repository.FeedRepositoryImpl
import com.mototracker.data.repository.GroupRepository
import com.mototracker.data.repository.GroupRepositoryImpl
import com.mototracker.data.repository.RiderRepository
import com.mototracker.data.repository.RiderRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module wiring Riders-screen repository interfaces to their implementations.
 *
 * [WaveRepository] binding stays in [RecordingModule] because it predates B5.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RidersModule {

    /** Binds [GroupRepositoryImpl] as the [GroupRepository] singleton. */
    @Binds
    @Singleton
    abstract fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository

    /** Binds [FeedRepositoryImpl] as the [FeedRepository] singleton. */
    @Binds
    @Singleton
    abstract fun bindFeedRepository(impl: FeedRepositoryImpl): FeedRepository

    /** Binds [RiderRepositoryImpl] as the [RiderRepository] singleton (X2). */
    @Binds
    @Singleton
    abstract fun bindRiderRepository(impl: RiderRepositoryImpl): RiderRepository
}
