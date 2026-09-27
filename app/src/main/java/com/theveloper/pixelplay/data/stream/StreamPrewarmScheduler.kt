package com.theveloper.pixelplay.data.stream

import android.content.Context
import android.net.ConnectivityManager
import com.theveloper.pixelplay.data.youtube.NewPipeExecution
import com.theveloper.pixelplay.data.youtube.YouTubeRateLimit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts stream work for songs the user is likely to play next, before they tap them:
 * a row being pressed (the tap lands ~100 ms later) and the top results of a finished search.
 * The next queue item is prepared separately by the service's NextStreamPrewarmer.
 *
 * Speculation is bounded so it stays cheap and polite: at most [MAX_CONCURRENT] jobs, at most
 * [MAX_PER_MINUTE] speculative starts per minute, nothing while YouTube is rate limiting, and
 * audio bytes only on unmetered networks (metered networks get the manifest only, a few KB).
 * Extraction runs in the background lane, so it never takes a thread a tapped song needs.
 */
@Singleton
class StreamPrewarmScheduler internal constructor(
    private val prewarm: suspend (videoId: String, headBytes: Int) -> Unit,
    private val isMetered: () -> Boolean,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) {
    @Inject constructor(
        proxy: YouTubeStreamProxy,
        @ApplicationContext context: Context,
    ) : this(
        prewarm = { id, headBytes -> proxy.prewarm(id, headBytes) },
        isMetered = {
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                ?.isActiveNetworkMetered ?: true
        },
        clock = System::currentTimeMillis,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    enum class Reason(val headBytes: Int, val countsAgainstBudget: Boolean) {
        /** A finger is on the row: very likely to be played. */
        PRESS(PRESS_HEAD_BYTES, countsAgainstBudget = false),
        /** Top results of a finished search. */
        SEARCH(SEARCH_HEAD_BYTES, countsAgainstBudget = true),
    }

    private class Pending(val job: Job, val reason: Reason) {
        @Volatile var started = false
    }

    private val permits = Semaphore(MAX_CONCURRENT)
    private val lock = Any()
    private val pending = HashMap<String, Pending>()
    private val recentStarts = ArrayDeque<Long>()
    private val warmed = object : LinkedHashMap<String, Long>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>) = size > 64
    }

    /** A song row is being pressed. */
    fun onPress(videoId: String) = schedule(listOf(videoId), Reason.PRESS)

    /** The press turned into a scroll: drop the job unless it already started. */
    fun onPressCancelled(videoId: String) = cancelWaiting(Reason.PRESS) { it == videoId }

    /** A search finished; [videoIds] are its song results in display order. */
    fun onSearchResults(videoIds: List<String>) {
        cancelWaiting(Reason.SEARCH) { true }
        schedule(videoIds.distinct().take(SEARCH_TOP_N), Reason.SEARCH)
    }

    private fun schedule(videoIds: List<String>, reason: Reason) {
        val now = clock()
        if (YouTubeRateLimit.isLimited(now)) return
        for (raw in videoIds) {
            val id = raw.removePrefix("yt_")
            if (!VIDEO_ID.matches(id)) continue
            synchronized(lock) {
                if (id in pending) return@synchronized
                // A manifest resolved a few minutes ago is still cached; don't spend budget on it.
                warmed[id]?.let { if (now - it < REWARM_AFTER_MS) return@synchronized }
                if (reason.countsAgainstBudget) {
                    while (recentStarts.isNotEmpty() && now - recentStarts.first() >= 60_000) recentStarts.removeFirst()
                    if (recentStarts.size >= MAX_PER_MINUTE) return
                    recentStarts.addLast(now)
                }
                lateinit var entry: Pending
                val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                    try {
                        permits.withPermit {
                            if (YouTubeRateLimit.isLimited(clock())) return@withPermit
                            entry.started = true
                            val headBytes = if (isMetered()) 0 else reason.headBytes
                            withContext(NewPipeExecution.Background) { prewarm(id, headBytes) }
                            synchronized(lock) { warmed[id] = clock() }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(TAG).d(e, "Prewarm skipped for %s", id)
                    } finally {
                        synchronized(lock) { if (pending[id] === entry) pending.remove(id) }
                    }
                }
                entry = Pending(job, reason)
                pending[id] = entry
                job.start()
            }
        }
    }

    private fun cancelWaiting(reason: Reason, match: (String) -> Boolean) {
        synchronized(lock) {
            val iterator = pending.entries.iterator()
            while (iterator.hasNext()) {
                val (id, entry) = iterator.next()
                if (entry.reason == reason && !entry.started && match(id)) {
                    entry.job.cancel()
                    iterator.remove()
                }
            }
        }
    }

    internal fun pendingCount(): Int = synchronized(lock) { pending.size }

    companion object {
        private const val TAG = "StreamPrewarm"
        const val MAX_CONCURRENT = 2
        const val MAX_PER_MINUTE = 20
        const val SEARCH_TOP_N = 2
        const val PRESS_HEAD_BYTES = 256 * 1024
        const val SEARCH_HEAD_BYTES = 128 * 1024
        private const val REWARM_AFTER_MS = 5L * 60 * 1000
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}
