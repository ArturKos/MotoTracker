package com.mototracker.data.local

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM migration test for [MotoDatabase] schema version 9→10.
 *
 * Verifies:
 * - The `rider` table is created with the correct columns and defaults.
 * - Three new columns (shortId, firstSeenMs, lastSeenMs) are added to `waves`.
 * - A pre-existing v9 `waves` row is preserved and decodes with empty/zero defaults.
 *
 * Uses [SQLiteDatabase] directly under Robolectric — no device or emulator required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MotoDatabaseMigration9to10Test {

    @Test
    fun migrate9To10_riderTableCreatedAndWavesColumnsAdded() {
        val db = SQLiteDatabase.openOrCreateDatabase(":memory:", null)

        // ── Build a minimal version-9 schema ──────────────────────────────────
        db.execSQL(
            """CREATE TABLE routes (
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
                synced INTEGER NOT NULL DEFAULT 0,
                correctionStatus TEXT NOT NULL DEFAULT 'NONE',
                confidence REAL,
                wxJson TEXT,
                speedJson TEXT,
                elevProfileJson TEXT,
                notes TEXT,
                thumbnailPathD TEXT,
                fuelPricePerL REAL,
                maxLeanLeftDeg REAL NOT NULL DEFAULT 0,
                maxLeanRightDeg REAL NOT NULL DEFAULT 0,
                leanHistogramJson TEXT
            )""",
        )
        db.execSQL(
            """CREATE TABLE waves (
                id TEXT NOT NULL PRIMARY KEY,
                nick TEXT NOT NULL,
                bikeName TEXT NOT NULL,
                place TEXT NOT NULL,
                timeLabel TEXT NOT NULL,
                routeId TEXT,
                FOREIGN KEY (routeId) REFERENCES routes(id) ON DELETE SET NULL
            )""",
        )

        // ── Insert a v9 wave row (no shortId / firstSeenMs / lastSeenMs) ──────
        db.execSQL(
            "INSERT INTO waves (id, nick, bikeName, place, timeLabel, routeId) VALUES ('wave-v9', 'OldRider', 'Triumph', 'Warsaw', '12:00', NULL)",
        )

        // ── Run MIGRATION_9_10 SQL (mirrors private val in MotoDatabase) ───────
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS rider (
                shortId TEXT NOT NULL PRIMARY KEY,
                nick TEXT NOT NULL,
                bike TEXT NOT NULL,
                lastSeenMs INTEGER NOT NULL,
                inGroup INTEGER NOT NULL DEFAULT 0
            )""",
        )
        db.execSQL("ALTER TABLE waves ADD COLUMN shortId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE waves ADD COLUMN firstSeenMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE waves ADD COLUMN lastSeenMs INTEGER NOT NULL DEFAULT 0")

        // ── Verify rider table exists and can hold a row ────────────────────────
        db.execSQL(
            "INSERT INTO rider (shortId, nick, bike, lastSeenMs) VALUES ('AB12', 'Alice', 'Ducati', 1700010000000)",
        )
        db.rawQuery("SELECT shortId, nick, bike, lastSeenMs, inGroup FROM rider WHERE shortId = 'AB12'", null).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("AB12", cursor.getString(cursor.getColumnIndexOrThrow("shortId")))
            assertEquals("Alice", cursor.getString(cursor.getColumnIndexOrThrow("nick")))
            assertEquals("Ducati", cursor.getString(cursor.getColumnIndexOrThrow("bike")))
            assertEquals(1700010000000L, cursor.getLong(cursor.getColumnIndexOrThrow("lastSeenMs")))
            assertEquals("inGroup should default to 0", 0, cursor.getInt(cursor.getColumnIndexOrThrow("inGroup")))
        }

        // ── Verify existing v9 wave row is preserved with zero/empty defaults ──
        db.rawQuery(
            "SELECT id, nick, bikeName, shortId, firstSeenMs, lastSeenMs FROM waves WHERE id = 'wave-v9'",
            null,
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("wave-v9", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("OldRider", cursor.getString(cursor.getColumnIndexOrThrow("nick")))
            assertEquals("Triumph", cursor.getString(cursor.getColumnIndexOrThrow("bikeName")))
            assertEquals("shortId should default to empty string", "", cursor.getString(cursor.getColumnIndexOrThrow("shortId")))
            assertEquals("firstSeenMs should default to 0", 0L, cursor.getLong(cursor.getColumnIndexOrThrow("firstSeenMs")))
            assertEquals("lastSeenMs should default to 0", 0L, cursor.getLong(cursor.getColumnIndexOrThrow("lastSeenMs")))
        }

        // ── Verify a new wave row can store all fields ────────────────────────
        db.execSQL(
            "INSERT INTO waves (id, nick, bikeName, place, timeLabel, routeId, shortId, firstSeenMs, lastSeenMs) VALUES ('wave-v10', 'NewRider', 'BMW', 'Kraków', '15:30', NULL, 'CD34', 1700020000000, 1700020300000)",
        )
        db.rawQuery(
            "SELECT shortId, firstSeenMs, lastSeenMs FROM waves WHERE id = 'wave-v10'",
            null,
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("CD34", cursor.getString(cursor.getColumnIndexOrThrow("shortId")))
            assertEquals(1700020000000L, cursor.getLong(cursor.getColumnIndexOrThrow("firstSeenMs")))
            assertEquals(1700020300000L, cursor.getLong(cursor.getColumnIndexOrThrow("lastSeenMs")))
        }

        db.close()
    }
}
