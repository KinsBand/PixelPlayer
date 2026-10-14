package com.theveloper.pixelplay.presentation.navigation

import DelimiterConfigScreen
import com.theveloper.pixelplay.presentation.screens.WordDelimiterConfigScreen
import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import com.theveloper.pixelplay.ui.theme.MotionTokens
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import com.theveloper.pixelplay.presentation.components.LocalSongPrimaryTap
import com.theveloper.pixelplay.presentation.components.SongActionRequest
import com.theveloper.pixelplay.presentation.components.SongActionSheet
import com.theveloper.pixelplay.presentation.components.SongPrimaryTapInterceptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.AnimatedContentScope
import androidx.navigation.navArgument
import com.theveloper.pixelplay.data.preferences.LaunchTab
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.presentation.screens.AlbumDetailScreen
import com.theveloper.pixelplay.presentation.screens.AccountsScreen
import com.theveloper.pixelplay.presentation.screens.ArtistDetailScreen
import com.theveloper.pixelplay.presentation.screens.ArtistSettingsScreen
import com.theveloper.pixelplay.presentation.screens.DailyMixScreen
import com.theveloper.pixelplay.presentation.screens.EditTransitionScreen
import com.theveloper.pixelplay.presentation.screens.EasterEggScreen
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.screens.GenreDetailScreen
import com.theveloper.pixelplay.presentation.screens.HomeScreen
import com.theveloper.pixelplay.presentation.screens.LibraryScreen
import com.theveloper.pixelplay.presentation.screens.MashupScreen
import com.theveloper.pixelplay.presentation.screens.NavBarCornerRadiusScreen
import com.theveloper.pixelplay.presentation.screens.PaletteStyleSettingsScreen
import com.theveloper.pixelplay.presentation.screens.PlaylistDetailScreen
import com.theveloper.pixelplay.presentation.screens.YourMusicScreen
import com.theveloper.pixelplay.presentation.screens.PlatformPlaylistsScreen
import com.theveloper.pixelplay.presentation.screens.PlaylistPlatform
import com.theveloper.pixelplay.presentation.screens.PracticeScreen
import com.theveloper.pixelplay.presentation.screens.RecentlyPlayedScreen
import com.theveloper.pixelplay.presentation.screens.RecentlyHeardScreen
import com.theveloper.pixelplay.presentation.screens.radio.RadioScreen

import com.theveloper.pixelplay.presentation.screens.AboutScreen
import com.theveloper.pixelplay.presentation.screens.SearchScreen
import com.theveloper.pixelplay.presentation.screens.StatsScreen
import com.theveloper.pixelplay.presentation.screens.SettingsScreen
import com.theveloper.pixelplay.presentation.screens.settings.SettingsCategoryScreen
import com.theveloper.pixelplay.presentation.screens.EqualizerScreen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import kotlinx.coroutines.flow.first
import com.theveloper.pixelplay.presentation.components.ScreenWrapper

