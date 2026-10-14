package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface SearchResultItem {
    /**
     * A song result. Other versions of the same song found by the same search (live cuts,
     * remasters, acoustic takes, covers of it…) are folded into [versions] by
     * `SearchVersionGrouping`; the row shows them behind a "+N versions" pill.
     * [isOfficialArtist] = the song is by the artist the query names.
     */
    data class SongItem(
        val song: Song,
        val artistBrowseId: String? = null,
        val versions: List<Song> = emptyList(),
        val isOfficialArtist: Boolean = false
    ) : SearchResultItem
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
    /** A Spotify person (Profiles filter): Follow / Following / Friends right in the list. */
    data class ProfileItem(val person: com.theveloper.pixelplay.data.social.SocialPerson) : SearchResultItem
}
