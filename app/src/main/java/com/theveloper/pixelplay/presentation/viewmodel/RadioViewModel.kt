package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.radio.RadioBrowserRepository
import com.theveloper.pixelplay.data.radio.RadioGeo
import com.theveloper.pixelplay.data.radio.RadioHome
import com.theveloper.pixelplay.data.radio.RadioLocationProvider
import com.theveloper.pixelplay.data.radio.RadioScope
import com.theveloper.pixelplay.data.radio.RadioScopeFilter
import com.theveloper.pixelplay.data.radio.RadioStation
import com.theveloper.pixelplay.data.radio.RadioStore
import com.theveloper.pixelplay.data.radio.RadioViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    /** What the list and dial show: [scopeStations] narrowed by genre and search text. */
    val stations: List<RadioStation> = emptyList(),
    /** What the map shows (only stations with coordinates; WORLD loads a larger set). */
    val mapStations: List<RadioStation> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Explains an empty or partial scope, e.g. LOCAL without location permission. */
    val notice: RadioNotice? = null,
    val genres: List<String> = emptyList(),
    val selectedGenre: String? = null,
    val query: String = "",
    /** Directory-wide name matches for [query]; null when not searching. */
    val searchResults: List<RadioStation>? = null,
    val searching: Boolean = false,
    /** Station uuid → km from home (only when home has coordinates). */
    val distances: Map<String, Double> = emptyMap(),
)

enum class RadioNotice { NEEDS_LOCATION, NEEDS_REGION, NEEDS_COUNTRY }

