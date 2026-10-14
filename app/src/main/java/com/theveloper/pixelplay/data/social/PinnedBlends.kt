package com.theveloper.pixelplay.data.social

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blends liked into Your playlists. A pinned blend stays a live daily mix (rebuilt every day);
 * only which blend is pinned is stored, on this device, so it works offline too.
 * Keys: [friendKey] for a friend's blend, [CUSTOM] for the custom blend.
 */
@Singleton
class PinnedBlends @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _keys = MutableStateFlow(prefs.getStringSet(KEY_PINNED, emptySet()).orEmpty().toSet())

    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    fun setPinned(key: String, pinned: Boolean) {
        _keys.update { if (pinned) it + key else it - key }
        prefs.edit().putStringSet(KEY_PINNED, HashSet(_keys.value)).apply()
    }

    companion object {
        private const val PREFS = "pinned_blends"
        private const val KEY_PINNED = "keys"
        const val CUSTOM = "custom"
        fun friendKey(friendId: String): String = "friend:$friendId"
    }
}
