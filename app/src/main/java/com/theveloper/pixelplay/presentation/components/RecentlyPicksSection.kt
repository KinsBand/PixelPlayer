package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion

/** Which list the "Back again" selector is showing. */
enum class RecentlyMode(val label: String, val queueName: String, val emptyText: String) {
    Listened("Recent", "Recently Played", "Nothing played yet."),
    Liked("Liked", "Your Likes", "No liked tracks yet."),
    Forgotten("Forgotten", "Forgotten favourites", "No forgotten favourites yet."),
    OnThisDay("On this day", "On this day", "Nothing from this day yet.")
}

/** A song for the On this day list and the year you played it. */
data class OnThisDaySong(val song: Song, val year: Int)

/**
 * "Back again": your own library resurfacing, in one shelf instead of four.
 * Recent · Liked · Forgotten (loved once, not played in 60 days) · On this day (played on this
 * date in an earlier year). Rows and paging match Quick picks. Modes with nothing in them are
 * hidden, except Recent, which always shows.
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
    forgottenSongs: List<Song> = emptyList(),
    onThisDaySongs: List<OnThisDaySong> = emptyList(),
    maxPages: Int = 5,
    /** Tapping the already-selected pill opens the full history for that mode. */
    onViewAllHistory: (RecentlyMode) -> Unit = {}
) {
    var mode by rememberSaveable { mutableStateOf(RecentlyMode.Listened) }
    val colors = MaterialTheme.colorScheme
    val reducedMotion = rememberSystemReducedMotion()

    val available = remember(likedSongs, forgottenSongs, onThisDaySongs) {
        RecentlyMode.entries.filter { m ->
            when (m) {
                RecentlyMode.Listened -> true
                RecentlyMode.Liked -> likedSongs.isNotEmpty()
                RecentlyMode.Forgotten -> forgottenSongs.isNotEmpty()
                RecentlyMode.OnThisDay -> onThisDaySongs.isNotEmpty()
            }
        }
    }
    val shownMode = if (mode in available) mode else RecentlyMode.Listened
    val yearById = remember(onThisDaySongs) { onThisDaySongs.associate { it.song.id to it.year } }

    fun songsFor(m: RecentlyMode): List<Song> = when (m) {
        RecentlyMode.Listened -> listenedSongs
        RecentlyMode.Liked -> likedSongs
        RecentlyMode.Forgotten -> forgottenSongs
        RecentlyMode.OnThisDay -> onThisDaySongs.map { it.song }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Title and the mode selector share one row: title left, compact segmented pill right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Back again",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(end = 12.dp)
            )
            // Compact segmented filter pill (Recent, Liked, …). Scrolls if all four don't fit.
            Surface(
                shape = CircleShape,
                color = colors.surfaceContainerHigh,
                tonalElevation = 2.dp,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    available.forEach { m ->
                        val selected = m == shownMode
                        val segmentColor by animateColorAsState(
                            targetValue = if (selected) colors.primary else Color.Transparent,
                            animationSpec = tween(MotionTokens.DurationShort4),
                            label = "back_again_segment_bg"
                        )
                        val labelColor by animateColorAsState(
                            targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                            animationSpec = tween(MotionTokens.DurationShort4),
                            label = "back_again_segment_fg"
                        )
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(CircleShape)
                                .background(segmentColor)
                                .clickable {
                                    if (selected) onViewAllHistory(m) else mode = m
                                }
                                .semantics {
                                    stateDescription = if (selected) "Showing ${m.label}, selected. Tap again to open all" else "Show ${m.label}"
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = m.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = labelColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // The list: same rows and paging as Quick picks. A plain crossfade, no slide.
        AnimatedContent(
            targetState = shownMode,
            transitionSpec = {
                if (reducedMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                        fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
                }
            },
            label = "BackAgainModeSwitch"
        ) { targetMode ->
            val pages = remember(targetMode, listenedSongs, likedSongs, forgottenSongs, onThisDaySongs, maxPages) {
                songsFor(targetMode).chunked(QuickPicksRowsPerPage).filter { it.isNotEmpty() }.take(maxPages)
            }
            if (pages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = targetMode.emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            } else {
                val pagerState = androidx.compose.runtime.key(targetMode) {
                    rememberPagerState(initialPage = 0) { pages.size }
                }
                LaunchedEffect(targetMode) {
                    pagerState.scrollToPage(0)
                }
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(start = 16.dp, end = 40.dp),
                    pageSpacing = 10.dp,
                    snapPosition = SnapPosition.Start,
                    modifier = Modifier.fillMaxWidth(),
                    key = { index -> "recently_${targetMode.name}_page_$index" }
                ) { pageIndex ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        pages[pageIndex].forEach { song ->
                            QuickPickRow(
                                song = song,
                                isCurrent = song.id == currentSongId,
                                isPlaying = isPlaying,
                                onClick = { onSongClick(song, targetMode) },
                                onMoreClick = { onSongMoreClick(song) },
                                subtitle = when (targetMode) {
                                    RecentlyMode.OnThisDay -> yearById[song.id]?.let { "${song.displayArtist} · $it" }
                                    RecentlyMode.Forgotten -> song.userActivityStats.playCount
                                        .takeIf { it > 0 }?.let { "${song.displayArtist} · $it plays, not lately" }
                                    else -> null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
