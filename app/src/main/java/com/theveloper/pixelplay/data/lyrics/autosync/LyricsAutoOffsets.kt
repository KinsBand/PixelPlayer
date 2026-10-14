package com.theveloper.pixelplay.data.lyrics.autosync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Automatically measured lyrics offsets for the songs touched this session, keyed by song id.
 *
 * Filled by [LyricsAutoSync] (from its on-disk cache or a fresh measurement) and read by
 * `UserPreferencesRepository.getLyricsSyncOffsetFlow`, so every lyrics surface (sheet, cover,
 * island) follows it with no extra wiring. A manual offset set by the user always wins.
 */
object LyricsAutoOffsets {

    /** An applied automatic offset; [lyricsFingerprint] ties it to the exact lyric timing it was measured on. */
    data class Entry(val offsetMs: Int, val confidence: Float, val lyricsFingerprint: String)

    private const val MAX_ENTRIES = 512

    private val _offsets = MutableStateFlow<Map<String, Entry>>(emptyMap())
    val offsets: StateFlow<Map<String, Entry>> = _offsets.asStateFlow()

    fun get(songId: String): Entry? = _offsets.value[songId]

    /**
     * Songs whose lyrics are currently shown with on-device word alignment. Their timing is
     * already in audio time, so an automatic offset must not be added on top: [put] ignores them.
     */
    private val suppressed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Removes [songId]'s automatic offset and keeps it from coming back (see [suppressed]). */
    fun suppress(songId: String) {
        suppressed += songId
        remove(songId)
    }

    /** Lets automatic offsets apply to [songId] again (they come back on its next measurement). */
    fun unsuppress(songId: String) {
        suppressed -= songId
    }

    fun unsuppressAll() {
        suppressed.clear()
    }

    fun put(songId: String, entry: Entry) {
        if (songId in suppressed) return
        _offsets.update { current ->
            if (current[songId] == entry) return@update current
            // Keep the map small on very long sessions; the disk cache keeps everything.
            val base = if (current.size >= MAX_ENTRIES && songId !in current) {
                current.entries.drop(current.size - MAX_ENTRIES / 2).associate { it.key to it.value }
            } else current
            base + (songId to entry)
        }
    }

    fun remove(songId: String) {
        _offsets.update { if (songId in it) it - songId else it }
    }

    fun clear() {
        _offsets.value = emptyMap()
    }
}
