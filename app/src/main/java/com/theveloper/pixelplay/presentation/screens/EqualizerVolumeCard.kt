package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One volume slider for the whole output: 0..100% is the device's media volume, and past
 * 100% it keeps the device at maximum and adds Extreme gain in the playback engine
 * (up to [maxBoostDb]), with a limiter so it doesn't clip.
 *
 * Level scale used here: 0..1 = media volume, 1..2 = Extreme.
 */
@Composable
internal fun ExtremeVolumeCard(
    systemVolume: Float,
    boostDb: Float,
    maxBoostDb: Float,
    onLevelChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val engineLevel = if (boostDb > 0.01f) 1f + (boostDb / maxBoostDb).coerceIn(0f, 1f) else systemVolume.coerceIn(0f, 1f)
    // While dragging, the thumb follows the finger; the media volume only has ~15-25 steps.
    var dragLevel by remember { mutableStateOf<Float?>(null) }
    val level = dragLevel ?: engineLevel
    val isExtreme = level > 1.001f
    val percent = (level * 100f).roundToInt()
    val extraDb = ((level - 1f).coerceAtLeast(0f) * maxBoostDb)

    val accent by animateColorAsState(
        targetValue = if (isExtreme) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        animationSpec = tween(250),
        label = "VolumeAccent"
    )
    val accentContainer by animateColorAsState(
        targetValue = if (isExtreme) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = tween(250),
        label = "VolumeAccentContainer"
    )
    val onAccentContainer by animateColorAsState(
        targetValue = if (isExtreme) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        animationSpec = tween(250),
        label = "VolumeOnAccentContainer"
    )

    val stateText = if (isExtreme) {
        stringResource(R.string.equalizer_volume_extreme_state, percent, extraDb)
    } else {
        stringResource(R.string.common_percentage_text, percent)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accentContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExtreme) Icons.Rounded.Bolt else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = onAccentContainer
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.equalizer_volume_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isExtreme) {
                            stringResource(R.string.equalizer_volume_extreme_subtitle, extraDb)
                        } else {
                            stringResource(R.string.equalizer_volume_subtitle)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.common_percentage_text, percent),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }

            val volumeLabel = stringResource(R.string.equalizer_volume_title)
            Slider(
                value = level,
                onValueChange = { raw ->
                    // Light snap to 100% so it's easy to leave Extreme.
                    val snapped = if (abs(raw - 1f) < 0.025f) 1f else raw
                    val previous = dragLevel ?: engineLevel
                    if ((previous <= 1f) != (snapped <= 1f)) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    dragLevel = snapped
                    onLevelChange(snapped)
                },
                onValueChangeFinished = { dragLevel = null },
                valueRange = 0f..2f,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = volumeLabel
                        stateDescription = stateText
                    }
            )

            // Scale under the slider: device range on the left half, Extreme on the right.
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.common_percentage_text, 0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.common_percentage_text, 100),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.equalizer_volume_extreme),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            AnimatedVisibility(
                visible = isExtreme,
                enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.equalizer_volume_extreme_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}
