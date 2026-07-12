package com.mototracker.di

import com.mototracker.data.repository.BackupRepository
import com.mototracker.data.repository.BackupRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that wires [BackupRepositoryImpl] as the [BackupRepository] singleton.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    /** Binds [BackupRepositoryImpl] as the [BackupRepository] singleton. */
    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository
}
