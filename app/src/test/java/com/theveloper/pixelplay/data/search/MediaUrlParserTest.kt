package com.theveloper.pixelplay.data.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MediaUrlParserTest {

    @Test
    fun `parse Spotify artist URL with query params`() {
        val input = "https://open.spotify.com/artist/4HVmXRMJawqE3RAt5bENQm?si=uLDZY7c_R-itjDLiOQkqng&utm_source=copy-link"
        val parsed = MediaUrlParser.parse(input)

        assertNotNull(parsed)
        assertEquals(MediaService.SPOTIFY, parsed?.service)
        assertEquals(MediaTargetType.ARTIST, parsed?.type)
        assertEquals("4HVmXRMJawqE3RAt5bENQm", parsed?.id)
        assertEquals("https://open.spotify.com/artist/4HVmXRMJawqE3RAt5bENQm", parsed?.cleanUrl)
    }

    @Test
    fun `parse Spotify album URL with query params`() {
        val input = "https://open.spotify.com/album/1bdWiwXpRNy5lteQW9kfsN?si=srpBUJMNQJ6YWxlLb0EkGA&utm_source=copy-link"
        val parsed = MediaUrlParser.parse(input)

        assertNotNull(parsed)
        assertEquals(MediaService.SPOTIFY, parsed?.service)
        assertEquals(MediaTargetType.ALBUM, parsed?.type)
        assertEquals("1bdWiwXpRNy5lteQW9kfsN", parsed?.id)
        assertEquals("https://open.spotify.com/album/1bdWiwXpRNy5lteQW9kfsN", parsed?.cleanUrl)
    }

    @Test
    fun `parse Spotify track URL with query params`() {
        val input = "https://open.spotify.com/track/3XbsXsdK0ns2J63eomL3YS?si=1bbqmKBcQc-SIve2a1EHZA&utm_source=copy-link"
        val parsed = MediaUrlParser.parse(input)

        assertNotNull(parsed)
        assertEquals(MediaService.SPOTIFY, parsed?.service)
        assertEquals(MediaTargetType.TRACK, parsed?.type)
        assertEquals("3XbsXsdK0ns2J63eomL3YS", parsed?.id)
        assertEquals("https://open.spotify.com/track/3XbsXsdK0ns2J63eomL3YS", parsed?.cleanUrl)
    }

    @Test
    fun `parse Spotify playlist URL with query params`() {
        val input = "https://open.spotify.com/playlist/5FHyIGtk9sUj3nhcLQOEps?si=1z9-4X-IQJS6V2LlIctXUw&utm_source=copy-link"
        val parsed = MediaUrlParser.parse(input)

        assertNotNull(parsed)
        assertEquals(MediaService.SPOTIFY, parsed?.service)
        assertEquals(MediaTargetType.PLAYLIST, parsed?.type)
        assertEquals("5FHyIGtk9sUj3nhcLQOEps", parsed?.id)
        assertEquals("https://open.spotify.com/playlist/5FHyIGtk9sUj3nhcLQOEps", parsed?.cleanUrl)
    }

    @Test
    fun `parse YouTube Music album URL`() {
        val input = "https://music.youtube.com/playlist?list=OLAK5uy_mbvIEnieksjbbqfIH50bRyvCDRVJRcZfI&si=vRWPv1oekFUrnldz"
        val parsed = MediaUrlParser.parse(input)

        assertNotNull(parsed)
        assertEquals(MediaService.YOUTUBE_MUSIC, parsed?.service)
        assertEquals(MediaTargetType.ALBUM, parsed?.type)
        assertEquals("OLAK5uy_mbvIEnieksjbbqfIH50bRyvCDRVJRcZfI", parsed?.id)
        assertEquals(
            "https://music.youtube.com/playlist?list=OLAK5uy_mbvIEnieksjbbqfIH50bRyvCDRVJRcZfI",
            parsed?.cleanUrl
        )
    }

    @Test
    fun `parse YouTube Music song URL`() {
        val input = "https://music.youtube.com/watch?v=4YFu4dvMHHY&si=65wwpmGDkLIYy4OY"
        val parsed = MediaUrlParser.parse(input)

        assertNotNull(parsed)
        assertEquals(MediaService.YOUTUBE_MUSIC, parsed?.service)
        assertEquals(MediaTargetType.TRACK, parsed?.type)
        assertEquals("4YFu4dvMHHY", parsed?.id)
        assertEquals("https://music.youtube.com/watch?v=4YFu4dvMHHY", parsed?.cleanUrl)
    }

    @Test
    fun `parse YouTube Music playlist URL`() {
        val input = "https://music.youtube.com/playlist?list=PLSF3AfzmiKo8&si=V3mmV5P7ei1WBbtR"
        val parsed = MediaUrlParser.parse(input)

        assertNotNull(parsed)
        assertEquals(MediaService.YOUTUBE_MUSIC, parsed?.service)
        assertEquals(MediaTargetType.PLAYLIST, parsed?.type)
        assertEquals("PLSF3AfzmiKo8", parsed?.id)
        assertEquals("https://music.youtube.com/playlist?list=PLSF3AfzmiKo8", parsed?.cleanUrl)
    }

    @Test
    fun `parse YouTube Music artist channel and browse URLs`() {
        val channelUrl = "https://music.youtube.com/channel/UC46IumvyP_y8sF8x4r_U0cA?si=test123"
        val channelParsed = MediaUrlParser.parse(channelUrl)
        assertNotNull(channelParsed)
        assertEquals(MediaService.YOUTUBE_MUSIC, channelParsed?.service)
        assertEquals(MediaTargetType.ARTIST, channelParsed?.type)
        assertEquals("UC46IumvyP_y8sF8x4r_U0cA", channelParsed?.id)
        assertEquals("https://music.youtube.com/channel/UC46IumvyP_y8sF8x4r_U0cA", channelParsed?.cleanUrl)

        val browseUrl = "https://music.youtube.com/browse/UC46IumvyP_y8sF8x4r_U0cA"
        val browseParsed = MediaUrlParser.parse(browseUrl)
        assertNotNull(browseParsed)
        assertEquals(MediaService.YOUTUBE_MUSIC, browseParsed?.service)
        assertEquals(MediaTargetType.ARTIST, browseParsed?.type)
        assertEquals("UC46IumvyP_y8sF8x4r_U0cA", browseParsed?.id)
        assertEquals("https://music.youtube.com/channel/UC46IumvyP_y8sF8x4r_U0cA", browseParsed?.cleanUrl)
    }

    @Test
    fun `parse YouTube track URL variations`() {
        val standardYt = MediaUrlParser.parse("https://www.youtube.com/watch?v=4YFu4dvMHHY&t=42s")
        assertNotNull(standardYt)
        assertEquals(MediaService.YOUTUBE_MUSIC, standardYt?.service)
        assertEquals(MediaTargetType.TRACK, standardYt?.type)
        assertEquals("4YFu4dvMHHY", standardYt?.id)
        assertEquals("https://music.youtube.com/watch?v=4YFu4dvMHHY", standardYt?.cleanUrl)

        val shortYt = MediaUrlParser.parse("https://youtu.be/4YFu4dvMHHY?si=65wwpmGDkLIYy4OY")
        assertNotNull(shortYt)
        assertEquals(MediaService.YOUTUBE_MUSIC, shortYt?.service)
        assertEquals(MediaTargetType.TRACK, shortYt?.type)
        assertEquals("4YFu4dvMHHY", shortYt?.id)
        assertEquals("https://music.youtube.com/watch?v=4YFu4dvMHHY", shortYt?.cleanUrl)

        val mobileYt = MediaUrlParser.parse("https://m.youtube.com/watch?v=4YFu4dvMHHY")
        assertNotNull(mobileYt)
        assertEquals("4YFu4dvMHHY", mobileYt?.id)
        assertEquals(MediaTargetType.TRACK, mobileYt?.type)
    }

    @Test
    fun `parse Spotify URI schemes`() {
        val trackUri = MediaUrlParser.parse("spotify:track:3XbsXsdK0ns2J63eomL3YS")
        assertNotNull(trackUri)
        assertEquals(MediaService.SPOTIFY, trackUri?.service)
        assertEquals(MediaTargetType.TRACK, trackUri?.type)
        assertEquals("3XbsXsdK0ns2J63eomL3YS", trackUri?.id)
        assertEquals("https://open.spotify.com/track/3XbsXsdK0ns2J63eomL3YS", trackUri?.cleanUrl)

        val artistUri = MediaUrlParser.parse("spotify:artist:4HVmXRMJawqE3RAt5bENQm")
        assertNotNull(artistUri)
        assertEquals(MediaService.SPOTIFY, artistUri?.service)
        assertEquals(MediaTargetType.ARTIST, artistUri?.type)
        assertEquals("4HVmXRMJawqE3RAt5bENQm", artistUri?.id)
        assertEquals("https://open.spotify.com/artist/4HVmXRMJawqE3RAt5bENQm", artistUri?.cleanUrl)

        val albumUri = MediaUrlParser.parse("spotify:album:1bdWiwXpRNy5lteQW9kfsN")
        assertNotNull(albumUri)
        assertEquals(MediaService.SPOTIFY, albumUri?.service)
        assertEquals(MediaTargetType.ALBUM, albumUri?.type)
        assertEquals("1bdWiwXpRNy5lteQW9kfsN", albumUri?.id)
        assertEquals("https://open.spotify.com/album/1bdWiwXpRNy5lteQW9kfsN", albumUri?.cleanUrl)

        val playlistUri = MediaUrlParser.parse("spotify:playlist:5FHyIGtk9sUj3nhcLQOEps")
        assertNotNull(playlistUri)
        assertEquals(MediaService.SPOTIFY, playlistUri?.service)
        assertEquals(MediaTargetType.PLAYLIST, playlistUri?.type)
        assertEquals("5FHyIGtk9sUj3nhcLQOEps", playlistUri?.id)
        assertEquals("https://open.spotify.com/playlist/5FHyIGtk9sUj3nhcLQOEps", playlistUri?.cleanUrl)
    }

    @Test
    fun `input trimming and quotes stripping`() {
        val doubleQuoted = MediaUrlParser.parse("\"https://open.spotify.com/track/3XbsXsdK0ns2J63eomL3YS\"")
        assertNotNull(doubleQuoted)
        assertEquals("3XbsXsdK0ns2J63eomL3YS", doubleQuoted?.id)

        val singleQuotedWithSpaces = MediaUrlParser.parse("  'https://music.youtube.com/watch?v=4YFu4dvMHHY'  ")
        assertNotNull(singleQuotedWithSpaces)
        assertEquals("4YFu4dvMHHY", singleQuotedWithSpaces?.id)

        val smartQuoted = MediaUrlParser.parse("“https://open.spotify.com/artist/4HVmXRMJawqE3RAt5bENQm”")
        assertNotNull(smartQuoted)
        assertEquals("4HVmXRMJawqE3RAt5bENQm", smartQuoted?.id)

        val angleBrackets = MediaUrlParser.parse("<https://music.youtube.com/playlist?list=PLSF3AfzmiKo8>")
        assertNotNull(angleBrackets)
        assertEquals("PLSF3AfzmiKo8", angleBrackets?.id)
    }

    @Test
    fun `invalid and non-matching inputs return null`() {
        assertNull(MediaUrlParser.parse(""))
        assertNull(MediaUrlParser.parse("   "))
        assertNull(MediaUrlParser.parse("hello world"))
        assertNull(MediaUrlParser.parse("https://example.com/watch?v=4YFu4dvMHHY"))
        assertNull(MediaUrlParser.parse("https://open.spotify.com/user/username123"))
        assertNull(MediaUrlParser.parse("https://music.youtube.com/watch"))
        assertNull(MediaUrlParser.parse("https://music.youtube.com/watch?v="))
        assertNull(MediaUrlParser.parse("https://music.youtube.com/playlist"))
        assertNull(MediaUrlParser.parse("https://music.youtube.com/playlist?list="))
        assertNull(MediaUrlParser.parse("spotify:unknown:12345"))
    }
}
