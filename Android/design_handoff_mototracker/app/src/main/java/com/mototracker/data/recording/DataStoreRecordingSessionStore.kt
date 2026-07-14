package com.mototracker.data.recording

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mototracker.domain.recording.RecordingEngineState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RecordingSessionStore] backed by the app's singleton [DataStore]<[Preferences]>.
 *
 * Reuses the same DataStore provided by [com.mototracker.di.SettingsModule] under
 * a dedicated key (`active_recording_session`) that does not collide with any other
 * preference keys used in this app.
 *
 * The snapshot is encoded as a JSON string via [encode] / [decode], which are exposed
 * as `internal` functions so the round-trip can be covered by unit tests.
 *
 * @param dataStore Injected singleton [DataStore]<[Preferences]> instance.
 */
@Singleton
class DataStoreRecordingSessionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : RecordingSessionStore {

    private object Keys {
        val ACTIVE_RECORDING_SESSION = stringPreferencesKey("active_recording_session")
    }

    /** Live stream of the most-recently persisted snapshot, decoded on each DataStore emission. */
    override val snapshot: Flow<ActiveSessionSnapshot?> = dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_RECORDING_SESSION]?.let { decode(it) }
    }

    /** Encodes [s] to JSON and atomically writes it to the DataStore. */
    override suspend fun save(s: ActiveSessionSnapshot) {
        dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_RECORDING_SESSION] = encode(s)
        }
    }

    /** Removes the snapshot key from the DataStore atomically. */
    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACTIVE_RECORDING_SESSION)
        }
    }
}

// ── JSON codec (internal for unit tests) ─────────────────────────────────────

/**
 * Encodes [snapshot] to a compact JSON string.
 *
 * All engine scalars are stored at the top level alongside the session fields;
 * there is no nested "engine" object, which keeps the decoder straightforward.
 * Arrays use the same key/shape as [RecordingEngine]'s build*Json helpers.
 */
internal fun encode(snapshot: ActiveSessionSnapshot): String {
    val e = snapshot.engineState
    return JSONObject().apply {
        put("startMs", snapshot.recordingStartMs)
        put("bikeId", if (snapshot.bikeId != null) snapshot.bikeId else JSONObject.NULL)
        put("paused", snapshot.paused)
        if (e.prevLat != null) put("prevLat", e.prevLat)
        if (e.prevLng != null) put("prevLng", e.prevLng)
        if (e.prevAlt != null) put("prevAlt", e.prevAlt)
        put("distKm", e.distanceKm)
        put("durSec", e.durationSec)
        put("movingSec", e.movingSec)
        put("spdKmh", e.currentSpeedKmh)
        put("maxSpdKmh", e.maxSpeedKmh)
        put("leanDeg", e.currentLeanDeg)
        put("maxLeanDeg", e.maxLeanDeg)
        put("altM", e.altitudeM)
        put("elevGainM", e.elevGainM)
        put("hdgDeg", e.headingDeg.toDouble())
        put("path", JSONArray().also { arr ->
            e.pathPoints.forEach { (lat, lng) ->
                arr.put(JSONObject().put("lat", lat).put("lng", lng))
            }
        })
        put("spd", JSONArray().also { arr ->
            e.speedOverTime.forEach { (t, v) ->
                arr.put(JSONObject().put("t", t).put("v", v))
            }
        })
        put("elev", JSONArray().also { arr ->
            e.elevOverDist.forEach { (d, a) ->
                arr.put(JSONObject().put("d", d).put("a", a))
            }
        })
        put("fuelRate", e.sessionFuelLper100km)
        if (e.tankCapacityL != null) put("tankCap", e.tankCapacityL) else put("tankCap", JSONObject.NULL)
        put("fillAnchorKm", e.fillAnchorKm)
    }.toString()
}

/**
 * Decodes a JSON string previously produced by [encode] back to an [ActiveSessionSnapshot].
 *
 * Returns null on any parse error so the caller can treat a corrupt entry as absent.
 */
internal fun decode(json: String): ActiveSessionSnapshot? = try {
    val o = JSONObject(json)

    fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null

    val pathArr = o.getJSONArray("path")
    val spdArr = o.getJSONArray("spd")
    val elevArr = o.getJSONArray("elev")

    val engineState = RecordingEngineState(
        prevLat = o.optDoubleOrNull("prevLat"),
        prevLng = o.optDoubleOrNull("prevLng"),
        prevAlt = o.optDoubleOrNull("prevAlt"),
        distanceKm = o.getDouble("distKm"),
        durationSec = o.getLong("durSec"),
        movingSec = o.optLong("movingSec", 0L),
        currentSpeedKmh = o.getDouble("spdKmh"),
        maxSpeedKmh = o.getDouble("maxSpdKmh"),
        currentLeanDeg = o.getDouble("leanDeg"),
        maxLeanDeg = o.getDouble("maxLeanDeg"),
        altitudeM = o.getDouble("altM"),
        elevGainM = o.getDouble("elevGainM"),
        headingDeg = o.getDouble("hdgDeg").toFloat(),
        pathPoints = (0 until pathArr.length()).map { i ->
            val p = pathArr.getJSONObject(i)
            p.getDouble("lat") to p.getDouble("lng")
        },
        speedOverTime = (0 until spdArr.length()).map { i ->
            val p = spdArr.getJSONObject(i)
            p.getLong("t") to p.getDouble("v")
        },
        elevOverDist = (0 until elevArr.length()).map { i ->
            val p = elevArr.getJSONObject(i)
            p.getDouble("d") to p.getDouble("a")
        },
        sessionFuelLper100km = o.optDouble("fuelRate", 5.0),
        tankCapacityL = o.optDoubleOrNull("tankCap"),
        fillAnchorKm = o.optDouble("fillAnchorKm", 0.0),
    )
    ActiveSessionSnapshot(
        engineState = engineState,
        recordingStartMs = o.getLong("startMs"),
        bikeId = if (o.has("bikeId") && !o.isNull("bikeId")) o.getString("bikeId") else null,
        paused = o.getBoolean("paused"),
    )
} catch (_: Exception) {
    null
}
