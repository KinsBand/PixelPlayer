@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.zIndex
import com.theveloper.pixelplay.presentation.components.subcomps.rememberShuffleSpin
import com.theveloper.pixelplay.presentation.components.subcomps.shuffleSpin
import com.theveloper.pixelplay.presentation.components.offlineVeil
import com.theveloper.pixelplay.presentation.components.ExpressiveFilterChip
import com.theveloper.pixelplay.presentation.navigation.popBackStackSafely
import com.theveloper.pixelplay.presentation.components.LocalSongPrimaryTap
import com.theveloper.pixelplay.presentation.components.handle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
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
import com.theveloper.pixelplay.presentation.components.songStartFeedback
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
import com.theveloper.pixelplay.presentation.navigation.navigateToTopLevelSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing
import com.theveloper.pixelplay.presentation.viewmodel.ArtistAlbumSection
import com.theveloper.pixelplay.presentation.screens.artist.*
import com.theveloper.pixelplay.presentation.viewmodel.ArtistDetailUiState
import com.theveloper.pixelplay.presentation.viewmodel.ArtistDetailViewModel
import androidx.compose.material.icons.rounded.Shuffle
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.presentation.viewmodel.ArtistSection
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
    LIVE("Live performances"),
    TIMELINE("Timeline"),
    FANS("Fans also like"),
    SIMILAR("Similar")
}

/** Songs shown on the page before "See more" (the 6th fades out to show there's more). */
private const val SONGS_PREVIEW = 6

/** Songs shown after "See more"; "Show all" then opens the full list. */
private const val SONGS_EXPANDED = 10

/** List key of the in-page tab chips (the docked copy appears once it scrolls under the top bar). */
private const val ARTIST_TABS_KEY = "artist_tabs"

/**
 * Fades the bottom of a row to transparent, so the last song of a cut-off list reads as "there's
 * more". Drawn in the draw phase only (offscreen layer + DstIn gradient).
 */
private fun Modifier.truncationFade(): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.Black,
                0.25f to Color.Black,
                1f to Color.Transparent
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
        )
    }

