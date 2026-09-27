package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing

import android.content.Intent
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.CollagePattern
import com.theveloper.pixelplay.presentation.components.Beta05CleanInstallDisclaimerDialog
import com.theveloper.pixelplay.presentation.components.StandardScreenTopBar
import com.theveloper.pixelplay.presentation.components.HomeOptionsBottomSheet
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.StatsOverviewCard
import com.theveloper.pixelplay.presentation.components.resolveMainScreenBottomGradientHeight
import com.theveloper.pixelplay.presentation.model.collectRecentlyPlayedSongIds
import com.theveloper.pixelplay.presentation.model.mapRecentlyPlayedSongs
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.LikedSongsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StatsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.presentation.components.VinylSleeveCard
import com.theveloper.pixelplay.presentation.components.RecentTracksList
import com.theveloper.pixelplay.presentation.components.PlaylistsCarousel
import com.theveloper.pixelplay.presentation.components.MixFilterDropdown
import com.theveloper.pixelplay.presentation.components.DailyMixLauncher
import com.theveloper.pixelplay.presentation.components.GeneratedMix
import com.theveloper.pixelplay.presentation.components.CondensedHomeTopBar
import com.theveloper.pixelplay.presentation.components.QuickPicksSection
import com.theveloper.pixelplay.presentation.components.RecentlyPicksSection
import com.theveloper.pixelplay.presentation.components.RecentlyMode
import com.theveloper.pixelplay.presentation.components.SpeedDialRow
import com.theveloper.pixelplay.presentation.components.SpeedDialTile
import com.theveloper.pixelplay.presentation.components.buildSpeedDialTiles
import com.theveloper.pixelplay.presentation.components.buildGeneratedMixes
import com.theveloper.pixelplay.presentation.components.MixFilterSelection
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.theveloper.pixelplay.ui.theme.ExpTitleTypography
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringResource

private const val HomeLoadingPlaceholderMinDurationMillis = 1200L

