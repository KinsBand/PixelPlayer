package com.theveloper.pixelplay.presentation.screens.radio

import com.theveloper.pixelplay.presentation.components.offlineVeil
import com.theveloper.pixelplay.presentation.components.ExpressiveFilterChip
import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.theveloper.pixelplay.data.radio.RadioHome
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.radio.RadioPlayback
import com.theveloper.pixelplay.data.radio.RadioScope
import com.theveloper.pixelplay.data.radio.RadioScopeFilter
import com.theveloper.pixelplay.data.radio.RadioStation
import com.theveloper.pixelplay.data.radio.RadioStore
import com.theveloper.pixelplay.data.radio.RadioViewMode
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.rememberAnimatedSheetDismiss
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.RadioNotice
import com.theveloper.pixelplay.presentation.viewmodel.RadioUiState
import com.theveloper.pixelplay.presentation.viewmodel.RadioViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.launch

/**
 * Internet radio: local → region → country → world, browsed as a list or on a map.
 * Stations come from the Radio Browser directory and play through the normal player.
 *
 * One header row holds everything: where you're listening (tap for scope and location),
 * search, and the list/map switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(
    playerViewModel: PlayerViewModel,
    // Radio is a bottom-nav tab, so there's no back arrow; kept for any caller that pushes it.
    onBackClick: (() -> Unit)? = null,
    viewModel: RadioViewModel = hiltViewModel(),
    /** The shared tab header's round settings button. */
    onSettingsClick: () -> Unit = {},
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
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()

    var showLocationSheet by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<RadioStation?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }

    // The player's map button: open the map on that station.
    LaunchedEffect(Unit) {
        com.theveloper.pixelplay.data.radio.RadioMapFocus.pending.collect { pending ->
            if (pending == null) return@collect
            val station = com.theveloper.pixelplay.data.radio.RadioMapFocus.consume() ?: return@collect
            if (searchOpen) {
                searchOpen = false
                viewModel.setQuery("")
            }
            if (station.hasGeo) {
                viewModel.showOnMap(station)
            } else {
                viewModel.setViewMode(RadioViewMode.MAP)
                Toast.makeText(context, "${station.name} doesn't list where it is", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onLocationPermissionResult(granted)
        if (!granted) Toast.makeText(context, "Pick your region instead to get local stations", Toast.LENGTH_SHORT).show()
    }
    val requestLocation = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }

    // The radio player's "Radio" button: reopen this screen with the filters the station was
    // found with (scope, filter chip and section, search text, list or map).
    LaunchedEffect(Unit) {
        com.theveloper.pixelplay.data.radio.RadioOpenRequest.pending.collect { pending ->
            if (pending == null) return@collect
            val request = com.theveloper.pixelplay.data.radio.RadioOpenRequest.consume() ?: return@collect
            val station = request.station
            val filters = request.filters
            if (filters != null) {
                // Scope first: changing it clears the filter chip.
                viewModel.setScope(filters.scope)
                viewModel.selectCategory(filters.category, filters.section)
                if (filters.query.isNotBlank()) {
                    searchOpen = true
                    viewModel.setQuery(filters.query)
                } else if (searchOpen || viewModel.state.value.query.isNotEmpty()) {
                    searchOpen = false
                    viewModel.setQuery("")
                }
                if (filters.viewMode == RadioViewMode.MAP && station.hasGeo && filters.query.isBlank()) {
                    viewModel.showOnMap(station)
                } else {
                    viewModel.setViewMode(filters.viewMode)
                }
            } else {
                // Started from somewhere else (home, a widget, a preset): show its kind of
                // station, with the station itself focused on the map when it has a place.
                if (searchOpen) {
                    searchOpen = false
                    viewModel.setQuery("")
                }
                val category = com.theveloper.pixelplay.data.radio.RadioCategories.categoriesOf(station).firstOrNull()
                viewModel.selectCategory(category, null)
                if (station.hasGeo) viewModel.showOnMap(station) else viewModel.setViewMode(RadioViewMode.LIST)
            }
        }
    }

    val play: (RadioStation) -> Unit = { station ->
        if (station.uuid == playingUuid) {
            playerViewModel.playPause()
        } else {
            val song = RadioPlayback.toSong(station)
            playerViewModel.playSongs(listOf(song), song, RadioPlayback.QUEUE_NAME)
            viewModel.onStationPlayed(station)
            // Remember how it was found, for the player's Radio button.
            com.theveloper.pixelplay.data.radio.RadioOrigin.record(
                station.uuid,
                com.theveloper.pixelplay.data.radio.RadioFilterSnapshot(
                    scope = state.scope,
                    category = state.selectedCategory,
                    section = state.selectedGenre,
                    query = state.query,
                    viewMode = state.viewMode,
                )
            )
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
            RadioHeader(
                state = state,
                searchOpen = searchOpen,
                onOpenSearch = {
                    searchOpen = true
                    if (state.viewMode != RadioViewMode.LIST) viewModel.setViewMode(RadioViewMode.LIST)
                },
                onCloseSearch = {
                    searchOpen = false
                    viewModel.setQuery("")
                },
                onQuery = viewModel::setQuery,
                onScope = viewModel::setScope,
                onChangeLocation = { showLocationSheet = true },
                onBackClick = onBackClick,
                onSettingsClick = onSettingsClick,
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // A saved list is on screen while a fresh one loads: say so without blocking anything.
            AnimatedVisibility(visible = state.refreshing && state.viewMode == RadioViewMode.LIST) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 2.dp)
                        .clip(CircleShape)
                )
            }

            // Clear the bottom nav bar (Radio is a tab now) plus the mini player.
            val contentBottom = resolveNavBarOccupiedHeight(bottomInset, navBarCompactMode) + MiniPlayerHeight + 16.dp
            Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = state.viewMode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "radioViewMode",
                modifier = Modifier.fillMaxSize()
            ) { mode ->
                when (mode) {
                    RadioViewMode.LIST -> RadioList(
                        state = state,
                        presets = presets,
                        favorites = favorites,
                        recents = recents,
                        playingUuid = playingUuid,
                        isPlaying = isPlaying,
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
                        onCategory = viewModel::selectCategory,
                        onRetry = viewModel::retry,
                        onRequestLocation = requestLocation,
                        onPickRegion = { showLocationSheet = true },
                    )
                    RadioViewMode.MAP -> RadioMap(
                        controller = viewModel.map,
                        state = state,
                        playingUuid = playingUuid,
                        isPlaying = isPlaying,
                        bottomPadding = contentBottom,
                        // The map's own buttons sit above the list/map button.
                        controlsBottomPadding = ViewModeFabClearance,
                        onPlay = play,
                        onToggleFavorite = viewModel.store::toggleFavorite,
                        isFavorite = { uuid -> favorites.any { it.uuid == uuid } },
                        onMore = { optionsFor = it },
                    )
                }
            }
            // List ↔ map: one action button, bottom right. It never hides: when the nav bar
            // slides away on scroll it moves down into the space the bar left, then back up
            // with it, exactly like the mini player (same margin rule as MainActivity).
            val navOccupied = resolveNavBarOccupiedHeight(bottomInset, navBarCompactMode)
            ViewModeFab(
                mode = state.viewMode,
                onToggle = {
                    val next = if (state.viewMode == RadioViewMode.LIST) RadioViewMode.MAP else RadioViewMode.LIST
                    if (next == RadioViewMode.MAP && searchOpen) {
                        searchOpen = false
                        viewModel.setQuery("")
                    }
                    viewModel.setViewMode(next)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = contentBottom)
                    .graphicsLayer {
                        val progress = com.theveloper.pixelplay.presentation.navigation.NavBarVisibility.visibilityProgress.value
                        val occupiedPx = navOccupied.toPx()
                        val margin = maxOf(occupiedPx * progress, bottomInset.toPx())
                        translationY = (occupiedPx - margin).coerceAtLeast(0f)
                    }
            )
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
        RadioStationOptionsSheet(
            station = station,
            presets = presets,
            isFavorite = favorites.any { it.uuid == station.uuid },
            isCurrent = station.uuid == playingUuid,
            isPlaying = isPlaying,
            // The sheet slides itself away before running these (then calls onDismiss).
            onPlay = { play(station) },
            onToggleFavorite = { viewModel.store.toggleFavorite(station) },
            onSetPreset = { slot ->
                viewModel.store.setPreset(slot, station)
                Toast.makeText(context, "Saved to preset ${slot + 1}", Toast.LENGTH_SHORT).show()
            },
            onOpenHomepage = station.homepage?.let { { openStationHomepage(context, station) } },
            onShowOnMap = { viewModel.showOnMap(station) },
            onDismiss = { optionsFor = null },
            // Queue · Next · Soon: the station queues like a song (a live stream never ends on its own).
            onQueue = {
                playerViewModel.addSongToQueue(RadioPlayback.toSong(station))
                playerViewModel.sendToast(context.getString(R.string.radio_queued, station.name))
                noteLiveQueued(context)
            },
            onNext = {
                playerViewModel.addSongNextToQueue(RadioPlayback.toSong(station))
                playerViewModel.sendToast(context.getString(R.string.radio_plays_next, station.name))
                noteLiveQueued(context)
            },
            onSoon = { k ->
                playerViewModel.addSongAfterUpcoming(RadioPlayback.toSong(station), k)
                noteLiveQueued(context)
            },
            soonPlayer = playerViewModel,
        )
    }
}

