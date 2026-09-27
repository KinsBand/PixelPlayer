package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.library.CollectionKeys
import com.theveloper.pixelplay.data.library.GenreFamilies
import com.theveloper.pixelplay.data.library.LibraryInsightsRepository
import com.theveloper.pixelplay.data.library.StreamCollectionRepository
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.library.MusicVibeFilters
import com.theveloper.pixelplay.presentation.library.VibeCategory
import com.theveloper.pixelplay.presentation.library.VibeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** One card of the Library › Genres tab: a genre family ("Rock") with its subgenres. */
data class GenreCardUi(
    val familyId: String,
    val label: String,
    val songCount: Int,
    val subgenres: List<String>,
    val covers: List<String>,
    val topArtists: List<Pair<String, String?>>,
    val listeningMs: Long
)

data class MoodChipUi(val filter: VibeFilter, val songCount: Int)

enum class GenreSort(val label: String) { LISTENING("By listening"), AZ("A–Z"), SONGS("Most songs") }

data class LibraryGenresUi(
    val cards: List<GenreCardUi> = emptyList(),
    val unknownCount: Int = 0,
    val moods: List<MoodChipUi> = emptyList(),
    val sort: GenreSort = GenreSort.LISTENING,
    val isLoading: Boolean = true
)

/**
 * State for the personal parts of the Library: the album and artist shelves, the "N streaming"
 * counts on cards, and the Genres tab (genre families, your genre DNA and mood mixes).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryCollectionViewModel @Inject constructor(
    private val insightsRepository: LibraryInsightsRepository,
    private val streamCollection: StreamCollectionRepository,
    private val musicDao: MusicDao,
    private val libraryStateHolder: LibraryStateHolder
) : ViewModel() {

    val insights: StateFlow<LibraryInsightsRepository.Insights> = insightsRepository.insights

    /** Library album id -> streamed liked songs merged into it. */
    val mergedAlbumCounts: StateFlow<Map<Long, Int>> = streamCollection.mergedAlbumCounts

    /** Library artist id -> streamed liked songs merged into it. */
    val mergedArtistCounts: StateFlow<Map<Long, Int>> = streamCollection.mergedArtistCounts

    private val sort = MutableStateFlow(GenreSort.LISTENING)
    private val moods = MutableStateFlow<List<MoodChipUi>>(emptyList())

    private data class GenreData(val cards: List<GenreCardUi>, val unknown: Int)

    private val genreData: StateFlow<GenreData?> = combine(
        // Rows re-emit on every write to `songs` (play counts, edits…); rebuild only on real changes.
        musicDao.observeGenreSongRows().debounce(600).distinctUntilChanged(),
        streamCollection.streamSongs,
        insightsRepository.insights.map { it.genreListening }.distinctUntilChanged()
    ) { rows, streamed, listening ->
        buildGenres(rows, streamed, listening)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val genresUi: StateFlow<LibraryGenresUi> = combine(genreData, sort, moods) { data, sortMode, moodChips ->
        if (data == null) return@combine LibraryGenresUi(sort = sortMode, moods = moodChips, isLoading = true)
        val sorted = when (sortMode) {
            GenreSort.LISTENING -> data.cards.sortedWith(compareByDescending<GenreCardUi> { it.listeningMs }.thenByDescending { it.songCount })
            GenreSort.AZ -> data.cards.sortedBy { it.label.lowercase() }
            GenreSort.SONGS -> data.cards.sortedByDescending { it.songCount }
        }
        LibraryGenresUi(sorted, data.unknown, moodChips, sortMode, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryGenresUi())

    // Nothing heavy runs in init: this ViewModel is created as soon as the Library opens,
    // whatever the tab. Shelves and moods are built when the tab that shows them appears.

    /** Called when a Library tab with shelves becomes visible. */
    fun onShelvesVisible() = insightsRepository.ensureFresh()

    /** Keeps the shelves current while a tab showing them is on screen (call from a LaunchedEffect). */
    suspend fun followInsights() = insightsRepository.follow()

    private var moodsRequested = false

    /** Called when the Genres tab appears: builds the mood chips once. */
    fun onGenresVisible() {
        if (moodsRequested) return
        moodsRequested = true
        computeMoods()
    }

    fun setGenreSort(value: GenreSort) {
        sort.value = value
    }

    /** Builds a mood mix from your library + streamed likes and hands it to the player. */
    fun playMood(filter: VibeFilter, favoriteIds: Set<String>, play: (List<Song>, VibeFilter) -> Unit) {
        viewModelScope.launch {
            val mix = withContext(Dispatchers.Default) {
                MusicVibeFilters.buildMix(
                    pool = moodPool(),
                    filter = filter,
                    likedIds = favoriteIds,
                    listening = emptyMap(),
                    seed = System.currentTimeMillis()
                )
            }
            if (mix.isNotEmpty()) play(mix, filter)
        }
    }

    private fun moodPool(): List<Song> =
        (libraryStateHolder.allSongs.value + streamCollection.streamSongs.value).distinctBy { it.id }

    private fun computeMoods() {
        viewModelScope.launch {
            val chips = withContext(Dispatchers.Default) {
                val pool = moodPool()
                if (pool.isEmpty()) return@withContext emptyList()
                MusicVibeFilters.presets.filter { it.category == VibeCategory.MOOD }.mapNotNull { filter ->
                    val count = pool.count { MusicVibeFilters.score(it, filter) >= MusicVibeFilters.MATCH_THRESHOLD }
                    if (count >= MIN_MOOD_SONGS) MoodChipUi(filter, count) else null
                }.sortedByDescending { it.songCount }
            }
            moods.value = chips
            // Built before the library finished loading (or too few songs): try again next visit.
            if (chips.isEmpty()) moodsRequested = false
        }
    }

    private suspend fun buildGenres(
        rows: List<com.theveloper.pixelplay.data.database.GenreSongRow>,
        streamed: List<Song>,
        listening: Map<String, Long>
    ): GenreData {
        class Acc(val family: String) {
            var songs = 0
            /** Tag key -> (most seen spelling, count). */
            val subgenres = HashMap<String, Pair<String, Int>>()
            val covers = LinkedHashSet<String>()
            val artists = HashMap<Long, Pair<String, Int>>()
        }
        val families = HashMap<String, Acc>()
        var unknown = 0
        fun add(genre: String?, artistId: Long, artistName: String, cover: String?) {
            // Same rule as the genre page: the family of the first usable tag.
            val family = GenreFamilies.familyOf(genre)
            if (family == null) { unknown++; return }
            val acc = families.getOrPut(family) { Acc(family) }
            acc.songs++
            GenreFamilies.primaryTag(genre)?.let { tag ->
                val key = GenreFamilies.tagKey(tag)
                val previous = acc.subgenres[key]
                acc.subgenres[key] = (previous?.first ?: tag) to ((previous?.second ?: 0) + 1)
            }
            if (cover != null && acc.covers.size < 4) acc.covers += cover
            val previous = acc.artists[artistId]
            acc.artists[artistId] = artistName to ((previous?.second ?: 0) + 1)
        }
        rows.forEach { add(it.genre, it.artistId, it.artistName, it.albumArtUriString?.takeIf { art -> art.isNotBlank() }) }
        val artistIds = streamCollection.artistIdByKey.value
        streamed.forEach { song ->
            val id = artistIds[CollectionKeys.normalizeArtist(song.artist)] ?: CollectionKeys.streamArtistId(song.artist)
            add(song.genre, id, song.artist, song.albumArtUriString?.takeIf { it.isNotBlank() })
        }

        // Artist pictures for the three top artists of each card (library artists only).
        val topIds = families.values.flatMap { acc -> acc.artists.entries.sortedByDescending { it.value.second }.take(3).map { it.key } }
            .filter { !CollectionKeys.isStreamArtistId(it) }
            .distinct()
        val images = HashMap<Long, String?>()
        topIds.chunked(500).forEach { chunk ->
            musicDao.getArtistsByIds(chunk).forEach { images[it.id] = it.customImageUri?.takeIf { u -> u.isNotBlank() } ?: it.imageUrl }
        }

        val cards = families.values.map { acc ->
            GenreCardUi(
                familyId = acc.family,
                label = GenreFamilies.familyLabel(acc.family),
                songCount = acc.songs,
                subgenres = acc.subgenres.values.sortedByDescending { it.second }.map { it.first }
                    .filterNot { GenreFamilies.tagKey(it) == GenreFamilies.tagKey(GenreFamilies.familyLabel(acc.family)) }
                    .take(3),
                covers = acc.covers.toList(),
                topArtists = acc.artists.entries.sortedByDescending { it.value.second }.take(3)
                    .map { (id, value) -> value.first to images[id] },
                listeningMs = listening[acc.family] ?: 0L
            )
        }
        return GenreData(cards, unknown)
    }

    companion object {
        private const val MIN_MOOD_SONGS = 5
    }
}
