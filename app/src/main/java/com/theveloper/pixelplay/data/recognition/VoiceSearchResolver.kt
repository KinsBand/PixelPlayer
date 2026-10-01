package com.theveloper.pixelplay.data.recognition

import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.youtube.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceSearchResolver @Inject constructor(
    private val musicRepository: MusicRepository,
    private val youTubeRepository: YouTubeRepository,
    private val lyricsRepository: LyricsRepository,
    private val favoritesDao: FavoritesDao
) {
    /**
     * Finds the song in your library first, then online (YouTube Music). Null when neither has
     * it. No lyrics are fetched, so this is cheap enough for list covers and taps.
     */
    suspend fun findSong(title: String, artist: String): Song? = withContext(Dispatchers.IO) {
        val query = "$title $artist".trim()
        Timber.d("VoiceSearchResolver: Resolving query '%s'", query)
        var matchedSong: Song? = null
        try {
            val localSongs = musicRepository.getAudioFiles().firstOrNull().orEmpty()
            matchedSong = localSongs.firstOrNull { song ->
                song.title.equals(title, ignoreCase = true) &&
                    (artist.isBlank() || song.artist.contains(artist, ignoreCase = true))
            } ?: localSongs.firstOrNull { song ->
                song.title.contains(title, ignoreCase = true) &&
                    (artist.isBlank() || song.artist.contains(artist, ignoreCase = true))
            }
        } catch (e: Exception) {
            Timber.w(e, "VoiceSearchResolver: Error during local search")
        }
        if (matchedSong == null) {
            try {
                matchedSong = youTubeRepository.searchSongs(query).firstOrNull()
            } catch (e: Exception) {
                Timber.w(e, "VoiceSearchResolver: Error during online search")
            }
        }
        matchedSong?.let { found ->
            val isFav = runCatching { favoritesDao.isFavorite(found.id) }.getOrNull() ?: false
            found.copy(isFavorite = isFav)
        }
    }

    /**
     * Resolves recognized song information into a concrete [Song] with favorite state
     * and fetches any available synced [Lyrics].
     */
    suspend fun resolve(title: String, artist: String): Pair<Song, Lyrics?> = withContext(Dispatchers.IO) {
        val matchedSong = findSong(title, artist)

        // 3. Fallback placeholder if completely unresolved
        val resolvedSong = matchedSong ?: Song.emptySong().copy(
            id = UUID.randomUUID().toString(),
            title = title,
            artist = artist.ifBlank { "Unknown Artist" },
            genre = "Music"
        )

        // 4. Check favorite status
        val isFav = favoritesDao.isFavorite(resolvedSong.id) ?: false
        val finalSong = resolvedSong.copy(isFavorite = isFav)

        // 5. Load synced lyrics
        val lyrics: Lyrics? = try {
            lyricsRepository.getLyrics(finalSong) ?: run {
                val remoteSearch = lyricsRepository.searchRemoteByQuery(title, artist)
                remoteSearch.getOrNull()?.second?.firstOrNull()?.let { searchResult ->
                    lyricsRepository.fetchFromRemote(finalSong).getOrNull()?.first
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "VoiceSearchResolver: Could not fetch lyrics for '%s'", title)
            null
        }

        Pair(finalSong, lyrics)
    }
}
