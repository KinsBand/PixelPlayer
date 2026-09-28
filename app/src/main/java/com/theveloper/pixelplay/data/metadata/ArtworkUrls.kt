package com.theveloper.pixelplay.data.metadata

/**
 * Rewrites cover-art URLs from the catalogues the app uses to ask for a larger image.
 * Every host here serves any size from the same URL pattern, so no extra request is needed.
 *
 * - Google (YouTube Music `lh3/yt3.googleusercontent.com`): `=w544-h544-…` / `=s226-…`
 * - Apple (`*.mzstatic.com`): `/100x100bb.jpg` (serves up to 3000 px)
 * - Deezer (`*.dzcdn.net`): `/500x500-000000-80-0-0.jpg` (1000 px is the documented max)
 *
 * YouTube video frames (`i.ytimg.com/vi/<id>/…`) shown at list size become `mqdefault.jpg`
 * (320×180, no letterbox bars, published for every video) instead of a 480–1280 px frame.
 * Anything else (local `content://` art, unknown hosts) is left untouched.
 */
object ArtworkUrls {
    /** A few shared variants avoid a separate network/disk entry for every layout size. */
    fun forDisplay(url: String, widthPx: Int, heightPx: Int): String {
        if (widthPx <= 0 || heightPx <= 0) return url
        val edge = maxOf(widthPx, heightPx)
        if (edge <= 256) youTubeListFrame(url)?.let { return it }
        val size = when {
            edge <= 128 -> 128
            edge <= 256 -> 256
            edge <= 512 -> 512
            edge <= 1024 -> 1024
            else -> DEFAULT_SIZE
        }
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return url
        // Query parameters can sign a URL. Never rewrite those, or local/unknown sources.
        if (uri.scheme !in setOf("http", "https") || uri.rawQuery != null || uri.rawFragment != null) return url
        val host = uri.host?.lowercase(java.util.Locale.ROOT) ?: return url
        fun onDomain(domain: String) = host == domain || host.endsWith(".$domain")
        if (!listOf("googleusercontent.com", "ggpht.com", "mzstatic.com", "dzcdn.net", "cdn-images.deezer.com").any(::onDomain)) return url
        val sourceSize = sizeInUrl.findAll(url).lastOrNull()?.groupValues?.drop(1)
            ?.firstOrNull { it.isNotEmpty() }?.toIntOrNull() ?: return url
        // Original-quality requests retain large originals but can promote search thumbnails.
        if (edge > 1024) return if (sourceSize < size) upgrade(url, size) ?: url else url
        // Tiny catalogue URLs may be all search supplied. Ask the provider for its larger
        // variant when opening the song, without enlarging downloads for small views.
        return if (sourceSize > size || size == 1024) upgrade(url, size) ?: url else url
    }

    /** Size used for display and for embedding into downloads. */
    const val DEFAULT_SIZE = 1400
    private const val DEEZER_MAX = 1000

    private val googleSize = Regex("""=(?:w\d+-h\d+|s\d+)([^/?#]*)$""")
    private val appleSize = Regex("""/\d+x\d+(bb|cc|-\d+)?\.(jpg|jpeg|png|webp)$""", RegexOption.IGNORE_CASE)
    private val deezerSize = Regex("""/\d+x\d+-(\d+-\d+-\d+-\d+)\.(jpg|png)$""", RegexOption.IGNORE_CASE)
    private val sizeInUrl = Regex("""(?:=w(\d+)-h\d+|=s(\d+)|/(\d+)x\d+)""")

    /** [url] rewritten to ask for a [size] px square, or unchanged if the host isn't known. */
    fun upgrade(url: String?, size: Int = DEFAULT_SIZE): String? {
        if (url.isNullOrBlank()) return url
        return when {
            isGoogle(url) -> googleSize.find(url)?.let { m ->
                // Keep crop / format flags ("-l90-rj"), drop only the size.
                url.substring(0, m.range.first) + "=w$size-h$size" + m.groupValues[1]
            } ?: url
            isApple(url) -> appleSize.replace(url) { m -> "/${size}x${size}bb.${m.groupValues[2]}" }
            isDeezer(url) -> {
                val s = size.coerceAtMost(DEEZER_MAX)
                deezerSize.replace(url) { m -> "/${s}x$s-${m.groupValues[1]}.${m.groupValues[2]}" }
            }
            else -> url
        }
    }

    /**
     * True when [url] is worth replacing with a catalogue cover: missing, a YouTube video frame
     * (16:9 with letterboxing), or a known host serving it smaller than [minSize].
     * Local art (`content://`, files) is never considered low quality.
     */
    fun isLowQuality(url: String?, minSize: Int = 500): Boolean {
        if (url.isNullOrBlank()) return true
        if (!url.startsWith("http")) return false
        if (url.contains("i.ytimg.com") || url.contains("img.youtube.com")) return true
        if (!isGoogle(url) && !isApple(url) && !isDeezer(url)) return false
        val px = sizeInUrl.findAll(url).lastOrNull()?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()
        return px != null && px < minSize
    }

    private val youTubeFrame = Regex(
        """^https://(?:i\d?\.ytimg\.com|img\.youtube\.com)/vi(?:_webp)?/([A-Za-z0-9_-]{11})/(hqdefault|hq720|sddefault|maxresdefault|hq[1-3])\.(?:jpg|webp)(?:\?[^#]*)?$"""
    )

    /**
     * List-size replacement for a large YouTube video frame. The query of a frame URL only
     * signs a custom crop of that public image, so it is safe to drop.
     */
    private fun youTubeListFrame(url: String): String? =
        youTubeFrame.find(url)?.groupValues?.get(1)?.let { id -> "https://i.ytimg.com/vi/$id/mqdefault.jpg" }

    private fun isGoogle(url: String) = url.contains("googleusercontent.com") || url.contains("ggpht.com")
    private fun isApple(url: String) = url.contains("mzstatic.com")
    private fun isDeezer(url: String) = url.contains("dzcdn.net") || url.contains("cdn-images.deezer.com")
}
