package com.theveloper.pixelplay.data.youtube.resolver

import com.theveloper.pixelplay.data.model.Song

data class PlaybackResolution(
    val uri: String,
    val mimeType: String? = "audio/mp4",
    val bitrate: Int? = 128,
    val expiresAt: Long? = null,
    val headers: Map<String, String> = emptyMap(),
    val cacheKey: String
)

interface PlaybackResolver {
    val providerName: String
    suspend fun resolve(song: Song): PlaybackResolution?
}
