package com.theveloper.pixelplay.data.social

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Who a queued song came from: shown on its queue row as the friend's picture and name. */
data class FriendAttribution(
    val friendId: String = "",
    val friendName: String = "",
    val avatarUrl: String? = null,
    val taggedAt: Long = 0,
)

/**
 * Remembers which songs were played or queued because of a friend (following them, playing or
 * queueing their songs, their history, the Friends Mix, New from friends), keyed by song id, so
 * queue rows can show the friend. Keyed by song rather than by queue entry, so it survives
 * reorders, undo and a restored queue without touching the player's media items.
 * Tags expire after [TTL_MS].
 */
object FriendQueueAttribution {
    private const val PREFS = "friend_queue_attribution"
    private const val KEY = "entries"
    private const val TTL_MS = 24L * 60 * 60 * 1000
    private const val MAX_ENTRIES = 500

    private val gson = Gson()
    private val _entries = MutableStateFlow<Map<String, FriendAttribution>>(emptyMap())
    val entries: StateFlow<Map<String, FriendAttribution>> = _entries.asStateFlow()
    @Volatile private var prefs: android.content.SharedPreferences? = null

    /** Call once at startup so tags survive a restart. */
    fun attach(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val now = System.currentTimeMillis()
        // Filtered inside the try: a value that isn't really a FriendAttribution (generic types
        // lost in a release build) must be dropped here, not crash a queue row later.
        val stored: Map<String, FriendAttribution> = try {
            p.getString(KEY, null)?.let {
                gson.fromJson<Map<String, FriendAttribution>>(it, object : TypeToken<Map<String, FriendAttribution>>() {}.type)
            }.orEmpty()
                .mapValues { (_, value) -> (value as FriendAttribution).let { a -> a.copy() } }
                .filterValues { now - it.taggedAt < TTL_MS }
        } catch (_: Throwable) { emptyMap() }
        _entries.update { current -> stored + current }
    }

    fun tag(songIds: Collection<String>, friendId: String, friendName: String, avatarUrl: String?) {
        if (songIds.isEmpty()) return
        val now = System.currentTimeMillis()
        val value = FriendAttribution(friendId, friendName, avatarUrl, now)
        _entries.update { current ->
            val fresh = current.filterValues { now - it.taggedAt < TTL_MS } + songIds.associateWith { value }
            if (fresh.size <= MAX_ENTRIES) fresh
            else fresh.entries.sortedByDescending { it.value.taggedAt }.take(MAX_ENTRIES).associate { it.key to it.value }
        }
        persist()
    }

    /** Tags each song with its own friend (Friends Mix, New from friends). */
    fun tagEach(tags: Map<String, FriendAttribution>) {
        if (tags.isEmpty()) return
        val now = System.currentTimeMillis()
        _entries.update { current ->
            current.filterValues { now - it.taggedAt < TTL_MS } + tags.mapValues { it.value.copy(taggedAt = now) }
        }
        persist()
    }

    fun forSong(songId: String): FriendAttribution? = _entries.value[songId]

    /** Drops tags for friends that were removed. */
    fun forgetFriends(friendIds: Collection<String>) {
        _entries.update { current -> current.filterValues { it.friendId !in friendIds } }
        persist()
    }

    private fun persist() {
        val p = prefs ?: return
        val snapshot = _entries.value
        try { p.edit().putString(KEY, gson.toJson(snapshot)).apply() } catch (_: Exception) { }
    }
}