/** Shown once per app run, the first time a live station is queued. */
private var liveQueueNoteShown = false // main thread only

private fun noteLiveQueued(context: android.content.Context) {
    if (liveQueueNoteShown) return
    liveQueueNoteShown = true
    Toast.makeText(context, context.getString(R.string.radio_queue_live_note), Toast.LENGTH_LONG).show()
}

/**
 * The shared tab header: "Radio", then (right side) the location pill, Search, Settings.
 * Search opens a search field under the header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadioHeader(
    state: RadioUiState,
    searchOpen: Boolean,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onQuery: (String) -> Unit,
    onScope: (RadioScope) -> Unit,
    onChangeLocation: () -> Unit,
    onBackClick: (() -> Unit)?,
    onSettingsClick: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    com.theveloper.pixelplay.presentation.components.TabHeader(
        title = "Radio",
        onSettingsClick = onSettingsClick,
        titleContent = if (onBackClick == null) null else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        onClick = onBackClick
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                    Spacer(Modifier.width(8.dp))
                    com.theveloper.pixelplay.presentation.components.TabHeaderTitle("Radio")
                }
            }
        },
        actions = {
            // Location between the title and Search; Search sits left of Settings.
            ScopePill(state = state, onScope = onScope, onChangeLocation = onChangeLocation)
            FilledTonalIconButton(
                onClick = { if (searchOpen) onCloseSearch() else onOpenSearch() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (searchOpen) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = if (searchOpen) "Close search" else "Search stations"
                )
            }
        }
    )
    AnimatedVisibility(visible = searchOpen) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            RadioSearchField(initial = state.query, onQuery = onQuery, onClose = onCloseSearch)
        }
    }
  }
}

/** Space the map keeps free above its own buttons for the list/map action button. */
private val ViewModeFabClearance = 72.dp

