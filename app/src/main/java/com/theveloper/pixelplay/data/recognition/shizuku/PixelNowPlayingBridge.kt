package com.theveloper.pixelplay.data.recognition.shizuku

import com.theveloper.pixelplay.data.model.HeardSongItem
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.HeardSongsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PixelNowPlayingBridge @Inject constructor(
    private val shizukuManager: ShizukuManager,
    private val heardSongsRepository: HeardSongsRepository
) {
    companion object {
        const val NOW_PLAYING_URI = "content://com.google.intelligence.sense.ambientmusic.history/entries"

        /**
         * Pure parsing function for `content query` command output.
         */
        fun parseContentQueryOutput(rawOutput: String): List<HeardSongItem> {
            val items = mutableListOf<HeardSongItem>()
            if (rawOutput.isBlank()) return items

            val rows = rawOutput.split("Row: ")
            for (row in rows) {
                if (row.isBlank()) continue
                val fields = mutableMapOf<String, String>()
                // Matches key=value tokens, handling comma separators
                val tokens = row.split(", ")
                for (token in tokens) {
                    val eqIndex = token.indexOf('=')
                    if (eqIndex > 0) {
                        val key = token.substring(0, eqIndex).trim()
                        val value = token.substring(eqIndex + 1).trim()
                        fields[key] = value
                    }
                }

                val title = fields["song_title"] ?: fields["title"] ?: fields["name"]
                val artist = fields["artist_name"] ?: fields["artist"] ?: ""
                val timestampStr = fields["timestamp"] ?: fields["detected_time"] ?: fields["date"]
                val timestamp = timestampStr?.toLongOrNull() ?: System.currentTimeMillis()

                if (!title.isNullOrBlank()) {
                    val id = fields["_id"] ?: fields["song_id"] ?: UUID.randomUUID().toString()
                    val dummySong = Song.emptySong().copy(
                        id = id,
                        title = title,
                        artist = artist.ifBlank { "Unknown Artist" }
                    )
                    items.add(
                        HeardSongItem(
                            id = id,
                            song = dummySong,
                            isOnlineMatch = true,
                            mentionCount = 1,
                            detectedAtEpochMs = timestamp,
                            rawSpokenPhrase = "Pixel Now Playing"
                        )
                    )
                }
            }
            return items.sortedByDescending { it.detectedAtEpochMs }
        }
    }

    /**
     * Queries Pixel Now Playing database via Shizuku shell.
     */
    suspend fun fetchNowPlayingHistory(): List<HeardSongItem> = withContext(Dispatchers.IO) {
        if (shizukuManager.status.value != ShizukuStatus.READY) {
            return@withContext heardSongsRepository.heardSongs.value
        }

        val result = shizukuManager.executeShell("content query --uri $NOW_PLAYING_URI")
        result.fold(
            onSuccess = { rawOutput ->
                val parsed = parseContentQueryOutput(rawOutput)
                // Merge into heardSongsRepository
                for (item in parsed) {
                    heardSongsRepository.addOrUpvote(
                        song = item.song,
                        isOnlineMatch = true,
                        rawPhrase = "Now Playing"
                    )
                }
                parsed.ifEmpty { heardSongsRepository.heardSongs.value }
            },
            onFailure = { error ->
                Timber.w(error, "PixelNowPlayingBridge: Failed to query HistoryContentProvider")
                heardSongsRepository.heardSongs.value
            }
        )
    }

    /**
     * Triggers Pixel's on-demand ambient audio detection broadcast.
     */
    suspend fun triggerAmbientDetection(): Boolean = withContext(Dispatchers.IO) {
        if (shizukuManager.status.value != ShizukuStatus.READY) return@withContext false
        val cmd = "am broadcast -a com.google.android.as.ambientmusic.ACTION_TRIGGER_DETECTION"
        val result = shizukuManager.executeShell(cmd)
        result.isSuccess
    }
}
