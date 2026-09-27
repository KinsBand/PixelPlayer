package com.theveloper.pixelplay.data.stream

/** Bounded retries for transient transport failures and expired signed URLs. */
internal object StreamRetryPolicy {
    fun retryStatus(code: Int) = code in setOf(401, 403, 408, 410, 429) || code in 500..599
    /** The signed URL itself is no longer accepted, so it must be re-resolved. */
    fun urlRejected(code: Int) = code == 401 || code == 403 || code == 410
    fun delayMs(attempt: Int) = 300L * (1L shl attempt.coerceIn(0, 3))
}
