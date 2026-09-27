package com.theveloper.pixelplay.presentation.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.theveloper.pixelplay.data.model.Song

import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

// Local colors for Olive/Mustard theme
val OliveDarker = Color(0xFF1E2314)
val OliveAccentYellow = Color(0xFFD4B13B) // Mustard
val OliveCream = Color(0xFFF7F5EC)
val OliveNeonGreen = Color(0xFF39FF14)

@Composable
fun VinylSleeveCard(
    songs: List<Song>,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onOpenDailyMix: () -> Unit,
    onPlayShuffled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    // Rotation animation
    val rotationAngle by if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "VinylRotation")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val customSleeveShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 34.dp,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = 24.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = 24.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = 24.dp,
            smoothnessAsPercentBR = 60
        )
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .aspectRatio(1f)
            .clip(customSleeveShape)
            .background(OliveDarker)
            .clickable { onOpenDailyMix() }
    ) {
        // Draw the Vinyl Record in the center
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(0.75f)
                .graphicsLayer(rotationZ = rotationAngle)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = this.center
                val outerRadius = size.minDimension / 2f
                
                // 1. Vinyl base (Dark grey/black circle)
                drawCircle(
                    color = Color(0xFF111111),
                    radius = outerRadius,
                    center = center
                )
                
                // 2. Grooves (Concentric rings)
                val grooveCount = 8
                val step = (outerRadius - (outerRadius * 0.35f)) / grooveCount
                for (i in 0 until grooveCount) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = (outerRadius * 0.35f) + (i * step),
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // 3. Cream label circle
                val labelRadius = outerRadius * 0.35f
                drawCircle(
                    color = OliveCream,
                    radius = labelRadius,
                    center = center
                )

                // 4. Arc text "YOUR MIX" at the top of the label
                val textPathRadius = labelRadius * 0.7f
                val path = Path().apply {
                    addArc(
                        oval = Rect(
                            center.x - textPathRadius,
                            center.y - textPathRadius,
                            center.x + textPathRadius,
                            center.y + textPathRadius
                        ),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 180f
                    )
                }

                val paint = Paint().apply {
                    color = OliveDarker.toArgb()
                    textSize = with(density) { 10.sp.toPx() }
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                
                // We use nativeCanvas to draw the text on the path
                drawContext.canvas.nativeCanvas.drawTextOnPath(
                    "YOUR MIX",
                    path.asAndroidPath(),
                    0f,
                    0f,
                    paint
                )

                // 5. Center spindle hole
                drawCircle(
                    color = OliveDarker,
                    radius = outerRadius * 0.08f,
                    center = center
                )
            }
        }

        // Thumbnails around the vinyl
        val thumbs = songs.take(5)
        thumbs.forEachIndexed { index, song ->
            val alignment = when (index) {
                0 -> Alignment.TopStart
                1 -> Alignment.TopEnd
                2 -> Alignment.BottomStart
                3 -> Alignment.BottomEnd
                else -> Alignment.CenterStart
            }
            val offset = when (index) {
                0 -> IntOffset(16, 16)
                1 -> IntOffset(-16, 16)
                2 -> IntOffset(16, -16)
                3 -> IntOffset(-16, -16)
                else -> IntOffset(12, 0)
            }

            Box(
                modifier = Modifier
                    .align(alignment)
                    .offset(offset.x.dp, offset.y.dp)
                    .zIndex(2f)
                    .size(44.dp)
                    .border(2.dp, OliveDarker, CircleShape)
                    .clip(CircleShape)
                    .clickable { onSongClick(song) }
            ) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = song.title,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Center neon-green FAB with shuffle icon (middle of the vinyl)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(3f)
        ) {
            FloatingActionButton(
                onClick = onPlayShuffled,
                containerColor = OliveNeonGreen,
                contentColor = OliveDarker,
                shape = CircleShape,
                modifier = Modifier
                    .size(52.dp)
                    .border(2.dp, OliveDarker, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle Your Mix",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
