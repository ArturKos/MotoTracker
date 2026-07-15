package com.mototracker.data.network

import org.json.JSONObject

/**
 * Injectable seam for fetching current weather conditions at a GPS coordinate.
 *
 * Called once at ride start when the device is online. Implementations must be
 * main-safe (dispatching blocking I/O to [kotlinx.coroutines.Dispatchers.IO] internally).
 */
interface WeatherClient {
    /**
     * Fetches current weather conditions at the given coordinate.
     *
     * @param lat Latitude in decimal degrees.
     * @param lon Longitude in decimal degrees.
     * @return [Result.success] with a [WeatherSnapshot] on success, or
     *         [Result.failure] on any network or HTTP error.
     */
    suspend fun fetch(lat: Double, lon: Double): Result<WeatherSnapshot>
}

/**
 * Immutable snapshot of weather conditions captured at ride start.
 *
 * @param tempC    Ambient temperature in degrees Celsius, or null when unavailable.
 * @param humidity Relative humidity as an integer percentage (0–100), or null when unavailable.
 * @param rain     `true` when measured precipitation is greater than zero.
 */
data class WeatherSnapshot(
    val tempC: Int?,
    val humidity: Int?,
    val rain: Boolean,
) {
    /**
     * Serialises this snapshot to a compact JSON string consumed by
     * [com.mototracker.core.format.RouteWeather.parse].
     *
     * Output format: `{"temp":Int|null,"hum":Int|null,"rain":Boolean}`.
     */
    fun toWxJson(): String = JSONObject().apply {
        if (tempC != null) put("temp", tempC) else put("temp", JSONObject.NULL)
        if (humidity != null) put("hum", humidity) else put("hum", JSONObject.NULL)
        put("rain", rain)
    }.toString()
}
