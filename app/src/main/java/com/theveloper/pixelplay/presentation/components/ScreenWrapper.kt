package com.theveloper.pixelplay.presentation.components

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel

/**
 * Hosts every NavHost screen on an opaque background.
 *
 * The screen-to-screen motion lives entirely in the NavHost transitions (fade through for tabs,
 * shared axis X for pushes — see navigation/Transitions.kt). This wrapper used to add depth
 * effects on top (an animated 24 dp blur, an offscreen compositing layer, rounded-corner
 * clipping and a black dim on the screen behind). With fade-through timing the screen behind is
 * already invisible for most of the transition, so those effects were almost never seen while
 * still costing a RenderEffect re-creation every frame plus an extra offscreen buffer per
 * screen. They're gone: transitions now only animate alpha, scale and offset.
 *
 * [navController], [playerViewModel] and [animatedVisibilityScope] are kept so the 30+ call
 * sites don't change.
 */
@OptIn(UnstableApi::class)
@Composable
fun ScreenWrapper(
    navController: androidx.navigation.NavController,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()
    }
}
