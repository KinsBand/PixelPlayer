package com.theveloper.pixelplay.data.artist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistAiAssistantTest {

    private val engine = ArtistAiEngine()

    private val sampleBio = """
        Radiohead are an English rock band formed in Abingdon, Oxfordshire, in 1985.
        The band consists of Thom Yorke (vocals, guitar, piano), brothers Jonny Greenwood (lead guitar, keys)
        and Colin Greenwood (bass), Ed O'Brien (guitar, backing vocals) and Philip Selway (drums).
        They have sold more than 30 million albums worldwide. Their landmark 1997 album OK Computer
        and 2000 album Kid A brought worldwide critical acclaim and pioneered electronic experimental rock.
        Read more on Last.fm. User-contributed text is available under the Creative Commons By-SA License.
    """.trimIndent()

    @Test
    fun testSanitizeBio() {
        val dirty = "<p>Some bio with <a href=\"https://last.fm\">links</a>. Read more on Last.fm.</p>"
        val cleaned = engine.sanitizeBio(dirty)
        assertNotNull(cleaned)
        assertFalse(cleaned!!.contains("<a"))
        assertFalse(cleaned.contains("Read more on Last.fm"))
    }

    @Test
    fun testMembersQuery() {
        val answer = engine.query("Radiohead", sampleBio, "Who are the band members?")
        assertTrue(answer.contains("Thom Yorke") || answer.contains("Jonny Greenwood"))
    }

    @Test
    fun testOriginQuery() {
        val answer = engine.query("Radiohead", sampleBio, "Where are they from and when did they form?")
        assertTrue(answer.contains("1985") || answer.contains("Abingdon") || answer.contains("Oxfordshire"))
    }

    @Test
    fun testGenreQuery() {
        val genres = listOf("Alternative Rock", "Art Rock", "Electronic")
        val answer = engine.query("Radiohead", sampleBio, "What genres do they play?", genres = genres)
        assertTrue(answer.contains("Alternative Rock") || answer.contains("rock", ignoreCase = true))
    }

    @Test
    fun testDiscographyQuery() {
        val answer = engine.query("Radiohead", sampleBio, "What are their famous albums?")
        assertTrue(answer.contains("OK Computer") || answer.contains("Kid A") || answer.contains("album"))
    }

    @Test
    fun testGeneralSummary() {
        val answer = engine.query("Radiohead", sampleBio, "Tell me about them")
        assertTrue(answer.contains("Radiohead"))
    }

    @Test
    fun testEmptyBioFallback() {
        val answer = engine.query("Unknown Artist", null, "Tell me about them")
        assertTrue(answer.contains("don't have enough local biographical data") || answer.contains("Unknown Artist"))
    }
}
