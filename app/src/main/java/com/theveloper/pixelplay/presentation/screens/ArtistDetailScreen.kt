@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.components.LocalSongPrimaryTap
import com.theveloper.pixelplay.presentation.components.handle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.TrackVideo
import com.theveloper.pixelplay.data.repository.ArtistAlbumItem
import com.theveloper.pixelplay.data.repository.ArtistCatalog
import com.theveloper.pixelplay.data.repository.ArtistSongSort
import com.theveloper.pixelplay.data.repository.ArtistPlaylistItem
import com.theveloper.pixelplay.data.repository.FansFilterType
import com.theveloper.pixelplay.data.repository.RelatedArtistItem
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.ExpressiveScrollBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SmartImageCompactListTargetSize
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing
import com.theveloper.pixelplay.presentation.viewmodel.ArtistAlbumSection
import com.theveloper.pixelplay.presentation.screens.artist.*
import com.theveloper.pixelplay.presentation.viewmodel.ArtistDetailUiState
import com.theveloper.pixelplay.presentation.viewmodel.ArtistDetailViewModel
import com.theveloper.pixelplay.presentation.viewmodel.ArtistTrackItem
import com.theveloper.pixelplay.presentation.viewmodel.TrackOwnership
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import com.theveloper.pixelplay.ui.theme.LocalShowScrollbar
import com.theveloper.pixelplay.ui.theme.PixelPlayStatusBarStyle
import com.theveloper.pixelplay.utils.formatSongCount
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.roundToInt

private const val UseSharedCollapsibleTopBarProbe = true

/** Full-screen lists opened from a section title (A5). */
private enum class ArtistSeeAll(val title: String) {
    SONGS("All songs"),
    ALBUMS("Albums"),
    SINGLES("Singles and EPs"),
    APPEARS_ON("Appears on"),
    VIDEOS("Music videos"),
    TIMELINE("Timeline"),
    FANS("Fans also like")
}

/** Songs shown on the page before "Show all" (A1). */
private const val SONGS_PREVIEW = 10

/**
 * Most songs handed to the player per tap. Catalogue songs are matched on YouTube before the queue
 * starts, so a 1,000-song queue would take minutes; the next 25 from the tapped song keeps it quick.
 */
private const val QUEUE_WINDOW = 25

