package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/*
 * Skeleton placeholders shown while a screen's data loads. They mirror the real layout
 * (same header height, art size and row height) so nothing jumps when content arrives.
 *
 * Wrap them in [DelayedSkeleton]: a load that finishes within ~120 ms shows nothing at all
 * (no flash), a slower one fades the skeleton in.
 */

/** Shows [content] only if it is still composed after [delayMs], fading it in over 200 ms. */
@Composable
fun DelayedSkeleton(
    modifier: Modifier = Modifier,
    delayMs: Long = SKELETON_DELAY_MS,
    content: @Composable () -> Unit
) {
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        visible.targetState = true
    }
    AnimatedVisibility(
        visibleState = visible,
        modifier = modifier,
        enter = fadeIn(tween(SKELETON_FADE_MS))
    ) {
        content()
    }
}

/** One shimmering block. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    ShimmerBox(modifier = modifier.clip(shape))
}

/** A song row: art, title and subtitle lines (matches EnhancedSongListItem's footprint). */
@Composable
fun SkeletonSongRow(
    modifier: Modifier = Modifier,
    showArt: Boolean = true,
    titleFraction: Float = 0.62f
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArt) {
            SkeletonBlock(Modifier.size(48.dp), RoundedCornerShape(12.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(titleFraction).height(14.dp), RoundedCornerShape(7.dp))
            SkeletonBlock(Modifier.fillMaxWidth(titleFraction * 0.6f).height(11.dp), RoundedCornerShape(6.dp))
        }
        Spacer(Modifier.width(12.dp))
        SkeletonBlock(Modifier.size(24.dp), CircleShape)
    }
}

/**
 * Album / playlist / artist page while it loads: a header block the size of the collapsing
 * header, the title lines, then [rows] song rows.
 */
@Composable
fun DetailScreenSkeleton(
    modifier: Modifier = Modifier,
    headerHeight: Dp = 300.dp,
    rows: Int = 8,
    circularArt: Boolean = false,
    showRowArt: Boolean = false
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
        ) {
            if (circularArt) {
                SkeletonBlock(
                    Modifier
                        .align(Alignment.Center)
                        .statusBarsPadding()
                        .size(headerHeight * 0.55f),
                    CircleShape
                )
            } else {
                SkeletonBlock(Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SkeletonBlock(Modifier.width(210.dp).height(26.dp), RoundedCornerShape(10.dp))
                SkeletonBlock(Modifier.width(140.dp).height(14.dp), RoundedCornerShape(7.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(rows) { index ->
                SkeletonSongRow(showArt = showRowArt, titleFraction = SKELETON_TITLE_WIDTHS[index % SKELETON_TITLE_WIDTHS.size])
            }
        }
    }
}

/** A Home shelf: section title and a row of square cards. */
@Composable
fun ShelfSkeleton(
    modifier: Modifier = Modifier,
    cardSize: Dp = 150.dp,
    cards: Int = 3
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBlock(Modifier.width(160.dp).height(20.dp), RoundedCornerShape(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(cards) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.size(cardSize).aspectRatio(1f), RoundedCornerShape(18.dp))
                    SkeletonBlock(Modifier.width(cardSize * 0.7f).height(12.dp), RoundedCornerShape(6.dp))
                }
            }
        }
    }
}

private val SKELETON_TITLE_WIDTHS = floatArrayOf(0.62f, 0.48f, 0.7f, 0.55f, 0.4f, 0.66f)
const val SKELETON_DELAY_MS = 120L
private const val SKELETON_FADE_MS = 200
