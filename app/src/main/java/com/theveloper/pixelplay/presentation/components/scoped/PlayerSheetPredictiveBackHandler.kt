package com.theveloper.pixelplay.presentation.components.scoped

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerSheetPredictiveBackHandler(
    enabled: Boolean,
    playerViewModel: PlayerViewModel,
    sheetCollapsedTargetY: Float,
    sheetExpandedTargetY: Float,
    sheetMotionController: SheetMotionController,
    animationDurationMs: Int,
    onSwipeEdgeChanged: (Int?) -> Unit,
    registrationKey: Any?
) {
    val scope = rememberCoroutineScope()
    key(registrationKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PredictiveBackHandler(enabled = enabled) { progressFlow ->
                try {
                    progressFlow.collect { backEvent ->
                        onSwipeEdgeChanged(backEvent.swipeEdge)
                        playerViewModel.updatePredictiveBackCollapseFraction(backEvent.progress)
                    }
                    scope.launch {
                        val progressAtRelease = playerViewModel.predictiveBackCollapseFraction.value
                        Animatable(progressAtRelease).animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = animationDurationMs.coerceAtLeast(200),
                                easing = MotionTokens.EmphasizedAccelerateEasing
                            )
                        ) {
                            playerViewModel.updatePredictiveBackCollapseFraction(this.value)
                        }
                        sheetMotionController.snapCollapsed(sheetCollapsedTargetY)
                        playerViewModel.collapsePlayerSheet(resetPredictiveState = false)
                        playerViewModel.resetPredictiveBackState()
                        onSwipeEdgeChanged(null)
                    }
                } catch (_: CancellationException) {
                    scope.launch {
                        Animatable(playerViewModel.predictiveBackCollapseFraction.value).animateTo(
                            targetValue = 0f,
                            animationSpec = tween(animationDurationMs)
                        ) {
                            playerViewModel.updatePredictiveBackCollapseFraction(this.value)
                        }

                        if (playerViewModel.sheetState.value == PlayerSheetState.EXPANDED) {
                            playerViewModel.expandPlayerSheet()
                        } else {
                            playerViewModel.collapsePlayerSheet()
                        }

                        onSwipeEdgeChanged(null)
                    }
                }
            }
        } else {
            BackHandler(enabled = enabled) {
                playerViewModel.collapsePlayerSheet()
            }
        }
    }
}
