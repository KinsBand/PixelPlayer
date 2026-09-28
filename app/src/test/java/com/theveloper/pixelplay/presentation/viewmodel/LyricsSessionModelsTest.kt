package com.theveloper.pixelplay.presentation.viewmodel

import androidx.media3.common.Player
import com.theveloper.pixelplay.data.SongReaction
import com.theveloper.pixelplay.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LyricsSessionModelsTest {

    private fun song(id: String) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        genre = "Rock",
        albumArtUriString = null,
        artistId = 1L,
        albumId = 1L,
        contentUriString = "content://dummy/$id",
        duration = 180_000L,
        bitrate = null,
        sampleRate = null,
        album = "Album",
        path = "path",
        mimeType = "audio/mpeg",
        trackNumber = 0,
        discNumber = null
    )

    private val queue = listOf(song("a"), song("b"), song("c"))

    @Test
    fun `next up is the entry after the current one`() {
        assertEquals("b", resolveNextUpSong(queue, "a", 0, Player.REPEAT_MODE_OFF)?.id)
    }

    @Test
    fun `end of queue has no next song unless repeat all`() {
        assertNull(resolveNextUpSong(queue, "c", 2, Player.REPEAT_MODE_OFF))
        assertNull(resolveNextUpSong(queue, "c", 2, Player.REPEAT_MODE_ONE))
        assertEquals("a", resolveNextUpSong(queue, "c", 2, Player.REPEAT_MODE_ALL)?.id)
    }

    @Test
    fun `a stale index falls back to looking the song up`() {
        assertEquals("c", resolveNextUpSong(queue, "b", 0, Player.REPEAT_MODE_OFF)?.id)
        assertNull(resolveNextUpSong(queue, "missing", 0, Player.REPEAT_MODE_OFF))
    }

    @Test
    fun `single song with repeat all does not point at itself`() {
        assertNull(resolveNextUpSong(listOf(song("a")), "a", 0, Player.REPEAT_MODE_ALL))
    }

    @Test
    fun `play soon goes right after the current song when nothing is lined up`() {
        assertEquals(3, playSoonInsertionIndex(currentIndex = 2, itemCount = 10) { false })
    }

    @Test
    fun `play soon goes after songs added with play next`() {
        val priority = setOf(3)
        assertEquals(4, playSoonInsertionIndex(currentIndex = 2, itemCount = 10) { it in priority })
    }

    @Test
    fun `play soon never lands more than three places ahead`() {
        assertEquals(5, playSoonInsertionIndex(currentIndex = 2, itemCount = 10) { true })
    }

    @Test
    fun `play soon at the end of the queue appends`() {
        assertEquals(3, playSoonInsertionIndex(currentIndex = 2, itemCount = 3) { true })
        assertEquals(5, playSoonInsertionIndex(currentIndex = -1, itemCount = 5) { false })
    }

    @Test
    fun `reactions keep separate axes and polarities`() {
        assertEquals(SongReaction.Axis.SONG_PREFERENCE, SongReaction.LOVE.axis)
        assertEquals(SongReaction.Axis.SESSION_VIBE, SongReaction.FIRE.axis)
        assertEquals(SongReaction.Axis.SESSION_VIBE, SongReaction.NOT_THE_VIBE.axis)
        assertEquals(SongReaction.Axis.ENGAGEMENT, SongReaction.BORING.axis)
        assertTrue(SongReaction.positive.all { it.isPositive })
        assertFalse(SongReaction.negative.any { it.isPositive })
        assertTrue(SongReaction.DISLIKE.signedWeight < SongReaction.NOT_THE_VIBE.signedWeight)
        assertTrue(SongReaction.LOVE.signedWeight > SongReaction.ALRIGHT.signedWeight)
    }
}
