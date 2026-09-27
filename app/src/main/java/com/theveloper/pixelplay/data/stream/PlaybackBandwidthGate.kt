package com.theveloper.pixelplay.data.stream

import android.os.Process
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Keeps background downloads from competing with the song that is playing.
 *
 * The player marks when it is still pulling the *current* song over the network
 * ([streamingCurrentSong]); while that is set, downloads are paced to [THROTTLED_BYTES_PER_SEC]
 * so the stream's buffer always wins. Downloads also run on [downloadDispatcher], whose threads
 * use Android's background priority so tagging/verification never steals CPU from the audio path.
 */
object PlaybackBandwidthGate {
    @Volatile
    var streamingCurrentSong: Boolean = false

    /** Download speed while the playing song is still streaming in (bytes per second). */
    const val THROTTLED_BYTES_PER_SEC: Long = 384L * 1024

    private val threadCount = AtomicInteger()

    val downloadDispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(3) { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "pixelplay-download-${threadCount.incrementAndGet()}").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    /**
     * Returns how long to wait after reading [bytes] that took [elapsedMs], so the average rate
     * stays at or below the throttle while the playing song is streaming. 0 when not throttled.
     */
    fun pauseAfterRead(bytes: Int, elapsedMs: Long): Long {
        if (!streamingCurrentSong || bytes <= 0) return 0L
        val targetMs = bytes * 1000L / THROTTLED_BYTES_PER_SEC
        return (targetMs - elapsedMs).coerceAtLeast(0L)
    }
}
