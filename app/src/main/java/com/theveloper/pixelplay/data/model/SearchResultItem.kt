package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface SearchResultItem {
    data class SongItem(val song: Song, val artistBrowseId: String? = null) : SearchResultItem
    data class AlbumItem(val album: Album, val browseId: String? = null) : SearchResultItem
    data class ArtistItem(val artist: Artist, val browseId: String? = null) : SearchResultItem
    data class PlaylistItem(val playlist: Playlist, val browseId: String? = null) : SearchResultItem
}
