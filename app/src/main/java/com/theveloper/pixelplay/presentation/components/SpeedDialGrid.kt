package com.theveloper.pixelplay.presentation.components

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One tile in the Home speed dial grid: Your Music, one of your playlists, a generated mix or a
 * song. Replaces the separate Playlists carousel and Speed dial row.
 */
@Immutable
sealed interface SpeedDialGridItem {
    val key: String
    val label: String
    val caption: String
    val kind: String

    @Immutable
    data class YourMusic(val likedSongs: ImmutableList<Song>) : SpeedDialGridItem {
        override val key: String get() = "your_music"
        override val label: String get() = "Your Music"
        override val caption: String get() = "${likedSongs.size}"
        override val kind: String get() = "collection"
    }

    @Immutable
    data class PlaylistItem(val playlist: Playlist) : SpeedDialGridItem {
        override val key: String get() = "playlist_${playlist.id}"
        override val label: String get() = playlist.name
        override val caption: String get() = "${playlist.songIds.size}"
        override val kind: String get() = "playlist"
    }

    /** A friend's public playlist that isn't saved yet (saved on first open). */
    @Immutable
    data class FriendPlaylistItem(
        val friendId: String,
        val friendName: String,
        val remoteId: String,
        val source: String,
        val title: String,
        val coverUrl: String?,
        val songCount: Int,
    ) : SpeedDialGridItem {
        override val key: String get() = "friend_playlist_${source}_$remoteId"
        override val label: String get() = title
        override val caption: String get() = if (friendName.isNotBlank()) "$songCount · $friendName" else "$songCount"
        override val kind: String get() = "playlist"
    }

    @Immutable
    data class TileItem(val tile: SpeedDialTile) : SpeedDialGridItem {
        override val key: String get() = tile.key
        override val label: String get() = tile.label
        override val caption: String get() = tile.caption
        override val kind: String get() = when (tile) {
            is SpeedDialTile.MixTile -> "mix"
            is SpeedDialTile.SongTile -> "song"
        }
    }
}

/** Pinned speed dial keys, newest pin last. Stored in plain SharedPreferences. */
object SpeedDialPinStore {
    private const val PREFS = "home_speed_dial"
    private const val KEY = "pinned_keys"
    private const val MaxPins = 6

    private val _pins = MutableStateFlow<List<String>>(emptyList())
    val pins: StateFlow<List<String>> = _pins.asStateFlow()
    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _pins.value = raw?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun toggle(context: Context, key: String) {
        val current = _pins.value
        val next = if (key in current) current - key else (current + key).takeLast(MaxPins)
        _pins.value = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, next.joinToString("\n")).apply()
    }
}

/** Pinned items first (in pin order), then everything else, de-duplicated by key. */
fun orderSpeedDialItems(
    items: List<SpeedDialGridItem>,
    pinnedKeys: List<String>,
    maxItems: Int = SpeedDialColumns * SpeedDialRows * SpeedDialMaxPages
): ImmutableList<SpeedDialGridItem> {
    val byKey = LinkedHashMap<String, SpeedDialGridItem>()
    items.forEach { byKey.putIfAbsent(it.key, it) }
    val pinned = pinnedKeys.mapNotNull { byKey[it] }
    val rest = byKey.values.filter { it.key !in pinnedKeys }
    // Every playlist is always shown; only generated mixes / songs are capped. Friends' playlists
    // only show under their picture (unless pinned), so they don't use up the tile budget.
    val ordered = pinned + rest
    val fixed = ordered.count { it !is SpeedDialGridItem.TileItem && !(it.isFriendPlaylist && it.key !in pinnedKeys) }
    var tilesLeft = (maxItems - fixed).coerceAtLeast(0)
    return ordered.filter { item ->
        if (item !is SpeedDialGridItem.TileItem) true
        else if (tilesLeft > 0) { tilesLeft--; true } else false
    }.toImmutableList()
}

private const val SpeedDialColumns = 3
private const val SpeedDialRows = 2
private const val SpeedDialMaxPages = 4

