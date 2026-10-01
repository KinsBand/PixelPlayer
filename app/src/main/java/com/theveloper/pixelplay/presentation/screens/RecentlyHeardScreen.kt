package com.theveloper.pixelplay.presentation.screens

import android.text.format.DateFormat as AndroidDateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.data.recognition.RecentlyHeardEntry
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.presentation.viewmodel.RecentlyHeardViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A day's worth of heard songs ("Today", "Yesterday", "Sunday 27 September"). */
private data class HeardDayGroup(val key: String, val label: String, val entries: List<RecentlyHeardEntry>)

/**
 * Recently heard: everything Now Playing recognised, newest first, grouped by day like
 * Android's Now Playing history. Each song can be liked, played, or opened in the usual
 * song options sheet.
 */
@Composable
fun RecentlyHeardScreen(
    playerViewModel: PlayerViewModel,
    navController: NavController,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    viewModel: RecentlyHeardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val resolvingId by viewModel.resolvingId.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var optionsEntry by remember { mutableStateOf<RecentlyHeardEntry?>(null) }
    var showSongInfo by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BackHandler(enabled = searchOpen) {
        searchOpen = false
        query = ""
    }

    val filtered = remember(entries, query) {
        val q = query.trim()
        if (q.isEmpty()) entries else entries.filter {
            it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true)
        }
    }
    val groups = remember(filtered) { groupByDay(filtered) }
    val is24h = remember(context) { AndroidDateFormat.is24HourFormat(context) }
    val timeFormatter = remember(is24h) {
        DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", Locale.getDefault())
    }

    val notFound: (RecentlyHeardEntry) -> Unit = { entry ->
        Toast.makeText(context, "Couldn't find “${entry.title}”", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        RecentlyHeardHeader(
            searchOpen = searchOpen,
            query = query,
            onQueryChange = { query = it },
            onOpenSearch = { searchOpen = true },
            onCloseSearch = {
                searchOpen = false
                query = ""
            },
            onBack = { navController.popBackStack() }
        )

        if (groups.isEmpty()) {
            RecentlyHeardEmpty(searching = query.isNotBlank())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 12.dp,
                    bottom = MiniPlayerHeight + navInset + 96.dp
                )
            ) {
                groups.forEach { group ->
                    item(key = "day_${group.key}", contentType = "day") {
                        Text(
                            text = group.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
                        )
                    }
                    items(group.entries, key = { it.id }, contentType = { "heard" }) { entry ->
                        RecentlyHeardRow(
                            entry = entry,
                            time = Instant.ofEpochMilli(entry.heardAtEpochMs)
                                .atZone(ZoneId.systemDefault())
                                .format(timeFormatter)
                                .lowercase(Locale.getDefault()),
                            isFavorite = entry.songId != null && favoriteSongIds.contains(entry.songId),
                            isResolving = resolvingId == entry.id,
                            onClick = {
                                viewModel.withSong(entry, onMissing = { notFound(entry) }) { song ->
                                    playerViewModel.playSong(song)
                                }
                            },
                            onToggleFavorite = {
                                viewModel.withSong(entry, onMissing = { notFound(entry) }) { song ->
                                    playerViewModel.toggleFavoriteSpecificSong(song)
                                }
                            },
                            onMoreOptions = {
                                viewModel.withSong(entry, onMissing = { notFound(entry) }) { song ->
                                    optionsEntry = entry
                                    playerViewModel.selectSongForInfo(song)
                                    showSongInfo = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // The same song options sheet used everywhere else.
    val infoSong = selectedSongForInfo
    if (showSongInfo && infoSong != null) {
        SongInfoBottomSheet(
            song = infoSong,
            isFavorite = favoriteSongIds.contains(infoSong.id),
            onToggleFavorite = { playerViewModel.toggleFavoriteSpecificSong(infoSong) },
            onDismiss = {
                showSongInfo = false
                showPlaylistSheet = false
            },
            onPlaySong = { playerViewModel.playSong(infoSong) },
            onAddToQueue = { playerViewModel.addSongToQueue(infoSong) },
            onAddNextToQueue = { playerViewModel.addSongNextToQueue(infoSong) },
            onAddToPlayList = { showPlaylistSheet = true },
            onDeleteFromDevice = playerViewModel::deleteFromDevice,
            onNavigateToAlbum = {
                navController.navigateSafely(Screen.AlbumDetail.createRoute(infoSong.albumId))
                showSongInfo = false
            },
            onNavigateToArtist = {
                navController.navigateSafely(Screen.ArtistDetail.createRouteForSong(infoSong))
                showSongInfo = false
            },
            onNavigateToArtistById = { artistId ->
                navController.navigateSafely(Screen.ArtistDetail.createRoute(artistId))
                showSongInfo = false
            },
            onNavigateToArtistByName = { artistName ->
                navController.navigateSafely(Screen.ArtistDetail.createRouteForName(artistName))
                showSongInfo = false
            },
            onNavigateToGenre = {
                infoSong.genre?.let {
                    navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                }
                showSongInfo = false
            },
            onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                playerViewModel.editSongMetadata(
                    infoSong,
                    newTitle,
                    newArtist,
                    newAlbum,
                    newAlbumArtist,
                    newComposer,
                    newGenre,
                    newLyrics,
                    newTrackNumber,
                    newDiscNumber,
                    replayGainTrackGainDb,
                    replayGainAlbumGainDb,
                    coverArtUpdate
                )
            },
            // "Remove from list" takes the song out of Recently heard.
            removeFromListTrigger = {
                optionsEntry?.let { viewModel.remove(it) }
                showSongInfo = false
            }
        )

        if (showPlaylistSheet) {
            PlaylistBottomSheet(
                playlistUiState = playlistUiState,
                songs = listOf(infoSong),
                onDismiss = { showPlaylistSheet = false },
                bottomBarHeight = navInset,
                playerViewModel = playerViewModel,
            )
        }
    }
}

@Composable
private fun RecentlyHeardHeader(
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledIconButton(
            onClick = onBack,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface
            ),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.width(12.dp))

        AnimatedContent(
            targetState = searchOpen,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.weight(1f),
            label = "recently_heard_header"
        ) { searching ->
            if (searching) {
                val focusRequester = remember { FocusRequester() }
                val keyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    "Search recently heard",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp),
                                cursorBrush = SolidColor(colors.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }
                        IconButton(onClick = onCloseSearch) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close search", tint = colors.onSurfaceVariant)
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Recently heard",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    FilledIconButton(
                        onClick = onOpenSearch,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.surfaceContainerHigh,
                            contentColor = colors.primary
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search recently heard")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentlyHeardRow(
    entry: RecentlyHeardEntry,
    time: String,
    isFavorite: Boolean,
    isResolving: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoreOptions: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (entry.artUri != null) {
                SmartImage(
                    model = entry.artUri,
                    contentDescription = entry.title,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            if (isResolving) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.scrim.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = colors.onPrimary
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(entry.artist, time).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Unlike" else "Like",
                tint = if (isFavorite) colors.primary else colors.onSurfaceVariant
            )
        }
        IconButton(onClick = onMoreOptions) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecentlyHeardEmpty(searching: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (searching) "No matches" else "Nothing heard yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (searching) {
                "Try a different song or artist."
            } else {
                "Songs Now Playing recognises around you show up here. Tap the mic, then Listen."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun groupByDay(entries: List<RecentlyHeardEntry>): List<HeardDayGroup> {
    if (entries.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val locale = Locale.getDefault()
    val thisYear = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    val otherYear = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)
    return entries
        .groupBy { Instant.ofEpochMilli(it.heardAtEpochMs).atZone(zone).toLocalDate() }
        .toSortedMap(compareByDescending { it })
        .map { (day, dayEntries) ->
            val label = when {
                day == today -> "Today"
                day == today.minusDays(1) -> "Yesterday"
                day.year == today.year -> day.format(thisYear)
                else -> day.format(otherYear)
            }
            HeardDayGroup(day.toString(), label, dayEntries.sortedByDescending { it.heardAtEpochMs })
        }
}

/** "12:33 am" / "00:33", matching the Recently heard list. */
internal fun formatHeardTime(context: android.content.Context, epochMs: Long): String {
    val pattern = if (AndroidDateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
        .lowercase(Locale.getDefault())
}

/** For the Search card: the time if today, otherwise "Yesterday" or the date. */
internal fun formatHeardWhen(context: android.content.Context, epochMs: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (day) {
        today -> formatHeardTime(context, epochMs)
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}
