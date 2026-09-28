package com.theveloper.pixelplay.data.network.lyrics.wordsync

import com.theveloper.pixelplay.data.lyrics.LyricsAttribution
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsTimingEvidence
import com.theveloper.pixelplay.utils.LyricsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UnisonLyricsTest {

    private class FakeUnison(var answer: (HttpUrl) -> Pair<Int, String>) : Interceptor {
        val requests = CopyOnWriteArrayList<HttpUrl>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val url = chain.request().url
            requests += url
            val (code, body) = answer(url)
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun client(fake: FakeUnison, clock: () -> Long = { 0L }) =
        UnisonLyricsClient(OkHttpClient.Builder().addInterceptor(fake).build(), clock = clock)

    private val videoId = "dQw4w9WgXcQ"

    private fun ttmlLine(startSec: Int) =
        """<p begin="$startSec.000" end="${startSec + 2}.000"><span begin="$startSec.000" end="$startSec.900">Never</span> <span begin="$startSec.900" end="${startSec + 2}.000">gonna</span></p>"""

    private val richTtml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div>""" +
        (0 until 5).joinToString("") { ttmlLine(10 + it * 3) } + "</div></body></tt>"

    private fun entry(format: String = "ttml", syncType: String = "richsync", lyrics: String = richTtml, hidden: Boolean = false) =
        JSONObject()
            .put("success", true)
            .put("data", JSONObject()
                .put("id", 42)
                .put("videoId", videoId)
                .put("song", "Never Gonna Give You Up")
                .put("artist", "Rick Astley")
                .put("lyrics", lyrics)
                .put("format", format)
                .put("syncType", syncType)
                .put("language", "en")
                .put("effectiveScore", 12.5)
                .put("voteCount", 7)
                .put("hidden", hidden))
            .toString()

    private val query = WordLyricsQuery("Never Gonna Give You Up", "Rick Astley", 213_400, album = "Whenever You Need Somebody", videoId = videoId)

    @Test fun `the exact video is asked first and needs no other request`() = runBlocking {
        val fake = FakeUnison { 200 to entry() }
        val found = requireNotNull(client(fake).lookup(query))
        assertTrue(found.matchedByVideoId)
        assertTrue(found.isWordSynced)
        assertEquals(1, fake.requests.size)
        assertEquals(videoId, fake.requests.single().queryParameter("v"))
        assertNull(fake.requests.single().queryParameter("song"))
    }

    @Test fun `without an entry for the video it matches song, artist and duration`() = runBlocking {
        val fake = FakeUnison { url -> if (url.queryParameter("v") != null) 404 to """{"success":false}""" else 200 to entry(syncType = "linesync") }
        val found = requireNotNull(client(fake).lookup(query))
        assertFalse(found.matchedByVideoId)
        assertTrue(found.isSynced)
        val metadataRequest = fake.requests.last()
        assertEquals("Never Gonna Give You Up", metadataRequest.queryParameter("song"))
        assertEquals("Rick Astley", metadataRequest.queryParameter("artist"))
        assertEquals("213", metadataRequest.queryParameter("duration"))
        assertEquals("Whenever You Need Somebody", metadataRequest.queryParameter("album"))
        assertNull(metadataRequest.queryParameter("v"))
    }

    @Test fun `answers are remembered, misses too`() = runBlocking {
        var now = 0L
        val fake = FakeUnison { 404 to "{}" }
        val unison = client(fake) { now }
        assertNull(unison.lookup(query))
        assertEquals(2, fake.requests.size)
        assertNull(unison.lookup(query))
        assertEquals(2, fake.requests.size)
        now += 11 * 60_000L
        assertNull(unison.lookup(query))
        assertEquals(4, fake.requests.size)
    }

    @Test fun `the word and line pipelines share one request`() = runBlocking(Dispatchers.Default) {
        val release = CountDownLatch(1)
        val fake = FakeUnison {
            release.await(5, TimeUnit.SECONDS)
            200 to entry()
        }
        val unison = client(fake)
        val first = async { unison.lookup(query) }
        withTimeout(5_000) { while (fake.requests.isEmpty()) delay(5) }
        val second = async { unison.lookup(query) }
        delay(100)
        release.countDown()
        assertEquals(42L, first.await()?.id)
        assertEquals(42L, second.await()?.id)
        assertEquals(1, fake.requests.size)
    }

    @Test fun `rate limiting is an error and pauses lookups`() {
        val fake = FakeUnison { 429 to "{}" }
        val unison = client(fake)
        assertThrows(IOException::class.java) { runBlocking { unison.lookup(query) } }
        assertThrows(IOException::class.java) { runBlocking { unison.lookup(query.copy(videoId = null)) } }
        assertEquals(1, fake.requests.size)
    }

    @Test fun `hidden, empty and unknown entries are ignored`() {
        assertNull(UnisonLyricsClient.parseEntry(entry(hidden = true), matchedByVideoId = true))
        assertNull(UnisonLyricsClient.parseEntry(entry(lyrics = " "), matchedByVideoId = true))
        assertNull(UnisonLyricsClient.parseEntry(entry(format = "srt"), matchedByVideoId = true))
        assertNull(UnisonLyricsClient.parseEntry("not json", matchedByVideoId = true))
        assertNotNull(UnisonLyricsClient.parseEntry(entry(), matchedByVideoId = true))
    }

    @Test fun `word provider offers exact richsync matches and reads their TTML`() = runBlocking {
        val provider = UnisonWordLyricsProvider(client(FakeUnison { 200 to entry() })) { LyricsUtils.parseLyrics(it) }
        val candidate = provider.search(query).single()
        assertTrue(candidate.exactMatch)
        assertEquals(WordLyricsSource.UNISON, candidate.source)
        val result = requireNotNull(provider.fetch(candidate))
        assertTrue(WordLyricsParsers.isWordTimed(result.lyrics))
        assertNull(result.rawTtml)
        val firstLine = result.lyrics.synced!!.first()
        assertEquals(10_000, firstLine.time)
        assertEquals(listOf("Never", "gonna"), firstLine.words!!.map { it.word })
        assertEquals(10_900, firstLine.words!!.first().endTime)
    }

    @Test fun `word provider leaves line synced entries to the line pipeline`() = runBlocking {
        val lrc = (0 until 5).joinToString("\n") { "[00:1$it.00]Line $it" }
        val provider = UnisonWordLyricsProvider(client(FakeUnison { 200 to entry(format = "lrc", syncType = "linesync", lyrics = lrc) })) {
            LyricsUtils.parseLyrics(it)
        }
        assertTrue(provider.search(query).isEmpty())
        val entry = requireNotNull(UnisonLyricsClient.parseEntry(entry(format = "lrc", syncType = "linesync", lyrics = lrc), true))
        val lyrics = requireNotNull(UnisonLyricsClient.toLyrics(entry) { LyricsUtils.parseLyrics(it) })
        assertEquals(5, lyrics.synced!!.size)
        assertTrue(lyrics.areFromRemote)
    }

    @Test fun `Unison lyrics always carry the wording its licence asks for`() {
        val stored = Lyrics(plain = listOf("a"), areFromRemote = false, timing = LyricsTimingEvidence(source = "online:unison"))
        val credit = requireNotNull(LyricsAttribution.creditFor(stored))
        assertEquals(UnisonLyricsClient.ATTRIBUTION, "${credit.lead} ${credit.name}")
        assertEquals("Lyrics from Unison (https://unison.boidu.dev)", "${credit.lead} ${credit.name}")
    }
}
