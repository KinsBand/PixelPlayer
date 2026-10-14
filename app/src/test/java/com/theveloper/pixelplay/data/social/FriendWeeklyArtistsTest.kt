package com.theveloper.pixelplay.data.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Guards the history sheet's artist pictures: one per main artist, most played first, newest cover. */
class FriendWeeklyArtistsTest {
    private fun play(artist: String, at: Long, cover: String? = "c$at") = FriendTrack(title = "t$at", artist = artist, coverUrl = cover, playedAt = at)

    @Test fun `features collapse into the main artist`() {
        val artists = FriendWeeklyArtists.from(listOf(play("Dua Lipa feat. DaBaby", 1), play("dua lipa", 2), play("Lorde", 3)))
        assertEquals(listOf("dua lipa", "Lorde"), artists.map { it.name })
        assertEquals(2, artists.first().plays)
    }

    @Test fun `newest play gives the cover`() {
        val artists = FriendWeeklyArtists.from(listOf(play("A", 1, "old"), play("A", 5, "new")))
        assertEquals("new", artists.single().coverUrl)
    }

    @Test fun `ties break by most recent`() {
        val artists = FriendWeeklyArtists.from(listOf(play("Old", 1), play("New", 9)))
        assertEquals(listOf("New", "Old"), artists.map { it.name })
    }

    @Test fun `blank artists are skipped`() {
        assertEquals(0, FriendWeeklyArtists.from(listOf(play("", 1), play("  ", 2))).size)
    }
}
