package com.theveloper.pixelplay.data.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SpotifyPageTagsTest {

    @Test
    fun `track page gives title, musician and cover`() {
        val html = """
            <meta property="og:title" content="One More Time"/>
            <meta property="og:description" content="Listen to One More Time on Spotify. Song · Daft Punk · 2000."/>
            <meta name="music:musician_description" content="Daft Punk"/>
            <meta property="og:image" content="https://i.scdn.co/image/abc"/>
        """.trimIndent()
        val tags = SpotifyPageTags.parse(html)
        assertEquals("One More Time", tags.title)
        assertEquals("Daft Punk", tags.artist)
        assertEquals("https://i.scdn.co/image/abc", tags.image)
    }

    @Test
    fun `artist falls back to the description when the musician tag is missing`() {
        val html = """<meta property="og:title" content="Discovery"/>
            <meta property="og:description" content="Listen to Discovery on Spotify. Album · Daft Punk · 2001 · 14 songs."/>"""
        assertEquals("Daft Punk", SpotifyPageTags.parse(html).artist)
    }

    @Test
    fun `playlist page gives its owner and unescapes entities`() {
        val html = """<meta content="Rock &amp; Roll" property="og:title"/>
            <meta property="og:description" content="Listen on Spotify. Playlist · Sam · 50 items · 1 saves"/>"""
        val tags = SpotifyPageTags.parse(html)
        assertEquals("Rock & Roll", tags.title)
        assertEquals("Sam", tags.owner)
        assertNull(tags.artist)
    }
}
