package com.theveloper.pixelplay.data.search

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MusicSearchQueryTest {
    @Test fun `natural tempo query defaults to five BPM tolerance`() {
        val query = MusicSearchQuery.parse("find me songs around 120 bpm")
        assertEquals(115f..125f, query.tempo)
        assertEquals("", query.terms)
        assertEquals("120 bpm songs", query.onlineQuery)
    }
    @Test fun `range keeps genre and normalizes reversed bounds`() {
        val query = MusicSearchQuery.parse("rock songs between 130–110 BPM")
        assertEquals(110f..130f, query.tempo)
        assertEquals("rock", query.terms)
    }
    @Test fun `tempo spelling from user and decimal tempos are understood`() {
        assertEquals(115f..125f, MusicSearchQuery.parse("songs with 120 bpi").tempo)
        assertEquals(115.5f..125.5f, MusicSearchQuery.parse("120.5 beats per minute").tempo)
    }
    @Test fun `numbers and invalid tempos stay literal`() {
        assertNull(MusicSearchQuery.parse("Summer of 69").tempo)
        assertNull(MusicSearchQuery.parse("999 bpm").tempo)
        assertEquals("1999", MusicSearchQuery.parse("1999").terms)
    }
    @Test fun `quoted and explicit lyric snippets preserve words`() {
        listOf("lyrics: hello from the other side", "song that goes hello from the other side", "\"hello from the other side\"").forEach {
            val query = MusicSearchQuery.parse(it)
            assertTrue(query.lyrics)
            assertEquals("hello from the other side", query.terms)
        }
    }
    @Test fun `wildcards are literal in database patterns`() {
        assertEquals("%100\\%\\_\\\\%", MusicSearchQuery.likePattern("100%_\\"))
    }
}
