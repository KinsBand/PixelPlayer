package com.theveloper.pixelplay.presentation.screens.library

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.data.library.LibraryInsightsRepository
import com.theveloper.pixelplay.data.library.NewReleasesRepository

// ─────────────────────────────────────────────────────────────────────────────
// Shared pieces
// ─────────────────────────────────────────────────────────────────────────────

/** Remembers which shelves are collapsed (per shelf id), across app restarts. */
private object ShelfPrefs {
    private const val FILE = "library_shelves"
    fun isCollapsed(context: Context, id: String) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("collapsed_$id", false)
    fun setCollapsed(context: Context, id: String, collapsed: Boolean) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("collapsed_$id", collapsed).apply()
    fun lastSeenRelease(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong("seen_release", 0L)
    fun setLastSeenRelease(context: Context, value: Long) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong("seen_release", value).apply()
}

@Composable
private fun Shelf(
    id: String,
    title: String,
    subtitle: String? = null,
    badge: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var collapsed by remember(id) { mutableStateOf(ShelfPrefs.isCollapsed(context, id)) }
    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    collapsed = !collapsed
                    ShelfPrefs.setCollapsed(context, id, collapsed)
                }
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badge) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                contentDescription = if (collapsed) "Show $title" else "Hide $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = !collapsed) {
            Box(Modifier.padding(top = 4.dp, bottom = 10.dp)) { content() }
        }
    }
}

