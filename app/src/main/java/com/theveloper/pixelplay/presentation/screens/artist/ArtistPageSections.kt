package com.theveloper.pixelplay.presentation.screens.artist

import com.theveloper.pixelplay.presentation.components.subcomps.rememberShuffleSpin
import com.theveloper.pixelplay.presentation.components.subcomps.shuffleSpin
import com.theveloper.pixelplay.presentation.components.CompactCountBadge
import com.theveloper.pixelplay.presentation.components.CompactMeta
import com.theveloper.pixelplay.presentation.components.ExpressiveFilterChip
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.theveloper.pixelplay.presentation.components.offlineVeil
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
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
import com.theveloper.pixelplay.presentation.components.songStartFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.theveloper.pixelplay.presentation.components.CollectionCardActionSize
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongCoverLoadingIndicator
import com.theveloper.pixelplay.presentation.components.SmartImageCompactListTargetSize
import com.theveloper.pixelplay.presentation.viewmodel.ArtistListeningUi
import com.theveloper.pixelplay.presentation.viewmodel.ArtistTrackItem
import com.theveloper.pixelplay.presentation.viewmodel.ArtistSongFilter
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.artist.BandMember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.ArrowDownward
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
    trailing: (@Composable () -> Unit)? = null,
    /** The count is songs: shows "[♪] n" like every other song count. */
    countIsSongs: Boolean = false,
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
                    if (countIsSongs) {
                        CompactCountBadge(
                            count = count,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    } else {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
        trailing?.invoke()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header stats + catalogue status
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The stats card: fans · songs · releases, with the catalogue status ("Catalogue updated 2 h ago",
 * loading, offline) and its refresh button as a footer inside the same card.
 */
@Composable
internal fun ArtistStatsRow(
    fanCount: Long?,
    songCount: Int,
    releaseCount: Int,
    isAggregating: Boolean = false,
    isOffline: Boolean = false,
    hasSavedCatalogue: Boolean = false,
    catalogueUpdatedAt: Long? = null,
    onRefresh: () -> Unit = {},
) {
    val stats = buildList {
        fanCount?.let { add(formatMetricCompact(it) to "Fans on Deezer") }
        if (songCount > 0) add("♪ $songCount" to if (songCount == 1) "Song" else "Songs")
        if (releaseCount > 0) add(releaseCount.toString() to if (releaseCount == 1) "Release" else "Releases")
    }
    val hasStatus = catalogueStatusText(isAggregating, isOffline, hasSavedCatalogue, catalogueUpdatedAt) != null
    if (stats.isEmpty() && !hasStatus) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = if (hasStatus) 2.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (stats.isNotEmpty()) ArtistStatsValues(stats, Modifier.padding(end = 8.dp))
        if (hasStatus) {
            if (stats.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
            }
            CatalogueStatusLine(
                isAggregating = isAggregating,
                isOffline = isOffline,
                hasSavedCatalogue = hasSavedCatalogue,
                updatedAt = catalogueUpdatedAt,
                onRefresh = onRefresh
            )
        }
    }
}

@Composable
private fun ArtistStatsValues(stats: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
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

/** The catalogue status text shown in the stats card's footer. */
internal fun catalogueStatusText(isAggregating: Boolean, isOffline: Boolean, hasSavedCatalogue: Boolean, updatedAt: Long?): String? = when {
    isAggregating -> "Loading the full catalogue…"
    isOffline && hasSavedCatalogue -> "Offline · showing saved catalogue"
    isOffline -> "Offline · showing your songs"
    updatedAt != null -> "Catalogue updated ${relativeTime(updatedAt)}"
    else -> null
}

@Composable
internal fun CatalogueStatusLine(
    isAggregating: Boolean,
    isOffline: Boolean,
    hasSavedCatalogue: Boolean,
    updatedAt: Long?,
    onRefresh: () -> Unit
) {
    val text = catalogueStatusText(isAggregating, isOffline, hasSavedCatalogue, updatedAt) ?: return
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
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
            IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.artist_refresh_catalogue_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// All songs: search, sort, rows
// ─────────────────────────────────────────────────────────────────────────────

/** Chip label for a sort (the enum lives in data/, so its text lives here). */
@androidx.annotation.StringRes
internal fun ArtistSongSort.labelRes(): Int = when (this) {
    ArtistSongSort.POPULAR -> R.string.artist_sort_popular
    ArtistSongSort.NEWEST -> R.string.artist_sort_newest
    ArtistSongSort.ALBUM -> R.string.artist_sort_album
    ArtistSongSort.YOUR_PLAYS -> R.string.artist_sort_plays
    ArtistSongSort.A_Z -> R.string.artist_sort_az
}

@androidx.annotation.StringRes
private fun ArtistSongFilter.labelRes(): Int = when (this) {
    ArtistSongFilter.LIKED -> R.string.artist_filter_liked
    ArtistSongFilter.ON_DEVICE -> R.string.artist_filter_on_device
    ArtistSongFilter.NOT_YOURS -> R.string.artist_filter_not_yours
}

/**
 * All songs controls in one row: a fixed direction button at the start (outside the scroller,
 * so it never scrolls away), then the sorts (one always selected), a divider, and the filters
 * (one at a time; tap the selected one again to clear it).
 */
@Composable
internal fun SongSortFilterRow(
    sort: ArtistSongSort,
    reversed: Boolean,
    filter: ArtistSongFilter?,
    onSort: (ArtistSongSort) -> Unit,
    onToggleDirection: () -> Unit,
    onFilter: (ArtistSongFilter?) -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (reversed) 180f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort4,
            easing = com.theveloper.pixelplay.ui.theme.MotionTokens.Emphasized
        ),
        label = "sortDirection"
    )
    val directionLabel = stringResource(if (reversed) R.string.artist_sort_direction_asc_cd else R.string.artist_sort_direction_desc_cd)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = onToggleDirection,
            modifier = Modifier.semantics { stateDescription = directionLabel }
        ) {
            Icon(
                Icons.Rounded.ArrowDownward,
                contentDescription = directionLabel,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation }
            )
        }
        Spacer(Modifier.width(4.dp))
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtistSongSort.entries.forEach { option ->
                ExpressiveFilterChip(
                    selected = option == sort,
                    onClick = { onSort(option) },
                    label = stringResource(option.labelRes())
                )
            }
            Box(
                Modifier
                    .height(24.dp)
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            ArtistSongFilter.entries.forEach { option ->
                ExpressiveFilterChip(
                    selected = option == filter,
                    onClick = { onFilter(if (option == filter) null else option) },
                    label = stringResource(option.labelRes())
                )
            }
        }
    }
}