@OptIn(UnstableApi::class)
@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun AppNavigation(
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    paddingValues: PaddingValues,
    userPreferencesRepository: UserPreferencesRepository,
    onOpenSidebar: () -> Unit,
    onVoiceSearchClick: () -> Unit = {},
    /** Search header mic: open voice search, or speak again while it's open. */
    onSearchVoiceClick: () -> Unit = onVoiceSearchClick,
    /** Non-null while voice search is open: Search's header shows the live listening state. */
    searchVoiceCapture: com.theveloper.pixelplay.presentation.components.voicesearch.VoiceSpeechCaptureState? = null
) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination = userPreferencesRepository.launchTabFlow
            .first()
            .toRoute()
    }

    // Add Song from the lyrics screen: while it's active, tapping a song's main area opens the
    // Play / Next / Soon / Queue sheet instead of playing it. Other buttons on a card are
    // untouched. Picking an action returns straight to the lyrics.
    val addSongSession by playerViewModel.addSongSession.collectAsStateWithLifecycle()
    var songActionRequest by remember { mutableStateOf<SongActionRequest?>(null) }
    LaunchedEffect(addSongSession) { if (addSongSession == null) songActionRequest = null }
    val songTapInterceptor = remember(addSongSession != null) {
        if (addSongSession != null) {
            SongPrimaryTapInterceptor { song, queue, name -> songActionRequest = SongActionRequest(song, queue, name) }
        } else {
            null
        }
    }

    songActionRequest?.let { request ->
        // The sheet takes the current song's cover-art colours, like the lyrics screen.
        val albumSchemes by playerViewModel.activePlayerColorSchemePair.collectAsStateWithLifecycle()
        val base = MaterialTheme.colorScheme
        val isDark = base.background.luminance() < 0.5f
        val scheme = albumSchemes?.let { if (isDark) it.dark else it.light } ?: base
        MaterialTheme(colorScheme = scheme) {
            SongActionSheet(
                request = request,
                onAction = { action ->
                    songActionRequest = null
                    playerViewModel.completeAddSong(action, request.song, request.contextQueue, request.queueName)
                },
                // Swipe down / tap outside: just close, stay in Search.
                onDismiss = { songActionRequest = null }
            )
        }
    }

    CompositionLocalProvider(LocalSongPrimaryTap provides songTapInterceptor) {
        startDestination?.let { initialRoute ->
            NavHost(
                navController = navController,
                startDestination = initialRoute,
                enterTransition = { aospSharedAxisEnter() },
                exitTransition = { aospSharedAxisExit() },
                popEnterTransition = { aospSharedAxisPopEnter() },
                popExitTransition = { aospSharedAxisPopExit() }
            ) {
                mainRootTab(Screen.Home.route) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        HomeScreen(
                            navController = navController, 
                            paddingValuesParent = paddingValues, 
                            playerViewModel = playerViewModel,
                            onOpenSidebar = onOpenSidebar
                        )
                    }
                }
                mainRootTab(Screen.Search.route) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        SearchScreen(
                            paddingValues = paddingValues,
                            playerViewModel = playerViewModel,
                            navController = navController,
                            onVoiceSearchClick = onSearchVoiceClick,
                            voiceCapture = searchVoiceCapture
                        )
                    }
                }
                mainRootTab(Screen.Library.route) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        LibraryScreen(navController = navController, playerViewModel = playerViewModel)
                    }
                }
                composable(
                    Screen.Settings.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        SettingsScreen(
                            navController = navController,
                            playerViewModel = playerViewModel,
                            onNavigationIconClick = {
                                navController.popBackStackSafely()
                            }
                        )
                    }
                }
                composable(
                    Screen.Accounts.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        AccountsScreen(
                            onBackClick = { navController.popBackStackSafely() },
                            playerViewModel = playerViewModel,
                            navController = navController
                        )
                    }
                }
                composable(
                    Screen.Profile.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        com.theveloper.pixelplay.presentation.screens.ProfileScreen(
                            onBackClick = { navController.popBackStackSafely() },
                            navController = navController
                        )
                    }
                }
                composable(
                    route = Screen.SettingsCategory.route,
                    arguments = listOf(
                        navArgument("categoryId") { type = NavType.StringType },
                        navArgument("highlight") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    ),
                ) { backStackEntry ->
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        val categoryId = backStackEntry.arguments?.getString("categoryId")
                        val highlight = backStackEntry.arguments?.getString("highlight")
                        if (categoryId != null) {
                            SettingsCategoryScreen(
                                categoryId = categoryId,
                                highlight = highlight,
                                navController = navController,
                                playerViewModel = playerViewModel,
                                onBackClick = { navController.popBackStackSafely() }
                            )
                        }
                    }
                }
                composable(
                    Screen.PaletteStyle.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        PaletteStyleSettingsScreen(
                            playerViewModel = playerViewModel,
                            onBackClick = { navController.popBackStackSafely() }
                        )
                    }
                }
                composable(
                    Screen.LyricsAnimationStyle.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        com.theveloper.pixelplay.presentation.screens.LyricsAnimationStyleScreen(
                            playerViewModel = playerViewModel,
                            onBackClick = { navController.popBackStackSafely() }
                        )
                    }
                }
                composable(
                    Screen.Experimental.route,
                ) {
                    // The Experimental screen was folded into Player & Lyrics; old links land there.
                    // Retry while the enter transition is still settling (navigateSafely refuses
                    // until the entry is STARTED); never leave the user on this blank route.
                    LaunchedEffect(Unit) {
                        repeat(20) {
                            if (navController.navigateSafelyReplacing(
                                    route = Screen.SettingsCategory.createRoute(SettingsCategory.LYRICS.id),
                                    patternToPop = Screen.Experimental.route
                                )
                            ) return@LaunchedEffect
                            kotlinx.coroutines.delay(50)
                        }
                        navController.popBackStackSafely()
                    }
                }
                composable(
                    Screen.DailyMixScreen.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        DailyMixScreen(
                            playerViewModel = playerViewModel,
                            navController = navController
                        )
                    }
                }
                composable(
                    route = Screen.RecentlyPlayed.route,
                    arguments = listOf(
                        navArgument("mode") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    ),
                ) { backStackEntry ->
                    val initialMode = backStackEntry.arguments?.getString("mode")
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        RecentlyPlayedScreen(
                            playerViewModel = playerViewModel,
                            navController = navController,
                            initialMode = initialMode
                        )
                    }
                }
                composable(
                    Screen.RecentlyHeard.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        RecentlyHeardScreen(
                            playerViewModel = playerViewModel,
                            navController = navController
                        )
                    }
                }
                composable(
                    Screen.LiveEvents.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        com.theveloper.pixelplay.presentation.screens.events.LiveEventsScreen(navController = navController)
                    }
                }
                composable(
                    Screen.Stats.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        StatsScreen(
                            navController = navController,
                            playerViewModel = playerViewModel
                        )
                    }
                }
                composable(
                    route = Screen.PlaylistDetail.route,
                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId")
                    val playlistViewModel: PlaylistViewModel = hiltViewModel()
                    if (playlistId != null) {
                        ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                            PlaylistDetailScreen(
                                playlistId = playlistId,
                                playerViewModel = playerViewModel,
                                playlistViewModel = playlistViewModel,
                                onBackClick = { navController.popBackStackSafely() },
                                onDeletePlayListClick = { navController.popBackStackSafely() },
                                navController = navController
                            )
                        }
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStackSafely() }
                    }
                }

                composable(
                    route = Screen.PlatformPlaylists.route,
                    arguments = listOf(navArgument("platform") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val platform = PlaylistPlatform.from(backStackEntry.arguments?.getString("platform"))
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        PlatformPlaylistsScreen(
                            platform = platform,
                            navController = navController,
                            onBackClick = { navController.popBackStackSafely() }
                        )
                    }
                }

                composable(Screen.Practice.route) {
                    val showPracticeMode by userPreferencesRepository.showPracticeModeFlow.collectAsStateWithLifecycle(initialValue = true)
                    if (showPracticeMode) {
                        ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                            PracticeScreen(
                                playerViewModel = playerViewModel,
                                onBackClick = { navController.popBackStackSafely() }
                            )
                        }
                    } else {
                        LaunchedEffect(Unit) {
                            navController.popBackStackSafely()
                        }
                    }
                }

                // Radio and Friends are bottom-nav tabs: same tab transitions as Home/Search/Library.
                mainRootTab(Screen.Radio.route) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        RadioScreen(
                            playerViewModel = playerViewModel,
                            onSettingsClick = { navController.navigateSafely(Screen.Settings.route) }
                        )
                    }
                }

                mainRootTab(Screen.Friends.route) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        com.theveloper.pixelplay.presentation.screens.FriendsScreen(
                            navController = navController,
                            playerViewModel = playerViewModel
                        )
                    }
                }

                composable(Screen.YourMusic.route) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        YourMusicScreen(
                            playerViewModel = playerViewModel,
                            navController = navController,
                            onBackClick = { navController.popBackStackSafely() }
                        )
                    }
                }

                composable(
                    Screen.DJSpace.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        MashupScreen(onBackClick = { navController.popBackStackSafely() })
                    }
                }
                composable(
                    route = Screen.GenreDetail.route,
                    arguments = listOf(navArgument("genreId") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val genreId = backStackEntry.arguments?.getString("genreId")
                    if (genreId != null) {
                        ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                            GenreDetailScreen(
                                navController = navController,
                                genreId = genreId,
                                playerViewModel = playerViewModel
                            )
                        }
                    } else {
                        Text(stringResource(R.string.nav_error_genre_id_missing), modifier = Modifier)
                        LaunchedEffect(Unit) { navController.popBackStackSafely() }
                    }
                }
                composable(
                    route = Screen.AlbumDetail.route,
                    arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val albumId = backStackEntry.arguments?.getString("albumId")
                    if (albumId != null) {
                        ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                            AlbumDetailScreen(
                                albumId = albumId,
                                navController = navController,
                                playerViewModel = playerViewModel
                            )
                        }
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStackSafely() }
                    }
                }
                composable(
                    route = Screen.ArtistDetail.route,
                    arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val artistId = backStackEntry.arguments?.getString("artistId")
                    if (artistId != null) {
                        ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                            ArtistDetailScreen(
                                artistId = artistId,
                                navController = navController,
                                playerViewModel = playerViewModel
                            )
                        }
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStackSafely() }
                    }
                }
                composable(
                    Screen.NavBarCrRad.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        NavBarCornerRadiusScreen(navController)
                    }
                }
                composable(
                    route = Screen.EditTransition.route,
                    arguments = listOf(navArgument("playlistId") {
                        type = NavType.StringType
                        nullable = true
                    }),
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        EditTransitionScreen(navController = navController)
                    }
                }
                composable(
                    Screen.About.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        AboutScreen(
                            navController = navController,
                            viewModel = playerViewModel,
                            onNavigationIconClick = { navController.popBackStackSafely() }
                        )
                    }
                }
                composable(
                    Screen.EasterEgg.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        EasterEggScreen(
                            viewModel = playerViewModel,
                            onNavigationIconClick = { navController.popBackStackSafely() },
                        )
                    }
                }
                composable(
                    Screen.ArtistSettings.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        ArtistSettingsScreen(navController = navController)
                    }
                }
                composable(
                    Screen.DelimiterConfig.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        DelimiterConfigScreen(navController = navController)
                    }
                }
                composable(
                    Screen.WordDelimiterConfig.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        WordDelimiterConfigScreen(navController = navController)
                    }
                }
                composable(
                    Screen.Equalizer.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        EqualizerScreen(
                            navController = navController,
                            playerViewModel = playerViewModel
                        )
                    }
                }
                composable(
                    Screen.DeviceCapabilities.route,
                ) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                        com.theveloper.pixelplay.presentation.screens.DeviceCapabilitiesScreen(
                            navController = navController,
                            playerViewModel = playerViewModel
                        )
                    }
                }
            }
        }
    }

    // Back from the Search screen itself (not from a page opened from it) ends Add Song and
    // goes straight back to the lyrics. Registered after the NavHost so it
    // takes priority over the NavHost's own back handling.
    val currentEntry by navController.currentBackStackEntryAsState()
    BackHandler(enabled = addSongSession != null && currentEntry?.destination?.route == Screen.Search.route) {
        playerViewModel.cancelAddSong()
    }
}

