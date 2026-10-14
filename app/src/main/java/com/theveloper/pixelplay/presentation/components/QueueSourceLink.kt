package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.playback.NowPlayingPlaylist
import com.theveloper.pixelplay.data.social.FRIENDS_MIX_QUEUE_NAME
import com.theveloper.pixelplay.presentation.navigation.Screen

/** Where tapping the queue's source name in the queue sheet goes. */
data class QueueSourceLink(
    val route: String,
    /** Detail pattern to replace on the back stack (album → album doesn't stack up); null = plain push. */
    val patternToPop: String?,
)

object QueueSourceLinks {
    /**
     * Resolves the page the current queue came from, or null when it has none (Library, Liked
     * Songs, mixes, search results…). Checked in this order:
     * 1. A playlist (own, a friend's saved one, a connected one): [NowPlayingPlaylist] recorded its
     *    id for this exact queue name.
     * 2. Friends Mix → the Friends screen.
     * 3. An album: some queued song's album title equals the queue name (album plays are named
     *    after the album).
     * 4. An artist: some queued song's artist equals the queue name (artist plays are named after
     *    the artist).
     */
    fun resolve(queueName: String, playlist: NowPlayingPlaylist.Entry?, queue: List<Song>): QueueSourceLink? {
        val name = queueName.trim()
        if (name.isEmpty()) return null

        if (playlist != null && playlist.playlistId.isNotBlank() && playlist.queueName == queueName) {
            return QueueSourceLink(Screen.PlaylistDetail.createRoute(playlist.playlistId), Screen.PlaylistDetail.route)
        }
        if (name == FRIENDS_MIX_QUEUE_NAME) return QueueSourceLink(Screen.Friends.route, null)

        queue.firstOrNull { it.album.trim().equals(name, ignoreCase = true) }?.let { song ->
            Screen.AlbumDetail.createRouteForSong(song)?.let { return QueueSourceLink(it, Screen.AlbumDetail.route) }
        }

        for (song in queue) {
            if (song.artist.trim().equals(name, ignoreCase = true)) {
                return QueueSourceLink(Screen.ArtistDetail.createRouteForSong(song), Screen.ArtistDetail.route)
            }
            if (song.artists.any { it.name.trim().equals(name, ignoreCase = true) }) {
                return QueueSourceLink(Screen.ArtistDetail.createRouteForName(name), Screen.ArtistDetail.route)
            }
        }
        return null
    }
}