/**
 * A cover that shows the M3 Expressive loading indicator (the app's song-card one) until the art
 * arrives, then the art; the music placeholder if there's none or it fails. Plain AsyncImage with
 * onState (no subcomposition), so it's cheap enough for every row in a long list.
 */
@Composable
internal fun LoadingCover(
    model: Any?,
    size: Dp,
    shape: Shape,
    indicatorSize: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    var state by remember(model) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val request = remember(context, model) {
        model?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(100)
                .allowHardware(false)
                .size(SmartImageCompactListTargetSize)
                .build()
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onState = { state = it },
                modifier = Modifier.fillMaxSize()
            )
        }
        when {
            request != null && (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Empty) ->
                SongCoverLoadingIndicator(size = indicatorSize, color = MaterialTheme.colorScheme.primary)
            request == null || state is AsyncImagePainter.State.Error -> Icon(
                painter = painterResource(R.drawable.ic_music_placeholder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2)
            )
        }
    }
}

/**
 * Album sort: a small header before each album's songs (All songs page only), with a shuffle
 * button on the right (the same round button as on the album cards) that plays the album
 * shuffled from a random song.
 */
@Composable
internal fun AlbumGroupHeader(
    coverUrl: String?,
    title: String,
    year: Int?,
    modifier: Modifier = Modifier,
    onShuffle: (() -> Unit)? = null,
    /** A song from this album is the one playing: the playing bars show on the cover's corner. */
    nowPlaying: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box {
                LoadingCover(model = coverUrl, size = 40.dp, shape = RoundedCornerShape(10.dp), indicatorSize = 24.dp)
                if (nowPlaying) {
                    // Discography visualiser: bottom-right of the album thumbnail, your theme colour.
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon(
                            modifier = Modifier.size(width = 10.dp, height = 9.dp),
                            color = com.theveloper.pixelplay.presentation.components.subcomps.playingIndicatorColor(
                                com.theveloper.pixelplay.presentation.components.subcomps.PlayingSource.MINE,
                                onCover = true
                            ),
                            isPlaying = isPlaying
                        )
                    }
                }
            }
            Text(
                text = title.ifBlank { "–" },
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            year?.let {
                Text(it.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onShuffle != null) {
            val spin = rememberShuffleSpin()
            val label = stringResource(R.string.artist_album_shuffle_cd, title.ifBlank { "–" })
            Box(
                Modifier
                    .size(CollectionCardActionSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = { spin.play(); onShuffle() }, onClickLabel = label, role = Role.Button)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp).shuffleSpin(spin))
            }
        }
    }
}

