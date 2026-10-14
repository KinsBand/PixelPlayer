package com.theveloper.pixelplay.data.recognition

import java.util.Locale

/** What [GsaResultParser] found on Google's song search result page. */
sealed interface GsaParseResult {
    data class Song(val title: String, val artist: String) : GsaParseResult
    /** Only the search box was readable (e.g. "Whirr Color Change" — artist and title, no separator). */
    data class QueryOnly(val query: String) : GsaParseResult
    data object None : GsaParseResult
}

/**
 * Reads the found song off the Google app's results page. Pure Kotlin (no Android types) so it
 * can be unit tested; fed by [SongSearchResultReader] (accessibility tree) and, via Shizuku,
 * [com.theveloper.pixelplay.data.recognition.shizuku.GsaDumpPoller] (uiautomator dump).
 *
 * Rules, in order:
 * 1. The song panel's "Song by <artist> · <year>" line gives the artist; the title is the nearest
 *    earlier text that isn't page chrome (tabs, nav labels, urls, site names, "⋮").
 * 2. Backup: a YouTube "<artist> - Topic" channel line gives the artist; the title is the
 *    nearest earlier text.
 * 3. Otherwise the search box text, as a [GsaParseResult.QueryOnly].
 */
object GsaResultParser {

    private val SONG_BY = Regex("""^Song by (.+?)(?:\s*·\s*\d{4})?$""", RegexOption.IGNORE_CASE)
    private val TOPIC_SUFFIX = Regex("""\s+[-–—]\s+Topic$""", RegexOption.IGNORE_CASE)
    private val URL_LIKE = Regex(
        """^(https?://|www\.)\S*$|^[\w-]+(\.[\w-]+)+(\s*›.*|/\S*)?$|›""",
        RegexOption.IGNORE_CASE
    )
    private val DURATION = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")

    private val CHROME = setOf(
        // Result tabs
        "ai mode", "all", "images", "videos", "short videos", "shop", "shopping", "news", "web",
        "books", "maps", "flights", "finance", "forums", "lyrics", "listen", "more", "tools",
        // Bottom navigation and toolbar
        "home", "search", "notifications", "activity", "saved", "collections", "discover",
        "back", "close", "clear", "share", "menu", "more options", "settings", "google", "google search",
        "voice search", "search by voice", "search with your camera", "lens", "google lens",
        "account and settings", "profile picture",
        // Site / source names
        "youtube", "youtube music", "genius", "spotify", "apple music", "amazon music", "deezer",
        "soundcloud", "bandcamp", "tidal", "pandora", "shazam", "wikipedia", "musixmatch", "azlyrics",
        "last.fm", "discogs", "allmusic", "facebook", "instagram", "tiktok", "x", "twitter", "reddit",
        // Panel labels
        "song", "songs", "album", "artist", "artists", "released", "genre", "genres", "play",
        "watch", "about", "overview", "people also search for", "people also ask", "other recordings",
        "feedback", "top stories", "see more", "show more", "view all"
    )

    fun parse(texts: List<String>, searchBoxText: String? = null): GsaParseResult {
        val lines = texts.asSequence()
            .flatMap { it.split('\n').asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val searchBox = searchBoxText?.trim()?.takeIf { it.isNotEmpty() }

        // (a) "Song by <artist> · <year>"
        for ((i, line) in lines.withIndex()) {
            val match = SONG_BY.matchEntire(line) ?: continue
            val artist = match.groupValues[1].split(" · ").first().trim()
            if (artist.isEmpty()) continue
            val title = nearestTitleBefore(lines, i, artist, searchBox)
            if (title != null) return GsaParseResult.Song(title, artist)
        }

        // (b) "<artist> - Topic" (YouTube's auto-generated artist channel)
        for ((i, line) in lines.withIndex()) {
            val channel = line.substringAfterLast(" · ").trim()
            if (!TOPIC_SUFFIX.containsMatchIn(channel)) continue
            val artist = channel.replace(TOPIC_SUFFIX, "").trim()
            if (artist.isEmpty()) continue
            val title = nearestTitleBefore(lines, i, artist, searchBox)
            if (title != null) return GsaParseResult.Song(title, artist)
        }

        // (c) Search box only
        return if (searchBox != null) GsaParseResult.QueryOnly(searchBox) else GsaParseResult.None
    }

    private fun nearestTitleBefore(lines: List<String>, index: Int, artist: String, searchBox: String?): String? {
        for (j in index - 1 downTo 0) {
            val candidate = lines[j]
            if (isChrome(candidate)) continue
            if (candidate.equals(artist, ignoreCase = true)) continue
            if (SONG_BY.matches(candidate) || TOPIC_SUFFIX.containsMatchIn(candidate)) continue
            // The search box usually holds "artist title" — not a clean title.
            if (searchBox != null && candidate.equals(searchBox, ignoreCase = true) &&
                candidate.contains(artist, ignoreCase = true)
            ) continue
            return candidate
        }
        return null
    }

    fun isChrome(text: String): Boolean {
        val t = text.trim()
        if (t.length <= 1) return true // "⋮", "·", "×", single glyph icons
        if (t == "⋮" || t == "…" || t == "...") return true
        val lower = t.lowercase(Locale.ROOT)
        if (lower in CHROME) return true
        if (URL_LIKE.containsMatchIn(t)) return true
        if (DURATION.matches(t)) return true
        return false
    }
}
