package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R

@Composable
fun SourceBadge(
    source: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp,
    containerSize: Dp = 26.dp
) {
    val (iconRes, description, tint) = when {
        source.equals("SPOTIFY", ignoreCase = true) -> Triple(R.drawable.ic_source_spotify, "Spotify", Color(0xFF1DB954))
        source.equals("YOUTUBE_MUSIC", ignoreCase = true) || source.equals("YOUTUBE", ignoreCase = true) ->
            Triple(R.drawable.ic_source_youtube_music, "YouTube Music", Color(0xFFFF0000))
        source.equals("APPLE_MUSIC", ignoreCase = true) -> Triple(R.drawable.ic_source_apple_music, "Apple Music", Color(0xFFFA243C))
        else -> return
    }

    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.12f),
        modifier = modifier.size(containerSize)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(containerSize)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