/** Empty list under a sort/filter: Plays with nothing played, or filters that match nothing. */
@Composable
internal fun SongsEmptyNote(text: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        TextButton(onClick = onAction) { Text(actionLabel) }
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
    onMoreClick: () -> Unit,
    /** Playing (bars move) vs paused (bars still) when this is the current song. */
    isPlaying: Boolean = false,
    /** Liked right now (live from favourites). Defaults to the catalogue's ownership snapshot. */
    isFavorite: Boolean = item.ownership == TrackOwnership.LIKED,
    /** Like / unlike from the row. Null = no like button. */
    onToggleFavorite: (() -> Unit)? = null
) {
    val song = item.track.song
    val nowPlayingState = stringResource(R.string.artist_now_playing_cd)
    Column {
        val rowShape = AbsoluteSmoothCornerShape(16.dp, 60)
        Surface(
            shape = rowShape,
            color = if (isCurrentSong) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f),
            // No ⋮ button: hold the row for the song's options.
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .songStartFeedback(song.id, rowShape)
                .combinedClickable(onClick = onClick, onLongClick = onMoreClick, onLongClickLabel = "Song options")
                .semantics { if (isCurrentSong) stateDescription = nowPlayingState }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    LoadingCover(
                        model = item.playSong.albumArtUriString ?: song.albumArtUriString,
                        contentDescription = song.title,
                        size = 48.dp,
                        shape = RoundedCornerShape(10.dp),
                        indicatorSize = 28.dp
                    )
                    // The app's playing bars over the cover (same overlay as the friend history rows).
                    if (isCurrentSong) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon(
                                modifier = Modifier.size(width = 18.dp, height = 16.dp),
                                color = com.theveloper.pixelplay.presentation.components.subcomps.playingIndicatorColor(
                                    com.theveloper.pixelplay.presentation.components.subcomps.PlayingSource.MINE,
                                    onCover = true
                                ),
                                isPlaying = isPlaying
                            )
                        }
                    }
                }
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
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Same badge rule as every other song row: cloud, cloud-heart, heart, or nothing.
                        com.theveloper.pixelplay.presentation.components.subcomps.TrackStatusBadges(
                            isFavorite = isFavorite,
                            source = item.playSong.source,
                            downloadState = if (item.ownership == TrackOwnership.DOWNLOADED) {
                                com.theveloper.pixelplay.data.model.DownloadState.DOWNLOADED
                            } else {
                                item.playSong.downloadState
                            },
                            spacing = 0.dp
                        )
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
                if (onToggleFavorite != null) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (isFavorite) "Unlike" else "Like",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
            .clip(AbsoluteSmoothCornerShape(16.dp, 60))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
            .clearAndSetSemantics { }
            // Same 8dp vertical padding around a 48dp cover as ArtistSongRow: 64dp tall.
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        com.theveloper.pixelplay.presentation.components.ShimmerBox(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            com.theveloper.pixelplay.presentation.components.SkeletonTextLine(MaterialTheme.typography.titleMedium, fraction = 0.55f)
            com.theveloper.pixelplay.presentation.components.SkeletonTextLine(MaterialTheme.typography.labelSmall, fraction = 0.35f)
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
                ReleaseMetaLine(
                    prefix = CompactMeta.join(
                        if (release.isSingleOrEp) "Single / EP" else "Album",
                        release.releaseDateFormatted.ifBlank { release.releaseYear?.toString() }
                    ),
                    trackCount = release.trackCount
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
    onLikeAlbums: (() -> Unit)?,
    onShuffle: () -> Unit
) {
    val percent = (completion.fraction * 100).toInt()
    Surface(
        shape = AbsoluteSmoothCornerShape(20.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Ring with the share you have, % in the middle.
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { completion.fraction },
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 5.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.artist_you_have),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.artist_you_have_of, completion.owned, completion.total),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.artist_you_have_breakdown, completion.liked, completion.downloaded),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val toDownload = completion.likedNotDownloaded.size
                if (toDownload > 0) {
                    // Icon + how many liked songs aren't on the device yet (no "Download … liked" text).
                    val downloadLabel = pluralStringResource(R.plurals.artist_download_liked_cd, toDownload, toDownload)
                    FilledTonalButton(
                        onClick = onDownloadLiked,
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.semantics { contentDescription = downloadLabel }
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(toDownload.toString())
                    }
                }
                if (onLikeAlbums != null) {
                    OutlinedButton(onClick = onLikeAlbums, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.artist_like_albums))
                    }
                }
                if (completion.offlineSongs.isNotEmpty()) {
                    val offlineSpin = rememberShuffleSpin()
                    OutlinedButton(onClick = { offlineSpin.play(); onShuffle() }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp).shuffleSpin(offlineSpin))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.artist_shuffle))
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

