package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.components.subcomps.rememberShuffleSpin
import com.theveloper.pixelplay.presentation.components.subcomps.shuffleSpin
import com.theveloper.pixelplay.presentation.navigation.popBackStackSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.components.ExpressiveFilterChip

import android.os.Trace
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.RecentlyPlayedRangeSelector
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.components.subcomps.TightWrapText
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.model.RecentlyPlayedSongUiModel
import com.theveloper.pixelplay.presentation.model.collectRecentlyPlayedSongIds
import com.theveloper.pixelplay.presentation.model.mapRecentlyPlayedSongs
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.LikedSongsViewModel
import com.theveloper.pixelplay.presentation.model.resolveBounds
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.text.format.DateFormat as AndroidDateFormat
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringResource

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun RecentlyPlayedScreen(
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavController,
    /** "Listened" (default) or "Liked", from the Home "Back again" pill. */
    initialMode: String? = null,
    likedSongsViewModel: LikedSongsViewModel = hiltViewModel()
) {
    Trace.beginSection("RecentlyPlayedScreen.Composition")

    val context = LocalContext.current
    val queueRecentlyPlayed = stringResource(R.string.recently_played_queue_name)
    val shuffleLabel = stringResource(R.string.common_shuffle)
    val playbackHistory by playerViewModel.playbackHistory.collectAsStateWithLifecycle()
    val currentSongId by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.currentSong?.id }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

    var historyMode by rememberSaveable {
        mutableStateOf(if (initialMode == "Liked") HistoryMode.Liked else HistoryMode.Recent)
    }
    // Likes span much longer than a week of plays, so Liked opens on All time.
    var selectedRange by rememberSaveable {
        mutableStateOf(if (initialMode == "Liked") StatsTimeRange.ALL else StatsTimeRange.WEEK)
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val likedWithTimes by likedSongsViewModel.likedSongsWithTimes.collectAsStateWithLifecycle()
    val hasLikes = !likedWithTimes.isNullOrEmpty()
    val lazyListState = rememberLazyListState()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val bottomBarHeightDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val recentSongIds = remember(playbackHistory, selectedRange) {
        collectRecentlyPlayedSongIds(
            playbackHistory = playbackHistory,
            range = selectedRange,
            maxItems = Int.MAX_VALUE
        )
    }
    val recentSongsInitialValue: List<Song>? = remember(recentSongIds) {
        if (recentSongIds.isEmpty()) emptyList<Song>() else null
    }
    val recentlyPlayedSourceSongs by remember(recentSongIds, playerViewModel) {
        playerViewModel.observeSongs(recentSongIds)
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = recentSongsInitialValue)

    val recentlyPlayedSongs = remember(playbackHistory, recentlyPlayedSourceSongs, selectedRange) {
        val sourceSongs = recentlyPlayedSourceSongs ?: return@remember emptyList()
        mapRecentlyPlayedSongs(
            playbackHistory = playbackHistory,
            songs = sourceSongs,
            range = selectedRange,
            maxItems = Int.MAX_VALUE
        )
    }
    // Liked: every like with its like time; platform likes (no local time) only show in All time.
    val likedSongsUi = remember(likedWithTimes, selectedRange) {
        val liked = likedWithTimes ?: return@remember emptyList<RecentlyPlayedSongUiModel>()
        val (start, end) = selectedRange.resolveBounds(System.currentTimeMillis(), ZoneId.systemDefault())
        liked.mapNotNull { (song, likedAt) ->
            when {
                likedAt == null -> if (selectedRange == StatsTimeRange.ALL) {
                    RecentlyPlayedSongUiModel(song = song, lastPlayedTimestamp = UNTIMED_LIKE)
                } else null
                likedAt > end -> RecentlyPlayedSongUiModel(song = song, lastPlayedTimestamp = end)
                start != null && likedAt < start -> null
                else -> RecentlyPlayedSongUiModel(song = song, lastPlayedTimestamp = likedAt)
            }
        }
    }
    val isLiked = historyMode == HistoryMode.Liked
    val isLoading = if (isLiked) likedWithTimes == null else recentlyPlayedSourceSongs == null
    val modeSongs = if (isLiked) likedSongsUi else recentlyPlayedSongs

    // Live search over title, artist and album; date dividers are rebuilt from the matches.
    val trimmedQuery = searchQuery.trim()
    val visibleSongs = remember(modeSongs, trimmedQuery) {
        if (trimmedQuery.isEmpty()) modeSongs
        else modeSongs.filter { ui ->
            val song = ui.song
            song.title.contains(trimmedQuery, ignoreCase = true) ||
                song.artist.contains(trimmedQuery, ignoreCase = true) ||
                song.displayArtist.contains(trimmedQuery, ignoreCase = true) ||
                song.album.contains(trimmedQuery, ignoreCase = true)
        }
    }
    val untimedLabel = "Liked on your services"
    val groupedSongs = remember(visibleSongs, selectedRange, context) {
        val (untimed, timed) = visibleSongs.partition { it.lastPlayedTimestamp == UNTIMED_LIKE }
        val groups = groupRecentlyPlayedSongs(
            context = context,
            songs = timed,
            range = selectedRange
        )
        if (untimed.isEmpty()) groups
        else groups + TimestampGroup(key = "untimed", label = untimedLabel, isHourBucket = false, songs = untimed)
    }
    val queueSongs = remember(visibleSongs) {
        visibleSongs.map { it.song }.toImmutableList()
    }
    val queueName = if (isLiked) "Your Likes" else queueRecentlyPlayed
    // ---- Your Music-style chrome: header bar, hero, pinned filter row ----
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var isSearchOpen by rememberSaveable { mutableStateOf(searchQuery.isNotEmpty()) }
    fun closeSearch() {
        isSearchOpen = false
        searchQuery = ""
        focusManager.clearFocus()
    }
    androidx.activity.compose.BackHandler(enabled = isSearchOpen) { closeSearch() }
    val isHeaderCollapsed by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }
    val colors = MaterialTheme.colorScheme
    val topBarColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isHeaderCollapsed || isSearchOpen) colors.surfaceContainer else colors.primaryContainer,
        animationSpec = androidx.compose.animation.core.tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized),
        label = "historyTopBar"
    )
    val screenTitle = if (isLiked) "Liked history" else stringResource(R.string.recently_played_title)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header bar: back (left), title once scrolled / search field, search (right).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(topBarColor)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.surfaceContainerHigh),
                    onClick = {
                        if (isSearchOpen) closeSearch() else navController.popBackStackSafely()
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = if (isSearchOpen) "Close search" else stringResource(R.string.common_back)
                    )
                }

                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSearchOpen,
                        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                            androidx.compose.animation.expandHorizontally(androidx.compose.animation.core.tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate), expandFrom = Alignment.End),
                        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)) +
                            androidx.compose.animation.shrinkHorizontally(androidx.compose.animation.core.tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate), shrinkTowards = Alignment.End)
                    ) {
                        val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(50)
                            runCatching { focusRequester.requestFocus() }
                        }
                        HistorySearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholder = if (isLiked) "Search your likes" else "Search your history",
                            onSearch = { focusManager.clearFocus() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isSearchOpen && isHeaderCollapsed,
                        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)),
                        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
                    ) {
                        Text(
                            screenTitle,
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!isSearchOpen) {
                    FilledTonalIconButton(
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.surfaceContainerHigh),
                        onClick = { isSearchOpen = true }
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = if (isLiked) "Search your likes" else "Search your history"
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator()
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = MiniPlayerHeight + bottomInset + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item(key = "history_hero", contentType = "hero") {
                        HistoryHero(
                            title = screenTitle,
                            subtitle = buildString {
                                append(if (visibleSongs.size == 1) "1 song" else "${visibleSongs.size} songs")
                                append(" · ")
                                append(rangeChipLabel(selectedRange))
                                if (trimmedQuery.isNotEmpty()) append(" · matching \u201c$trimmedQuery\u201d")
                            },
                            isLiked = isLiked,
                            artUris = remember(visibleSongs) {
                                visibleSongs.asSequence().mapNotNull { it.song.albumArtUriString }.distinct().take(4).toList()
                            },
                            shuffleEnabled = queueSongs.isNotEmpty(),
                            onShuffle = {
                                playerViewModel.playSongsShuffled(
                                    songsToPlay = queueSongs,
                                    queueName = queueName,
                                    startAtZero = true,
                                )
                            }
                        )
                    }

                    // Mode + range filters sit right above the first song and stay pinned under the header.
                    stickyHeader(key = "history_filters", contentType = "filters") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(colors.surface)
                                .padding(bottom = 6.dp)
                        ) {
                            HistoryFilterRow(
                                mode = historyMode,
                                showLiked = hasLikes || isLiked,
                                range = selectedRange,
                                onModeChange = { mode ->
                                    if (mode != historyMode) {
                                        historyMode = mode
                                        // Likes reach back further than a week of plays.
                                        if (mode == HistoryMode.Liked && selectedRange == StatsTimeRange.WEEK) {
                                            selectedRange = StatsTimeRange.ALL
                                        }
                                    }
                                },
                                onRangeChange = { selectedRange = it }
                            )
                        }
                    }

                    if (visibleSongs.isEmpty()) {
                        item(key = "history_empty", contentType = "empty") {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = when {
                                        trimmedQuery.isNotEmpty() -> Icons.Rounded.Search
                                        isLiked -> Icons.Rounded.Favorite
                                        else -> Icons.Rounded.History
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = colors.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    when {
                                        trimmedQuery.isNotEmpty() -> "No matches for \u201c$trimmedQuery\u201d"
                                        isLiked -> "No likes ${rangeChipLabel(selectedRange).lowercase()}"
                                        else -> stringResource(R.string.recently_played_empty_title, selectedRange.displayName.lowercase())
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Text(
                                    when {
                                        trimmedQuery.isNotEmpty() -> "Try a different title, artist or album."
                                        isLiked -> "Songs you like show up here as you like them."
                                        else -> stringResource(R.string.recently_played_empty_subtitle)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        groupedSongs.forEachIndexed { groupIndex, group ->
                            item(key = "history_time_${historyMode.name}_${groupIndex}_${group.key}", contentType = "date_header") {
                                RecentlyPlayedTimestampDivider(
                                    label = group.label,
                                    count = group.songs.size,
                                    isHourBucket = group.isHourBucket,
                                    modifier = Modifier.animateItem()
                                )
                            }
                            items(
                                group.songs,
                                key = { songUi -> "${historyMode.name}_${songUi.song.id}" },
                                contentType = { "recently_played_song" }
                            ) { item ->
                                EnhancedSongListItem(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .animateItem(),
                                    song = item.song,
                                    isCurrentSong = currentSongId == item.song.id,
                                    isPlaying = currentSongId == item.song.id && isPlaying,
                                    onClick = {
                                        focusManager.clearFocus()
                                        playerViewModel.playSongs(
                                            songsToPlay = queueSongs,
                                            startSong = item.song,
                                            queueName = queueName
                                        )
                                    },
                                    onMoreOptionsClick = { song ->
                                        playerViewModel.selectSongForInfo(song)
                                        showSongInfoBottomSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showSongInfoBottomSheet && selectedSongForInfo != null) {
            val song = selectedSongForInfo!!
            SongInfoBottomSheet(
                onStartSongRadio = { seedSong -> playerViewModel.startSongRadio(seedSong) },
                song = song,
                isFavorite = favoriteSongIds.contains(song.id),
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(song)
                },
                onDismiss = {
                    showSongInfoBottomSheet = false
                    showPlaylistBottomSheet = false
                },
                onPlaySong = {
                    if (queueSongs.isNotEmpty()) {
                        playerViewModel.playSongs(queueSongs, song, queueName)
                    }
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(song)
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(song)
                },
                // Hold Next → Soon (the "after which song" slider).
                soonPlayer = playerViewModel,
                onAddSoon = { k -> playerViewModel.addSongAfterUpcoming(song, k) },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    Screen.AlbumDetail.createRouteForSong(song)?.let { navController.navigateSafely(it) }
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafely(Screen.ArtistDetail.createRouteForSong(song))
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistById = { artistId ->
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(artistId))
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistByName = { artistName ->
                    navController.navigateSafely(Screen.ArtistDetail.createRouteForName(artistName))
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    song.genre?.let {
                        navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        song,
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
                removeFromListTrigger = {}
            )

            if (showPlaylistBottomSheet) {
                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = listOf(song),
                    onDismiss = { showPlaylistBottomSheet = false },
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }

    Trace.endSection()
}

private data class TimestampGroup(
    val key: String,
    val label: String,
    val isHourBucket: Boolean,
    val songs: List<RecentlyPlayedSongUiModel>
)

private fun groupRecentlyPlayedSongs(
    context: android.content.Context,
    songs: List<RecentlyPlayedSongUiModel>,
    range: StatsTimeRange
): List<TimestampGroup> {
    if (songs.isEmpty()) return emptyList()
    val zoneId = ZoneId.systemDefault()
    val sorted = songs.sortedByDescending { it.lastPlayedTimestamp }

    val groups = mutableListOf<TimestampGroup>()
    var currentBucketKey: String? = null
    var currentLabel: String? = null
    var currentIsHourBucket = false
    var bucket = mutableListOf<RecentlyPlayedSongUiModel>()

    sorted.forEach { item ->
        val timeBucket = resolveTimestampBucket(
            context = context,
            timestamp = item.lastPlayedTimestamp,
            range = range,
            zoneId = zoneId
        )
        if (currentBucketKey == null || currentBucketKey == timeBucket.key) {
            currentBucketKey = timeBucket.key
            currentLabel = timeBucket.label
            currentIsHourBucket = timeBucket.isHourBucket
            bucket += item
        } else {
            groups += TimestampGroup(
                key = currentBucketKey,
                label = currentLabel!!,
                isHourBucket = currentIsHourBucket,
                songs = bucket
            )
            currentBucketKey = timeBucket.key
            currentLabel = timeBucket.label
            currentIsHourBucket = timeBucket.isHourBucket
            bucket = mutableListOf(item)
        }
    }

    if (bucket.isNotEmpty() && currentLabel != null && currentBucketKey != null) {
        groups += TimestampGroup(
            key = currentBucketKey,
            label = currentLabel,
            isHourBucket = currentIsHourBucket,
            songs = bucket
        )
    }

    return groups
}

private data class TimestampBucket(
    val key: String,
    val label: String,
    val isHourBucket: Boolean
)

private fun resolveTimestampBucket(
    context: android.content.Context,
    timestamp: Long,
    range: StatsTimeRange,
    zoneId: ZoneId
): TimestampBucket {
    val safeTimestamp = timestamp.coerceAtLeast(0L)
    val safeNow = System.currentTimeMillis().coerceAtLeast(0L)
    val zonedDateTime = Instant.ofEpochMilli(safeTimestamp).atZone(zoneId)
    val date = zonedDateTime.toLocalDate()
    val nowDate = Instant.ofEpochMilli(safeNow).atZone(zoneId).toLocalDate()

    if (range == StatsTimeRange.DAY) {
        val hourStart = zonedDateTime
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        return TimestampBucket(
            key = hourStart.toInstant().toEpochMilli().toString(),
            label = hourStart.format(
                DateTimeFormatter.ofPattern(
                    if (AndroidDateFormat.is24HourFormat(context)) "HH:mm" else "h a",
                    Locale.getDefault()
                )
            ),
            isHourBucket = true
        )
    }

    return when {
        date == nowDate -> {
            TimestampBucket(
                key = date.toString(),
                label = context.getString(R.string.recently_played_date_today),
                isHourBucket = false
            )
        }
        date == nowDate.minusDays(1) -> {
            TimestampBucket(
                key = date.toString(),
                label = context.getString(R.string.recently_played_date_yesterday),
                isHourBucket = false
            )
        }
        range == StatsTimeRange.YEAR || range == StatsTimeRange.ALL -> {
            TimestampBucket(
                key = date.toString(),
                label = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())),
                isHourBucket = false
            )
        }
        else -> {
            TimestampBucket(
                key = date.toString(),
                label = date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())),
                isHourBucket = false
            )
        }
    }
}


/** Which list the history screen shows. */
private enum class HistoryMode(val label: String) { Recent("Recent"), Liked("Liked") }

/** Marks a like with no local like time (platform / favourites-playlist likes). */
private const val UNTIMED_LIKE = -1L

/** Short chip labels for the time ranges ("Week to Date" is too long for a chip). */
private fun rangeChipLabel(range: StatsTimeRange): String = when (range) {
    StatsTimeRange.DAY -> "Today"
    StatsTimeRange.WEEK -> "This week"
    StatsTimeRange.MONTH -> "This month"
    StatsTimeRange.YEAR -> "This year"
    StatsTimeRange.ALL -> "All time"
}

/**
 * Playlist-style hero, matching Your Music: tinted backdrop, cover (a 2×2 of the latest album art,
 * or a gradient tile with the mode icon), title, counts and the big round Shuffle button.
 */
@Composable
private fun HistoryHero(
    title: String,
    subtitle: String,
    isLiked: Boolean,
    artUris: List<String>,
    shuffleEnabled: Boolean,
    onShuffle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to colors.primaryContainer,
                    0.55f to colors.primaryContainer.copy(alpha = 0.35f),
                    1f to colors.surface
                )
            )
    ) {
        val coverSize = (maxWidth * 0.46f).coerceAtMost(220.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(coverSize)
                    .shadow(12.dp, MaterialTheme.shapes.large)
                    .clip(MaterialTheme.shapes.large)
                    .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary))),
                contentAlignment = Alignment.Center
            ) {
                if (artUris.size >= 4) {
                    Column(Modifier.fillMaxSize()) {
                        artUris.take(4).chunked(2).forEach { rowUris ->
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                rowUris.forEach { uri ->
                                    SmartImage(
                                        model = uri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.weight(1f).fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                    // Mode badge over the collage.
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.History,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Icon(
                        if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.History,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(coverSize * 0.45f)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansRounded),
                color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(16.dp))
            val spin = rememberShuffleSpin()
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (shuffleEnabled) colors.onSurface else colors.onSurface.copy(alpha = 0.3f))
                    .clickable(enabled = shuffleEnabled, onClick = { spin.play(); onShuffle() })
                    .semantics { contentDescription = "Shuffle $title" },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = colors.surface, modifier = Modifier.size(32.dp).shuffleSpin(spin))
            }
        }
    }
}

/** Recent · Liked, a divider, then the time ranges — Your Music's filter-chip row. */
@Composable
private fun HistoryFilterRow(
    mode: HistoryMode,
    showLiked: Boolean,
    range: StatsTimeRange,
    onModeChange: (HistoryMode) -> Unit,
    onRangeChange: (StatsTimeRange) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "mode_recent") {
            ExpressiveFilterChip(
                selected = mode == HistoryMode.Recent,
                onClick = { onModeChange(HistoryMode.Recent) },
                label = "Recent",
                leadingIcon = {
                    Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }
        if (showLiked) {
            item(key = "mode_liked") {
                val selected = mode == HistoryMode.Liked
                ExpressiveFilterChip(
                    selected = selected,
                    onClick = { onModeChange(HistoryMode.Liked) },
                    label = "Liked",
                    leadingIcon = {
                        Icon(
                            if (selected) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
        item(key = "divider") {
            VerticalDivider(
                Modifier
                    .height(24.dp)
                    .padding(horizontal = 2.dp)
            )
        }
        StatsTimeRange.entries.forEach { r ->
            item(key = "range_${r.name}") {
                ExpressiveFilterChip(
                    selected = r == range,
                    onClick = { onRangeChange(r) },
                    label = rangeChipLabel(r)
                )
            }
        }
    }
}

/** Date section header: "Today", "Yesterday", "Mon, Oct 5"… with the song count on the right. */
@Composable
private fun RecentlyPlayedTimestampDivider(
    label: String,
    count: Int,
    isHourBucket: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
            fontWeight = FontWeight.Bold,
            color = if (isHourBucket) colors.primary else colors.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (count == 1) "1 song" else "$count songs",
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = GoogleSansRounded),
            color = colors.onSurfaceVariant
        )
    }
}

/** A slim (40dp) pill search field, the same one Your Music uses in its header. */
@Composable
private fun HistorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
