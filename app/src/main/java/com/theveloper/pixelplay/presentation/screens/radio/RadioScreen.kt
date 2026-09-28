package com.theveloper.pixelplay.presentation.screens.radio

import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.radio.RadioBand
import com.theveloper.pixelplay.data.radio.RadioFrequency
import com.theveloper.pixelplay.data.radio.RadioGeo
import com.theveloper.pixelplay.data.radio.RadioPlayback
import com.theveloper.pixelplay.data.radio.RadioScope
import com.theveloper.pixelplay.data.radio.RadioScopeFilter
import com.theveloper.pixelplay.data.radio.RadioStation
import com.theveloper.pixelplay.data.radio.RadioStore
import com.theveloper.pixelplay.data.radio.RadioViewMode
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.RadioNotice
import com.theveloper.pixelplay.presentation.viewmodel.RadioUiState
import com.theveloper.pixelplay.presentation.viewmodel.RadioViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.launch

/**
 * Internet radio: local → region → country → world, browsed as a list, a tuner dial or a map.
 * Stations come from the Radio Browser directory and play through the normal player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    viewModel: RadioViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val presets by viewModel.store.presets.collectAsStateWithLifecycle()
    val favorites by viewModel.store.favorites.collectAsStateWithLifecycle()
    val recents by viewModel.store.recents.collectAsStateWithLifecycle()
    val playerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val playingUuid = RadioPlayback.uuidOf(playerState.currentSong)
    val isPlaying = playerState.isPlaying
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var showLocationSheet by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<RadioStation?>(null) }
    var searchOpen by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onLocationPermissionResult(granted)
        if (!granted) Toast.makeText(context, "Pick your region instead to get local stations", Toast.LENGTH_SHORT).show()
    }
    val requestLocation = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }

    val play: (RadioStation) -> Unit = { station ->
        if (station.uuid == playingUuid) {
            playerViewModel.playPause()
        } else {
            val song = RadioPlayback.toSong(station)
            playerViewModel.playSongs(listOf(song), song, RadioPlayback.QUEUE_NAME)
            viewModel.onStationPlayed(station)
        }
    }
    // What a long-pressed preset saves: the radio station playing right now, if any.
    val currentStation: RadioStation? = remember(playingUuid, state.scopeStations, recents) {
        playingUuid?.let { uuid ->
            state.scopeStations.firstOrNull { it.uuid == uuid }
                ?: recents.firstOrNull { it.uuid == uuid }
                ?: favorites.firstOrNull { it.uuid == uuid }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Radio", fontFamily = GoogleSansRounded, maxLines = 1)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showLocationSheet = true }
                        ) {
                            Icon(Icons.Rounded.LocationOn, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(2.dp))
                            Text(
                                if (state.locating) "Finding you…" else state.home?.label ?: "Set location",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = GoogleSansRounded),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        onClick = onBackClick
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) viewModel.setQuery("")
                        if (searchOpen && state.viewMode != RadioViewMode.LIST) viewModel.setViewMode(RadioViewMode.LIST)
                    }) {
                        Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, contentDescription = "Search stations")
                    }
                    IconButton(onClick = { showLocationSheet = true }) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = "Change location")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            ScopeLadder(
                selected = state.scope,
                onSelect = viewModel::setScope,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                RadioViewMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = state.viewMode == mode,
                        onClick = { viewModel.setViewMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = RadioViewMode.entries.size),
                        label = { Text(mode.label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            val contentBottom = MiniPlayerHeight + bottomInset + 16.dp
            AnimatedContent(
                targetState = state.viewMode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "radioViewMode",
                modifier = Modifier.weight(1f)
            ) { mode ->
                when (mode) {
                    RadioViewMode.LIST -> RadioList(
                        state = state,
                        presets = presets,
                        favorites = favorites,
                        recents = recents,
                        playingUuid = playingUuid,
                        isPlaying = isPlaying,
                        searchOpen = searchOpen,
                        bottomPadding = contentBottom,
                        onPlay = play,
                        onPresetLongPress = { slot ->
                            if (currentStation != null) {
                                viewModel.store.setPreset(slot, currentStation)
                                Toast.makeText(context, "Saved ${currentStation.name} to preset ${slot + 1}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Play a station, then hold a preset to save it", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onToggleFavorite = viewModel.store::toggleFavorite,
                        onMore = { optionsFor = it },
                        onGenre = viewModel::selectGenre,
                        onQuery = viewModel::setQuery,
                        onRetry = viewModel::retry,
                        onRequestLocation = requestLocation,
                        onPickRegion = { showLocationSheet = true },
                    )
                    RadioViewMode.DIAL -> RadioDial(
                        state = state,
                        presets = presets,
                        playingUuid = playingUuid,
                        isPlaying = isPlaying,
                        bottomPadding = contentBottom,
                        onTune = play,
                        onPlayPause = { playerViewModel.playPause() },
                        onSavePreset = { slot, station ->
                            viewModel.store.setPreset(slot, station)
                            Toast.makeText(context, "Saved ${station.name} to preset ${slot + 1}", Toast.LENGTH_SHORT).show()
                        },
                    )
                    RadioViewMode.MAP -> RadioMap(
                        state = state,
                        playingUuid = playingUuid,
                        isPlaying = isPlaying,
                        bottomPadding = contentBottom,
                        onPlay = play,
                        onToggleFavorite = viewModel.store::toggleFavorite,
                        isFavorite = { uuid -> favorites.any { it.uuid == uuid } },
                    )
                }
            }
        }
    }

    if (showLocationSheet) {
        LocationSheet(
            viewModel = viewModel,
            state = state,
            onUseDeviceLocation = {
                showLocationSheet = false
                if (state.hasLocationPermission) viewModel.clearManualHome() else requestLocation()
            },
            onDismiss = { showLocationSheet = false },
        )
    }

    optionsFor?.let { station ->
        StationOptionsSheet(
            station = station,
            presets = presets,
            isFavorite = favorites.any { it.uuid == station.uuid },
            onPlay = { play(station); optionsFor = null },
            onToggleFavorite = { viewModel.store.toggleFavorite(station) },
            onSetPreset = { slot ->
                viewModel.store.setPreset(slot, station)
                Toast.makeText(context, "Saved to preset ${slot + 1}", Toast.LENGTH_SHORT).show()
                optionsFor = null
            },
            onOpenHomepage = station.homepage?.let { url ->
                {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                    optionsFor = null
                }
            },
            onDismiss = { optionsFor = null },
        )
    }
}

/** LOCAL · REGION · COUNTRY · WORLD — each step out widens the station pool. */
@Composable
fun ScopeLadder(selected: RadioScope, onSelect: (RadioScope) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RadioScope.entries.forEach { scope ->
            val isSel = scope == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSel) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(scope) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    scope.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = GoogleSansRounded),
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RadioList(
    state: RadioUiState,
    presets: List<RadioStation?>,
    favorites: List<RadioStation>,
    recents: List<RadioStation>,
    playingUuid: String?,
    isPlaying: Boolean,
    searchOpen: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onPlay: (RadioStation) -> Unit,
    onPresetLongPress: (Int) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    onMore: (RadioStation) -> Unit,
    onGenre: (String?) -> Unit,
    onQuery: (String) -> Unit,
    onRetry: () -> Unit,
    onRequestLocation: () -> Unit,
    onPickRegion: () -> Unit,
) {
    val favoriteIds = remember(favorites) { favorites.mapTo(HashSet()) { it.uuid } }
    val grouped = remember(state.stations, state.scope) {
        if (state.scope == RadioScope.LOCAL) listOf("" to state.stations)
        else state.stations.groupBy { RadioScopeFilter.groupKey(state.scope, it) }
            .entries.sortedByDescending { it.value.size }
            .map { it.key to it.value }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (searchOpen) {
            item(key = "search") {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    placeholder = { Text("Station name or genre") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
        }

        item(key = "presets") {
            SectionHeader("Presets", hint = "hold to save")
            PresetRow(presets, playingUuid, onPlay, onPresetLongPress)
        }

        if (state.query.isBlank() && favorites.isNotEmpty()) {
            item(key = "favorites") {
                SectionHeader("Favourites")
                StationChips(favorites, playingUuid, onPlay)
            }
        }
        if (state.query.isBlank() && recents.isNotEmpty()) {
            item(key = "recents") {
                SectionHeader("Recently played")
                StationChips(recents.take(10), playingUuid, onPlay)
            }
        }

        if (state.genres.isNotEmpty()) {
            item(key = "genres") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    items(state.genres, key = { it }) { g ->
                        FilterChip(
                            selected = state.selectedGenre == g,
                            onClick = { onGenre(if (state.selectedGenre == g) null else g) },
                            label = { Text(g.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }

        when {
            state.loading || state.locating -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> item(key = "error") {
                MessageCard(
                    title = "No signal",
                    body = state.error,
                    action = "Try again" to onRetry,
                )
            }
            state.notice == RadioNotice.NEEDS_LOCATION -> item(key = "notice") {
                MessageCard(
                    title = "Stations near you",
                    body = "Allow approximate location to find stations within about ${RadioScopeFilter.LOCAL_RADIUS_KM.toInt()} km, or pick your region by hand.",
                    action = "Use my location" to onRequestLocation,
                    secondary = "Pick region" to onPickRegion,
                )
            }
            state.notice == RadioNotice.NEEDS_REGION || state.notice == RadioNotice.NEEDS_COUNTRY -> item(key = "notice") {
                MessageCard(
                    title = "Where are you listening from?",
                    body = "Pick your country and state or territory to see its stations.",
                    action = "Pick region" to onPickRegion,
                    secondary = "Use my location" to onRequestLocation,
                )
            }
            state.stations.isEmpty() && state.searchResults.isNullOrEmpty() -> item(key = "empty") {
                MessageCard(
                    title = "Nothing on this band",
                    body = if (state.query.isNotBlank() || state.selectedGenre != null) "No stations match. Try another filter." else "No stations listed here yet. Try a wider scope.",
                )
            }
        }

        if (!state.loading) {
            grouped.forEach { (group, stations) ->
                if (stations.isEmpty()) return@forEach
                item(key = "h_$group") {
                    SectionHeader(
                        title = when (state.scope) {
                            RadioScope.LOCAL -> "Near you"
                            else -> group
                        },
                        hint = "${stations.size}",
                    )
                }
                items(stations, key = { "s_${group}_${it.uuid}" }) { station ->
                    StationRow(
                        station = station,
                        distanceKm = state.distances[station.uuid],
                        isCurrent = station.uuid == playingUuid,
                        isPlaying = isPlaying,
                        isFavorite = station.uuid in favoriteIds,
                        onClick = { onPlay(station) },
                        onToggleFavorite = { onToggleFavorite(station) },
                        onMore = { onMore(station) },
                    )
                }
            }
        }

        val shownIds = state.stations.mapTo(HashSet()) { it.uuid }
        val extra = state.searchResults?.filterNot { it.uuid in shownIds }.orEmpty()
        if (state.searching || extra.isNotEmpty()) {
            item(key = "search_header") {
                val home = state.home
                SectionHeader(
                    if (state.scope == RadioScope.WORLD || home?.countryCode == null) "Everywhere"
                    else "Elsewhere in ${home.countryName ?: home.countryCode}",
                    hint = if (state.searching) "searching…" else "${extra.size}",
                )
            }
            items(extra, key = { "q_${it.uuid}" }) { station ->
                StationRow(
                    station = station,
                    distanceKm = state.home?.let { RadioGeo.distanceKm(it, station) },
                    isCurrent = station.uuid == playingUuid,
                    isPlaying = isPlaying,
                    isFavorite = station.uuid in favoriteIds,
                    onClick = { onPlay(station) },
                    onToggleFavorite = { onToggleFavorite(station) },
                    onMore = { onMore(station) },
                )
            }
        }

        item(key = "credit") {
            Text(
                "Station directory: radio-browser.info",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, hint: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = GoogleSansRounded, letterSpacing = 1.2.sp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Six numbered buttons, like a car radio: tap to tune, hold to save the current station. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PresetRow(
    presets: List<RadioStation?>,
    playingUuid: String?,
    onPlay: (RadioStation) -> Unit,
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(RadioStore.PRESET_COUNT) { slot ->
            val station = presets.getOrNull(slot)
            val active = station != null && station.uuid == playingUuid
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.9f)
                    .clip(RoundedCornerShape(14.dp))
                    .combinedClickable(
                        onClick = { station?.let(onPlay) ?: onLongPress(slot) },
                        onLongClick = { onLongPress(slot) },
                    ),
                shape = RoundedCornerShape(14.dp),
                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    Modifier.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "${slot + 1}",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        station?.let { shortName(it) } ?: "--",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** "Triple M Brisbane 104.5" → "104.5", else the first word. Fits a preset button. */
fun shortName(station: RadioStation): String =
    station.frequency?.let { frequencyNumber(it) }
        ?: station.name.split(' ', '-', '|').firstOrNull { it.isNotBlank() }?.take(6)
        ?: station.name.take(6)

/** "104.5" for FM, "612" for AM. */
fun frequencyNumber(f: RadioFrequency): String =
    if (f.band == RadioBand.FM) "%.1f".format(f.value) else f.value.toInt().toString()

@Composable
private fun StationChips(stations: List<RadioStation>, playingUuid: String?, onPlay: (RadioStation) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
        items(stations, key = { it.uuid }) { s ->
            val active = s.uuid == playingUuid
            Surface(
                onClick = { onPlay(s) },
                shape = RoundedCornerShape(16.dp),
                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.width(96.dp)
            ) {
                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    StationLogo(s, Modifier.size(56.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(s.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun StationLogo(station: RadioStation, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Radio, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
        if (station.favicon != null) {
            SmartImage(
                model = station.favicon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        }
    }
}

@Composable
fun StationRow(
    station: RadioStation,
    distanceKm: Double?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMore: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                StationLogo(station, Modifier.size(48.dp))
                if (isCurrent) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Equalizer else Icons.Rounded.Radio,
                            contentDescription = if (isPlaying) "Playing" else "Paused",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        station.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    station.frequency?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${it.band.label} ${frequencyNumber(it)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    listOfNotNull(
                        station.subtitle.takeIf { it.isNotBlank() },
                        distanceKm?.let { RadioGeo.formatDistance(it) },
                        station.bitrate.takeIf { it > 0 }?.let { "${it}k" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favourites" else "Add to favourites",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onMore != null) {
                IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
fun MessageCard(
    title: String,
    body: String,
    action: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Radio, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                secondary?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
                action?.let { (label, onClick) -> FilledTonalButton(onClick = onClick) { Text(label) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationOptionsSheet(
    station: RadioStation,
    presets: List<RadioStation?>,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetPreset: (Int) -> Unit,
    onOpenHomepage: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StationLogo(station, Modifier.size(56.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(station.name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded), maxLines = 2)
                    Text(
                        listOfNotNull(station.state, station.country, station.codec, station.bitrate.takeIf { it > 0 }?.let { "$it kbps" })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (station.tags.isNotEmpty()) {
                Text(
                    station.tags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPlay) { Text("Play") }
                FilledTonalButton(onClick = onToggleFavorite) {
                    Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isFavorite) "Favourite" else "Add to favourites")
                }
            }
            SectionHeader("Save to preset")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(RadioStore.PRESET_COUNT) { slot ->
                    val holder = presets.getOrNull(slot)
                    val isThis = holder?.uuid == station.uuid
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable { onSetPreset(slot) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${slot + 1}",
                                fontWeight = FontWeight.Bold,
                                color = if (isThis) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                holder?.let { shortName(it) } ?: "empty",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                color = if (isThis) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (onOpenHomepage != null) {
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("Open station website") },
                    leadingContent = { Icon(Icons.Rounded.Language, null) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenHomepage)
                )
            }
        }
    }
}

/** Where "local" is: device location, or a country + state picked by hand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSheet(
    viewModel: RadioViewModel,
    state: RadioUiState,
    onUseDeviceLocation: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val countries = remember { viewModel.countries() }
    var filter by remember { mutableStateOf("") }
    var chosenCountry by remember { mutableStateOf<Pair<String, String>?>(null) }
    var states by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }

    LaunchedEffect(chosenCountry) {
        val cc = chosenCountry?.first ?: return@LaunchedEffect
        states = null
        states = viewModel.statesFor(cc)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                if (chosenCountry == null) "Where are you listening?" else chosenCountry!!.second,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
            )
            Text(
                state.home?.let { "Now: ${it.label}${if (it.isManual) " (picked)" else ""}" } ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (chosenCountry == null) {
                FilledTonalButton(onClick = onUseDeviceLocation, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Use my location")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Search countries") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth()
                )
                val shown = remember(filter) {
                    countries.filter { filter.isBlank() || it.second.contains(filter, true) || it.first.equals(filter, true) }
                }
                LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                    items(shown, key = { it.first }) { c ->
                        ListItem(
                            headlineContent = { Text(c.second) },
                            supportingContent = { Text(c.first) },
                            modifier = Modifier.clickable { chosenCountry = c }
                        )
                    }
                }
            } else {
                val (cc, name) = chosenCountry!!
                TextButton(onClick = { chosenCountry = null }) { Text("‹ Other country") }
                ListItem(
                    headlineContent = { Text("Whole country") },
                    supportingContent = { Text("Skip the state; Local and Region will be empty") },
                    modifier = Modifier.clickable {
                        viewModel.setManualHome(cc, name, null)
                        onDismiss()
                    }
                )
                HorizontalDivider()
                val list = states
                if (list == null) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (list.isEmpty()) {
                    Text(
                        "No states listed for $name.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                        items(list, key = { it.first }) { (st, count) ->
                            ListItem(
                                headlineContent = { Text(st) },
                                trailingContent = { Text("$count") },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        viewModel.setManualHome(cc, name, st)
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Small round "LIVE" pill used by the dial and map. */
@Composable
fun LiveBadge(active: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "LIVE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (active) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
