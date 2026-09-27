package com.theveloper.pixelplay.data.scrobble

import com.theveloper.pixelplay.data.network.listenbrainz.ListenBrainzRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends finished plays to ListenBrainz when the user has saved a token.
 *
 * A play counts once the user has heard half the track or 4 minutes, whichever comes
 * first, and the track is at least 30 seconds long (the standard scrobbling rule).
 * Listens that fail to send (offline, server error) are kept in a small persisted
 * queue and retried with the next play.
 */
@Singleton
class Scrobbler @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: Lazy<MusicRepository>,
    private val listenBrainzRepository: ListenBrainzRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueMutex = Mutex()

    fun onPlaybackFinished(songId: String, listenedMs: Long, durationMs: Long, endedAtEpochMs: Long) {
        if (!qualifies(listenedMs, durationMs)) return
        scope.launch {
            runCatching {
                val token = userPreferencesRepository.listenBrainzTokenFlow.first()
                if (token.isBlank()) return@launch
                val song = withTimeoutOrNull(5_000) {
                    musicRepository.get().getSong(songId).first()
                } ?: return@launch
                if (song.title.isBlank() || song.artist.isBlank()) return@launch
                val listen = PendingListen(
                    artist = song.artist,
                    title = song.title,
                    album = song.album.takeIf { it.isNotBlank() },
                    listenedAtSeconds = ((endedAtEpochMs - listenedMs).coerceAtLeast(0L)) / 1000L
                )
                queueMutex.withLock {
                    val pending = readQueue() + listen
                    writeQueue(submitAll(token, pending))
                }
            }.onFailure { Timber.tag(TAG).w(it, "Scrobble failed for song=%s", songId) }
        }
    }

    /** Retries anything left in the queue, e.g. after the user saves a token. */
    fun flushPending() {
        scope.launch {
            runCatching {
                val token = userPreferencesRepository.listenBrainzTokenFlow.first()
                if (token.isBlank()) return@launch
                queueMutex.withLock { writeQueue(submitAll(token, readQueue())) }
            }
        }
    }

    /** Sends listens oldest-first; stops at the first failure and returns what is left. */
    private suspend fun submitAll(token: String, pending: List<PendingListen>): List<PendingListen> {
        pending.forEachIndexed { index, listen ->
            val ok = listenBrainzRepository.submitScrobble(
                userToken = token,
                artistName = listen.artist,
                trackName = listen.title,
                releaseName = listen.album,
                recordingMbid = null,
                listenedAtSeconds = listen.listenedAtSeconds
            )
            if (!ok) return pending.drop(index)
        }
        return emptyList()
    }

    private suspend fun readQueue(): List<PendingListen> {
        val raw = userPreferencesRepository.listenBrainzPendingFlow.first()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                PendingListen(
                    artist = o.getString("a"),
                    title = o.getString("t"),
                    album = o.optString("r").takeIf { it.isNotBlank() },
                    listenedAtSeconds = o.getLong("ts")
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun writeQueue(queue: List<PendingListen>) {
        val trimmed = queue.takeLast(MAX_PENDING)
        val array = JSONArray()
        trimmed.forEach { listen ->
            array.put(
                JSONObject()
                    .put("a", listen.artist)
                    .put("t", listen.title)
                    .put("r", listen.album ?: "")
                    .put("ts", listen.listenedAtSeconds)
            )
        }
        userPreferencesRepository.setListenBrainzPending(if (trimmed.isEmpty()) "" else array.toString())
    }

    private data class PendingListen(
        val artist: String,
        val title: String,
        val album: String?,
        val listenedAtSeconds: Long,
    )

    companion object {
        private const val TAG = "Scrobbler"
        private const val MIN_TRACK_MS = 30_000L
        private const val MAX_REQUIRED_LISTEN_MS = 4 * 60_000L
        private const val MAX_PENDING = 500

        fun qualifies(listenedMs: Long, durationMs: Long): Boolean {
            if (durationMs in 1 until MIN_TRACK_MS) return false
            val required = if (durationMs > 0) minOf(durationMs / 2, MAX_REQUIRED_LISTEN_MS) else MAX_REQUIRED_LISTEN_MS
            return listenedMs >= required
        }
    }
}
