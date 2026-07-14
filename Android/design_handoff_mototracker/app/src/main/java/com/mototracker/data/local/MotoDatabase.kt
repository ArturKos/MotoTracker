package com.mototracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mototracker.core.format.TraceChunkCodec
import com.mototracker.data.local.dao.BikeDao
import com.mototracker.data.local.dao.CorrectionQueueDao
import com.mototracker.data.local.dao.GroupDao
import com.mototracker.data.local.dao.RefuelEventDao
import com.mototracker.data.local.dao.RouteDao
import com.mototracker.data.local.dao.RouteTraceChunkDao
import com.mototracker.data.local.dao.SyncQueueDao
import com.mototracker.data.local.dao.WaveDao
import com.mototracker.data.local.entity.BikeEntity
import com.mototracker.data.local.entity.CorrectionQueueEntity
import com.mototracker.data.local.entity.GroupMemberEntity
import com.mototracker.data.local.entity.RefuelEventEntity
import com.mototracker.data.local.entity.RouteEntity
import com.mototracker.data.local.entity.RouteTraceChunkEntity
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
 *
 * Version history:
 * - 1 → 2: Added correction columns + correction_queue table (A10 GPS-correction pipeline).
 * - 2 → 3: Moved pathJson/correctedPathJson out-of-row to route_trace_chunk; added thumbnailPathD (D11 CursorWindow fix).
 * - 3 → 4: Added per-bike fuel model columns (tankCapacityL, fuelPricePerL, consumptionLper100km) to bikes;
 *           added per-route fuel price override (fuelPricePerL) to routes (E3).
 * - 4 → 5: Added maxLeanLeftDeg and maxLeanRightDeg (NOT NULL DEFAULT 0) to routes (E7 separate L/R lean tracking).
 * - 5 → 6: Added refuel_event table with per-route refuel ledger (G5).
 */
