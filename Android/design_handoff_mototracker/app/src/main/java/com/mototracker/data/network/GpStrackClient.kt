package com.mototracker.data.network

import com.mototracker.data.model.Route

/**
 * Client contract for interacting with the GPStrack server.
 *
 * Contract:
 * - An HTTP 2xx response is a success → [Result.success] wrapping [Unit].
 * - A non-2xx status code or any I/O error is a failure → [Result.failure] wrapping the
 *   underlying [Throwable]. The caller is responsible for scheduling retries.
 * - A 401 response returns [Result.failure] wrapping [UnauthorizedException] and clears
 *   the persisted session so the UI can prompt re-login.
 * - Implementations must be main-safe (do their own dispatcher switching if needed).
 */
interface GpStrackClient {

    /**
     * Authenticates with the GPStrack server using e-mail and password.
     *
     * On success the session cookie received in the `Set-Cookie` response header is
     * persisted and attached to subsequent requests automatically.
     *
     * Assumption: the GPStrack server accepts credentials as
     * `application/x-www-form-urlencoded` at `<serverAddress>/login` with fields
     * `email` and `password`. Verify against the actual backend if this differs.
     *
     * @param serverAddress Base URL of the GPStrack server, e.g. `http://192.168.1.145/gpstrack`.
     * @param email         User's e-mail address.
     * @param password      User's plain-text password.
     * @return [Result.success] on HTTP 2xx with a valid `Set-Cookie`;
     *         [Result.failure] on non-2xx, missing cookie, or I/O error.
     */
    suspend fun login(serverAddress: String, email: String, password: String): Result<Unit>

    /**
     * Uploads [route] to the GPStrack server at [serverAddress].
     *
     * Attaches the persisted session cookie when one is available. A 401 response
     * clears the session and returns [Result.failure] wrapping [UnauthorizedException].
     *
     * @param serverAddress Base URL of the GPStrack server, e.g. `http://192.168.1.145/gpstrack`.
     * @param route         Domain model of the route to upload.
     * @return [Result.success] on HTTP 2xx; [Result.failure] on any error.
     */
    suspend fun uploadRoute(serverAddress: String, route: Route): Result<Unit>
}
