package com.theveloper.pixelplay.presentation.components

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun DailyMixBannerHero(
    songs: List<Song>,
    isPlaying: Boolean,
    onOpenDailyMix: () -> Unit,
    onPlayShuffled: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Rotation animation for vinyl record
    val rotationAngle by if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "VinylRotationBanner")
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

    val bannerShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 28.dp,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = 28.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = 14.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = 14.dp,
            smoothnessAsPercentBR = 60
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(bannerShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF262C1A),
                        Color(0xFF161A10),
                        Color(0xFF10130C)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = OliveAccentYellow.copy(alpha = 0.25f),
                shape = bannerShape
            )
            .clickable { onOpenDailyMix() }
            .padding(18.dp)
    ) {
        // Right Side: Vinyl Record Peek Effect
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 24.dp)
                .size(160.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(rotationZ = rotationAngle)
            ) {
                val center = this.center
                val outerRadius = size.minDimension / 2f

                // Base Vinyl
                drawCircle(
                    color = Color(0xFF111111),
                    radius = outerRadius,
                    center = center
                )

                // Concentric Grooves
                val grooveCount = 6
                val step = (outerRadius - (outerRadius * 0.35f)) / grooveCount
                for (i in 0 until grooveCount) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        radius = (outerRadius * 0.35f) + (i * step),
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Center Cream Label
                drawCircle(
                    color = OliveCream,
                    radius = outerRadius * 0.35f,
                    center = center
                )

                // Center Hole
                drawCircle(
                    color = OliveDarker,
                    radius = outerRadius * 0.08f,
                    center = center
                )
            }

            // Shuffle Play Floating Button in the center of vinyl peek
            FloatingActionButton(
                onClick = onPlayShuffled,
                containerColor = OliveAccentYellow,
                contentColor = OliveDarker,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
                    .border(2.dp, OliveDarker, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.common_shuffle_play),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Left Side: Title, Subtitle, and Tag Badges
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.68f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Daily Mix",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp
                    ),
                    color = OliveCream
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (songs.isNotEmpty()) {
                        "${songs.size} tracks curated for you"
                    } else {
                        "Personalized daily playlist"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = OliveCream.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Tag badges row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OliveAccentYellow.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OliveAccentYellow.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Curated",
                        style = MaterialTheme.typography.labelSmall,
                        color = OliveAccentYellow,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "Auto-Refresh",
                        style = MaterialTheme.typography.labelSmall,
                        color = OliveCream.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