/**
 * Playlists: a horizontally paged 3×2 grid of square tiles (Your Music, your playlists, mixes,
 * songs). Friends' pictures next to the title filter it to that friend's playlists. Long-press a
 * tile to pin it to the first page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDialGrid(
    items: ImmutableList<SpeedDialGridItem>,
    pinnedKeys: List<String>,
    currentSongId: String?,
    onItemClick: (SpeedDialGridItem) -> Unit,
    onTogglePin: (SpeedDialGridItem) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Playlists",
    /** Friends with playlists in this grid; tapping one shows only their playlists. */
    friendFilters: List<PlaylistFriendFilter> = emptyList()
) {
    if (items.isEmpty()) return
    // Only friends who actually have a playlist in the grid get a picture.
    val shownPlaylistIds = remember(items) {
        items.mapNotNullTo(HashSet()) { (it as? SpeedDialGridItem.PlaylistItem)?.playlist?.id }
    }
    val friendsWithPublic = remember(items) {
        items.mapNotNullTo(HashSet()) { (it as? SpeedDialGridItem.FriendPlaylistItem)?.friendId }
    }
    val filters = remember(friendFilters, shownPlaylistIds, friendsWithPublic) {
        friendFilters.filter { f -> f.id in friendsWithPublic || f.playlistIds.any { it in shownPlaylistIds } }
    }
    var selectedFriendId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedFriend = filters.firstOrNull { it.id == selectedFriendId }
    val visibleItems = remember(items, selectedFriend, pinnedKeys) {
        // No friend picked: only your own playlists (a friend's one you pinned stays too).
        if (selectedFriend == null) items.filter { !it.isFriendPlaylist || it.key in pinnedKeys }
        else items.filter {
            (it is SpeedDialGridItem.PlaylistItem && it.playlist.id in selectedFriend.playlistIds) ||
                (it is SpeedDialGridItem.FriendPlaylistItem && it.friendId == selectedFriend.id)
        }
    }
    val pages = remember(visibleItems) { visibleItems.chunked(SpeedDialColumns * SpeedDialRows) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    // Back to the first page whenever the friend filter changes.
    LaunchedEffect(selectedFriend?.id) { if (pages.isNotEmpty()) pagerState.scrollToPage(0) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (filters.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // `this.` because the composable has a parameter called `items`.
                    this.items(filters, key = { "grid_friend_" + it.id }) { friend ->
                        FriendFilterAvatar(
                            friend = friend,
                            selected = friend.id == selectedFriendId,
                            dimmed = selectedFriendId != null && friend.id != selectedFriendId,
                            onClick = { selectedFriendId = if (selectedFriendId == friend.id) null else friend.id },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 16.dp,
            key = { index -> pages[index].firstOrNull()?.key ?: index },
            modifier = Modifier.fillMaxWidth()
        ) { pageIndex ->
            val page = pages[pageIndex]
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                page.chunked(SpeedDialColumns).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { item ->
                            SpeedDialGridTile(
                                item = item,
                                isPinned = item.key in pinnedKeys,
                                isCurrent = item.containsSong(currentSongId),
                                onClick = { onItemClick(item) },
                                onTogglePin = { onTogglePin(item) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Keep a short last row aligned to the grid.
                        repeat(SpeedDialColumns - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Page ${pagerState.currentPage + 1} of ${pages.size}" },
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { index ->
                    val selected = index == pagerState.currentPage
                    val dotColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        animationSpec = tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized),
                        label = "speedDialDot"
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
        }
    }
}

/** A friend's playlist: saved from a friend (has a friendId) or one of their public ones not saved yet. */
internal val SpeedDialGridItem.isFriendPlaylist: Boolean
    get() = this is SpeedDialGridItem.FriendPlaylistItem ||
        (this is SpeedDialGridItem.PlaylistItem && playlist.friendId != null)

private fun SpeedDialGridItem.containsSong(songId: String?): Boolean {
    if (songId == null) return false
    return when (this) {
        is SpeedDialGridItem.TileItem -> when (val t = tile) {
            is SpeedDialTile.SongTile -> t.song.id == songId
            is SpeedDialTile.MixTile -> t.mix.songs.any { it.id == songId }
        }
        is SpeedDialGridItem.PlaylistItem -> false
        is SpeedDialGridItem.FriendPlaylistItem -> false
        is SpeedDialGridItem.YourMusic -> false
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialGridTile(
    item: SpeedDialGridItem,
    isPinned: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .combinedClickable(
                onClickLabel = "Play",
                onLongClickLabel = if (isPinned) "Unpin from Speed dial" else "Pin to Speed dial",
                onClick = onClick,
                onLongClick = { menuOpen = true }
            )
            .semantics {
                contentDescription = buildString {
                    append(item.label).append(", ").append(item.kind)
                    if (isPinned) append(", pinned")
                }
            }
    ) {
        val sizeDp = maxWidth.value.toInt().coerceAtLeast(1)
        when (item) {
            is SpeedDialGridItem.YourMusic -> Box(Modifier.fillMaxSize()) {
                if (item.likedSongs.isNotEmpty()) {
                    MixArtCollage(songs = item.likedSongs, size = sizeDp, corner = 0)
                }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = colors.onPrimaryContainer)
                }
            }
            is SpeedDialGridItem.PlaylistItem -> {
                val cover = item.playlist.coverImageUri
                if (cover != null) {
                    SmartImage(
                        model = cover,
                        contentDescription = null,
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(item.playlist.coverColorArgb?.let { Color(it) } ?: colors.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = colors.onSecondaryContainer)
                    }
                }
            }
            is SpeedDialGridItem.FriendPlaylistItem -> {
                if (item.coverUrl != null) {
                    SmartImage(
                        model = item.coverUrl,
                        contentDescription = null,
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(colors.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = colors.onTertiaryContainer)
                    }
                }
            }
            is SpeedDialGridItem.TileItem -> when (val tile = item.tile) {
                is SpeedDialTile.MixTile -> MixArtCollage(songs = tile.mix.songs, size = sizeDp, corner = 0)
                is SpeedDialTile.SongTile -> SmartImage(
                    model = tile.song.albumArtUriString,
                    contentDescription = null,
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Scrim so the label stays legible over any artwork (same treatment as the old row).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.72f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isPinned) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(colors.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PushPin, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(14.dp))
                }
            }
            if (isCurrent) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }

        // The queue came from this playlist: the same bars badge as everywhere else, bottom-right.
        val playlistPlaying = (item as? SpeedDialGridItem.PlaylistItem)
            ?.let { rememberIsPlaylistPlaying(it.playlist.id) } ?: false
        PlaylistPlayingBadge(
            visible = playlistPlaying,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            size = 22.dp,
            ringColor = colors.surfaceContainer
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 8.dp, end = if (playlistPlaying) 34.dp else 8.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (item is SpeedDialGridItem.PlaylistItem || item is SpeedDialGridItem.FriendPlaylistItem || item is SpeedDialGridItem.YourMusic) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(11.dp)
                    )
                }
                Text(
                    text = item.caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (isPinned) "Unpin from Speed dial" else "Pin to first page") },
                leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onTogglePin()
                }
            )
        }
    }
}
