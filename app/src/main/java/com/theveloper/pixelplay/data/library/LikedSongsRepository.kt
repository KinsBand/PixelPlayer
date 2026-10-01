package com.theveloper.pixelplay.data.library

import com.theveloper.pixelplay.data.accounts.ConnectedLibraryRepository
import com.theveloper.pixelplay.data.accounts.mergeLikedSongs
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.presentation.library.isFavoritesPlaylistName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one "Liked Songs" list every screen reads (Home's Recently Liked, the Playlists tab's
 * Your Music card, the Your Music screen): local favourites, YouTube Music / Spotify / Apple
 * Music likes, and songs in any playlist named like "Favourites", with duplicates removed
 * (see [mergeLikedSongs]).
 *
 * Order: songs liked in PixelPlayer come first, newest like first (the favourites table keeps
 * the time of each like). Platform likes follow in the platform's own order, which is newest
 * first for Spotify and YouTube Music. Favourites-playlist songs come last.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LikedSongsRepository @Inject constructor(
    music: MusicRepository,
    connected: ConnectedLibraryRepository,
    playlistPreferences: PlaylistPreferencesRepository,
    removals: YourMusicRemovals,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Songs from local / connected playlists called "Favourites", "My Favorites", "Liked"... */
    private val favoritesPlaylistSongs: Flow<List<Song>> = combine(
        playlistPreferences.userPlaylistsFlow.onStart { emit(emptyList()) }.catch { emit(emptyList()) },
        connected.snapshot
    ) { local, snapshot ->
        val localIds = local.filter { isFavoritesPlaylistName(it.name) }.flatMap { it.songIds }.distinct()
        val connectedSongs = snapshot.playlists
            .filter { it.friendId == null && isFavoritesPlaylistName(it.title) }
            .flatMap { it.songs }
        localIds to connectedSongs
    }.distinctUntilChanged().flatMapLatest { (localIds, connectedSongs) ->
        if (localIds.isEmpty()) flowOf(connectedSongs)
        else music.getSongsByIds(localIds).map { it + connectedSongs }
    }.catch { emit(emptyList()) }

    /** Every liked song, newest like first, each song once. */
    val likedSongs: Flow<List<Song>> = combine(
        connected.likedSongs.onStart { emit(emptyList()) }.catch { emit(emptyList()) },
        music.getFavoriteSongIdsByRecentFlow().onStart { emit(emptyList()) }.catch { emit(emptyList()) },
        favoritesPlaylistSongs.onStart { emit(emptyList()) },
        removals.keys
    ) { liked, recentIds, favoritesPlaylist, removed ->
        val rank = HashMap<String, Int>(recentIds.size * 2)
        recentIds.forEachIndexed { index, id -> rank.putIfAbsent(id, index) }
        // A downloaded copy may carry a different id than the one that was liked; fall back to
        // the YouTube id form so it keeps its place.
        fun rankOf(song: Song): Int? = rank[song.id] ?: song.youtubeId?.let { rank["yt_$it"] }
        val (inApp, platform) = liked.partition { rankOf(it) != null }
        val ordered = inApp.sortedBy { rankOf(it) } + platform + favoritesPlaylist
        // Songs deleted from Your Music stay hidden even if a service still reports them liked.
        mergeLikedSongs(ordered.filter {
            (it.contentUriString.isNotBlank() || it.youtubeId != null) && !removals.isRemoved(it, removed)
        })
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
}
