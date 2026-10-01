package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface SearchResultItem {
    data class SongItem(val song: Song, val artistBrowseId: String? = null) : SearchResultItem
    data class AlbumItem(val album: Album, val browseId: String? = null) : SearchResultItem
    data class ArtistItem(val artist: Artist, val browseId: String? = null) : SearchResultItem
    data class PlaylistItem(val playlist: Playlist, val browseId: String? = null) : SearchResultItem
    /** A friend matched by name, with the playlists we know for them (saved + public). */
    data class FriendItem(
        val friendId: String,
        val name: String,
        val avatarUrl: String? = null,
        val source: String = "",
        val playlists: List<Playlist> = emptyList()
    ) : SearchResultItem
}
