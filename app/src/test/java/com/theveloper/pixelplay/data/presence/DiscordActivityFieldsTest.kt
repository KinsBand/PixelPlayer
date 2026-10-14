package com.theveloper.pixelplay.data.presence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DiscordActivityFieldsTest {
    private fun now(
        title: String = "Song",
        artist: String = "Artist",
        album: String? = "Album",
        art: String? = "https://example.com/a.jpg",
        position: Long = 10_000L,
        duration: Long = 200_000L,
    ) = NowPlayingPresence(
        mediaId = "id", title = title, artist = artist, album = album, artworkUrl = art,
        positionMs = position, durationMs = duration, isPlaying = true,
    )

    @Test fun `blank title and artist fall back`() {
        val f = discordActivityFields(now(title = "  ", artist = ""), nowMs = 1_000_000L)
        assertEquals("Unknown title", f.details)
        assertEquals("Unknown artist", f.state)
    }

    @Test fun `long text is capped at 128`() {
        val f = discordActivityFields(now(title = "x".repeat(200), album = "y".repeat(300)), nowMs = 0L)
        assertEquals(128, f.details.length)
        assertEquals(128, f.largeText?.length)
    }

    @Test fun `timestamps follow position and length`() {
        val f = discordActivityFields(now(position = 10_000L, duration = 200_000L), nowMs = 1_000_000L)
        assertEquals(990_000L, f.startMs)
        assertEquals(1_190_000L, f.endMs)
    }

    @Test fun `no length means no end`() {
        assertEquals(0L, discordActivityFields(now(duration = 0L), nowMs = 1_000L).endMs)
    }

    @Test fun `negative position counts as zero`() {
        assertEquals(5_000L, discordActivityFields(now(position = -3L), nowMs = 5_000L).startMs)
    }

    @Test fun `only https covers are sent`() {
        assertNull(discordActivityFields(now(art = "content://media/1"), nowMs = 0L).largeImage)
        assertNull(discordActivityFields(now(art = "http://example.com/a.jpg"), nowMs = 0L).largeImage)
        assertEquals("https://example.com/a.jpg", discordActivityFields(now(), nowMs = 0L).largeImage)
    }

    @Test fun `blank album is no caption`() {
        assertNull(discordActivityFields(now(album = " "), nowMs = 0L).largeText)
    }

    @Test fun `application id must be 17 to 20 digits`() {
        assertEquals(1558799774789017683L, parseDiscordApplicationId(" 1558799774789017683 "))
        assertNull(parseDiscordApplicationId("1234567890"))
        assertNull(parseDiscordApplicationId("15587997747890176ab"))
        assertNull(parseDiscordApplicationId(""))
    }
}
