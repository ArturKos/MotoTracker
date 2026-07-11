package com.mototracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mototracker.data.local.dao.BikeDao
import com.mototracker.data.local.dao.CorrectionQueueDao
import com.mototracker.data.local.dao.GroupDao
import com.mototracker.data.local.dao.RouteDao
import com.mototracker.data.local.dao.SyncQueueDao
import com.mototracker.data.local.dao.WaveDao
import com.mototracker.data.local.entity.BikeEntity
import com.mototracker.data.local.entity.CorrectionQueueEntity
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
        CorrectionQueueEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MotoDatabase : RoomDatabase() {

    abstract fun bikeDao(): BikeDao
    abstract fun routeDao(): RouteDao
    abstract fun groupDao(): GroupDao
    abstract fun waveDao(): WaveDao
    abstract fun syncQueueDao(): SyncQueueDao
    /** Provides the DAO for the OSRM GPS-correction queue. */
    abstract fun correctionQueueDao(): CorrectionQueueDao

    companion object {
        /** File name used by Room when building the on-device database. */
        const val DATABASE_NAME = "moto_tracker.db"

        /**
         * Sequential schema migrations wired via [androidx.room.RoomDatabase.Builder.addMigrations].
         *
         * Add `Migration(fromVersion, toVersion) { db -> db.execSQL("...") }` here
         * whenever the schema changes. Keep version numbers contiguous.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}

/**
 * Schema migration from version 1 to 2.
 *
 * Adds three correction columns to the `routes` table and creates the new
 * `correction_queue` table (plus its routeId index) for the A10 GPS-correction
 * pipeline.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE routes ADD COLUMN correctedPathJson TEXT")
        db.execSQL("ALTER TABLE routes ADD COLUMN correctionStatus TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE routes ADD COLUMN confidence REAL")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS correction_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                routeId TEXT NOT NULL,
                state TEXT NOT NULL,
                attemptCount INTEGER NOT NULL,
                lastAttemptEpochMs INTEGER,
                nextRetryEpochMs INTEGER,
                lastError TEXT,
                FOREIGN KEY (routeId) REFERENCES routes(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_correction_queue_routeId ON correction_queue (routeId)",
        )
    }
}
