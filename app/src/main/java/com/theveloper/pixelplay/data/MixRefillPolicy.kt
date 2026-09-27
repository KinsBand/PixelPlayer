package com.theveloper.pixelplay.data

internal object MixRefillPolicy {
    /** A mix never holds more than this many songs at once (recent history + current + upcoming). */
    const val MAX_QUEUE = 50
    /** Once fewer than this many songs are left, the mix is topped back up to [MAX_QUEUE]. */
    const val REMAINING_THRESHOLD = 10
    /** Played songs kept behind the current one (for "previous") when the queue is over the cap. */
    const val KEEP_HISTORY = 5
    /** Songs planned per refill pass; passes repeat until the queue is full. */
    const val BATCH = 20

    fun shouldRefill(queueSize: Int, currentIndex: Int): Boolean =
        currentIndex in 0 until queueSize && queueSize - currentIndex - 1 < REMAINING_THRESHOLD

    /** How many more songs fit, counting only [KEEP_HISTORY] played songs (older ones get trimmed). */
    fun room(queueSize: Int, currentIndex: Int): Int {
        val trimmable = (currentIndex - KEEP_HISTORY).coerceAtLeast(0)
        return (MAX_QUEUE - (queueSize - trimmable)).coerceAtLeast(0)
    }
}
