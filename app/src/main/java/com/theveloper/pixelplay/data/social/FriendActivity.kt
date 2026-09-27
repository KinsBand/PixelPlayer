package com.theveloper.pixelplay.data.social

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One song a friend played. [playedAt] = when it started (epoch ms). */
data class FriendTrack(
    val title: String = "",
    val artist: String = "",
    val coverUrl: String? = null,
    /** e.g. spotify:track:… — used to tell replays of the same song apart from new songs. */
    val trackUri: String? = null,
    val durationMs: Long? = null,
    val playedAt: Long = 0,
) {
    val key: String get() = trackUri?.takeIf { it.isNotBlank() } ?: "${title.lowercase()}|${artist.lowercase()}"
}

/** A public playlist a platform reports for a friend (may not be saved in PixelPlayer yet). */
data class FriendPublicPlaylist(
    val remoteId: String = "",
    val source: String = "",
    val title: String = "",
    val coverUrl: String? = null,
    val trackCount: Int = 0,
    val durationMs: Long? = null,
)

/** What a [FriendActivitySource] saw for one friend on one poll. */
data class FriendActivityReport(
    /** Same convention as playlists: "SPOTIFY:<userId>", "YOUTUBE_MUSIC:<channelId>". */
    val friendId: String,
    /** Name on the platform. A rename in PixelPlayer (alias) wins over this. */
    val displayName: String,
    val avatarUrl: String? = null,
    val track: FriendTrack? = null,
    /** True only when the platform says the song is playing right now. */
    val isPlaying: Boolean = false,
    /** Followed, but they don't share listening activity. */
    val activityHidden: Boolean = false,
    /** Null = unchanged / unknown. */
    val publicPlaylists: List<FriendPublicPlaylist>? = null,
)

/**
 * A platform that can tell what friends are playing. Bind implementations with
 * `@Binds @IntoSet` (see FriendActivityModule). The Spotify web-session feed is the first one.
 */
interface FriendActivitySource {
    val source: String
    suspend fun poll(): List<FriendActivityReport>
}

enum class FriendPresence { LISTENING_NOW, RECENT, OFFLINE, HIDDEN, UNKNOWN }

data class FriendProfile(val displayName: String = "", val avatarUrl: String? = null,
    val activityHidden: Boolean = false, val publicPlaylists: List<FriendPublicPlaylist>? = null)

data class FriendLive(val track: FriendTrack? = null, val isPlaying: Boolean = false, val seenAt: Long = 0)

private data class FriendActivityFile(
    val profiles: Map<String, FriendProfile>? = null,
    val live: Map<String, FriendLive>? = null,
    val history: Map<String, List<FriendTrack>>? = null,
)

const val FRIEND_HISTORY_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
private const val RECENT_WINDOW_MS = 30L * 60 * 1000
private const val MIN_TRACK_MS = 3L * 60 * 1000

/** Live status from the last report, judged at [now]. */
fun FriendLive?.presence(profile: FriendProfile?, now: Long): FriendPresence {
    val track = this?.track
    val playing = this?.isPlaying == true
    if (track == null) return if (profile?.activityHidden == true) FriendPresence.HIDDEN else FriendPresence.UNKNOWN
    val age = now - track.playedAt
    val playingWindow = maxOf(track.durationMs ?: 0L, MIN_TRACK_MS) + 60_000
    return when {
        playing && age < playingWindow -> FriendPresence.LISTENING_NOW
        age < RECENT_WINDOW_MS -> FriendPresence.RECENT
        else -> FriendPresence.OFFLINE
    }
}

/**
 * Keeps friends' live status and a rolling 7-day history. Platforms only expose "what's
 * playing now", so the history is built here from what PixelPlayer has seen while polling.
 */
