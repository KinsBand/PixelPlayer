package com.theveloper.pixelplay.data.image

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ArtworkCallFactoryTest {
    @Test fun `player bypasses saturated thumbnail host and routing header stays internal`() {
        val thumbnailsStarted = CountDownLatch(4)
        val releaseThumbnails = CountDownLatch(1)
        val playerFinished = CountDownLatch(1)
        val base = OkHttpClient.Builder().addInterceptor { chain ->
            assertNull(chain.request().header(ArtworkCallFactory.PRIORITY_HEADER))
            if (chain.request().url.encodedPath != "/player") {
                thumbnailsStarted.countDown()
                assertTrue(releaseThumbnails.await(10, TimeUnit.SECONDS))
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("cover".toResponseBody()).build()
        }.build()
        val factory = ArtworkCallFactory(base)
        val callback = object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) {
                response.close()
                if (call.request().url.encodedPath == "/player") playerFinished.countDown()
            }
        }
        val calls = mutableListOf<Call>()
        try {
            repeat(5) { index ->
                calls += factory.newCall(Request.Builder().url("https://art.test/thumb$index").build())
                    .also { it.enqueue(callback) }
            }
            assertTrue(thumbnailsStarted.await(5, TimeUnit.SECONDS))
            calls += factory.newCall(Request.Builder().url("https://art.test/player")
                .header(ArtworkCallFactory.PRIORITY_HEADER, "player").build())
                .also { it.enqueue(callback) }
            assertTrue(playerFinished.await(5, TimeUnit.SECONDS), "Player must not wait for thumbnails")
        } finally {
            releaseThumbnails.countDown()
            calls.forEach { it.cancel() }
        }
    }
}
