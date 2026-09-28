package com.theveloper.pixelplay.data.metadata

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.lastfm.LastFmRepository
import com.theveloper.pixelplay.data.network.musicbrainz.MbArtist
import com.theveloper.pixelplay.data.network.musicbrainz.MbIsrcResponse
import com.theveloper.pixelplay.data.network.musicbrainz.MbRecording
import com.theveloper.pixelplay.data.network.musicbrainz.MbRecordingSearchResponse
import com.theveloper.pixelplay.data.network.musicbrainz.MbRelation
import com.theveloper.pixelplay.data.network.musicbrainz.MbWork
import com.theveloper.pixelplay.data.network.musicbrainz.MusicBrainzApiService
import com.theveloper.pixelplay.data.network.musicbrainz.MusicBrainzRateLimiter
import com.theveloper.pixelplay.data.network.musicbrainz.MusicBrainzRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Deezer + iTunes + MusicBrainz for one YouTube song, all answered from fixtures. */
class SongMetadataGathererDeepTest {
    @TempDir lateinit var dir: File

    private val deezerSearch = """{"data":[{"id":9997018,"title":"Bohemian Rhapsody","duration":355,
        "artist":{"name":"Queen"},"album":{"id":915785,"title":"A Night At The Opera (2011 Remaster)"}}]}"""
    private val deezerTrack = """{"id":9997018,"title":"Bohemian Rhapsody","isrc":"GBUM71029604","duration":355,
        "track_position":11,"disk_number":1,"release_date":"2011-03-14","explicit_lyrics":false,"bpm":143.2,"gain":-8.1,
        "artist":{"name":"Queen"},"album":{"id":915785,"title":"A Night At The Opera (2011 Remaster)"}}"""
    private val deezerAlbum = """{"id":915785,"title":"A Night At The Opera (2011 Remaster)","upc":"00602527642340",
        "label":"EMI","release_date":"2011-03-14","genres":{"data":[{"id":152,"name":"Rock"}]},"artist":{"name":"Queen"}}"""
    private val itunesSearch = """{"resultCount":1,"results":[{"wrapperType":"track","trackName":"Bohemian Rhapsody",
        "artistName":"Queen","collectionName":"A Night at the Opera (Deluxe Edition)","trackTimeMillis":354320,
        "releaseDate":"1975-11-21T08:00:00Z","primaryGenreName":"Rock","trackNumber":11,"discNumber":1,
        "trackExplicitness":"notExplicit"}]}"""
    private val recordingJson = """{"id":"rec-1","title":"Bohemian Rhapsody","length":355000,
        "first-release-date":"1975-10-31","artist-credit":[{"name":"Queen","artist":{"id":"a1","name":"Queen"}}],
        "isrcs":["GBUM71029604"],
        "releases":[{"id":"rel-opera","title":"A Night at the Opera","status":"Official","date":"1975-11-21",
          "cover-art-archive":{"artwork":true,"front":true,"count":1}},
          {"id":"rel-hits","title":"Greatest Hits","status":"Official","date":"1981-10-26"}],
        "tags":[{"count":3,"name":"rock"},{"count":2,"name":"melancholic"}],
        "genres":[{"count":8,"name":"rock"}],
        "relations":[{"type":"performance","target-type":"work","work":{"id":"work-1","title":"Bohemian Rhapsody"}}]}"""

    private val calls = mutableListOf<String>()
    /** Every catalogue answers "no match". */
    @Volatile private var noMatches = false

