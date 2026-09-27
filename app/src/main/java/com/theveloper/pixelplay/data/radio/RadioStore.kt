package com.theveloper.pixelplay.data.radio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * The radio's memory: six preset buttons, favourites, recently played, a hand-picked home
 * location and the last scope/view. Kept in SharedPreferences as JSON (station snapshots, so
 * presets still play when the directory is unreachable); one shared instance for the whole app.
 */
class RadioStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("radio", Context.MODE_PRIVATE)

    private val _presets = MutableStateFlow(loadPresets())
    /** Always [PRESET_COUNT] long; null = empty button. */
    val presets: StateFlow<List<RadioStation?>> = _presets.asStateFlow()

    private val _favorites = MutableStateFlow(loadList(KEY_FAVORITES))
    val favorites: StateFlow<List<RadioStation>> = _favorites.asStateFlow()

    private val _recents = MutableStateFlow(loadList(KEY_RECENTS))
    val recents: StateFlow<List<RadioStation>> = _recents.asStateFlow()

    var lastScope: RadioScope
        get() = enumOr(prefs.getString(KEY_SCOPE, null), RadioScope.LOCAL)
        set(value) = prefs.edit().putString(KEY_SCOPE, value.name).apply()

    var lastViewMode: RadioViewMode
        get() = enumOr(prefs.getString(KEY_VIEW, null), RadioViewMode.LIST)
        set(value) = prefs.edit().putString(KEY_VIEW, value.name).apply()

    /** A home the user picked by hand; overrides the device location when set. */
    var manualHome: RadioHome?
        get() {
            val raw = prefs.getString(KEY_HOME, null) ?: return null
            return runCatching {
                val o = JSONObject(raw)
                RadioHome(
                    countryCode = o.optString("cc").takeIf { it.isNotBlank() },
                    countryName = o.optString("country").takeIf { it.isNotBlank() },
                    state = o.optString("state").takeIf { it.isNotBlank() },
                    isManual = true,
                )
            }.getOrNull()
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_HOME).apply()
            } else {
                val o = JSONObject()
                value.countryCode?.let { o.put("cc", it) }
                value.countryName?.let { o.put("country", it) }
                value.state?.let { o.put("state", it) }
                prefs.edit().putString(KEY_HOME, o.toString()).apply()
            }
        }

    fun isFavorite(uuid: String): Boolean = _favorites.value.any { it.uuid == uuid }

    fun toggleFavorite(station: RadioStation) = synchronized(this) {
        val list = _favorites.value
        val next = if (list.any { it.uuid == station.uuid }) list.filterNot { it.uuid == station.uuid } else listOf(station) + list
        _favorites.value = next
        saveList(KEY_FAVORITES, next)
    }

    fun setPreset(slot: Int, station: RadioStation?) = synchronized(this) {
        if (slot !in 0 until PRESET_COUNT) return@synchronized
        val next = _presets.value.toMutableList().also { it[slot] = station }
        _presets.value = next
        prefs.edit().putString(KEY_PRESETS, JSONArray().apply {
            next.forEach { put(it?.let(RadioBrowserRepository::toJson) ?: JSONObject.NULL) }
        }.toString()).apply()
    }

    fun addRecent(station: RadioStation) = synchronized(this) {
        val next = (listOf(station) + _recents.value.filterNot { it.uuid == station.uuid }).take(MAX_RECENTS)
        _recents.value = next
        saveList(KEY_RECENTS, next)
    }

    private fun loadPresets(): List<RadioStation?> {
        val slots = MutableList<RadioStation?>(PRESET_COUNT) { null }
        runCatching {
            val arr = JSONArray(prefs.getString(KEY_PRESETS, "[]") ?: "[]")
            for (i in 0 until minOf(arr.length(), PRESET_COUNT)) {
                slots[i] = arr.optJSONObject(i)?.let(RadioBrowserRepository::parseStation)
            }
        }
        return slots
    }

    private fun loadList(key: String): List<RadioStation> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(RadioBrowserRepository::parseStation) }
    }.getOrDefault(emptyList())

    private fun saveList(key: String, list: List<RadioStation>) {
        prefs.edit().putString(key, JSONArray().apply {
            list.forEach { put(RadioBrowserRepository.toJson(it)) }
        }.toString()).apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

    companion object {
        const val PRESET_COUNT = 6
        private const val MAX_RECENTS = 20
        private const val KEY_PRESETS = "presets"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENTS = "recents"
        private const val KEY_SCOPE = "scope"
        private const val KEY_VIEW = "view"
        private const val KEY_HOME = "manual_home"

        @Volatile
        private var instance: RadioStore? = null

        fun get(context: Context): RadioStore =
            instance ?: synchronized(this) { instance ?: RadioStore(context).also { instance = it } }
    }
}
