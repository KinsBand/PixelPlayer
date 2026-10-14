package com.theveloper.pixelplay.presentation.screens.artist

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.components.CollectionCardSkeleton
import com.theveloper.pixelplay.presentation.components.ShimmerBox
import com.theveloper.pixelplay.presentation.components.SkeletonTextLine
import com.theveloper.pixelplay.ui.theme.MotionTokens

/*
 * Loading states for the artist page. Every section shows a skeleton the same size as its real
 * content until its own data arrives, then crossfades in place, so the page doesn't move under
 * your finger while the rest is still loading.
 */

/** Where a section is in loading. */
enum class ArtistSectionState { LOADING, READY, EMPTY }

fun artistSectionState(loaded: Boolean, isEmpty: Boolean): ArtistSectionState = when {
    !loaded -> ArtistSectionState.LOADING
    isEmpty -> ArtistSectionState.EMPTY
    else -> ArtistSectionState.READY
}

/**
 * Skeleton → content, crossfaded in place. Sizes match, so the swap doesn't move anything; if a
 * real section ends up a little taller (two-line titles, an extra decade), the size change is
 * eased rather than snapped.
 */
@Composable
fun ArtistSectionReveal(
    ready: Boolean,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = ready,
        transitionSpec = {
            fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)) using
                SizeTransform(clip = false) { _, _ -> tween(MotionTokens.DurationMedium1, easing = MotionTokens.Emphasized) }
        },
        contentAlignment = Alignment.TopStart,
        label = "artistSectionReveal"
    ) { isReady ->
        if (isReady) content() else Box(Modifier.clearAndSetSemantics { }) { skeleton() }
    }
}

/**
 * Ignores taps that start just after the page layout shifted (a section collapsed because it came
 * back empty), so a row that slid under your finger isn't opened by accident. Scrolling still
 * works: only the release that would complete a click is consumed.
 */
fun Modifier.tapGuard(guardUntil: MutableLongState): Modifier = pointerInput(guardUntil) {
    awaitPointerEventScope {
        var blockThisPress = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            when (event.type) {
                PointerEventType.Press -> blockThisPress = SystemClock.uptimeMillis() < guardUntil.longValue
                PointerEventType.Release -> if (blockThisPress) {
                    event.changes.forEach { it.consume() }
                    blockThisPress = false
                }
                else -> Unit
            }
        }
    }
}

/** A tap guard that arms for [MotionTokens.DurationMedium2] every time [shiftKey] changes. */
@Composable
fun rememberTapGuard(shiftKey: Any?): MutableLongState {
    val guardUntil = remember { mutableLongStateOf(0L) }
    val last = remember { arrayOf(shiftKey) }
    LaunchedEffect(shiftKey) {
        if (last[0] != shiftKey) {
            last[0] = shiftKey
            guardUntil.longValue = SystemClock.uptimeMillis() + MotionTokens.DurationMedium2
        }
    }
    return guardUntil
}

// ---- Skeletons, one per section, sized like the real thing ------------------------------------

/** A horizontal row of [count] skeleton cards with the carousels' spacing. */
@Composable
private fun CarouselSkeleton(count: Int = 4, card: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
    ) {
        repeat(count) { card() }
    }
}

/** Albums / Singles / Mix albums: [CollectionCardSkeleton]s. */
@Composable
fun ReleaseCarouselSkeleton() = CarouselSkeleton { CollectionCardSkeleton() }

/** Appears on, Fans also like → Songs: RelatedSongCard's footprint (136dp square + two lines). */
@Composable
fun SongCardCarouselSkeleton() = CarouselSkeleton {
    Column(Modifier.width(136.dp).padding(bottom = 6.dp)) {
        ShimmerBox(Modifier.size(136.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(8.dp))
        SkeletonTextLine(MaterialTheme.typography.bodyMedium, fraction = 0.8f)
        Spacer(Modifier.height(2.dp))
        SkeletonTextLine(MaterialTheme.typography.labelSmall, fraction = 0.5f)
    }
}

/** Fans also like → Artists: RelatedArtistCard's footprint (90dp circle + name + fans). */
@Composable
fun ArtistCircleCarouselSkeleton() = CarouselSkeleton(count = 5) {
    Column(Modifier.width(100.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ShimmerBox(Modifier.size(90.dp).clip(CircleShape))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(80.dp)) { SkeletonTextLine(MaterialTheme.typography.bodySmall, fraction = 1f) }
        Spacer(Modifier.height(2.dp))
        Box(Modifier.width(50.dp)) { SkeletonTextLine(MaterialTheme.typography.labelSmall, fraction = 1f) }
    }
}

/** Music videos: ArtistVideoCard's footprint (16:9 thumbnail, two title lines, channel). */
@Composable
fun VideoCarouselSkeleton() = CarouselSkeleton(count = 2) {
    Column(Modifier.width(224.dp).padding(bottom = 6.dp)) {
        ShimmerBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(6.dp))
        SkeletonTextLine(MaterialTheme.typography.bodyMedium, lines = 2, fraction = 0.9f)
        SkeletonTextLine(MaterialTheme.typography.labelSmall, fraction = 0.4f)
    }
}

/** All songs → Show all: the FilledTonalButton's footprint (40dp, full width). */
@Composable
fun ShowAllButtonSkeleton() {
    ShimmerBox(Modifier.fillMaxWidth().height(40.dp).clip(CircleShape))
}

/** Timeline: a decade label and [rows] TimelineRow-sized rows. */
@Composable
fun TimelineSkeleton(rows: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.padding(top = 4.dp).width(120.dp)) {
            SkeletonTextLine(MaterialTheme.typography.labelLarge, fraction = 1f)
        }
        repeat(rows) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.width(40.dp)) { SkeletonTextLine(MaterialTheme.typography.labelLarge, fraction = 0.9f) }
                ShimmerBox(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.weight(1f)) {
                    SkeletonTextLine(MaterialTheme.typography.bodyMedium, fraction = 0.6f)
                    SkeletonTextLine(MaterialTheme.typography.labelSmall, fraction = 0.35f)
                }
            }
        }
    }
}

/** Genres & moods: a row of chip-sized blocks (AssistChip is 32dp tall). */
@Composable
fun GenreChipsSkeleton() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        listOf(72.dp, 96.dp, 64.dp, 84.dp).forEach { w ->
            ShimmerBox(Modifier.width(w).height(32.dp).clip(MaterialTheme.shapes.small))
        }
    }
}

/** About: AboutCard's footprint (four bio lines, then the source row). */
@Composable
fun AboutSkeleton() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column {
            repeat(4) { i -> SkeletonTextLine(MaterialTheme.typography.bodyMedium, fraction = if (i == 3) 0.55f else 0.95f) }
        }
        Box(Modifier.height(24.dp).width(110.dp), contentAlignment = Alignment.CenterStart) {
            SkeletonTextLine(MaterialTheme.typography.labelSmall, fraction = 1f)
        }
    }
}
