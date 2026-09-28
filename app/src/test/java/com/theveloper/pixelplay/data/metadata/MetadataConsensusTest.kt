package com.theveloper.pixelplay.data.metadata

import com.theveloper.pixelplay.data.metadata.MetadataConsensus.DEEZER
import com.theveloper.pixelplay.data.metadata.MetadataConsensus.ITUNES
import com.theveloper.pixelplay.data.metadata.MetadataConsensus.MUSICBRAINZ
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetadataConsensusTest {
    private fun deezer(block: GatheredMetadata.() -> GatheredMetadata = { this }) =
        GatheredMetadata(sources = listOf(DEEZER)).block()
    private fun itunes(block: GatheredMetadata.() -> GatheredMetadata = { this }) =
        GatheredMetadata(sources = listOf(ITUNES)).block()
    private fun musicBrainz(block: GatheredMetadata.() -> GatheredMetadata = { this }) =
        GatheredMetadata(sources = listOf(MUSICBRAINZ), deepAt = 42L).block()

    @Test fun `two sources outvote the first one`() {
        val merged = MetadataConsensus.combine(listOf(
            deezer { copy(album = "Greatest Hits") },
            itunes { copy(album = "A Night at the Opera") },
            musicBrainz { copy(album = "A Night At The Opera") }
        ))
        assertEquals("A Night at the Opera", merged.album)
        assertEquals(1.6f / 2.6f, merged.agreement.getValue("album"), 0.001f)
        assertTrue(merged.conflicts.single().startsWith("album: Deezer \"Greatest Hits\""))
    }

    @Test fun `edition and single suffixes don't count as disagreement`() {
        val merged = MetadataConsensus.combine(listOf(
            deezer { copy(album = "Future Nostalgia (The Moonlight Edition)") },
            itunes { copy(album = "Future Nostalgia - Single") }
        ))
        assertEquals("Future Nostalgia (The Moonlight Edition)", merged.album)
        assertEquals(1f, merged.agreement.getValue("album"))
        assertTrue(merged.conflicts.isEmpty())
    }

    @Test fun `genre spellings of the same genre agree`() {
        val merged = MetadataConsensus.combine(listOf(
            deezer { copy(genre = "Rap/Hip Hop") },
            itunes { copy(genre = "Hip-Hop/Rap") },
            musicBrainz { copy(genre = "Pop Rap") }
        ))
        assertEquals("Rap/Hip Hop", merged.genre)
        assertEquals(1.7f / 2.9f, merged.agreement.getValue("genre"), 0.001f)
    }

    @Test fun `community genre wins when every source disagrees`() {
        val merged = MetadataConsensus.combine(listOf(
            deezer { copy(genre = "Pop") },
            itunes { copy(genre = "Alternative") },
            musicBrainz { copy(genre = "Synth-Pop") }
        ))
        assertEquals("Synth-Pop", merged.genre)
    }

    @Test fun `the first release beats a remaster's date`() {
        val merged = MetadataConsensus.combine(listOf(
            deezer { copy(year = 2011, releaseDate = "2011-03-14") },
            itunes { copy(year = 2011, releaseDate = "2011-03-14") },
            musicBrainz { copy(year = 1975, releaseDate = "1975-10-31") }
        ))
        assertEquals(1975, merged.year)
        assertEquals("1975-10-31", merged.releaseDate)
        assertEquals(1f / 3f, merged.agreement.getValue("year"), 0.001f)
        assertTrue(merged.conflicts.any { it.startsWith("year:") && it.endsWith("(first release)") })
    }

    @Test fun `a later MusicBrainz date is outvoted`() {
        val merged = MetadataConsensus.combine(listOf(
            deezer { copy(year = 1975, releaseDate = "1975-11-21") },
            itunes { copy(year = 1975, releaseDate = "1975-11-21") },
            musicBrainz { copy(year = 2011, releaseDate = "2011") }
        ))
        assertEquals(1975, merged.year)
        assertEquals("1975-11-21", merged.releaseDate)
    }

    @Test fun `durations within two seconds are the same`() {
        val merged = MetadataConsensus.combine(listOf(
            deezer { copy(durationMs = 354_000) },
            itunes { copy(durationMs = 355_400) },
            musicBrainz { copy(durationMs = 355_000) }
        ))
        assertEquals(354_000L, merged.durationMs)
        assertEquals(1f, merged.agreement.getValue("duration"))
    }

    @Test fun `track numbers trust iTunes first`() {
        val merged = MetadataConsensus.combine(listOf(
            deezer { copy(trackNumber = 1) },
            itunes { copy(trackNumber = 11) }
        ))
        assertEquals(11, merged.trackNumber)
    }

    @Test fun `explicit if any source says so`() {
        val merged = MetadataConsensus.combine(listOf(
            deezer { copy(explicit = true) },
            itunes { copy(explicit = false) }
        ))
        assertEquals(true, merged.explicit)
    }

    @Test fun `single sources are taken as they are, without an agreement figure`() {
        val merged = MetadataConsensus.combine(listOf(
            deezer { copy(bpm = 118f, label = "EMI", genre = "Rock", coverUrl = "dz") },
            itunes { copy(coverUrl = "it") }
        ))
        assertEquals(118f, merged.bpm)
        assertEquals("EMI", merged.label)
        assertEquals("Rock", merged.genre)
        assertEquals("it", merged.coverUrl)
        assertFalse("genre" in merged.agreement)
        assertEquals(listOf(DEEZER, ITUNES), merged.sources)
        assertEquals(0L, merged.deepAt)
    }

    @Test fun `MusicBrainz-only facts come through`() {
        val merged = MetadataConsensus.combine(listOf(
            deezer { copy(isrc = "gbum71029604") },
            musicBrainz {
                copy(isrc = "GBUM71029604", composer = "Freddie Mercury", tags = listOf("rock", "epic"),
                    recordingMbid = "rec", workMbid = "work", language = "eng")
            }
        ))
        assertEquals("GBUM71029604", merged.isrc)
        assertEquals(1f, merged.agreement.getValue("isrc"))
        assertEquals("Freddie Mercury", merged.composer)
        assertEquals(listOf("rock", "epic"), merged.tags)
        assertEquals("rec", merged.recordingMbid)
        assertEquals("eng", merged.language)
        assertEquals(42L, merged.deepAt)
    }

    @Test fun `nothing found stays not found`() {
        assertFalse(MetadataConsensus.combine(emptyList()).found)
        assertFalse(MetadataConsensus.combine(listOf(GatheredMetadata(found = false, sources = listOf(DEEZER)))).found)
        assertNull(MetadataConsensus.combine(emptyList()).genre)
    }

    @Test fun `MusicBrainz genres are title-cased for display`() {
        assertEquals("Hip Hop", MetadataConsensus.displayGenre("hip hop"))
        assertEquals("Contemporary R&B", MetadataConsensus.displayGenre("contemporary r&b"))
        assertEquals("Synth-Pop", MetadataConsensus.displayGenre("synth-pop"))
        assertEquals("K-Pop", MetadataConsensus.displayGenre("k-pop"))
        assertEquals("EDM", MetadataConsensus.displayGenre("edm"))
    }
}
