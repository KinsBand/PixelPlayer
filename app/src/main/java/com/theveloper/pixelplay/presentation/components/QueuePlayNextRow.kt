package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private val ActionRowCorner = 18.dp

private val ActionRowShape = AbsoluteSmoothCornerShape(
    cornerRadiusTR = ActionRowCorner, smoothnessAsPercentBR = 60,
    cornerRadiusBR = ActionRowCorner, smoothnessAsPercentTL = 60,
    cornerRadiusTL = ActionRowCorner, smoothnessAsPercentBL = 60,
    cornerRadiusBL = ActionRowCorner, smoothnessAsPercentTR = 60
)

/**
 * The universal row: Queue (left) · Play (middle) · Next (right). Holding Next asks for Soon
 * ([onNextLongClick]; null = no long-press). [soonOpen] tints Next while the Soon panel shows.
 */
@Composable
fun QueuePlayNextRow(
    onQueue: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onNextLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    soonOpen: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val nextContainer by animateColorAsState(
        if (soonOpen) colors.primaryContainer else colors.secondaryContainer,
        tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized), label = "nextContainer"
    )
    val nextContent by animateColorAsState(
        if (soonOpen) colors.onPrimaryContainer else colors.onSecondaryContainer,
        tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized), label = "nextContent"
    )
    val soonOpenState = stringResource(R.string.action_row_soon_open_state)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionRowButton(
            label = stringResource(R.string.playlist_option_queue),
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            container = colors.secondaryContainer,
            content = colors.onSecondaryContainer,
            onClick = onQueue,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        ActionRowButton(
            label = stringResource(R.string.common_play),
            icon = Icons.Rounded.PlayArrow,
            container = colors.primary,
            content = colors.onPrimary,
            onClick = onPlay,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        ActionRowButton(
            // While the Soon panel is open, Next reads "Soon" (morphs back when it closes).
            label = stringResource(if (soonOpen) R.string.playlist_option_soon else R.string.playlist_option_next),
            icon = if (soonOpen) Icons.Rounded.Schedule else Icons.AutoMirrored.Rounded.PlaylistPlay,
            container = nextContainer,
            content = nextContent,
            onClick = onNext,
            onLongClick = onNextLongClick,
            onLongClickLabel = onNextLongClick?.let { stringResource(R.string.action_row_next_hold_soon) },
            stateDescription = soonOpenState.takeIf { soonOpen },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Queue · Next · Soon, for sheets that already have their own Play (e.g. a radio station). */
@Composable
fun QueueNextSoonRow(
    onQueue: () -> Unit,
    onNext: () -> Unit,
    onSoon: () -> Unit,
    soonOpen: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val soonContainer by animateColorAsState(
        if (soonOpen) colors.primaryContainer else colors.secondaryContainer,
        tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized), label = "soonContainer"
    )
    val soonContent by animateColorAsState(
        if (soonOpen) colors.onPrimaryContainer else colors.onSecondaryContainer,
        tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized), label = "soonContent"
    )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionRowButton(
            label = stringResource(R.string.playlist_option_queue),
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            container = colors.secondaryContainer,
            content = colors.onSecondaryContainer,
            onClick = onQueue,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        ActionRowButton(
            label = stringResource(R.string.playlist_option_next),
            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            container = colors.secondaryContainer,
            content = colors.onSecondaryContainer,
            onClick = onNext,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        ActionRowButton(
            label = stringResource(R.string.playlist_option_soon),
            icon = Icons.Rounded.Schedule,
            container = soonContainer,
            content = soonContent,
            onClick = onSoon,
            selected = soonOpen,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionRowButton(
    label: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    stateDescription: String? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = ActionRowShape,
        color = container,
        contentColor = content,
        modifier = modifier
            .heightIn(min = 58.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(ActionRowShape)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick?.let { longClick ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        longClick()
                    }
                },
                onClick = onClick,
            )
            .semantics {
                stateDescription?.let { this.stateDescription = it }
                selected?.let { this.selected = it }
            },
    ) {
        val reduced = rememberSystemReducedMotion()
        Box(Modifier.padding(PaddingValues(horizontal = 10.dp, vertical = 8.dp)), contentAlignment = Alignment.Center) {
            // Label + icon change (Next ↔ Soon) slides up through the button; the size stays put.
            AnimatedContent(
                targetState = label to icon,
                transitionSpec = {
                    if (reduced) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (slideInVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) { it / 2 } +
                            fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate))) togetherWith
                            (slideOutVertically(tween(MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedAccelerate)) { -it / 2 } +
                                fadeOut(tween(MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedAccelerate)))
                    }
                },
                contentAlignment = Alignment.Center,
                label = "actionRowLabel"
            ) { (shownLabel, shownIcon) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(shownIcon, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        shownLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
