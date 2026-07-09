package com.mototracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.mototracker.data.local.dao.BikeDao
import com.mototracker.data.local.dao.GroupDao
import com.mototracker.data.local.dao.RouteDao
import com.mototracker.data.local.dao.SyncQueueDao
import com.mototracker.data.local.dao.WaveDao
import com.mototracker.data.local.entity.BikeEntity
import com.mototracker.data.local.entity.GroupMemberEntity
import com.mototracker.data.local.entity.RouteEntity
import com.mototracker.data.local.entity.SyncQueueEntity
import com.mototracker.data.local.entity.WaveEntity

/**
 * Room database for MotoTracker.
 *
 * Single source of truth for all locally persisted ride data. Built by
 * [com.mototracker.di.DatabaseModule] as a singleton.
 *
 * Schema is exported to `app/schemas/` for version-controlled migration auditing.
 * When bumping [version], add a [Migration] to [MIGRATIONS] — never use
 * `fallbackToDestructiveMigration` in production builds.
 */
@Database(
    entities = [
        BikeEntity::class,
        RouteEntity::class,
        GroupMemberEntity::class,
        WaveEntity::class,
        SyncQueueEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MotoDatabase : RoomDatabase() {

    abstract fun bikeDao(): BikeDao
    abstract fun routeDao(): RouteDao
    abstract fun groupDao(): GroupDao
    abstract fun waveDao(): WaveDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        /** File name used by Room when building the on-device database. */
        const val DATABASE_NAME = "moto_tracker.db"

        /**
         * Sequential schema migrations wired via [androidx.room.RoomDatabase.Builder.addMigrations].
         *
         * Add `Migration(fromVersion, toVersion) { db -> db.execSQL("...") }` here
         * whenever the schema changes. Keep version numbers contiguous.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
