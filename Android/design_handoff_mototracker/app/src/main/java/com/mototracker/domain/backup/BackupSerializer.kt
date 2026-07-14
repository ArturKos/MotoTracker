package com.mototracker.domain.backup

import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.settings.AppSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JSON serialiser for [BackupData].
 *
 * Uses `org.json` (available on the JVM classpath) so the domain layer has no Android
 * framework dependency. All encode/decode logic is deterministic and fully unit-testable.
 *
 * Null-safety convention:
 * - Nullable fields are written as `JSONObject.NULL` when absent.
 * - `optString`/`optDouble`/`optLong`/`optBoolean` are used for all reads so missing
 *   keys degrade gracefully rather than throwing.
 * - Unknown enum names for [CorrectionStatus] fall back to [CorrectionStatus.NONE];
 *   unknown [BikeStatus] names fall back to [BikeStatus.ACTIVE].
 */
object BackupSerializer {

    private const val KEY_SCHEMA = "schemaVersion"
    private const val KEY_ROUTES = "routes"
    private const val KEY_BIKES = "bikes"
    private const val KEY_SETTINGS = "settings"

    // Route keys
    private const val R_ID = "id"
    private const val R_NAME = "name"
    private const val R_DATE = "dateEpochMs"
    private const val R_BIKE_ID = "bikeId"
    private const val R_KM = "km"
    private const val R_DUR = "durSec"
    private const val R_AVG = "avg"
    private const val R_MAX = "max"
    private const val R_LEAN = "lean"
    private const val R_ELEV = "elev"
    private const val R_FUEL = "fuel"
    private const val R_SYNCED = "synced"
    private const val R_WX = "wxJson"
    private const val R_PATH = "pathJson"
    private const val R_SPEED = "speedJson"
    private const val R_ELEV_PROFILE = "elevProfileJson"
    private const val R_NOTES = "notes"
    private const val R_CORRECTED_PATH = "correctedPathJson"
    private const val R_CORRECTION_STATUS = "correctionStatus"
    private const val R_CONFIDENCE = "confidence"
    private const val R_FUEL_PRICE_PER_L = "fuelPricePerL"

    // Bike keys
    private const val B_ID = "id"
    private const val B_NAME = "name"
    private const val B_YEAR = "year"
    private const val B_PLATE = "plate"
    private const val B_STATUS = "status"
    private const val B_TANK_CAPACITY_L = "tankCapacityL"
    private const val B_FUEL_PRICE_PER_L = "fuelPricePerL"
    private const val B_CONSUMPTION_L_PER_100KM = "consumptionLper100km"

    // Settings keys
    private const val S_OFFLINE = "offline"
    private const val S_AUTO_SYNC = "autoSync"
    private const val S_OFFLINE_ONLY = "offlineOnly"
    private const val S_GPS_CORRECT = "gpsCorrect"
    private const val S_CURRENT_BIKE_ID = "currentBikeId"
    private const val S_SERVER_ADDRESS = "serverAddress"
    private const val S_UNITS = "units"
    private const val S_THEME = "theme"
    private const val S_ACCENT = "accent"
    private const val S_LANG = "lang"
    private const val S_AUTO_PAUSE = "autoPause"
    private const val S_KEEP_SCREEN_ON = "keepScreenOn"
    private const val S_ANDROID_AUTO = "androidAutoEnabled"
    private const val S_BC_NAME = "bcName"
    private const val S_BC_PHONE = "bcPhone"
    private const val S_BC_ORIGIN = "bcOrigin"
    private const val S_BC_SOCIAL = "bcSocial"
    private const val S_DEBUG_LOGGING = "debugLoggingEnabled"
    private const val S_OSRM_BASE_URL = "osrmBaseUrl"
    private const val S_CURRENCY = "currency"

    /**
     * Serialises [data] to a compact JSON string.
     *
     * @param data The backup snapshot to encode.
     * @return A JSON string that can be written to a file or transferred over the network.
     */
    fun encode(data: BackupData): String {
        val root = JSONObject()
        root.put(KEY_SCHEMA, data.schemaVersion)
        root.put(KEY_ROUTES, encodeRoutes(data.routes))
        root.put(KEY_BIKES, encodeBikes(data.bikes))
        root.put(KEY_SETTINGS, encodeSettings(data.settings))
        return root.toString()
    }

