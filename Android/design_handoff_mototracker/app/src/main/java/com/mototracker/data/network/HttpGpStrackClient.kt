package com.mototracker.data.network

import com.mototracker.data.model.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

private const val ENDPOINT_ROUTES = "/api/routes"
private const val ENDPOINT_LOGIN = "/login"

/**
 * [GpStrackClient] implementation that delegates transport to [HttpTransport] and
 * persists / attaches session cookies via [SessionStore].
 *
 * The actual HTTP round-trip is handled by [UrlConnectionHttpTransport] in production
 * (on-device only, 🔬). A [FakeHttpTransport] is injected in unit tests.
 *
 * @param transport    Injectable HTTP transport seam.
 * @param sessionStore Persists the session cookie between requests.
 */
@Singleton
class HttpGpStrackClient @Inject constructor(
    private val transport: HttpTransport,
    private val sessionStore: SessionStore,
) : GpStrackClient {

    /**
     * POSTs credentials to `<serverAddress>/login` as `application/x-www-form-urlencoded`.
     *
     * Field names are `email` and `password` (assumption — verify against the backend if
     * the server uses different names). On a 2xx response the first `Set-Cookie` value's
     * `name=value` pair is extracted and persisted via [SessionStore.save].
     *
     * @return [Result.success] on HTTP 2xx + valid Set-Cookie;
     *         [Result.failure] on non-2xx, missing cookie, or transport error.
     */
    override suspend fun login(
        serverAddress: String,
        email: String,
        password: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyStr = "email=${URLEncoder.encode(email, "UTF-8")}" +
                "&password=${URLEncoder.encode(password, "UTF-8")}"
            val body = bodyStr.toByteArray(Charsets.UTF_8)
            val request = HttpRequest(
                url = "$serverAddress$ENDPOINT_LOGIN",
                method = "POST",
                headers = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "Content-Length" to body.size.toString(),
                ),
                body = body,
            )
            val response = transport.execute(request).getOrThrow()
            check(response.code in 200..299) { "HTTP ${response.code}" }
            val cookie = parseSessionCookie(response.headers)
                ?: error("No Set-Cookie in login response")
            sessionStore.save(cookie, email)
        }
    }

    /**
     * POSTs [route] as a JSON body to `<serverAddress>/api/routes`.
     *
     * Attaches the current session cookie (if any) as a `Cookie` request header.
     * On a 401 response the session is cleared and [UnauthorizedException] is returned.
     *
     * @return [Result.success] on HTTP 2xx; [Result.failure] wrapping
     *         [UnauthorizedException] on 401, or any other error otherwise.
     */
    override suspend fun uploadRoute(serverAddress: String, route: Route): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val session = sessionStore.session.first()
                val body = buildJson(route).toString().toByteArray(Charsets.UTF_8)
                val headers = buildMap<String, String> {
                    put("Content-Type", "application/json; charset=utf-8")
                    session.cookie?.let { put("Cookie", it) }
                }
                val request = HttpRequest(
                    url = "$serverAddress$ENDPOINT_ROUTES",
                    method = "POST",
                    headers = headers,
                    body = body,
                )
                val response = transport.execute(request).getOrThrow()
                if (response.code == 401) {
                    sessionStore.clear()
                    throw UnauthorizedException()
                }
                check(response.code in 200..299) { "HTTP ${response.code}" }
            }
        }

    /**
     * Finds the first `Set-Cookie` header in [headers] (case-insensitive key lookup)
     * and returns the `name=value` pair before the first semicolon, or null if absent.
     */
    private fun parseSessionCookie(headers: Map<String, List<String>>): String? {
        val values = headers.entries
            .firstOrNull { it.key.equals("Set-Cookie", ignoreCase = true) }
            ?.value
            ?: return null
        return values.firstOrNull()
            ?.split(";")
            ?.firstOrNull()
            ?.trim()
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
