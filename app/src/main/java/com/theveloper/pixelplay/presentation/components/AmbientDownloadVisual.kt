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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.material.icons.rounded.Pause
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

/**
 * Live download progress for every song (the download manager's map). Read by
 * [DownloadTraceCoverOverlay]; provided once at the app root.
 */
val LocalDownloadProgressMap = androidx.compose.runtime.staticCompositionLocalOf<
    kotlinx.coroutines.flow.StateFlow<Map<String, com.theveloper.pixelplay.data.youtube.DownloadProgress>>
> { kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }

/**
 * The player cover while its song downloads: a line a few dp inside the cover's edge that
 * starts at the top middle, grows left and right at once, runs down both sides and meets at
 * the bottom middle when the download finishes (the same motion as the music-note breaks).
 * A paused download keeps its line and shows a pause mark.
 */
@Composable
fun DownloadTraceCoverOverlay(
    songId: String,
    corner: Dp,
    modifier: Modifier = Modifier,
) {
    val progressMap = LocalDownloadProgressMap.current
    val state by androidx.compose.runtime.remember(progressMap, songId) {
        progressMap.map { it[songId] }.distinctUntilChanged()
    }.collectAsState(initial = progressMap.value[songId])
    val fraction = when (val s = state) {
        is com.theveloper.pixelplay.data.youtube.DownloadProgress.Downloading -> s.percent / 100f
        is com.theveloper.pixelplay.data.youtube.DownloadProgress.Paused -> s.percent / 100f
        is com.theveloper.pixelplay.data.youtube.DownloadProgress.Tagging,
        is com.theveloper.pixelplay.data.youtube.DownloadProgress.Scanning,
        is com.theveloper.pixelplay.data.youtube.DownloadProgress.Completed -> 1f
        else -> 0f
    }
    val animated by androidx.compose.animation.core.animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 450, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        label = "downloadTrace"
    )
    val paused = state is com.theveloper.pixelplay.data.youtube.DownloadProgress.Paused
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxSize()
            .downloadTrace(progress = { animated }, color = color, corner = corner),
        contentAlignment = Alignment.Center
    ) {
        if (paused) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .drawBehind { drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Pause,
                    contentDescription = "Download paused",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

/** Draws [progress] (0..1) as the top-middle → bottom-middle trace, inset inside the edge. */
private fun Modifier.downloadTrace(
    progress: () -> Float,
    color: androidx.compose.ui.graphics.Color,
    corner: Dp,
): Modifier = this.drawWithCache {
    val stroke = 4.dp.toPx()
    val inset = stroke / 2f + 5.dp.toPx()
    val maxRad = (minOf(size.width, size.height) / 2f - inset).coerceAtLeast(0f)
    val rad = (corner.toPx() - inset).coerceIn(0f, maxRad)
    val (left, right) = com.theveloper.pixelplay.utils.splitOutlineFromTop(
        inset, inset, size.width - inset, size.height - inset, rad
    )
    val mLeft = androidx.compose.ui.graphics.PathMeasure().apply { setPath(left, false) }
    val mRight = androidx.compose.ui.graphics.PathMeasure().apply { setPath(right, false) }
    val segLeft = androidx.compose.ui.graphics.Path()
    val segRight = androidx.compose.ui.graphics.Path()
    val track = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    onDrawWithContent {
        drawContent()
        drawPath(left, color.copy(alpha = 0.18f), style = track)
        drawPath(right, color.copy(alpha = 0.18f), style = track)
        val p = progress().coerceIn(0f, 1f)
        if (p <= 0f) return@onDrawWithContent
        segLeft.reset()
        segRight.reset()
        mLeft.getSegment(0f, mLeft.length * p, segLeft, true)
        mRight.getSegment(0f, mRight.length * p, segRight, true)
        drawPath(segLeft, color, style = track)
        drawPath(segRight, color, style = track)
        if (p < 1f) {
            drawCircle(color, radius = stroke * 1.1f, center = mLeft.getPosition(mLeft.length * p))
            drawCircle(color, radius = stroke * 1.1f, center = mRight.getPosition(mRight.length * p))
        }
    }
}
