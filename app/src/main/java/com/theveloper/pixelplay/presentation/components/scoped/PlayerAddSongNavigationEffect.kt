package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.isMainRootRoute
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest

/**
 * Add Song from the lyrics screen, navigation side:
 *  - a request collapses the player (same way artist / album navigation does) and opens the
 *    existing Search screen, remembering the destination the user was on;
 *  - once an action is picked (or Add Song is backed out of), everything opened since then is
 *    popped again, so the user never lands in Search, a detail page or the queue;
 *  - leaving for another main tab, or opening the player by hand, quietly ends the session so
 *    song taps elsewhere go back to playing normally.
 */
@Composable
internal fun PlayerAddSongNavigationEffect(
    navController: NavHostController,
    sheetCollapsedTargetY: Float,
    sheetMotionController: SheetMotionController,
    playerViewModel: PlayerViewModel
) {
    val latestSheetCollapsedTargetY by rememberUpdatedState(sheetCollapsedTargetY)

    LaunchedEffect(navController) {
        playerViewModel.addSongNavigationRequests.collectLatest {
            val origin = navController.currentDestination?.id
            playerViewModel.attachAddSongOrigin(origin)
            sheetMotionController.snapCollapsed(latestSheetCollapsedTargetY)
            playerViewModel.collapsePlayerSheet()
            if (navController.currentDestination?.route != Screen.Search.route) {
                navController.navigate(Screen.Search.route) { launchSingleTop = true }
            }
        }
    }

    LaunchedEffect(navController) {
        playerViewModel.addSongReturnRequests.collectLatest { session ->
            val origin = session.originDestinationId
            if (origin != null && navController.currentDestination?.id != origin) {
                navController.popBackStack(origin, inclusive = false)
            }
        }
    }

    // Opening the player by hand while choosing a song ends the session (no return trip).
    LaunchedEffect(playerViewModel) {
        playerViewModel.sheetState.collectLatest { state ->
            if (state == PlayerSheetState.EXPANDED && playerViewModel.addSongSession.value?.originDestinationId != null) {
                playerViewModel.cancelAddSong(returnToLyrics = false)
            }
        }
    }

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val route = destination.route
            if (playerViewModel.addSongSession.value != null &&
                isMainRootRoute(route) && route != Screen.Search.route
            ) {
                playerViewModel.cancelAddSong(returnToLyrics = false)
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}
