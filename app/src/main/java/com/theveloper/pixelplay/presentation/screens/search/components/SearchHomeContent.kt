package com.theveloper.pixelplay.presentation.screens.search.components

import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.recognition.RecentlyHeardEntry
import com.theveloper.pixelplay.presentation.components.SmartImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.library.LibraryInsightsRepository
import com.theveloper.pixelplay.presentation.components.SkeletonBlock
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.data.search.ArtistSearchSuggestionHub
import com.theveloper.pixelplay.data.search.HubSuggestion
import com.theveloper.pixelplay.data.search.HubSuggestionKind
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Person

/**
 * What Search shows before you type (genres moved to Library › Genres):
 * recent searches, quick ways into your music, and searches based on what you listen to.
 */
@Composable
fun SearchHomeContent(
    history: List<SearchHistoryItem>,
    /** Up to two artists from your listening, each with its searches. Empty = none yet. */
    artistHubs: List<ArtistSearchSuggestionHub>,
    /** Listening insights are still being built: show the hub's skeleton. */
    hubsLoading: Boolean,
    /** Searches shown when there's no listening history ("New releases", "Top charts"…). */
    fallbackQueries: List<String>,
    onArtistClick: (LibraryInsightsRepository.ArtistCard) -> Unit,
    bottomPadding: Dp,
    onQuery: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onNewReleases: () -> Unit,
    onCharts: () -> Unit,
    onGenresAndMoods: () -> Unit,
    modifier: Modifier = Modifier,
    recentlyHeard: RecentlyHeardEntry? = null,
    recentlyHeardTime: String = "",
    onRecentlyHeard: () -> Unit = {}
) {
    // Always open at the very top. History, Recently heard and suggestions load after the
    // first frame; a lazy list keeps its first visible item anchored, so without this the
    // searches inserted above ended up scrolled out of view.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val hasHistory = history.isNotEmpty()
    androidx.compose.runtime.LaunchedEffect(hasHistory, recentlyHeard != null, artistHubs.isNotEmpty()) {
        if (!listState.isScrollInProgress) listState.scrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Always present (empty when there's no history) so the list never gets an item
        // inserted above what's on screen.
        item(key = "recent") {
            if (hasHistory) {
                RecentSearchesGrid(
                    history = history,
                    onQuery = onQuery,
                    onDelete = onDeleteHistory,
                    onClear = onClearHistory
                )
            }
        }
        item(key = "quick") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickCard("New releases", "From your artists", Icons.Rounded.NewReleases, Modifier.weight(1f), onNewReleases)
                QuickCard(
                    stringResource(R.string.live_events_title),
                    stringResource(R.string.live_events_card_subtitle),
                    Icons.Rounded.Event,
                    Modifier.weight(1f),
                    onCharts
                )
                QuickCard("Genres & moods", "In your Library", Icons.Rounded.Category, Modifier.weight(1f), onGenresAndMoods)
            }
        }
        item(key = "recently_heard") {
            RecentlyHeardCard(
                latest = recentlyHeard,
                time = recentlyHeardTime,
                onClick = onRecentlyHeard
            )
        }
        // Two artists you listen to, side by side, each with searches to try. One artist:
        // a full-width card. No history: generic searches instead.
        item(key = "artist_hub") {
            DualArtistDiscoveryHub(
                hubs = artistHubs,
                loading = hubsLoading,
                fallbackQueries = fallbackQueries,
                onArtistClick = onArtistClick,
                onQuery = onQuery
            )
        }
    }
}

/**
 * "Because you listened" cards: one per artist (two side by side, or one full width), with a
 * round artist picture that opens the artist page and search tiles that run straight away.
 */
@Composable
fun DualArtistDiscoveryHub(
    hubs: List<ArtistSearchSuggestionHub>,
    loading: Boolean,
    fallbackQueries: List<String>,
    onArtistClick: (LibraryInsightsRepository.ArtistCard) -> Unit,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        loading && hubs.isEmpty() -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(2) { ArtistHubCardSkeleton(Modifier.weight(1f)) }
        }
        hubs.isEmpty() -> {
            if (fallbackQueries.isNotEmpty()) {
                ExploreHubCard(queries = fallbackQueries, onQuery = onQuery, modifier = modifier.fillMaxWidth())
            }
        }
        else -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            hubs.take(2).forEach { hub ->
                ArtistHubCard(
                    hub = hub,
                    onArtistClick = { onArtistClick(hub.artist) },
                    onQuery = onQuery,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private val HubCardShape = RoundedCornerShape(20.dp)
private val HubTileShape = RoundedCornerShape(12.dp)
private val HubAvatarSize = 56.dp

@Composable
private fun ArtistHubCard(
    hub: ArtistSearchSuggestionHub,
    onArtistClick: () -> Unit,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val openLabel = stringResource(R.string.search_cd_open_artist, hub.artist.name)
    Surface(
        modifier = modifier.heightIn(min = 220.dp),
        shape = HubCardShape,
        color = colors.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Picture + name: one target that opens the artist page.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = openLabel, onClick = onArtistClick)
                    .padding(vertical = 4.dp)
            ) {
                SmartImage(
                    model = hub.artist.imageUrl,
                    contentDescription = null,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(HubAvatarSize)
                        .border(1.dp, colors.outlineVariant.copy(alpha = 0.3f), CircleShape)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = hub.artist.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.search_hub_because_listened),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    maxLines = 1
                )
            }
            // Similar artists first, then searches for the artist's mood / genre / era.
            // They change every rotation, so the swap crossfades.
            androidx.compose.animation.Crossfade(
                targetState = hub.suggestions,
                animationSpec = androidx.compose.animation.core.tween(
                    if (com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()) 0 else 300
                ),
                modifier = Modifier.padding(top = 10.dp),
                label = "hubSuggestions"
            ) { suggestions ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { suggestion -> HubSuggestionTile(suggestion = suggestion, onQuery = onQuery) }
                }
            }
        }
    }
}

