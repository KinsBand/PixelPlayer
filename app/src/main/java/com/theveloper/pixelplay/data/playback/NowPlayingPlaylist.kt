package com.theveloper.pixelplay.data.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which playlist the current queue was started from. Set by every playlist play (own or a
 * friend's pinned one) and cleared by any non-playlist play, so the library can mark it.
 * [queueName] lets the UI drop a stale match when some other path replaces the queue.
 */
object NowPlayingPlaylist {
    data class Entry(val playlistId: String, val queueName: String)

    private val _current = MutableStateFlow<Entry?>(null)
    val current: StateFlow<Entry?> = _current.asStateFlow()

    fun onQueueStarted(playlistId: String?, queueName: String) {
        _current.value = playlistId?.takeIf { it.isNotBlank() }?.let { Entry(it, queueName) }
    }
}
