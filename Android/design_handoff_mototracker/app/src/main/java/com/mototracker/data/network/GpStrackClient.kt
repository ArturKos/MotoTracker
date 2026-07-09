package com.mototracker.data.network

import com.mototracker.data.model.Route

/**
 * Client contract for uploading a recorded route to the GPStrack server.
 *
 * Contract:
 * - An HTTP 2xx response is a success → [Result.success] wrapping [Unit].
 * - A non-2xx status code or any I/O error is a failure → [Result.failure] wrapping the
 *   underlying [Throwable]. The caller is responsible for scheduling retries.
 * - Implementations must be main-safe (do their own dispatcher switching if needed).
 */
interface GpStrackClient {

    /**
     * Uploads [route] to the GPStrack server at [serverAddress].
     *
     * @param serverAddress Base URL of the GPStrack server, e.g. `http://192.168.1.145/gpstrack`.
     * @param route         Domain model of the route to upload.
     * @return [Result.success] on HTTP 2xx; [Result.failure] on any error.
     */
    suspend fun uploadRoute(serverAddress: String, route: Route): Result<Unit>
}
