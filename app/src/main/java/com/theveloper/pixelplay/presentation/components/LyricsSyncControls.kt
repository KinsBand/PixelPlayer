package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import androidx.compose.ui.unit.sp

@Composable
fun LyricsSyncControls(
    modifier: Modifier = Modifier,
    offsetMillis: Int,
    onOffsetChange: (Int) -> Unit,
    backgroundColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    onBackgroundColor: Color,
    /** The offset was measured automatically (no manual offset for this song). */
    isAutomatic: Boolean = false,
    /** The user has set an offset for this song by hand (possibly 0). */
    hasManualOffset: Boolean = false,
    /** Drops the manual offset so the automatic one (if any) applies again. */
    onResetToAutomatic: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(
                color = backgroundColor,
                shape = CircleShape
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // -0.5s
        SyncButton(
            text = stringResource(R.string.lyrics_offset_minus_half),
            onClick = { onOffsetChange(offsetMillis - 500) },
            weight = 1f,
            containerColor = accentColor,
            contentColor = onAccentColor
        )
        // -0.1s
        SyncButton(
            text = stringResource(R.string.lyrics_offset_minus_point_one),
            onClick = { onOffsetChange(offsetMillis - 100) },
            weight = 1f,
            containerColor = accentColor,
            contentColor = onAccentColor
        )
        // Center Display / Reset.
        // Automatic offset: shows "Auto +0.3s"; tapping sets 0 for this song (turns it off).
        // Manual offset: tapping goes back to the automatic offset, or to 0 when there is none.
        val highlighted = offsetMillis != 0 || hasManualOffset
        SyncButton(
            text = when {
                isAutomatic -> stringResource(R.string.lyrics_offset_auto_fmt, offsetMillis / 1000f)
                offsetMillis == 0 -> stringResource(R.string.lyrics_offset_zero)
                else -> stringResource(R.string.lyrics_offset_seconds_fmt, offsetMillis / 1000f)
            },
            onClick = {
                when {
                    isAutomatic -> onOffsetChange(0)
                    hasManualOffset && onResetToAutomatic != null -> onResetToAutomatic()
                    else -> onOffsetChange(0)
                }
            },
            weight = if (isAutomatic) 1.6f else 1.3f, // Slightly wider
            containerColor = if (highlighted) accentColor else backgroundColor,
            contentColor = if (highlighted) onAccentColor else onBackgroundColor,
            enabled = highlighted,
            fontSize = 12.sp
        )
        // +0.1s
        SyncButton(
            text = stringResource(R.string.lyrics_offset_plus_point_one),
            onClick = { onOffsetChange(offsetMillis + 100) },
            weight = 1f,
            containerColor = accentColor,
            contentColor = onAccentColor
        )
        // +0.5s
        SyncButton(
            text = stringResource(R.string.lyrics_offset_plus_half),
            onClick = { onOffsetChange(offsetMillis + 500) },
            weight = 1f,
            containerColor = accentColor,
            contentColor = onAccentColor
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SyncButton(
    text: String,
    onClick: () -> Unit,
    weight: Float,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight(),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor
        ),
        contentPadding = PaddingValues(0.dp) // Tight padding
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