/**
 * A music video / live performance: the page's shared [CollectionCard] (same padding, corners,
 * two-line title and type as playlist cards) with a 16:9 thumbnail and a play mark.
 */
@Composable
internal fun ArtistVideoCard(video: TrackVideo, onClick: () -> Unit, modifier: Modifier = Modifier.width(ArtistVideoCardWidth)) {
    com.theveloper.pixelplay.presentation.components.CollectionCard(
        title = cleanVideoTitle(video.title),
        subtitle = video.channel,
        coverModel = video.thumbnailUrl,
        onClick = onClick,
        // Videos always stream.
        modifier = modifier.offlineVeil(com.theveloper.pixelplay.presentation.components.rememberNeedsOnlineBlocked(), MaterialTheme.shapes.large),
        coverAspectRatio = 16f / 9f,
        coverOverlay = {
            Box(
                modifier = Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
            }
        }
    )
}

/** Width of a video card in the page's carousels (16:9 needs more room than the square cards). */
internal val ArtistVideoCardWidth = 236.dp

/** "Artist - Song (Official Music Video)" → "Song". */
internal fun cleanVideoTitle(title: String): String =
    title.replace(Regex("""(?i)\s*[(\[][^)\]]*(official|video|audio|lyric|visuali[sz]er|hd|4k)[^)\]]*[)\]]"""), "")
        .substringAfter(" - ", title)
        .trim()
        .ifBlank { title }

/**
 * A decade on the timeline: a pill on the rail ("2010s") and how many releases, with
 * the rail running on under it.
 */
@Composable
internal fun TimelineDecadeHeader(decade: String, count: Int, isFirst: Boolean = false) {
    val rail = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(TimelineRailWidth)
                .fillMaxHeight()
                .drawBehind {
                    // The rail continues down from the pill (and up into it, except for the first decade).
                    val x = size.width / 2f
                    val top = if (isFirst) size.height / 2f else 0f
                    drawLine(rail, Offset(x, top), Offset(x, size.height), strokeWidth = TimelineRailStroke.toPx())
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Text(
                    decade.removeSuffix("s").takeLast(2) + "s",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            decade,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 10.dp)
        )
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .semantics { contentDescription = "$count release${if (count == 1) "" else "s"}" }
            )
        }
    }
}

/**
 * One release on the timeline: the rail with a node (filled when you have songs from it),
 * then a card with the year, cover, title, type and "[♪] 12" (or "[♪] 3/12" when you have some).
 */
