package com.theveloper.pixelplay.data.ai.nano

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NanoParsingTest {

    @Test
    fun `reads fenced json and drops invented artists`() {
        val intent = NanoMixIntentReader.parseAnswer(
            "upbeat 80s rock like queen",
            "```json\n{\"tempo\":\"fast\",\"energy\":\"high\",\"decades\":[1980],\"genres\":[\"rock\"],\"artists\":[\"Queen\",\"Journey\"]}```"
        )
        assertNotNull(intent)
        assertEquals(1980..1989, intent!!.years)
        assertEquals(listOf("Queen"), intent.artists)
        assertTrue("rock" in intent.genres)
    }

    @Test
    fun `country decade strings and mood`() {
        val intent = NanoMixIntentReader.parseAnswer(
            "aussie chill songs 2010s",
            "{\"energy\":\"low\",\"countries\":[\"australian\"],\"decades\":[\"2010s\"],\"mood\":\"happy\"}"
        )!!
        assertEquals(2010..2019, intent.years)
        assertTrue("australian" in intent.regions)
        assertNotNull(intent.valence)
    }

    @Test
    fun `garbage is null`() {
        assertNull(NanoMixIntentReader.parseAnswer("x", "nonsense"))
        assertNull(NanoMixIntentReader.parseAnswer("x", "{}"))
    }

    @Test
    fun `translation chunk must line up`() {
        assertEquals(listOf("hola", "adios"), NanoLyricsTranslator.parseChunk("{\"lines\":[\"1. hola\",\"adios\"]}", 2)?.lines)
        assertEquals(true, NanoLyricsTranslator.parseChunk("{\"same\":true}", 2)?.alreadyInTarget)
        assertNull(NanoLyricsTranslator.parseChunk("{\"lines\":[\"a\"]}", 2))
    }
}