@Composable
private fun Artwork(url: String?, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape, placeholder: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {}) {
    val context = LocalContext.current
    Box(modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (url.isNullOrBlank()) {
            placeholder()
        } else {
            AsyncImage(
                model = remember(url) { ImageRequest.Builder(context).data(url).size(320).crossfade(true).build() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

fun formatListening(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

@Composable
private fun AlbumShelfCard(card: LibraryInsightsRepository.AlbumCard, caption: String, showRing: Boolean = false, onClick: () -> Unit) {
    Column(modifier = Modifier.width(128.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Box {
            Artwork(card.artUri, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(14.dp))
            if (showRing && card.total > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ) {
                    AlbumProgressRing(owned = card.owned, total = card.total, size = 26, modifier = Modifier.padding(3.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(card.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Albums tab
// ─────────────────────────────────────────────────────────────────────────────

/** Shelves above the album grid: Heavy rotation, Complete these, Rediscover. */
@Composable
fun AlbumShelvesHeader(
    insights: LibraryInsightsRepository.Insights,
    onAlbumClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (insights.heavyRotation.isEmpty() && insights.completeThese.isEmpty() && insights.rediscover.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        if (insights.heavyRotation.isNotEmpty()) {
            Shelf(id = "albums_heavy", title = "Heavy rotation", subtitle = "Most played · 30 days") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    items(insights.heavyRotation, key = { "heavy_${it.id}" }) { card ->
                        AlbumShelfCard(card, "${card.plays} plays · ${formatListening(card.playTimeMs)}") { onAlbumClick(card.id) }
                    }
                }
            }
        }
        if (insights.completeThese.isNotEmpty()) {
            Shelf(id = "albums_complete", title = "Complete these", subtitle = "Albums you have part of") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    items(insights.completeThese, key = { "complete_${it.id}" }) { card ->
                        AlbumShelfCard(card, "${card.owned}/${card.total} · ${card.artist}", showRing = true) { onAlbumClick(card.id) }
                    }
                }
            }
        }
        if (insights.rediscover.isNotEmpty()) {
            Shelf(id = "albums_rediscover", title = "Rediscover", subtitle = "Loved before, not played in 6 months") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    items(insights.rediscover, key = { "rediscover_${it.id}" }) { card ->
                        AlbumShelfCard(card, "${card.plays} plays before") { onAlbumClick(card.id) }
                    }
                }
            }
        }
    }
}

/**
 * Marker for "streamed, not on this device". Labels built with it ([cloudLabel]) render as a
 * cloud icon in [AlbumCardBadge] and [CloudAwareText] instead of the word "streaming".
 */
const val CLOUD_MARK = "\u2601"

/** Cloud badge text: just the cloud, or the cloud with how many songs stream ("☁ 3"). */
fun cloudLabel(count: Int? = null): String = if (count == null) CLOUD_MARK else "$CLOUD_MARK $count"

/**
 * Small badge on an album card: a cloud for albums made only of liked streams, a cloud with a
 * count for library albums that also have streamed likes, "Just added" for new ones.
 */
@Composable
fun AlbumCardBadge(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
        contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        if (text.startsWith(CLOUD_MARK)) {
            val rest = text.removePrefix(CLOUD_MARK).trim()
            Row(
                modifier = Modifier.padding(horizontal = if (rest.isEmpty()) 5.dp else 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Cloud, contentDescription = "Streaming", modifier = Modifier.size(13.dp))
                if (rest.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(rest, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                maxLines = 1
            )
        }
    }
}

/** Text in which every [CLOUD_MARK] is drawn as a small cloud icon (subtitles, info lines). */
@Composable
fun CloudAwareText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    if (!text.contains(CLOUD_MARK)) {
        Text(text = text, style = style, color = color, modifier = modifier, maxLines = maxLines,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        return
    }
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        text.split(CLOUD_MARK).forEachIndexed { index, part ->
            if (index > 0) appendInlineContent("cloud", CLOUD_MARK)
            append(part)
        }
    }
    val inline = mapOf(
        "cloud" to androidx.compose.foundation.text.InlineTextContent(
            androidx.compose.ui.text.Placeholder(
                width = style.fontSize.takeIf { it != androidx.compose.ui.unit.TextUnit.Unspecified } ?: 12.sp,
                height = style.fontSize.takeIf { it != androidx.compose.ui.unit.TextUnit.Unspecified } ?: 12.sp,
                placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.Center
            )
        ) {
            Icon(Icons.Rounded.Cloud, contentDescription = "Streaming", tint = color, modifier = Modifier.fillMaxSize())
        }
    )
    Text(text = annotated, style = style, color = color, modifier = modifier, maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, inlineContent = inline)
}

// ─────────────────────────────────────────────────────────────────────────────
// Artists tab
// ─────────────────────────────────────────────────────────────────────────────

/** Shelves above the artist list: your top artists this month, new from your artists. */
@Composable
fun ArtistShelvesHeader(
    insights: LibraryInsightsRepository.Insights,
    onArtistClick: (Long) -> Unit,
    onReleaseClick: (NewReleasesRepository.Release) -> Unit,
    modifier: Modifier = Modifier
) {
    if (insights.topArtists.isEmpty() && insights.newReleases.isEmpty()) return
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        if (insights.topArtists.isNotEmpty()) {
            val month = remember { java.time.LocalDate.now().month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) }
            Shelf(id = "artists_top", title = "Your top artists", subtitle = month) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    items(insights.topArtists, key = { "top_${it.id}" }) { artist ->
                        Column(
                            modifier = Modifier.width(84.dp).clip(RoundedCornerShape(16.dp)).clickable { onArtistClick(artist.id) }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Artwork(artist.imageUrl, Modifier.size(72.dp), CircleShape) {
                                Icon(Icons.Rounded.Person, null, Modifier.align(Alignment.Center), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(artist.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatListening(artist.playTimeMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (insights.newReleases.isNotEmpty()) {
            val newest = insights.newReleases.maxOf { it.releasedAt }
            val hasUnseen = remember(newest) { newest > ShelfPrefs.lastSeenRelease(context) }
            androidx.compose.runtime.LaunchedEffect(newest) { ShelfPrefs.setLastSeenRelease(context, newest) }
            Shelf(
                id = "artists_new",
                title = "New from your artists",
                subtitle = "${insights.newReleases.size} releases · last ${NewReleasesRepository.WINDOW_DAYS} days",
                badge = hasUnseen
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    items(insights.newReleases, key = { "release_${it.collectionId}" }) { release ->
                        Column(modifier = Modifier.width(128.dp).clip(RoundedCornerShape(16.dp)).clickable { onReleaseClick(release) }.padding(4.dp)) {
                            Box {
                                Artwork(release.artworkUrl, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(14.dp))
                                if (release.daysAgo() <= 7) {
                                    AlbumCardBadge("NEW", Modifier.align(Alignment.TopStart).padding(6.dp), emphasized = true)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(release.displayTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${release.artist} · ${release.kind} · ${daysLabel(release.daysAgo())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun daysLabel(days: Int): String = when {
    days <= 0 -> "today"
    days < 7 -> "${days}d"
    days < 30 -> "${days / 7}w"
    else -> "${days / 30}mo"
}

// ─────────────────────────────────────────────────────────────────────────────
// Genre page
// ─────────────────────────────────────────────────────────────────────────────

/** Genre page header: Start genre mix, top artists, subgenre chips. */
@Composable
fun GenreDetailHeader(
    subgenres: List<Pair<String, Int>>,
    selectedSubgenre: String?,
    topArtists: List<Pair<String, Int>>,
    onSubgenreSelected: (String?) -> Unit,
    onStartMix: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        FilledTonalButton(onClick = onStartMix) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Start genre mix")
        }
        if (topArtists.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Top artists: " + topArtists.take(5).joinToString(" · ") { it.first },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (subgenres.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "sub_all") {
                    FilterChip(selected = selectedSubgenre == null, onClick = { onSubgenreSelected(null) }, label = { Text("All") })
                }
                items(subgenres, key = { "sub_${it.first}" }) { (name, count) ->
                    FilterChip(
                        selected = selectedSubgenre.equals(name, ignoreCase = true),
                        onClick = { onSubgenreSelected(name) },
                        label = { Text("$name · $count") }
                    )
                }
            }
        }
    }
}
