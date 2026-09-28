package com.theveloper.pixelplay.data.metadata

import com.google.gson.Gson
import com.theveloper.pixelplay.data.network.musicbrainz.MbIsrcResponse
import com.theveloper.pixelplay.data.network.musicbrainz.MbRecording
import com.theveloper.pixelplay.data.network.musicbrainz.MbWorkDetails
import com.theveloper.pixelplay.data.network.musicbrainz.MusicBrainzRateLimiter
import com.theveloper.pixelplay.data.network.musicbrainz.MusicBrainzRepository
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MusicBrainzSongFactsTest {
    // Shape of `ws/2/recording/<mbid>?inc=artists+releases+isrcs+tags+genres+work-rels&fmt=json`.
    private val recordingJson = """
        {
          "id": "b1a9c0e9-d987-4042-ae91-78d6a3267d69",
          "title": "Bohemian Rhapsody",
          "length": 355000,
          "disambiguation": "",
          "video": false,
          "first-release-date": "1975-10-31",
          "artist-credit": [{"name": "Queen", "joinphrase": "",
            "artist": {"id": "0383dadf-2a4e-4d10-a46a-e9e041da8eb3", "name": "Queen", "sort-name": "Queen"}}],
          "isrcs": ["GBUM71029604", "GBUM71505078"],
          "releases": [
            {"id": "rel-opera", "title": "A Night at the Opera", "status": "Official", "date": "1975-11-21", "country": "GB",
             "cover-art-archive": {"artwork": true, "front": true, "back": false, "count": 1}},
            {"id": "rel-single", "title": "Bohemian Rhapsody", "status": "Official", "date": "1975-10-31", "country": "GB"},
            {"id": "rel-hits", "title": "Greatest Hits", "status": "Official", "date": "1981-10-26", "country": "GB"}
          ],
          "tags": [{"count": 3, "name": "rock"}, {"count": 1, "name": "epic"}, {"count": 2, "name": "melancholic"}],
          "genres": [{"count": 5, "name": "progressive rock", "id": "g1"}, {"count": 8, "name": "rock", "id": "g2"}],
          "relations": [{"type": "performance", "target-type": "work", "direction": "forward",
            "work": {"id": "work-1", "title": "Bohemian Rhapsody", "language": "eng", "iswcs": ["T-010.004.829-7"]}}]
        }
    """.trimIndent()

    private val work = MbWorkDetails(
        workMbid = "work-1", title = "Bohemian Rhapsody", language = "eng", iswcs = emptyList(),
        composers = listOf("Freddie Mercury"), lyricists = listOf("Freddie Mercury"), songwriters = emptyList()
    )

    @Test fun `a recording lookup becomes MusicBrainz's vote`() {
        val recording = Gson().fromJson(recordingJson, MbRecording::class.java)
        val facts = MusicBrainzSongFacts.toGathered(recording, work, releaseMbid = "rel-opera", isrc = "GBUM71505078", now = 7L)

        assertEquals("Rock", facts.genre)
        assertEquals("A Night at the Opera", facts.album)
        assertEquals("Queen", facts.artist)
        assertEquals(355_000L, facts.durationMs)
        assertEquals(1975, facts.year)
        assertEquals("1975-10-31", facts.releaseDate)
        assertEquals("GBUM71505078", facts.isrc)
        assertEquals(listOf("rock", "melancholic", "epic"), facts.tags)
        assertEquals("Freddie Mercury", facts.composer)
        assertEquals("Freddie Mercury", facts.lyricist)
        assertNull(facts.songwriter)
        assertEquals("eng", facts.language)
        assertEquals("b1a9c0e9-d987-4042-ae91-78d6a3267d69", facts.recordingMbid)
        assertEquals("rel-opera", facts.releaseMbid)
        assertEquals("work-1", facts.workMbid)
        assertEquals(listOf(MetadataConsensus.MUSICBRAINZ), facts.sources)
        assertEquals(7L, facts.deepAt)
    }

    @Test fun `missing first release date falls back to the earliest release`() {
        val recording = Gson().fromJson(recordingJson, MbRecording::class.java).copy(firstReleaseDate = null)
        val facts = MusicBrainzSongFacts.toGathered(recording, null, releaseMbid = null, isrc = "XX0000000000", now = 0L)
        assertEquals("1975-10-31", facts.releaseDate)
        assertNull(facts.album)
        assertEquals("GBUM71029604", facts.isrc) // The code asked for isn't on this recording.
        assertNull(facts.composer)
    }

    // Shape of `ws/2/isrc/<isrc>?fmt=json`.
    private val isrcJson = """
        {"isrc": "GBUM71029604", "recordings": [
          {"id": "live", "title": "Bohemian Rhapsody (live at Wembley)", "length": 412000},
          {"id": "studio", "title": "Bohemian Rhapsody", "length": 355000, "first-release-date": "1975-10-31"},
          {"id": "other", "title": "Killer Queen", "length": 181000}
        ]}
    """.trimIndent()

    private val repository = MusicBrainzRepository(mockk(), mockk(), MusicBrainzRateLimiter(), OkHttpClient())

    @Test fun `the recording an ISRC belongs to must be the song asked about`() {
        val recordings = Gson().fromJson(isrcJson, MbIsrcResponse::class.java).recordings
        assertEquals("studio", repository.bestIsrcRecording(recordings, "Bohemian Rhapsody", 354_000)?.id)
        // Without a duration the closer title still wins.
        assertEquals("studio", repository.bestIsrcRecording(recordings, "Bohemian Rhapsody", null)?.id)
        assertNull(repository.bestIsrcRecording(recordings, "Another One Bites the Dust", null))
        // A length far from the song's: a wrong ISRC on the upload.
        assertNull(repository.bestIsrcRecording(recordings.filter { it.id == "studio" }, "Bohemian Rhapsody", 200_000))
    }
}