// Modern HomeScreen with collapsible top bar and staggered grid layout
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    paddingValuesParent: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    onOpenSidebar: () -> Unit,
    onVoiceSearchClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    // DETECTAR MODO BENCHMARK
    val isBenchmarkMode = remember {
        (context as? android.app.Activity)?.intent?.getBooleanExtra("is_benchmark", false) ?: false
    }
    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour < 12) "Morning, Trai." else "Afternoon, Trai."
    }
    val statsViewModel: StatsViewModel = hiltViewModel()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val dailyMixSongs by playerViewModel.dailyMixSongs.collectAsStateWithLifecycle()
    val curatedYourMixSongs by playerViewModel.yourMixSongs.collectAsStateWithLifecycle()
    val homeMixPreviewSongs by playerViewModel.homeMixPreviewSongs.collectAsStateWithLifecycle()
    val playbackHistory by playerViewModel.playbackHistory.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val currentSong = stablePlayerState.currentSong
    val isPlaying = stablePlayerState.isPlaying
    val isShuffleEnabled = stablePlayerState.isShuffleEnabled

    // Sourcing Liked Tracks
    val likedPlaylist = remember(playlistUiState.playlists) {
        playlistUiState.playlists.find {
            it.name.contains("like", ignoreCase = true) || it.name.contains("favor", ignoreCase = true)
        }
    }
    val likedPlaylistId = likedPlaylist?.id
    // Likes no longer live in a "Liked" playlist: they are local favourites, platform likes and
    // favourites-named playlists. Read the same merged list as Your Music, newest like first.
    val likedSongsViewModel: LikedSongsViewModel = hiltViewModel()
    val likedSongs by likedSongsViewModel.likedSongs.collectAsStateWithLifecycle()
    val recentlyLikedSongs by likedSongsViewModel.recentlyLiked.collectAsStateWithLifecycle()

    val usesFallbackHomeMix = remember(curatedYourMixSongs, dailyMixSongs) {
        curatedYourMixSongs.isEmpty() && dailyMixSongs.isEmpty()
    }
    val yourMixSongs = remember(curatedYourMixSongs, dailyMixSongs, homeMixPreviewSongs) {
        when {
            curatedYourMixSongs.isNotEmpty() -> curatedYourMixSongs
            dailyMixSongs.isNotEmpty() -> dailyMixSongs
            else -> homeMixPreviewSongs
        }
    }
    var homePlaceholderRefreshGeneration by rememberSaveable { mutableIntStateOf(0) }
    var hasHomeLoadingMinimumElapsed by rememberSaveable(homePlaceholderRefreshGeneration) {
        mutableStateOf(false)
    }

    LaunchedEffect(homePlaceholderRefreshGeneration, yourMixSongs.isEmpty()) {
        if (yourMixSongs.isEmpty()) {
            hasHomeLoadingMinimumElapsed = false
            delay(HomeLoadingPlaceholderMinDurationMillis)
            hasHomeLoadingMinimumElapsed = true
        } else {
            hasHomeLoadingMinimumElapsed = true
        }
    }

    val shouldShowYourMixLoadingPlaceholder = yourMixSongs.isEmpty() && !hasHomeLoadingMinimumElapsed
    val recentSongIds = remember(playbackHistory) {
        collectRecentlyPlayedSongIds(
            playbackHistory = playbackHistory,
            maxItems = 64
        )
    }
    val recentlyPlayedSourceSongsInitialValue = remember(recentSongIds) {
        if (recentSongIds.isEmpty()) persistentListOf<Song>() else null
    }
    val recentlyPlayedSourceSongs by remember(recentSongIds, playerViewModel) {
        playerViewModel.observeSongs(recentSongIds)
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = recentlyPlayedSourceSongsInitialValue)
    val latestRecentlyPlayedSongs = remember(playbackHistory, recentlyPlayedSourceSongs) {
        val sourceSongs = recentlyPlayedSourceSongs ?: return@remember emptyList()
        mapRecentlyPlayedSongs(
            playbackHistory = playbackHistory,
            songs = sourceSongs,
            maxItems = 64
        )
    }
    // Keep the visible Home snapshot stable and only refresh it once the screen is off-screen.
    var recentlyPlayedSongs by rememberSaveable { mutableStateOf(latestRecentlyPlayedSongs) }
    val latestRecentlyPlayedSongsState = rememberUpdatedState(latestRecentlyPlayedSongs)

    LaunchedEffect(latestRecentlyPlayedSongs, lifecycleOwner) {
        val isHomeVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (recentlyPlayedSongs.isEmpty() || !isHomeVisible) {
            recentlyPlayedSongs = latestRecentlyPlayedSongs
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                recentlyPlayedSongs = latestRecentlyPlayedSongsState.value
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val recentlyPlayedQueue = remember(recentlyPlayedSongs) {
        recentlyPlayedSongs.map { it.song }.toImmutableList()
    }

    val allSongs by playerViewModel.allSongsFlow.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var selectedSongForInfo by remember { mutableStateOf<Song?>(null) }

    // Everything below walks the whole library (de-duplicating, ranking quick picks against
    // liked songs, grouping mixes). It used to run inside remember {} during composition, on
    // the main thread, again every time the play history or favourites changed — the main
    // source of the home-screen lag spikes. It now runs on a background thread and the
    // previous result stays on screen until the new one is ready.
    val homeShelves by androidx.compose.runtime.produceState(
        initialValue = HomeShelves.Empty,
        yourMixSongs, allSongs, recentlyPlayedQueue, favoriteSongIds
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            buildHomeShelves(yourMixSongs, allSongs, recentlyPlayedQueue, favoriteSongIds)
        }
    }
    val mixFilterPool = homeShelves.mixFilterPool
    val quickPickSongs = homeShelves.quickPickSongs
    val speedDialTiles = homeShelves.speedDialTiles

    ReportDrawnWhen {
        yourMixSongs.isNotEmpty() || hasHomeLoadingMinimumElapsed || isBenchmarkMode
    }

    val yourMixSong: String = "Today's Mix for you"

    // Padding inferior si hay canción en reproducción
    val bottomPadding = if (currentSong != null) MiniPlayerHeight else 0.dp
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomGradientHeight = resolveMainScreenBottomGradientHeight(navBarCompactMode)

    var showOptionsBottomSheet by remember { mutableStateOf(false) }
    var cleanInstallDisclaimerDismissedThisSession by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val dm = LocalPixelPlayDarkTheme.current
    val colorScheme = MaterialTheme.colorScheme
    val gradientColorsDark = remember(colorScheme) {
        listOf(
            colorScheme.primaryContainer.copy(alpha = 0.5f),
            Color.Transparent
        ).toImmutableList()
    }
    val gradientColorsLight = remember(colorScheme) {
        listOf(
            colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            Color.Transparent
        ).toImmutableList()
    }
    val gradientColors = if (dm) gradientColorsDark else gradientColorsLight
    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }
    val headerContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)

    val homeStatsOverview by statsViewModel.homeOverview.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    // Drawer state for sidebar
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val shouldShowCleanInstallDisclaimer =
        settingsUiState.beta05CleanInstallDisclaimerDismissed == false &&
            !cleanInstallDisclaimerDismissedThisSession

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.background(brush = gradientBrush).fillMaxSize(),
            topBar = {
                CondensedHomeTopBar(
                    title = greeting,
                    onSettingsClick = {
                        navController.navigateSafely(Screen.Settings.route)
                    },
                    onVoiceSearchClick = onVoiceSearchClick
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(top = innerPadding.calculateTopPadding())
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .background(color = headerContainerColor)
                        .fillMaxSize()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                        shape = AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 34.dp,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBL = 0.dp,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusBR = 0.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusTR = 34.dp,
                            smoothnessAsPercentTL = 60
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent)
                                .verticalScroll(scrollState)
                                .padding(
                                    top = 0.dp,
                                    bottom = paddingValuesParent.calculateBottomPadding()
                                            + 38.dp + bottomPadding
                                )
                        ) {
                            if (yourMixSongs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (shouldShowYourMixLoadingPlaceholder) {
                                        YourMixLoadingPlaceholder()
                                    } else {
                                        YourMixEmptyPlaceholder(
                                            onRefresh = {
                                                homePlaceholderRefreshGeneration++
                                                settingsViewModel.refreshLibrary()
                                                playerViewModel.forceUpdateDailyMix()
                                            }
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    DailyMixLauncher(
                                        songs = yourMixSongs,
                                        filterPool = mixFilterPool,
                                        isPlaying = isPlaying,
                                        currentSongId = currentSong?.id,
                                        onPlayMix = { playerViewModel.startContinuousMix(com.theveloper.pixelplay.data.MixFlavor.NORMAL, yourMixSongs) },
                                        onOpenDailyMixScreen = {
                                            navController.navigateSafely(Screen.DailyMixScreen.route)
                                        },
                                        onPlayGeneratedMix = { mix: GeneratedMix ->
                                            val first = mix.songs.firstOrNull()
                                            if (first != null) {
                                                playerViewModel.playSongs(
                                                    songsToPlay = mix.songs,
                                                    startSong = first,
                                                    queueName = mix.title
                                                )
                                            }
                                        },
                                        onOpenGeneratedMix = { mix: GeneratedMix ->
                                            val pseudoPlaylist = Playlist(
                                                id = "${PlaylistViewModel.GENERATED_MIX_PREFIX}${mix.id}",
                                                name = mix.title,
                                                songIds = mix.songs.map { it.id }
                                            )
                                            PlaylistViewModel.registerTransientPlaylist(pseudoPlaylist, mix.songs)
                                            navController.navigateSafely(
                                                Screen.PlaylistDetail.createRoute(pseudoPlaylist.id)
                                            )
                                        }
                                    )
                                }

                                if (speedDialTiles.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(20.dp))
                                    SpeedDialRow(
                                        tiles = speedDialTiles,
                                        currentSongId = currentSong?.id,
                                        onTileClick = { tile ->
                                            when (tile) {
                                                is SpeedDialTile.MixTile -> {
                                                    val first = tile.mix.songs.firstOrNull()
                                                    if (first != null) {
                                                        playerViewModel.playSongs(
                                                            songsToPlay = tile.mix.songs,
                                                            startSong = first,
                                                            queueName = tile.mix.title
                                                        )
                                                    }
                                                }
                                                is SpeedDialTile.SongTile -> {
                                                    playerViewModel.showAndPlaySong(
                                                        tile.song,
                                                        mixFilterPool,
                                                        "Speed Dial"
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }

                                if (quickPickSongs.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    QuickPicksSection(
                                        songs = quickPickSongs,
                                        currentSongId = currentSong?.id,
                                        isPlaying = isPlaying,
                                        onSongClick = { song ->
                                            playerViewModel.playSongs(
                                                songsToPlay = quickPickSongs,
                                                startSong = song,
                                                queueName = "Quick picks"
                                            )
                                        },
                                        onSongMoreClick = { song ->
                                            selectedSongForInfo = song
                                            showSongInfoBottomSheet = true
                                        }
                                    )
                                }

                                if (recentlyPlayedQueue.isNotEmpty() || recentlyLikedSongs.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    RecentlyPicksSection(
                                        listenedSongs = recentlyPlayedQueue,
                                        likedSongs = recentlyLikedSongs,
                                        currentSongId = currentSong?.id,
                                        isPlaying = isPlaying,
                                        onSongClick = { song, mode ->
                                            val queue = if (mode == RecentlyMode.Listened) recentlyPlayedQueue else recentlyLikedSongs
                                            val queueName = if (mode == RecentlyMode.Listened) "Recently Played" else "Your Likes"
                                            playerViewModel.playSongs(
                                                songsToPlay = queue,
                                                startSong = song,
                                                queueName = queueName
                                            )
                                        },
                                        onSongMoreClick = { song ->
                                            selectedSongForInfo = song
                                            showSongInfoBottomSheet = true
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                PlaylistsCarousel(
                                    likedSongs = likedSongs,
                                    likedPlaylistId = likedPlaylistId ?: PlaylistViewModel.LIKED_PLAYLIST_ID,
                                    navController = navController,
                                    playlists = remember(playlistUiState.playlists, likedPlaylistId) {
                                        playlistUiState.playlists.filter {
                                            !PlaylistViewModel.isSystemPlaylistId(it.id) && it.id != likedPlaylistId
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                if (homeStatsOverview != null) {
                                    StatsOverviewCard(
                                        summary = homeStatsOverview,
                                        onClick = { navController.navigateSafely(Screen.Stats.route) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(bottomGradientHeight)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.2f to Color.Transparent,
                            0.8f to MaterialTheme.colorScheme.surfaceContainerLowest,
                            1.0f to MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
        ) {

        }
    }
    if (showOptionsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsBottomSheet = false },
            sheetState = sheetState
        ) {
            HomeOptionsBottomSheet(
                onNavigateToMashup = {
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showOptionsBottomSheet = false
                            navController.navigateSafely(Screen.DJSpace.route)
                        }
                    }
                }
            )
        }
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val songForInfo = selectedSongForInfo!!
        val isFav = remember(songForInfo.id, favoriteSongIds) {
            favoriteSongIds.contains(songForInfo.id)
        }
        SongInfoBottomSheet(
            song = songForInfo,
            isFavorite = isFav,
            onToggleFavorite = {
                playerViewModel.toggleFavoriteSpecificSong(songForInfo)
            },
            onDismiss = {
                showSongInfoBottomSheet = false
                selectedSongForInfo = null
            },
            onPlaySong = {
                playerViewModel.showAndPlaySong(songForInfo)
            },
            onAddToQueue = {
                playerViewModel.addSongToQueue(songForInfo)
            },
            onAddNextToQueue = {
                playerViewModel.addSongNextToQueue(songForInfo)
            },
            onAddToPlayList = {
                showSongInfoBottomSheet = false
            },
            onDeleteFromDevice = { act, song, callback ->
                playerViewModel.deleteFromDevice(act, song, callback)
            },
            onNavigateToAlbum = {
                navController.navigateSafely(Screen.AlbumDetail.createRoute(songForInfo.albumId))
                showSongInfoBottomSheet = false
            },
            onNavigateToArtist = {
                navController.navigateSafely(Screen.ArtistDetail.createRouteForSong(songForInfo))
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
                songForInfo.genre?.let {
                    navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                }
                showSongInfoBottomSheet = false
            },
            onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                playerViewModel.editSongMetadata(
                    songForInfo,
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
                showSongInfoBottomSheet = false
            },
            removeFromListTrigger = {}
        )
    }

    if (shouldShowCleanInstallDisclaimer) {
        Beta05CleanInstallDisclaimerDialog(
            onDismiss = { dontShowAgain ->
                cleanInstallDisclaimerDismissedThisSession = true
                if (dontShowAgain) {
                    settingsViewModel.setBeta05CleanInstallDisclaimerDismissed(true)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YourMixLoadingPlaceholder() {
    // Same footprint as the loaded shelf, so the page doesn't jump when the mix arrives.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(256.dp)
    ) {
        com.theveloper.pixelplay.presentation.components.DelayedSkeleton {
            com.theveloper.pixelplay.presentation.components.ShelfSkeleton(cardSize = 170.dp)
        }
    }
}

@Composable
private fun YourMixEmptyPlaceholder(
    onRefresh: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 256.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(76.dp),
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 28.dp,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusBR = 28.dp,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusBL = 28.dp,
                    smoothnessAsPercentBR = 60,
                    cornerRadiusTR = 28.dp,
                    smoothnessAsPercentBL = 60,
                ),
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_empty_placeholder_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.home_empty_placeholder_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            FilledTonalButton(
                onClick = onRefresh,
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 22.dp,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusBR = 22.dp,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusBL = 22.dp,
                    smoothnessAsPercentBR = 60,
                    cornerRadiusTR = 22.dp,
                    smoothnessAsPercentBL = 60,
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.home_empty_placeholder_refresh))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YourMixHeader(
    song: String,
    isShuffleEnabled: Boolean = false,
    onPlayShuffled: () -> Unit
) {
    val buttonCorners = 68.dp
    val colors = MaterialTheme.colorScheme

    val titleStyle = rememberYourMixTitleStyle()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(256.dp)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 12.dp)
        ) {
            // Your Mix Title
            Text(
                text = stringResource(R.string.home_your_mix_title),
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
            )

            // Artist/Song subtitle
            Text(
                text = song,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        // Play Button - color changes based on shuffle state
        LargeExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp),
            onClick = onPlayShuffled,
            containerColor = if (isShuffleEnabled) colors.primary else colors.tertiaryContainer,
            contentColor = if (isShuffleEnabled) colors.onPrimary else colors.onTertiaryContainer,
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = buttonCorners,
                smoothnessAsPercentTR = 60,
                cornerRadiusBR = buttonCorners,
                smoothnessAsPercentTL = 60,
                cornerRadiusBL = buttonCorners,
                smoothnessAsPercentBR = 60,
                cornerRadiusTR = buttonCorners,
                smoothnessAsPercentBL = 60,
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_shuffle_24),
                contentDescription = stringResource(R.string.common_shuffle_play),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}


// SongListItem (modificado para aceptar parámetros individuales)
@Composable
fun SongListItemFavs(
    modifier: Modifier = Modifier,
    cardCorners: Dp = 12.dp,
    title: String,
    artist: String,
    albumArtUrl: String?,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isCurrentSong) colors.primaryContainer.copy(alpha = 0.46f) else colors.surfaceContainer
    val contentColor = if (isCurrentSong) colors.primary else colors.onSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(cardCorners),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(0.9f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = albumArtUrl,
                    contentDescription = stringResource(R.string.common_album_art_for_title, title),
                    contentScale = ContentScale.Crop,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                        color = contentColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist, style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            if (isCurrentSong) {
                PlayingEqIcon(
                    modifier = Modifier
                        .weight(0.1f)
                        .padding(start = 8.dp)
                        .size(width = 18.dp, height = 16.dp), // similar al tamaño del ícono
                    color = colors.primary,
                    isPlaying = isPlaying  // o conectalo a tu estado real de reproducción
                )
            }
        }
    }
}

// Wrapper Composable for SongListItemFavs to isolate state observation
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SongListItemFavsWrapper(
    song: Song,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect the stablePlayerState once
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    // Derive isThisSongPlaying using remember
    val isThisSongPlaying = remember(song.id, stablePlayerState.currentSong?.id, stablePlayerState.isPlaying) {
        song.id == stablePlayerState.currentSong?.id
    }

    // Call the presentational composable
    SongListItemFavs(
        modifier = modifier,
        cardCorners = 0.dp,
        title = song.title,
        artist = song.displayArtist,
        albumArtUrl = song.albumArtUriString,
        isPlaying = stablePlayerState.isPlaying,
        isCurrentSong = song.id == stablePlayerState.currentSong?.id,
        onClick = onClick
    )
}


@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberYourMixTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(636),
                        FontVariation.width(152f),
                        FontVariation.Setting("ROND", 50f),
                        FontVariation.Setting("XTRA", 520f),
                        FontVariation.Setting("YOPQ", 90f),
                        FontVariation.Setting("YTLC", 505f)
                    )
                )
            ),
            fontWeight = FontWeight(760),
            fontSize = 64.sp,
            lineHeight = 62.sp
        )
    }
}

/** Library-wide lists the home screen derives from the song pool, built off the main thread. */
@androidx.compose.runtime.Immutable
private data class HomeShelves(
    val mixFilterPool: kotlinx.collections.immutable.ImmutableList<Song>,
    val quickPickSongs: List<Song>,
    val speedDialTiles: kotlinx.collections.immutable.ImmutableList<SpeedDialTile>
) {
    companion object {
        val Empty = HomeShelves(persistentListOf(), emptyList(), persistentListOf())
    }
}

private fun buildHomeShelves(
    yourMixSongs: List<Song>,
    allSongs: List<Song>,
    recentlyPlayed: List<Song>,
    favoriteSongIds: Set<String>
): HomeShelves {
    val mixFilterPool = (yourMixSongs + allSongs).distinctBy { it.id }.toImmutableList()
    val quickPickSongs = com.theveloper.pixelplay.data.personalizedQuickPicks(
        mixFilterPool, recentlyPlayed, favoriteSongIds
    )
    val personalizedPool = (quickPickSongs + mixFilterPool).distinctBy { it.id }
    val generatedMixes = buildGeneratedMixes(personalizedPool, MixFilterSelection())
    return HomeShelves(
        mixFilterPool = mixFilterPool,
        quickPickSongs = quickPickSongs,
        speedDialTiles = buildSpeedDialTiles(generatedMixes, quickPickSongs)
    )
}
