package com.mototracker.di

import android.content.Context
import androidx.room.Room
import com.mototracker.data.local.MotoDatabase
import com.mototracker.data.local.dao.BikeDao
import com.mototracker.data.local.dao.CorrectionQueueDao
import com.mototracker.data.local.dao.GroupDao
import com.mototracker.data.local.dao.RouteDao
import com.mototracker.data.local.dao.SyncQueueDao
import com.mototracker.data.local.dao.WaveDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the [MotoDatabase] and all DAO singletons.
 *
 * The database is built once per app process; DAOs are lightweight facades
 * over the same connection and are therefore also scoped to [SingletonComponent].
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides the singleton [MotoDatabase] instance.
     *
     * Migrations are applied in order from [MotoDatabase.MIGRATIONS]. The database
     * name is [MotoDatabase.DATABASE_NAME].
     *
     * @param context Application context (injected by Hilt).
     */
    @Provides
    @Singleton
    fun provideMotoDatabase(@ApplicationContext context: Context): MotoDatabase =
        Room.databaseBuilder(context, MotoDatabase::class.java, MotoDatabase.DATABASE_NAME)
            .addMigrations(*MotoDatabase.MIGRATIONS)
            .build()

    /** Provides the [BikeDao] from the singleton database. */
    @Provides
    fun provideBikeDao(db: MotoDatabase): BikeDao = db.bikeDao()

    /** Provides the [RouteDao] from the singleton database. */
    @Provides
    fun provideRouteDao(db: MotoDatabase): RouteDao = db.routeDao()

    /** Provides the [GroupDao] from the singleton database. */
    @Provides
    fun provideGroupDao(db: MotoDatabase): GroupDao = db.groupDao()

    /** Provides the [WaveDao] from the singleton database. */
    @Provides
    fun provideWaveDao(db: MotoDatabase): WaveDao = db.waveDao()

    /** Provides the [SyncQueueDao] from the singleton database. */
    @Provides
    fun provideSyncQueueDao(db: MotoDatabase): SyncQueueDao = db.syncQueueDao()

    /** Provides the [CorrectionQueueDao] from the singleton database. */
    @Provides
    fun provideCorrectionQueueDao(db: MotoDatabase): CorrectionQueueDao = db.correctionQueueDao()
}
