package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.library.LikedSongsRepository
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.presentation.library.MusicVibeFilters
import com.theveloper.pixelplay.presentation.library.VibeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class YourMusicControls(
    val query: String = "",
    val likedOnly: Boolean = false,
    /** Only one vibe filter can be on at a time; it turns the list into a generated mix. */
    val selectedFilterId: String? = null,
    val customFilters: List<VibeFilter> = emptyList(),
    /** Changes every time a filter is picked or "New mix" is tapped, so each mix is fresh. */
    val mixSeed: Long = System.nanoTime()
)

data class YourMusicUiState(
    val songs: List<Song> = emptyList(),
    val totalCount: Int = 0,
    val likedCount: Int = 0,
    val isLoading: Boolean = true,
    val activeMix: VibeFilter? = null,
    val controls: YourMusicControls = YourMusicControls()
) {
    val hasActiveFilters: Boolean
        get() = controls.likedOnly || controls.selectedFilterId != null || controls.query.isNotBlank()
}

/**
 * Backs the combined "Your Music" screen: every song + every like (local favorites, YouTube Music
 * and Spotify likes, and any playlist named like "Favourites").
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class YourMusicViewModel @Inject constructor(
    musicRepository: MusicRepository,
    likedSongsRepository: LikedSongsRepository,
    private val dailyMixManager: DailyMixManager,
    private val mixFeedback: com.theveloper.pixelplay.data.MixFeedback,
    private val adaptiveMix: com.theveloper.pixelplay.data.AdaptiveMix,
    @ApplicationContext context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("your_music_filters", Context.MODE_PRIVATE)
    private val controls = MutableStateFlow(loadControls())

    private data class Library(val all: List<Song>, val likedIds: Set<String>)
    private data class MixKey(val filter: VibeFilter, val seed: Long, val query: String, val likedOnly: Boolean, val poolSize: Int)

    @Volatile private var cachedMix: Pair<MixKey, List<String>>? = null

    private val library = combine(
        musicRepository.getAudioFiles().catch { emit(emptyList()) },
        likedSongsRepository.likedSongs.onStart { emit(emptyList()) }.catch { emit(emptyList()) }
    ) { local, liked ->
        // The shared liked list already merges local favourites, platform likes and
        // favourites-named playlists, so the liked count matches the Playlists tab and Home.
        val likedIds = liked.mapTo(HashSet(liked.size * 2)) { it.id }
        val all = (local + liked)
            .filter { it.contentUriString.isNotBlank() || it.youtubeId != null }
            .distinctBy { it.id }
            .sortedBy { it.title.lowercase(Locale.ROOT) }
        Library(all, likedIds)
    }

    val uiState: StateFlow<YourMusicUiState> = combine(library, controls) { lib, c -> lib to c }
        .mapLatest { (lib, c) ->
            val scoped = if (c.likedOnly) lib.all.filter { it.id in lib.likedIds } else lib.all
            val query = c.query.trim().lowercase(Locale.ROOT)
            val searched = if (query.isEmpty()) scoped else scoped.filter { song ->
                song.title.lowercase(Locale.ROOT).contains(query) ||
                    song.displayArtist.lowercase(Locale.ROOT).contains(query) ||
                    song.album.lowercase(Locale.ROOT).contains(query)
            }
            val filter = (MusicVibeFilters.presets + c.customFilters).firstOrNull { it.id == c.selectedFilterId }
            val songs = if (filter == null) searched else {
                // Keep the same mix while it's on screen (liking a song must not reshuffle it);
                // only a new filter / "New mix" / search / scope change rebuilds it.
                val key = MixKey(filter, c.mixSeed, query, c.likedOnly, searched.size)
                val cached = cachedMix
                if (cached != null && cached.first == key) {
                    val byId = searched.associateBy { it.id }
                    cached.second.mapNotNull { byId[it] }
                } else {
                    // A generated mix is an automatic pick: songs excluded everywhere stay out,
                    // and what the mixes have learned (taste, snoozes, removals) orders it.
                    val pool = adaptiveMix.analysed(searched.filterNot(mixFeedback::isExcludedEverywhere))
                    val learned = try {
                        adaptiveMix.personalAdjustments(pool)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyMap()
                    }
                    MusicVibeFilters.buildMix(
                        pool = pool,
                        adjust = learned,
                        filter = filter,
                        likedIds = lib.likedIds,
                        listening = listening(),
                        seed = c.mixSeed
                    ).also { mix -> cachedMix = key to mix.map { it.id } }
                }
            }
            YourMusicUiState(
                songs = songs,
                totalCount = lib.all.size,
                likedCount = lib.all.count { it.id in lib.likedIds },
                isLoading = false,
                activeMix = filter,
                controls = c
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YourMusicUiState(controls = controls.value))

    private suspend fun listening(): Map<String, MusicVibeFilters.Listening> = try {
        dailyMixManager.getAllEngagementStats().mapValues { (_, stats) ->
            MusicVibeFilters.Listening(stats.playCount, stats.lastPlayedTimestamp)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyMap()
    }

    fun setQuery(query: String) = controls.update { it.copy(query = query) }

    fun toggleLikedOnly() = update { it.copy(likedOnly = !it.likedOnly) }

    /** Selecting a filter replaces the previous one; tapping the active one turns it off. */
    fun selectFilter(id: String) = update {
        if (it.selectedFilterId == id) it.copy(selectedFilterId = null)
        else it.copy(selectedFilterId = id, mixSeed = System.nanoTime())
    }

    /** Rebuilds the current mix with a different shuffle / selection. */
    fun regenerateMix() = controls.update { it.copy(mixSeed = System.nanoTime()) }

    fun clearFilters() = update { it.copy(likedOnly = false, selectedFilterId = null) }

    /** Saves a custom filter permanently and turns it on. Returns false for blank / duplicate text. */
    fun addCustomFilter(text: String): Boolean {
        val clean = text.trim().replace(Regex("\\s+"), " ").take(60)
        if (clean.isBlank()) return false
        val existing = controls.value.customFilters.firstOrNull { it.label.equals(clean, ignoreCase = true) }
        if (existing != null) {
            update { it.copy(selectedFilterId = existing.id, mixSeed = System.nanoTime()) }
            return false
        }
        val filter = MusicVibeFilters.custom("custom_" + UUID.randomUUID().toString().take(8), clean)
        update { it.copy(customFilters = it.customFilters + filter, selectedFilterId = filter.id, mixSeed = System.nanoTime()) }
        return true
    }

    fun deleteCustomFilter(id: String) = update {
        it.copy(
            customFilters = it.customFilters.filterNot { f -> f.id == id },
            selectedFilterId = it.selectedFilterId.takeUnless { selected -> selected == id }
        )
    }

    private fun update(change: (YourMusicControls) -> YourMusicControls) {
        controls.update(change)
        saveControls(controls.value)
    }

    private fun loadControls(): YourMusicControls = try {
        val customs = JSONArray(prefs.getString(KEY_CUSTOM, "[]") ?: "[]").let { array ->
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id"); val text = obj.optString("text")
                if (id.isBlank() || text.isBlank()) null else MusicVibeFilters.custom(id, text)
            }
        }
        val validIds = (MusicVibeFilters.presets + customs).mapTo(hashSetOf()) { it.id }
        YourMusicControls(
            likedOnly = prefs.getBoolean(KEY_LIKED, false),
            selectedFilterId = prefs.getString(KEY_SELECTED_ONE, null)?.takeIf { it in validIds },
            customFilters = customs
        )
    } catch (_: Exception) {
        YourMusicControls()
    }

    private fun saveControls(c: YourMusicControls) {
        val array = JSONArray()
        c.customFilters.forEach { array.put(JSONObject().put("id", it.id).put("text", it.query ?: it.label)) }
        prefs.edit()
            .putString(KEY_CUSTOM, array.toString())
            .putString(KEY_SELECTED_ONE, c.selectedFilterId)
            .remove(KEY_SELECTED_LEGACY)
            .putBoolean(KEY_LIKED, c.likedOnly)
            .apply()
    }

    private companion object {
        const val KEY_CUSTOM = "custom_filters"
        const val KEY_SELECTED_LEGACY = "selected_filters"
        const val KEY_SELECTED_ONE = "selected_filter"
        const val KEY_LIKED = "liked_only"
    }
}