/** No listening history yet: a full-width card of generic searches. */
@Composable
private fun ExploreHubCard(queries: List<String>, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = HubCardShape, color = colors.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.search_hub_explore_title),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.search_hub_explore_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                queries.forEach { query ->
                    HubQueryTile(query = query, onQuery = onQuery) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** A recommended artist (round picture) or a mood search (sparkle); both run a search. */
@Composable
private fun HubSuggestionTile(suggestion: HubSuggestion, onQuery: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    HubQueryTile(query = suggestion.query, onQuery = onQuery) {
        when (suggestion.kind) {
            HubSuggestionKind.ARTIST -> if (suggestion.imageUrl != null) {
                SmartImage(
                    model = suggestion.imageUrl,
                    contentDescription = null,
                    shape = CircleShape,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            HubSuggestionKind.MOOD -> Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun HubQueryTile(query: String, onQuery: (String) -> Unit, leading: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.search_cd_search_query, query)
    Surface(
        shape = HubTileShape,
        color = colors.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .clip(HubTileShape)
            .clickable(onClickLabel = label) { onQuery(query) }
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            Spacer(Modifier.width(8.dp))
            Text(
                text = query,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistHubCardSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 220.dp),
        shape = HubCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonBlock(Modifier.size(HubAvatarSize), CircleShape)
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(14.dp), RoundedCornerShape(7.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.45f).height(10.dp), RoundedCornerShape(5.dp))
            Spacer(Modifier.height(2.dp))
            repeat(3) {
                SkeletonBlock(Modifier.fillMaxWidth().height(32.dp), HubTileShape)
            }
        }
    }
}

private val HistoryCellHeight = 40.dp
private val HistoryCellGap = 8.dp
private const val HISTORY_COLUMNS_IN_VIEW = 3

/**
 * Recent searches, no heading: one row that scrolls sideways, sized so three searches are always
 * in view with a peek of the next, hinting there's more. Hold a search to remove it; "Clear"
 * sits at the end.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecentSearchesGrid(
    history: List<SearchHistoryItem>,
    onQuery: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val reducedMotion = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 3 full cells + a 12% peek of the 4th, so it reads as scrollable.
        val cellWidth = (maxWidth - HistoryCellGap * HISTORY_COLUMNS_IN_VIEW) / (HISTORY_COLUMNS_IN_VIEW + 0.12f)
        val entries = history.take(24)
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(HistoryCellHeight),
            horizontalArrangement = Arrangement.spacedBy(HistoryCellGap)
        ) {
            items(
                count = entries.size,
                key = { i -> "h_${entries[i].id ?: entries[i].query}" }
            ) { i ->
                val item = entries[i]
                HistoryCell(
                    query = item.query,
                    width = cellWidth,
                    onClick = { onQuery(item.query) },
                    onDelete = { onDelete(item.query) },
                    // The rest slide over to fill the gap a removed search leaves.
                    modifier = Modifier.animateItem(
                        fadeInSpec = if (reducedMotion) null else androidx.compose.animation.core.tween(
                            com.theveloper.pixelplay.ui.theme.MotionTokens.DurationMedium1,
                            easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedDecelerate
                        ),
                        placementSpec = if (reducedMotion) null else motionScheme.defaultSpatialSpec(),
                        fadeOutSpec = if (reducedMotion) null else androidx.compose.animation.core.tween(
                            com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort3,
                            easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedAccelerate
                        )
                    )
                )
            }
            item(key = "h_clear") {
                Surface(
                    onClick = onClear,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .animateItem()
                        .width(cellWidth)
                        .height(HistoryCellHeight)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Clear", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HistoryCell(
    query: String,
    width: Dp,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .width(width)
            .height(HistoryCellHeight)
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onDelete()
                },
                onLongClickLabel = "Remove from history"
            )
            .semantics {
                customActions = listOf(
                    androidx.compose.ui.semantics.CustomAccessibilityAction("Remove from history") { onDelete(); true }
                )
            }
    ) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                query,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.heightIn(min = 108.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** Full-width "Recently heard" button: the last song Now Playing recognised. */
@Composable
private fun RecentlyHeardCard(latest: RecentlyHeardEntry?, time: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open recently heard", onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (latest?.artUri != null) {
                    SmartImage(
                        model = latest.artUri,
                        contentDescription = latest.title,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Recently heard",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary
                )
                Text(
                    text = latest?.title ?: "Nothing heard yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (latest != null) {
                        listOf(latest.artist, time).filter { it.isNotBlank() }.joinToString(" \u2022 ")
                    } else {
                        "Songs Now Playing recognises show up here"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )
        }
    }
}