@Singleton
class FriendActivityRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val sources: Set<@JvmSuppressWildcards FriendActivitySource>,
) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "friend-activity-v1.json"))
    private val gson = Gson()
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _profiles = MutableStateFlow<Map<String, FriendProfile>>(emptyMap())
    private val _live = MutableStateFlow<Map<String, FriendLive>>(emptyMap())
    private val _history = MutableStateFlow<Map<String, List<FriendTrack>>>(emptyMap())
    val profiles = _profiles.asStateFlow()
    val live = _live.asStateFlow()
    val history = _history.asStateFlow()

    /** False until a platform that reports friend activity is connected. */
    val hasSources: Boolean get() = sources.isNotEmpty()

    private val loaded = scope.async {
        lock.withLock {
            if (!file.baseFile.exists()) return@withLock
            try {
                val stored = file.openRead().bufferedReader().use { gson.fromJson(it, FriendActivityFile::class.java) }
                _profiles.value = stored.profiles.orEmpty()
                _live.value = stored.live.orEmpty()
                _history.value = prune(stored.history.orEmpty(), System.currentTimeMillis())
            } catch (_: Exception) { /* unreadable cache: start fresh, it's re-fetchable */ }
        }
    }

    fun historyFor(friendId: String): Flow<List<FriendTrack>> =
        history.map { it[friendId].orEmpty() }.distinctUntilChanged()

    /**
     * Polls every source while collected: 10 s while any friend is playing, 30 s otherwise,
     * 60 s after an error. Shared, so the dropdown and the history sheet don't double-poll.
     */
    val polling: Flow<Unit> = flow<Unit> {
        if (sources.isEmpty()) return@flow
        loaded.await()
        while (currentCoroutineContext().isActive) {
            var anyPlaying = false
            var failed = false
            for (source in sources) {
                try {
                    val reports = source.poll()
                    report(reports)
                    anyPlaying = anyPlaying || reports.any { it.isPlaying }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
            }
            delay(when { failed -> 60_000L; anyPlaying -> 10_000L; else -> 30_000L })
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(0), replay = 0)

    /** Entry point for sources (and tests): merges reports into status + history and saves. */
    suspend fun report(reports: List<FriendActivityReport>) = withContext(Dispatchers.IO) {
        if (reports.isEmpty()) return@withContext
        loaded.await()
        lock.withLock {
            val now = System.currentTimeMillis()
            val profiles = _profiles.value.toMutableMap()
            val live = _live.value.toMutableMap()
            val history = _history.value.toMutableMap()
            for (r in reports) {
                val old = profiles[r.friendId]
                profiles[r.friendId] = FriendProfile(
                    displayName = r.displayName.ifBlank { old?.displayName.orEmpty() },
                    avatarUrl = r.avatarUrl ?: old?.avatarUrl,
                    activityHidden = r.activityHidden,
                    publicPlaylists = r.publicPlaylists ?: old?.publicPlaylists,
                )
                live[r.friendId] = FriendLive(r.track, r.isPlaying, now)
                val track = r.track
                if (track != null) {
                    val list = history[r.friendId].orEmpty()
                    // Same song + same start (±30 s) = the same play seen again on a later poll.
                    val seen = list.any { it.key == track.key && kotlin.math.abs(it.playedAt - track.playedAt) < 30_000 }
                    if (!seen) history[r.friendId] = (listOf(track) + list).sortedByDescending { it.playedAt }
                }
            }
            val pruned = prune(history, now)
            save(FriendActivityFile(profiles, live, pruned))
            _profiles.value = profiles; _live.value = live; _history.value = pruned
        }
    }

    private fun prune(history: Map<String, List<FriendTrack>>, now: Long) =
        history.mapValues { (_, list) -> list.filter { now - it.playedAt <= FRIEND_HISTORY_WINDOW_MS } }
            .filterValues { it.isNotEmpty() }

    private fun save(value: FriendActivityFile) {
        val stream = file.startWrite()
        try { stream.write(gson.toJson(value).toByteArray()); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream) }
    }
}
