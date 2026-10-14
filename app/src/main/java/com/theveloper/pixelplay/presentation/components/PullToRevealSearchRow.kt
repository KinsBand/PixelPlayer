package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A horizontally scrolling chip row with a text box hidden off its start edge.
 *
 * Scrolled all the way to the start, keep swiping right on any chip: the row stretches and a
 * pen bubble slides in. Let go past the mark (you feel a tick) and the row turns into a text
 * box — type anything ("rainy sunday", "2am drive", "90s rock") and press enter. Swiping back,
 * or letting go early, just springs the row home.
 *
 * Mirrors itself in right-to-left layouts (there the start is on the right).
 */
@Composable
fun PullToRevealSearchRow(
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /** Shown in the bubble while pulling, e.g. "Custom mood". */
    pullLabel: String = "Type your own",
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    rowHeight: Dp = 48.dp,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val thresholdPx = with(density) { 64.dp.toPx() }
    val maxPullPx = with(density) { 112.dp.toPx() }
    var pullPx by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val latestOnRevealedChange by rememberUpdatedState(onRevealedChange)

    val connection = remember(listState, isRtl, thresholdPx, maxPullPx) {
        // Finger movement toward the end of the row is + in LTR and − in RTL.
        fun toPull(x: Float) = if (isRtl) -x else x
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || pullPx <= 0f) return Offset.Zero
                val delta = toPull(available.x)
                if (delta >= 0f) return Offset.Zero
                // Swiping back: shrink the pull before the list scrolls.
                val taken = maxOf(delta, -pullPx)
                pullPx += taken
                if (armed && pullPx < thresholdPx) armed = false
                return Offset(toPull(taken), 0f)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val delta = toPull(available.x)
                if (delta <= 0f || listState.canScrollBackward) return Offset.Zero
                // Rubber band: the further you pull, the harder it gets.
                val resistance = (1f - pullPx / maxPullPx).coerceIn(0.15f, 1f) * 0.55f
                pullPx = (pullPx + delta * resistance).coerceAtMost(maxPullPx)
                if (!armed && pullPx >= thresholdPx) {
                    armed = true
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                return available.copy(y = 0f)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullPx <= 0f) return Velocity.Zero
                val open = pullPx >= thresholdPx
                armed = false
                if (open) latestOnRevealedChange(true)
                val start = pullPx
                scope.launch {
                    animate(start, 0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                        pullPx = value
                    }
                }
                // The pull used this gesture up; don't fling the list.
                return available
            }
        }
    }

    AnimatedContent(
        targetState = revealed,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.96f)) togetherWith fadeOut(tween(120))
        },
        label = "pullToRevealSearch",
        modifier = modifier.fillMaxWidth()
    ) { open ->
        if (open) {
            InlineCustomSearchField(
                placeholder = placeholder,
                height = rowHeight,
                onSubmit = { text ->
                    onSubmit(text)
                    onRevealedChange(false)
                },
                onClose = { onRevealedChange(false) }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(connection),
                contentAlignment = Alignment.CenterStart
            ) {
                // The bubble that slides in from the start edge while pulling.
                val progress = (pullPx / thresholdPx).coerceIn(0f, 1f)
                if (pullPx > 0f) {
                    PullBubble(
                        label = if (armed) "Let go to type" else pullLabel,
                        armed = armed,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .graphicsLayer {
                                alpha = progress
                                val s = 0.7f + 0.3f * progress
                                scaleX = s
                                scaleY = s
                            }
                    )
                }
                LazyRow(
                    state = listState,
                    contentPadding = contentPadding,
                    horizontalArrangement = horizontalArrangement,
                    verticalAlignment = verticalAlignment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = if (isRtl) -pullPx else pullPx },
                    content = content
                )
            }
        }
    }
}

@Composable
private fun PullBubble(label: String, armed: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (armed) colors.primary else colors.secondaryContainer)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Edit,
            contentDescription = null,
            tint = if (armed) colors.onPrimary else colors.onSecondaryContainer,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (armed) colors.onPrimary else colors.onSecondaryContainer,
            maxLines = 1,
        )
    }
}

/** The text box the row turns into. Focused straight away; Enter submits. */
@Composable
private fun InlineCustomSearchField(
    placeholder: String,
    height: Dp,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val colors = MaterialTheme.colorScheme
    val submit = { text.trim().takeIf { it.isNotEmpty() }?.let(onSubmit) }
    Surface(
        shape = CircleShape,
        color = colors.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(height)
    ) {
        Row(Modifier.padding(start = 14.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Edit, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSecondaryContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(60) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSecondaryContainer),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            if (text.isNotBlank()) {
                IconButton(onClick = { submit() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Use this", tint = colors.primary)
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.onSecondaryContainer)
            }
        }
    }
}
