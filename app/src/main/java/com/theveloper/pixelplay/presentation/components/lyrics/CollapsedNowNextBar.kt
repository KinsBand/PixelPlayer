package com.theveloper.pixelplay.presentation.components.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage

/**
 * Collapsed form of the lyrics header: one compact bar, Now on the left (at most half the
 * width, visually dominant) and Next on the right (quieter). Tapping the Next half skips to that
 * song straight away. The expand button restores the full header card. Face-to-face mode uses the
 * bar without the expand button, and tapping its current song leaves face-to-face ([onNowClick]).
 *
 * When the queue moves forward (the song that was Next becomes Now), both halves slide left so the
 * bar reads as the queue progressing; any other change (previous, a new queue) just crossfades.
 */
@Composable
internal fun CollapsedNowNextBar(
    currentSong: Song?,
    nextSong: Song?,
    isPlaying: Boolean,
    backgroundColor: Color,
    contentColor: Color,
    nextContainerColor: Color,
    nextContentColor: Color,
    accentColor: Color,
    onExpand: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 0..1 alpha for the quieter Next half when the screen has been idle. */
    nextAlpha: () -> Float = { 1f },
    /** Tap on the current song (cover + title). Not clickable when null. */
    onNowClick: (() -> Unit)? = null,
    /** Spoken action for [onNowClick], e.g. "Leave face-to-face lyrics". */
    nowClickLabel: String? = null,
    /** Face-to-face has no room for the full card, so it hides the expand button. */
    showExpand: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current

    // "Did the queue just move forward?" = the new current song is the one we showed as Next.
    val lastNextId = remember { LastIdHolder() }
    val progressed = remember(currentSong?.id) {
        currentSong != null && currentSong.id == lastNextId.id
    }
    SideEffect { lastNextId.id = nextSong?.id }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val halfWidth = maxWidth * 0.5f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .clip(CircleShape)
                .background(backgroundColor),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Now ──
            AnimatedContent(
                targetState = currentSong,
                contentKey = { it?.id },
                transitionSpec = { nowNextTransition(progressed) },
                modifier = Modifier.widthIn(max = halfWidth),
                label = "collapsedNow"
            ) { song ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .then(
                            if (onNowClick != null) {
                                Modifier
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button, onClickLabel = nowClickLabel) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNowClick()
                                    }
                            } else Modifier
                        )
                        .padding(start = 6.dp, end = if (showExpand) 0.dp else 14.dp)
                ) {
                    SmartImage(
                        model = song?.albumArtUriString ?: R.drawable.rounded_album_24,
                        shape = CircleShape,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = song?.title.orEmpty(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (showExpand) {
                        IconButton(
                            onClick = onExpand,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Expand song details",
                                tint = contentColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // ── Next ── (the whole surface is one button: skip to this song now)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                AnimatedContent(
                    targetState = nextSong,
                    contentKey = { it?.id },
                    transitionSpec = { nowNextTransition(progressed) },
                    label = "collapsedNext"
                ) { song ->
                    if (song != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(nextContainerColor.copy(alpha = nextContainerColor.alpha * nextAlpha()))
                                .clickable(role = Role.Button) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNextClick()
                                }
                                .semantics { contentDescription = "Play next song now: ${song.title}" }
                                .padding(start = 4.dp, end = 12.dp)
                        ) {
                            SmartImage(
                                model = song.albumArtUriString ?: R.drawable.rounded_album_24,
                                shape = RoundedCornerShape(8.dp),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                contentScale = ContentScale.Crop,
                                alpha = nextAlpha()
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Next",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor.copy(alpha = 0.9f * nextAlpha()),
                                    maxLines = 1
                                )
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = nextContentColor.copy(alpha = 0.85f * nextAlpha()),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private val BarHeight = 52.dp

private class LastIdHolder { var id: String? = null }

private fun nowNextTransition(progressed: Boolean): ContentTransform =
    if (progressed) {
        // The queue moved on: everything travels left, towards "Now".
        (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } +
            fadeIn(tween(260, delayMillis = 60)) +
            scaleIn(initialScale = 0.9f, animationSpec = tween(320)))
            .togetherWith(
                slideOutHorizontally(tween(260)) { -it / 3 } + fadeOut(tween(180))
            )
    } else {
        (fadeIn(tween(300)) + scaleIn(initialScale = 0.94f, animationSpec = tween(300)))
            .togetherWith(fadeOut(tween(220)))
    }
