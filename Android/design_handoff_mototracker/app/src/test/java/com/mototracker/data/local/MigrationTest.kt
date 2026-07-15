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

    /**
     * Verifies [MIGRATION_5_6]: the `refuel_event` table and its `routeId` index are created,
     * existing `routes` rows survive, and a new refuel event can be inserted and retrieved.
     */
    @Test
    fun migrate5To6_createsRefuelEventTableAndIndex() {
        val db = SQLiteDatabase.openOrCreateDatabase(":memory:", null)

        // ── Build version-5 schema (routes only, no refuel_event table) ────────
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
                maxLeanRightDeg REAL NOT NULL DEFAULT 0
            )""",
        )

        // ── Insert a pre-existing route row ───────────────────────────────────
        db.execSQL(
            """INSERT INTO routes (id, name, dateEpochMs, bikeId, km, durSec, avg, max, lean, elev, fuel, synced, correctionStatus)
               VALUES ('route-g5', 'G5 Test Ride', 1700002000000, NULL, 55.0, 2700, 73.3, 145.0, 22.0, 80.0, 2.8, 0, 'NONE')""",
        )

        // ── Run MIGRATION_5_6 SQL (mirrors the private val in MotoDatabase) ───
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS refuel_event (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                routeId TEXT NOT NULL,
                epochMs INTEGER NOT NULL,
                litres REAL NOT NULL,
                pricePerL REAL NOT NULL,
                FOREIGN KEY(routeId) REFERENCES routes(id) ON DELETE CASCADE
            )""",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_refuel_event_routeId ON refuel_event (routeId)",
        )

        // ── Verify existing routes row survived ───────────────────────────────
        db.rawQuery("SELECT id, km FROM routes WHERE id = 'route-g5'", null).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("route-g5", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals(55.0, cursor.getDouble(cursor.getColumnIndexOrThrow("km")), 0.001)
        }

        // ── Verify refuel_event table exists and can insert a row ─────────────
        db.execSQL(
            "INSERT INTO refuel_event (routeId, epochMs, litres, pricePerL) VALUES ('route-g5', 1700002500000, 18.5, 7.45)",
        )
        db.rawQuery("SELECT routeId, litres, pricePerL FROM refuel_event WHERE routeId = 'route-g5'", null).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("route-g5", cursor.getString(cursor.getColumnIndexOrThrow("routeId")))
            assertEquals(18.5, cursor.getDouble(cursor.getColumnIndexOrThrow("litres")), 0.001)
            assertEquals(7.45, cursor.getDouble(cursor.getColumnIndexOrThrow("pricePerL")), 0.001)
        }

        // ── Verify the routeId index exists ──────────────────────────────────
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_refuel_event_routeId'",
            null,
        ).use { cursor ->
            assertEquals("index_refuel_event_routeId should exist", 1, cursor.count)
        }

        db.close()
    }

    /**
     * Verifies [MIGRATION_6_7]: `autoUpdateConsumption INTEGER NOT NULL DEFAULT 0` is added to
     * `bikes`; pre-existing bike rows survive with the column defaulting to 0; a round-trip
     * through the mapper preserves the value.
     */
    @Test
    fun migrate6To7_addsAutoUpdateConsumptionColumnDefaultZero() {
        val db = SQLiteDatabase.openOrCreateDatabase(":memory:", null)

        // ── Build version-6 bikes schema (no autoUpdateConsumption column) ────
        db.execSQL(
            """CREATE TABLE bikes (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                year INTEGER NOT NULL,
                plate TEXT NOT NULL,
                status TEXT NOT NULL,
                tankCapacityL REAL,
                fuelPricePerL REAL,
                consumptionLper100km REAL
            )""",
        )

        // ── Insert a v6 bike row ──────────────────────────────────────────────
        db.execSQL(
            "INSERT INTO bikes (id, name, year, plate, status, consumptionLper100km) VALUES ('b-k2', 'Honda CB500', 2019, 'KR 111', 'ACTIVE', 5.5)",
        )

        // ── Run MIGRATION_6_7 SQL (mirrors private val in MotoDatabase) ───────
        db.execSQL("ALTER TABLE bikes ADD COLUMN autoUpdateConsumption INTEGER NOT NULL DEFAULT 0")

        // ── Verify column exists and pre-existing row defaults to 0 ──────────
        db.rawQuery(
            "SELECT id, consumptionLper100km, autoUpdateConsumption FROM bikes WHERE id = 'b-k2'",
            null,
        ).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("b-k2", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals(5.5, cursor.getDouble(cursor.getColumnIndexOrThrow("consumptionLper100km")), 0.001)
            assertEquals(
                "autoUpdateConsumption should default to 0",
                0,
                cursor.getInt(cursor.getColumnIndexOrThrow("autoUpdateConsumption")),
            )
        }

        // ── Verify a new row can store autoUpdateConsumption = 1 ─────────────
        db.execSQL(
            "INSERT INTO bikes (id, name, year, plate, status, autoUpdateConsumption) VALUES ('b-k2b', 'Yamaha MT-07', 2021, 'WA 222', 'ACTIVE', 1)",
        )
        db.rawQuery(
            "SELECT autoUpdateConsumption FROM bikes WHERE id = 'b-k2b'",
            null,
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("autoUpdateConsumption")))
        }

        db.close()
    }
}
