package com.theveloper.pixelplay.data.youtube

/**
 * Process-wide record of YouTube rate limiting (HTTP 429 / reCAPTCHA). Speculative work checks
 * it and stands down, because extra requests during a challenge only prolong it.
 */
object YouTubeRateLimit {
    const val BACKOFF_MS = 10L * 60 * 1000

    @Volatile private var limitedUntil = 0L

    fun report(now: Long = System.currentTimeMillis()) {
        limitedUntil = maxOf(limitedUntil, now + BACKOFF_MS)
    }

    fun isLimited(now: Long = System.currentTimeMillis()): Boolean = now < limitedUntil

    internal fun resetForTest() {
        limitedUntil = 0L
    }
}
