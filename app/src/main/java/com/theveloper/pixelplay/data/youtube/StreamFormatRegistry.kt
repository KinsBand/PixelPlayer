package com.theveloper.pixelplay.data.youtube

/**
 * Remembers which YouTube audio rendition each online song is actually streaming (container,
 * bitrate, size), so the player can show file info — "160 kbps • OPUS • 48.0 kHz" — under the
 * timeline for online songs too, not just files and downloads. The stream's own header can't
 * be read without downloading it, and the player often reports no bitrate for WebM/Opus.
 */
object StreamFormatRegistry {

    data class Info(
        /** Container type as YouTube gives it, e.g. "audio/webm" or "audio/mp4". */
        val mimeType: String,
        /** Bits per second; 0 when unknown. */
        val bitrate: Int,
        /** Bytes, or -1 when unknown. */
        val contentLength: Long,
    ) {
        /** Codec-level type the rest of the app understands (Opus in WebM, AAC in MP4). */
        val codecMimeType: String
            get() = when {
                mimeType.contains("opus", true) || mimeType.contains("webm", true) -> "audio/opus"
                mimeType.contains("mp4", true) || mimeType.contains("m4a", true) -> "audio/mp4a-latm"
                else -> mimeType.substringBefore(';').trim()
            }
    }

    private val formats = object : LinkedHashMap<String, Info>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Info>) = size > 128
    }

    fun record(videoId: String, stream: YouTubeAudioStream) {
        val id = videoId.removePrefix("yt_")
        synchronized(formats) {
            formats[id] = Info(stream.mimeType, stream.bitrate.coerceAtLeast(0), stream.contentLength)
        }
    }

    /** Accepts a video id or a song id ("yt_…"). */
    fun get(videoOrSongId: String?): Info? {
        val id = videoOrSongId?.removePrefix("yt_") ?: return null
        return synchronized(formats) { formats[id] }
    }
}
