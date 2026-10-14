package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.radio.RadioBrowserRepository
import com.theveloper.pixelplay.data.radio.RadioGeo
import com.theveloper.pixelplay.data.radio.RadioHome
import com.theveloper.pixelplay.data.radio.RadioLabels
import com.theveloper.pixelplay.data.radio.RadioLocationProvider
import com.theveloper.pixelplay.data.radio.RadioScope
import com.theveloper.pixelplay.data.radio.RadioScopeFilter
import com.theveloper.pixelplay.data.radio.RadioSearchIndex
import com.theveloper.pixelplay.data.radio.RadioStation
import com.theveloper.pixelplay.data.radio.RadioStationCache
import com.theveloper.pixelplay.data.radio.RadioStore
import com.theveloper.pixelplay.data.radio.RadioViewMode
import com.theveloper.pixelplay.presentation.screens.radio.RadioMapController
import com.theveloper.pixelplay.presentation.screens.radio.RadioMapTiles
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.Locale
import javax.inject.Inject

@Immutable
data class RadioUiState(
    val home: RadioHome? = null,
    val locating: Boolean = true,
    val hasLocationPermission: Boolean = false,
    val scope: RadioScope = RadioScope.LOCAL,
    val viewMode: RadioViewMode = RadioViewMode.LIST,
    /** Every station in the scope, before genre/search filters. */
    val scopeStations: List<RadioStation> = emptyList(),
    /** What the list shows: [scopeStations] narrowed by genre and search text. */
    val stations: List<RadioStation> = emptyList(),
    /** What the map shows (only stations with coordinates; WORLD loads a larger set). */
    val mapStations: List<RadioStation> = emptyList(),
    /** Nothing to show yet and the directory is being asked. */
    val loading: Boolean = false,
    /** Showing a saved list while a fresh copy loads in the background. */
    val refreshing: Boolean = false,
    val error: String? = null,
    /** Explains an empty or partial scope, e.g. LOCAL without location permission. */
    val notice: RadioNotice? = null,
    val genres: List<String> = emptyList(),
    /** The selected section inside [selectedCategory] (a short tag like "Rock"), if any. */
    val selectedGenre: String? = null,
    /** The simple filters (Music, News & talk, Sport…) found in this scope. */
    val categories: List<com.theveloper.pixelplay.data.radio.RadioCategoryGroup> = emptyList(),
    val selectedCategory: com.theveloper.pixelplay.data.radio.RadioCategory? = null,
    val query: String = "",
    /**
     * Matches for [query] from everything already loaded (instant) merged with the directory's
     * own name search (arrives a moment later). Null when not searching.
     */
    val searchResults: List<RadioStation>? = null,
    /** The directory-wide search is still running. */
    val searching: Boolean = false,
    /** Station uuid → km from home (only when home has coordinates). */
    val distances: Map<String, Double> = emptyMap(),
    /** A station the map was asked to show (e.g. from the player's map button). Always pinned. */
    val focusStation: RadioStation? = null,
    /** Bumped on every "show this station on the map" request, so the camera moves once per ask. */
    val focusRequest: Int = 0,
)

enum class RadioNotice { NEEDS_LOCATION, NEEDS_REGION, NEEDS_COUNTRY }

/**
 * Radio browsing, built to never make you wait twice:
 *  - The last home and every loaded scope are cached (memory + disk), so the screen opens on
 *    real stations immediately and refreshes quietly in the background.
 *  - Once home is known, the other scopes are prefetched, so switching scope is instant.
 *  - Search answers from everything already loaded on each keystroke, then merges the
 *    directory's own results when they arrive.
 */
