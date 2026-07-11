package com.mototracker.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 30_000

/**
 * [HttpTransport] implementation backed by [HttpURLConnection], running on [Dispatchers.IO].
 *
 * This class performs real network I/O and is **on-device only** (🔬). Unit tests should
 * inject a fake [HttpTransport] via the seam instead.
 */
@Singleton
class UrlConnectionHttpTransport @Inject constructor() : HttpTransport {

    /**
     * Opens a [HttpURLConnection] to [HttpRequest.url], writes the body when present,
     * and returns an [HttpResponse] wrapping the status code, headers, and decoded body.
     *
     * Response headers are preserved as-is from [HttpURLConnection.getHeaderFields], so
     * multiple `Set-Cookie` values appear as a `List<String>` under the `"Set-Cookie"` key.
     */
    override suspend fun execute(request: HttpRequest): Result<HttpResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(request.url).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = request.method
                    conn.connectTimeout = CONNECT_TIMEOUT_MS
                    conn.readTimeout = READ_TIMEOUT_MS
                    request.headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }
                    if (request.body != null) {
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Length", request.body.size.toString())
                        conn.outputStream.use { it.write(request.body) }
                    }
                    val code = conn.responseCode
                    // HttpURLConnection.getHeaderFields() null-keys are status lines — drop them
                    val headers: Map<String, List<String>> = conn.headerFields
                        .filterKeys { it != null }
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val body = stream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                    HttpResponse(code = code, headers = headers, body = body)
                } finally {
                    conn.disconnect()
                }
            }
        }
}
