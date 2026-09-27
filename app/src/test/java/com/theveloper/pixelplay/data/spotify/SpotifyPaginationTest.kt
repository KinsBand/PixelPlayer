package com.theveloper.pixelplay.data.spotify

import io.mockk.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpotifyPaginationTest {
    @Test fun `fetches later pages and accepts migrated item wrapper`() = runTest {
        val auth = mockk<SpotifyAuthManager>()
        coEvery { auth.refreshAccessTokenIfNeeded() } returns "token"
        val client = mockk<OkHttpClient>()
        val urls = mutableListOf<String>()
        every { client.newCall(any()) } answers {
            val request = firstArg<Request>()
            urls.add(request.url.toString())
            val body = if (urls.size == 1) """{"items":[{"track":{"id":"one","name":"First"}}],"next":"https://api.spotify.com/v1/me/tracks?offset=50"}"""
                else """{"items":[{"item":{"id":"two","name":"Second"}}],"next":null}"""
            mockk<okhttp3.Call>().also { call -> every { call.execute() } returns Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody("application/json".toMediaType())).build() }
        }
        assertEquals(listOf("one", "two"), SpotifyRepository(auth, client).getLikedSongs().map { it.id })
        assertEquals(2, urls.size)
    }
    @Test fun `unavailable items keep their place`() {
        val repository = SpotifyRepository(mockk(), mockk())
        val track = repository.parseTrack(org.json.JSONObject("""{"track":null}"""), "p:4")
        assertTrue(track.id.startsWith("unavailable:"))
        assertEquals("", track.toSong().contentUriString)
    }
}

