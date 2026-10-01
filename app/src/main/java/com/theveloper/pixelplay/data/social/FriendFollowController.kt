package com.theveloper.pixelplay.data.social

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.spotify.SpotifyTrack
import com.theveloper.pixelplay.data.spotify.toSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** A friend's song as something PixelPlayer can play (matched to audio when it starts), or null. */
fun FriendTrack.toPlayableSong(): Song? {
    val uri = trackUri ?: return null
    if (!uri.startsWith("spotify:track:") || title.isBlank()) return null
    return SpotifyTrack(
        id = uri.substringAfterLast(':'), title = title, artistName = artist.ifBlank { "Unknown artist" },
        albumName = "", durationMs = (durationMs ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        isrc = null, coverUrl = coverUrl
    ).toSong()
}

/**
 * "Follow" a friend: every new song they start is handed to the player as Play next, so you
 * listen along. Lives app-wide (not in a screen) so it keeps going after the Friends card is
 * closed; it keeps friend activity polling while active and stops by itself once the friend
 * has been quiet for [IDLE_STOP_MS].
 */
@Singleton
class FriendFollowController @Inject constructor(
    private val activity: FriendActivityRepository,
) {
    data class Session(val friendId: String, val friendName: String, val queued: Int = 0)

    // One thread: the follow loop's small bits of state are touched by two child coroutines.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _newSongs = MutableSharedFlow<Song>(extraBufferCapacity = 16)
    /** Songs to queue as Play next (collected by the Activity, which owns the player). */
    val newSongs: SharedFlow<Song> = _newSongs.asSharedFlow()

    private var job: Job? = null

    fun isFollowing(friendId: String): Boolean = _session.value?.friendId == friendId

    fun follow(friendId: String, friendName: String) {
        stop()
        _session.value = Session(friendId, friendName)
        job = scope.launch {
            // Friend activity only polls while someone collects it.
            launch { activity.polling.collect { } }
            var last: FriendTrack? = null
            var lastActiveAt = System.currentTimeMillis()
            launch {
                while (isActive) {
                    delay(60_000)
                    if (System.currentTimeMillis() - lastActiveAt > IDLE_STOP_MS) stop()
                }
            }
            activity.live.collect { live ->
                val status = live[friendId] ?: return@collect
                val track = status.track ?: return@collect
                if (!status.isPlaying) return@collect
                lastActiveAt = System.currentTimeMillis()
                val previous = last
                // Same song + same start (±30 s) is the same play seen again on a later poll.
                val isNew = previous == null || previous.key != track.key || abs(previous.playedAt - track.playedAt) >= 30_000
                if (!isNew) return@collect
                last = track
                val song = track.toPlayableSong() ?: return@collect
                val session = _session.value
                FriendQueueAttribution.tag(listOf(song.id), friendId, session?.friendName ?: friendName,
                    activity.profiles.value[friendId]?.avatarUrl)
                _newSongs.emit(song)
                _session.update { it?.copy(queued = it.queued + 1) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _session.value = null
    }

    private companion object {
        const val IDLE_STOP_MS = 15L * 60 * 1000
    }
}
