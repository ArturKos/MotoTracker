package com.mototracker.data.local

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM migration tests for [MotoDatabase] schema versions 3→4 and 4→5.
 *
 * Runs entirely on the Robolectric JVM runtime — no device or emulator required.
 * Uses [SQLiteDatabase] directly (fully implemented by Robolectric) to avoid the
 * asset-path limitation of [androidx.room.testing.MigrationTestHelper] in unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationTest {

    @Test
    fun migrate3To4_preservesBikesAndRoutesRows() {
        val db = SQLiteDatabase.openOrCreateDatabase(":memory:", null)

        // ── Build version-3 schema ────────────────────────────────────────────
        db.execSQL(
            """CREATE TABLE bikes (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                year INTEGER NOT NULL,
                plate TEXT NOT NULL,
                status TEXT NOT NULL
            )""",
        )
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
                wxJson TEXT,
                speedJson TEXT,
                elevProfileJson TEXT,
                notes TEXT,
                thumbnailPathD TEXT
            )""",
        )

        // ── Insert v3 seed rows ───────────────────────────────────────────────
        db.execSQL(
            "INSERT INTO bikes (id, name, year, plate, status) VALUES ('bike-1', 'Yamaha MT-07', 2021, 'WA 12345', 'ACTIVE')",
        )
        db.execSQL(
            """INSERT INTO routes (id, name, dateEpochMs, bikeId, km, durSec, avg, max, lean, elev, fuel, synced, correctionStatus)
               VALUES ('route-1', 'Morning Ride', 1700000000000, 'bike-1', 42.5, 3600, 70.0, 120.0, 15.0, 250.0, 2.1, 0, 'NONE')""",
        )

        // ── Run MIGRATION_3_4 SQL (mirrors the private val in MotoDatabase) ───
        db.execSQL("ALTER TABLE bikes ADD COLUMN tankCapacityL REAL")
        db.execSQL("ALTER TABLE bikes ADD COLUMN fuelPricePerL REAL")
        db.execSQL("ALTER TABLE bikes ADD COLUMN consumptionLper100km REAL")
        db.execSQL("ALTER TABLE routes ADD COLUMN fuelPricePerL REAL")

        // ── Verify bikes row survived and new nullable columns are NULL ────────
        db.rawQuery(
            "SELECT id, name, year, plate, status, tankCapacityL, fuelPricePerL, consumptionLper100km FROM bikes WHERE id = 'bike-1'",
            null,
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("bike-1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("Yamaha MT-07", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals(2021, cursor.getInt(cursor.getColumnIndexOrThrow("year")))
            assertEquals("WA 12345", cursor.getString(cursor.getColumnIndexOrThrow("plate")))
            assertEquals("ACTIVE", cursor.getString(cursor.getColumnIndexOrThrow("status")))
            assertTrue("tankCapacityL should be NULL", cursor.isNull(cursor.getColumnIndexOrThrow("tankCapacityL")))
            assertTrue("fuelPricePerL should be NULL", cursor.isNull(cursor.getColumnIndexOrThrow("fuelPricePerL")))
            assertTrue("consumptionLper100km should be NULL", cursor.isNull(cursor.getColumnIndexOrThrow("consumptionLper100km")))
        }

        // ── Verify routes row survived and new nullable fuelPricePerL is NULL ──
        db.rawQuery(
            "SELECT id, name, km, fuel, fuelPricePerL FROM routes WHERE id = 'route-1'",
            null,
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("route-1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("Morning Ride", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals(42.5, cursor.getDouble(cursor.getColumnIndexOrThrow("km")), 0.001)
            assertEquals(2.1, cursor.getDouble(cursor.getColumnIndexOrThrow("fuel")), 0.001)
            assertTrue("fuelPricePerL should be NULL", cursor.isNull(cursor.getColumnIndexOrThrow("fuelPricePerL")))
        }

        db.close()
    }

    /**
     * Verifies [MIGRATION_4_5]: two NOT NULL DEFAULT 0 columns are added to `routes`,
     * pre-existing v4 rows survive, and the new columns default to 0.
     */
    @Test
    fun migrate4To5_addsMaxLeanColumnsWithDefaultZero() {
        val db = SQLiteDatabase.openOrCreateDatabase(":memory:", null)

        // ── Build version-4 routes schema (mirrors v4 DDL from MIGRATION_2_3) ─
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
                fuelPricePerL REAL
            )""",
        )

        // ── Insert a v4 seed row ──────────────────────────────────────────────
        db.execSQL(
            """INSERT INTO routes (id, name, dateEpochMs, bikeId, km, durSec, avg, max, lean, elev, fuel, synced, correctionStatus)
               VALUES ('route-2', 'Evening Ride', 1700001000000, NULL, 78.3, 5400, 52.2, 130.0, 38.0, 120.0, 3.9, 0, 'NONE')""",
        )

        // ── Run MIGRATION_4_5 SQL (mirrors private val in MotoDatabase) ───────
        db.execSQL("ALTER TABLE routes ADD COLUMN maxLeanLeftDeg REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE routes ADD COLUMN maxLeanRightDeg REAL NOT NULL DEFAULT 0")

        // ── Verify existing row survived and new columns default to 0 ─────────
        db.rawQuery(
            "SELECT id, lean, maxLeanLeftDeg, maxLeanRightDeg FROM routes WHERE id = 'route-2'",
            null,
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("route-2", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals(38.0, cursor.getDouble(cursor.getColumnIndexOrThrow("lean")), 0.001)
            assertEquals(
                0.0,
                cursor.getDouble(cursor.getColumnIndexOrThrow("maxLeanLeftDeg")),
                0.0,
            )
            assertEquals(
                0.0,
                cursor.getDouble(cursor.getColumnIndexOrThrow("maxLeanRightDeg")),
                0.0,
            )
        }

        db.close()
    }
}