@Database(
    entities = [
        BikeEntity::class,
        RouteEntity::class,
        RouteTraceChunkEntity::class,
        GroupMemberEntity::class,
        WaveEntity::class,
        SyncQueueEntity::class,
        CorrectionQueueEntity::class,
        RefuelEventEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MotoDatabase : RoomDatabase() {

    abstract fun bikeDao(): BikeDao
    abstract fun routeDao(): RouteDao
    /** Provides the DAO for out-of-row GPS trace chunk storage. */
    abstract fun routeTraceChunkDao(): RouteTraceChunkDao
    abstract fun groupDao(): GroupDao
    abstract fun waveDao(): WaveDao
    abstract fun syncQueueDao(): SyncQueueDao
    /** Provides the DAO for the OSRM GPS-correction queue. */
    abstract fun correctionQueueDao(): CorrectionQueueDao

    /** Provides the DAO for the per-route refuel event ledger (G5). */
    abstract fun refuelEventDao(): RefuelEventDao

    companion object {
        /** File name used by Room when building the on-device database. */
        const val DATABASE_NAME = "moto_tracker.db"

        /**
         * Sequential schema migrations wired via [androidx.room.RoomDatabase.Builder.addMigrations].
         *
         * Add `Migration(fromVersion, toVersion) { db -> db.execSQL("...") }` here
         * whenever the schema changes. Keep version numbers contiguous.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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

/** Chunk `kind` discriminator for the original GPS trace (mirrors `RouteRepositoryImpl.KIND_RAW`). */
private const val KIND_RAW = "RAW"

/** Chunk `kind` discriminator for the OSRM-corrected trace (mirrors `RouteRepositoryImpl.KIND_CORRECTED`). */
private const val KIND_CORRECTED = "CORRECTED"

/**
 * Schema migration from version 2 to 3.
 *
 * Moves GPS trace blobs out-of-row to fix `SQLiteBlobTooBigException` on long rides (D11).
 *
 * Steps:
 * 1. Create `route_trace_chunk` table.
 * 2. Add `thumbnailPathD` column to `routes`.
 * 3. Chunk any existing `pathJson`/`correctedPathJson` data and insert into `route_trace_chunk`.
 * 4. Recreate `routes` table without `pathJson`/`correctedPathJson` (SQLite cannot DROP columns
 *    before version 3.35, so we use the create-new-table → copy → drop → rename pattern).
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Step 1 – create the chunk table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS route_trace_chunk (
                routeId TEXT NOT NULL,
                kind    TEXT NOT NULL,
                seq     INTEGER NOT NULL,
                chunkJson TEXT NOT NULL,
                PRIMARY KEY (routeId, kind, seq),
                FOREIGN KEY (routeId) REFERENCES routes(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_route_trace_chunk_routeId ON route_trace_chunk (routeId)",
        )

        // Step 2 – add thumbnailPathD column
        db.execSQL("ALTER TABLE routes ADD COLUMN thumbnailPathD TEXT")

        // Step 3 – chunk existing trace data.
        // Read one blob column per row (never both together): a single route could hold a large
        // raw AND a large corrected trace, whose combined size would overflow the 2 MB CursorWindow
        // and crash the migration itself. Collect the (tiny) ids first, then fetch each blob alone.
        val routeIds = mutableListOf<String>()
        val idCursor = db.query("SELECT id FROM routes")
        try {
            while (idCursor.moveToNext()) routeIds.add(idCursor.getString(0))
        } finally {
            idCursor.close()
        }
        for (routeId in routeIds) {
            insertChunks(db, routeId, KIND_RAW, readBlob(db, routeId, "pathJson"))
            insertChunks(db, routeId, KIND_CORRECTED, readBlob(db, routeId, "correctedPathJson"))
        }

        // Step 4 – recreate routes table without pathJson/correctedPathJson
        db.execSQL(
            """
            CREATE TABLE routes_new (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                dateEpochMs INTEGER NOT NULL,
                bikeId TEXT,
                km REAL NOT NULL,
                durSec INTEGER NOT NULL,
                avg REAL NOT NULL,
                max REAL NOT NULL,
                lean REAL NOT NULL,
                elev REAL NOT NULL,
                fuel REAL NOT NULL,
                synced INTEGER NOT NULL,
                wxJson TEXT,
                speedJson TEXT,
                elevProfileJson TEXT,
                notes TEXT,
                thumbnailPathD TEXT,
                correctionStatus TEXT NOT NULL DEFAULT 'NONE',
                confidence REAL,
                FOREIGN KEY (bikeId) REFERENCES bikes(id) ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_routes_new_bikeId ON routes_new (bikeId)")
        db.execSQL(
            """
            INSERT INTO routes_new
              (id, name, dateEpochMs, bikeId, km, durSec, avg, max, lean, elev, fuel, synced,
               wxJson, speedJson, elevProfileJson, notes, thumbnailPathD, correctionStatus, confidence)
            SELECT
              id, name, dateEpochMs, bikeId, km, durSec, avg, max, lean, elev, fuel, synced,
              wxJson, speedJson, elevProfileJson, notes, thumbnailPathD, correctionStatus, confidence
            FROM routes
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE routes")
        db.execSQL("ALTER TABLE routes_new RENAME TO routes")
    }

    /**
     * Reads a single nullable text [column] for one route, so no CursorWindow load ever holds more
     * than one trace blob at a time. Returns null when the row is missing or the value is NULL.
     */
    private fun readBlob(db: SupportSQLiteDatabase, routeId: String, column: String): String? {
        val cursor = db.query("SELECT $column FROM routes WHERE id = ?", arrayOf<Any?>(routeId))
        return try {
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        } finally {
            cursor.close()
        }
    }

    private fun insertChunks(
        db: SupportSQLiteDatabase,
        routeId: String,
        kind: String,
        json: String?,
    ) {
        val chunks = TraceChunkCodec.split(json)
        chunks.forEachIndexed { seq, chunkJson ->
            db.execSQL(
                "INSERT INTO route_trace_chunk (routeId, kind, seq, chunkJson) VALUES (?, ?, ?, ?)",
                arrayOf(routeId, kind, seq, chunkJson),
            )
        }
    }
}

/**
 * Schema migration from version 3 to 4 (E3 — per-bike fuel model).
 *
 * Adds three nullable fuel columns to `bikes` and one nullable fuel-price-override
 * column to `routes`. All are REAL (nullable) with no NOT NULL/DEFAULT so existing
 * rows remain valid and the new fields default to NULL (unknown until the user sets them).
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bikes ADD COLUMN tankCapacityL REAL")
        db.execSQL("ALTER TABLE bikes ADD COLUMN fuelPricePerL REAL")
        db.execSQL("ALTER TABLE bikes ADD COLUMN consumptionLper100km REAL")
        db.execSQL("ALTER TABLE routes ADD COLUMN fuelPricePerL REAL")
    }
}

/**
 * Schema migration from version 4 to 5 (E7 — separate max-left/right lean tracking).
 *
 * Adds two NOT NULL REAL columns with DEFAULT 0 to `routes` so that existing rows
 * have a well-defined zero value for rides recorded before E7.
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE routes ADD COLUMN maxLeanLeftDeg REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE routes ADD COLUMN maxLeanRightDeg REAL NOT NULL DEFAULT 0")
    }
}

/**
 * Schema migration from version 5 to 6 (G5 — per-route refuel event ledger).
 *
 * Creates the `refuel_event` table with a CASCADE foreign key to `routes` and an
 * index on `routeId` so per-route queries remain fast regardless of event count.
 */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS refuel_event (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                routeId TEXT NOT NULL,
                epochMs INTEGER NOT NULL,
                litres REAL NOT NULL,
                pricePerL REAL NOT NULL,
                FOREIGN KEY(routeId) REFERENCES routes(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_refuel_event_routeId ON refuel_event (routeId)",
        )
    }
}
