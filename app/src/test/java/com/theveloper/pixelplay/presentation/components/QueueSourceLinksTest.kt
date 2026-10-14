package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.playback.NowPlayingPlaylist
import com.theveloper.pixelplay.data.social.FRIENDS_MIX_QUEUE_NAME
import com.theveloper.pixelplay.presentation.navigation.Screen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The queue sheet's source name must only link where the queue really came from. */
class QueueSourceLinksTest {
    private fun song(id: String, album: String = "Album", albumId: Long = 7L, artist: String = "Artist") =
        Song.emptySong().copy(id = id, title = "T$id", album = album, albumId = albumId, artist = artist)

    @Test fun `playlist recorded for this queue name links to the playlist`() {
        val link = QueueSourceLinks.resolve("Road trip", NowPlayingPlaylist.Entry("p1", "Road trip"), listOf(song("1")))
        assertEquals(Screen.PlaylistDetail.createRoute("p1"), link?.route)
    }

    @Test fun `stale playlist entry for another queue name is ignored`() {
        val link = QueueSourceLinks.resolve("Liked Songs", NowPlayingPlaylist.Entry("p1", "Road trip"), listOf(song("1")))
        assertNull(link)
    }

    @Test fun `friends mix links to the friends screen`() {
        assertEquals(Screen.Friends.route, QueueSourceLinks.resolve(FRIENDS_MIX_QUEUE_NAME, null, emptyList())?.route)
    }

    @Test fun `queue named after a queued song's album links to that album`() {
        val link = QueueSourceLinks.resolve("Blue", null, listOf(song("1", album = "Other"), song("2", album = "Blue", albumId = 42L)))
        assertEquals(Screen.AlbumDetail.createRoute(42L), link?.route)
    }

    @Test fun `queue named after an artist links to the artist`() {
        val link = QueueSourceLinks.resolve("Artist", null, listOf(song("1", album = "X")))
        assertEquals(Screen.ArtistDetail.route, link?.patternToPop)
    }

    @Test fun `generic queue names have no link`() {
        assertNull(QueueSourceLinks.resolve("Library", null, listOf(song("1"))))
        assertNull(QueueSourceLinks.resolve("", null, listOf(song("1"))))
    }
}
