package com.theveloper.pixelplay.data.accounts

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.CreditsAndRelease
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConnectedLibraryTest {
    private fun song(id: String, title: String = "Song", duration: Long = 200000) = Song(id, title, "Artist", 1,
        album = "Album", albumId = 1, path = "", contentUriString = "youtube://$id", albumArtUriString = null, duration = duration)
    @Test fun `combines copies across providers without requiring a download`() {
        assertEquals(1, mergeLikedSongs(listOf(song("yt_1"), song("spotify_2", " song ", 201000))).size)
    }
    @Test fun `keeps different performances and unknown durations`() {
        assertEquals(4, mergeLikedSongs(listOf(song("1"), song("2", "Song (Live)"), song("3", duration = 220000), song("4", duration = 0))).size)
    }
    @Test fun `identical recording codes match despite metadata differences`() {
        val credits = CreditsAndRelease(isrc = "AU1234567890")
        assertEquals(1, mergeLikedSongs(listOf(song("1").copy(creditsAndRelease = credits), song("2", "Different label").copy(creditsAndRelease = credits))).size)
    }
    @Test fun `same video identity is only included once`() {
        assertEquals(1, mergeLikedSongs(listOf(song("1").copy(youtubeId = "abc"), song("2", "Other title").copy(youtubeId = "abc"))).size)
    }
}
