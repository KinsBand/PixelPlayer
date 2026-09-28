package com.theveloper.pixelplay.data.repository

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.database.LyricsDao
import com.theveloper.pixelplay.data.database.LyricsEntity
import com.theveloper.pixelplay.data.lyrics.LyricsAttribution
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.lyrics.LrcLibApiService
import com.theveloper.pixelplay.data.network.lyrics.LrcLibResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.nio.file.Files

/** The Lyricsfile and Unison additions to the automatic pipeline. */
class LyricsRepositoryOnlineSourcesTest {

    private fun song(id: String, youtubeId: String? = null) = Song(
        id = id, title = "Never Gonna Give You Up", artist = "Rick Astley", artistId = 1L,
        album = "Whenever You Need Somebody", albumId = 2L, path = "", contentUriString = "",
        albumArtUriString = null, duration = 213_000L, youtubeId = youtubeId
    )

    private fun context(): Context {
        val dir = Files.createTempDirectory("pixelplay-online-lyrics").toFile()
        return mockk(relaxed = true) { every { this@mockk.filesDir } returns dir }
    }

    /** Every host answers 404 except Unison, which answers [unison] (status to body). */
    private fun http(unison: (String) -> Pair<Int, String> = { 404 to "{}" }) = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val url = chain.request().url
            val (code, body) = if (url.host == "unison.betterlyrics.org") unison(url.toString()) else 404 to "{}"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        })
        .build()

    private val wordLines = (0 until 4).joinToString("\n") { i ->
        val start = 10_000 + i * 4_000
        """
        |  - text: Never gonna
        |    start_ms: $start
        |    end_ms: ${start + 2_000}
        |    words:
        |      - {text: 'Never ', start_ms: $start, end_ms: ${start + 800}}
        |      - {text: gonna, start_ms: ${start + 800}, end_ms: ${start + 2_000}}
        """.trimMargin()
    }
    private val lyricsfile = "version: '1.0'\nmetadata:\n  title: Never Gonna Give You Up\n  artist: Rick Astley\n  instrumental: false\nlines:\n$wordLines\n"

    @Test fun `an LRCLIB record with word timing is stored as its Lyricsfile`() = runTest {
        val api = mockk<LrcLibApiService>(relaxed = true)
        val dao = mockk<LyricsDao>(relaxed = true)
        val stored = slot<LyricsEntity>()
        coEvery { dao.getLyrics(301L) } returns null
        coEvery { dao.insert(capture(stored)) } returns Unit
        coEvery { api.getLyrics(any(), any(), any(), any()) } returns LrcLibResponse(
            id = 7, name = "Never Gonna Give You Up", artistName = "Rick Astley",
            albumName = "Whenever You Need Somebody", duration = 213.0, plainLyrics = "Never gonna",
            syncedLyrics = (0 until 4).joinToString("\n") { "[00:${10 + it * 4}.00]Never gonna" },
            lyricsfile = lyricsfile
        )
        val repository = LyricsRepositoryImpl(context(), api, dao, http())

        val (lyrics, raw) = repository.fetchFromRemote(song("301")).getOrThrow()

        assertThat(raw).isEqualTo(lyricsfile)
        assertThat(stored.captured.content).isEqualTo(lyricsfile)
        assertThat(lyrics.synced!!.first().words!!.map { it.endTime }).containsExactly(10_800, 12_000).inOrder()
        assertThat(LyricsAttribution.creditFor(lyrics)).isEqualTo(LyricsAttribution.LRCLIB)
    }

    @Test fun `a YouTube song takes Unison lyrics timed to its own video, with the credit Unison requires`() = runTest {
        val api = mockk<LrcLibApiService>(relaxed = true)
        val dao = mockk<LyricsDao>(relaxed = true)
        val stored = slot<LyricsEntity>()
        coEvery { dao.getLyrics(302L) } returns null
        coEvery { dao.insert(capture(stored)) } returns Unit
        coEvery { api.getLyrics(any(), any(), any(), any()) } returns null
        coEvery { api.searchLyrics(any(), any(), any(), any()) } returns emptyArray()
        val lrc = (0 until 6).joinToString("\n") { "[00:${10 + it * 3}.00]Line number $it" }
        val requests = mutableListOf<String>()
        val repository = LyricsRepositoryImpl(context(), api, dao, http { url ->
            requests += url
            if ("v=dQw4w9WgXcQ" in url) {
                200 to JSONObject().put("success", true).put("data", JSONObject()
                    .put("id", 9).put("videoId", "dQw4w9WgXcQ").put("song", "Never Gonna Give You Up")
                    .put("artist", "Rick Astley").put("lyrics", lrc).put("format", "lrc").put("syncType", "linesync")).toString()
            } else {
                404 to "{}"
            }
        })

        val (lyrics, raw) = repository.fetchFromRemote(song("302", youtubeId = "dQw4w9WgXcQ")).getOrThrow()

        assertThat(lyrics.synced!!.map { it.line }).contains("Line number 3")
        // Stored as timing JSON, so the source (and the credit) survives a reload.
        assertThat(raw).contains("online:unison")
        assertThat(stored.captured.content).isEqualTo(raw)
        assertThat(LyricsAttribution.creditFor(lyrics.copy(areFromRemote = false))).isEqualTo(LyricsAttribution.UNISON)
        // The word and line pipelines shared one lookup.
        assertThat(requests.count { "v=dQw4w9WgXcQ" in it }).isEqualTo(1)
    }

    @Test fun `songs Unison matches only by name must pass the usual checks`() = runTest {
        val api = mockk<LrcLibApiService>(relaxed = true)
        val dao = mockk<LyricsDao>(relaxed = true)
        coEvery { dao.getLyrics(303L) } returns null
        coEvery { api.getLyrics(any(), any(), any(), any()) } returns null
        coEvery { api.searchLyrics(any(), any(), any(), any()) } returns emptyArray()
        val lrc = (0 until 6).joinToString("\n") { "[00:${10 + it * 3}.00]Other words $it" }
        val repository = LyricsRepositoryImpl(context(), api, dao, http { url ->
            if ("song=" in url) {
                200 to JSONObject().put("success", true).put("data", JSONObject()
                    .put("id", 10).put("song", "Never Gonna Give You Up (Live)").put("artist", "Rick Astley")
                    .put("lyrics", lrc).put("format", "lrc").put("syncType", "linesync")).toString()
            } else {
                404 to "{}"
            }
        })

        // The live take's lyrics are not attached to the studio recording.
        assertThat(repository.fetchFromRemote(song("303")).isFailure).isTrue()
    }
}
