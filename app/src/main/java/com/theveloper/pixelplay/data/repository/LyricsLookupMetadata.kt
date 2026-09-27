package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.model.Song

/**
 * Online (YouTube / YouTube Music / Spotify) songs carry video-style metadata that lyric
 * providers never match: "Artist - Topic" / "ArtistVEVO" channels, "Artist - Song (Official
 * Video)" titles and the placeholder album "YouTube Music" (which made LRCLIB's exact lookup
 * return 404 for every streamed song). This builds a lookup-only copy with provider-friendly
 * title/artist/album. The id is unchanged, so persistence still keys on the real song.
 */
internal object LyricsLookupMetadata {
    private val placeholderAlbums = setOf(
        "youtube music", "youtube", "unknown album", "<unknown>", "unknown", "spotify", "single"
    )
    private val channelSuffix = Regex("""(?i)\s*(?:-\s*topic|vevo|official(?:\s+channel)?|\(official\))\s*$""")
    private val videoNoise = Regex(
        """(?i)\s*[\(\[【]\s*(?:official\s*)?(?:music\s*|lyrics?\s*|lyric\s*|audio\s*|visuali[sz]er\s*|hd\s*|hq\s*|4k\s*|video\s*|mv\s*|m/v\s*|clip\s*|explicit\s*|clean\s*)+[^\)\]】]*[\)\]】]"""
    )
    private val bareNoise = Regex(
        """(?i)\s+(?:\||//)\s+.*$|\s+-\s+(?:official\s+)?(?:music\s+video|lyric\s+video|lyrics|audio|visuali[sz]er)\s*$|\s+(?:official\s+)?(?:music\s+video|lyric\s+video|audio)\s*$"""
    )
    private val titleSplit = Regex("""\s+[-–—]\s+""")

    fun cleanArtist(raw: String): String {
        val trimmed = raw.trim()
        val cleaned = trimmed.replace(channelSuffix, "").trim().trim(',', '-', ' ')
        // "TaylorSwiftVEVO" -> "TaylorSwift" -> "Taylor Swift"
        return if (cleaned != trimmed && ' ' !in cleaned && cleaned.any { it.isLowerCase() }) {
            cleaned.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
        } else cleaned
    }

    fun cleanTitle(raw: String): String =
        raw.trim().replace(videoNoise, "").replace(bareNoise, "").trim().trim('"', '“', '”', '\'', ' ')

    private fun key(value: String) = value.lowercase().filter { it.isLetterOrDigit() }

    fun forLookup(song: Song): Song {
        val online = song.youtubeId != null || song.id.startsWith("yt_") || song.id.startsWith("spotify_") ||
            song.contentUriString.startsWith("youtube://")
        val album = song.album.trim().takeUnless { it.lowercase() in placeholderAlbums }.orEmpty()
        if (!online) return if (album == song.album) song else song.copy(album = album)

        var artist = cleanArtist(song.displayArtist.ifBlank { song.artist })
        var title = cleanTitle(song.title)
        val parts = title.split(titleSplit, limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            val left = parts[0].trim()
            val artistKey = key(artist)
            val leftKey = key(left)
            // "Artist - Song": drop the artist prefix, or adopt it when the uploader is not the artist.
            if (artistKey.isBlank() || leftKey == artistKey || leftKey.contains(artistKey) || artistKey.contains(leftKey)) {
                title = cleanTitle(parts[1])
                if (artistKey.isBlank()) artist = left
            }
        }
        if (title.isBlank()) title = song.title.trim()
        if (artist.isBlank()) artist = song.artist.trim()
        return song.copy(title = title, artist = artist, artists = emptyList(), album = album)
    }
}

internal fun Song.forLyricsLookup(): Song = LyricsLookupMetadata.forLookup(this)
