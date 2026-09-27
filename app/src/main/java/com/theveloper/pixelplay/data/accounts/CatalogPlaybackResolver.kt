package com.theveloper.pixelplay.data.accounts

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.spotify.SpotifyToYouTubeResolver
import com.theveloper.pixelplay.utils.KeyedMutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matches catalog songs (Spotify / Apple Music / Deezer) to YouTube audio when the player
 * actually needs them, instead of matching a whole queue before the first song can start.
 *
 * Callers [register] the songs they queue so the match has title, artist, duration and ISRC.
 * An unregistered URI (e.g. a queue restored after process death) can still use a stored match.
 */
@Singleton
class CatalogPlaybackResolver @Inject constructor(
    private val matcher: SpotifyToYouTubeResolver
) {
    internal data class MatchRequest(
        val songId: String,
        val title: String,
        val artist: String,
        val durationMs: Int,
        val isrc: String?
    )

    private val requests = lru<MatchRequest>(MAX_ENTRIES)
    private val matches = lru<String>(MAX_ENTRIES)
    private val locks = KeyedMutex<String>()

    /** Remembers what is needed to match each unmatched catalog song in [songs]. */
    fun register(songs: Iterable<Song>) {
        synchronized(requests) {
            for (song in songs) {
                val uri = song.contentUriString
                if (!CatalogTracks.isCatalogUri(uri)) continue
                song.youtubeId?.let { videoId -> synchronized(matches) { matches[uri] = videoId.removePrefix("yt_") } }
                requests[uri] = MatchRequest(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    durationMs = song.duration.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                    isrc = song.creditsAndRelease.isrc
                )
            }
        }
    }

    /** Video id already known for [uri] without any lookup, or null. */
    fun knownVideoId(uri: String): String? = synchronized(matches) { matches[uri] }

    /** YouTube video id for a catalog [uri], matching it if needed; null when there is no match. */
    suspend fun videoIdFor(uri: String): String? {
        knownVideoId(uri)?.let { return it }
        return locks.withKey(uri) {
            knownVideoId(uri)?.let { return@withKey it }
            val request = synchronized(requests) { requests[uri] }
            val videoId = if (request != null) {
                matcher.resolveSpotifyTrackToVideoId(
                    spotifyId = CatalogTracks.matchKey(request.songId),
                    title = request.title,
                    artistName = request.artist,
                    durationMs = request.durationMs,
                    isrc = request.isrc
                )
            } else {
                // Nothing to search with; a stored match is still usable.
                songIdForUri(uri)?.let { matcher.cachedVideoId(CatalogTracks.matchKey(it)) }
            }
            videoId?.removePrefix("yt_")?.also { id -> synchronized(matches) { matches[uri] = id } }
        }
    }

    internal companion object {
        const val MAX_ENTRIES = 4096
        val SCHEMES = setOf("spotify", "applemusic", "deezer")

        /** `spotify://abc` → `spotify_abc` (the id scheme every catalog repository uses). */
        fun songIdForUri(uri: String): String? {
            val scheme = uri.substringBefore("://", "")
            val id = uri.substringAfter("://", "").substringBefore('/').substringBefore('?')
            return if (scheme in SCHEMES && id.isNotBlank()) "${scheme}_$id" else null
        }

        private fun <V> lru(max: Int) = object : LinkedHashMap<String, V>(64, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>) = size > max
        }
    }
}
