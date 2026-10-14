package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion

/** Fewer finds than this and the section stays hidden. */
const val MinFreshFindsToShow = 6

/**
 * Fresh Finds: this week's ~30 songs you've never played (friends' plays you haven't heard, and
 * library artists you've never played). Refreshes Monday. Shuffle plays them in random order;
 * long-press a song for "Not for me".
 */
@Composable
fun FreshFindsSection(
    finds: List<FreshFind>,
    isNew: Boolean,
    currentSongId: String?,
    isPlaying: Boolean,
    onShuffle: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongMoreClick: (Song) -> Unit,
    onNotForMe: (Song) -> Unit,
    modifier: Modifier = Modifier,
    /** Hold Shuffle: schedule the shuffle with Soon. Null = tap only. */
    onShuffleLongClick: (() -> Unit)? = null
) {
    val reducedMotion = rememberSystemReducedMotion()
    AnimatedVisibility(
        visible = finds.size >= MinFreshFindsToShow,
        enter = if (reducedMotion) EnterTransition.None else
            fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)) +
                expandVertically(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
        exit = if (reducedMotion) ExitTransition.None else
            fadeOut(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)) +
                shrinkVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)),
        modifier = modifier,
        label = "freshFinds"
    ) {
        val pages = remember(finds) { finds.chunked(QuickPicksRowsPerPage).take(8) }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Fresh Finds",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        if (isNew) {
                            Spacer(Modifier.width(8.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) { Text("New", modifier = Modifier.padding(horizontal = 2.dp)) }
                        }
                    }
                }
                // Tap = shuffle (with the spin), hold = Soon.
                com.theveloper.pixelplay.presentation.components.subcomps.ShuffleSoonButton(
                    label = androidx.compose.ui.res.stringResource(R.string.home_shuffle_label),
                    onClick = onShuffle,
                    onLongClick = onShuffleLongClick,
                    longClickLabel = androidx.compose.ui.res.stringResource(R.string.home_shuffle_soon_hold)
                )
            }

            val pagerState = rememberPagerState(initialPage = 0) { pages.size }
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(start = 16.dp, end = 40.dp),
                pageSpacing = 10.dp,
                snapPosition = SnapPosition.Start,
                modifier = Modifier.fillMaxWidth(),
                key = { index -> "fresh_page_$index" }
            ) { pageIndex ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    pages[pageIndex].forEach { find ->
                        QuickPickRow(
                            song = find.song,
                            isCurrent = find.song.id == currentSongId,
                            isPlaying = isPlaying,
                            onClick = { onSongClick(find.song) },
                            onMoreClick = { onSongMoreClick(find.song) },
                            // Only a friend is worth a line; library finds just show the artist.
                            subtitle = find.why?.let { "${find.song.displayArtist} · $it" },
                            badgeImageUrl = find.friendAvatarUrl,
                            secondaryAction = "Not for me" to { onNotForMe(find.song) }
                        )
                    }
                }
            }
        }
    }
}
