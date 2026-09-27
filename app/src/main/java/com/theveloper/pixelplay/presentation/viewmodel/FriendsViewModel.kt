package com.theveloper.pixelplay.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository
import com.theveloper.pixelplay.data.social.FriendActivityRepository
import com.theveloper.pixelplay.data.social.FriendPresence
import com.theveloper.pixelplay.data.social.FriendTrack
import com.theveloper.pixelplay.data.social.presence
import com.theveloper.pixelplay.data.spotify.toSong
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class FriendPlaylistUi(
    /** PixelPlayer playlist id when it's saved; null for a public playlist not saved yet. */
    val playlistId: String?,
    val remoteId: String,
    val source: String,
    val title: String,
    val coverUrl: String?,
    val songCount: Int,
    val durationMs: Long?,
)

@Immutable
data class FriendUi(
    val id: String,
    val name: String,
    /** Name on the platform, shown as a hint while renaming. */
    val platformName: String,
    val source: String,
    val avatarUrl: String?,
    val presence: FriendPresence,
    /** Current song when live, otherwise the last one seen. */
    val track: FriendTrack?,
    val playlists: List<FriendPlaylistUi>,
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val library: ConnectedLibraryRepository,
    private val activity: FriendActivityRepository,
) : ViewModel() {

    /** Re-judges live / "2 h ago" every 30 s even when no new data arrives. */
    private val clock = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }

    val friends: StateFlow<List<FriendUi>> = combine(library.snapshot, activity.profiles, activity.live, clock) { snapshot, profiles, live, now ->
        val saved = snapshot.playlists.filter { it.friendId != null }.groupBy { it.friendId!! }
        (saved.keys + profiles.keys).distinct().map { id ->
            val profile = profiles[id]
            val mine = saved[id].orEmpty()
            val platformName = profile?.displayName?.takeIf { it.isNotBlank() }
                ?: mine.firstOrNull()?.ownerName?.takeIf { it.isNotBlank() } ?: "Friend"
            val savedUi = mine.map { p ->
                FriendPlaylistUi(p.id, p.remoteId, p.source, p.title, p.coverUrl ?: p.songs.firstOrNull()?.albumArtUriString,
                    p.songs.size, p.songs.sumOf { it.duration }.takeIf { it > 0 })
            }
            val savedRemote = savedUi.mapTo(hashSetOf()) { it.source + ":" + it.remoteId }
            val publicUi = profile?.publicPlaylists.orEmpty()
                .filter { it.source + ":" + it.remoteId !in savedRemote }
                .map { FriendPlaylistUi(null, it.remoteId, it.source, it.title, it.coverUrl, it.trackCount, it.durationMs) }
            val status = live[id]
            FriendUi(
                id = id,
                name = snapshot.aliases[id] ?: platformName,
                platformName = platformName,
                source = id.substringBefore(':', "").takeIf { it == "SPOTIFY" || it == "YOUTUBE_MUSIC" || it == "APPLE_MUSIC" }
                    ?: mine.firstOrNull()?.source.orEmpty(),
                avatarUrl = profile?.avatarUrl,
                presence = status.presence(profile, now),
                track = status?.track ?: activity.history.value[id]?.firstOrNull(),
                playlists = savedUi + publicUi,
            )
        }.sortedWith(
            // Friends with playlists first, then who's listening, then most recent, then name.
            // (Sort keys must be Longs: mixing Int 0 with Long timestamps crashed the sort.)
            compareBy<FriendUi> { if (it.playlists.isEmpty()) 1 else 0 }
                .thenBy { it.presence.ordinal }
                .thenByDescending { it.track?.playedAt ?: 0L }
                .thenBy { it.name.lowercase() }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasLiveSource: Boolean get() = activity.hasSources

    /** Collect while friends are on screen to keep their status fresh. */
    val polling: Flow<Unit> get() = activity.polling

    val notice = MutableStateFlow<String?>(null)

    fun history(friendId: String): Flow<List<FriendTrack>> = activity.historyFor(friendId)

    fun rename(friendId: String, name: String) = launch { library.renameFriend(friendId, name) }

    fun addPlaylist(name: String, link: String, done: () -> Unit) = launch { library.addFriendPlaylist(name, link); done() }

    /** Saves a friend's public playlist into the library, then hands back its playlist id. */
    fun savePublicPlaylist(friend: FriendUi, playlist: FriendPlaylistUi, opened: (String) -> Unit) = launch {
        val link = com.theveloper.pixelplay.data.accounts.MusicSources.playlistUrl(playlist.source, playlist.remoteId)
            ?: "https://music.youtube.com/playlist?list=${playlist.remoteId}"
        opened(library.addFriendPlaylist(friend.name, link, knownFriendId = friend.id))
    }

    private val gathering = mutableSetOf<String>()

    /**
     * Opening a friend's row saves their public playlists into the library in the background,
     * so the songs are gathered and each playlist opens instantly with its real song count.
     */
    fun gatherPlaylists(friend: FriendUi) {
        val pending = friend.playlists.filter { it.playlistId == null && (it.source + ":" + it.remoteId) !in gathering }
        if (pending.isEmpty()) return
        pending.forEach { gathering += it.source + ":" + it.remoteId }
        viewModelScope.launch {
            for (playlist in pending) {
                val link = com.theveloper.pixelplay.data.accounts.MusicSources.playlistUrl(playlist.source, playlist.remoteId) ?: continue
                try { library.addFriendPlaylist(friend.name, link, knownFriendId = friend.id) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { gathering -= playlist.source + ":" + playlist.remoteId } // retried next time the row opens
            }
        }
    }

    /** A friend's song as something PixelPlayer can play (matched to audio when it starts), or null if unknown. */
    fun songFor(track: FriendTrack): com.theveloper.pixelplay.data.model.Song? {
        val uri = track.trackUri ?: return null
        if (!uri.startsWith("spotify:track:") || track.title.isBlank()) return null
        return com.theveloper.pixelplay.data.spotify.SpotifyTrack(
            id = uri.substringAfterLast(':'), title = track.title, artistName = track.artist.ifBlank { "Unknown artist" },
            albumName = "", durationMs = (track.durationMs ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            isrc = null, coverUrl = track.coverUrl
        ).toSong()
    }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { notice.value = e.message ?: "Please try again." }
    }
}
