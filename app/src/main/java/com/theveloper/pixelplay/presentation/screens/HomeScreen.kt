package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.components.offlineVeil
import com.theveloper.pixelplay.presentation.components.songStartFeedback
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
import com.theveloper.pixelplay.presentation.components.HomeOptionsBottomSheet
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.MinFreshFindsToShow
import com.theveloper.pixelplay.presentation.components.freshFindsWeekStamp
import com.theveloper.pixelplay.presentation.components.buildFreshFinds
import com.theveloper.pixelplay.presentation.components.FreshFindsStore
import com.theveloper.pixelplay.presentation.components.FreshFindsSection
import com.theveloper.pixelplay.presentation.components.FreshFind
import com.theveloper.pixelplay.presentation.components.OnThisDaySong
import com.theveloper.pixelplay.presentation.components.computeHomeHistoryPicks
import com.theveloper.pixelplay.presentation.components.HomeHistoryPicks
import com.theveloper.pixelplay.presentation.components.moodFallbackCaption
import com.theveloper.pixelplay.presentation.components.rankSongsByMood
import com.theveloper.pixelplay.presentation.components.buildHomeMoodScores
import com.theveloper.pixelplay.presentation.components.homeMoodOptions
import com.theveloper.pixelplay.presentation.components.HomeMoodScores
import com.theveloper.pixelplay.presentation.components.HomeMoodChips
import com.theveloper.pixelplay.presentation.components.orderSpeedDialItems
import com.theveloper.pixelplay.presentation.components.SpeedDialPinStore
import com.theveloper.pixelplay.presentation.components.SpeedDialGridItem
import com.theveloper.pixelplay.presentation.components.SpeedDialGrid
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
import com.theveloper.pixelplay.presentation.viewmodel.HomeDiscoveryViewModel
import com.theveloper.pixelplay.presentation.components.VinylSleeveCard
import com.theveloper.pixelplay.presentation.components.RecentTracksList
import com.theveloper.pixelplay.presentation.components.MixFilterDropdown
import com.theveloper.pixelplay.presentation.components.DailyMixLauncher
import com.theveloper.pixelplay.presentation.components.GeneratedMix
import com.theveloper.pixelplay.presentation.components.QuickPicksSection
import com.theveloper.pixelplay.presentation.components.RecentlyPicksSection
import com.theveloper.pixelplay.presentation.components.RecentlyMode
import com.theveloper.pixelplay.presentation.components.SpeedDialTile
import com.theveloper.pixelplay.presentation.components.buildSpeedDialTiles
import com.theveloper.pixelplay.presentation.components.buildGeneratedMixes
import com.theveloper.pixelplay.presentation.components.MixFilterSelection
import com.theveloper.pixelplay.presentation.components.MixFilterSelectionSaver
import com.theveloper.pixelplay.presentation.components.MixFilterOptions
import com.theveloper.pixelplay.presentation.components.DailyMixSheet
import com.theveloper.pixelplay.presentation.components.DailyMixSheetModel
import com.theveloper.pixelplay.presentation.components.buildDailyMixSheetModel
import com.theveloper.pixelplay.presentation.components.applyMixFilters
import com.theveloper.pixelplay.presentation.components.buildMixFilterOptions
import com.theveloper.pixelplay.presentation.components.BottomDeckSheetSlot
import com.theveloper.pixelplay.presentation.components.CustomMoodPrefix
import com.theveloper.pixelplay.presentation.components.HomeCustomMoodStore
import com.theveloper.pixelplay.presentation.components.MoodRanked
import com.theveloper.pixelplay.presentation.components.moodQuickPicks
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
    onOpenSidebar: () -> Unit
) {
    val context = LocalContext.current
    // DETECTAR MODO BENCHMARK
    val isBenchmarkMode = remember {
        (context as? android.app.Activity)?.intent?.getBooleanExtra("is_benchmark", false) ?: false
    }
    // Time of day for the history picks; re-read on every return to Home.
    var daypart by remember { mutableStateOf(com.theveloper.pixelplay.presentation.components.Daypart.now()) }
    val greetingLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(greetingLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                daypart = com.theveloper.pixelplay.presentation.components.Daypart.now()
            }
        }
        greetingLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { greetingLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val statsViewModel: StatsViewModel = hiltViewModel()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val dailyMixSongs by playerViewModel.dailyMixSongs.collectAsStateWithLifecycle()
    // Library artists, used for the Daily Mix artist row photos (custom image first, then Deezer).
    val libraryArtists by playerViewModel.artistsFlow.collectAsStateWithLifecycle()
    val artistImageByName = remember(libraryArtists) {
        libraryArtists.asSequence()
            .mapNotNull { artist -> artist.effectiveImageUrl?.let { artist.name.trim().lowercase() to it } }
            .toMap()
    }
    val curatedYourMixSongs by playerViewModel.yourMixSongs.collectAsStateWithLifecycle()
    val homeMixPreviewSongs by playerViewModel.homeMixPreviewSongs.collectAsStateWithLifecycle()
    val playbackHistory by playerViewModel.playbackHistory.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    remember(context) { SpeedDialPinStore.load(context) }
    val speedDialPins by SpeedDialPinStore.pins.collectAsStateWithLifecycle()
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
    // Live: a song you play shows up in Recent straight away (it used to be frozen while Home
    // was on screen and only refreshed once you left it). Liked is live the same way.
    val recentlyPlayedSongs = latestRecentlyPlayedSongs

    val recentlyPlayedQueue = remember(recentlyPlayedSongs) {
        recentlyPlayedSongs.map { it.song }.toImmutableList()
    }

    val allSongs by playerViewModel.allSongsFlow.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    // Hold a Home Shuffle button: which shuffle the Soon sheet is scheduling (null = closed).
    var shuffleSoonFor by remember { mutableStateOf<HomeShuffleSoon?>(null) }
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

    // Mood chips (For you · Chill · Hype …): one Home-wide lens. Which moods get a chip, and the
    // per-song scores for the picked one, are computed off the main thread.
    var selectedMoodId by rememberSaveable { mutableStateOf<String?>(null) }
    // Moods typed into the chip row (swipe right at its start); kept until removed.
    var customMoods by remember { mutableStateOf(HomeCustomMoodStore.load(context)) }
    val moodOptions by androidx.compose.runtime.produceState(
        initialValue = emptyList<com.theveloper.pixelplay.presentation.library.VibeFilter>(),
        mixFilterPool
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            homeMoodOptions(mixFilterPool)
        }
    }
    // A picked mood that's no longer offered (library changed) quietly goes back to "For you".
    LaunchedEffect(moodOptions, customMoods, selectedMoodId) {
        val id = selectedMoodId
        if (id != null && !id.startsWith(CustomMoodPrefix) && moodOptions.isNotEmpty() && moodOptions.none { it.id == id }) {
            selectedMoodId = null
        }
        if (id != null && id.startsWith(CustomMoodPrefix) && customMoods.none { it.id == id }) {
            selectedMoodId = null
        }
    }
    val moodScores by androidx.compose.runtime.produceState<HomeMoodScores?>(
        initialValue = null,
        selectedMoodId, moodOptions, customMoods, mixFilterPool
    ) {
        val mood = (customMoods + moodOptions).firstOrNull { it.id == selectedMoodId }
        value = if (mood == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            buildHomeMoodScores(mixFilterPool, mood)
        }
    }
    // Quick picks follow the mood properly: your picks that fit it, topped up from the whole
    // library with the best fits, instead of just hiding the ones that don't.
    val moodQuickPicks by androidx.compose.runtime.produceState(
        initialValue = MoodRanked(quickPickSongs, fellBack = false),
        quickPickSongs, mixFilterPool, moodScores
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            moodQuickPicks(quickPickSongs, mixFilterPool, moodScores)
        }
    }
    // The mood reaches every section, the Daily Mix card included.
    val moodDailyMix = remember(yourMixSongs, moodScores) { rankSongsByMood(yourMixSongs, moodScores) }
    val moodMixFilterPool = remember(mixFilterPool, moodScores) { rankSongsByMood(mixFilterPool, moodScores) }
    // Stable list instances, so the Daily Mix card doesn't rebuild its filters on every recomposition.
    val moodDailyMixSongs = remember(moodDailyMix) { moodDailyMix.items.toImmutableList() }
    val moodMixFilterPoolSongs = remember(moodMixFilterPool) { moodMixFilterPool.items.toImmutableList() }

    // ---- Daily Mix sheet ------------------------------------------------------------------------
    // The card opens a docked sheet (the mini player and bottom bar move up on top of it) with the
    // full Daily Mix, the mixes made from the filters, and the filter chips.
    var dailyMixSheetOpen by remember { mutableStateOf(false) }
    var mixSelection by rememberSaveable(saver = MixFilterSelectionSaver) { mutableStateOf(MixFilterSelection()) }
    // The sheet's pool reaches past the device: the library plus everything in Your Music that's
    // streamed (liked / saved online songs), following the mood like the rest of Home. Online
    // copies of a song already on the device are left out (matched by ISRC or title + artist).
    val dailyMixPool by androidx.compose.runtime.produceState(
        initialValue = moodMixFilterPoolSongs,
        moodMixFilterPoolSongs, likedSongs, moodScores
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val libraryIds = moodMixFilterPoolSongs.mapTo(HashSet()) { it.id }
            val libraryIdentities = moodMixFilterPoolSongs.mapTo(HashSet()) { com.theveloper.pixelplay.data.mixIdentity(it) }
            val likedForMood = rankSongsByMood(likedSongs, moodScores).let { if (it.fellBack) emptyList() else it.items }
            val streamed = likedForMood.filter { it.id !in libraryIds && libraryIdentities.add(com.theveloper.pixelplay.data.mixIdentity(it)) }
            (moodMixFilterPoolSongs + streamed).toImmutableList()
        }
    }
    // With a filter set, online songs that fit are fetched and mixed in (spinner meanwhile; offline
    // or slow = just the library results).
    var onlineMixPicks by remember { mutableStateOf<List<Song>>(emptyList()) }
    // Songs found online *for* a filter value the library barely has (e.g. a typed artist).
    var filterSearchPicks by remember { mutableStateOf<List<Song>>(emptyList()) }
    var fetchingOnlineMix by remember { mutableStateOf(false) }
    val mixOnlineSearch: com.theveloper.pixelplay.presentation.viewmodel.MixOnlineSearchViewModel =
        hiltViewModel()
    val mixSheetOnline by mixOnlineSearch.isOnline.collectAsStateWithLifecycle()
    var loadingMixArtist by remember { mutableStateOf<String?>(null) }
    val mixArtistScope = rememberCoroutineScope()
    LaunchedEffect(mixSelection, dailyMixSheetOpen, dailyMixPool) {
        if (!dailyMixSheetOpen || mixSelection.isEmpty) {
            onlineMixPicks = emptyList()
            filterSearchPicks = emptyList()
            fetchingOnlineMix = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(350) // wait for the user to finish picking
        fetchingOnlineMix = true
        try {
            val (seeds, excluded) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                applyMixFilters(dailyMixPool, mixSelection).take(5) to
                    dailyMixPool.mapTo(HashSet()) { com.theveloper.pixelplay.data.mixIdentity(it) }
            }
            val picks = playerViewModel.onlineMixPicks(seeds, excluded)
            onlineMixPicks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                applyMixFilters(picks, mixSelection)
            }
            // Few library matches (a value that isn't in the library): search online for the
            // value itself, so the mix isn't empty.
            val libraryMatches = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                applyMixFilters(dailyMixPool, mixSelection).size + onlineMixPicks.size
            }
            filterSearchPicks = if (libraryMatches < MinLibraryMatchesBeforeOnlineSearch) {
                val target = OnlineSearchFieldOrder.firstNotNullOfOrNull { field ->
                    mixSelection.valueOf(field)?.let { field to it }
                }
                if (target == null) emptyList()
                else playerViewModel.filterOnlinePicks(mixOnlineSearch.songsForFilter(target.first, target.second))
                    .filterNot { song -> excluded.contains(com.theveloper.pixelplay.data.mixIdentity(song)) }
            } else emptyList()
        } finally {
            fetchingOnlineMix = false
        }
    }
    // Chip options walk the whole pool: built once per pool, off the main thread.
    val mixFilterOptions by androidx.compose.runtime.produceState(
        initialValue = MixFilterOptions(),
        dailyMixPool
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            buildMixFilterOptions(dailyMixPool)
        }
    }
    val dailyMixSheetModel by androidx.compose.runtime.produceState(
        initialValue = DailyMixSheetModel.Empty,
        dailyMixPool, onlineMixPicks, filterSearchPicks, mixSelection, mixFilterOptions
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val pool = if (onlineMixPicks.isEmpty()) dailyMixPool else (dailyMixPool + onlineMixPicks).distinctBy { it.id }
            buildDailyMixSheetModel(pool, mixSelection, mixFilterOptions, extraMatches = filterSearchPicks)
        }
    }

    // ---- Discovery: Back again (Forgotten / On this day) and Fresh Finds -----------------------
    // These read months of plays, so they use their own deep history rather than the capped list
    // behind Recently played. All the work happens on a background thread.
    val homeDiscoveryViewModel: HomeDiscoveryViewModel = hiltViewModel()
    val deepHistory by homeDiscoveryViewModel.deepHistory.collectAsStateWithLifecycle()
    val historyLoaded = deepHistory != null
    val historyPicks by androidx.compose.runtime.produceState(
        initialValue = HomeHistoryPicks(daypart),
        deepHistory, daypart
    ) {
        val history = deepHistory ?: return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            computeHomeHistoryPicks(history)
        }
    }
    val historyPickIds = historyPicks.allIds
    val historySongsFlow = remember(historyPickIds, playerViewModel) { playerViewModel.observeSongs(historyPickIds) }
    val historySongs by historySongsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val historySongById = remember(historySongs) { historySongs.associateBy { it.id } }

    val forgottenSongs = remember(historyPicks, historySongById, moodScores) {
        rankSongsByMood(historyPicks.forgottenIds.mapNotNull { historySongById[it] }, moodScores)
    }
    val onThisDaySongs = remember(historyPicks, historySongById) {
        historyPicks.onThisDay.mapNotNull { (id, year) -> historySongById[id]?.let { OnThisDaySong(it, year) } }
    }
    val moodRecentlyPlayed = remember(recentlyPlayedQueue, moodScores) { rankSongsByMood(recentlyPlayedQueue, moodScores) }
    val moodRecentlyLiked = remember(recentlyLikedSongs, moodScores) { rankSongsByMood(recentlyLikedSongs, moodScores) }

    // Fresh Finds: friends' plays you haven't heard + library artists you've never played.
    val friendsViewModel: com.theveloper.pixelplay.presentation.viewmodel.FriendsViewModel = hiltViewModel()
    val friendsDiscovery by friendsViewModel.discovery.collectAsStateWithLifecycle()
    // Friends' pictures next to the Playlists title filter the grid to their playlists.
    val homeFriends by friendsViewModel.friends.collectAsStateWithLifecycle()
    val playlistFriendFilters = remember(homeFriends) {
        homeFriends.map { friend ->
            com.theveloper.pixelplay.presentation.components.PlaylistFriendFilter(
                id = friend.id,
                name = friend.name,
                avatarUrl = friend.avatarUrl,
                playlistIds = friend.playlists.mapNotNullTo(HashSet()) { it.playlistId }
            )
        }.filter { it.playlistIds.isNotEmpty() }
    }
    val freshWeek = remember(daypart) { freshFindsWeekStamp() }
    var freshHidden by remember(freshWeek) { mutableStateOf(FreshFindsStore.hidden(context, freshWeek)) }
    var freshIsNew by remember(freshWeek) { mutableStateOf(FreshFindsStore.isNew(context, freshWeek)) }
    val freshFinds by androidx.compose.runtime.produceState(
        initialValue = emptyList<FreshFind>(),
        friendsDiscovery, allSongs, historyPicks, freshHidden, freshWeek, historyLoaded
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val friendFinds = friendsDiscovery.songs.map { d ->
                FreshFind(d.song, "${d.friend.name} played this", d.friend.avatarUrl)
            }
            val finds = buildFreshFinds(
                friendFinds = friendFinds,
                library = allSongs,
                everPlayedIds = historyPicks.everPlayedIds,
                hiddenIds = freshHidden,
                weekStamp = freshWeek,
                cachedIds = FreshFindsStore.cachedIds(context, freshWeek)
            )
            // Save this week's library picks once real history is in (so already-played songs
            // aren't locked in from the empty placeholder). Friend finds aren't saved.
            if (historyLoaded) {
                val friendIds = friendFinds.mapTo(HashSet()) { it.song.id }
                val libraryIds = finds.map { it.song.id }.filter { it !in friendIds }
                if (libraryIds.isNotEmpty()) FreshFindsStore.saveIds(context, freshWeek, libraryIds)
            }
            finds
        }
    }
    // Fresh Finds follow the mood too: this week's finds that fit it, topped up with songs you've
    // never played that fit it, so the shelf changes with the chip instead of staying put.
    val moodFreshFinds by androidx.compose.runtime.produceState(
        initialValue = freshFinds,
        freshFinds, moodScores, allSongs, historyPicks, freshHidden, freshWeek
    ) {
        val mood = moodScores
        value = if (mood == null) freshFinds else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val matching = freshFinds.filter { mood.matches(it.song) }.sortedByDescending { mood.score(it.song) }
            if (matching.size >= MinFreshFindsToShow) matching
            else {
                val extra = buildFreshFinds(
                    friendFinds = emptyList(),
                    library = allSongs.filter { mood.matches(it) },
                    everPlayedIds = historyPicks.everPlayedIds,
                    hiddenIds = freshHidden,
                    weekStamp = freshWeek + mood.mood.id
                )
                val combined = (matching + extra).distinctBy { it.song.id }
                if (combined.size >= MinFreshFindsToShow) combined.take(30)
                else (combined + freshFinds.sortedByDescending { mood.score(it.song) }).distinctBy { it.song.id }.take(30)
            }
        }
    }
    // Speed dial keeps its tiles; matching songs and mixes just move to the front.
    val moodSpeedDialTiles = remember(speedDialTiles, moodScores) {
        val mood = moodScores ?: return@remember speedDialTiles
        speedDialTiles.sortedByDescending { tile ->
            when (tile) {
                is SpeedDialTile.SongTile -> if (mood.matches(tile.song)) 1.0 else 0.0
                is SpeedDialTile.MixTile -> tile.mix.songs.count { mood.matches(it) }.toDouble() / tile.mix.songs.size.coerceAtLeast(1)
            }
        }.toImmutableList()
    }

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
                // Shared tab header: "Home" + round settings button (no greeting, no mic).
                com.theveloper.pixelplay.presentation.components.TabHeader(
                    title = "Home",
                    onSettingsClick = {
                        navController.navigateSafely(Screen.Settings.route)
                    },
                    // "Online" / "Offline" peeks out from behind the settings button on entry.
                    actions = {
                        com.theveloper.pixelplay.presentation.components.ConnectivityPeek(
                            replayKey = Unit,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(top = innerPadding.calculateTopPadding())
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
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
                                        songs = moodDailyMixSongs,
                                        moodLabel = moodScores?.takeIf { !moodDailyMix.fellBack }?.mood?.label,
                                        topRow = {
                                            HomeMoodChips(
                                                moods = moodOptions,
                                                customMoods = customMoods,
                                                selectedId = selectedMoodId,
                                                onSelect = { selectedMoodId = it },
                                                onCustomMood = { text ->
                                                    customMoods = HomeCustomMoodStore.add(context, text)
                                                    selectedMoodId = HomeCustomMoodStore.idFor(text)
                                                },
                                                onRemoveCustomMood = { id ->
                                                    customMoods = HomeCustomMoodStore.remove(context, id)
                                                    if (selectedMoodId == id) selectedMoodId = null
                                                }
                                            )
                                        },
                                        isPlaying = isPlaying,
                                        sheetOpen = dailyMixSheetOpen,
                                        selection = mixSelection,
                                        // The mix button mixes: shuffled every time rather than starting from song one.
                                        onPlayMix = { playerViewModel.startContinuousMix(com.theveloper.pixelplay.data.MixFlavor.NORMAL, moodDailyMix.items.shuffled()) },
                                        onOpenSheet = { dailyMixSheetOpen = !dailyMixSheetOpen }
                                    )
                                }

                                BottomDeckSheetSlot(
                                    open = dailyMixSheetOpen,
                                    onDismiss = { dailyMixSheetOpen = false }
                                ) {
                                    DailyMixSheet(
                                        model = dailyMixSheetModel,
                                        selection = mixSelection,
                                        isFetchingOnline = fetchingOnlineMix,
                                        dailyMixSongs = moodDailyMixSongs,
                                        currentSongId = currentSong?.id,
                                        artistImageFor = { name -> artistImageByName[name.trim().lowercase()] },
                                        onSelectionChange = { mixSelection = it },
                                        onOpenFullDailyMix = {
                                            dailyMixSheetOpen = false
                                            navController.navigateSafely(Screen.DailyMixScreen.route)
                                        },
                                        onPlayFiltered = {
                                            val songs = if (mixSelection.isEmpty) moodDailyMix.items else dailyMixSheetModel.filtered
                                            if (songs.isNotEmpty()) {
                                                playerViewModel.startContinuousMix(com.theveloper.pixelplay.data.MixFlavor.NORMAL, songs.shuffled())
                                            }
                                        },
                                        onPlayGeneratedMix = { mix: GeneratedMix ->
                                            val shuffled = mix.songs.shuffled()
                                            val first = shuffled.firstOrNull()
                                            if (first != null) {
                                                playerViewModel.playSongs(
                                                    songsToPlay = shuffled,
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
                                            dailyMixSheetOpen = false
                                            navController.navigateSafely(
                                                Screen.PlaylistDetail.createRoute(pseudoPlaylist.id)
                                            )
                                        },
                                        onlineSuggest = { field, query -> mixOnlineSearch.suggest(field, query) },
                                        searchesOnline = { field -> mixOnlineSearch.searchesOnline(field) },
                                        isOnline = mixSheetOnline,
                                        loadingArtist = loadingMixArtist,
                                        onPlayArtistByName = { name ->
                                            // An artist with no mix here: library songs by them, topped
                                            // up with their songs found online.
                                            if (loadingMixArtist == null) mixArtistScope.launch {
                                                loadingMixArtist = name
                                                try {
                                                    val key = com.theveloper.pixelplay.presentation.viewmodel.normalizeMixQuery(name)
                                                    val library = dailyMixPool.filter {
                                                        com.theveloper.pixelplay.presentation.viewmodel.normalizeMixQuery(it.displayArtist).contains(key)
                                                    }
                                                    val online = if (library.size >= MinLibraryMatchesBeforeOnlineSearch) emptyList()
                                                    else playerViewModel.filterOnlinePicks(
                                                        mixOnlineSearch.songsForFilter(
                                                            com.theveloper.pixelplay.presentation.components.MixFilterField.Artist, name
                                                        )
                                                    )
                                                    val songs = (library + online).distinctBy { it.id }.shuffled()
                                                    songs.firstOrNull()?.let { first ->
                                                        playerViewModel.playSongs(songsToPlay = songs, startSong = first, queueName = "$name mix")
                                                    }
                                                } finally {
                                                    loadingMixArtist = null
                                                }
                                            }
                                        },
                                        onOpenArtist = { name ->
                                            dailyMixSheetOpen = false
                                            navController.navigateSafely(Screen.ArtistDetail.createRouteForName(name))
                                        }
                                    )
                                }

                                // Playlists: Your Music, your playlists, mixes and songs in one dense 3×2
                                // grid, with friends' pictures as filters next to the title.
                                val speedDialItems = remember(likedSongs, playlistUiState.playlists, likedPlaylistId, moodSpeedDialTiles, speedDialPins, moodScores, homeFriends) {
                                    val ownPlaylists = playlistUiState.playlists.filter {
                                        // "Favourites"-style playlists are part of Your Music, so they aren't repeated.
                                        !PlaylistViewModel.isSystemPlaylistId(it.id) && it.id != likedPlaylistId &&
                                            !com.theveloper.pixelplay.presentation.library.isFavoritesPlaylistName(it.name)
                                    }.let { playlists ->
                                        // With a mood picked, playlists that fit it best come first.
                                        val mood = moodScores ?: return@let playlists
                                        playlists.sortedByDescending { pl ->
                                            if (pl.songIds.isEmpty()) 0.0
                                            else pl.songIds.count {
                                                (mood.scores[it] ?: 0.0) >= com.theveloper.pixelplay.presentation.library.MusicVibeFilters.MATCH_THRESHOLD
                                            }.toDouble() / pl.songIds.size
                                        }
                                    }
                                    // Friends' public playlists that aren't saved yet (saved ones are
                                    // already in ownPlaylists).
                                    val friendPublic = homeFriends.flatMap { friend ->
                                        friend.playlists.filter { it.playlistId == null }.map { p ->
                                            SpeedDialGridItem.FriendPlaylistItem(
                                                friendId = friend.id,
                                                friendName = friend.name,
                                                remoteId = p.remoteId,
                                                source = p.source,
                                                title = p.title,
                                                coverUrl = p.coverUrl,
                                                songCount = p.songCount
                                            )
                                        }
                                    }
                                    val all = buildList<SpeedDialGridItem> {
                                        add(SpeedDialGridItem.YourMusic(likedSongs.toImmutableList()))
                                        ownPlaylists.forEach { add(SpeedDialGridItem.PlaylistItem(it)) }
                                        addAll(friendPublic)
                                        moodSpeedDialTiles.forEach { add(SpeedDialGridItem.TileItem(it)) }
                                    }
                                    orderSpeedDialItems(all, speedDialPins)
                                }
                                Spacer(modifier = Modifier.height(HomeSectionSpacing))
                                SpeedDialGrid(
                                    items = speedDialItems,
                                    title = "Playlists",
                                    friendFilters = playlistFriendFilters,
                                    pinnedKeys = speedDialPins,
                                    currentSongId = currentSong?.id,
                                    onTogglePin = { item -> SpeedDialPinStore.toggle(context, item.key) },
                                    onItemClick = { item ->
                                        when (item) {
                                            is SpeedDialGridItem.YourMusic ->
                                                navController.navigateSafely(Screen.YourMusic.route)
                                            is SpeedDialGridItem.PlaylistItem ->
                                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.playlist.id))
                                            is SpeedDialGridItem.FriendPlaylistItem -> {
                                                // Saved on first open, then opened like any playlist.
                                                val friend = homeFriends.firstOrNull { it.id == item.friendId }
                                                val playlist = friend?.playlists?.firstOrNull { it.remoteId == item.remoteId && it.source == item.source }
                                                if (friend != null && playlist != null) {
                                                    friendsViewModel.savePublicPlaylist(friend, playlist) { saved ->
                                                        navController.navigateSafely(Screen.PlaylistDetail.createRoute(saved))
                                                    }
                                                }
                                            }
                                            is SpeedDialGridItem.TileItem -> when (val tile = item.tile) {
                                                is SpeedDialTile.MixTile -> {
                                                    val shuffled = tile.mix.songs.shuffled()
                                                    val first = shuffled.firstOrNull()
                                                    if (first != null) {
                                                        playerViewModel.playSongs(
                                                            songsToPlay = shuffled,
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
                                    }
                                )

                                if (quickPickSongs.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(HomeSectionSpacing))
                                    val quickPickList = moodQuickPicks.items
                                    QuickPicksSection(
                                        songs = quickPickList,
                                        subtitle = if (moodQuickPicks.fellBack) moodFallbackCaption(moodScores) else null,
                                        contentKey = moodScores?.mood?.id,
                                        currentSongId = currentSong?.id,
                                        isPlaying = isPlaying,
                                        onSongClick = { song ->
                                            playerViewModel.playSongs(
                                                songsToPlay = quickPickList,
                                                startSong = song,
                                                queueName = moodScores?.takeIf { !moodQuickPicks.fellBack }
                                                    ?.let { "Quick picks · ${it.mood.label}" } ?: "Quick picks"
                                            )
                                        },
                                        onSongMoreClick = { song ->
                                            selectedSongForInfo = song
                                            showSongInfoBottomSheet = true
                                        },
                                        onShuffleClick = {
                                            val shuffled = quickPickList.shuffled()
                                            shuffled.firstOrNull()?.let { first ->
                                                playerViewModel.playSongs(
                                                    songsToPlay = shuffled,
                                                    startSong = first,
                                                    queueName = moodScores?.takeIf { !moodQuickPicks.fellBack }
                                                        ?.let { "Quick picks · ${it.mood.label}" } ?: "Quick picks"
                                                )
                                            }
                                        },
                                        onShuffleLongClick = { shuffleSoonFor = HomeShuffleSoon.QUICK_PICKS }
                                    )
                                }

                                // Fresh Finds: this week's discovery drop (replaces "New from friends").
                                if (moodFreshFinds.size >= MinFreshFindsToShow) {
                                    Spacer(modifier = Modifier.height(HomeSectionSpacing))
                                }
                                FreshFindsSection(
                                    finds = moodFreshFinds,
                                    isNew = freshIsNew,
                                    currentSongId = currentSong?.id,
                                    isPlaying = isPlaying,
                                    onShuffle = {
                                        val songs = moodFreshFinds.map { it.song }.shuffled()
                                        if (songs.isNotEmpty()) {
                                            friendsViewModel.prepareDiscoverySongs() // tags friend plays for queue attribution
                                            // Keeps going: the shuffled finds play first, then Smart Mix keeps
                                            // adding new discoveries like them instead of the queue ending.
                                            playerViewModel.startContinuousMix(
                                                com.theveloper.pixelplay.data.MixFlavor.SMART,
                                                initial = songs,
                                                playInitialFirst = true
                                            )
                                            FreshFindsStore.markSeen(context, freshWeek); freshIsNew = false
                                        }
                                    },
                                    onShuffleLongClick = { shuffleSoonFor = HomeShuffleSoon.FRESH_FINDS },
                                    onSongClick = { song ->
                                        friendsViewModel.prepareDiscoverySongs()
                                        playerViewModel.playSongs(
                                            songsToPlay = moodFreshFinds.map { it.song },
                                            startSong = song,
                                            queueName = "Fresh Finds"
                                        )
                                        FreshFindsStore.markSeen(context, freshWeek); freshIsNew = false
                                    },
                                    onSongMoreClick = { song ->
                                        selectedSongForInfo = song
                                        showSongInfoBottomSheet = true
                                    },
                                    onNotForMe = { song ->
                                        freshHidden = FreshFindsStore.hide(context, freshWeek, song.id)
                                    }
                                )

                                // Back again: Recent · Liked · Forgotten · On this day.
                                if (recentlyPlayedQueue.isNotEmpty() || recentlyLikedSongs.isNotEmpty() ||
                                    forgottenSongs.items.isNotEmpty() || onThisDaySongs.isNotEmpty()
                                ) {
                                    Spacer(modifier = Modifier.height(HomeSectionSpacing))
                                    RecentlyPicksSection(
                                        listenedSongs = moodRecentlyPlayed.items,
                                        likedSongs = moodRecentlyLiked.items,
                                        forgottenSongs = forgottenSongs.items,
                                        onThisDaySongs = onThisDaySongs,
                                        currentSongId = currentSong?.id,
                                        isPlaying = isPlaying,
                                        onSongClick = { song, mode ->
                                            val queue = when (mode) {
                                                RecentlyMode.Listened -> moodRecentlyPlayed.items
                                                RecentlyMode.Liked -> moodRecentlyLiked.items
                                                RecentlyMode.Forgotten -> forgottenSongs.items
                                                RecentlyMode.OnThisDay -> onThisDaySongs.map { it.song }
                                            }
                                            playerViewModel.playSongs(
                                                songsToPlay = queue,
                                                startSong = song,
                                                queueName = mode.queueName
                                            )
                                        },
                                        onSongMoreClick = { song ->
                                            selectedSongForInfo = song
                                            showSongInfoBottomSheet = true
                                        },
                                        onViewAllHistory = { mode ->
                                            // Full history only exists for Recent and Liked.
                                            if (mode == RecentlyMode.Listened || mode == RecentlyMode.Liked) {
                                                navController.navigateSafely(Screen.RecentlyPlayed.createRoute(mode.name))
                                            }
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(HomeSectionSpacing))

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
            com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
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

    shuffleSoonFor?.let { which ->
        val quickPicksName = stringResource(R.string.home_quick_picks_name)
        val freshFindsName = stringResource(R.string.home_fresh_finds_name)
        com.theveloper.pixelplay.presentation.components.ShuffleSoonSheet(
            playerViewModel = playerViewModel,
            name = if (which == HomeShuffleSoon.QUICK_PICKS) quickPicksName else freshFindsName,
            onDismiss = { shuffleSoonFor = null },
            onConfirm = { afterUpcoming ->
                val placement = com.theveloper.pixelplay.presentation.viewmodel.PlaybackDispatchStateHolder.PlaylistQueuePlacement.AfterUpcoming(afterUpcoming)
                when (which) {
                    HomeShuffleSoon.QUICK_PICKS -> {
                        val picks = moodQuickPicks.items
                        if (picks.isNotEmpty()) {
                            val name = moodScores?.takeIf { !moodQuickPicks.fellBack }
                                ?.let { "$quickPicksName · ${it.mood.label}" } ?: quickPicksName
                            playerViewModel.queuePlaylist(picks, name, null, placement, shuffled = true)
                        }
                    }
                    HomeShuffleSoon.FRESH_FINDS -> {
                        val finds = moodFreshFinds.map { it.song }
                        if (finds.isNotEmpty()) {
                            friendsViewModel.prepareDiscoverySongs()
                            playerViewModel.queuePlaylist(finds, freshFindsName, null, placement, shuffled = true)
                            FreshFindsStore.markSeen(context, freshWeek); freshIsNew = false
                        }
                    }
                }
            }
        )
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val songForInfo = selectedSongForInfo!!
        val isFav = remember(songForInfo.id, favoriteSongIds) {
            favoriteSongIds.contains(songForInfo.id)
        }
        SongInfoBottomSheet(
            onStartSongRadio = { seedSong -> playerViewModel.startSongRadio(seedSong) },
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
            // Hold Next → Soon (the "after which song" slider).
            soonPlayer = playerViewModel,
            onAddSoon = { k -> playerViewModel.addSongAfterUpcoming(songForInfo, k) },
            onAddToPlayList = {
                showSongInfoBottomSheet = false
            },
            onDeleteFromDevice = { act, song, callback ->
                playerViewModel.deleteFromDevice(act, song, callback)
            },
            onNavigateToAlbum = {
                Screen.AlbumDetail.createRouteForSong(songForInfo)?.let { navController.navigateSafely(it) }
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
        modifier = modifier
            .songStartFeedback(song.id, androidx.compose.ui.graphics.RectangleShape)
            .offlineVeil(song),
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

/** One vertical rhythm between Home sections. */
private val HomeSectionSpacing = 24.dp

/** Below this many matches, a filter value is also searched online. */
private const val MinLibraryMatchesBeforeOnlineSearch = 8

/** Which filter value to search online for when several are set (most specific first). */
private val OnlineSearchFieldOrder = listOf(
    com.theveloper.pixelplay.presentation.components.MixFilterField.Artist,
    com.theveloper.pixelplay.presentation.components.MixFilterField.Album,
    com.theveloper.pixelplay.presentation.components.MixFilterField.Genre,
    com.theveloper.pixelplay.presentation.components.MixFilterField.Mood,
    com.theveloper.pixelplay.presentation.components.MixFilterField.Activity,
    com.theveloper.pixelplay.presentation.components.MixFilterField.Era,
)

/** Which Home shuffle the Soon sheet is scheduling (hold Shuffle on Quick picks / Fresh Finds). */
private enum class HomeShuffleSoon { QUICK_PICKS, FRESH_FINDS }
