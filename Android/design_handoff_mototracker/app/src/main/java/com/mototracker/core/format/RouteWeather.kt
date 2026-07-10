package com.mototracker.core.format

import org.json.JSONObject

/**
 * Pre-formatted weather snapshot for the route-detail screen.
 *
 * String fields are ready for direct display; [rain] is a raw boolean so the Composable
 * can resolve it via localized string resources (`label_wx_rain_yes` / `label_wx_rain_no`).
 *
 * @param offline     `true` when no weather data is available (null JSON or missing temp).
 * @param tempDisplay Formatted temperature string, e.g. `"22°C"`, or `"—"` when offline.
 * @param humDisplay  Formatted humidity string, e.g. `"60%"`, or `"—"` when offline/absent.
 * @param rain        `true` = raining, `false` = dry, `null` = no data (offline).
 */
data class WeatherUi(
    val offline: Boolean,
    val tempDisplay: String,
    val humDisplay: String,
    val rain: Boolean?,
)

/**
 * Pure parser that converts a raw `wxJson` string into a [WeatherUi] for display.
 *
 * The expected JSON format is `{"temp": Int|null, "hum": Int|null, "rain": Boolean}`.
 * A null/blank [wxJson] or a null `temp` field results in an offline [WeatherUi].
 */
object RouteWeather {

    private const val DASH = "—"
    private val OFFLINE_UI = WeatherUi(offline = true, tempDisplay = DASH, humDisplay = DASH, rain = null)

    /**
     * Parses [wxJson] and returns a [WeatherUi] ready for display.
     *
     * Offline conditions (returns `WeatherUi(offline=true, …)`):
     * - [wxJson] is null or blank
     * - JSON is malformed
     * - the `"temp"` key is JSON null or absent
     *
     * @param wxJson Raw serialised weather object, or null.
     */
    fun parse(wxJson: String?): WeatherUi {
        if (wxJson.isNullOrBlank()) return OFFLINE_UI
        return try {
            val obj = JSONObject(wxJson)
            val temp = if (obj.isNull("temp")) null else obj.optInt("temp", Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE }
            if (temp == null) return OFFLINE_UI
            val hum = if (obj.isNull("hum")) null else obj.optInt("hum", Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE }
            val rain = obj.optBoolean("rain", false)
            WeatherUi(
                offline = false,
                tempDisplay = "$temp°C",
                humDisplay = if (hum != null) "$hum%" else DASH,
                rain = rain,
            )
        } catch (_: Exception) {
            OFFLINE_UI
        }
    }
}
