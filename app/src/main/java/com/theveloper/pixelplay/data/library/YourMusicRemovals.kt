package com.theveloper.pixelplay.data.library

import android.content.Context
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Songs the user deleted from Your Music.
 *
 * Deleting removes the file, the download and the like of every copy, but a streaming service
 * can keep reporting a song as liked (Spotify likes aren't pushed back, and a "Favourites"
 * playlist on YouTube Music is only mirrored). Those songs are hidden here, so a deleted song
 * stays gone from Your Music, Liked Songs and Home. Liking it again brings it back.
 *
 * Keys are the song id and its YouTube video id, so every copy of the recording matches.
 */
@Singleton
class YourMusicRemovals @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _keys = MutableStateFlow(prefs.getStringSet(KEY_REMOVED, emptySet()).orEmpty().toSet())

    /** Keys of every removed song (see [keysOf]). */
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    /** True when [song] (any copy of it) was deleted from Your Music. */
    fun isRemoved(song: Song, removed: Set<String> = _keys.value): Boolean =
        removed.isNotEmpty() && keysOf(song).any { it in removed }

    /** Hides every copy in [songs] from Your Music. */
    fun remove(songs: Collection<Song>) {
        val added = songs.flatMapTo(HashSet()) { keysOf(it) }
        if (added.isEmpty()) return
        _keys.update { it + added }
        save()
    }

    /** Shows [song] again (it was liked or downloaded after being deleted). */
    fun restore(song: Song) {
        if (_keys.value.isEmpty()) return
        val keys = keysOf(song)
        if (keys.none { it in _keys.value }) return
        _keys.update { it - keys }
        save()
    }

    private fun save() {
        prefs.edit().putStringSet(KEY_REMOVED, HashSet(_keys.value)).apply()
    }

    companion object {
        private const val PREFS = "your_music_removals"
        private const val KEY_REMOVED = "removed_keys"

        /** The song's own id plus its YouTube video id, when it has one. */
        fun keysOf(song: Song): Set<String> = buildSet {
            if (song.id.isNotBlank()) add("id:" + song.id)
            videoId(song)?.let { add("yt:$it") }
        }

        private fun videoId(song: Song): String? =
            song.youtubeId?.removePrefix("yt_")?.takeIf { it.isNotBlank() }
                ?: song.id.takeIf { it.startsWith("yt_") }?.removePrefix("yt_")?.takeIf { it.isNotBlank() }
                ?: song.contentUriString.takeIf { it.startsWith("youtube://") }
                    ?.removePrefix("youtube://")?.substringBefore('/')?.removePrefix("yt_")?.takeIf { it.isNotBlank() }
    }
}
