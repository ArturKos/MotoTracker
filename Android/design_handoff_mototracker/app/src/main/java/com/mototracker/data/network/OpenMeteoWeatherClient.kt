package com.mototracker.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"

/**
 * [WeatherClient] implementation backed by the Open-Meteo public current-conditions API.
 *
 * No API key is required. The request fetches `temperature_2m`, `relative_humidity_2m`,
 * and `precipitation` for the supplied coordinate with `timezone=auto` so times are
 * interpreted locally. All network I/O is dispatched to [Dispatchers.IO] internally.
 *
 * Missing or null JSON fields are tolerated: absent data produces null for temp/humidity
 * and `false` for rain rather than failing the whole request.
 *
 * @param transport Injectable HTTP transport; replace with a fake in unit tests.
 */
@Singleton
class OpenMeteoWeatherClient @Inject constructor(
    private val transport: HttpTransport,
) : WeatherClient {

    /**
     * Fetches current weather at [lat]/[lon] from Open-Meteo.
     *
     * @return [Result.success] containing a [WeatherSnapshot], or [Result.failure]
     *         wrapping the underlying [Throwable] on any I/O or non-2xx error.
     */
    override suspend fun fetch(lat: Double, lon: Double): Result<WeatherSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$BASE_URL?" +
                    "latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,precipitation" +
                    "&timezone=auto"
                val response = transport.execute(HttpRequest(url = url, method = "GET"))
                    .getOrThrow()
                check(response.code in 200..299) { "Open-Meteo HTTP ${response.code}" }
                parseBody(response.body)
            }
        }

    private fun parseBody(body: String): WeatherSnapshot {
        val root = JSONObject(body)
        val current = root.optJSONObject("current")
        val tempC = if (current != null && !current.isNull("temperature_2m")) {
            current.optDouble("temperature_2m", Double.NaN)
                .takeIf { !it.isNaN() }
                ?.roundToInt()
        } else null
        val humidity = if (current != null && !current.isNull("relative_humidity_2m")) {
            current.optInt("relative_humidity_2m", Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE }
        } else null
        val rain = if (current != null && !current.isNull("precipitation")) {
            current.optDouble("precipitation", 0.0) > 0.0
        } else false
        return WeatherSnapshot(tempC = tempC, humidity = humidity, rain = rain)
    }
}
