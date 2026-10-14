package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon

internal const val QuickPicksRowsPerPage = 4

/**
 * Quick picks — four stacked song rows per page, paged horizontally with the
 * next page peeking at the right edge. No avatar beside the heading.
 */
@Composable
fun QuickPicksSection(
    songs: List<Song>,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onSongMoreClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Quick picks",
    /** Optional caption under the title (e.g. a mood fallback note). */
    subtitle: String? = null,
    /** Changing this crossfades the rows (e.g. the Home mood); same key = in-place update. */
    contentKey: Any? = null,
    maxPages: Int = 5,
    /** Shuffle-plays the picks; null hides the button beside the title. */
    onShuffleClick: (() -> Unit)? = null,
    /** Hold Shuffle: schedule the shuffle with Soon. Null = tap only. */
    onShuffleLongClick: (() -> Unit)? = null
) {
    if (songs.isEmpty()) return

    val pages = remember(songs, maxPages) {
        songs.chunked(QuickPicksRowsPerPage)
            .filter { it.isNotEmpty() }
            .take(maxPages)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Title (and caption) on the left, shuffle on the right of the same row.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onShuffleClick != null) {
                // Same button as Fresh Finds' Shuffle: tap = shuffle (with the spin), hold = Soon.
                com.theveloper.pixelplay.presentation.components.subcomps.ShuffleSoonButton(
                    label = androidx.compose.ui.res.stringResource(R.string.home_shuffle_label),
                    onClick = onShuffleClick,
                    onLongClick = onShuffleLongClick,
                    longClickLabel = androidx.compose.ui.res.stringResource(R.string.home_shuffle_soon_hold)
                )
            }
        }

        val reducedMotion = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
        androidx.compose.animation.AnimatedContent(
            targetState = contentKey to pages,
            contentKey = { it.first },
            transitionSpec = {
                if (reducedMotion) {
                    androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                } else {
                    androidx.compose.animation.fadeIn(
                        androidx.compose.animation.core.tween(
                            com.theveloper.pixelplay.ui.theme.MotionTokens.DurationMedium1,
                            easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedDecelerate
                        )
                    ) togetherWith androidx.compose.animation.fadeOut(
                        androidx.compose.animation.core.tween(
                            com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort3,
                            easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedAccelerate
                        )
                    )
                }
            },
            label = "quickPicksContent"
        ) { (targetContentKey, shownPages) ->
            // One pager state per mood, so a crossfade never shares a state between two rows and a
            // new mood starts from the first page.
            val pagerState = androidx.compose.runtime.key(targetContentKey) {
                rememberPagerState(initialPage = 0) { shownPages.size }
            }
            LaunchedEffect(targetContentKey) {
                pagerState.scrollToPage(0)
            }
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(start = 16.dp, end = 40.dp),
                pageSpacing = 10.dp,
                snapPosition = SnapPosition.Start,
                modifier = Modifier.fillMaxWidth(),
                key = { index -> "quick_pick_page_$index" }
            ) { pageIndex ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    shownPages[pageIndex].forEach { song ->
                        QuickPickRow(
                            song = song,
                            isCurrent = song.id == currentSongId,
                            isPlaying = isPlaying,
                            onClick = { onSongClick(song) },
                            onMoreClick = { onSongMoreClick(song) }
                        )
                    }
                }
            }
        }
    }
}

/** One song row. Shared with [RecentlyPicksSection] so both lists read identically. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QuickPickRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Replaces the default "artist · N plays" line (e.g. a "why" line or a year). */
    subtitle: String? = null,
    /** Small round picture over the cover's corner (e.g. the friend who played it). */
    badgeImageUrl: String? = null,
    /** Extra action reachable from TalkBack (e.g. "Not for me"); hold always opens the options. */
    secondaryAction: Pair<String, () -> Unit>? = null,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .songStartFeedback(song.id, RoundedCornerShape(14.dp))
            .offlineVeil(song, RoundedCornerShape(14.dp))
            .background(
                if (isCurrent) colors.primaryContainer.copy(alpha = 0.45f) else colors.surface
            )
            // No ⋮ button: hold the row for the song's options.
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMoreClick,
                onLongClickLabel = "Song options"
            )
            .then(
                if (secondaryAction != null) Modifier.semantics {
                    customActions = listOf(CustomAccessibilityAction(secondaryAction.first) { secondaryAction.second(); true })
                } else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            SmartImage(
                model = song.albumArtUriString,
                contentDescription = null,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(52.dp)
            )
            if (badgeImageUrl != null) {
                SmartImage(
                    model = badgeImageUrl,
                    contentDescription = null,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                )
            }
            if (isCurrent && isPlaying) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.scrim.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    PlayingEqIcon(
                        modifier = Modifier.size(20.dp),
                        color = colors.onPrimaryContainer
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle ?: quickPickSubtitle(song),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * "Artist · 348 plays" when we have a play count, otherwise just the artist —
 * mirrors the reference layout without inventing a number we don't have.
 */
internal fun quickPickSubtitle(song: Song): String {
    val plays = song.userActivityStats.playCount
    return if (plays > 0) {
        "${song.displayArtist} · $plays ${if (plays == 1) "play" else "plays"}"
    } else {
        song.displayArtist
    }
}
