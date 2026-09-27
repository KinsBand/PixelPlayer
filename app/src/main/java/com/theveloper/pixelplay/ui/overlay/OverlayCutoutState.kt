package com.theveloper.pixelplay.ui.overlay

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SyncedLine

/**
 * Everything the island draws except the playback position, which lives in its own flow
 * (see OverlayCutoutService.positionFlow) so a position tick never recomposes the island.
 */
@Immutable
data class OverlayCutoutState(
    val expansionLevel: CutoutExpansionLevel = CutoutExpansionLevel.COLLAPSED,
    val isVisible: Boolean = false,
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val lyrics: Lyrics? = null,
    val activeLineIndex: Int = -1,
    /** Per-song lyrics sync offset (ms) — the same value the lyrics sheet and cover use. */
    val lyricsSyncOffsetMs: Int = 0,
    /** Dark album-art scheme for the current song; null until it has been generated. */
    val colorScheme: ColorScheme? = null,
    /**
     * True when the island's window sits above the status bar (accessibility overlay), so
     * the open island hides the time and status icons. False with the plain "Appear on top"
     * window, which Android always draws under the status bar.
     */
    val coversStatusBar: Boolean = true
) {
    val syncedLines: List<SyncedLine>
        get() = lyrics?.synced.orEmpty()

    /** Shown when the song has no synced lyrics. */
    val fallbackLine: String
        get() = song?.album?.takeIf { it.isNotBlank() } ?: song?.artist ?: "PixelPlayer"
}
