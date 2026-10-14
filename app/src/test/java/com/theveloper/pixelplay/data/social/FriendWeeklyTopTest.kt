package com.theveloper.pixelplay.data.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Guards the friend sheet's Top songs: grouped by song, most plays first, newest play wins ties. */
class FriendWeeklyTopTest {
    private fun play(title: String, at: Long, uri: String? = null) =
        FriendTrack(title = title, artist = "A", trackUri = uri, playedAt = at)

    @Test fun `most played first with play counts`() {
        val top = FriendWeeklyTop.topSongs(listOf(play("x", 1), play("y", 2), play("x", 3)))
        assertEquals(listOf("x" to 2, "y" to 1), top.map { it.track.title to it.plays })
    }

    @Test fun `newest play is kept and breaks ties`() {
        val top = FriendWeeklyTop.topSongs(listOf(play("old", 1), play("new", 9)))
        assertEquals(listOf("new", "old"), top.map { it.track.title })
        val same = FriendWeeklyTop.topSongs(listOf(play("x", 1), play("x", 7)))
        assertEquals(7L, same.single().track.playedAt)
    }

    @Test fun `same uri groups even when titles differ`() {
        val top = FriendWeeklyTop.topSongs(listOf(play("Song", 1, "spotify:track:1"), play("Song - Remaster", 2, "spotify:track:1")))
        assertEquals(2, top.single().plays)
    }

    @Test fun `limit applies`() {
        assertEquals(3, FriendWeeklyTop.topSongs((1..10).map { play("s$it", it.toLong()) }, limit = 3).size)
    }
}