/** Releases shown inline in Timeline before "see all". */
private const val TIMELINE_PREVIEW = 6

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistDetailScreen(
    artistId: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: ArtistDetailViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    var isTransitionFinished by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        isTransitionFinished = true
    }

    val lazyListState = rememberLazyListState()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val isDarkTheme = LocalPixelPlayDarkTheme.current
    val baseColorScheme = MaterialTheme.colorScheme

    // Page state
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var seeAll by rememberSaveable { mutableStateOf<ArtistSeeAll?>(null) }
    var expandedVersions by remember { mutableStateOf(emptySet<String>()) }
    var showNotThisArtist by remember { mutableStateOf(false) }
    var showLikeAllDialog by remember { mutableStateOf(false) }

    val artistColorSchemePair by viewModel.artistColorScheme.collectAsStateWithLifecycle()
    val artistColorScheme = remember(artistColorSchemePair, isDarkTheme) {
        artistColorSchemePair?.let { pair -> if (isDarkTheme) pair.dark else pair.light }
            ?: baseColorScheme
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.setCustomImage(it) }
    }

    LaunchedEffect(Unit) {
        playerViewModel.collapsePlayerSheet()
    }

    BackHandler(enabled = seeAll != null) { seeAll = null }
    BackHandler(enabled = seeAll == null && searchOpen) {
        searchOpen = false
        viewModel.setSongQuery("")
    }

    // --- Header Collapse Logic ---
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 310.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
    val headerImageRequestSize = remember(
        configuration.screenWidthDp,
        density.density,
        maxTopBarHeightPx
    ) {
        Size(
            width = with(density) { configuration.screenWidthDp.dp.roundToPx() },
            height = maxTopBarHeightPx.roundToInt()
        )
    }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    val collapseFraction by remember(minTopBarHeightPx, maxTopBarHeightPx) {
        derivedStateOf {
            1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch {
                        topBarHeight.snapTo(newHeight)
                    }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return super.onPostFling(consumed, available)
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0

            val targetValue = if (shouldExpand && canExpand) {
                maxTopBarHeightPx
            } else {
                minTopBarHeightPx
            }

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    // --- Derived lists ---
    val allReleases = remember(uiState.popularReleases, uiState.singlesAndEPs) {
        (uiState.popularReleases + uiState.singlesAndEPs).sortedByDescending { it.releaseDate ?: it.releaseYear?.toString() ?: "" }
    }
    val ownedByRelease = remember(uiState.tracks, allReleases) {
        val owned = uiState.tracks.filter { it.ownership != TrackOwnership.NONE }
            .groupingBy { ArtistCatalog.fold(it.track.song.album) }.eachCount()
        allReleases.associate { it.id to (owned[ArtistCatalog.fold(it.title)] ?: 0) }
    }

    // --- Actions ---
    val songTap = LocalSongPrimaryTap.current
    val artistName = uiState.artist?.name.orEmpty()

    /** B5: the list you're looking at, from the tapped song on. */
    fun playFrom(item: ArtistTrackItem, list: List<ArtistTrackItem>, queueLabel: String) {
        val songs = list.map { it.playSong }
        val index = songs.indexOfFirst { it.id == item.playSong.id }.coerceAtLeast(0)
        val window = songs.drop(index).take(QUEUE_WINDOW).ifEmpty { listOf(item.playSong) }
        // Add Song from lyrics: opens the action sheet instead of playing.
        songTap.handle(item.playSong, window, queueLabel) {
            playerViewModel.showAndPlaySong(item.playSong, window, queueLabel)
        }
    }

    fun songQueueLabel(): String =
        if (uiState.songQuery.isNotBlank()) "$artistName · “${uiState.songQuery}”" else "$artistName · ${uiState.songSort.label}"

    fun openSongInfo(song: Song) {
        playerViewModel.selectSongForInfo(song)
        showSongInfoBottomSheet = true
    }

    fun toggleVersions(key: String) {
        expandedVersions = if (key in expandedVersions) expandedVersions - key else expandedVersions + key
    }

    fun openRelease(release: ArtistAlbumItem) {
        navController.navigateSafely(Screen.AlbumDetail.createRoute(release.collectionId))
    }

    fun playRelease(release: ArtistAlbumItem) {
        coroutineScope.launch {
            val songs = viewModel.releaseSongs(release)
            if (songs.isNotEmpty()) {
                playerViewModel.playSongs(songs.take(QUEUE_WINDOW), songs.first(), release.title)
            }
        }
    }

    fun playVideo(video: TrackVideo) {
        playerViewModel.showAndPlaySong(videoAsSong(video, artistName, uiState.artist?.id ?: 0L))
    }

    fun openGenre(genre: String) {
        navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(genre, "UTF-8")))
    }

    fun openArtist(related: RelatedArtistItem) {
        // Artist → artist must stack a new page: single-top would reuse this destination
        // (same route pattern) and the tap would appear to do nothing.
        navController.navigateSafely(Screen.ArtistDetail.createRouteForName(related.name)) { launchSingleTop = false }
    }

    /** Opens a "Fans also like" playlist as a playlist page (it isn't saved, so it's transient). */
    fun openArtistPlaylist(item: ArtistPlaylistItem) {
        if (item.songs.isEmpty()) return
        val playlist = com.theveloper.pixelplay.data.model.Playlist(
            id = "${PlaylistViewModel.GENERATED_MIX_PREFIX}${item.id}",
            name = item.title,
            songIds = item.songs.map { it.id },
            coverImageUri = item.coverArtUrl
        )
        PlaylistViewModel.registerTransientPlaylist(playlist, item.songs)
        navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
    }

    fun shuffleArtist() {
        val offline = uiState.completion?.offlineSongs.orEmpty()
        val top = uiState.tracks.sortedByDescending { it.track.popularity ?: 0 }.take(40).map { it.playSong }
        val pool = (offline + top).ifEmpty { uiState.songs }.distinctBy { it.id }
        if (pool.isNotEmpty()) {
            playerViewModel.playSongsShuffled(pool.shuffled().take(QUEUE_WINDOW), artistName, startAtZero = true)
        }
    }

    MaterialTheme(
        colorScheme = artistColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
                when {
                    uiState.isLoading && uiState.artist == null -> {
                        com.theveloper.pixelplay.presentation.components.DelayedSkeleton {
                            com.theveloper.pixelplay.presentation.components.DetailScreenSkeleton(
                                headerHeight = 320.dp,
                                showRowArt = true
                            )
                        }
                    }
                    uiState.error != null && uiState.artist == null -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    uiState.artist != null -> {
                        val artist = uiState.artist!!
                        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

                        val isScrollbarEnabled = LocalShowScrollbar.current
                        val showScrollBar by remember(isScrollbarEnabled) {
                            derivedStateOf {
                                isScrollbarEnabled &&
                                        collapseFraction > 0.95f &&
                                        (lazyListState.canScrollForward || lazyListState.canScrollBackward)
                            }
                        }

                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .offset {
                                    val extraHeight = (topBarHeight.value - minTopBarHeightPx).roundToInt()
                                    IntOffset(0, extraHeight)
                                },
                            contentPadding = PaddingValues(
                                top = minTopBarHeight + 8.dp,
                                start = 16.dp,
                                end = if (showScrollBar) 24.dp else 16.dp,
                                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 0. Real stats + catalogue status
                            item(key = "header_stats", contentType = "stats") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ArtistStatsRow(
                                        fanCount = uiState.fanCount,
                                        songCount = uiState.tracks.size,
                                        releaseCount = allReleases.size
                                    )
                                    CatalogueStatusLine(
                                        isAggregating = uiState.isAggregating,
                                        isOffline = uiState.isOffline,
                                        hasSavedCatalogue = uiState.hasSavedCatalogue,
                                        updatedAt = uiState.catalogueUpdatedAt,
                                        onRefresh = viewModel::refreshCatalogue
                                    )
                                }
                            }

                            // 1. Latest release
                            uiState.latestRelease?.let { release ->
                                item(key = "latest_release", contentType = "latest_release") {
                                    LatestReleaseCard(
                                        release = release,
                                        onOpen = { openRelease(release) },
                                        onPlay = { playRelease(release) }
                                    )
                                }
                            }

                            // 2. All songs: title + count, search icon (A2), sort chips (A3)
                            if (uiState.tracks.isNotEmpty() || uiState.isAggregating) {
                                item(key = "songs_header", contentType = "section_header") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ArtistSectionTitle(
                                            title = "All songs",
                                            count = uiState.tracks.size,
                                            onClick = { seeAll = ArtistSeeAll.SONGS },
                                            trailing = {
                                                IconButton(onClick = {
                                                    if (searchOpen) viewModel.setSongQuery("")
                                                    searchOpen = !searchOpen
                                                }) {
                                                    Icon(
                                                        imageVector = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                                                        contentDescription = if (searchOpen) "Close search" else "Search songs"
                                                    )
                                                }
                                            }
                                        )
                                        AnimatedVisibility(visible = searchOpen) {
                                            ArtistSongSearchField(
                                                query = uiState.songQuery,
                                                artistName = artist.name,
                                                songCount = uiState.tracks.size,
                                                isLoading = uiState.isAggregating,
                                                onQueryChange = viewModel::setSongQuery,
                                                onClose = {
                                                    viewModel.setSongQuery("")
                                                    searchOpen = false
                                                }
                                            )
                                        }
                                        SongSortChips(selected = uiState.songSort, onSelect = viewModel::setSongSort)
                                    }
                                }

                                val preview = uiState.visibleTracks.take(SONGS_PREVIEW)
                                when {
                                    uiState.tracks.isEmpty() -> {
                                        items(5, key = { "song_skeleton_$it" }, contentType = { "song_skeleton" }) {
                                            SongRowSkeleton()
                                        }
                                    }
                                    preview.isEmpty() && uiState.songQuery.isNotBlank() -> {
                                        item(key = "songs_no_match", contentType = "songs_no_match") {
                                            NoSongsMatch(query = uiState.songQuery, onClear = { viewModel.setSongQuery("") })
                                        }
                                    }
                                    else -> {
                                        items(preview, key = { "song_${it.key}" }, contentType = { "song_row" }) { item ->
                                            ArtistSongRow(
                                                item = item,
                                                isCurrentSong = stablePlayerState.currentSong?.id == item.playSong.id,
                                                versionsExpanded = item.key in expandedVersions,
                                                onClick = { playFrom(item, uiState.visibleTracks, songQueueLabel()) },
                                                onToggleVersions = { toggleVersions(item.key) },
                                                onVersionClick = { version -> songTap.handle(version, listOf(version), artist.name) { playerViewModel.showAndPlaySong(version, listOf(version), artist.name) } },
                                                onMoreClick = { openSongInfo(item.playSong) }
                                            )
                                        }
                                        if (uiState.visibleTracks.size > SONGS_PREVIEW) {
                                            item(key = "songs_show_all", contentType = "show_all") {
                                                FilledTonalButton(
                                                    onClick = { seeAll = ArtistSeeAll.SONGS },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        if (uiState.songQuery.isNotBlank()) "Show all ${uiState.visibleTracks.size} results"
                                                        else "Show all ${uiState.visibleTracks.size} songs"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Artist mix
                            if (uiState.tracks.isNotEmpty() || uiState.songs.isNotEmpty()) {
                                item(key = "artist_mix", contentType = "artist_mix") {
                                    ArtistMixCard(
                                        artistName = artist.name,
                                        enabled = true,
                                        onClick = {
                                            val seeds = viewModel.artistMixSeeds().ifEmpty { uiState.songs.shuffled().take(20) }
                                            if (seeds.isNotEmpty()) {
                                                playerViewModel.startContinuousMix(
                                                    com.theveloper.pixelplay.data.MixFlavor.NORMAL,
                                                    initial = seeds
                                                )
                                            }
                                        }
                                    )
                                }
                            }

                            // 4. You & Artist
                            uiState.listening?.let { listening ->
                                item(key = "you_and_artist_header", contentType = "section_header") {
                                    ArtistSectionTitle(title = "You & ${artist.name}")
                                }
                                item(key = "you_and_artist", contentType = "you_and_artist") {
                                    YouAndArtistCard(
                                        artistName = artist.name,
                                        listening = listening,
                                        onTopSongClick = { song -> songTap.handle(song, listOf(song), artist.name) { playerViewModel.showAndPlaySong(song, listOf(song), artist.name) } }
                                    )
                                }
                            }

                            // 5. Discography completion
                            uiState.completion?.let { completion ->
                                item(key = "completion", contentType = "completion") {
                                    DiscographyCompletionCard(
                                        completion = completion,
                                        onDownloadLiked = { viewModel.downloadLikedSongs() },
                                        onLikeAll = { showLikeAllDialog = true },
                                        onShuffleDownloaded = {
                                            playerViewModel.playSongsShuffled(
                                                completion.offlineSongs,
                                                "${artist.name} · on this device",
                                                startAtZero = true
                                            )
                                        }
                                    )
                                }
                            }

                            // 6. Albums
                            if (uiState.popularReleases.isNotEmpty()) {
                                item(key = "albums_header", contentType = "section_header") {
                                    ArtistSectionTitle(
                                        title = "Albums",
                                        count = uiState.popularReleases.size,
                                        onClick = { seeAll = ArtistSeeAll.ALBUMS }
                                    )
                                }
                                item(key = "albums_carousel", contentType = "carousel") {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        items(uiState.popularReleases, key = { it.id }) { album ->
                                            ArtistSingleCard(item = album, onClick = { openRelease(album) })
                                        }
                                    }
                                }
                            }

                            // 7. Singles and EPs
                            if (uiState.singlesAndEPs.isNotEmpty()) {
                                item(key = "singles_eps_header", contentType = "section_header") {
                                    ArtistSectionTitle(
                                        title = "Singles and EPs",
                                        count = uiState.singlesAndEPs.size,
                                        onClick = { seeAll = ArtistSeeAll.SINGLES }
                                    )
                                }
                                item(key = "singles_eps_carousel", contentType = "carousel") {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(uiState.singlesAndEPs, key = { it.id }) { single ->
                                            ArtistSingleCard(item = single, onClick = { openRelease(single) })
                                        }
                                    }
                                }
                            }

                            // 8. Appears on
                            if (uiState.appearsOn.isNotEmpty()) {
                                item(key = "appears_on_header", contentType = "section_header") {
                                    ArtistSectionTitle(
                                        title = "Appears on",
                                        count = uiState.appearsOn.size,
                                        onClick = { seeAll = ArtistSeeAll.APPEARS_ON }
                                    )
                                }
                                item(key = "appears_on_carousel", contentType = "carousel") {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(uiState.appearsOn.take(15), key = { it.key }) { item ->
                                            RelatedSongCard(
                                                song = item.playSong,
                                                onClick = { playFrom(item, uiState.appearsOn, "${artist.name} · appears on") }
                                            )
                                        }
                                    }
                                }
                            }

                            // 9. Music videos
                            if (uiState.videos.isNotEmpty()) {
                                item(key = "videos_header", contentType = "section_header") {
                                    ArtistSectionTitle(
                                        title = "Music videos",
                                        count = uiState.videos.size,
                                        onClick = { seeAll = ArtistSeeAll.VIDEOS }
                                    )
                                }
                                item(key = "videos_carousel", contentType = "carousel") {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(uiState.videos, key = { it.id }) { video ->
                                            ArtistVideoCard(video = video, onClick = { playVideo(video) })
                                        }
                                    }
                                }
                            }

                            // 10. Timeline
                            if (allReleases.size > 1) {
                                item(key = "timeline_header", contentType = "section_header") {
                                    ArtistSectionTitle(
                                        title = "Timeline",
                                        count = allReleases.size,
                                        onClick = { seeAll = ArtistSeeAll.TIMELINE }
                                    )
                                }
                                item(key = "timeline_preview", contentType = "timeline") {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        TimelineContent(
                                            releases = allReleases.take(TIMELINE_PREVIEW),
                                            ownedByRelease = ownedByRelease,
                                            onOpen = ::openRelease
                                        )
                                    }
                                }
                            }

                            // 11. Genres & moods
                            if (uiState.genres.isNotEmpty()) {
                                item(key = "genres_header", contentType = "section_header") {
                                    ArtistSectionTitle(title = "Genres & moods")
                                }
                                item(key = "genres", contentType = "genres") {
                                    GenreChips(genres = uiState.genres, onClick = ::openGenre)
                                }
                            }

                            // 12. Fans also like (A6: Artists · Songs · Playlists, one always selected)
                            if (uiState.fansAlsoLikeArtists.isNotEmpty() || uiState.fansAlsoLikeSongs.isNotEmpty() || uiState.fansAlsoLikePlaylists.isNotEmpty()) {
                                item(key = "fans_also_like_header", contentType = "section_header") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ArtistSectionTitle(
                                            title = "Fans also like",
                                            count = uiState.fansAlsoLikeArtists.size.takeIf { it > 0 },
                                            onClick = { seeAll = ArtistSeeAll.FANS }
                                        )
                                        FansFilterChips(selected = uiState.activeFansFilter, onSelect = viewModel::setFansFilter)
                                    }
                                }

                                item(key = "fans_carousel_${uiState.activeFansFilter}", contentType = "carousel") {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        contentPadding = PaddingValues(horizontal = 2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        when (uiState.activeFansFilter) {
                                            FansFilterType.ARTISTS -> items(uiState.fansAlsoLikeArtists, key = { it.id }) { related ->
                                                RelatedArtistCard(item = related, onClick = { openArtist(related) })
                                            }
                                            FansFilterType.SONGS -> items(uiState.fansAlsoLikeSongs, key = { it.id }) { song ->
                                                RelatedSongCard(
                                                    song = song,
                                                    onClick = {
                                                        songTap.handle(song, uiState.fansAlsoLikeSongs, "Fans also like") {
                                                            playerViewModel.showAndPlaySong(song, uiState.fansAlsoLikeSongs, "Fans also like")
                                                        }
                                                    }
                                                )
                                            }
                                            FansFilterType.PLAYLISTS -> items(uiState.fansAlsoLikePlaylists, key = { it.id }) { playlistItem ->
                                                ArtistPlaylistCard(item = playlistItem, onClick = { openArtistPlaylist(playlistItem) })
                                            }
                                        }
                                    }
                                }
                            }

                            // 13. About
                            uiState.bio?.let { bio ->
                                item(key = "about_header", contentType = "section_header") {
                                    ArtistSectionTitle(title = "About")
                                }
                                item(key = "about", contentType = "about") {
                                    AboutCard(bio = bio)
                                }
                            }
                        }

                        if (showScrollBar) {
                            ExpressiveScrollBar(
                                listState = lazyListState,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(
                                        top = minTopBarHeight + 12.dp,
                                        bottom = MiniPlayerHeight + systemNavBarInset + 8.dp
                                    )
                            )
                        }

                        val songsCount = uiState.tracks.size.takeIf { it > 0 } ?: uiState.songs.size
                        val onNotThisArtist: (() -> Unit)? =
                            if (uiState.deezerCandidates.size > 1) ({ showNotThisArtist = true }) else null
                        if (UseSharedCollapsibleTopBarProbe) {
                            SharedArtistTopBarProbe(
                                artist = artist,
                                effectiveImageUrl = uiState.effectiveImageUrl,
                                songsCount = songsCount,
                                collapseFraction = collapseFraction,
                                headerHeight = currentTopBarHeightDp,
                                headerImageRequestSize = headerImageRequestSize,
                                hasCustomImage = !artist.customImageUri.isNullOrBlank(),
                                fanCount = uiState.fanCount,
                                onNotThisArtist = onNotThisArtist,
                                onBackPressed = { navController.popBackStack() },
                                onPlayClick = ::shuffleArtist,
                                onChangeImage = { imagePickerLauncher.launch("image/*") },
                                onClearCustomImage = { viewModel.clearCustomImage() }
                            )
                        } else {
                            CustomCollapsingTopBar(
                                artist = artist,
                                effectiveImageUrl = uiState.effectiveImageUrl,
                                hasCustomImage = !artist.customImageUri.isNullOrBlank(),
                                songsCount = songsCount,
                                collapseFraction = collapseFraction,
                                headerHeight = currentTopBarHeightDp,
                                headerImageRequestSize = headerImageRequestSize,
                                onBackPressed = { navController.popBackStack() },
                                onPlayClick = ::shuffleArtist,
                                onChangeImage = { imagePickerLauncher.launch("image/*") },
                                onClearCustomImage = { viewModel.clearCustomImage() }
                            )
                        }

                        // "See all" pages, drawn over the whole screen.
                        AnimatedVisibility(
                            visible = seeAll != null,
                            enter = slideInHorizontally { it } + fadeIn(),
                            exit = slideOutHorizontally { it } + fadeOut()
                        ) {
                            // Keep the last target while the exit animation runs.
                            var lastTarget by remember { mutableStateOf(ArtistSeeAll.SONGS) }
                            LaunchedEffect(seeAll) { seeAll?.let { lastTarget = it } }
                            ArtistSeeAllPage(
                                target = seeAll ?: lastTarget,
                                artistName = artist.name,
                                uiState = uiState,
                                allReleases = allReleases,
                                ownedByRelease = ownedByRelease,
                                currentSongId = stablePlayerState.currentSong?.id,
                                expandedVersions = expandedVersions,
                                bottomPadding = MiniPlayerHeight + systemNavBarInset + 16.dp,
                                onBack = { seeAll = null },
                                onQueryChange = viewModel::setSongQuery,
                                onSortChange = viewModel::setSongSort,
                                onFansFilterChange = viewModel::setFansFilter,
                                onSongClick = { item, list -> playFrom(item, list, songQueueLabel()) },
                                onToggleVersions = ::toggleVersions,
                                onVersionClick = { version -> songTap.handle(version, listOf(version), artist.name) { playerViewModel.showAndPlaySong(version, listOf(version), artist.name) } },
                                onSongMore = ::openSongInfo,
                                onReleaseClick = ::openRelease,
                                onVideoClick = ::playVideo,
                                onArtistClick = ::openArtist,
                                onPlaylistClick = ::openArtistPlaylist,
                                onPlaySongs = { song, list, label -> playerViewModel.showAndPlaySong(song, list.take(QUEUE_WINDOW), label) }
                            )
                        }
                    }
                }
            }
        } // End Surface

        if (showNotThisArtist) {
            NotThisArtistDialog(
                candidates = uiState.deezerCandidates,
                currentId = uiState.deezerArtistId,
                onPick = { candidate ->
                    showNotThisArtist = false
                    viewModel.chooseDeezerArtist(candidate)
                },
                onDismiss = { showNotThisArtist = false }
            )
        }

        if (showLikeAllDialog) {
            val toLike = uiState.completion?.notOwned.orEmpty()
            LikeAllDialog(
                count = toLike.size,
                artistName = artistName,
                onConfirm = {
                    showLikeAllDialog = false
                    toLike.filterNot { it.id in favoriteIds }.forEach { playerViewModel.toggleFavoriteSpecificSong(it) }
                },
                onDismiss = { showLikeAllDialog = false }
            )
        }

        // Song Info Bottom Sheet for track options
        if (showSongInfoBottomSheet && selectedSongForInfo != null) {
            val currentSong = selectedSongForInfo
            val isFavorite = remember(currentSong?.id, favoriteIds) {
                currentSong?.let { favoriteIds.contains(it.id) } ?: false
            }

            if (currentSong != null) {
                SongInfoBottomSheet(
                    song = currentSong,
                    isFavorite = isFavorite,
                    onToggleFavorite = {
                        playerViewModel.toggleFavoriteSpecificSong(currentSong)
                    },
                    onDismiss = { showSongInfoBottomSheet = false },
                    onPlaySong = {
                        playerViewModel.showAndPlaySong(currentSong)
                    },
                    onAddToQueue = {
                        playerViewModel.addSongToQueue(currentSong)
                    },
                    onAddNextToQueue = {
                        playerViewModel.addSongNextToQueue(currentSong)
                    },
                    onAddToPlayList = {
                        showPlaylistBottomSheet = true
                    },
                    onDeleteFromDevice = playerViewModel::deleteFromDevice,
                    onNavigateToAlbum = {
                        navController.navigateSafelyReplacing(
                            route = Screen.AlbumDetail.createRoute(currentSong.albumId),
                            patternToPop = Screen.AlbumDetail.route
                        )
                        showSongInfoBottomSheet = false
                    },
                    onNavigateToArtist = {
                        navController.navigateSafelyReplacing(
                            route = Screen.ArtistDetail.createRouteForSong(currentSong),
                            patternToPop = Screen.ArtistDetail.route
                        )
                        showSongInfoBottomSheet = false
                    },
                    onNavigateToArtistById = { id ->
                        navController.navigateSafelyReplacing(
                            route = Screen.ArtistDetail.createRouteForSongArtist(currentSong, id),
                            patternToPop = Screen.ArtistDetail.route
                        )
                        showSongInfoBottomSheet = false
                    },
                    onNavigateToArtistByName = { name ->
                        navController.navigateSafelyReplacing(
                            route = Screen.ArtistDetail.createRouteForName(name),
                            patternToPop = Screen.ArtistDetail.route
                        )
                        showSongInfoBottomSheet = false
                    },
                    onNavigateToGenre = {
                        currentSong.genre?.let {
                            navController.navigateSafelyReplacing(
                                route = Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")),
                                patternToPop = Screen.GenreDetail.route
                            )
                        }
                        showSongInfoBottomSheet = false
                    },
                    onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                        playerViewModel.editSongMetadata(
                            currentSong,
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
                    removeFromListTrigger = { viewModel.removeSongFromAlbumSection(currentSong.id) }
                )

                if (showPlaylistBottomSheet) {
                    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

                    PlaylistBottomSheet(
                        playlistUiState = playlistUiState,
                        songs = listOf(currentSong),
                        onDismiss = { showPlaylistBottomSheet = false },
                        bottomBarHeight = bottomBarHeightDp,
                        playerViewModel = playerViewModel,
                    )
                }
            }
        }
    } // End MaterialTheme
}