private fun String.toRoute(): String = when (this) {
    LaunchTab.SEARCH -> Screen.Search.route
    LaunchTab.LIBRARY -> Screen.Library.route
    else -> Screen.Home.route
}

/**
 * A bottom-nav tab destination. Switching between tabs fades through; pushing or popping any
 * other screen falls back to the shared-axis transitions the rest of the NavHost uses, so a push and its pop always match.
 */
private fun NavGraphBuilder.mainRootTab(
    route: String,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route,
        enterTransition = {
            mainRootEnterTransition(initialState.destination.route, targetState.destination.route, aospSharedAxisEnter())
        },
        exitTransition = {
            mainRootExitTransition(initialState.destination.route, targetState.destination.route, aospSharedAxisExit())
        },
        popEnterTransition = {
            mainRootEnterTransition(initialState.destination.route, targetState.destination.route, aospSharedAxisPopEnter())
        },
        popExitTransition = {
            mainRootExitTransition(initialState.destination.route, targetState.destination.route, aospSharedAxisPopExit())
        },
        content = content
    )
}

/** True when both routes are bottom-nav tabs (and different), i.e. a tab switch. */
private fun isMainRootSwitch(fromRoute: String?, toRoute: String?): Boolean {
    val fromIndex = mainRootRouteIndex(fromRoute) ?: return false
    val toIndex = mainRootRouteIndex(toRoute) ?: return false
    return fromIndex != toIndex
}

// Tab switches fade through (see Transitions.kt): the old tab is gone before the new one
// fades and scales in, so the two never overlap. Pushes / pops keep the shared-axis fallback.
private fun mainRootEnterTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: EnterTransition
): EnterTransition = if (isMainRootSwitch(fromRoute, toRoute)) fadeThroughEnter() else fallback

private fun mainRootExitTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: ExitTransition
): ExitTransition = if (isMainRootSwitch(fromRoute, toRoute)) fadeThroughExit() else fallback

