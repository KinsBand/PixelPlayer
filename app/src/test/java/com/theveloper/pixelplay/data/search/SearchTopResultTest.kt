package com.theveloper.pixelplay.data.search

import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchTopResultTest {

    private fun songItem(id: String, title: String, artist: String) =
        SearchResultItem.SongItem(Song.emptySong().copy(id = id, title = title, artist = artist))

    private fun artistItem(id: Long, name: String) = SearchResultItem.ArtistItem(Artist(id, name, 0))

    @Test fun `scores ignore case, accents, punctuation and spacing`() {
        assertEquals(1.0, SearchTopResult.score("Radiohead", "radiohead"))
        assertEquals(1.0, SearchTopResult.score("beyonce", "Beyoncé"))
        assertEquals(0.99, SearchTopResult.score("acdc", "AC/DC"))
        assertEquals(0.97, SearchTopResult.score("cure the", "The Cure"))
        assertTrue(SearchTopResult.score("radio", "Radiohead") < SearchTopResult.THRESHOLD)
    }

    @Test fun `exact artist is the top result when the results have their songs`() {
        val artist = artistItem(1, "Radiohead")
        val pick = SearchTopResult.pick("radiohead", listOf(songItem("s", "Creep", "Radiohead"), artist))
        assertEquals(artist, pick?.item)
    }

    @Test fun `a song beats a same-named artist that has no songs in the results`() {
        val song = songItem("s", "Creep", "Radiohead")
        val pick = SearchTopResult.pick("creep", listOf(artistItem(2, "Creep"), song))
        assertEquals(song, pick?.item)
    }

    @Test fun `title plus artist in either order matches a song`() {
        val song = songItem("s", "Creep", "Radiohead")
        assertEquals(song, SearchTopResult.pick("creep radiohead", listOf(song))?.item)
        assertEquals(song, SearchTopResult.pick("Radiohead Creep", listOf(song))?.item)
    }

    @Test fun `video titles are cleaned before matching`() {
        val song = songItem("s", "Radiohead - Creep (Official Music Video)", "Radiohead")
        assertEquals(song, SearchTopResult.pick("creep", listOf(song))?.item)
    }

    @Test fun `no top result below the threshold`() {
        assertNull(SearchTopResult.pick("radio", listOf(artistItem(1, "Radiohead"), songItem("s", "Creep", "Radiohead"))))
        assertNull(SearchTopResult.pick("", listOf(artistItem(1, "Radiohead"))))
    }
}
