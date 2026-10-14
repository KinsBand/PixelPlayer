package com.theveloper.pixelplay.data.recognition.ambient

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.spotify.TrackMatching
import com.theveloper.pixelplay.data.youtube.YouTubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A heard song turned into something the player can actually play. */
data class ResolvedHeardSong(val song: Song, val isLocal: Boolean)

/**
 * Turns a heard title (+ artist) into a playable [Song]: your library first, then YouTube Music
 * search, the same path Search uses. Shared by spoken suggestions and Pixel "Now Playing", so a
 * Heard entry is never a bare title with nothing to play.
 */
@Singleton
class HeardSongResolver @Inject constructor(
    private val musicRepository: MusicRepository,
    private val youTubeRepository: YouTubeRepository,
) {
    suspend fun resolve(suggestion: ExtractedSongSuggestion, relaxed: Boolean = false): ResolvedHeardSong? {
        try {
            val local = withTimeoutOrNull(750) {
                musicRepository.searchSongs(suggestion.cleanTitle).first()
            }.orEmpty()
            AmbientSongMatcher.bestMatch(local, suggestion)?.let { return ResolvedHeardSong(it, isLocal = true) }
            val online = withTimeoutOrNull(8_000) {
                val query = listOfNotNull(suggestion.cleanTitle, suggestion.artist).joinToString(" ")
                youTubeRepository.searchSongs(query)
            }.orEmpty()
            val match = AmbientSongMatcher.bestMatch(online, suggestion)
                ?: if (relaxed) relaxedMatch(online, suggestion) else null
            return match?.takeIf(::isPlayable)?.let { ResolvedHeardSong(it, isLocal = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not resolve heard song")
            return null
        }
    }

    /** Title + artist as reported by Pixel Now Playing (artist may be blank). */
    suspend fun resolve(title: String, artist: String): ResolvedHeardSong? = resolve(
        ExtractedSongSuggestion(
            rawText = "$title $artist".trim(),
            cleanTitle = title,
            artist = artist.takeIf { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
        ),
        relaxed = true
    )

    /**
     * Now Playing titles are exact but catalogue titles often add "(Remastered)" etc. Accept a
     * result whose title starts with the heard title when the artist also matches.
     */
    private fun relaxedMatch(songs: List<Song>, mention: ExtractedSongSuggestion): Song? {
        val title = TrackMatching.normalize(mention.cleanTitle)
        val artist = mention.artist?.let(TrackMatching::normalize)?.takeIf { it.isNotBlank() } ?: return null
        return songs.firstOrNull { song ->
            TrackMatching.normalize(song.title).startsWith(title) &&
                song.artist.split(Regex(",| & | feat[.]? ", RegexOption.IGNORE_CASE))
                    .any { TrackMatching.normalize(it) == artist }
        }
    }

    companion object {
        /** True when the player has a source for [song] (a file, a content URI or a YouTube id). */
        fun isPlayable(song: Song): Boolean =
            song.contentUriString.isNotBlank() || song.path.isNotBlank() || !song.youtubeId.isNullOrBlank()
    }
}
