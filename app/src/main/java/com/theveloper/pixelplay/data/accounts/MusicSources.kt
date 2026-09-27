package com.theveloper.pixelplay.data.accounts

/** Names and links for the streaming services a connected playlist can come from. */
object MusicSources {
    const val SPOTIFY = "SPOTIFY"
    const val YOUTUBE_MUSIC = "YOUTUBE_MUSIC"
    const val APPLE_MUSIC = "APPLE_MUSIC"

    fun displayName(source: String): String = when (source.uppercase()) {
        SPOTIFY -> "Spotify"
        APPLE_MUSIC -> "Apple Music"
        YOUTUBE_MUSIC, "YOUTUBE" -> "YouTube Music"
        else -> source
    }

    /** Web link for a playlist on its service, or null when there isn't one. */
    fun playlistUrl(source: String, remoteId: String): String? {
        if (remoteId.isBlank()) return null
        return when (source.uppercase()) {
            SPOTIFY -> "https://open.spotify.com/playlist/$remoteId"
            YOUTUBE_MUSIC, "YOUTUBE" -> "https://music.youtube.com/playlist?list=$remoteId"
            APPLE_MUSIC -> if (remoteId.startsWith("p.")) "https://music.apple.com/library/playlist/$remoteId"
                else "https://music.apple.com/us/playlist/$remoteId"
            else -> null
        }
    }
}

/**
 * Songs from Spotify, Apple Music and Deezer only carry catalog metadata; the actual audio is
 * matched on YouTube when they're played or downloaded. These helpers let every code path treat
 * them the same way. (Artist pages build `applemusic_` songs from iTunes and `deezer_` songs from
 * Deezer's discography.)
 */
object CatalogTracks {
    private val URI_PREFIXES = listOf("spotify:", "applemusic:", "deezer:")

    /** True for `spotify://…` / `applemusic://…` / `deezer://…` songs that still need an audio match. */
    fun isCatalogUri(uri: String): Boolean = URI_PREFIXES.any { uri.startsWith(it) }

    fun isCatalogSongId(id: String): Boolean =
        id.startsWith("spotify_") || id.startsWith("applemusic_") || id.startsWith("deezer_")

    /**
     * Key used to cache the audio match. Spotify keeps its bare track id (existing caches stay
     * valid); Apple Music ids are prefixed so they can never collide.
     */
    fun matchKey(songId: String): String = when {
        songId.startsWith("applemusic_") -> "am:" + songId.removePrefix("applemusic_")
        songId.startsWith("deezer_") -> "dz:" + songId.removePrefix("deezer_")
        else -> songId.removePrefix("spotify_")
    }

    fun sourceType(songId: String): String = when {
        songId.startsWith("applemusic_") -> "applemusic"
        songId.startsWith("deezer_") -> "deezer"
        songId.startsWith("spotify_") -> "spotify"
        else -> "youtube"
    }
}
