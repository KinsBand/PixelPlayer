package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * Condensed replacement for [StandardScreenTopBar] on Home.
 *
 * Greeting on the left; on the right a microphone action for voice and audio
 * song search sitting immediately beside the settings action, on a single 56dp
 * row with a 26sp title instead of the 40sp / ~112dp header, so the Daily Mix
 * bar is above the fold. Colours come from the active Material You scheme
 * rather than fixed values.
 *
 * [onVoiceSearchClick] is nullable so call sites that have no voice search
 * wired yet simply do not show the microphone, rather than failing to compile.
 */
@Composable
fun CondensedHomeTopBar(
    title: String,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onVoiceSearchClick: (() -> Unit)? = null,
    isListening: Boolean = false
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.primaryContainer.copy(alpha = 0.4f))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 20.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.primary,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onVoiceSearchClick != null) {
                    // While a capture is running the button breathes, so the
                    // top bar carries the "we are listening" state even when
                    // the sheet is scrolled behind something else.
                    val pulse = rememberInfiniteTransition(label = "micPulse")
                    val scale by if (isListening) {
                        pulse.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.12f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(620),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "micPulseScale"
                        )
                    } else {
                        animateFloatAsState(targetValue = 1f, label = "micRest")
                    }

                    FilledIconButton(
                        onClick = onVoiceSearchClick,
                        modifier = Modifier
                            .size(40.dp)
                            .scale(scale),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isListening) colors.primary
                            else colors.primaryContainer,
                            contentColor = if (isListening) colors.onPrimary
                            else colors.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_mic_24),
                            contentDescription = stringResource(
                                R.string.voice_search_cd_open
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                FilledIconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_settings_24),
                        contentDescription = stringResource(R.string.library_cd_open_settings),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
