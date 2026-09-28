package com.theveloper.pixelplay.data.youtube

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A single audio-only rendition, analogous to Spotube's audio-source manifest. */
data class YouTubeAudioStream(
    val url: String,
    val mimeType: String,
    val bitrate: Int, // bits/second, not NewPipe's kbit/second
    val expiresAt: Long,
    /** Exact byte size (googlevideo `clen`), or -1 when unknown. */
    val contentLength: Long = -1L,
    /** Which stream client produced [url] (see [StreamClients]); empty when unknown. */
    val client: String = "",
) {
    val container: String get() = if (mimeType.contains("webm")) "webm" else "mp4"
    val fileExtension: String get() = if (container == "webm") "webm" else "m4a"

    companion object {
        fun expiry(url: String, now: Long): Long {
            val seconds = url.toHttpUrlOrNull()?.queryParameter("expire")?.toLongOrNull()
            val advertised = seconds?.takeIf { it in 1..(Long.MAX_VALUE / 1000) }?.times(1000)
            return minOf(advertised ?: (now + 15 * 60_000), now + 6 * 60 * 60_000) - 60_000
        }

        fun select(streams: List<YouTubeAudioStream>, quality: AudioQualityPreset): YouTubeAudioStream? =
            when (quality) {
                AudioQualityPreset.AUTO, AudioQualityPreset.HIGH -> streams.maxByOrNull { it.bitrate }
                AudioQualityPreset.NORMAL -> streams.minByOrNull { kotlin.math.abs(it.bitrate.toLong() - 128_000) }
                AudioQualityPreset.DATA_SAVER -> streams.minByOrNull { it.bitrate }
            }
    }
}
