package com.mototracker.core.sms

/**
 * Pure, stateless decision seam for the Y2 periodic SMS feature.
 *
 * Fully JVM-testable — no Android framework imports.
 */
object SmsSendScheduler {

    /**
     * Returns `true` when a location SMS should be sent on this tick.
     *
     * All five preconditions must hold simultaneously:
     * 1. [enabled] — the user has switched SMS sharing on.
     * 2. [recipientCount] > 0 — there is at least one configured recipient.
     * 3. [hasFix] — a valid GPS fix is available (the sample is not `null`).
     * 4. The configured interval has elapsed since the last send, or no send has happened yet.
     *
     * When [lastSentMs] is `null` and all other conditions hold, the function returns `true` so
     * that the first SMS is sent immediately on the first tick after a fix is obtained.
     *
     * [intervalMinutes] is coerced to a minimum of 1 to guard against zero-division /
     * infinite-send loops if a corrupted value is persisted.
     *
     * @param enabled        Whether the SMS sharing feature is active in settings.
     * @param recipientCount Number of configured SMS recipients.
     * @param hasFix         Whether the service has received at least one GPS sample.
     * @param lastSentMs     Wall-clock ms of the previous successful send, or `null` if never sent.
     * @param nowMs          Current wall-clock ms (pass [System.currentTimeMillis] in production).
     * @param intervalMinutes Minimum minutes between successive sends (persisted setting).
     * @return `true` iff an SMS should be sent on this tick.
     */
    fun shouldSend(
        enabled: Boolean,
        recipientCount: Int,
        hasFix: Boolean,
        lastSentMs: Long?,
        nowMs: Long,
        intervalMinutes: Int,
    ): Boolean {
        if (!enabled) return false
        if (recipientCount <= 0) return false
        if (!hasFix) return false
        val intervalMs = intervalMinutes.coerceAtLeast(1) * 60_000L
        return lastSentMs == null || nowMs - lastSentMs >= intervalMs
    }
}
