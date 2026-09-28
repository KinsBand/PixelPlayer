package com.theveloper.pixelplay.presentation.components.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.viewmodel.LyricsConfirmation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * Small, non-blocking "Playing next ✓" pill. Pops in, holds, then fades away by itself. It has no
 * pointer input, so touches go straight through to the lyrics underneath.
 */
@Composable
internal fun LyricsConfirmationPill(
    confirmations: Flow<LyricsConfirmation?>,
    containerColor: Color,
    contentColor: Color,
    onShown: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Long = 1_600L,
) {
    var current by remember { mutableStateOf<LyricsConfirmation?>(null) }
    var visible by remember { mutableStateOf(false) }
    val onShownState = rememberUpdatedState(onShown)

    LaunchedEffect(confirmations) {
        confirmations.collect { confirmation ->
            if (confirmation == null) return@collect
            // Ignore stale replays (e.g. a confirmation from long before this sheet opened).
            if (System.currentTimeMillis() - confirmation.at > 10_000L) {
                onShownState.value()
                return@collect
            }
            current = confirmation
            visible = true
            onShownState.value()
        }
    }
    LaunchedEffect(current) {
        if (current != null) {
            delay(holdMillis)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.85f, animationSpec = tween(180)),
        exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.95f, animationSpec = tween(250)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(containerColor, CircleShape)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = current?.action?.confirmation.orEmpty(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor
            )
        }
    }
}