@HiltViewModel
class RadioViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: RadioBrowserRepository,
    private val locationProvider: RadioLocationProvider,
) : ViewModel() {

    val store: RadioStore = RadioStore.get(context)

    private val _state = MutableStateFlow(
        RadioUiState(
            scope = store.lastScope,
            viewMode = store.lastViewMode,
            hasLocationPermission = locationProvider.hasLocationPermission(),
        )
    )
    val state: StateFlow<RadioUiState> = _state.asStateFlow()

    /** Loaded station lists, per scope, for the current home. Cleared when home changes. */
    private val cache = mutableMapOf<RadioScope, List<RadioStation>>()
    private var worldMapCache: List<RadioStation>? = null
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        refreshHome()
    }

    /** Re-reads where "local" is: the hand-picked home, or the device location. */
    fun refreshHome() {
        viewModelScope.launch {
            _state.update { it.copy(locating = true, hasLocationPermission = locationProvider.hasLocationPermission()) }
            val manual = store.manualHome
            val home = manual ?: runCatching { locationProvider.resolveHome() }.getOrElse { RadioHome(countryCode = null) }
            cache.clear()
            _state.update { it.copy(home = home, locating = false) }
            load()
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
        _state.update { it.copy(scope = scope, selectedGenre = null, error = null) }
        load()
    }

    fun setViewMode(mode: RadioViewMode) {
        store.lastViewMode = mode
        _state.update { it.copy(viewMode = mode) }
        if (mode == RadioViewMode.MAP && _state.value.scope == RadioScope.WORLD) loadWorldMap()
    }

    fun selectGenre(genre: String?) {
        _state.update { it.copy(selectedGenre = genre).withFilters() }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query).withFilters() }
        searchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _state.update { it.copy(searchResults = null, searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _state.update { it.copy(searching = true) }
            val s = _state.value
            val cc = if (s.scope == RadioScope.WORLD) null else s.home?.countryCode
            val results = runCatching { repository.searchByName(q, cc) }.getOrElse {
                if (it is CancellationException) throw it
                emptyList()
            }
            _state.update { it.copy(searchResults = results, searching = false) }
        }
    }

    fun retry() {
        cache.remove(_state.value.scope)
        load()
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

    /** States/territories that have stations in a country, busiest first. */
    suspend fun statesFor(countryCode: String): List<Pair<String, Int>> =
        runCatching { repository.byCountry(countryCode, limit = 2000) }
            .getOrDefault(emptyList())
            .mapNotNull { it.state?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }

    private fun load() {
        val scope = _state.value.scope
        val home = _state.value.home ?: return
        loadJob?.cancel()
        cache[scope]?.let { cached ->
            publish(scope, home, cached)
            return
        }
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notice = null) }
            try {
                val stations = fetch(scope, home)
                cache[scope] = stations
                publish(scope, home, stations)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = "Couldn't reach the radio directory. Check your connection.",
                        scopeStations = emptyList(),
                        stations = emptyList(),
                        mapStations = emptyList(),
                    )
                }
            }
        }
    }

    private suspend fun fetch(scope: RadioScope, home: RadioHome): List<RadioStation> {
        val cc = home.countryCode
        val lat = home.lat
        val lon = home.lon
        return when (scope) {
            RadioScope.LOCAL -> {
                if (lat == null || lon == null) return emptyList()
                coroutineScopeFetch(
                    { repository.nearby(lat, lon, radiusKm = 250.0) },
                    { if (cc != null && !home.state.isNullOrBlank()) repository.byState(cc, home.state) else emptyList() },
                ).let { RadioScopeFilter.local(it, home) }
            }
            RadioScope.REGION -> when {
                cc != null && !home.state.isNullOrBlank() -> repository.byState(cc, home.state)
                lat != null && lon != null -> repository.nearby(lat, lon, RadioScopeFilter.REGION_FALLBACK_RADIUS_KM)
                    .filter { s -> RadioGeo.distanceKm(home, s)?.let { it <= RadioScopeFilter.REGION_FALLBACK_RADIUS_KM } == true }
                else -> emptyList()
            }
            RadioScope.COUNTRY -> if (cc != null) repository.byCountry(cc) else emptyList()
            RadioScope.WORLD -> repository.top(limit = 500)
        }
    }

    private suspend fun coroutineScopeFetch(
        vararg calls: suspend () -> List<RadioStation>,
    ): List<RadioStation> = kotlinx.coroutines.coroutineScope {
        // One failing call shouldn't sink the other (the state lookup is a bonus).
        val results = calls.map { call -> async { runCatching { call() } } }.map { it.await() }
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        results.flatMap { it.getOrDefault(emptyList()) }.distinctBy { it.uuid }
    }

    private fun publish(scope: RadioScope, home: RadioHome, stations: List<RadioStation>) {
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
        _state.update {
            it.copy(
                loading = false,
                error = null,
                notice = notice,
                scopeStations = stations,
                mapStations = if (scope == RadioScope.WORLD) worldMapCache ?: stations.filter { s -> s.hasGeo } else stations.filter { s -> s.hasGeo },
                genres = topGenres(stations),
                distances = distances,
            ).withFilters()
        }
        if (scope == RadioScope.WORLD && _state.value.viewMode == RadioViewMode.MAP) loadWorldMap()
    }

    /** The world map wants far more dots than the world list; fetched only when the map opens. */
    private fun loadWorldMap() {
        if (worldMapCache != null) return
        viewModelScope.launch {
            val geo = runCatching { repository.top(limit = 3000, geoOnly = true) }.getOrNull() ?: return@launch
            worldMapCache = geo
            if (_state.value.scope == RadioScope.WORLD) _state.update { it.copy(mapStations = geo) }
        }
    }

    private fun RadioUiState.withFilters(): RadioUiState {
        val genre = selectedGenre
        val q = query.trim()
        val filtered = scopeStations.filter { s ->
            (genre == null || s.tags.any { it.equals(genre, ignoreCase = true) }) &&
                (q.isEmpty() || s.name.contains(q, ignoreCase = true) || s.tags.any { it.contains(q, ignoreCase = true) })
        }
        return copy(stations = filtered)
    }

    private fun topGenres(stations: List<RadioStation>): List<String> =
        stations.flatMap { s -> s.tags.map { it.lowercase() } }
            .filter { it.length in 2..18 }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
}