@Composable
internal fun TimelineRow(release: ArtistAlbumItem, ownedCount: Int, onClick: () -> Unit, isLast: Boolean = false) {
    val rail = MaterialTheme.colorScheme.outlineVariant
    val owned = ownedCount > 0
    val nodeColor = if (owned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val nodeRing = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(TimelineRailWidth)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2f
                    val y = size.height / 2f
                    drawLine(rail, Offset(x, 0f), Offset(x, if (isLast) y else size.height), strokeWidth = TimelineRailStroke.toPx())
                    val r = TimelineNodeRadius.toPx()
                    drawCircle(nodeColor, radius = r, center = Offset(x, y))
                    drawCircle(nodeRing, radius = r, center = Offset(x, y), style = Stroke(width = 1.5.dp.toPx()))
                }
        )
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onClick)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmartImage(
                model = release.coverArtUrl,
                contentDescription = release.title,
                targetSize = SmartImageCompactListTargetSize,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(release.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                ReleaseMetaLine(
                    prefix = CompactMeta.join(
                        release.releaseYear?.toString(),
                        if (release.isSingleOrEp) "Single / EP" else "Album"
                    ),
                    trackCount = release.trackCount,
                    ownedCount = ownedCount
                )
            }
        }
    }
}

private val TimelineRailWidth = 36.dp
private val TimelineRailStroke = 2.dp
private val TimelineNodeRadius = 6.dp

/**
 * "prefix · [♪] 12" for releases (or "prefix · [♪] 3/12" in the accent colour when you have
 * 3 of its 12 songs). No "tracks" / "songs" words, like every other count.
 */
@Composable
internal fun ReleaseMetaLine(
    prefix: String?,
    trackCount: Int,
    ownedCount: Int = 0,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelSmall,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) {
    val owned = ownedCount > 0 && trackCount > 0
    val countColor = if (owned) MaterialTheme.colorScheme.primary else color
    val spoken = listOfNotNull(
        prefix?.ifBlank { null },
        trackCount.takeIf { it > 0 }?.let { total -> if (owned) "you have $ownedCount of $total songs" else "$total songs" }
    ).joinToString(", ")
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!prefix.isNullOrBlank()) {
            Text(prefix, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            if (trackCount > 0) Text(CompactMeta.SEPARATOR, style = style, color = color, maxLines = 1)
        }
        if (trackCount > 0) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = countColor, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
            Text(if (owned) "$ownedCount/$trackCount" else trackCount.toString(), style = style, color = countColor, maxLines = 1)
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
internal fun AboutCard(bio: String, source: String) {
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
                Text(source, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.artist_about_show_less else R.string.artist_about_show_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The About text when neither Last.fm nor Wikipedia has a bio: a short summary from what the
 * page already knows (your library, genres, members, best-known songs), so About is never empty.
 */
@Composable
internal fun localArtistBio(
    artistName: String,
    songCount: Int,
    releaseCount: Int,
    genres: List<String>,
    members: List<BandMember>,
    topSongs: List<String>,
): String {
    val parts = mutableListOf<String>()
    parts += if (songCount > 0) {
        androidx.compose.ui.res.pluralStringResource(R.plurals.artist_about_local_songs, songCount, artistName, songCount) +
            if (releaseCount > 0) " " + androidx.compose.ui.res.pluralStringResource(R.plurals.artist_about_local_releases, releaseCount, releaseCount) else ""
    } else {
        stringResource(R.string.artist_about_local_none, artistName)
    }
    if (genres.isNotEmpty()) parts += stringResource(R.string.artist_about_local_genres, genres.take(3).joinToString(", "))
    val current = members.filter { it.isCurrent }.ifEmpty { members }
    if (current.isNotEmpty()) parts += stringResource(R.string.artist_about_local_members, current.take(5).joinToString(", ") { it.name })
    if (topSongs.isNotEmpty()) parts += stringResource(R.string.artist_about_local_top, topSongs.take(3).joinToString(", ") { "\u201C$it\u201D" })
    return parts.joinToString(" ")
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
        // Same compact style as the rest of the app: 5m, 3h, 2d.
        else -> CompactMeta.relativeTime(timestamp, now)
    }
}

internal fun formatListeningTime(ms: Long): String {
    // "1h 59m", like playlist lengths; past 100 hours just the hours.
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    return if (minutes < 60 * 100) CompactMeta.duration(ms) else "${minutes / 60}h"
}

private fun daysSince(isoDate: String): Long? = try {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate.take(10)) ?: return null
    TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - date.time)
} catch (e: Exception) {
    null
}
