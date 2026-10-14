package com.theveloper.pixelplay.presentation.components.scoped

import com.theveloper.pixelplay.presentation.navigation.navigateSafelyReplacing
import com.theveloper.pixelplay.presentation.navigation.navigateToTopLevelSafely

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun PlayerArtistNavigationEffect(
    navController: NavHostController,
    sheetCollapsedTargetY: Float,
    sheetMotionController: SheetMotionController,
    playerViewModel: PlayerViewModel
) {
    val latestSheetCollapsedTargetY by rememberUpdatedState(sheetCollapsedTargetY)
    LaunchedEffect(navController) {
        playerViewModel.artistNavigationRequests.collectLatest { artistId ->
            sheetMotionController.snapCollapsed(latestSheetCollapsedTargetY)
            playerViewModel.collapsePlayerSheet()

            navController.navigateSafelyReplacing(
                route = Screen.ArtistDetail.createRoute(artistId),
                patternToPop = Screen.ArtistDetail.route
            )
        }
    }
    // Radio: the player's map button opens the Radio tab (the station is picked up there).
    LaunchedEffect(navController) {
        playerViewModel.radioMapNavigationRequests.collectLatest {
            sheetMotionController.snapCollapsed(latestSheetCollapsedTargetY)
            playerViewModel.collapsePlayerSheet()
            if (navController.currentDestination?.route != Screen.Radio.route) {
                navController.navigateToTopLevelSafely(Screen.Radio.route)
            }
        }
    }
    // Any other page opened from the player or a song sheet (e.g. a collab page).
    LaunchedEffect(navController) {
        com.theveloper.pixelplay.presentation.navigation.RouteRequests.flow.collectLatest { route ->
            sheetMotionController.snapCollapsed(latestSheetCollapsedTargetY)
            playerViewModel.collapsePlayerSheet()
            navController.navigate(route) { launchSingleTop = true }
        }
    }
    // Online artists (no library id) are opened by name.
    LaunchedEffect(navController) {
        playerViewModel.artistNameNavigationRequests.collectLatest { artistName ->
            sheetMotionController.snapCollapsed(latestSheetCollapsedTargetY)
            playerViewModel.collapsePlayerSheet()

            navController.navigateSafelyReplacing(
                route = Screen.ArtistDetail.createRouteForName(artistName),
                patternToPop = Screen.ArtistDetail.route
            )
        }
    }
}
