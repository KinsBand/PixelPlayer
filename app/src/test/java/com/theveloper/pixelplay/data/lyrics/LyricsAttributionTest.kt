package com.theveloper.pixelplay.data.lyrics

import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsTimingEvidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LyricsAttributionTest {
    private fun lyrics(source: String?, remote: Boolean) =
        Lyrics(plain = listOf("line"), areFromRemote = remote, timing = source?.let { LyricsTimingEvidence(source = it) })

    @Test fun `online results credit the provider that found them`() {
        assertEquals("QQ Music", LyricsAttribution.creditFor(lyrics("online:qq", remote = true))?.name)
        assertEquals("NetEase Cloud Music", LyricsAttribution.creditFor(lyrics("online:netease_yrc", remote = true))?.name)
        assertEquals("AMLL TTML DB", LyricsAttribution.creditFor(lyrics("online:amll", remote = true))?.name)
        assertEquals(LyricsAttribution.LRCLIB, LyricsAttribution.creditFor(lyrics("online:lrclib", remote = true)))
    }

    @Test fun `older online results without a source stay credited to LRCLIB`() {
        assertEquals(LyricsAttribution.LRCLIB, LyricsAttribution.creditFor(lyrics(null, remote = true)))
        assertEquals(LyricsAttribution.LRCLIB, LyricsAttribution.creditFor(lyrics("ttml", remote = true)))
    }

    @Test fun `local, embedded and imported lyrics get no credit, except Unison's`() {
        assertNull(LyricsAttribution.creditFor(lyrics(null, remote = false)))
        assertNull(LyricsAttribution.creditFor(lyrics("online:qq", remote = false)))
        assertNull(LyricsAttribution.creditFor(lyrics("lyricsfile", remote = false)))
        assertEquals(LyricsAttribution.UNISON, LyricsAttribution.creditFor(lyrics("online:unison", remote = false)))
        assertNull(LyricsAttribution.creditFor(null))
    }
}