@Composable
private fun FansFilterChips(selected: FansFilterType, onSelect: (FansFilterType) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            FansFilterType.ARTISTS to "Artists",
            FansFilterType.SONGS to "Songs",
            FansFilterType.PLAYLISTS to "Playlists"
        ).forEach { (filter, label) ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(label) },
                leadingIcon = if (selected == filter) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

@Composable
private fun TimelineContent(
    releases: List<ArtistAlbumItem>,
    ownedByRelease: Map<String, Int>,
    onOpen: (ArtistAlbumItem) -> Unit
) {
    releases.groupBy { release -> release.releaseYear?.let { "${it / 10 * 10}s" } ?: "Undated" }
        .forEach { (decade, inDecade) ->
            TimelineDecadeHeader(decade = decade, count = inDecade.size)
            inDecade.forEach { release ->
                TimelineRow(release = release, ownedCount = ownedByRelease[release.id] ?: 0, onClick = { onOpen(release) })
            }
        }
}

/** A music video played as audio in the normal player (its Video mode can show the picture). */
private fun videoAsSong(video: TrackVideo, artistName: String, artistId: Long): Song = Song(
    id = "yt_${video.id}",
    title = cleanVideoTitle(video.title),
    artist = artistName,
    artistId = artistId,
    album = "Music videos",
    albumId = 0L,
    path = "",
    contentUriString = "youtube://${video.id}",
    albumArtUriString = video.thumbnailUrl,
    duration = 0L,
    youtubeId = video.id,
    explicitSource = com.theveloper.pixelplay.data.model.TrackSource.YOUTUBE_MUSIC
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistSeeAllPage(
    target: ArtistSeeAll,
    artistName: String,
    uiState: ArtistDetailUiState,
    allReleases: List<ArtistAlbumItem>,
    ownedByRelease: Map<String, Int>,
    currentSongId: String?,
    expandedVersions: Set<String>,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (ArtistSongSort) -> Unit,
    onFansFilterChange: (FansFilterType) -> Unit,
    onSongClick: (ArtistTrackItem, List<ArtistTrackItem>) -> Unit,
    onToggleVersions: (String) -> Unit,
    onVersionClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    onReleaseClick: (ArtistAlbumItem) -> Unit,
    onVideoClick: (TrackVideo) -> Unit,
    onArtistClick: (RelatedArtistItem) -> Unit,
    onPlaylistClick: (ArtistPlaylistItem) -> Unit,
    onPlaySongs: (Song, List<Song>, String) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        target.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Text(artistName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding)
            when (target) {
                ArtistSeeAll.SONGS -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ArtistSongSearchField(
                            query = uiState.songQuery,
                            artistName = artistName,
                            songCount = uiState.tracks.size,
                            isLoading = uiState.isAggregating,
                            onQueryChange = onQueryChange,
                            onClose = { onQueryChange("") },
                            autoFocus = false
                        )
                        SongSortChips(selected = uiState.songSort, onSelect = onSortChange)
                    }
                    LazyColumn(
                        contentPadding = listPadding,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.visibleTracks.isEmpty() && uiState.songQuery.isNotBlank()) {
                            item(key = "no_match") { NoSongsMatch(query = uiState.songQuery, onClear = { onQueryChange("") }) }
                        }
                        items(uiState.visibleTracks, key = { it.key }, contentType = { "song_row" }) { item ->
                            ArtistSongRow(
                                item = item,
                                isCurrentSong = currentSongId == item.playSong.id,
                                versionsExpanded = item.key in expandedVersions,
                                onClick = { onSongClick(item, uiState.visibleTracks) },
                                onToggleVersions = { onToggleVersions(item.key) },
                                onVersionClick = onVersionClick,
                                onMoreClick = { onSongMore(item.playSong) }
                            )
                        }
                    }
                }
                ArtistSeeAll.ALBUMS, ArtistSeeAll.SINGLES -> {
                    val releases = if (target == ArtistSeeAll.ALBUMS) uiState.popularReleases else uiState.singlesAndEPs
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = listPadding,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        gridItems(releases, key = { it.id }) { release ->
                            ReleaseGridCard(item = release, ownedCount = ownedByRelease[release.id] ?: 0, onClick = { onReleaseClick(release) })
                        }
                    }
                }
                ArtistSeeAll.APPEARS_ON -> {
                    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(uiState.appearsOn, key = { it.key }) { item ->
                            ArtistSongRow(
                                item = item,
                                isCurrentSong = currentSongId == item.playSong.id,
                                versionsExpanded = false,
                                onClick = { onSongClick(item, uiState.appearsOn) },
                                onToggleVersions = {},
                                onVersionClick = onVersionClick,
                                onMoreClick = { onSongMore(item.playSong) }
                            )
                        }
                    }
                }
                ArtistSeeAll.VIDEOS -> {
                    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                        items(uiState.videos, key = { it.id }) { video ->
                            ArtistVideoCard(video = video, onClick = { onVideoClick(video) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                ArtistSeeAll.TIMELINE -> {
                    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxSize()) {
                        item(key = "timeline") {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                TimelineContent(releases = allReleases, ownedByRelease = ownedByRelease, onOpen = onReleaseClick)
                            }
                        }
                    }
                }
                ArtistSeeAll.FANS -> {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        FansFilterChips(selected = uiState.activeFansFilter, onSelect = onFansFilterChange)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = listPadding,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (uiState.activeFansFilter) {
                            FansFilterType.ARTISTS -> gridItems(uiState.fansAlsoLikeArtists, key = { it.id }) { related ->
                                RelatedArtistCard(item = related, onClick = { onArtistClick(related) })
                            }
                            FansFilterType.SONGS -> gridItems(uiState.fansAlsoLikeSongs, key = { it.id }) { song ->
                                RelatedSongCard(song = song, onClick = { onPlaySongs(song, uiState.fansAlsoLikeSongs, "Fans also like") })
                            }
                            FansFilterType.PLAYLISTS -> gridItems(uiState.fansAlsoLikePlaylists, key = { it.id }) { playlist ->
                                ArtistPlaylistCard(item = playlist, onClick = { onPlaylistClick(playlist) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseGridCard(item: ArtistAlbumItem, ownedCount: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(8.dp)
    ) {
        SmartImage(
            model = item.coverArtUrl,
            contentDescription = item.title,
            targetSize = SmartImageCompactListTargetSize,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = listOfNotNull(item.releaseYear?.toString(), if (item.isSingleOrEp) "Single / EP" else "Album").joinToString(" • "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        if (item.trackCount > 0) {
            Text(
                text = if (ownedCount > 0) "You have $ownedCount of ${item.trackCount}" else "${item.trackCount} tracks",
                style = MaterialTheme.typography.labelSmall,
                color = if (ownedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Components for the New Redesigned Layout
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ArtistSingleCard(
    item: ArtistAlbumItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(8.dp)
    ) {
        SmartImage(
            model = item.coverArtUrl,
            contentDescription = item.title,
            targetSize = SmartImageCompactListTargetSize,
            modifier = Modifier
                .size(156.dp)
                .clip(RoundedCornerShape(16.dp))
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = listOfNotNull(item.releaseYear?.toString(), if (item.isSingleOrEp) "Single / EP" else "Album").joinToString(" \u2022 "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(text = "${item.trackCount} tracks", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ArtistPlaylistCard(
    item: ArtistPlaylistItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp)
    ) {
        SmartImage(
            model = item.coverArtUrl,
            contentDescription = item.title,
            targetSize = SmartImageCompactListTargetSize,
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(16.dp))
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RelatedArtistCard(
    item: RelatedArtistItem,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        SmartImage(
            model = item.imageUrl,
            contentDescription = item.name,
            targetSize = SmartImageCompactListTargetSize,
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (item.fanCount > 0) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${formatMetricCompact(item.fanCount)} fans",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RelatedSongCard(
    song: Song,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp)
    ) {
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = song.title,
            targetSize = SmartImageCompactListTargetSize,
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(16.dp))
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = song.artist,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SharedArtistTopBarProbe(
    artist: Artist,
    effectiveImageUrl: String?,
    songsCount: Int,
    collapseFraction: Float,
    headerHeight: Dp,
    headerImageRequestSize: Size,
    hasCustomImage: Boolean,
    fanCount: Long?,
    onNotThisArtist: (() -> Unit)?,
    onBackPressed: () -> Unit,
    onPlayClick: () -> Unit,
    onChangeImage: () -> Unit,
    onClearCustomImage: () -> Unit
) {
    var showImageMenu by remember { mutableStateOf(false) }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val statusBarColor =
        if (LocalPixelPlayDarkTheme.current) Color.Black.copy(alpha = 0.6f)
        else Color.White.copy(alpha = 0.4f)
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val expandedContentAlpha = 1f - solidAlpha
    val displayUrl = effectiveImageUrl?.takeIf { it.isNotBlank() }
    val headerOverlayBrush = remember(surfaceColor, expandedContentAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                surfaceColor.copy(alpha = 0.24f * expandedContentAlpha),
                surfaceColor.copy(alpha = 0.84f * expandedContentAlpha),
                surfaceColor
            )
        )
    }
    val statusBarBrush = remember(statusBarColor) {
        Brush.verticalGradient(colors = listOf(statusBarColor, Color.Transparent))
    }
    val expandedStatusBarFallback = remember(statusBarColor, surfaceColor) {
        statusBarColor.compositeOver(surfaceColor)
    }
    val fallbackStatusBarColor = remember(expandedStatusBarFallback, surfaceColor, solidAlpha) {
        lerpColor(expandedStatusBarFallback, surfaceColor, solidAlpha)
    }
    val titleVerticalBias = lerp(1f, -1f, collapseFraction)
    val shuffleAlignment = BiasAlignment(horizontalBias = 1f, verticalBias = titleVerticalBias)

    PixelPlayStatusBarStyle(color = fallbackStatusBarColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clipToBounds()
    ) {
        if (expandedContentAlpha > 0.01f) {
            if (!displayUrl.isNullOrEmpty()) {
                SmartImage(
                    model = displayUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    targetSize = headerImageRequestSize,
                    allowHardware = true,
                    crossfadeDurationMillis = 0,
                    alpha = expandedContentAlpha,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                MusicIconPattern(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = expandedContentAlpha }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(headerOverlayBrush)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(statusBarBrush)
                .align(Alignment.TopCenter)
        )

        CollapsibleCommonTopBar(
            title = artist.name,
            subtitle = fanCount?.let { "${formatMetricCompact(it)} fans • ${formatSongCount(songsCount)}" }
                ?: formatSongCount(songsCount),
            collapseFraction = collapseFraction,
            headerHeight = headerHeight,
            onBackClick = onBackPressed,
            containerColor = surfaceColor.copy(alpha = solidAlpha),
            collapsedTitleStartPadding = 68.dp,
            expandedTitleStartPadding = 24.dp,
            collapsedTitleEndPadding = 88.dp,
            expandedTitleEndPadding = 136.dp,
            containerHeightRange = 112.dp to 56.dp,
            titleStyle = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                textGeometricTransform = TextGeometricTransform(scaleX = 1.08f)
            ),
            titleScaleRange = 1f to 1f,
            titleFontSizeRange = 30.sp to 18.sp,
            maxLines = if (collapseFraction < 0.5f) 2 else 1,
            collapsedSubtitleMaxLines = 1,
            expandedSubtitleMaxLines = 2,
            contentColor = MaterialTheme.colorScheme.onSurface,
            subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
            fadeSubtitleOnCollapse = false,
            syncStatusBarWithContainer = false,
            actions = {
                Box(
                    modifier = Modifier.padding(end = 12.dp, top = 4.dp)
                ) {
                    FilledIconButton(
                        onClick = { showImageMenu = true },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.artist_cd_edit_image)
                        )
                    }

                    DropdownMenu(
                        expanded = showImageMenu,
                        onDismissRequest = { showImageMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.artist_action_change_photo)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                            },
                            onClick = {
                                showImageMenu = false
                                onChangeImage()
                            }
                        )
                        if (hasCustomImage) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.artist_action_reset_to_default)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Delete, contentDescription = null)
                                },
                                onClick = {
                                    showImageMenu = false
                                    onClearCustomImage()
                                }
                            )
                        }
                        if (onNotThisArtist != null) {
                            DropdownMenuItem(
                                text = { Text("Not this artist?") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.PersonSearch, contentDescription = null)
                                },
                                onClick = {
                                    showImageMenu = false
                                    onNotThisArtist()
                                }
                            )
                        }
                    }
                }
            }
        )

        LargeExtendedFloatingActionButton(
            onClick = onPlayClick,
            shape = RoundedStarShape(sides = 8, curve = 0.05, rotation = 0f),
            modifier = Modifier
                .align(shuffleAlignment)
                .statusBarsPadding()
                .padding(end = 16.dp)
                .graphicsLayer {
                    scaleX = expandedContentAlpha
                    scaleY = expandedContentAlpha
                    alpha = expandedContentAlpha
                }
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.artist_cd_shuffle_play))
        }
    }
}

@Composable
private fun CustomCollapsingTopBar(
    artist: Artist,
    effectiveImageUrl: String?,
    hasCustomImage: Boolean,
    songsCount: Int,
    collapseFraction: Float,
    headerHeight: Dp,
    headerImageRequestSize: Size,
    onBackPressed: () -> Unit,
    onPlayClick: () -> Unit,
    onChangeImage: () -> Unit,
    onClearCustomImage: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val statusBarColor =
        if (LocalPixelPlayDarkTheme.current) Color.Black.copy(alpha = 0.6f)
        else Color.White.copy(alpha = 0.4f)
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val expandedContentAlpha = 1f - solidAlpha
    val displayUrl = effectiveImageUrl?.takeIf { it.isNotBlank() }
    val headerOverlayBrush = remember(surfaceColor, expandedContentAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                surfaceColor.copy(alpha = 0.24f * expandedContentAlpha),
                surfaceColor.copy(alpha = 0.84f * expandedContentAlpha),
                surfaceColor
            )
        )
    }
    val statusBarBrush = remember(statusBarColor) {
        Brush.verticalGradient(colors = listOf(statusBarColor, Color.Transparent))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clipToBounds()
    ) {
        if (expandedContentAlpha > 0.01f) {
            if (!displayUrl.isNullOrEmpty()) {
                SmartImage(
                    model = displayUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    targetSize = headerImageRequestSize,
                    allowHardware = true,
                    crossfadeDurationMillis = 0,
                    alpha = expandedContentAlpha,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                MusicIconPattern(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = expandedContentAlpha }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(headerOverlayBrush)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(statusBarBrush)
                .align(Alignment.TopCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onBackPressed,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    }
}

@Composable
private fun MusicIconPattern(modifier: Modifier = Modifier) {
    val color1 = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
    val color2 = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

    Box(modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null, tint = color1,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 76.dp, y = 72.dp)
                .size(60.dp)
                .graphicsLayer { rotationZ = -12f }
        )
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null, tint = color1,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 4.dp, y = 78.dp)
                .size(50.dp)
                .graphicsLayer { rotationZ = 18f }
        )
        Icon(
            imageVector = Icons.Rounded.Album,
            contentDescription = null, tint = color2,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 4.dp, y = (-70).dp)
                .size(70.dp)
                .graphicsLayer { rotationZ = 12f }
        )
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null, tint = color1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = 14.dp, y = (-6).dp)
                .size(60.dp)
                .graphicsLayer { rotationZ = 14f }
        )
        Icon(
            imageVector = Icons.Rounded.SurroundSound,
            contentDescription = null, tint = color2,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = (-24).dp, y = 32.dp)
                .size(80.dp)
                .graphicsLayer { rotationZ = 10f }
        )
        Icon(
            imageVector = Icons.Rounded.Headphones,
            contentDescription = null, tint = color2,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 72.dp, y = 34.dp)
                .size(45.dp)
                .graphicsLayer { rotationZ = -8f }
        )
    }
}
