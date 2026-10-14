package com.theveloper.pixelplay.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MixPromptParserTest {

    private fun parse(text: String) = MixPromptParser.parse(text, currentYear = 2026)

    @Test
    fun `plain name has no vibe`() {
        val intent = parse("Queen")
        assertFalse(intent.hasVibe)
        assertEquals(listOf("queen"), intent.freeWords)
    }

    @Test
    fun `tempo era genre and artist combine`() {
        val intent = parse("upbeat 80s rock like Queen")
        assertTrue(intent.hasVibe)
        assertNotNull(intent.bpm)
        assertTrue(120f in intent.bpm!!)
        assertEquals(1980..1989, intent.years)
        assertTrue("rock" in intent.genres)
        assertEquals(listOf("Queen"), intent.artists)
        assertEquals(listOf("upbeat", "80s", "rock", "like Queen"), intent.labels)
    }

    @Test
    fun `mood texture and artist`() {
        val intent = parse("slow sad acoustic like Bon Iver")
        assertEquals(true, intent.prefersMinor)
        assertNotNull(intent.valence)
        assertEquals(0.6f, intent.minAcousticness)
        assertEquals(listOf("Bon Iver"), intent.artists)
    }

    @Test
    fun `region and multi word genre`() {
        val intent = parse("fast aussie hip hop")
        assertTrue("australian" in intent.regions)
        assertTrue("hip hop" in intent.genres)
        assertTrue("rap" in intent.genres)
    }

    @Test
    fun `songs like with modifier`() {
        val intent = parse("songs like Mr Brightside but chiller")
        assertEquals(listOf("Mr Brightside"), intent.songs)
        assertNotNull(intent.energy)
        assertTrue(intent.energy!!.endInclusive <= 0.5f)
    }

    @Test
    fun `quoted song and exclusion`() {
        val intent = parse("chill songs like \"Yellow\" but no rap")
        assertEquals(listOf("Yellow"), intent.songs)
        assertTrue("rap" in intent.excludeGenres)
        assertTrue("no rap" in intent.labels)
    }

    @Test
    fun `explicit bpm and year range`() {
        val intent = parse("120 bpm happy pop from 2010 to 2015")
        assertTrue(120f in intent.bpm!!)
        assertEquals(2010..2015, intent.years)
        assertEquals(false, intent.prefersMinor)
    }

    @Test
    fun `trailing vibe words leave the artist phrase`() {
        val intent = parse("by Taylor Swift happy")
        assertEquals(listOf("Taylor Swift"), intent.artists)
        assertNotNull(intent.valence)
    }

    @Test
    fun `new means recent years`() {
        assertEquals(2023..2026, parse("new k-pop").years)
        assertNull(parse("k-pop").years)
    }

    @Test
    fun `merge fills only gaps`() {
        val local = parse("upbeat rock")
        val ai = PromptIntent(raw = "upbeat rock", years = 1980..1989, bpm = 60f..70f, artists = listOf("Queen"))
        val merged = local.mergedWith(ai)
        assertEquals(local.bpm, merged.bpm)
        assertEquals(1980..1989, merged.years)
        assertEquals(listOf("Queen"), merged.artists)
    }
}
