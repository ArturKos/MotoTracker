package com.mototracker.domain

/**
 * Computes exponential-backoff retry delays for the sync queue.
 *
 * Each failed attempt doubles the delay starting from [BASE_DELAY_MS], capped at
 * [MAX_DELAY_MS]. The exponent is clamped to 7 so delay never exceeds 64 × 30 s
 * before the cap applies.
 *
 * | attemptCount | delay     |
 * |-------------|-----------|
 * | 0           | 30 s      |
 * | 1           | 60 s      |
 * | 2           | 2 min     |
 * | 3           | 4 min     |
 * | 4           | 8 min     |
 * | 5           | 16 min    |
 * | 6           | 32 min    |
 * | 7+          | 60 min    |
 */
object SyncRetryPolicy {

    private const val BASE_DELAY_MS = 30_000L
    private const val MAX_DELAY_MS = 3_600_000L  // 1 hour

    /**
     * Returns the delay in milliseconds before the next upload attempt should run,
     * given the number of previous attempts that have already been made.
     *
     * @param attemptCount Number of upload attempts already made (≥ 0).
     * @return Delay in milliseconds, between [BASE_DELAY_MS] and [MAX_DELAY_MS].
     */
    fun nextRetryDelayMs(attemptCount: Int): Long {
        val exponent = attemptCount.coerceIn(0, 7)
        return (BASE_DELAY_MS shl exponent).coerceAtMost(MAX_DELAY_MS)
    }
}
