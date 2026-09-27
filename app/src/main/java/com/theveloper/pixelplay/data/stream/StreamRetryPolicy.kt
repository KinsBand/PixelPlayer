package com.theveloper.pixelplay.data.stream

/** Bounded retries for transient transport failures and expired signed URLs. */
internal object StreamRetryPolicy {
    fun retryStatus(code: Int) = code in setOf(401, 403, 408, 429) || code in 500..599
    fun delayMs(attempt: Int) = 300L * (1L shl attempt.coerceIn(0, 3))
}