/** List ↔ map as one action button; the icon shows where a tap goes. */
@Composable
private fun ViewModeFab(mode: RadioViewMode, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val toMap = mode == RadioViewMode.LIST
    androidx.compose.material3.FloatingActionButton(
        onClick = onToggle,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = toMap,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "radioViewModeFab"
        ) { showMapIcon ->
            Icon(
                if (showMapIcon) Icons.Rounded.Map else Icons.AutoMirrored.Rounded.List,
                contentDescription = if (showMapIcon) "Show map" else "Show list"
            )
        }
    }
}

/** Where you're listening + how far out: "Local · Brisbane". Tap for scopes and location. */
@Composable
private fun ScopePill(
    state: RadioUiState,
    onScope: (RadioScope) -> Unit,
    onChangeLocation: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val home = state.home
    Box {
        Surface(
            onClick = { menuOpen = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    scopeIcon(state.scope),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                // Just the scope here ("Local"); where it covers shows in the menu.
                Text(
                    if (state.locating && home == null) "Finding…" else state.scope.label,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Icon(
                    Icons.Rounded.ArrowDropDown,
                    contentDescription = "Change scope or location",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = RoundedCornerShape(20.dp),
        ) {
            RadioScope.entries.forEach { scope ->
                val isSel = scope == state.scope
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                scope.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                scopePlace(scope, home),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            scopeIcon(scope),
                            contentDescription = null,
                            tint = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = if (isSel) {
                        { Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    onClick = {
                        menuOpen = false
                        onScope(scope)
                    }
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text(if (home?.isManual == true) "Change location (${home.label})" else "Change location") },
                leadingIcon = { Icon(Icons.Rounded.EditLocationAlt, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onChangeLocation()
                }
            )
        }
    }
}

/** Search box that replaces the scope pill while searching; focused as soon as it opens. */
@Composable
private fun RadioSearchField(initial: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Row(Modifier.padding(start = 14.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) {
                    Text(
                        "Station, genre or place",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onQuery(it)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close search")
            }
        }
    }
}

/** Two-icon switch: list ↔ map. */
@Composable
private fun ViewModeToggle(selected: RadioViewMode, onSelect: (RadioViewMode) -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        RadioViewMode.entries.forEach { mode ->
            val isSel = mode == selected
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isSel) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(onClickLabel = "Show as ${mode.label.lowercase()}") { onSelect(mode) }
                    .semantics { stateDescription = if (isSel) "Selected" else "Not selected" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (mode) {
                        RadioViewMode.LIST -> Icons.AutoMirrored.Rounded.List
                        RadioViewMode.MAP -> Icons.Rounded.Map
                    },
                    contentDescription = mode.label,
                    modifier = Modifier.size(20.dp),
                    tint = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun scopeIcon(scope: RadioScope): ImageVector = when (scope) {
    RadioScope.LOCAL -> Icons.Rounded.NearMe
    RadioScope.REGION -> Icons.Rounded.Map
    RadioScope.COUNTRY -> Icons.Rounded.Flag
    RadioScope.WORLD -> Icons.Rounded.Public
}

/** The place each scope covers, for the header and its menu. */
private fun scopePlace(scope: RadioScope, home: RadioHome?): String = when (scope) {
    RadioScope.LOCAL -> home?.city
        ?: home?.state?.let { "Near you, ${com.theveloper.pixelplay.data.radio.RadioLabels.shortState(it, home.countryCode)}" }
        ?: if (home?.hasCoordinates == true) "Near you" else "Needs your location"
    RadioScope.REGION -> home?.state?.let { com.theveloper.pixelplay.data.radio.RadioLabels.shortState(it, home.countryCode) }
        ?: home?.countryName ?: "Your state or territory"
    RadioScope.COUNTRY -> home?.countryName ?: home?.countryCode ?: "Your country"
    RadioScope.WORLD -> "Everywhere"
}

@Composable
private fun RadioList(
    state: RadioUiState,
    presets: List<RadioStation?>,
    favorites: List<RadioStation>,
    recents: List<RadioStation>,
    playingUuid: String?,
    isPlaying: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onPlay: (RadioStation) -> Unit,
    onPresetLongPress: (Int) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    onMore: (RadioStation) -> Unit,
    onCategory: (com.theveloper.pixelplay.data.radio.RadioCategory?, String?) -> Unit,
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
        // Recently played (one icon button, fixed at the start) | the simple filters.
        if (recents.isNotEmpty() || state.categories.isNotEmpty()) {
            item(key = "filters") {
                RadioFilterRow(
                    state = state,
                    recents = recents,
                    playingUuid = playingUuid,
                    onPlay = onPlay,
                    onCategory = onCategory,
                )
            }
        }

        when {
            (state.loading || state.locating) && state.stations.isEmpty() -> item(key = "loading") {
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
    val radioOffline = com.theveloper.pixelplay.presentation.components.rememberNeedsOnlineBlocked()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(RadioStore.PRESET_COUNT) { slot ->
            val station = presets.getOrNull(slot)
            val active = station != null && station.uuid == playingUuid
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.9f)
                    .clip(RoundedCornerShape(14.dp))
                    .offlineVeil(station != null && radioOffline, RoundedCornerShape(14.dp))
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
                modifier = Modifier
                    .width(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .offlineVeil(com.theveloper.pixelplay.presentation.components.rememberNeedsOnlineBlocked(), RoundedCornerShape(16.dp))
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
        shape = RoundedCornerShape(18.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        // No ⋮ button: hold the station for its options.
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            // Radio always streams: veiled while offline.
            .offlineVeil(com.theveloper.pixelplay.presentation.components.rememberNeedsOnlineBlocked(), RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onMore, onLongClickLabel = "Station options")
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
            Spacer(Modifier.width(8.dp))
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

    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()
    val dismissSheetAnimated = rememberAnimatedSheetDismiss(sheetState, onDismiss)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
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
                        dismissSheetAnimated()
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
                                        dismissSheetAnimated()
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

/**
 * [🕘] | Music  News & talk  Sport  Community …
 * Tap a filter to show only it (tap again for all). Hold it for its sections found in this
 * area (Music → Rock, 80s, Dance…), each with how many stations have it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RadioFilterRow(
    state: RadioUiState,
    recents: List<RadioStation>,
    playingUuid: String?,
    onPlay: (RadioStation) -> Unit,
    onCategory: (com.theveloper.pixelplay.data.radio.RadioCategory?, String?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (recents.isNotEmpty()) {
            var recentsOpen by remember { mutableStateOf(false) }
            Box {
                FilledTonalIconButton(
                    onClick = { recentsOpen = true },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.surfaceContainerHigh)
                ) {
                    Icon(Icons.Rounded.History, contentDescription = "Recently played stations")
                }
                DropdownMenu(
                    expanded = recentsOpen,
                    onDismissRequest = { recentsOpen = false },
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        "Recently played",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    recents.take(10).forEach { station ->
                        DropdownMenuItem(
                            text = { Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { StationLogo(station, Modifier.size(32.dp)) },
                            trailingIcon = if (station.uuid == playingUuid) {
                                { Icon(Icons.Rounded.Equalizer, contentDescription = "Playing", tint = colors.primary) }
                            } else null,
                            onClick = {
                                recentsOpen = false
                                onPlay(station)
                            }
                        )
                    }
                }
            }
            androidx.compose.material3.VerticalDivider(
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 6.dp),
                color = colors.outlineVariant
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 4.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(state.categories, key = { it.category.name }) { group ->
                RadioCategoryChip(
                    group = group,
                    selected = state.selectedCategory == group.category,
                    selectedSection = state.selectedGenre.takeIf { state.selectedCategory == group.category },
                    onSelect = { section -> onCategory(group.category, section) },
                    onClear = { onCategory(null, null) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RadioCategoryChip(
    group: com.theveloper.pixelplay.data.radio.RadioCategoryGroup,
    selected: Boolean,
    selectedSection: String?,
    onSelect: (section: String?) -> Unit,
    onClear: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var sectionsOpen by remember { mutableStateOf(false) }
    val label = group.category.label + (selectedSection?.let {
        " · " + com.theveloper.pixelplay.data.radio.RadioCategories.sectionLabel(it)
    } ?: "")
    Box {
        ExpressiveFilterChip(
            label = label,
            selected = selected,
            onClick = { if (selected) onClear() else onSelect(null) },
            onLongClick = {
                if (group.sections.isNotEmpty()) {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    sectionsOpen = true
                }
            },
            onClickLabel = if (selected) "Show all stations" else "Show only ${group.category.label}",
            onLongClickLabel = "Show ${group.category.label} sections",
            trailingIcon = if (group.sections.isNotEmpty()) {
                {
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            } else null
        )
        DropdownMenu(
            expanded = sectionsOpen,
            onDismissRequest = { sectionsOpen = false },
            shape = RoundedCornerShape(20.dp),
        ) {
            DropdownMenuItem(
                text = { Text("All ${group.category.label.lowercase()}") },
                trailingIcon = { Text("${group.count}", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) },
                leadingIcon = if (selected && selectedSection == null) {
                    { Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = colors.primary) }
                } else null,
                onClick = {
                    sectionsOpen = false
                    onSelect(null)
                }
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            group.sections.forEach { (section, count) ->
                val isSel = selected && selectedSection == section
                DropdownMenuItem(
                    text = { Text(com.theveloper.pixelplay.data.radio.RadioCategories.sectionLabel(section)) },
                    trailingIcon = { Text("$count", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) },
                    leadingIcon = if (isSel) {
                        { Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = colors.primary) }
                    } else null,
                    onClick = {
                        sectionsOpen = false
                        onSelect(section)
                    }
                )
            }
        }
    }
}
