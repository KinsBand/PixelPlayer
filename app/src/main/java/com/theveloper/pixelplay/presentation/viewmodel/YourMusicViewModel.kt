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

/** How Your Music is ordered (a vibe mix keeps its own order). */
enum class YourMusicSort(val label: String) {
    /** Liked filter: newest like first. Otherwise: newest downloaded / added first. */
    LATEST("Latest"),
    A_Z("A to Z"),
    Z_A("Z to A"),
    MOST_PLAYED("Most played"),
    LEAST_PLAYED("Least played"),
    LAST_PLAYED("Last played"),
}

data class YourMusicControls(
    val query: String = "",
    val sort: YourMusicSort = YourMusicSort.LATEST,
    val likedOnly: Boolean = false,
    /** Only one vibe filter can be on at a time; it turns the list into a generated mix. */
    val selectedFilterId: String? = null,
    val customFilters: List<VibeFilter> = emptyList(),
    /** Changes every time a filter is picked or "New mix" is tapped, so each mix is fresh. */
    val mixSeed: Long = System.nanoTime()
)

data class YourMusicUiState(
    val songs: List<Song> = emptyList(),
    /**
     * [songs] grouped into versions (live, remaster, cover… under their original). Empty while a
     * vibe mix is on: a mix is a flat, ordered list.
     */
    val families: List<com.theveloper.pixelplay.data.library.SongUnifier.Family> = emptyList(),
    /** Every copy (local, downloaded, streamed, liked) behind each shown song id. */
    val copies: Map<String, List<Song>> = emptyMap(),
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
    private val removals: com.theveloper.pixelplay.data.library.YourMusicRemovals,
    private val dailyMixManager: DailyMixManager,
    private val mixFeedback: com.theveloper.pixelplay.data.MixFeedback,
    private val adaptiveMix: com.theveloper.pixelplay.data.AdaptiveMix,
    @ApplicationContext context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("your_music_filters", Context.MODE_PRIVATE)
    private val controls = MutableStateFlow(loadControls())

    private data class Library(
        val all: List<Song>,
        val likedIds: Set<String>,
        val copies: Map<String, List<Song>>,
        val unified: Map<String, com.theveloper.pixelplay.data.library.SongUnifier.UnifiedSong>,
        /** Position in the liked list (0 = newest like), best over every copy. */
        val likedRank: Map<String, Int> = emptyMap(),
        /** Newest add / download time over every copy. */
        val addedAt: Map<String, Long> = emptyMap(),
    )
    private data class MixKey(val filter: VibeFilter, val seed: Long, val query: String, val likedOnly: Boolean, val poolSize: Int)

    @Volatile private var cachedMix: Pair<MixKey, List<String>>? = null

    private val library = combine(
        musicRepository.getAudioFiles().catch { emit(emptyList()) },
        likedSongsRepository.likedSongs.onStart { emit(emptyList()) }.catch { emit(emptyList()) },
        removals.keys
    ) { local, liked, removed ->
        // One row per recording: a local file, its download, the streamed copy and the like
        // (Spotify / YouTube Music) are merged, and the row is liked / downloaded if any copy is.
        val likedIds = liked.mapTo(HashSet(liked.size * 2)) { it.id }
        val playable = (local + liked)
            .filter { it.contentUriString.isNotBlank() || it.youtubeId != null }
            // Deleted from Your Music: gone even while a copy lingers (e.g. a Spotify like).
            .filterNot { removals.isRemoved(it, removed) }
            .distinctBy { it.id }
        val unified = com.theveloper.pixelplay.data.library.SongUnifier.unify(playable, likedIds)
            .sortedBy { it.song.title.lowercase(Locale.ROOT) }
        // The liked list arrives newest like first.
        val likeIndex = HashMap<String, Int>(liked.size * 2).apply { liked.forEachIndexed { i, song -> putIfAbsent(song.id, i) } }
        Library(
            likedRank = unified.associate { u -> u.id to (u.copies.mapNotNull { likeIndex[it.id] }.minOrNull() ?: Int.MAX_VALUE) },
            addedAt = unified.associate { u -> u.id to maxOf(u.song.dateAdded, u.copies.maxOfOrNull { it.dateAdded } ?: 0L) },
            all = unified.map { it.song },
            likedIds = unified.filter { it.isLiked }.mapTo(HashSet(unified.size)) { it.id },
            copies = unified.associate { it.id to it.copies },
            unified = unified.associateBy { it.id }
        )
    }.flowOn(Dispatchers.Default)

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
            val songs = if (filter == null) sortSongs(searched, lib, c) else {
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
            val families = if (filter != null) emptyList() else
                com.theveloper.pixelplay.data.library.SongUnifier.families(songs.mapNotNull { lib.unified[it.id] })
            YourMusicUiState(
                songs = songs,
                families = families,
                copies = lib.copies,
                totalCount = lib.all.size,
                likedCount = lib.all.count { it.id in lib.likedIds },
                isLoading = false,
                activeMix = filter,
                controls = c
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YourMusicUiState(controls = controls.value))

    private suspend fun sortSongs(songs: List<Song>, lib: Library, c: YourMusicControls): List<Song> {
        val title = compareBy<Song> { it.title.lowercase(Locale.ROOT) }
        return when (c.sort) {
            YourMusicSort.LATEST -> if (c.likedOnly) {
                songs.sortedWith(compareBy<Song> { lib.likedRank[it.id] ?: Int.MAX_VALUE }.then(title))
            } else {
                songs.sortedWith(compareByDescending<Song> { lib.addedAt[it.id] ?: 0L }.then(title))
            }
            YourMusicSort.A_Z -> songs.sortedWith(title)
            YourMusicSort.Z_A -> songs.sortedWith(title.reversed())
            YourMusicSort.MOST_PLAYED, YourMusicSort.LEAST_PLAYED, YourMusicSort.LAST_PLAYED -> {
                val stats = try { dailyMixManager.getAllEngagementStats() }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }
                // A song's plays = its plays across every copy (local, downloaded, streamed).
                fun copiesOf(song: Song) = lib.copies[song.id].orEmpty().ifEmpty { listOf(song) }
                fun plays(song: Song) = copiesOf(song).sumOf { stats[it.id]?.playCount ?: 0 }
                fun lastPlayed(song: Song) = copiesOf(song).maxOfOrNull { stats[it.id]?.lastPlayedTimestamp ?: 0L } ?: 0L
                when (c.sort) {
                    YourMusicSort.MOST_PLAYED -> songs.sortedWith(compareByDescending<Song> { plays(it) }.then(title))
                    YourMusicSort.LEAST_PLAYED -> songs.sortedWith(compareBy<Song> { plays(it) }.then(title))
                    else -> songs.sortedWith(compareByDescending<Song> { lastPlayed(it) }.then(title))
                }
            }
        }
    }

    fun setSort(sort: YourMusicSort) = update { it.copy(sort = sort) }

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
            sort = prefs.getString(KEY_SORT, null)?.let { name -> YourMusicSort.entries.firstOrNull { it.name == name } } ?: YourMusicSort.LATEST,
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
            .putString(KEY_SORT, c.sort.name)
            .apply()
    }

    private companion object {
        const val KEY_CUSTOM = "custom_filters"
        const val KEY_SELECTED_LEGACY = "selected_filters"
        const val KEY_SELECTED_ONE = "selected_filter"
        const val KEY_LIKED = "liked_only"
        const val KEY_SORT = "sort"
    }
}
