package com.theveloper.pixelplay.data.lyrics.training

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Library › Folders › Sort by › "Synced lyrics badge": marks songs that have a synced .lrc saved
 * next to them (what the word-sync training export uses). Off by default.
 */
object SyncedLyricsBadgePrefs {
    private const val FILE = "folders_view"
    private const val KEY = "synced_lyrics_badge"
    @Volatile private var state: MutableStateFlow<Boolean>? = null

    fun enabled(context: Context): StateFlow<Boolean> = state ?: synchronized(this) {
        state ?: MutableStateFlow(
            context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)
        ).also { state = it }
    }

    fun set(context: Context, value: Boolean) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
        (enabled(context) as MutableStateFlow<Boolean>).value = value
    }
}
