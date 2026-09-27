package com.theveloper.pixelplay.data.analysis

import android.content.Context
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.UncategorizedSongRow
import com.theveloper.pixelplay.data.media.AudioMetadataReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenreCategorizerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao
) {
    private val isRunning = AtomicBoolean(false)
    private val processedSongIds = ConcurrentHashMap.newKeySet<Long>()

    suspend fun categorizeSongsIfNeeded() {
        if (!isRunning.compareAndSet(false, true)) {
            Timber.tag(TAG).d("Genre categorization is already running.")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                Timber.tag(TAG).d("Starting genre categorization engine pass...")
                // Light rows of only the songs that need a genre. This used to load every song
                // with all its JSON columns each time the Genres tab opened.
                val uncategorizedSongs = musicDao.getUncategorizedSongRows()
                    .filter { !processedSongIds.contains(it.id) }

                if (uncategorizedSongs.isEmpty()) {
                    Timber.tag(TAG).d("No uncategorized songs found.")
                    return@withContext
                }

                Timber.tag(TAG).d("Categorizing ${uncategorizedSongs.size} uncategorized songs...")

                // Files are read outside the transaction; each batch is written in one
                // transaction so the library observers reload once per batch, not per song.
                var updatedCount = 0
                uncategorizedSongs.chunked(WRITE_BATCH).forEach { batch ->
                    val updates = batch.mapNotNull { song ->
                        val genre = inferOrExtractGenre(song)
                        processedSongIds.add(song.id)
                        genre?.takeIf { it.isNotBlank() }?.let { song.id to it }
                    }
                    if (updates.isNotEmpty()) {
                        musicDao.updateGenresIfBlank(updates)
                        updatedCount += updates.size
                    }
                }

                Timber.tag(TAG).d("Genre categorization complete. Updated $updatedCount songs.")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error during genre categorization process")
            } finally {
                isRunning.set(false)
            }
        }
    }

    private fun inferOrExtractGenre(song: UncategorizedSongRow): String? {
        // 1. Try reading metadata tag from file if local path exists
        if (!song.filePath.isNullOrBlank()) {
            val file = File(song.filePath)
            if (file.exists()) {
                val metadata = AudioMetadataReader.read(file, readArtwork = false)
                val tagGenre = metadata?.genre?.trim()
                if (!tagGenre.isNullOrBlank() && !tagGenre.equals("Unknown", ignoreCase = true)) {
                    return sanitizeGenreName(tagGenre)
                }
            }
        }

        // 2. Heuristic text classification based on title, artistName, albumName, path
        val textToScan = buildString {
            append(song.title.lowercase()).append(" ")
            append((song.artistName).lowercase()).append(" ")
            append((song.albumName).lowercase()).append(" ")
            append((song.filePath).lowercase())
        }

        return classifyTextToGenre(textToScan)
    }

    private fun classifyTextToGenre(text: String): String? {
        return when {
            text.containsAny("metal", "deathmetal", "thrash", "metalcore", "heavy metal") -> "Metal"
            text.containsAny("rock", "punk", "grunge", "alt rock", "alternative", "hard rock", "indie rock") -> "Rock"
            text.containsAny("hip hop", "hip-hop", "rap", "trap", "lo-fi", "lofi", "boombap") -> "Hip Hop"
            text.containsAny("edm", "electro", "house", "techno", "trance", "dubstep", "dnb", "drum and bass", "ambient", "synthwave", "dance") -> "Electronic"
            text.containsAny("pop", "dance pop", "synthpop", "k-pop", "kpop", "j-pop", "jpop") -> "Pop"
            text.containsAny("jazz", "blues", "swing", "bossa", "bossa nova") -> "Jazz"
            text.containsAny("classical", "symphony", "concerto", "piano", "orchestra", "violin", "opera", "bach", "mozart", "beethoven", "chopin") -> "Classical"
            text.containsAny("country", "bluegrass", "folk", "americana", "acoustic") -> "Country"
            text.containsAny("r&b", "rnb", "soul", "funk", "neo-soul", "motown") -> "R&B"
            text.containsAny("reggae", "dub", "ska", "dancehall") -> "Reggae"
            text.containsAny("ost", "soundtrack", "theme", "score", "film score", "game ost") -> "Soundtrack"
            text.containsAny("latin", "salsa", "bachata", "reggaeton") -> "Latin"
            else -> "Other"
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }

    private fun sanitizeGenreName(rawGenre: String): String {
        val trimmed = rawGenre.trim()
        if (trimmed.isEmpty()) return "Other"
        return trimmed.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    companion object {
        private const val TAG = "GenreCategorizerEngine"
        private const val WRITE_BATCH = 250
    }
}