    /**
     * Deserialises a JSON string back to [BackupData].
     *
     * Returns [Result.failure] when:
     * - The string is not valid JSON.
     * - Any top-level key (`schemaVersion`, `routes`, `bikes`, `settings`) is absent.
     * - `schemaVersion` is greater than [BackupData.CURRENT_SCHEMA_VERSION].
     *
     * Unknown enum names are silently replaced by safe defaults rather than causing failure.
     *
     * @param json The raw JSON string to parse.
     * @return [Result.success] wrapping a [BackupData], or [Result.failure] with a descriptive exception.
     */
    fun decode(json: String): Result<BackupData> = runCatching {
        val root = JSONObject(json)
        require(root.has(KEY_SCHEMA)) { "Missing key: $KEY_SCHEMA" }
        require(root.has(KEY_ROUTES)) { "Missing key: $KEY_ROUTES" }
        require(root.has(KEY_BIKES)) { "Missing key: $KEY_BIKES" }
        require(root.has(KEY_SETTINGS)) { "Missing key: $KEY_SETTINGS" }

        val version = root.getInt(KEY_SCHEMA)
        require(version <= BackupData.CURRENT_SCHEMA_VERSION) {
            "Unsupported schema version $version (max supported: ${BackupData.CURRENT_SCHEMA_VERSION})"
        }

        BackupData(
            schemaVersion = version,
            routes = decodeRoutes(root.getJSONArray(KEY_ROUTES)),
            bikes = decodeBoikes(root.getJSONArray(KEY_BIKES)),
            settings = decodeSettings(root.getJSONObject(KEY_SETTINGS)),
        )
    }

    // ── Routes ────────────────────────────────────────────────────────────────

    private fun encodeRoutes(routes: List<Route>): JSONArray {
        val arr = JSONArray()
        for (r in routes) arr.put(encodeRoute(r))
        return arr
    }

    private fun encodeRoute(r: Route): JSONObject {
        val o = JSONObject()
        o.put(R_ID, r.id)
        o.put(R_NAME, r.name)
        o.put(R_DATE, r.dateEpochMs)
        o.put(R_BIKE_ID, r.bikeId ?: JSONObject.NULL)
        o.put(R_KM, r.km)
        o.put(R_DUR, r.durSec)
        o.put(R_AVG, r.avg)
        o.put(R_MAX, r.max)
        o.put(R_LEAN, r.lean)
        o.put(R_ELEV, r.elev)
        o.put(R_FUEL, r.fuel)
        o.put(R_SYNCED, r.synced)
        o.put(R_WX, r.wxJson ?: JSONObject.NULL)
        o.put(R_PATH, r.pathJson ?: JSONObject.NULL)
        o.put(R_SPEED, r.speedJson ?: JSONObject.NULL)
        o.put(R_ELEV_PROFILE, r.elevProfileJson ?: JSONObject.NULL)
        o.put(R_NOTES, r.notes ?: JSONObject.NULL)
        o.put(R_CORRECTED_PATH, r.correctedPathJson ?: JSONObject.NULL)
        o.put(R_CORRECTION_STATUS, r.correctionStatus.name)
        o.put(R_CONFIDENCE, r.confidence ?: JSONObject.NULL)
        o.put(R_FUEL_PRICE_PER_L, r.fuelPricePerL ?: JSONObject.NULL)
        return o
    }

    private fun decodeRoutes(arr: JSONArray): List<Route> =
        (0 until arr.length()).map { decodeRoute(arr.getJSONObject(it)) }

    private fun decodeRoute(o: JSONObject): Route {
        val correctionStatus = runCatching {
            CorrectionStatus.valueOf(o.optString(R_CORRECTION_STATUS, CorrectionStatus.NONE.name))
        }.getOrDefault(CorrectionStatus.NONE)

        return Route(
            id = o.getString(R_ID),
            name = o.optString(R_NAME, ""),
            dateEpochMs = o.getLong(R_DATE),
            bikeId = o.optStringOrNull(R_BIKE_ID),
            km = o.optDouble(R_KM, 0.0),
            durSec = o.optLong(R_DUR, 0L),
            avg = o.optDouble(R_AVG, 0.0),
            max = o.optDouble(R_MAX, 0.0),
            lean = o.optDouble(R_LEAN, 0.0),
            elev = o.optDouble(R_ELEV, 0.0),
            fuel = o.optDouble(R_FUEL, 0.0),
            synced = o.optBoolean(R_SYNCED, false),
            wxJson = o.optStringOrNull(R_WX),
            pathJson = o.optStringOrNull(R_PATH),
            speedJson = o.optStringOrNull(R_SPEED),
            elevProfileJson = o.optStringOrNull(R_ELEV_PROFILE),
            notes = o.optStringOrNull(R_NOTES),
            correctedPathJson = o.optStringOrNull(R_CORRECTED_PATH),
            correctionStatus = correctionStatus,
            confidence = if (o.isNull(R_CONFIDENCE) || !o.has(R_CONFIDENCE)) null else o.optDouble(R_CONFIDENCE),
            fuelPricePerL = if (o.isNull(R_FUEL_PRICE_PER_L) || !o.has(R_FUEL_PRICE_PER_L)) null else o.optDouble(R_FUEL_PRICE_PER_L),
        )
    }

    // ── Bikes ─────────────────────────────────────────────────────────────────

    private fun encodeBikes(bikes: List<Bike>): JSONArray {
        val arr = JSONArray()
        for (b in bikes) arr.put(encodeBike(b))
        return arr
    }