@HiltViewModel
class RadioViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: RadioBrowserRepository,
    private val locationProvider: RadioLocationProvider,
    private val cache: RadioStationCache,
    mapTiles: RadioMapTiles,
) : ViewModel() {

    val store: RadioStore = RadioStore.get(context)

    /** The map keeps its camera, tiles and pins while you flip between list and map. */
    private val mapHolder = lazy { RadioMapController(mapTiles) }
    val map: RadioMapController get() = mapHolder.value

    private val startHome: RadioHome? = store.manualHome ?: store.lastResolvedHome

    private val _state = MutableStateFlow(
        RadioUiState(
            home = startHome,
            locating = startHome == null,
            scope = store.lastScope,
            viewMode = store.lastViewMode,
            hasLocationPermission = locationProvider.hasLocationPermission(),
        )
    )
    val state: StateFlow<RadioUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var prefetchJob: Job? = null
    /** One network request per list, shared by whoever needs it (the screen and the prefetch). */
    private val inFlight = HashMap<String, Deferred<List<RadioStation>>>()
    private val statesByCountry = HashMap<String, List<Pair<String, Int>>>()

    private var searchIndex: RadioSearchIndex? = null
    private var searchIndexSignature = -1

    init {
        // DNS + TLS to the mirrors while location is still being worked out.
        repository.warmUp()
        if (startHome != null) load()
        refreshHome(quiet = startHome != null)
    }

    /**
     * Re-reads where "local" is: the hand-picked home, or the device location. With [quiet] the
     * current (saved) home stays on screen and is only replaced if the real one differs.
     */
    fun refreshHome(quiet: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    locating = !quiet || it.home == null,
                    hasLocationPermission = locationProvider.hasLocationPermission(),
                )
            }
            val manual = store.manualHome
            val home = manual ?: runCatching { locationProvider.resolveHome() }.getOrElse {
                if (it is CancellationException) throw it
                _state.value.home ?: RadioHome(countryCode = null)
            }
            if (manual == null) store.lastResolvedHome = home
            val previous = _state.value.home
            _state.update { it.copy(home = home, locating = false) }
            if (previous == null || !sameArea(previous, home) || _state.value.scopeStations.isEmpty()) {
                load()
            } else if (previous != home) {
                // Same area, slightly different fix: keep the lists, update the distances.
                val s = _state.value
                publish(s.scope, home, s.scopeStations, refreshing = s.refreshing)
            }
            prefetchOtherScopes()
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasLocationPermission = granted) }
        if (granted) {
            // Location beats an old manual pick once the user grants it.
            store.manualHome = null
            refreshHome()
        }
    }

    fun setManualHome(countryCode: String, countryName: String, state: String?) {
        store.manualHome = RadioHome(countryCode = countryCode, countryName = countryName, state = state, isManual = true)
        refreshHome()
    }

    fun clearManualHome() {
        store.manualHome = null
        refreshHome()
    }

    fun setScope(scope: RadioScope) {
        if (scope == _state.value.scope) return
        store.lastScope = scope
        _state.update { it.copy(scope = scope, selectedGenre = null, selectedCategory = null, error = null) }
        load()
        // Results "elsewhere" depend on the scope (country vs everywhere).
        if (_state.value.query.isNotBlank()) setQuery(_state.value.query)
    }

    fun setViewMode(mode: RadioViewMode) {
        store.lastViewMode = mode
        _state.update { it.copy(viewMode = mode) }
        if (mode == RadioViewMode.MAP && _state.value.scope == RadioScope.WORLD) loadWorldMap()
    }

    /**
     * Opens the map on [station] (the player's map button). The station is pinned even if it
     * isn't in the current scope, and the camera flies to it.
     */
    fun showOnMap(station: RadioStation) {
        setViewMode(RadioViewMode.MAP)
        _state.update {
            it.copy(
                focusStation = station,
                focusRequest = it.focusRequest + 1,
                mapStations = withFocus(it.mapStations, station),
            )
        }
    }

    /** [genre] is a short label ("Comm", "Talk"); it matches every tag that shortens to it. */
    fun selectGenre(genre: String?) {
        _state.update { it.copy(selectedGenre = genre).withFilters() }
    }

    /** Picks a simple filter (null = all). A [section] narrows it (e.g. Music → Rock). */
    fun selectCategory(category: com.theveloper.pixelplay.data.radio.RadioCategory?, section: String? = null) {
        _state.update { it.copy(selectedCategory = category, selectedGenre = if (category == null) null else section).withFilters() }
    }

    /**
     * Instant part: filters the scope and searches everything already loaded, in the same frame.
     * Network part: the directory's name search, debounced, merged in when it lands.
     */
    fun setQuery(query: String) {
        searchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _state.update { it.copy(query = query, searchResults = null, searching = false).withFilters() }
            return
        }
        val s0 = _state.value
        val preferCountry = if (s0.scope == RadioScope.WORLD) null else s0.home?.countryCode
        val local = index().search(q, limit = 120, preferCountry = preferCountry)
        _state.update { it.copy(query = query, searchResults = local, searching = true).withFilters() }
        searchJob = viewModelScope.launch {
            // Short debounce: the whole country is already searched locally on every keystroke,
            // the directory only adds stations from elsewhere.
            delay(150)
            val s = _state.value
            val cc = if (s.scope == RadioScope.WORLD) null else s.home?.countryCode
            val remote = coroutineScope {
                val byName = async { runCatching { repository.searchByName(q, cc) }.getOrElse { if (it is CancellationException) throw it; emptyList() } }
                val byTag = async {
                    if (q.length < 3) emptyList<RadioStation>()
                    else runCatching { repository.searchByTag(q, cc) }.getOrElse { if (it is CancellationException) throw it; emptyList() }
                }
                byName.await() + byTag.await()
            }
            val seen = local.mapTo(HashSet()) { it.uuid }
            val merged = local + remote.filter { seen.add(it.uuid) }
            _state.update { it.copy(searchResults = merged, searching = false) }
        }
    }

    fun retry() {
        val s = _state.value
        val home = s.home ?: return refreshHome()
        RadioStationCache.keyFor(s.scope, home)?.let { inFlight.remove(it) }
        load(force = true)
    }

    /** Call after a station starts playing: remembers it and reports the play to the directory. */
    fun onStationPlayed(station: RadioStation) {
        store.addRecent(station)
        viewModelScope.launch { repository.reportClick(station.uuid) }
    }

    /** Country list for the manual location picker, alphabetical. */
    fun countries(): List<Pair<String, String>> =
        Locale.getISOCountries()
            .map { code -> code to Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH) }
            .filter { it.second.isNotBlank() }
            .sortedBy { it.second }

    /**
     * States/territories that have stations in a country, busiest first. Spellings of the same
     * state are counted together under its usual name. Remembered per country.
     */
    suspend fun statesFor(countryCode: String): List<Pair<String, Int>> {
        val cc = countryCode.uppercase()
        statesByCountry[cc]?.let { return it }
        val stations = runCatching { countryStations(cc) }.getOrDefault(emptyList())
        val result = withContext(Dispatchers.Default) { stations
            .filter { !it.state.isNullOrBlank() }
            .groupBy { RadioLabels.stateKey(it.state, cc, it.subdivisionCode) ?: it.state!!.trim() }
            .map { (key, group) ->
                // The state's proper name when we know it ("New South Wales", not "NSW" or
                // "Sydney"), else the most common spelling.
                val name = RadioLabels.stateName(key) ?: group.mapNotNull { it.state?.trim() }.groupingBy { it }.eachCount()
                    .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.key.length > 3 }.thenByDescending { it.value })
                    .first().key
                name to group.size
            }
            .sortedByDescending { it.second } }
        if (result.isNotEmpty()) statesByCountry[cc] = result
        return result
    }

    private fun load(force: Boolean = false) {
        val scope = _state.value.scope
        val home = _state.value.home ?: return
        loadJob?.cancel()
        val key = RadioStationCache.keyFor(scope, home)
        if (key == null) {
            // Nothing to fetch for this home (e.g. LOCAL without coordinates): explain why.
            publish(scope, home, emptyList(), refreshing = false)
            return
        }
        val memory = cache.peek(key)
        if (memory != null) publish(scope, home, memory.stations, refreshing = force || memory.isStale)
        loadJob = viewModelScope.launch {
            val saved = memory ?: cache.get(key)
            if (saved != null && memory == null) publish(scope, home, saved.stations, refreshing = force || saved.isStale)
            if (saved != null && !saved.isStale && !force) return@launch
            if (saved == null) _state.update { it.copy(loading = true, error = null, notice = null) }
            try {
                val fresh = fetchShared(key) { fetch(scope, home) }.await()
                if (isCurrent(scope, home)) publish(scope, home, fresh, refreshing = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrent(scope, home)) return@launch
                if (saved != null) {
                    // Keep the saved list; it's better than an error.
                    _state.update { it.copy(refreshing = false) }
                } else {
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = "Couldn't reach the radio directory. Check your connection.",
                            scopeStations = emptyList(),
                            stations = emptyList(),
                            mapStations = emptyList(),
                        )
                    }
                }
            }
        }
        if (scope == RadioScope.WORLD && _state.value.viewMode == RadioViewMode.MAP) loadWorldMap()
    }

    private fun isCurrent(scope: RadioScope, home: RadioHome): Boolean {
        val s = _state.value
        return s.scope == scope && s.home?.let { sameArea(it, home) } == true
    }

    /** Shares one request per list between the visible scope and the background prefetch. */
    private fun fetchShared(key: String, block: suspend () -> List<RadioStation>): Deferred<List<RadioStation>> {
        inFlight[key]?.takeIf { it.isActive }?.let { return it }
        val deferred = viewModelScope.async {
            val stations = block()
            // An empty answer for a scope that should have stations is not worth caching.
            if (stations.isNotEmpty()) cache.put(key, stations)
            stations
        }
        inFlight[key] = deferred
        deferred.invokeOnCompletion { if (inFlight[key] === deferred) inFlight.remove(key) }
        return deferred
    }

    /** Loads the scopes you're not looking at, so tapping one shows it at once. */
    private fun prefetchOtherScopes() {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            delay(400) // let the visible scope go first
            val home = _state.value.home ?: return@launch
            for (scope in RadioScope.entries) {
                if (scope == _state.value.scope) continue
                val key = RadioStationCache.keyFor(scope, home) ?: continue
                val saved = cache.peek(key) ?: cache.get(key)
                if (saved != null && !saved.isStale) continue
                launch { runCatching { fetchShared(key) { fetch(scope, home) }.await() } }
            }
        }
    }

    /**
     * Local, Region and Country are all cut from one full country list (plus a geo search
     * around you), so:
     *  - every station is found whatever its state is spelled like ("New South Wales", "NSW",
     *    "Sydney", "AU-NSW") or whether it lists coordinates,
     *  - one request serves all three scopes, and switching between them is instant.
     */
    private suspend fun fetch(scope: RadioScope, home: RadioHome): List<RadioStation> {
        val cc = home.countryCode
        val lat = home.lat
        val lon = home.lon
        return when (scope) {
            RadioScope.LOCAL -> {
                if (lat == null || lon == null) return emptyList()
                coroutineScopeFetch(
                    { nearbyStations(lat, lon) },
                    { if (cc != null) countryStations(cc) else emptyList() },
                ).let { all -> withContext(Dispatchers.Default) { RadioScopeFilter.local(all, home) } }
            }
            RadioScope.REGION -> {
                val nearby = if (lat != null && lon != null) {
                    runCatching { nearbyStations(lat, lon, RadioScopeFilter.REGION_FALLBACK_RADIUS_KM) }.getOrElse {
                        if (it is CancellationException) throw it
                        emptyList()
                    }
                } else emptyList()
                when {
                    cc != null && !home.state.isNullOrBlank() -> countryStations(cc).let { country ->
                        withContext(Dispatchers.Default) { RadioScopeFilter.region(country, home, nearby) }
                    }
                    lat != null && lon != null -> nearby
                        .filter { s -> RadioGeo.distanceKm(home, s)?.let { it <= RadioScopeFilter.REGION_FALLBACK_RADIUS_KM } == true }
                    else -> emptyList()
                }
            }
            // Not countryStations(): this list *is* the shared "country_CC" entry (same cache
            // key), and awaiting itself through fetchShared would never finish.
            RadioScope.COUNTRY -> if (cc != null) repository.byCountry(cc) else emptyList()
            RadioScope.WORLD -> repository.top(limit = 500)
        }
    }

    /** The whole country, shared by every scope and the state picker; one request per 6 h. */
    private suspend fun countryStations(cc: String): List<RadioStation> {
        val key = "country_${cc.uppercase()}"
        val saved = cache.peek(key) ?: cache.get(key)
        if (saved != null && !saved.isStale) return saved.stations
        return try {
            fetchShared(key) { repository.byCountry(cc) }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            saved?.stations ?: throw e
        }
    }

    /** Stations with coordinates around a point (on a ~11 km grid so small moves reuse it). */
    private suspend fun nearbyStations(lat: Double, lon: Double, radiusKm: Double = 250.0): List<RadioStation> {
        val key = "nearby_%.1f_%.1f_%d".format(Locale.ROOT, lat, lon, radiusKm.toInt())
        val saved = cache.peek(key) ?: cache.get(key)
        if (saved != null && !saved.isStale) return saved.stations
        return try {
            fetchShared(key) { repository.nearby(lat, lon, radiusKm) }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            saved?.stations ?: throw e
        }
    }

    private suspend fun coroutineScopeFetch(
        vararg calls: suspend () -> List<RadioStation>,
    ): List<RadioStation> = coroutineScope {
        // One failing call shouldn't sink the other (the state lookup is a bonus).
        val results = calls.map { call ->
            async {
                try {
                    Result.success(call())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure<List<RadioStation>>(e)
                }
            }
        }.map { it.await() }
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        results.flatMap { it.getOrDefault(emptyList()) }.distinctBy { it.uuid }
    }

    private fun publish(scope: RadioScope, home: RadioHome, stations: List<RadioStation>, refreshing: Boolean) {
        val notice = when {
            stations.isNotEmpty() -> null
            scope == RadioScope.LOCAL && !home.hasCoordinates -> RadioNotice.NEEDS_LOCATION
            scope == RadioScope.REGION && home.state.isNullOrBlank() && !home.hasCoordinates -> RadioNotice.NEEDS_REGION
            home.countryCode == null && scope != RadioScope.WORLD -> RadioNotice.NEEDS_COUNTRY
            else -> null
        }
        val distances = if (home.hasCoordinates) {
            stations.mapNotNull { s -> RadioGeo.distanceKm(home, s)?.let { s.uuid to it } }.toMap()
        } else emptyMap()
        val worldMap = cache.peek(RadioStationCache.WORLD_MAP_KEY)?.stations
        _state.update {
            it.copy(
                loading = false,
                refreshing = refreshing && stations.isNotEmpty(),
                error = null,
                notice = notice,
                scopeStations = stations,
                mapStations = withFocus(
                    if (scope == RadioScope.WORLD) worldMap ?: stations.filter { s -> s.hasGeo } else stations.filter { s -> s.hasGeo },
                    it.focusStation,
                ),
                genres = topGenres(stations),
                categories = com.theveloper.pixelplay.data.radio.RadioCategories.groups(stations),
                distances = distances,
            ).withFilters()
        }
    }

    /** The world map wants far more pins than the world list; fetched only when the map opens. */
    private fun loadWorldMap() {
        viewModelScope.launch {
            val key = RadioStationCache.WORLD_MAP_KEY
            val saved = cache.peek(key) ?: cache.get(key)
            if (saved != null) showWorldMap(saved.stations)
            if (saved != null && !saved.isStale) return@launch
            val geo = runCatching { fetchShared(key) { repository.top(limit = 3000, geoOnly = true) }.await() }.getOrNull()
                ?: return@launch
            showWorldMap(geo)
        }
    }

    private fun showWorldMap(stations: List<RadioStation>) {
        if (_state.value.scope == RadioScope.WORLD && stations.isNotEmpty()) {
            _state.update { it.copy(mapStations = withFocus(stations, it.focusStation)) }
        }
    }

    /** Makes sure the asked-for station has a pin, wherever it is. */
    private fun withFocus(stations: List<RadioStation>, focus: RadioStation?): List<RadioStation> =
        if (focus == null || !focus.hasGeo || stations.any { it.uuid == focus.uuid }) stations else stations + focus

    /** Everything loaded so far plus your favourites and recents; rebuilt only when it grows. */
    private fun index(): RadioSearchIndex {
        val favorites = store.favorites.value
        val recents = store.recents.value
        val scoped = _state.value.scopeStations
        val signature = ((cache.version * 31 + favorites.size) * 31 + recents.size) * 31 + scoped.size
        val current = searchIndex
        if (current != null && searchIndexSignature == signature) return current
        return RadioSearchIndex(favorites + recents + scoped + cache.allStations()).also {
            searchIndex = it
            searchIndexSignature = signature
        }
    }

    private fun RadioUiState.withFilters(): RadioUiState {
        val genre = selectedGenre
        val q = query.trim()
        val category = selectedCategory
        val filtered = scopeStations.filter { s ->
            (if (category != null) com.theveloper.pixelplay.data.radio.RadioCategories.matches(s, category, genre)
            else genre == null || s.tags.any { RadioLabels.shortTag(it) == genre }) &&
                (q.isEmpty() || s.name.contains(q, ignoreCase = true) || s.tags.any { it.contains(q, ignoreCase = true) })
        }
        return copy(stations = filtered)
    }

    /** The scope's most common genres as short labels ("Comm", "Talk", "Rock"), each counted once per station. */
    private fun topGenres(stations: List<RadioStation>): List<String> =
        stations.flatMap { s -> s.tags.map(RadioLabels::shortTag).distinct() }
            .filter { it != "Radio" && it != "Music" }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(12)
            .map { it.key }

    override fun onCleared() {
        if (mapHolder.isInitialized()) map.shutdown()
        super.onCleared()
    }

    private companion object {
        /** Two homes are "the same area" when they share a cache key for every scope. */
        fun sameArea(a: RadioHome, b: RadioHome): Boolean =
            RadioScope.entries.all { RadioStationCache.keyFor(it, a) == RadioStationCache.keyFor(it, b) }
    }
}
