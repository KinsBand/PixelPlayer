package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CompositionLocal providing the set of song IDs that are currently downloading.
 */
val LocalDownloadingSongIds = compositionLocalOf<Set<String>> { emptySet() }

/**
 * An ambient, very low-opacity visual overlay that softly flashes/pulses over an album cover
 * to indicate that the song is currently downloading in the background.
 */
@Composable
fun AmbientDownloadCoverOverlay(
    isDownloading: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    iconSize: Dp? = null
) {
    if (!isDownloading) return

    val infiniteTransition = rememberInfiniteTransition(label = "AmbientDownloadTransition")

    // Very low opacity ambient flash / breathing pulse for the entire album cover
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AmbientPulseAlpha"
    )

    // Subtle scale breathing for the download icon
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AmbientIconScale"
    )

    // Subtle breathing alpha for the download icon (low opacity so the cover art remains clearly visible)
    val iconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AmbientIconAlpha"
    )

    // All three animated values are read in the draw/layer phase only, so the pulse redraws
    // the overlay without recomposing it (and its parent cover) on every frame.
    val pulseColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .drawBehind { drawRect(pulseColor.copy(alpha = pulseAlpha)) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                    alpha = iconAlpha
                }
                .then(
                    if (iconSize != null) Modifier.size(iconSize)
                    else Modifier.fillMaxSize(0.42f).sizeIn(minWidth = 16.dp, minHeight = 16.dp, maxWidth = 52.dp, maxHeight = 52.dp)
                )
        )
    }
}
