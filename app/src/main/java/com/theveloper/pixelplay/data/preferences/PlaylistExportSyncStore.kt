package com.theveloper.pixelplay.data.preferences

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class RemoteSyncTarget(
    val service: String, // "SPOTIFY" or "YOUTUBE_MUSIC"
    val remotePlaylistId: String,
    val autoSync: Boolean = true,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Singleton
class PlaylistExportSyncStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("playlist_export_sync_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<Map<String, RemoteSyncTarget>>() {}.type

    private val _syncTargets = MutableStateFlow<Map<String, RemoteSyncTarget>>(loadFromPrefs())
    val syncTargets: StateFlow<Map<String, RemoteSyncTarget>> = _syncTargets.asStateFlow()

    private fun loadFromPrefs(): Map<String, RemoteSyncTarget> {
        val raw = prefs.getString(KEY_TARGETS, null) ?: return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, RemoteSyncTarget>>(raw, type)
        }.getOrNull() ?: emptyMap()
    }

    private fun saveToPrefs(targets: Map<String, RemoteSyncTarget>) {
        val raw = gson.toJson(targets)
        prefs.edit().putString(KEY_TARGETS, raw).apply()
    }

    fun getSyncTarget(playlistId: String): RemoteSyncTarget? = _syncTargets.value[playlistId]

    fun setSyncTarget(playlistId: String, target: RemoteSyncTarget?) {
        _syncTargets.update { current ->
            val next = if (target != null) {
                current + (playlistId to target)
            } else {
                current - playlistId
            }
            saveToPrefs(next)
            next
        }
    }

    fun setAutoSync(playlistId: String, enabled: Boolean) {
        val existing = _syncTargets.value[playlistId] ?: return
        setSyncTarget(playlistId, existing.copy(autoSync = enabled))
    }

    fun markSynced(playlistId: String) {
        val existing = _syncTargets.value[playlistId] ?: return
        setSyncTarget(playlistId, existing.copy(lastSyncedAt = System.currentTimeMillis()))
    }

    fun removeSyncTarget(playlistId: String) {
        setSyncTarget(playlistId, null)
    }

    companion object {
        private const val KEY_TARGETS = "sync_targets_json"
    }
}
