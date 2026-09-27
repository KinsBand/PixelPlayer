package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.model.Song
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PersonalizedQuickPicksTest {
    private fun song(id: String, artist: String = "Artist") = Song(id, id, artist, 1,
        album = "Album", albumId = 1, path = "", contentUriString = "youtube://$id",
        albumArtUriString = null, duration = 200000)

    @Test fun `first page includes unheard music related to recent listening`() {
        val recent = (1..3).map { song("heard$it") }
        val discovery = song("new")
        val picks = personalizedQuickPicks(recent + discovery, recent, emptySet(), 4)
        assertEquals(discovery, picks[3])
        assertEquals(4, picks.distinctBy(::mixIdentity).size)
    }

    @Test fun `favorite and recent artists outrank unrelated library order`() {
        val favorite = song("favorite", "Loved")
        val unrelated = song("unrelated", "Other").copy(album = "Other")
        assertEquals(favorite, personalizedQuickPicks(listOf(unrelated, favorite), emptyList(), setOf(favorite.id), 1).first())
    }

    @Test fun `cold start and small pools are bounded and deduplicated`() {
        val track = song("one")
        assertEquals(listOf(track), personalizedQuickPicks(listOf(track, track.copy(id = "duplicate")), emptyList(), emptySet()))
        assertTrue(personalizedQuickPicks(listOf(track), emptyList(), emptySet(), 0).isEmpty())
    }
}