    private fun encodeBike(b: Bike): JSONObject {
        val o = JSONObject()
        o.put(B_ID, b.id)
        o.put(B_NAME, b.name)
        o.put(B_YEAR, b.year)
        o.put(B_PLATE, b.plate)
        o.put(B_STATUS, b.status.name)
        o.put(B_TANK_CAPACITY_L, b.tankCapacityL ?: JSONObject.NULL)
        o.put(B_FUEL_PRICE_PER_L, b.fuelPricePerL ?: JSONObject.NULL)
        o.put(B_CONSUMPTION_L_PER_100KM, b.consumptionLper100km ?: JSONObject.NULL)
        return o
    }

    private fun decodeBoikes(arr: JSONArray): List<Bike> =
        (0 until arr.length()).map { decodeBike(arr.getJSONObject(it)) }

    private fun decodeBike(o: JSONObject): Bike {
        val status = runCatching {
            BikeStatus.valueOf(o.optString(B_STATUS, BikeStatus.ACTIVE.name))
        }.getOrDefault(BikeStatus.ACTIVE)

        return Bike(
            id = o.getString(B_ID),
            name = o.optString(B_NAME, ""),
            year = o.optInt(B_YEAR, 0),
            plate = o.optString(B_PLATE, ""),
            status = status,
            tankCapacityL = if (o.isNull(B_TANK_CAPACITY_L) || !o.has(B_TANK_CAPACITY_L)) null else o.optDouble(B_TANK_CAPACITY_L),
            fuelPricePerL = if (o.isNull(B_FUEL_PRICE_PER_L) || !o.has(B_FUEL_PRICE_PER_L)) null else o.optDouble(B_FUEL_PRICE_PER_L),
            consumptionLper100km = if (o.isNull(B_CONSUMPTION_L_PER_100KM) || !o.has(B_CONSUMPTION_L_PER_100KM)) null else o.optDouble(B_CONSUMPTION_L_PER_100KM),
        )
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    private fun encodeSettings(s: AppSettings): JSONObject {
        val defaults = AppSettings()
        val o = JSONObject()
        o.put(S_OFFLINE, s.offline)
        o.put(S_AUTO_SYNC, s.autoSync)
        o.put(S_OFFLINE_ONLY, s.offlineOnly)
        o.put(S_GPS_CORRECT, s.gpsCorrect)
        o.put(S_CURRENT_BIKE_ID, s.currentBikeId ?: JSONObject.NULL)
        o.put(S_SERVER_ADDRESS, s.serverAddress)
        o.put(S_UNITS, s.units)
        o.put(S_THEME, s.theme)
        o.put(S_ACCENT, s.accent)
        o.put(S_LANG, s.lang)
        o.put(S_AUTO_PAUSE, s.autoPause)
        o.put(S_KEEP_SCREEN_ON, s.keepScreenOn)
        o.put(S_ANDROID_AUTO, s.androidAutoEnabled)
        o.put(S_BC_NAME, s.bcName)
        o.put(S_BC_PHONE, s.bcPhone)
        o.put(S_BC_ORIGIN, s.bcOrigin)
        o.put(S_BC_SOCIAL, s.bcSocial)
        o.put(S_DEBUG_LOGGING, s.debugLoggingEnabled)
        o.put(S_OSRM_BASE_URL, s.osrmBaseUrl)
        o.put(S_CURRENCY, s.currency)
        return o
    }

    private fun decodeSettings(o: JSONObject): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            offline = o.optBoolean(S_OFFLINE, defaults.offline),
            autoSync = o.optBoolean(S_AUTO_SYNC, defaults.autoSync),
            offlineOnly = o.optBoolean(S_OFFLINE_ONLY, defaults.offlineOnly),
            gpsCorrect = o.optBoolean(S_GPS_CORRECT, defaults.gpsCorrect),
            currentBikeId = o.optStringOrNull(S_CURRENT_BIKE_ID),
            serverAddress = o.optString(S_SERVER_ADDRESS, defaults.serverAddress),
            units = o.optString(S_UNITS, defaults.units),
            theme = o.optString(S_THEME, defaults.theme),
            accent = o.optString(S_ACCENT, defaults.accent),
            lang = o.optString(S_LANG, defaults.lang),
            autoPause = o.optBoolean(S_AUTO_PAUSE, defaults.autoPause),
            keepScreenOn = o.optBoolean(S_KEEP_SCREEN_ON, defaults.keepScreenOn),
            androidAutoEnabled = o.optBoolean(S_ANDROID_AUTO, defaults.androidAutoEnabled),
            bcName = o.optString(S_BC_NAME, defaults.bcName),
            bcPhone = o.optString(S_BC_PHONE, defaults.bcPhone),
            bcOrigin = o.optString(S_BC_ORIGIN, defaults.bcOrigin),
            bcSocial = o.optString(S_BC_SOCIAL, defaults.bcSocial),
            debugLoggingEnabled = o.optBoolean(S_DEBUG_LOGGING, defaults.debugLoggingEnabled),
            osrmBaseUrl = o.optString(S_OSRM_BASE_URL, defaults.osrmBaseUrl),
            currency = o.optString(S_CURRENCY, defaults.currency),
        )
    }

    // ── Null-safe helpers ─────────────────────────────────────────────────────

    /** Returns the string value for [key], or `null` if absent or equal to [JSONObject.NULL]. */
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key, null)
}