    private val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val url = chain.request().url
        synchronized(calls) { calls += url.host + url.encodedPath }
        val body = when {
            noMatches && url.host == "api.deezer.com" -> """{"data":[]}"""
            noMatches && url.host == "itunes.apple.com" -> """{"resultCount":0,"results":[]}"""
            url.host == "api.deezer.com" && url.encodedPath == "/search" -> deezerSearch
            url.host == "api.deezer.com" && url.encodedPath == "/track/9997018" -> deezerTrack
            url.host == "api.deezer.com" && url.encodedPath == "/album/915785" -> deezerAlbum
            url.host == "itunes.apple.com" -> itunesSearch
            else -> null
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(if (body != null) 200 else 404).message("")
            .body((body ?: "{}").toResponseBody("application/json".toMediaType()))
            .build()
    }).build()

    private fun context(): Context {
        val capabilities = mockk<NetworkCapabilities> { every { hasCapability(any()) } returns true }
        val network = mockk<Network>()
        val connectivity = mockk<ConnectivityManager> {
            every { activeNetwork } returns network
            every { getNetworkCapabilities(network) } returns capabilities
        }
        return mockk(relaxed = true) {
            every { filesDir } returns dir
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
        }
    }

    private val song = Song(
        id = "yt_fJ9rUzIMcZQ", title = "Bohemian Rhapsody (Official Video)", artist = "Queen",
        artistId = 0L, album = "YouTube Music", albumId = 0L, path = "", contentUriString = "youtube://fJ9rUzIMcZQ",
        albumArtUriString = null, duration = 0L, genre = "YouTube Music", youtubeId = "fJ9rUzIMcZQ"
    )

    @Test fun `a later lookup that matches nothing keeps what was found`() = runBlocking {
        val api = mockk<MusicBrainzApiService> {
            coEvery { searchRecordings(any(), any(), any()) } returns MbRecordingSearchResponse()
        }
        val musicBrainz = MusicBrainzRepository(api, mockk(), MusicBrainzRateLimiter(), OkHttpClient())
        val store = mockk<SongMetadataStore> { coEvery { record(any(), any()) } returns mockk() }
        val lastFm = mockk<LastFmRepository> { coEvery { getTrackInfo(any(), any()) } returns null }
        val gatherer = SongMetadataGatherer(context(), lastFm, store, musicBrainz, http)

        assertEquals("Rock", gatherer.gather(song, timeoutMs = 15_000).genre)
        noMatches = true
        val again = gatherer.gatherDeep(song, timeoutMs = 15_000)

        assertEquals("Rock", again.genre)
        assertEquals(11, again.trackNumber)
        assertTrue(gatherer.cachedFor(song)!!.deepAt > 0)
    }

    @Test fun `a deep lookup votes three catalogues together`() = runBlocking {
        val api = mockk<MusicBrainzApiService> {
            coEvery { lookupIsrc("GBUM71029604", any()) } returns MbIsrcResponse(
                "GBUM71029604", listOf(MbRecording(id = "rec-1", title = "Bohemian Rhapsody", length = 355_000)))
            coEvery { lookupRecording("rec-1", any(), any()) } returns Gson().fromJson(recordingJson, MbRecording::class.java)
            coEvery { lookupWork("work-1", any(), any()) } returns MbWork(
                id = "work-1", title = "Bohemian Rhapsody", language = "eng",
                relations = listOf(
                    MbRelation(type = "composer", artist = MbArtist(id = "fm", name = "Freddie Mercury")),
                    MbRelation(type = "lyricist", artist = MbArtist(id = "fm", name = "Freddie Mercury"))
                ))
        }
        val musicBrainz = MusicBrainzRepository(api, mockk(), MusicBrainzRateLimiter(), OkHttpClient())
        val claims = slot<Map<String, MetadataClaim>>()
        val store = mockk<SongMetadataStore> { coEvery { record(any(), capture(claims)) } returns mockk() }
        val lastFm = mockk<LastFmRepository> { coEvery { getTrackInfo(any(), any()) } returns null }
        val gatherer = SongMetadataGatherer(context(), lastFm, store, musicBrainz, http)

        val filled = gatherer.gatherDeep(song, timeoutMs = 15_000)

        // The original release, not the 2011 remaster album's date.
        assertEquals(1975, filled.year)
        assertEquals("Rock", filled.genre)
        assertEquals("A Night At The Opera (2011 Remaster)", filled.album)
        assertEquals(11, filled.trackNumber)
        assertEquals(355_000L, filled.duration)
        assertEquals(143.2f, filled.musicalFeatures.bpm!!, 0.001f)
        assertEquals("Freddie Mercury", filled.creditsAndRelease.composer)
        assertEquals("Freddie Mercury", filled.musicalFeatures.lyricist)
        assertEquals("Sad", filled.mixIntelligence.mood) // From MusicBrainz's "melancholic" tag.
        coVerify(exactly = 1) { api.lookupIsrc("GBUM71029604", any()) }

        val gathered = gatherer.cachedFor(song)!!
        assertEquals(listOf("Deezer", "iTunes", "MusicBrainz"), gathered.sources)
        assertEquals("rec-1", gathered.recordingMbid)
        assertEquals("rel-opera", gathered.releaseMbid)
        assertEquals(1f, gathered.agreement.getValue("genre"))
        assertTrue(gathered.conflicts.any { it.startsWith("year:") })

        val recorded = claims.captured
        assertEquals("rec-1", recorded.getValue("identity.musicbrainz_recording_id").value)
        assertEquals("Freddie Mercury", recorded.getValue("credits.composer").value)
        assertEquals("1975-10-31", recorded.getValue("release.original_release_date").value)
        assertEquals(MetadataConsensus.METHOD, recorded.getValue("classification.genres").methodVersion)
        assertEquals("MusicBrainz tags", recorded.getValue("classification.mood").source)

        // Asked once: the next look is answered from the cache.
        calls.clear()
        assertEquals(1975, gatherer.gatherDeep(song, timeoutMs = 15_000).year)
        assertTrue(calls.isEmpty(), "looked up again: $calls")
    }
}
