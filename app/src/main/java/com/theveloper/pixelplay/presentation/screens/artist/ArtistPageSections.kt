package com.theveloper.pixelplay.presentation.screens.artist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.TrackVideo
import com.theveloper.pixelplay.data.repository.ArtistAlbumItem
import com.theveloper.pixelplay.data.repository.ArtistSongSort
import com.theveloper.pixelplay.data.repository.RelatedArtistItem
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SmartImageCompactListTargetSize
import com.theveloper.pixelplay.presentation.viewmodel.ArtistListeningUi
import com.theveloper.pixelplay.presentation.viewmodel.ArtistTrackItem
import com.theveloper.pixelplay.presentation.viewmodel.DiscographyCompletion
import com.theveloper.pixelplay.presentation.viewmodel.TrackOwnership
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// Section title: no arrow; count badge; tap opens "see all" (A5).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ArtistSectionTitle(
    title: String,
    count: Int? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (count != null && count > 0) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
        trailing?.invoke()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header stats + catalogue status
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ArtistStatsRow(fanCount: Long?, songCount: Int, releaseCount: Int) {
    val stats = buildList {
        fanCount?.let { add(formatMetricCompact(it) to "Fans on Deezer") }
        if (songCount > 0) add(songCount.toString() to if (songCount == 1) "Song" else "Songs")
        if (releaseCount > 0) add(releaseCount.toString() to if (releaseCount == 1) "Release" else "Releases")
    }
    if (stats.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stats.forEachIndexed { index, (value, label) ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun CatalogueStatusLine(
    isAggregating: Boolean,
    isOffline: Boolean,
    hasSavedCatalogue: Boolean,
    updatedAt: Long?,
    onRefresh: () -> Unit
) {
    val text = when {
        isAggregating -> "Loading the full catalogue…"
        isOffline && hasSavedCatalogue -> "Offline · showing saved catalogue"
        isOffline -> "Offline · showing your songs"
        updatedAt != null -> "Catalogue updated ${relativeTime(updatedAt)}"
        else -> null
    } ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            isAggregating -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            isOffline -> Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (!isAggregating) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh catalogue", modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// All songs: search, sort, rows
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SongSortChips(selected: ArtistSongSort, onSelect: (ArtistSongSort) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ArtistSongSort.entries.forEach { sort ->
            FilterChip(
                selected = sort == selected,
                onClick = { onSelect(sort) },
                label = { Text(sort.label) },
                leadingIcon = if (sort == selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

/** Expanded search field for the song list (A2: opens from the search icon next to the title). */
@Composable
internal fun ArtistSongSearchField(
    query: String,
    artistName: String,
    songCount: Int,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    autoFocus: Boolean = true
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focusRequester.requestFocus() } }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Search $songCount songs by $artistName", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.Search, contentDescription = null)
        },
        trailingIcon = {
            IconButton(onClick = { if (query.isNotEmpty()) onQueryChange("") else onClose() }) {
                Icon(Icons.Rounded.Close, contentDescription = if (query.isNotEmpty()) "Clear search" else "Close search")
            }
        },
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )
}

@Composable
internal fun ArtistSongRow(
    item: ArtistTrackItem,
    isCurrentSong: Boolean,
    versionsExpanded: Boolean,
    onClick: () -> Unit,
    onToggleVersions: () -> Unit,
    onVersionClick: (Song) -> Unit,
    onMoreClick: () -> Unit
) {
    val song = item.track.song
    Column {
        Surface(
            onClick = onClick,
            shape = AbsoluteSmoothCornerShape(16.dp, 60),
            color = if (isCurrentSong) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmartImage(
                    model = item.playSong.albumArtUriString ?: song.albumArtUriString,
                    contentDescription = song.title,
                    targetSize = SmartImageCompactListTargetSize,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        OwnershipBadge(item.ownership)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val subtitle = listOfNotNull(
                            item.track.releaseYear?.toString(),
                            song.album.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        item.track.popularity?.let { PopularityBar(it) }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (item.track.versions.isNotEmpty()) {
                        Text(
                            text = "+${item.track.versions.size} version${if (item.track.versions.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onToggleVersions)
                                .padding(vertical = 2.dp)
                        )
                    }
                }
                IconButton(onClick = onMoreClick) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        AnimatedVisibility(visible = versionsExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item.track.versions.forEach { version ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onVersionClick(version) }
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(version.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (version.album.isNotBlank()) {
                                Text(
                                    listOfNotNull(version.year.takeIf { it > 0 }?.toString(), version.album).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
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
}

@Composable
private fun OwnershipBadge(ownership: TrackOwnership) {
    val (icon, description) = when (ownership) {
        TrackOwnership.LOCAL, TrackOwnership.DOWNLOADED -> Icons.Rounded.DownloadDone to "On this device"
        TrackOwnership.LIKED -> Icons.Rounded.Favorite to "Liked"
        TrackOwnership.NONE -> return
    }
    Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
}

/** Small 0–100 bar (A4). */
@Composable
private fun PopularityBar(popularity: Int) {
    Box(
        modifier = Modifier
            .width(32.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(popularity / 100f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
internal fun SongRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(160.dp).height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
            Box(Modifier.width(100.dp).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        }
    }
}

@Composable
internal fun NoSongsMatch(query: String, onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("No songs match “$query”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClear) { Text("Clear search") }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Latest release
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LatestReleaseCard(release: ArtistAlbumItem, onOpen: () -> Unit, onPlay: () -> Unit) {
    val ageDays = release.releaseDate?.let { daysSince(it) }
    val label = when {
        ageDays != null && ageDays in 0..60 -> "NEW · ${if (ageDays == 0L) "today" else "$ageDays day${if (ageDays == 1L) "" else "s"} ago"}"
        ageDays != null && ageDays < 0 -> "UPCOMING"
        else -> "LATEST RELEASE"
    }
    Surface(
        onClick = onOpen,
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SmartImage(
                model = release.coverArtUrl,
                contentDescription = release.title,
                targetSize = SmartImageCompactListTargetSize,
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(14.dp))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = release.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        if (release.isSingleOrEp) "Single / EP" else "Album",
                        release.releaseDateFormatted.ifBlank { release.releaseYear?.toString() },
                        release.trackCount.takeIf { it > 0 }?.let { "$it tracks" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            FilledTonalIconButton(onClick = onPlay) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${release.title}")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Artist mix + You & Artist
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ArtistMixCard(artistName: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AbsoluteSmoothCornerShape(20.dp, 60))
            .background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$artistName mix",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Their biggest songs, your favourites and similar artists. Keeps going.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2
                )
            }
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Start mix", tint = Color.White)
        }
    }
}

@Composable
internal fun YouAndArtistCard(
    artistName: String,
    listening: ArtistListeningUi,
    onTopSongClick: (Song) -> Unit
) {
    Surface(
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                StatCell(formatListeningTime(listening.totalDurationMs), "Listened")
                StatCell(listening.plays.toString(), if (listening.plays == 1) "Play" else "Plays")
                StatCell(
                    if (listening.shareOfListening >= 0.001f) "${"%.1f".format(Locale.ROOT, listening.shareOfListening * 100)}%" else "<0.1%",
                    "Of your listening"
                )
                if (listening.streakDays > 1) StatCell("${listening.streakDays} days", "Streak")
            }
            listening.firstPlayedAt?.let {
                Text(
                    "You first played $artistName on ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            listening.topSong?.let { song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onTopSongClick(song) }
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmartImage(
                        model = song.albumArtUriString,
                        contentDescription = song.title,
                        targetSize = SmartImageCompactListTargetSize,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Your #1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(song.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${listening.topSongPlays} plays", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatCell(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Discography completion
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun DiscographyCompletionCard(
    completion: DiscographyCompletion,
    onDownloadLiked: () -> Unit,
    onLikeAll: () -> Unit,
    onShuffleDownloaded: () -> Unit
) {
    Surface(
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "You have ${completion.owned} of ${completion.total} songs",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(completion.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = { completion.fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
            )
            Text(
                "${completion.downloaded} on this device · ${completion.owned - completion.downloaded} liked online",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (completion.likedNotDownloaded.isNotEmpty()) {
                    FilledTonalButton(onClick = onDownloadLiked, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download ${completion.likedNotDownloaded.size} liked")
                    }
                }
                if (completion.notOwned.isNotEmpty()) {
                    OutlinedButton(onClick = onLikeAll, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Like all")
                    }
                }
                if (completion.offlineSongs.isNotEmpty()) {
                    OutlinedButton(onClick = onShuffleDownloaded, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle downloaded")
                    }
                }
            }
        }
    }
}

@Composable
internal fun LikeAllDialog(count: Int, artistName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Like $count songs?") },
        text = { Text("Every $artistName song you don't have yet will be added to Liked Songs.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Like all") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Videos, timeline, genres, about
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ArtistVideoCard(video: TrackVideo, onClick: () -> Unit, modifier: Modifier = Modifier.width(224.dp)) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp)
    ) {
        Box {
            SmartImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                targetSize = SmartImageCompactListTargetSize,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
            )
            Box(
                modifier = Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(cleanVideoTitle(video.title), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(video.channel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Artist - Song (Official Music Video)" → "Song". */
internal fun cleanVideoTitle(title: String): String =
    title.replace(Regex("""(?i)\s*[(\[][^)\]]*(official|video|audio|lyric|visuali[sz]er|hd|4k)[^)\]]*[)\]]"""), "")
        .substringAfter(" - ", title)
        .trim()
        .ifBlank { title }

@Composable
internal fun TimelineDecadeHeader(decade: String, count: Int) {
    Text(
        "$decade · $count release${if (count == 1) "" else "s"}",
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
internal fun TimelineRow(release: ArtistAlbumItem, ownedCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            release.releaseYear?.toString() ?: "—",
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp)
        )
        SmartImage(
            model = release.coverArtUrl,
            contentDescription = release.title,
            targetSize = SmartImageCompactListTargetSize,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(release.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    if (release.isSingleOrEp) "Single / EP" else "Album",
                    release.trackCount.takeIf { it > 0 }?.let { total ->
                        if (ownedCount > 0) "you have $ownedCount of $total" else "$total tracks"
                    }
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (ownedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun GenreChips(genres: List<String>, onClick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        genres.forEach { genre ->
            AssistChip(
                onClick = { onClick(genre) },
                label = { Text(genre) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            )
        }
    }
}

@Composable
internal fun AboutCard(bio: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                bio,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Source: Last.fm", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Show less" else "Show more",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// "Not this artist?"
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun NotThisArtistDialog(
    candidates: List<RelatedArtistItem>,
    currentId: Long?,
    onPick: (RelatedArtistItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which artist is this?") },
        text = {
            if (candidates.isEmpty()) {
                Text("No other artists with this name were found.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(candidates, key = { it.id }) { candidate ->
                        val selected = candidate.id == currentId?.toString()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(candidate) }
                                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SmartImage(
                                model = candidate.imageUrl,
                                contentDescription = candidate.name,
                                targetSize = SmartImageCompactListTargetSize,
                                modifier = Modifier.size(44.dp).clip(CircleShape)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(candidate.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${formatMetricCompact(candidate.fanCount)} fans", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) Icon(Icons.Rounded.Check, contentDescription = "Current", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Formatting
// ─────────────────────────────────────────────────────────────────────────────

internal fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes((now - timestamp).coerceAtLeast(0))
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} h ago"
        else -> "${minutes / (60 * 24)} d ago"
    }
}

internal fun formatListeningTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    return when {
        minutes < 60 -> "$minutes min"
        minutes < 60 * 100 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "${minutes / 60} h"
    }
}

private fun daysSince(isoDate: String): Long? = try {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate.take(10)) ?: return null
    TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - date.time)
} catch (e: Exception) {
    null
}
