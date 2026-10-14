package com.theveloper.pixelplay.data.recognition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GsaResultParserTest {

    /** Texts from the Google app's result page for "Color Change" by Whirr, in tree order. */
    private val whirrPage = listOf(
        "Whirr Color Change",
        "AI Mode", "All", "Images", "Videos", "Short videos", "Shop", "⋮",
        "Color Change",
        "Song by Whirr · 2012",
        "YouTube",
        "Color Change",
        "Whirr - Topic",
        "⋮",
        "Genius",
        "genius.com › Whirr-color-change-lyrics",
        "Whirr – Color Change Lyrics",
        "Home", "Search", "Notifications", "Activity"
    )

    @Test
    fun `song panel line gives title and artist`() {
        assertEquals(
            GsaParseResult.Song("Color Change", "Whirr"),
            GsaResultParser.parse(whirrPage, searchBoxText = "Whirr Color Change")
        )
    }

    @Test
    fun `song by without year still parses`() {
        val texts = listOf("All", "Images", "Color Change", "⋮", "Song by Whirr", "Home")
        assertEquals(GsaParseResult.Song("Color Change", "Whirr"), GsaResultParser.parse(texts))
    }

    @Test
    fun `youtube topic channel is the backup`() {
        val texts = listOf("All", "Videos", "YouTube", "Color Change", "Whirr - Topic", "Home", "Activity")
        assertEquals(GsaParseResult.Song("Color Change", "Whirr"), GsaResultParser.parse(texts))
    }

    @Test
    fun `search box only gives a query`() {
        val texts = listOf("AI Mode", "All", "Images", "Home", "Search", "Notifications", "Activity")
        assertEquals(
            GsaParseResult.QueryOnly("Whirr Color Change"),
            GsaResultParser.parse(texts, searchBoxText = "Whirr Color Change")
        )
    }

    @Test
    fun `nothing readable gives none`() {
        assertEquals(GsaParseResult.None, GsaResultParser.parse(listOf("Listening…", "Home"), null))
        assertEquals(GsaParseResult.None, GsaResultParser.parse(emptyList(), "  "))
    }

    @Test
    fun `chrome is recognised`() {
        listOf("⋮", "AI Mode", "Short videos", "genius.com › Whirr-color-change-lyrics", "https://youtube.com", "YouTube", "3:45")
            .forEach { assertTrue(GsaResultParser.isChrome(it), it) }
        listOf("Color Change", "1999", "Mr. Brightside")
            .forEach { assertFalse(GsaResultParser.isChrome(it), it) }
    }
}
