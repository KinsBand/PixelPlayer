package com.theveloper.pixelplay.data.network.musicbrainz

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Strict 1 request/second rate limiter shared across MusicBrainz AND
 * Cover Art Archive calls (both are MetaBrainz services operating under
 * the same "no more than one request per second" policy).
 *
 * The timestamp critical section is kept short (only spacing is
 * serialized); the actual HTTP request runs outside the mutex so slow
 * responses don't block other coroutines from scheduling their slot.
 */
@Singleton
class MusicBrainzRateLimiter @Inject constructor() {

    private val mutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun <T> withPermit(block: suspend () -> T): T {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val waitMs = MIN_INTERVAL_MS - (now - lastRequestAt)
            if (waitMs > 0) {
                delay(waitMs)
            }
            lastRequestAt = System.currentTimeMillis()
        }
        return block()
    }

    private companion object {
        const val MIN_INTERVAL_MS = 1000L
    }
}
