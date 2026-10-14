package com.theveloper.pixelplay.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion
import kotlinx.coroutines.launch

/** Which action a horizontal swipe on a song row is at. */
internal enum class SwipeStage { NONE, NEXT, SOON, QUEUE }

/**
 * Swipe right past [nextPx] = Next; further, past [soonPx] (when [soonEnabled]) = Soon.
 * Swipe left past [nextPx] = Queue.
 */
internal fun swipeStage(offsetPx: Float, nextPx: Float, soonPx: Float, soonEnabled: Boolean): SwipeStage = when {
    offsetPx <= -nextPx -> SwipeStage.QUEUE
    soonEnabled && offsetPx >= soonPx -> SwipeStage.SOON
    offsetPx >= nextPx -> SwipeStage.NEXT
    else -> SwipeStage.NONE
}

/**
 * Swipe right = Next, keep going = Soon ([onSoon]; null = right swipe has only Next).
 * Swipe left = Queue. Text-only labels behind the row. A haptic tick at each stage; the action
 * runs on release and the row springs back. The offset is read only in graphicsLayer.
 */
@Composable
fun SwipeToQueueRow(
    enabled: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onSoon: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberSystemReducedMotion()
    val offset = remember { Animatable(0f) }
    val soonEnabled = onSoon != null
    val nextPx = with(density) { 96.dp.toPx() }
    val soonPx = with(density) { 176.dp.toPx() }
    val maxRightPx = with(density) { (if (soonEnabled) 220.dp else 160.dp).toPx() }
    val maxLeftPx = with(density) { 160.dp.toPx() }
    val latestPlayNext by rememberUpdatedState(onPlayNext)
    val latestAddToQueue by rememberUpdatedState(onAddToQueue)
    val latestSoon by rememberUpdatedState(onSoon)
    val playNextAction = stringResource(R.string.search_swipe_play_next)
    val addAction = stringResource(R.string.search_swipe_add_to_queue)
    val soonAction = stringResource(R.string.action_row_next_hold_soon)
    val colors = MaterialTheme.colorScheme
    val stage by remember(soonEnabled, nextPx, soonPx) {
        derivedStateOf { swipeStage(offset.value, nextPx, soonPx, soonEnabled) }
    }

    Box(
        modifier = modifier.semantics {
            if (enabled) {
                customActions = buildList {
                    add(CustomAccessibilityAction(playNextAction) { latestPlayNext(); true })
                    if (soonEnabled) add(CustomAccessibilityAction(soonAction) { latestSoon?.invoke(); true })
                    add(CustomAccessibilityAction(addAction) { latestAddToQueue(); true })
                }
            }
        }
    ) {
        if (enabled) {
            // Right swipe: "Next", then "Soon", on the left edge.
            val rightIsSoon = stage == SwipeStage.SOON
            val rightContainer by animateColorAsState(
                if (rightIsSoon) colors.tertiaryContainer else colors.primaryContainer,
                tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized), label = "swipeRightBg"
            )
            val rightContent by animateColorAsState(
                if (rightIsSoon) colors.onTertiaryContainer else colors.onPrimaryContainer,
                tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized), label = "swipeRightFg"
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = (offset.value / nextPx).coerceIn(0f, 1f) }
                    .clip(shape)
                    .background(rightContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                AnimatedContent(
                    targetState = rightIsSoon,
                    transitionSpec = {
                        if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                        else fadeIn(tween(MotionTokens.DurationShort3)) togetherWith fadeOut(tween(MotionTokens.DurationShort3))
                    },
                    label = "swipeRightLabel"
                ) { soon ->
                    SwipeLabel(stringResource(if (soon) R.string.playlist_option_soon else R.string.playlist_option_next), rightContent)
                }
            }
            // Left swipe: "Queue" on the right edge.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = (-offset.value / nextPx).coerceIn(0f, 1f) }
                    .clip(shape)
                    .background(colors.secondaryContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                SwipeLabel(stringResource(R.string.playlist_option_queue), colors.onSecondaryContainer)
            }
        }
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offset.value }
                .then(
                    if (!enabled) Modifier else Modifier.pointerInput(soonEnabled) {
                        var lastStage = SwipeStage.NONE
                        val springBack: () -> Unit = {
                            scope.launch { if (reducedMotion) offset.snapTo(0f) else offset.animateTo(0f, tween(220)) }
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { lastStage = SwipeStage.NONE },
                            onDragEnd = {
                                when (swipeStage(offset.value, nextPx, soonPx, soonEnabled)) {
                                    SwipeStage.NEXT -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        latestPlayNext()
                                    }
                                    SwipeStage.SOON -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        latestSoon?.invoke()
                                    }
                                    SwipeStage.QUEUE -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        latestAddToQueue()
                                    }
                                    SwipeStage.NONE -> Unit
                                }
                                springBack()
                            },
                            onDragCancel = { springBack() },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val next = (offset.value + dragAmount).coerceIn(-maxLeftPx, maxRightPx)
                                val nowStage = swipeStage(next, nextPx, soonPx, soonEnabled)
                                if (nowStage != lastStage && nowStage != SwipeStage.NONE) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                lastStage = nowStage
                                scope.launch { offset.snapTo(next) }
                            }
                        )
                    }
                )
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
}

/**
 * The "after which song" (Soon) slider, opening from under a song or station row. [song] is
 * only called while it shows (a radio station is turned into a song, which registers recording).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun InlineSoonPanel(
    visible: Boolean,
    song: () -> Song,
    playerViewModel: PlayerViewModel,
    onClose: () -> Unit,
    onConfirm: (afterUpcoming: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberSystemReducedMotion()
    BackHandler(enabled = visible) { onClose() }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reducedMotion) EnterTransition.None else
            expandVertically(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate), expandFrom = Alignment.Top) +
                fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
        exit = if (reducedMotion) ExitTransition.None else
            shrinkVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate), shrinkTowards = Alignment.Top) +
                fadeOut(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)),
        label = "inlineSoon"
    ) {
        val forSong = remember { song() }
        // A row near the bottom of the list: scroll the opened slider into view once it has grown.
        val requester = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(MotionTokens.DurationMedium2.toLong())
            requester.bringIntoView()
        }
        Box(
            Modifier
                .bringIntoViewRequester(requester)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            PlaylistSoonPanel(
                playerViewModel = playerViewModel,
                onBack = onClose,
                onConfirm = onConfirm,
                forSong = forSong,
            )
        }
    }
}
