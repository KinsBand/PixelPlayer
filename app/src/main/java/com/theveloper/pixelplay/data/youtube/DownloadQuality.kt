package com.theveloper.pixelplay.data.youtube

/** Download quality picked from the full player's download menu. */
enum class DownloadQuality(val label: String) {
    /** The best rendition YouTube serves (usually Opus ~160 kbps in WebM). */
    HIGH("High"),
    /** AAC ~128 kbps (M4A, gets title and artwork tags). */
    MEDIUM("Medium"),
    /** The smallest rendition (~48–70 kbps). */
    LOW("Low");
}

/** One row of the download menu: what [quality] resolves to for a song right now. */
data class DownloadOption(
    val quality: DownloadQuality,
    val codec: String,
    val bitrateKbps: Int,
    val sizeBytes: Long,
)

internal fun YouTubeAudioStream.codecLabel(): String = when {
    mimeType.contains("opus") -> "Opus"
    mimeType.contains("mp4a") || container == "mp4" -> "AAC"
    else -> container.uppercase()
}

/** The stream [quality] means in [manifest]. */
internal fun DownloadQuality.pick(manifest: List<YouTubeAudioStream>): YouTubeAudioStream? = when (this) {
    DownloadQuality.HIGH -> manifest.maxByOrNull { it.bitrate }
    DownloadQuality.MEDIUM -> (manifest.filter { it.container == "mp4" }.ifEmpty { manifest })
        .minByOrNull { kotlin.math.abs(it.bitrate.toLong() - 128_000) }
    DownloadQuality.LOW -> manifest.minByOrNull { it.bitrate }
}
