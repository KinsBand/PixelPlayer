package com.theveloper.pixelplay.data.recognition

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.recognition.ambient.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AmbientSongMatcherTest {
    private fun song(title: String, artist: String) = Song.emptySong().copy(title = title, artist = artist)
    @Test fun `wrong artist and substring matches are not suggested`() {
        val mention = ExtractedSongSuggestion("", "Hello", "Adele")
        assertNull(AmbientSongMatcher.bestMatch(listOf(song("Hello", "Lionel Richie")), mention))
        assertNull(AmbientSongMatcher.bestMatch(listOf(song("Hello Again", "Adele")), mention))
        assertNull(AmbientSongMatcher.bestMatch(listOf(song("Hello (Karaoke)", "Adele")), mention))
        assertEquals("Adele", AmbientSongMatcher.bestMatch(listOf(song("Hello", "Adele")), mention)?.artist)
    }
    @Test fun `conversational mention and punctuation are parsed`() {
        val mention = ConversationalIntentFilter.parse("Hey, I love Blinding Lights by The Weeknd!")
        assertEquals("blinding lights", mention?.cleanTitle)
        assertEquals("the weeknd", mention?.artist)
        assertEquals("hello", ConversationalIntentFilter.parse("Play Hello right now please!")?.cleanTitle)
        assertNull(ConversationalIntentFilter.parse("I love having dinner with friends"))
        assertNull(ConversationalIntentFilter.parse("Don't play Hello by Adele"))
        assertNull(ConversationalIntentFilter.parse("player one wins"))
    }
}
