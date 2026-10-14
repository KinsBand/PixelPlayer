package com.theveloper.pixelplay.presentation.components.scoped

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    // The animation that finishes a gesture (commit -> collapse, or cancel -> spring back).
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // True while a committed Back is still collapsing the sheet. A second Back during that
    // window used to start another collapse animation that fought the first one (jitter).
    var isCommitting by remember { mutableStateOf(false) }
    key(registrationKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PredictiveBackHandler(enabled = enabled && !isCommitting) { progressFlow ->
                // A new gesture takes over from a still-running spring back.
                settleJob?.cancel()
                try {
                    progressFlow.collect { backEvent ->
                        onSwipeEdgeChanged(backEvent.swipeEdge)
                        playerViewModel.updatePredictiveBackCollapseFraction(backEvent.progress)
                    }
                    isCommitting = true
                    settleJob = scope.launch {
                        try {
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
                        } finally {
                            // Always land collapsed, even if the animation was interrupted
                            // (sheet left composition, new back stack entry, ...). Before, an
                            // interrupted commit left the player stuck half-collapsed and blurred.
                            withContext(NonCancellable) {
                                sheetMotionController.snapCollapsed(sheetCollapsedTargetY)
                                playerViewModel.collapsePlayerSheet(resetPredictiveState = false)
                                playerViewModel.resetPredictiveBackState()
                                onSwipeEdgeChanged(null)
                            }
                            isCommitting = false
                        }
                    }
                } catch (_: CancellationException) {
                    settleJob = scope.launch {
                        try {
                            Animatable(playerViewModel.predictiveBackCollapseFraction.value).animateTo(
                                targetValue = 0f,
                                animationSpec = tween(animationDurationMs)
                            ) {
                                playerViewModel.updatePredictiveBackCollapseFraction(this.value)
                            }
                        } finally {
                            // Never leave a partial collapse fraction behind.
                            withContext(NonCancellable) {
                                playerViewModel.updatePredictiveBackCollapseFraction(0f)
                                onSwipeEdgeChanged(null)
                            }
                        }

                        if (playerViewModel.sheetState.value == PlayerSheetState.EXPANDED) {
                            playerViewModel.expandPlayerSheet()
                        } else {
                            playerViewModel.collapsePlayerSheet()
                        }
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
