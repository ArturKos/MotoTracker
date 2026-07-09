package com.mototracker.data.network

import com.mototracker.data.model.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 30_000
private const val ENDPOINT_PATH = "/api/routes"

/**
 * [GpStrackClient] implementation using [HttpURLConnection] on [Dispatchers.IO].
 *
 * No third-party HTTP library is introduced — only the JVM's built-in networking classes
 * and the Android [org.json.JSONObject] already present in the SDK.
 *
 * The actual HTTP round-trip is on-device only (🔬); unit tests should use a
 * fake [GpStrackClient] instead.
 */
@Singleton
class HttpGpStrackClient @Inject constructor() : GpStrackClient {

    /**
     * POSTs [route] as a JSON body to `<serverAddress>/api/routes`.
     *
     * Returns [Result.success] on HTTP 200–299; [Result.failure] on any non-2xx status or
     * I/O exception.
     */
    override suspend fun uploadRoute(serverAddress: String, route: Route): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJson(route).toString().toByteArray(Charsets.UTF_8)
                val conn = URL("$serverAddress$ENDPOINT_PATH").openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.setRequestProperty("Content-Length", body.size.toString())
                    conn.connectTimeout = CONNECT_TIMEOUT_MS
                    conn.readTimeout = READ_TIMEOUT_MS
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body) }

                    val code = conn.responseCode
                    check(code in 200..299) { "HTTP $code" }
                } finally {
                    conn.disconnect()
                }
            }
        }

    private fun buildJson(route: Route): JSONObject = JSONObject().apply {
        put("id", route.id)
        put("name", route.name)
        put("dateEpochMs", route.dateEpochMs)
        put("bikeId", route.bikeId ?: JSONObject.NULL)
        put("km", route.km)
        put("durSec", route.durSec)
        put("avg", route.avg)
        put("max", route.max)
        put("lean", route.lean)
        put("elev", route.elev)
        put("fuel", route.fuel)
        route.wxJson?.let { put("wxJson", it) }
        route.pathJson?.let { put("pathJson", it) }
        route.speedJson?.let { put("speedJson", it) }
        route.elevProfileJson?.let { put("elevProfileJson", it) }
        route.notes?.let { put("notes", it) }
    }
}
