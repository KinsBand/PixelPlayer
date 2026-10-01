package com.theveloper.pixelplay.data.social

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** The friends ticked for the custom blend (you are always in it). Kept across app restarts. */
@Singleton
class CustomBlendSelection @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _selected = MutableStateFlow(prefs.getStringSet(KEY_FRIENDS, emptySet()).orEmpty().toSet())

    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    fun toggle(friendId: String) = set(if (friendId in _selected.value) _selected.value - friendId else _selected.value + friendId)

    fun set(friendIds: Set<String>) {
        _selected.update { friendIds }
        prefs.edit().putStringSet(KEY_FRIENDS, HashSet(friendIds)).apply()
    }

    private companion object {
        const val PREFS = "custom_blend"
        const val KEY_FRIENDS = "friend_ids"
    }
}
