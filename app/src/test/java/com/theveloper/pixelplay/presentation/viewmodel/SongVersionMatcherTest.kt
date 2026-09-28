package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SongVersionMatcherTest {

    private fun song(id: String, title: String, artist: String, youtubeId: String? = null) = Song(
        id = id,
        title = title,
        artist = artist,
        artistId = 0L,
        album = "",
        albumId = 0L,
        path = "",
        contentUriString = "",
        albumArtUriString = null,
        duration = 200_000L,
        youtubeId = youtubeId
    )

    @Test
    fun `online titles with artist prefix or filler match the base title`() {
        assertTrue(SongVersionMatcher.titleMatchesBase("Yellow (Live in Buenos Aires)", "yellow", "Coldplay"))
        assertTrue(SongVersionMatcher.titleMatchesBase("Coldplay - Yellow (Official Video)", "yellow", "Coldplay"))
        assertTrue(SongVersionMatcher.titleMatchesBase("Yellow - Remastered 2011", "yellow", "Coldplay"))
    }

    @Test
    fun `a longer title that only contains the base does not match`() {
        assertFalse(SongVersionMatcher.titleMatchesBase("I Love You Always", "love", "Someone"))
        assertFalse(SongVersionMatcher.titleMatchesBase("Mellow Yellow", "yellow", "Coldplay"))
    }

    @Test
    fun `online versions skip listed songs, put the same artist first and mark covers`() {
        val current = song("1", "Yellow", "Coldplay")
        val candidates = listOf(
            song("yt:a", "Yellow", "Some Busker", youtubeId = "a"),
            song("yt:b", "Yellow (Live)", "Coldplay", youtubeId = "b"),
            song("yt:c", "Yellow", "Coldplay", youtubeId = "c"),
            song("yt:c2", "Yellow", "Coldplay", youtubeId = "c"),   // duplicate video
            song("yt:d", "Fix You", "Coldplay", youtubeId = "d"),   // different song
            song("yt:e", "Yellow", "Coldplay", youtubeId = "e"),    // already in the local list
        )
        val result = SongVersionMatcher.orderOnlineVersions(current, candidates, excludeIds = setOf("1", "e"))

        assertEquals(listOf("c", "b", "a"), result.map { it.song.youtubeId })
        assertNull(result[0].tag)
        assertEquals("Live", result[1].tag)
        assertEquals("Cover", result[2].tag)
        assertTrue(result.none { it.isOriginal })
    }
}
