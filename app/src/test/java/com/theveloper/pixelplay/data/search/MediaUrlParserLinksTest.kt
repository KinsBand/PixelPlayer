package com.theveloper.pixelplay.data.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The seven links from the roadmap (6.1) must each be recognised as the right kind of item. */
class MediaUrlParserLinksTest {

    private fun parse(url: String) = assertNotNull(MediaUrlParser.parse(url), url)!!

    @Test
    fun `spotify artist album track and playlist links with share parameters`() {
        parse("https://open.spotify.com/artist/4HVmXRMJawqE3RAt5bENQm?si=uLDZY7c_R-itjDLiOQkqng&utm_source=copy-link").let {
            assertEquals(MediaService.SPOTIFY, it.service); assertEquals(MediaTargetType.ARTIST, it.type); assertEquals("4HVmXRMJawqE3RAt5bENQm", it.id)
        }
        parse("https://open.spotify.com/album/1bdWiwXpRNy5lteQW9kfsN?si=srpBUJMNQJ6YWxlLb0EkGA&utm_source=copy-link").let {
            assertEquals(MediaTargetType.ALBUM, it.type); assertEquals("1bdWiwXpRNy5lteQW9kfsN", it.id)
        }
        parse("https://open.spotify.com/track/3XbsXsdK0ns2J63eomL3YS?si=1bbqmKBcQc-SIve2a1EHZA&utm_source=copy-link").let {
            assertEquals(MediaTargetType.TRACK, it.type); assertEquals("3XbsXsdK0ns2J63eomL3YS", it.id)
        }
        parse("https://open.spotify.com/playlist/5FHyIGtk9sUj3nhcLQOEps?si=1z9-4X-IQJS6V2LlIctXUw&utm_source=copy-link").let {
            assertEquals(MediaTargetType.PLAYLIST, it.type); assertEquals("5FHyIGtk9sUj3nhcLQOEps", it.id)
            assertEquals("https://open.spotify.com/playlist/5FHyIGtk9sUj3nhcLQOEps", it.cleanUrl)
        }
    }

    @Test
    fun `youtube music album song and playlist links`() {
        parse("https://music.youtube.com/playlist?list=OLAK5uy_mbvIEnieksjbbqfIH50bRyvCDRVJRcZfI&si=vRWPv1oekFUrnldz").let {
            assertEquals(MediaService.YOUTUBE_MUSIC, it.service); assertEquals(MediaTargetType.ALBUM, it.type)
            assertEquals("OLAK5uy_mbvIEnieksjbbqfIH50bRyvCDRVJRcZfI", it.id)
        }
        parse("https://music.youtube.com/watch?v=4YFu4dvMHHY&si=65wwpmGDkLIYy4OY").let {
            assertEquals(MediaTargetType.TRACK, it.type); assertEquals("4YFu4dvMHHY", it.id)
        }
        parse("https://music.youtube.com/playlist?list=PLSF3AfzmiKo8&si=V3mmV5P7ei1WBbtR").let {
            assertEquals(MediaTargetType.PLAYLIST, it.type); assertEquals("PLSF3AfzmiKo8", it.id)
        }
    }

    @Test
    fun `plain searches are not links`() {
        assertNull(MediaUrlParser.parse("daft punk around the world"))
        assertNull(MediaUrlParser.parse(""))
    }
}
