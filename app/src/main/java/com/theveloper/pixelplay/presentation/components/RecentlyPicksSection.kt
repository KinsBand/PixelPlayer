package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song

/** Which of the two lists the selector is showing. */
enum class RecentlyMode { Listened, Liked }

/**
 * The "Recently" block that sits under the speed dial row.
 *
 * A small "Recently" label sits above a two-button selector — Listened on the
 * left, Liked on the right — and the list below is rendered with the same row
 * and paging as Quick picks, so the two sections read as one family.
 */
@Composable
fun RecentlyPicksSection(
    listenedSongs: List<Song>,
    likedSongs: List<Song>,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongClick: (Song, RecentlyMode) -> Unit,
    onSongMoreClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    maxPages: Int = 5
) {
    var mode by rememberSaveable { mutableStateOf(RecentlyMode.Listened) }
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()

    val songs = remember(mode, listenedSongs, likedSongs) {
        if (mode == RecentlyMode.Listened) listenedSongs else likedSongs
    }
    val pages = remember(songs, maxPages) {
        songs.chunked(QuickPicksRowsPerPage)
            .filter { it.isNotEmpty() }
            .take(maxPages)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Small label row above the selector.
        Text(
            text = "Recently",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Two-button selector: Listened | Liked.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surfaceContainerHighest),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ToggleSegmentButton(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                active = mode == RecentlyMode.Listened,
                activeColor = colors.primary,
                inactiveColor = colors.surfaceContainerHighest,
                activeContentColor = colors.onPrimary,
                inactiveContentColor = colors.onSurfaceVariant,
                activeCornerRadius = 22.dp,
                onClick = { mode = RecentlyMode.Listened },
                text = "Listened"
            )
            ToggleSegmentButton(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                active = mode == RecentlyMode.Liked,
                activeColor = colors.primary,
                inactiveColor = colors.surfaceContainerHighest,
                activeContentColor = colors.onPrimary,
                inactiveContentColor = colors.onSurfaceVariant,
                activeCornerRadius = 22.dp,
                onClick = { mode = RecentlyMode.Liked },
                text = "Liked"
            )
        }

        // The list — same rows and same paging as Quick picks.
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 90)) +
                    slideInVertically(tween(220, delayMillis = 90)) { it / 3 })
                    .togetherWith(
                        fadeOut(tween(90)) + slideOutVertically(tween(90)) { it / 3 }
                    )
            },
            label = "RecentlyModeSwitch"
        ) { targetMode ->
            if (pages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (targetMode == RecentlyMode.Listened) {
                            "Nothing played yet."
                        } else {
                            "No liked tracks yet."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        count = pages.size,
                        key = { index -> "recently_${targetMode.name}_page_$index" }
                    ) { index ->
                        Column(
                            modifier = Modifier.fillParentMaxWidth(0.88f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            pages[index].forEach { song ->
                                QuickPickRow(
                                    song = song,
                                    isCurrent = song.id == currentSongId,
                                    isPlaying = isPlaying,
                                    onClick = { onSongClick(song, targetMode) },
                                    onMoreClick = { onSongMoreClick(song) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
