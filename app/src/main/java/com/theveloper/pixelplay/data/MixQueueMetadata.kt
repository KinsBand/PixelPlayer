package com.theveloper.pixelplay.data

import android.os.Bundle
import androidx.media3.common.MediaItem

/** Metadata travels with each queue occurrence, including across MediaController IPC. */
object MixQueueMetadata {
    const val DECISION = "pixelplay.mix.decision"
    const val SESSION = "pixelplay.mix.session"
    const val RECORDING = "pixelplay.mix.recording"
    const val AUTOMATIC = "pixelplay.mix.automatic"
    const val FILTER_PICK = "pixelplay.mix.filterPick"
    fun automatic(item: MediaItem): Boolean = item.mediaMetadata.extras?.getBoolean(AUTOMATIC, false) == true

    /**
     * Songs added by a one-shot mix button in the queue (Similar / a vibe filter). They are not
     * the mix's own songs (a re-plan never removes them) and not hand-picked songs either (they
     * don't steer the continuous mix), so the filter only applies when the button is pressed.
     */
    fun filterPick(item: MediaItem): Boolean = item.mediaMetadata.extras?.getBoolean(FILTER_PICK, false) == true

    /** Marks [item] as added by a one-shot queue mix button; see [filterPick]. */
    fun markFilterPick(item: MediaItem): MediaItem {
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply { putBoolean(FILTER_PICK, true) }
        return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
    }

    /** Anything the user chose themselves, one song at a time (not the mix, not a mix button). */
    fun userPick(item: MediaItem): Boolean = !automatic(item) && !filterPick(item)
    fun tag(item: MediaItem, decision: String, session: String, recording: String): MediaItem {
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply {
            putBoolean(AUTOMATIC, true)
            putString(DECISION, decision)
            putString(SESSION, session)
            putString(RECORDING, recording)
        }
        return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
    }
}