/** Which service a playlist on the artist page comes from: its logo, top-right on the cover. */
@Composable
private fun BoxScope.ArtistPlaylistSourceBadge(source: String) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        com.theveloper.pixelplay.presentation.components.SourceBadge(source, iconSize = 12.dp, containerSize = 22.dp)
    }
}

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
    var seeAll by rememberSaveable { mutableStateOf<ArtistSeeAll?>(null) }
    /** All songs was opened from its search icon: the page starts with the field open. */
    var seeAllSearchOpen by rememberSaveable { mutableStateOf(false) }
    var expandedVersions by remember { mutableStateOf(emptySet<String>()) }
    var showNotThisArtist by remember { mutableStateOf(false) }
    var showLikeAllDialog by remember { mutableStateOf(false) }
    var showLikeAlbums by rememberSaveable { mutableStateOf(false) }
    // Like albums: releases whose songs are loading, and ones liked this visit.
    var likeBusyIds by remember { mutableStateOf(emptySet<String>()) }
    var likedReleaseIds by remember { mutableStateOf(emptySet<String>()) }
    var showMixAlbums by rememberSaveable { mutableStateOf(false) }
    // Tabs (Music · Events · Merch) and the Events tab's own state.
    var selectedTab by rememberSaveable { mutableStateOf(ArtistTab.MUSIC) }
    var eventsView by rememberSaveable { mutableStateOf(EventsView.LIST) }
    var eventsCountry by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedShowId by rememberSaveable { mutableStateOf<String?>(null) }
    var tourSheetOpen by rememberSaveable { mutableStateOf(false) }
    // Spotify playlists whose songs are loading (tap / play).
    var spotifyBusyIds by remember { mutableStateOf(emptySet<String>()) }
    // All songs: 6 rows (6th fading) → "See more" shows 10 → "Show all" opens the full list.
    var songsExpanded by rememberSaveable { mutableStateOf(false) }
    // YouTube Music playlists being opened (their songs load first).
    var ytBusyIds by remember { mutableStateOf(emptySet<String>()) }
    val ytPlaylistLoader: com.theveloper.pixelplay.presentation.viewmodel.SearchBrowseViewModel = hiltViewModel()
    val context = LocalContext.current
    val similarLabel = stringResource(R.string.artist_similar_title)
    val playlistOpenFailed = stringResource(R.string.artist_playlist_open_failed)

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

    /** Leaving a see-all page; All songs also drops its search so the page preview isn't left filtered. */
    fun closeSeeAll() {
        if (seeAll == ArtistSeeAll.SONGS) viewModel.setSongQuery("")
        seeAllSearchOpen = false
        seeAll = null
    }

    BackHandler(enabled = seeAll != null) { closeSeeAll() }

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

    val sortLabel = stringResource(uiState.songSort.labelRes())
    fun songQueueLabel(): String =
        if (uiState.songQuery.isNotBlank()) "$artistName · “${uiState.songQuery}”" else "$artistName · $sortLabel"

    // Now playing: same id, or the same recording under another id (catalogue songs play as
    // their matched copy). The current song's key is worked out once, rows carry theirs.
    val currentSong = stablePlayerState.currentSong
    val currentRecordingKey = remember(currentSong?.id) {
        currentSong?.let { com.theveloper.pixelplay.data.library.RecordingKeys.of(it) }
    }
    fun isCurrent(item: ArtistTrackItem): Boolean =
        currentSong != null && (item.playSong.id == currentSong.id ||
            (currentRecordingKey != null && item.recordingKey.isNotEmpty() && item.recordingKey == currentRecordingKey))

    // Live like state for the song rows (the catalogue's ownership is only a snapshot).
    fun isTrackLiked(item: ArtistTrackItem): Boolean =
        item.playSong.id in favoriteIds || item.track.song.id in favoriteIds

    fun toggleTrackLike(item: ArtistTrackItem) {
        // Unlike the id that is actually liked; otherwise like the playable song.
        val target = if (item.playSong.id !in favoriteIds && item.track.song.id in favoriteIds) item.track.song else item.playSong
        playerViewModel.toggleFavoriteSpecificSong(target)
    }

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

    /** Mix albums → Shuffle: the whole release, shuffled. */
    fun shuffleRelease(release: ArtistAlbumItem) {
        coroutineScope.launch {
            val songs = viewModel.releaseSongs(release)
            if (songs.isNotEmpty()) {
                playerViewModel.playSongsShuffled(songs, release.title, startAtZero = true)
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

    /** A Spotify playlist from "On Spotify": its songs are read (first 100), then opened or played. */
    fun openSpotifyPlaylist(item: com.theveloper.pixelplay.data.spotify.web.SpotifyArtistPlaylist, play: Boolean) {
        if (item.id in spotifyBusyIds) return
        spotifyBusyIds = spotifyBusyIds + item.id
        coroutineScope.launch {
            val songs = viewModel.spotifyPlaylistSongs(item.id)
            spotifyBusyIds = spotifyBusyIds - item.id
            if (songs.isEmpty()) {
                android.widget.Toast.makeText(context, playlistOpenFailed, android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (play) {
                playerViewModel.playSongs(songs.take(QUEUE_WINDOW), songs.first(), item.title)
            } else {
                val playlist = com.theveloper.pixelplay.data.model.Playlist(
                    id = "${PlaylistViewModel.GENERATED_MIX_PREFIX}sp_${item.id}",
                    name = item.title,
                    songIds = songs.map { it.id },
                    coverImageUri = item.coverUrl
                )
                PlaylistViewModel.registerTransientPlaylist(playlist, songs)
                navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
            }
        }
    }

    fun shuffleArtist() {
        // Truly random over the artist's whole catalogue: every song (yours and the catalogue's)
        // has the same chance; no weighting by popularity. The queue window is a random sample.
        val pool = (uiState.tracks.map { it.playSong } + uiState.completion?.offlineSongs.orEmpty() + uiState.songs)
            .distinctBy { it.id }
        if (pool.isNotEmpty()) {
            playerViewModel.playSongsShuffled(pool.shuffled().take(QUEUE_WINDOW), artistName, startAtZero = true)
        }
    }

    fun openYtPlaylist(item: com.theveloper.pixelplay.presentation.viewmodel.ArtistYtPlaylist, play: Boolean) {
        if (item.browseId in ytBusyIds) return
        ytBusyIds = ytBusyIds + item.browseId
        coroutineScope.launch {
            val songs = try {
                ytPlaylistLoader.loadAllSongs(item.browseId)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                ytBusyIds = ytBusyIds - item.browseId
                throw cancelled
            } catch (e: Exception) {
                emptyList()
            }
            ytBusyIds = ytBusyIds - item.browseId
            if (songs.isEmpty()) {
                android.widget.Toast.makeText(context, playlistOpenFailed, android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (play) {
                playerViewModel.playSongs(songs.take(QUEUE_WINDOW), songs.first(), item.title)
            } else {
                val playlist = com.theveloper.pixelplay.data.model.Playlist(
                    id = "${PlaylistViewModel.GENERATED_MIX_PREFIX}yt_${item.browseId}",
                    name = item.title,
                    songIds = songs.map { it.id },
                    coverImageUri = item.coverUrl
                )
                PlaylistViewModel.registerTransientPlaylist(playlist, songs)
                navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
            }
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
                                headerHeight = maxTopBarHeight,
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

                        // Tabs: Music always; Events when there are shows; Merch when there's merch or an
                        // official site to buy from; Socials when any links are known.
                        val upcomingShows = uiState.tour?.upcoming.orEmpty()
                        val socials = uiState.hub.socials
                        val officialWebsite = socials.officialWebsite
                        val hasSocials = officialWebsite != null || socials.platforms.isNotEmpty()
                        val tabs = remember(upcomingShows.isNotEmpty(), uiState.merch.isNotEmpty(), officialWebsite, hasSocials) {
                            buildList {
                                add(ArtistTab.MUSIC)
                                if (upcomingShows.isNotEmpty()) add(ArtistTab.EVENTS)
                                if (uiState.merch.isNotEmpty() || officialWebsite != null) add(ArtistTab.MERCH)
                                if (hasSocials) add(ArtistTab.SOCIALS)
                            }
                        }
                        // A tab that went away (e.g. after a refresh) falls back to Music.
                        val shownTab = if (selectedTab in tabs) selectedTab else ArtistTab.MUSIC
                        // Switching tabs: the new tab's sections slide in a little from the side
                        // the tab sits on and fade up, instead of popping in. Read in the draw
                        // phase only, so the slide doesn't recompose the list.
                        val tabEnter = remember { Animatable(1f) }
                        var tabDirection by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                        var lastTabIndex by remember { androidx.compose.runtime.mutableIntStateOf(tabs.indexOf(shownTab)) }
                        val reducedTabMotion = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
                        LaunchedEffect(shownTab) {
                            val index = tabs.indexOf(shownTab)
                            if (index == lastTabIndex) return@LaunchedEffect
                            tabDirection = if (index > lastTabIndex) 1 else -1
                            lastTabIndex = index
                            if (reducedTabMotion) return@LaunchedEffect
                            tabEnter.snapTo(0f)
                            tabEnter.animateTo(1f, androidx.compose.animation.core.tween(MotionTokens.DurationMedium3, easing = MotionTokens.EmphasizedDecelerate))
                        }
                        val tabMotion = Modifier.graphicsLayer {
                            val p = tabEnter.value
                            alpha = 0.15f + 0.85f * p
                            translationX = (1f - p) * tabDirection * 36.dp.toPx()
                        }
                        val eventCountries = remember(uiState.tour) { uiState.tour?.countries.orEmpty() }
                        val shownShows = remember(upcomingShows, eventsCountry) {
                            if (eventsCountry == null) upcomingShows else upcomingShows.filter { it.country == eventsCountry }
                        }

                        // Each section's own state: loading (skeleton), ready, or empty (left out).
                        val loaded = uiState.loadedSections
                        val songsState = artistSectionState(ArtistSection.SONGS in loaded, uiState.tracks.isEmpty())
                        val albumsState = artistSectionState(ArtistSection.RELEASES in loaded, uiState.popularReleases.isEmpty())
                        val singlesState = artistSectionState(ArtistSection.RELEASES in loaded, uiState.singlesAndEPs.isEmpty())
                        val timelineState = artistSectionState(ArtistSection.RELEASES in loaded, allReleases.size <= 1)
                        val appearsOnState = artistSectionState(ArtistSection.APPEARS_ON in loaded, uiState.appearsOn.isEmpty())
                        val videosState = artistSectionState(ArtistSection.VIDEOS in loaded, uiState.videos.isEmpty())
                        // Genres come from the catalogue and from Last.fm tags: wait for both, so an
                        // empty-then-filled section doesn't close and reopen.
                        val genresState = artistSectionState(
                            ArtistSection.GENRES in loaded && ArtistSection.ABOUT in loaded,
                            uiState.genres.isEmpty()
                        )
                        val fansState = artistSectionState(
                            ArtistSection.FANS in loaded,
                            uiState.fansAlsoLikeArtists.isEmpty() && uiState.fansAlsoLikeSongs.isEmpty() && uiState.fansAlsoLikePlaylists.isEmpty()
                        )
                        // About always has text (Last.fm → Wikipedia → a summary from your library),
                        // so it's never empty: a skeleton while loading, then the bio.
                        val aboutState = artistSectionState(ArtistSection.ABOUT in loaded, false)
                        val spotifyState = artistSectionState(ArtistSection.SPOTIFY in loaded, uiState.spotifyPlaylists.isEmpty())
                        // Similar leaves out anyone already in Fans also like.
                        val fansNames = remember(uiState.fansAlsoLikeArtists) {
                            uiState.fansAlsoLikeArtists.mapTo(HashSet()) { ArtistCatalog.fold(it.name) }
                        }
                        val similarArtists = remember(uiState.similarArtists, fansNames) {
                            uiState.similarArtists.filter { ArtistCatalog.fold(it.name) !in fansNames }
                        }
                        val similarState = artistSectionState(
                            ArtistSection.SIMILAR in loaded,
                            similarArtists.isEmpty() && uiState.similarSongs.isEmpty()
                        )
                        // A section that came back empty closes up and moves what's below it: for
                        // a moment after that, a tap is ignored so it can't land on the wrong row.
                        val emptySections = listOf(
                            songsState, albumsState, singlesState, timelineState, appearsOnState,
                            videosState, genresState, fansState, aboutState, spotifyState, similarState
                        ).map { it == ArtistSectionState.EMPTY }
                        val tapGuard = rememberTapGuard(emptySections)
                        val liveVideos = remember(uiState.liveVideos, uiState.videos) {
                            val musicVideoIds = uiState.videos.mapTo(HashSet()) { it.id }
                            uiState.liveVideos.filterNot { it.id in musicVideoIds }
                        }
                        val liveState = artistSectionState(ArtistSection.LIVE in loaded, liveVideos.isEmpty())

                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .tapGuard(tapGuard)
                                .offset {
                                    val extraHeight = (topBarHeight.value - minTopBarHeightPx).roundToInt()
                                    IntOffset(0, extraHeight)
                                },
                            contentPadding = PaddingValues(
                                top = minTopBarHeight + 8.dp,
                                start = 16.dp,
                                end = if (showScrollBar) 24.dp else 16.dp,
                                // Room for the Mix buttons, so the last row isn't under them.
                                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp +
                                    ArtistMixFabStackHeight + 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // 0. Stats card (with the catalogue status + refresh as its footer), then
                            // the Music · Events · Merch tabs when the artist has events or merch.
                            // Everything from here up stays put when switching tabs; everything below switches.
                            item(key = "header_stats", contentType = "stats") {
                                Column(
                                    modifier = Modifier.animateContentSize(tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized)),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ArtistStatsRow(
                                        fanCount = uiState.fanCount,
                                        songCount = uiState.tracks.size,
                                        releaseCount = allReleases.size,
                                        isAggregating = uiState.isAggregating,
                                        isOffline = uiState.isOffline,
                                        hasSavedCatalogue = uiState.hasSavedCatalogue,
                                        catalogueUpdatedAt = uiState.catalogueUpdatedAt,
                                        onRefresh = viewModel::refreshCatalogue
                                    )
                                }
                            }
                            // The tab chips (compact). When they scroll under the top bar, a docked copy
                            // takes over just below it (overlay further down), so tabs switch from anywhere.
                            if (tabs.size > 1) {
                                item(key = ARTIST_TABS_KEY, contentType = "artist_tabs") {
                                    ArtistTabRow(
                                        tabs = tabs,
                                        selected = shownTab,
                                        eventCount = upcomingShows.size,
                                        merchCount = uiState.merch.size,
                                        onSelect = { selectedTab = it }
                                    )
                                }
                            }

                            // ── Events tab: list (or map + list) of shows, Ticketmaster + Spotify ──
                            if (shownTab == ArtistTab.EVENTS) {
                                item(key = "events_controls", contentType = "events_controls") {
                                    Box(sectionItem(tabMotion)) {
                                        EventsControls(
                                            view = eventsView,
                                            countries = eventCountries,
                                            country = eventsCountry,
                                            onView = { eventsView = it },
                                            onCountry = { eventsCountry = it }
                                        )
                                    }
                                }
                                // The map is always here: compact, and it grows in place when tapped.
                                val hasMappableShows = shownShows.any { it.latitude != null && it.longitude != null }
                                if (hasMappableShows) {
                                    item(key = "events_map", contentType = "events_map") {
                                        Box(sectionItem(tabMotion)) {
                                            EventsMapCard(
                                                shows = shownShows,
                                                tiles = viewModel.mapTiles,
                                                selectedId = selectedShowId,
                                                withoutLocation = shownShows.count { it.latitude == null || it.longitude == null },
                                                onPinClick = { show -> selectedShowId = show.id }
                                            )
                                        }
                                    }
                                }
                                items(shownShows, key = { "event_${it.id}" }, contentType = { "event_row" }) { show ->
                                    Box(sectionItem(tabMotion)) {
                                        ArtistEventRow(
                                            show = show,
                                            // First tap picks the show (its pin lights up on the map); a second opens tickets.
                                            highlighted = hasMappableShows && show.id == selectedShowId,
                                            onClick = {
                                                if (hasMappableShows && show.latitude != null && show.id != selectedShowId) {
                                                    selectedShowId = show.id
                                                } else {
                                                    show.ticketUrl?.let { openWebLink(context, it) }
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // ── Merch tab: two per row ──
                            if (shownTab == ArtistTab.MERCH) {
                                val pairs = uiState.merch.chunked(2)
                                val rates = uiState.audRates
                                items(pairs.size, key = { "merch_${pairs[it].first().id}" }, contentType = { "merch_pair" }) { index ->
                                    Box(sectionItem(tabMotion)) {
                                        MerchPair(
                                            items = pairs[index],
                                            onOpen = { item -> openWebLink(context, item.url) },
                                            // Prices in AUD only ("$54 AUD"), at today's rate.
                                            formatPrice = { raw -> com.theveloper.pixelplay.data.artist.MerchPriceConverter.formatAudPrice(raw, rates) }
                                        )
                                    }
                                }
                                // No store items (or as well as them): the official site, where merch lives.
                                officialWebsite?.let { site ->
                                    item(key = "merch_official_store", contentType = "merch_store") {
                                        Box(sectionItem(tabMotion)) {
                                            com.theveloper.pixelplay.presentation.screens.artist.MerchStoreCard(
                                                hasItems = uiState.merch.isNotEmpty(),
                                                onOpen = { openWebLink(context, site) }
                                            )
                                        }
                                    }
                                }
                            }

                            // ── Socials tab: website + search, then Streaming / Social / Community ──
                            if (shownTab == ArtistTab.SOCIALS) {
                                item(key = "socials_hub", contentType = "socials_hub") {
                                    Box(sectionItem(tabMotion)) {
                                        com.theveloper.pixelplay.presentation.screens.artist.ArtistSocialsHub(
                                            artistName = artist.name,
                                            hub = socials
                                        )
                                    }
                                }
                            }

                            if (shownTab == ArtistTab.MUSIC) {
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
                            if (songsState != ArtistSectionState.EMPTY) {
                                item(key = "songs_header", contentType = "section_header") {
                                    Column(sectionItem(tabMotion), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ArtistSectionTitle(
                                            title = "All songs",
                                            count = uiState.tracks.size,
                                            countIsSongs = true,
                                            onClick = { seeAll = ArtistSeeAll.SONGS },
                                            trailing = {
                                                // Searches this artist's songs: opens All songs with its field open.
                                                IconButton(onClick = {
                                                    seeAllSearchOpen = true
                                                    seeAll = ArtistSeeAll.SONGS
                                                }) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Search,
                                                        contentDescription = stringResource(R.string.artist_songs_search_cd, artist.name)
                                                    )
                                                }
                                            }
                                        )
                                        SongSortFilterRow(
                                            sort = uiState.songSort,
                                            reversed = uiState.songSortReversed,
                                            filter = uiState.songFilter,
                                            onSort = viewModel::setSongSort,
                                            onToggleDirection = viewModel::toggleSongSortDirection,
                                            onFilter = viewModel::setSongFilter
                                        )
                                    }
                                }

                                val previewCount = if (songsExpanded) SONGS_EXPANDED else SONGS_PREVIEW

                                val preview = uiState.visibleTracks.take(previewCount)

                                // Collapsed with more to come: the last row fades out to show it's cut off.

                                val fadeLastRow = !songsExpanded && uiState.visibleTracks.size > SONGS_PREVIEW
                                when {
                                    songsState == ArtistSectionState.LOADING -> {
                                        // As many rows as the real preview, plus "Show all", so the
                                        // sections below are already where they'll stay.
                                        items(SONGS_PREVIEW, key = { "song_skeleton_$it" }, contentType = { "song_skeleton" }) {
                                            Box(sectionItem(tabMotion)) { SongRowSkeleton() }
                                        }
                                        item(key = "songs_show_all", contentType = "show_all") {
                                            Box(sectionItem(tabMotion)) { ShowAllButtonSkeleton() }
                                        }
                                    }
                                    preview.isEmpty() && uiState.songQuery.isNotBlank() -> {
                                        item(key = "songs_no_match", contentType = "songs_no_match") {
                                            NoSongsMatch(query = uiState.songQuery, onClear = { viewModel.setSongQuery("") })
                                        }
                                    }
                                    preview.isEmpty() && uiState.tracks.isNotEmpty() -> {
                                        item(key = "songs_no_match", contentType = "songs_no_match") {
                                            ArtistSongsEmpty(uiState, artist.name, viewModel)
                                        }
                                    }
                                    else -> {
                                        items(preview, key = { "song_${it.key}" }, contentType = { "song_row" }) { item ->
                                            val fading = fadeLastRow && item.key == preview.last().key
                                            Box(sectionItem(tabMotion).then(if (fading) Modifier.truncationFade() else Modifier)) {
                                                ArtistSongRow(
                                                    item = item,
                                                    isCurrentSong = isCurrent(item),
                                                    isPlaying = stablePlayerState.isPlaying,
                                                    versionsExpanded = item.key in expandedVersions,
                                                    onClick = { playFrom(item, uiState.visibleTracks, songQueueLabel()) },
                                                    onToggleVersions = { toggleVersions(item.key) },
                                                    onVersionClick = { version -> songTap.handle(version, listOf(version), artist.name) { playerViewModel.showAndPlaySong(version, listOf(version), artist.name) } },
                                                    onMoreClick = { openSongInfo(item.playSong) },
                                                    isFavorite = isTrackLiked(item),
                                                    onToggleFavorite = { toggleTrackLike(item) }
                                                )
                                            }
                                        }
                                        if (uiState.visibleTracks.size > SONGS_PREVIEW) {
                                            item(key = "songs_show_all", contentType = "show_all") {
                                                com.theveloper.pixelplay.presentation.screens.artist.AllSongsMoreRow(
                                                    expanded = songsExpanded,
                                                    total = uiState.visibleTracks.size,
                                                    canShowAll = uiState.visibleTracks.size > SONGS_EXPANDED,
                                                    onSeeMore = { songsExpanded = true },
                                                    onShowLess = { songsExpanded = false },
                                                    onShowAll = { seeAll = ArtistSeeAll.SONGS },
                                                    modifier = sectionItem(tabMotion)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. You & Artist (the Artist mix moved to the Mix buttons, bottom right)
                            uiState.listening?.let { listening ->
                                item(key = "you_and_artist_header", contentType = "section_header") {
                                    Box(sectionItem(tabMotion)) { ArtistSectionTitle(title = "You & ${artist.name}") }
                                }
                                item(key = "you_and_artist", contentType = "you_and_artist") {
                                    Box(sectionItem(tabMotion)) {
                                        YouAndArtistCard(
                                            artistName = artist.name,
                                            listening = listening,
                                            onTopSongClick = { song -> songTap.handle(song, listOf(song), artist.name) { playerViewModel.showAndPlaySong(song, listOf(song), artist.name) } }
                                        )
                                    }
                                }
                            }

                            // 4. Discography completion
                            uiState.completion?.let { completion ->
                                item(key = "completion", contentType = "completion") {
                                    Box(sectionItem(tabMotion)) {
                                        DiscographyCompletionCard(
                                            completion = completion,
                                            onDownloadLiked = { viewModel.downloadLikedSongs() },
                                            onLikeAlbums = if (allReleases.isNotEmpty()) ({ showLikeAlbums = true }) else null,
                                            onShuffle = {
                                                playerViewModel.playSongsShuffled(
                                                    completion.offlineSongs,
                                                    "${artist.name} · on this device",
                                                    startAtZero = true
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            // 5. Albums
                            if (albumsState != ArtistSectionState.EMPTY) {
                                item(key = "albums_header", contentType = "section_header") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionTitle(
                                            title = "Albums",
                                            count = uiState.popularReleases.size,
                                            onClick = if (albumsState == ArtistSectionState.READY) ({ seeAll = ArtistSeeAll.ALBUMS }) else null
                                        )
                                    }
                                }
                                item(key = "albums_carousel", contentType = "carousel") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionReveal(ready = albumsState == ArtistSectionState.READY, skeleton = { ReleaseCarouselSkeleton() }) {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                contentPadding = PaddingValues(horizontal = 2.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(uiState.popularReleases, key = { it.id }) { album ->
                                                    ReleaseCard(release = album, onOpen = { openRelease(album) }, onShuffle = { shuffleRelease(album) })
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 6. Singles and EPs
                            if (singlesState != ArtistSectionState.EMPTY) {
                                item(key = "singles_eps_header", contentType = "section_header") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionTitle(
                                            title = "Singles and EPs",
                                            count = uiState.singlesAndEPs.size,
                                            onClick = if (singlesState == ArtistSectionState.READY) ({ seeAll = ArtistSeeAll.SINGLES }) else null
                                        )
                                    }
                                }
                                item(key = "singles_eps_carousel", contentType = "carousel") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionReveal(ready = singlesState == ArtistSectionState.READY, skeleton = { ReleaseCarouselSkeleton() }) {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                contentPadding = PaddingValues(horizontal = 2.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(uiState.singlesAndEPs, key = { it.id }) { single ->
                                                    ReleaseCard(release = single, onOpen = { openRelease(single) }, onShuffle = { shuffleRelease(single) })
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 7. Appears on
                            if (appearsOnState != ArtistSectionState.EMPTY) {
                                item(key = "appears_on_header", contentType = "section_header") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionTitle(
                                            title = "Appears on",
                                            count = uiState.appearsOn.size,
                                            onClick = if (appearsOnState == ArtistSectionState.READY) ({ seeAll = ArtistSeeAll.APPEARS_ON }) else null
                                        )
                                    }
                                }
                                item(key = "appears_on_carousel", contentType = "carousel") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionReveal(ready = appearsOnState == ArtistSectionState.READY, skeleton = { SongCardCarouselSkeleton() }) {
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
                                }
                            }

                            // 8. Music videos
                            if (videosState != ArtistSectionState.EMPTY) {
                                item(key = "videos_header", contentType = "section_header") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionTitle(
                                            title = "Music videos",
                                            count = uiState.videos.size,
                                            onClick = if (videosState == ArtistSectionState.READY) ({ seeAll = ArtistSeeAll.VIDEOS }) else null
                                        )
                                    }
                                }
                                item(key = "videos_carousel", contentType = "carousel") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionReveal(ready = videosState == ArtistSectionState.READY, skeleton = { VideoCarouselSkeleton() }) {
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
                                }
                            }

                            // 8b. Live performances, right under the music videos (no repeats of those).
                            if (liveState != ArtistSectionState.EMPTY) {
                                item(key = "live_header", contentType = "section_header") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionTitle(
                                            title = stringResource(R.string.artist_live_performances_title),
                                            count = liveVideos.size,
                                            onClick = if (liveState == ArtistSectionState.READY) ({ seeAll = ArtistSeeAll.LIVE }) else null
                                        )
                                    }
                                }
                                item(key = "live_carousel", contentType = "carousel") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionReveal(ready = liveState == ArtistSectionState.READY, skeleton = { VideoCarouselSkeleton() }) {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                contentPadding = PaddingValues(horizontal = 2.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(liveVideos, key = { "live_" + it.id }) { video ->
                                                    ArtistVideoCard(video = video, onClick = { playVideo(video) })
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 9. Timeline (needs releases)
                            if (timelineState != ArtistSectionState.EMPTY) {
                                item(key = "timeline_header", contentType = "section_header") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionTitle(
                                            title = "Timeline",
                                            count = allReleases.size,
                                            onClick = if (timelineState == ArtistSectionState.READY) ({ seeAll = ArtistSeeAll.TIMELINE }) else null
                                        )
                                    }
                                }
                                item(key = "timeline_preview", contentType = "timeline") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionReveal(ready = timelineState == ArtistSectionState.READY, skeleton = { TimelineSkeleton(TIMELINE_PREVIEW) }) {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                TimelineContent(
                                                    releases = allReleases.take(TIMELINE_PREVIEW),
                                                    ownedByRelease = ownedByRelease,
                                                    onOpen = ::openRelease
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 10. Genres & moods
                            if (genresState != ArtistSectionState.EMPTY) {
                                item(key = "genres_header", contentType = "section_header") {
                                    Box(sectionItem(tabMotion)) { ArtistSectionTitle(title = "Genres & moods") }
                                }
                                item(key = "genres", contentType = "genres") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionReveal(ready = genresState == ArtistSectionState.READY, skeleton = { GenreChipsSkeleton() }) {
                                            GenreChips(genres = uiState.genres, onClick = ::openGenre)
                                        }
                                    }
                                }
                            }

                            // 10b. The artist's playlists: Spotify (own, "This Is", featuring…) and YouTube
                            // Music in one row, each badged with its service's logo top-right.
                            val playlistsLoaded = ArtistSection.SPOTIFY in loaded && ArtistSection.YT_PLAYLISTS in loaded
                            val artistPlaylistsState = artistSectionState(
                                playlistsLoaded,
                                uiState.spotifyPlaylists.isEmpty() && uiState.ytPlaylists.isEmpty()
                            )
                            if (artistPlaylistsState != ArtistSectionState.EMPTY) {
                                item(key = "spotify_playlists_header", contentType = "section_header") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionTitle(
                                            title = stringResource(R.string.artist_playlists_title),
                                            count = (uiState.spotifyPlaylists.size + uiState.ytPlaylists.size).takeIf { it > 0 }
                                        )
                                    }
                                }
                                item(key = "spotify_playlists_carousel", contentType = "carousel") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionReveal(ready = artistPlaylistsState == ArtistSectionState.READY, skeleton = { ReleaseCarouselSkeleton() }) {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                contentPadding = PaddingValues(horizontal = 2.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(uiState.spotifyPlaylists, key = { "sp_" + it.id }) { playlist ->
                                                    val busy = playlist.id in spotifyBusyIds
                                                    com.theveloper.pixelplay.presentation.components.CollectionCard(
                                                        title = playlist.title,
                                                        subtitle = playlist.subtitle,
                                                        coverModel = playlist.coverUrl,
                                                        onClick = { openSpotifyPlaylist(playlist, play = false) },
                                                        onAction = { openSpotifyPlaylist(playlist, play = true) },
                                                        actionLabel = stringResource(R.string.artist_play_playlist_cd, playlist.title),
                                                        actionContent = {
                                                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                                            else Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                                        },
                                                        coverOverlay = { ArtistPlaylistSourceBadge("SPOTIFY") }
                                                    )
                                                }
                                                items(uiState.ytPlaylists, key = { "yt_" + it.browseId }) { playlist ->
                                                    val busy = playlist.browseId in ytBusyIds
                                                    com.theveloper.pixelplay.presentation.components.CollectionCard(
                                                        title = playlist.title,
                                                        subtitle = "YouTube Music",
                                                        coverModel = playlist.coverUrl,
                                                        onClick = { openYtPlaylist(playlist, play = false) },
                                                        onAction = { openYtPlaylist(playlist, play = true) },
                                                        actionLabel = stringResource(R.string.artist_play_playlist_cd, playlist.title),
                                                        actionContent = {
                                                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                                            else Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                                        },
                                                        coverOverlay = { ArtistPlaylistSourceBadge("YOUTUBE_MUSIC") }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 11. Fans also like (A6: Artists · Songs · Playlists, one always selected)
                            if (fansState != ArtistSectionState.EMPTY) {
                                item(key = "fans_also_like_header", contentType = "section_header") {
                                    Column(sectionItem(tabMotion), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ArtistSectionTitle(
                                            title = "Fans also like",
                                            count = uiState.fansAlsoLikeArtists.size.takeIf { it > 0 },
                                            onClick = if (fansState == ArtistSectionState.READY) ({ seeAll = ArtistSeeAll.FANS }) else null
                                        )
                                        FansFilterChips(selected = uiState.activeFansFilter, onSelect = viewModel::setFansFilter)
                                    }
                                }

                                item(key = "fans_carousel", contentType = "carousel") {
                                    Box(sectionItem(tabMotion)) {
                                        ArtistSectionReveal(
                                            ready = fansState == ArtistSectionState.READY,
                                            skeleton = {
                                                if (uiState.activeFansFilter == FansFilterType.ARTISTS) ArtistCircleCarouselSkeleton()
                                                else SongCardCarouselSkeleton()
                                            }
                                        ) {
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
                                }
                            }

                            // 11b. Similar (Spotify related artists, or Last.fm's): Artists · Songs
                            if (similarState != ArtistSectionState.EMPTY) {
                                item(key = "similar_header", contentType = "section_header") {
                                    Column(sectionItem(tabMotion), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ArtistSectionTitle(
                                            title = stringResource(R.string.artist_similar_title),
                                            count = similarArtists.size.takeIf { it > 0 },
                                            onClick = if (similarState == ArtistSectionState.READY) ({ seeAll = ArtistSeeAll.SIMILAR }) else null
                                        )
                                        SimilarFilterChips(
                                            selected = uiState.activeSimilarFilter,
                                            hasSongs = uiState.similarSongs.isNotEmpty(),
                                            onSelect = viewModel::setSimilarFilter
                                        )
                                    }
                                }
                                item(key = "similar_carousel", contentType = "carousel") {
                                    Box(sectionItem(tabMotion)) {
                                        val showSongs = uiState.activeSimilarFilter == FansFilterType.SONGS && uiState.similarSongs.isNotEmpty()
                                        ArtistSectionReveal(
                                            ready = similarState == ArtistSectionState.READY,
                                            skeleton = { if (showSongs) SongCardCarouselSkeleton() else ArtistCircleCarouselSkeleton() }
                                        ) {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                contentPadding = PaddingValues(horizontal = 2.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (showSongs) {
                                                    items(uiState.similarSongs, key = { it.id }) { song ->
                                                        RelatedSongCard(
                                                            song = song,
                                                            onClick = {
                                                                songTap.handle(song, uiState.similarSongs, similarLabel) {
                                                                    playerViewModel.showAndPlaySong(song, uiState.similarSongs, similarLabel)
                                                                }
                                                            }
                                                        )
                                                    }
                                                } else {
                                                    items(similarArtists, key = { "similar_" + it.id }) { related ->
                                                        RelatedArtistCard(item = related, onClick = { openArtist(related) })
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 12. About: the page's centred finale. Bio, band members, and (where Gemini
                            // Nano is on the phone) an on-device "Ask about…" box.
                            val members = uiState.hub.members
                            val showAbout = aboutState != ArtistSectionState.EMPTY || members.isNotEmpty() || uiState.ai.available
                            if (showAbout) {
                                item(key = "about_header", contentType = "section_header") {
                                    Box(sectionItem(tabMotion).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        com.theveloper.pixelplay.presentation.screens.artist.AboutFinaleTitle(artist.name)
                                    }
                                }
                                if (aboutState != ArtistSectionState.EMPTY) {
                                    item(key = "about", contentType = "about") {
                                        Box(sectionItem(tabMotion)) {
                                            ArtistSectionReveal(ready = aboutState == ArtistSectionState.READY, skeleton = { AboutSkeleton() }) {
                                                val remoteBio = uiState.bio?.takeIf { it.isNotBlank() }
                                                val localBio = com.theveloper.pixelplay.presentation.screens.artist.localArtistBio(
                                                    artistName = artist.name,
                                                    songCount = uiState.tracks.size,
                                                    releaseCount = uiState.popularReleases.size + uiState.singlesAndEPs.size,
                                                    genres = uiState.genres,
                                                    members = members,
                                                    topSongs = remember(uiState.tracks) {
                                                        uiState.tracks.sortedByDescending { t -> t.track.popularity ?: 0 }.take(3).map { t -> t.track.song.title }
                                                    },
                                                )
                                                AboutCard(
                                                    bio = remoteBio ?: localBio,
                                                    source = if (remoteBio != null && uiState.bioSource != null) {
                                                        stringResource(R.string.artist_about_source, uiState.bioSource!!)
                                                    } else {
                                                        stringResource(R.string.artist_about_source_library)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                if (members.isNotEmpty()) {
                                    item(key = "about_members", contentType = "about_members") {
                                        Box(sectionItem(tabMotion)) {
                                            com.theveloper.pixelplay.presentation.screens.artist.BandMembersRow(members = members)
                                        }
                                    }
                                }
                                if (uiState.ai.available) {
                                    item(key = "about_ai", contentType = "about_ai") {
                                        Box(sectionItem(tabMotion)) {
                                            com.theveloper.pixelplay.presentation.screens.artist.ArtistAskBox(
                                                artistName = artist.name,
                                                state = uiState.ai,
                                                onAsk = viewModel::askAi
                                            )
                                        }
                                    }
                                }
                            }
                            } // end Music tab
                        }

                        // Docked tabs: once the in-page tab chips slide under the top bar, this copy
                        // sits right below the bar so you can switch tabs from anywhere on the page.
                        val tabsDocked by remember {
                            derivedStateOf {
                                val info = lazyListState.layoutInfo
                                val tabItem = info.visibleItemsInfo.firstOrNull { it.key == ARTIST_TABS_KEY }
                                if (tabItem != null) tabItem.offset < 0 else lazyListState.firstVisibleItemIndex >= 2
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = tabs.size > 1 && tabsDocked && seeAll == null,
                            enter = fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                                androidx.compose.animation.slideInVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) { -it / 2 },
                            exit = fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)) +
                                androidx.compose.animation.slideOutVertically(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)) { -it / 2 },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = currentTopBarHeightDp)
                                .zIndex(1f)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 3.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ArtistTabRow(
                                    tabs = tabs,
                                    selected = shownTab,
                                    eventCount = upcomingShows.size,
                                    merchCount = uiState.merch.size,
                                    onSelect = { tab ->
                                        selectedTab = tab
                                        // Switching from the docked chips lands at the top of that tab.
                                        coroutineScope.launch { lazyListState.animateScrollToItem(1) }
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
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
                                onBackPressed = { navController.popBackStackSafely() },
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
                                onBackPressed = { navController.popBackStackSafely() },
                                onPlayClick = ::shuffleArtist,
                                onChangeImage = { imagePickerLauncher.launch("image/*") },
                                onClearCustomImage = { viewModel.clearCustomImage() }
                            )
                        }

                        // Mix all songs + Mix albums, bottom right above the mini player.
                        ArtistMixFabs(
                            showMixAll = seeAll == null && selectedTab == ArtistTab.MUSIC && (uiState.tracks.isNotEmpty() || uiState.songs.isNotEmpty()),
                            showMixAlbums = seeAll == null && selectedTab == ArtistTab.MUSIC && uiState.popularReleases.isNotEmpty(),
                            onMixAll = {
                                val seeds = viewModel.artistMixSeeds().ifEmpty { uiState.songs.shuffled().take(20) }
                                if (seeds.isNotEmpty()) {
                                    playerViewModel.startContinuousMix(
                                        com.theveloper.pixelplay.data.MixFlavor.NORMAL,
                                        initial = seeds
                                    )
                                }
                            },
                            onMixAlbums = { showMixAlbums = true },
                            artistName = artist.name,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = MiniPlayerHeight + systemNavBarInset + 16.dp)
                        )

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
                                isCurrent = ::isCurrent,
                                isPlaying = stablePlayerState.isPlaying,
                                isLiked = ::isTrackLiked,
                                onToggleLike = ::toggleTrackLike,
                                startWithSearch = seeAllSearchOpen,
                                expandedVersions = expandedVersions,
                                bottomPadding = MiniPlayerHeight + systemNavBarInset + 16.dp,
                                onBack = ::closeSeeAll,
                                onQueryChange = viewModel::setSongQuery,
                                onSortChange = viewModel::setSongSort,
                                onToggleDirection = viewModel::toggleSongSortDirection,
                                onFilterChange = viewModel::setSongFilter,
                                emptyContent = { ArtistSongsEmpty(uiState, artist.name, viewModel) },
                                onFansFilterChange = viewModel::setFansFilter,
                                onSongClick = { item, list -> playFrom(item, list, songQueueLabel()) },
                                onToggleVersions = ::toggleVersions,
                                onVersionClick = { version -> songTap.handle(version, listOf(version), artist.name) { playerViewModel.showAndPlaySong(version, listOf(version), artist.name) } },
                                onSongMore = ::openSongInfo,
                                onReleaseClick = ::openRelease,
                                onVideoClick = ::playVideo,
                                onArtistClick = ::openArtist,
                                onPlaylistClick = ::openArtistPlaylist,
                                onPlaySongs = { song, list, label -> playerViewModel.showAndPlaySong(song, list.take(QUEUE_WINDOW), label) },
                                onShuffleSongs = { songs, label -> playerViewModel.playSongsShuffled(songs, label, startAtZero = true) }
                            )
                        }
                    }
                }
            }
        } // End Surface

        if (showMixAlbums) {
            MixAlbumsSheet(
                artistName = artistName,
                albums = uiState.popularReleases,
                onShuffle = ::shuffleRelease,
                onOpen = ::openRelease,
                onDismiss = { showMixAlbums = false }
            )
        }

        if (tourSheetOpen) {
            uiState.tour?.let { tour ->
                TourSheet(tour = tour, artistName = artistName, tiles = viewModel.mapTiles, onDismiss = { tourSheetOpen = false })
            }
        }

        if (showLikeAlbums) {
            val likeFailedFormat = stringResource(R.string.artist_like_album_failed)

            LikeAlbumsSheet(
                artistName = artistName,
                releases = allReleases,
                isLiked = { release ->
                    release.id in likedReleaseIds || run {
                        val title = ArtistCatalog.fold(release.title)
                        val onIt = uiState.tracks.filter { ArtistCatalog.fold(it.track.song.album) == title }
                        onIt.isNotEmpty() && onIt.all { it.isLiked }
                    }
                },
                busyIds = likeBusyIds,
                notOwnedCount = uiState.completion?.notOwned?.size ?: 0,
                onLikeRelease = { release ->
                    likeBusyIds = likeBusyIds + release.id
                    coroutineScope.launch {
                        val songs = viewModel.songsToLike(release, favoriteIds)
                        likeBusyIds = likeBusyIds - release.id
                        if (songs == null) {
                            android.widget.Toast.makeText(context, likeFailedFormat.format(release.title), android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            songs.forEach { playerViewModel.toggleFavoriteSpecificSong(it) }
                            likedReleaseIds = likedReleaseIds + release.id
                        }
                    }
                },
                onLikeAll = { showLikeAllDialog = true },
                onOpen = ::openRelease,
                onDismiss = { showLikeAlbums = false }
            )
        }

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
                    onStartSongRadio = { seedSong -> playerViewModel.startSongRadio(seedSong) },
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
                    // Hold Next → Soon (the "after which song" slider).
                    soonPlayer = playerViewModel,
                    onAddSoon = { k -> playerViewModel.addSongAfterUpcoming(currentSong, k) },
                    onAddToPlayList = {
                        showPlaylistBottomSheet = true
                    },
                    onDeleteFromDevice = playerViewModel::deleteFromDevice,
                    onNavigateToAlbum = {
                        Screen.AlbumDetail.createRouteForSong(currentSong)?.let { route ->
                            navController.navigateSafelyReplacing(
                                route = route,
                                patternToPop = Screen.AlbumDetail.route
                            )
                        }
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
            ExpressiveFilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = label
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
    // A vertical rail: a pill per decade, a node per release (filled when you have songs from it).
    val decades = releases.groupBy { release -> release.releaseYear?.let { "${it / 10 * 10}s" } ?: "Undated" }.toList()
    Column {
        decades.forEachIndexed { decadeIndex, (decade, inDecade) ->
            TimelineDecadeHeader(decade = decade, count = inDecade.size, isFirst = decadeIndex == 0)
            inDecade.forEachIndexed { i, release ->
                TimelineRow(
                    release = release,
                    ownedCount = ownedByRelease[release.id] ?: 0,
                    onClick = { onOpen(release) },
                    isLast = decadeIndex == decades.lastIndex && i == inDecade.lastIndex
                )
            }
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
    isCurrent: (ArtistTrackItem) -> Boolean,
    isPlaying: Boolean,
    isLiked: (ArtistTrackItem) -> Boolean,
    onToggleLike: (ArtistTrackItem) -> Unit,
    startWithSearch: Boolean,
    expandedVersions: Set<String>,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (ArtistSongSort) -> Unit,
    onToggleDirection: () -> Unit,
    onFilterChange: (com.theveloper.pixelplay.presentation.viewmodel.ArtistSongFilter?) -> Unit,
    emptyContent: @Composable () -> Unit,
    onFansFilterChange: (FansFilterType) -> Unit,
    onSongClick: (ArtistTrackItem, List<ArtistTrackItem>) -> Unit,
    onToggleVersions: (String) -> Unit,
    onVersionClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    onReleaseClick: (ArtistAlbumItem) -> Unit,
    onVideoClick: (TrackVideo) -> Unit,
    onArtistClick: (RelatedArtistItem) -> Unit,
    onPlaylistClick: (ArtistPlaylistItem) -> Unit,
    onPlaySongs: (Song, List<Song>, String) -> Unit,
    onShuffleSongs: (List<Song>, String) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            if (target == ArtistSeeAll.SONGS) {
                AllSongsHeader(
                    uiState = uiState,
                    artistName = artistName,
                    startWithSearch = startWithSearch,
                    onBack = onBack,
                    onQueryChange = onQueryChange
                )
            } else {
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
            }

            val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding)
            when (target) {
                ArtistSeeAll.SONGS -> {
                    SongSortFilterRow(
                        sort = uiState.songSort,
                        reversed = uiState.songSortReversed,
                        filter = uiState.songFilter,
                        onSort = onSortChange,
                        onToggleDirection = onToggleDirection,
                        onFilter = onFilterChange,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    // The album of the song playing now, for the visualiser on its group header.
                    val playingAlbumKey = uiState.visibleTracks.firstOrNull { isCurrent(it) }
                        ?.let { com.theveloper.pixelplay.data.repository.ArtistCatalog.albumKey(it.track) }
                    // Album sort: a small header where each album's songs start.
                    val groupStarts = remember(uiState.visibleTracks, uiState.songSort) {
                        if (uiState.songSort == ArtistSongSort.ALBUM) {
                            com.theveloper.pixelplay.data.repository.ArtistCatalog.albumGroupStarts(uiState.visibleTracks.map { it.track })
                        } else emptyMap()
                    }
                    LazyColumn(
                        contentPadding = listPadding,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.visibleTracks.isEmpty() && uiState.songQuery.isNotBlank()) {
                            item(key = "no_match") { NoSongsMatch(query = uiState.songQuery, onClear = { onQueryChange("") }) }
                        } else if (uiState.visibleTracks.isEmpty() && uiState.tracks.isNotEmpty()) {
                            item(key = "no_match") { emptyContent() }
                        }
                        uiState.visibleTracks.forEachIndexed { index, row ->
                            groupStarts[index]?.let { groupKey ->
                                item(key = "album_${groupKey}_$index", contentType = "album_header") {
                                    AlbumGroupHeader(
                                        coverUrl = row.track.song.albumArtUriString,
                                        title = row.track.song.album,
                                        year = row.track.releaseYear,
                                        nowPlaying = groupKey == playingAlbumKey,
                                        isPlaying = isPlaying,
                                        onShuffle = {
                                            // The whole album (not just what the search/filter shows),
                                            // shuffled and started on a random song.
                                            val albumSongs = uiState.tracks
                                                .filter { com.theveloper.pixelplay.data.repository.ArtistCatalog.albumKey(it.track) == groupKey }
                                                .map { it.playSong }
                                                .distinctBy { it.id }
                                            if (albumSongs.isNotEmpty()) {
                                                onShuffleSongs(albumSongs, row.track.song.album.ifBlank { artistName })
                                            }
                                        }
                                    )
                                }
                            }
                            item(key = row.key, contentType = "song_row") {
                                ArtistSongRow(
                                    item = row,
                                    isCurrentSong = isCurrent(row),
                                    isPlaying = isPlaying,
                                    versionsExpanded = row.key in expandedVersions,
                                    onClick = { onSongClick(row, uiState.visibleTracks) },
                                    onToggleVersions = { onToggleVersions(row.key) },
                                    onVersionClick = onVersionClick,
                                    onMoreClick = { onSongMore(row.playSong) },
                                    isFavorite = isLiked(row),
                                    onToggleFavorite = { onToggleLike(row) }
                                )
                            }
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
                                isCurrentSong = isCurrent(item),
                                isPlaying = isPlaying,
                                versionsExpanded = false,
                                onClick = { onSongClick(item, uiState.appearsOn) },
                                onToggleVersions = {},
                                onVersionClick = onVersionClick,
                                onMoreClick = { onSongMore(item.playSong) },
                                isFavorite = isLiked(item),
                                onToggleFavorite = { onToggleLike(item) }
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
                ArtistSeeAll.LIVE -> {
                    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                        items(uiState.liveVideos, key = { it.id }) { video ->
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
                ArtistSeeAll.SIMILAR -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = listPadding,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.activeSimilarFilter == FansFilterType.SONGS && uiState.similarSongs.isNotEmpty()) {
                            gridItems(uiState.similarSongs, key = { it.id }) { song ->
                                RelatedSongCard(song = song, onClick = { onPlaySongs(song, uiState.similarSongs, target.title) })
                            }
                        } else {
                            gridItems(uiState.similarArtists, key = { "similar_" + it.id }) { related ->
                                RelatedArtistCard(item = related, onClick = { onArtistClick(related) })
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
        ReleaseMetaLine(
            prefix = listOfNotNull(item.releaseYear?.toString(), if (item.isSingleOrEp) "Single / EP" else "Album")
                .joinToString(com.theveloper.pixelplay.presentation.components.CompactMeta.SEPARATOR),
            trackCount = item.trackCount,
            ownedCount = ownedCount
        )
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

        ReleaseMetaLine(
            prefix = listOfNotNull(item.releaseYear?.toString(), if (item.isSingleOrEp) "Single / EP" else "Album")
                .joinToString(com.theveloper.pixelplay.presentation.components.CompactMeta.SEPARATOR),
            trackCount = item.trackCount
        )
    }
}

@Composable
private fun ArtistPlaylistCard(
    item: ArtistPlaylistItem,
    onClick: () -> Unit
) {
    // Same card as every other playlist / album on the page.
    com.theveloper.pixelplay.presentation.components.CollectionCard(
        title = item.title,
        subtitle = item.subtitle,
        coverModel = item.coverArtUrl,
        onClick = onClick
    )
}

@Composable
private fun RelatedArtistCard(
    item: RelatedArtistItem,
    onClick: () -> Unit
) {
    // The shared card, with a round picture so artists still read as people.
    com.theveloper.pixelplay.presentation.components.CollectionCard(
        title = item.name,
        subtitle = if (item.fanCount > 0) "${formatMetricCompact(item.fanCount)} fans" else "",
        coverModel = item.imageUrl,
        onClick = onClick,
        coverShape = CircleShape
    )
}

@Composable
private fun RelatedSongCard(
    song: Song,
    onClick: () -> Unit
) {
    // The shared card (same size, corners and type as playlists and albums).
    com.theveloper.pixelplay.presentation.components.CollectionCard(
        title = song.title,
        subtitle = song.artist,
        coverModel = song.albumArtUriString,
        onClick = onClick,
        modifier = Modifier
            .songStartFeedback(song.id, MaterialTheme.shapes.large)
            .offlineVeil(song, MaterialTheme.shapes.large)
    )
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

/** An album or single on the artist page: the shared playlist card, with Shuffle on the cover. */
@Composable
private fun ReleaseCard(release: ArtistAlbumItem, onOpen: () -> Unit, onShuffle: () -> Unit) {
    val spin = rememberShuffleSpin()
    com.theveloper.pixelplay.presentation.components.CollectionCard(
        title = release.title,
        subtitle = listOfNotNull(
            release.releaseYear?.toString(),
            release.trackCount.takeIf { it > 0 }?.let { if (it == 1) "1 song" else "$it songs" }
        ).joinToString(" · "),
        subtitleContent = { ReleaseMetaLine(prefix = release.releaseYear?.toString(), trackCount = release.trackCount) },
        coverModel = release.coverArtUrl,
        onClick = onOpen,
        onAction = { spin.play(); onShuffle() },
        actionLabel = "Shuffle ${release.title}",
        actionContent = {
            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp).shuffleSpin(spin))
        }
    )
}

/**
 * Artist page list items glide (rather than jump) when a section above them changes size or
 * closes up, and fade when they come or go.
 */
private fun androidx.compose.foundation.lazy.LazyItemScope.sectionItem(extra: Modifier = Modifier): Modifier = Modifier.animateItem(
    fadeInSpec = androidx.compose.animation.core.tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate),
    placementSpec = androidx.compose.animation.core.tween(MotionTokens.DurationMedium1, easing = MotionTokens.Emphasized),
    fadeOutSpec = androidx.compose.animation.core.tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)
).then(extra)

/**
 * All songs header, one quiet row: back · "All songs" ♪ n · search (top right). Search swaps the
 * title for the field in the same row (fade through), so nothing below moves.
 */
@Composable
private fun AllSongsHeader(
    uiState: ArtistDetailUiState,
    artistName: String,
    startWithSearch: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit
) {
    var searchOpen by rememberSaveable(startWithSearch) { mutableStateOf(startWithSearch || uiState.songQuery.isNotBlank()) }
    val reduced = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        androidx.compose.animation.AnimatedContent(
            targetState = searchOpen,
            transitionSpec = {
                if (reduced) {
                    androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                } else {
                    fadeIn(tween(MotionTokens.DurationShort4, delayMillis = MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                        fadeOut(tween(MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedAccelerate))
                }
            },
            contentAlignment = Alignment.CenterStart,
            label = "allSongsHeader",
            modifier = Modifier.weight(1f)
        ) { open ->
            if (open) {
                Box(Modifier.fillMaxWidth().padding(end = 8.dp)) {
                    ArtistSongSearchField(
                        query = uiState.songQuery,
                        artistName = artistName,
                        songCount = uiState.tracks.size,
                        isLoading = uiState.isAggregating,
                        onQueryChange = onQueryChange,
                        onClose = {
                            onQueryChange("")
                            searchOpen = false
                        },
                        autoFocus = true
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.artist_all_songs_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    com.theveloper.pixelplay.presentation.components.CompactCountBadge(
                        count = uiState.tracks.size,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.artist_songs_search_cd, artistName))
                    }
                }
            }
        }
    }
}

/** Nothing to list: filters that match nothing, or "Plays" before you've played anything. */
@Composable
private fun ArtistSongsEmpty(uiState: ArtistDetailUiState, artistName: String, viewModel: ArtistDetailViewModel) {
    if (uiState.songFilter != null) {
        SongsEmptyNote(
            text = stringResource(R.string.artist_no_filter_match),
            actionLabel = stringResource(R.string.artist_clear_filters),
            onAction = { viewModel.setSongFilter(null) }
        )
    } else {
        SongsEmptyNote(
            text = stringResource(R.string.artist_no_plays, artistName),
            actionLabel = stringResource(R.string.artist_sort_popular),
            onAction = { viewModel.setSongSort(ArtistSongSort.POPULAR) }
        )
    }
}

/** Similar's chips: Artists · Songs (Songs only when there are some). Same look as Fans also like. */
@Composable
private fun SimilarFilterChips(selected: FansFilterType, hasSongs: Boolean, onSelect: (FansFilterType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ExpressiveFilterChip(
            selected = selected == FansFilterType.ARTISTS || !hasSongs,
            onClick = { onSelect(FansFilterType.ARTISTS) },
            label = stringResource(R.string.artist_similar_artists)
        )
        if (hasSongs) {
            ExpressiveFilterChip(
                selected = selected == FansFilterType.SONGS,
                onClick = { onSelect(FansFilterType.SONGS) },
                label = stringResource(R.string.artist_similar_songs)
            )
        }
    }
}
