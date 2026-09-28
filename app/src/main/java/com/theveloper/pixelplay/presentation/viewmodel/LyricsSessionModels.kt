package com.theveloper.pixelplay.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.media3.common.Player
import com.theveloper.pixelplay.data.model.Song

/** The four things a song action sheet can do with a song. */
enum class SongQuickAction(val confirmation: String) {
    PLAY("Playing now"),
    NEXT("Playing next"),
    SOON("Playing soon"),
    QUEUE("Added to queue"),
}

/**
 * The lyrics screen's + button is being used: the user is in Search picking a song. While this is
 * set, tapping a song opens the action sheet instead of playing it.
 *
 * [originDestinationId] is the navigation destination the user was on when they opened the
 * player, so choosing an action can pop Search (and anything opened from it) off again.
 */
@Immutable
data class AddSongSession(
    val originDestinationId: Int?,
    val startedAt: Long = System.currentTimeMillis(),
)

/** A short "Playing next ✓" pill on the lyrics screen. [at] makes repeated actions distinct. */
@Immutable
data class LyricsConfirmation(val action: SongQuickAction, val at: Long) {
    val text: String get() = "${action.confirmation} ✓"
}

/**
 * The song after the current one in play order: the entry after the current queue position,
 * the first entry again with repeat all, or null at the end of the queue.
 *
 * [currentIndex] is trusted only when it still points at [currentSongId]; otherwise the song is
 * looked up (the queue flow can briefly lag the player state during a change).
 */
fun resolveNextUpSong(
    queue: List<Song>,
    currentSongId: String?,
    currentIndex: Int,
    repeatMode: Int,
): Song? {
    if (queue.isEmpty() || currentSongId == null) return null
    val index = if (queue.getOrNull(currentIndex)?.id == currentSongId) {
        currentIndex
    } else {
        queue.indexOfFirst { it.id == currentSongId }
    }
    if (index < 0) return null
    queue.getOrNull(index + 1)?.let { return it }
    return if (repeatMode == Player.REPEAT_MODE_ALL && queue.size > 1) queue.first() else null
}
