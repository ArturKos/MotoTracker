package com.mototracker.di

import com.mototracker.data.repository.RefuelRepository
import com.mototracker.data.repository.RefuelRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding [RefuelRepositoryImpl] as the [RefuelRepository] singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RefuelModule {

    /** Binds [RefuelRepositoryImpl] as the [RefuelRepository] singleton (G5). */
    @Binds
    @Singleton
    abstract fun bindRefuelRepository(impl: RefuelRepositoryImpl): RefuelRepository
}
