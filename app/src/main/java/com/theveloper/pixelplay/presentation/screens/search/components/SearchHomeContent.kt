package com.theveloper.pixelplay.presentation.screens.search.components

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

/**
 * What Search shows before you type (genres moved to Library › Genres):
 * recent searches, quick ways into your music, and searches based on what you listen to.
 */
@Composable
fun SearchHomeContent(
    history: List<SearchHistoryItem>,
    suggestions: List<String>,
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (history.isNotEmpty()) {
            item(key = "recent") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Recent searches",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearHistory) { Text("Clear") }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history.take(15), key = { "h_${it.id ?: it.query}" }) { item ->
                            InputChip(
                                selected = false,
                                onClick = { onQuery(item.query) },
                                label = { Text(item.query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Remove ${item.query}",
                                        modifier = Modifier.size(18.dp).clickable { onDeleteHistory(item.query) }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
        item(key = "quick") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickCard("New releases", "From your artists", Icons.Rounded.NewReleases, Modifier.weight(1f), onNewReleases)
                QuickCard("Your charts", "Top songs & artists", Icons.AutoMirrored.Rounded.TrendingUp, Modifier.weight(1f), onCharts)
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
        if (suggestions.isNotEmpty()) {
            item(key = "suggest_title") {
                Text(
                    "Because you listen to ${suggestions.first()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // One compact block: the list's 14 dp item spacing between single-line suggestions
            // made them look scattered, and a 48 dp icon button per row doubled their height.
            item(key = "suggest_list") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    suggestions.take(8).forEach { query ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClickLabel = "Search $query") { onQuery(query) }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(16.dp))
                            Text(query, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(
                                Icons.Rounded.NorthWest,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp).size(20.dp)
                            )
                        }
                    }
                }
            }
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
