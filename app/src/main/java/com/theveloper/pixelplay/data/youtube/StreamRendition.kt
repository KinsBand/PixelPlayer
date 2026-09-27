package com.theveloper.pixelplay.data.youtube

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Byte-range retries must use the same rendition, never a different bitrate/container. */
internal fun YouTubeAudioStream.renditionKey(): String =
    url.toHttpUrlOrNull()?.queryParameter("itag")?.let { "$mimeType:$it" }
        ?: "$mimeType:$bitrate"

internal fun selectRendition(
    streams: List<YouTubeAudioStream>, key: String?, mp4Only: Boolean = false
): YouTubeAudioStream? = streams.asSequence()
    .filter { !mp4Only || it.container == "mp4" }
    .filter { key == null || it.renditionKey() == key }
    .maxByOrNull { it.bitrate }
