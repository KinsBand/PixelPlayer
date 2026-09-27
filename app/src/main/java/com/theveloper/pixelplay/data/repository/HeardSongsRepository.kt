package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.model.HeardSongItem
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the in-memory state of songs recognized from conversation during playback.
 * Handles deduplication, social party upvoting (mention counters), and item lifecycle.
 */
@Singleton
class HeardSongsRepository @Inject constructor() {
    private val dismissedUntil = mutableMapOf<String, Long>()
    private fun songKey(song: Song) = "${song.title.trim().lowercase(java.util.Locale.ROOT)}|${song.artist.trim().lowercase(java.util.Locale.ROOT)}"

    private val _heardSongs = MutableStateFlow<List<HeardSongItem>>(emptyList())
    val heardSongs: StateFlow<List<HeardSongItem>> = _heardSongs.asStateFlow()

    private val _latestHeardEvent = MutableSharedFlow<HeardSongItem>(extraBufferCapacity = 8)
    val latestHeardEvent: SharedFlow<HeardSongItem> = _latestHeardEvent.asSharedFlow()

    /**
     * Adds a newly recognized song to the heard list, or bumps & increments its
     * mention counter if already present in the list.
     *
     * @param song The resolved song track
     * @param isOnlineMatch True if resolved from YouTube Music or external streaming
     * @param rawPhrase The spoken phrase captured
     * @param currentPlayingSongId The ID of the currently playing track (to skip if identical)
     * @return True if the suggestion was added or upvoted, false if ignored
     */
    @Synchronized
    fun addOrUpvote(
        song: Song,
        isOnlineMatch: Boolean = false,
        rawPhrase: String = "",
        currentPlayingSongId: String? = null
    ): Boolean {
        val now = System.nanoTime() / 1_000_000
        dismissedUntil.entries.removeAll { it.value <= now }
        if (songKey(song) in dismissedUntil) return false
        if (currentPlayingSongId != null && song.id == currentPlayingSongId) {
            Timber.d("AmbientSuggestion: Ignored '%s' because it is already actively playing", song.title)
            return false
        }

        var resultItem: HeardSongItem? = null

        _heardSongs.update { currentList ->
            val existingIndex = currentList.indexOfFirst {
                it.song.id == song.id || (it.song.title.equals(song.title, ignoreCase = true) &&
                        it.song.artist.equals(song.artist, ignoreCase = true))
            }

            if (existingIndex >= 0) {
                val existing = currentList[existingIndex]
                val updated = existing.copy(
                    mentionCount = existing.mentionCount + 1,
                    detectedAtEpochMs = System.currentTimeMillis(),
                    rawSpokenPhrase = rawPhrase.ifBlank { existing.rawSpokenPhrase }
                )
                resultItem = updated
                Timber.d("AmbientSuggestion: Upvoted '%s' to %d mentions", song.title, updated.mentionCount)
                // Move updated item to index 0
                val newList = currentList.toMutableList()
                newList.removeAt(existingIndex)
                newList.add(0, updated)
                newList
            } else {
                val newItem = HeardSongItem(
                    id = UUID.randomUUID().toString(),
                    song = song,
                    isOnlineMatch = isOnlineMatch,
                    mentionCount = 1,
                    detectedAtEpochMs = System.currentTimeMillis(),
                    rawSpokenPhrase = rawPhrase
                )
                resultItem = newItem
                Timber.d("AmbientSuggestion: Added new heard song '%s' by %s", song.title, song.artist)
                (listOf(newItem) + currentList).take(50)
            }
        }

        resultItem?.let { _latestHeardEvent.tryEmit(it) }
        return true
    }

    /**
     * Removes a suggestion by its ID (e.g. when dismissed or added to queue).
     */
    @Synchronized
    fun dismiss(id: String) {
        _heardSongs.value.firstOrNull { it.id == id }?.let {
            dismissedUntil[songKey(it.song)] = System.nanoTime() / 1_000_000 + 5 * 60_000L
        }
        _heardSongs.update { current ->
            current.filterNot { it.id == id }
        }
    }

    /**
     * Clears all heard suggestions for the current session.
     */
    @Synchronized
    fun clearAll() {
        val until = System.nanoTime() / 1_000_000 + 5 * 60_000L
        _heardSongs.value.forEach { dismissedUntil[songKey(it.song)] = until }
        _heardSongs.value = emptyList()
    }
}
