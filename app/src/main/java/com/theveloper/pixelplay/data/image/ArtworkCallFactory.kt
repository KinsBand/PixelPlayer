package com.theveloper.pixelplay.data.image

import okhttp3.Call
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request

/** Reserved network capacity for the opened song, sharing warm connections with thumbnails. */
class ArtworkCallFactory(base: OkHttpClient) : Call.Factory {
    private val thumbnails = base.newBuilder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 12
            maxRequestsPerHost = 4
        })
        .build()
    private val player = base.newBuilder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 4
            maxRequestsPerHost = 4
        })
        .build()

    override fun newCall(request: Request): Call {
        val client = if (request.header(PRIORITY_HEADER) == "player") player else thumbnails
        // This is an internal routing hint, never a header sent to an artwork provider.
        return client.newCall(request.newBuilder().removeHeader(PRIORITY_HEADER).build())
    }

    companion object {
        const val PRIORITY_HEADER = "X-PixelPlay-Artwork-Priority"
    }
}
