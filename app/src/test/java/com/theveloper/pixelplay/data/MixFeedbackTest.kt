package com.theveloper.pixelplay.data

import android.content.Context
import android.content.SharedPreferences
import com.theveloper.pixelplay.data.model.Song
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MixFeedbackTest {
    @Test fun `feedback survives reload and excludes another provider alias`() {
        val saved = mutableMapOf<String, Set<String>>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putStringSet(any(), any()) } answers {
            saved[firstArg()] = secondArg<Set<String>>().toSet()
            editor
        }
        val preferences = mockk<SharedPreferences> {
            every { getStringSet(any(), any()) } answers { saved[firstArg()] ?: emptySet() }
            every { edit() } returns editor
        }
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns preferences
        }
        val song = Song.emptySong().copy(id = "spotify_track", title = "Title", artist = "Artist", youtubeId = "abcdefghijk")
        MixFeedback(context).dislike(song)
        val reloaded = MixFeedback(context)
        assertTrue(reloaded.isDisliked(song.copy(id = "yt_alias", title = "Different provider title")))
        assertTrue(reloaded.isDisliked(song.copy(youtubeId = null, title = " TITLE ", artist = "artist")))
        assertEquals(0, reloaded.penalty(song.copy(title = "Another song")))
        assertFalse(reloaded.isDisliked(song.copy(title = "Another song", youtubeId = null)))
        assertFalse(reloaded.isDisliked(song, "smart"))
        reloaded.dislike(song, "smart")
        assertTrue(reloaded.isDisliked(song, "smart"))
        assertTrue(reloaded.undo())
        assertFalse(reloaded.isDisliked(song, "smart"))
        assertTrue(reloaded.isDisliked(song, "normal"))
        reloaded.dislike(song, null)
        assertTrue(reloaded.isDisliked(song, "any-mix"))
        assertTrue(reloaded.undo())
        assertFalse(reloaded.isDisliked(song, "any-mix"))
    }
}
