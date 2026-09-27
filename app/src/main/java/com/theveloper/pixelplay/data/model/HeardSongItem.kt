package com.theveloper.pixelplay.data.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

/**
 * Represents a song suggested in conversation and picked up by ambient listening.
 *
 * @param id Unique identifier for this suggestion entry
 * @param song The resolved [Song] object (local or online)
 * @param isOnlineMatch True if this was resolved from an online stream (e.g. YouTube Music)
 * @param mentionCount Number of times this song has been suggested in the current session
 * @param detectedAtEpochMs Timestamp when the suggestion was captured
 * @param rawSpokenPhrase The original spoken phrase transcribed from audio
 */
@Immutable
@Parcelize
data class HeardSongItem(
    val id: String,
    val song: Song,
    val isOnlineMatch: Boolean = false,
    val mentionCount: Int = 1,
    val detectedAtEpochMs: Long = System.currentTimeMillis(),
    val rawSpokenPhrase: String = ""
) : Parcelable
