package com.theveloper.pixelplay.presentation.screens.search.components

import com.theveloper.pixelplay.presentation.components.offlineVeil
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.theveloper.pixelplay.presentation.components.songStartFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SmartImageListTargetSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlinx.coroutines.launch
import java.util.Locale

/** One line in the live suggestions list. [query] is what gets searched when it's tapped. */
data class LiveSearchSuggestion(
    val text: String,
    val query: String,
    val kind: Kind,
    val subtitle: String? = null
) {
    /** [Query] = a search completion (what YT Music / Spotify show while you type). */
    enum class Kind { History, Query, Artist, Song, Album, Playlist }
}

/** Compiled once: built per suggestion per keystroke otherwise (OOM rule). */
private val WhitespaceRun = Regex("\\s+")

/**
 * Search suggestions for what's typed so far, the way YouTube Music and Spotify do it: plain
 * search phrases that complete — and correct — the query, never artist/album/song rows.
 *
 * 1. Past searches that match (up to 3, history icon).
 * 2. YouTube Music's own autocomplete for the typed text ([online]); it fixes spelling
 *    ("deftons" → "deftones") server-side.
 * 3. Offline fallback / fill-in: names from the live results (artists, song and album titles)
 *    that start with the typed words, or are within a small typo distance of them.
 */
fun buildLiveSuggestions(
    query: String,
    results: List<SearchResultItem>,
    history: List<SearchHistoryItem>,
    online: List<String> = emptyList(),
    limit: Int = 8
): List<LiveSearchSuggestion> {
    val typed = query.trim().lowercase(Locale.ROOT)
    if (typed.isEmpty()) return emptyList()
    val out = ArrayList<LiveSearchSuggestion>()
    val seen = HashSet<String>()
    fun add(text: String, kind: LiveSearchSuggestion.Kind): Boolean {
        val clean = text.trim().replace(WhitespaceRun, " ")
        val key = clean.lowercase(Locale.ROOT)
        if (clean.isEmpty() || key == typed || !seen.add(key)) return false
        out += LiveSearchSuggestion(clean, clean, kind)
        return true
    }

    // 1. Matching past searches.
    history.asSequence()
        .map { it.query.trim() }
        .filter { suggestionMatchScore(typed, it.lowercase(Locale.ROOT)) != null }
        .take(3)
        .forEach { add(it, LiveSearchSuggestion.Kind.History) }

    // 2. Online autocomplete (already ranked and spell-corrected by YouTube Music).
    online.forEach { if (out.size < limit) add(it, LiveSearchSuggestion.Kind.Query) }

    // 3. Local completions from the live results, best match first.
    if (out.size < limit) {
        val phrases = ArrayList<String>()
        results.forEach { result ->
            when (result) {
                is SearchResultItem.ArtistItem -> phrases += result.artist.name
                is SearchResultItem.SongItem -> {
                    phrases += result.song.title
                    result.song.displayArtist.takeIf { it.isNotBlank() && it != "<unknown>" }?.let { phrases += it }
                }
                is SearchResultItem.AlbumItem -> phrases += result.album.title
                is SearchResultItem.PlaylistItem -> phrases += result.playlist.name
                is SearchResultItem.FriendItem -> Unit
                is SearchResultItem.ProfileItem -> phrases += result.person.name
            }
        }
        phrases.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 60 }
            .mapIndexedNotNull { index, phrase ->
                suggestionMatchScore(typed, phrase.lowercase(Locale.ROOT))?.let { Triple(phrase, it, index) }
            }
            .sortedWith(compareBy({ it.second }, { it.third }))
            .forEach { (phrase, _, _) ->
                // Search phrases read as typed text, like YT Music: lower case.
                if (out.size < limit) add(phrase.lowercase(Locale.ROOT), LiveSearchSuggestion.Kind.Query)
            }
    }
    return out.take(limit)
}

/**
 * How well [phrase] completes [typed] (lower = better), or null when it doesn't:
 * 0 starts with it · 1 a word starts with it · 2 contains it · 3+ a typo-tolerant prefix match
 * (1 edit allowed from 4 letters, 2 from 8).
 */
internal fun suggestionMatchScore(typed: String, phrase: String): Int? {
    if (typed.isEmpty() || phrase.isEmpty()) return null
    if (phrase.startsWith(typed)) return 0
    if (phrase.split(' ', '-', '(', '/', '&').any { it.startsWith(typed) }) return 1
    if (phrase.contains(typed)) return 2
    val allowed = when {
        typed.length >= 8 -> 2
        typed.length >= 4 -> 1
        else -> 0
    }
    if (allowed == 0) return null
    // Fuzzy: compare against the phrase start and each word start.
    var best = Int.MAX_VALUE
    var start = 0
    while (start < phrase.length) {
        best = minOf(best, prefixEditDistance(typed, phrase, start))
        val next = phrase.indexOf(' ', start)
        if (next < 0) break
        start = next + 1
    }
    return if (best <= allowed) 3 + best else null
}

/** Levenshtein distance from [typed] to the closest prefix of [phrase] beginning at [from]. */
private fun prefixEditDistance(typed: String, phrase: String, from: Int): Int {
    val maxLen = minOf(phrase.length - from, typed.length + 2)
    if (maxLen <= 0) return typed.length
    var prev = IntArray(maxLen + 1) { it }
    var cur = IntArray(maxLen + 1)
    for (i in 1..typed.length) {
        cur[0] = i
        for (j in 1..maxLen) {
            val cost = if (typed[i - 1] == phrase[from + j - 1]) 0 else 1
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        }
        val t = prev; prev = cur; cur = t
    }
    return prev.minOrNull() ?: typed.length
}

/**
 * What Search shows while typing (before the search is entered): live suggestions from the top,
 * and the matching songs as a strip docked at the bottom, right above the keyboard (or above the
 * mini player once the keyboard closes). The strip shows one row; pull it up (or tap its handle)
 * for a second row. Tapping a suggestion (or the keyboard's Search) turns it into the full results.
 */
@Composable
fun LiveSearchContent(
    query: String,
    songs: List<Song>,
    suggestions: List<LiveSearchSuggestion>,
    currentSongId: String?,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onSuggestionClick: (LiveSearchSuggestion) -> Unit,
    onSuggestionFill: (LiveSearchSuggestion) -> Unit,
    /** Where the strip rests while the keyboard is closed (nav bar + mini player + gap). */
    restBottom: Dp,
    stripExpanded: Boolean,
    onStripExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Typing always shows the top suggestion first.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val hasSongs = songs.isNotEmpty()
    androidx.compose.runtime.LaunchedEffect(query, hasSongs) {
        listState.scrollToItem(0)
    }
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val stripGapPx = with(density) { 8.dp.roundToPx() }
    val restBottomPx = with(density) { restBottom.roundToPx() }
    var stripHeightPx by remember { mutableIntStateOf(0) }
    // The list scrolls clear of the strip: its height plus where it currently sits.
    val imeBottomPx = ime.getBottom(density)
    val stripBottomPx = maxOf(if (imeBottomPx > 0) imeBottomPx + stripGapPx else 0, restBottomPx)
    val listBottomPadding = with(density) {
        ((if (hasSongs) stripHeightPx else 0) + stripBottomPx).toDp()
    } + 8.dp

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = listBottomPadding)
        ) {
            items(suggestions, key = { "live_suggestion_${it.kind}_${it.text}" }) { suggestion ->
                LiveSuggestionRow(
                    query = query,
                    suggestion = suggestion,
                    onClick = { onSuggestionClick(suggestion) },
                    onFill = { onSuggestionFill(suggestion) },
                    modifier = Modifier.animateItem()
                )
            }
            if (suggestions.isEmpty() && songs.isEmpty()) {
                item(key = "live_empty") {
                    Text(
                        text = "Press Search to look for “${query.trim()}”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                    )
                }
            }
        }
        val reducedMotion = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
        androidx.compose.animation.AnimatedVisibility(
            visible = hasSongs,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Rides the keyboard's own animation; read in layout, so no recomposition per frame.
                .offset {
                    val imePx = ime.getBottom(this)
                    IntOffset(0, -maxOf(if (imePx > 0) imePx + stripGapPx else 0, restBottomPx))
                }
                .onSizeChanged { stripHeightPx = it.height },
            enter = if (reducedMotion) androidx.compose.animation.EnterTransition.None else
                androidx.compose.animation.slideInVertically(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)) { it } +
                    androidx.compose.animation.fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
            exit = if (reducedMotion) androidx.compose.animation.ExitTransition.None else
                androidx.compose.animation.slideOutVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)) { it } +
                    androidx.compose.animation.fadeOut(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)),
            label = "live_song_strip"
        ) {
            LiveSongStrip(
                query = query,
                songs = songs,
                currentSongId = currentSongId,
                expanded = stripExpanded,
                onExpandedChange = onStripExpandedChange,
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
            )
        }
    }
}

/** How many columns the strip has: the top row holds songs[0 until cols], the second row the rest. */
internal fun liveStripColumns(songCount: Int): Int = if (songCount >= 2) (songCount + 1) / 2 else songCount

/** Index of the song in the second row under column [column], or null when there is none. */
internal fun liveStripLowerIndex(songCount: Int, column: Int): Int? {
    if (songCount < 2) return null
    val lower = column + liveStripColumns(songCount)
    return lower.takeIf { it < songCount }
}

/**
 * The docked song strip: one row, or two after pulling up. Each column is a best-ranked song on
 * top and a lower-ranked one under it, so expanding grows the second row in without reshuffling
 * the first. The expand progress is read only in layout / graphicsLayer.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LiveSongStrip(
    query: String,
    songs: List<Song>,
    currentSongId: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val reducedMotion = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
    val canExpand = songs.size >= 2
    val progress = remember { Animatable(if (expanded && canExpand) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val settleSpec: androidx.compose.animation.core.AnimationSpec<Float> = if (reducedMotion) snap() else spatialSpec
    LaunchedEffect(expanded, canExpand) {
        if (!dragging) progress.animateTo(if (expanded && canExpand) 1f else 0f, settleSpec)
    }
    val rowPx = with(density) { LiveSongRowHeight.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    val rangePx = rowPx + gapPx
    val flingPx = with(density) { 400.dp.toPx() }
    val stripListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(query) { stripListState.scrollToItem(0) }
    val cols = liveStripColumns(songs.size)

    val dragState = rememberDraggableState { dy ->
        scope.launch { progress.snapTo((progress.value - dy / rangePx).coerceIn(0f, 1f)) }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = canExpand,
                onDragStarted = { dragging = true },
                onDragStopped = { velocity ->
                    dragging = false
                    val up = velocity < -flingPx || (velocity <= flingPx && progress.value > 0.5f)
                    if (up != expanded) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onExpandedChange(up)
                    progress.animateTo(if (up) 1f else 0f, settleSpec, initialVelocity = -velocity / rangePx)
                }
            )
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Handle: tap to switch between one and two rows.
            val handleLabel = stringResource(if (expanded) R.string.search_live_strip_collapse else R.string.search_live_strip_expand)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (canExpand) 20.dp else 8.dp)
                    .then(
                        if (canExpand) Modifier.clickable(
                            onClickLabel = handleLabel,
                            role = Role.Button
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onExpandedChange(!expanded)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (canExpand) {
                    Box(
                        Modifier
                            .size(width = 32.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }
            }
            LazyRow(
                state = stripListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                        val height = (rowPx + rangePx * progress.value).toInt()
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    }
                    .clipToBounds(),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                items(cols, key = { i -> "live_col_${songs[i].id}" }) { i ->
                    val top = songs[i]
                    val lowerIndex = liveStripLowerIndex(songs.size, i)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiveSongCell(
                            song = top,
                            isCurrent = top.id == currentSongId,
                            onClick = { onSongClick(top) },
                            onLongClick = { onSongLongClick(top) }
                        )
                        if (lowerIndex != null) {
                            val lower = songs[lowerIndex]
                            Box(
                                Modifier.graphicsLayer {
                                    alpha = progress.value
                                    translationY = (1f - progress.value) * -8.dp.toPx()
                                }
                            ) {
                                LiveSongCell(
                                    song = lower,
                                    isCurrent = lower.id == currentSongId,
                                    onClick = { onSongClick(lower) },
                                    onLongClick = { onSongLongClick(lower) }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private val LiveSongRowHeight = 60.dp

@Composable
private fun LiveSongCell(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(268.dp)
            .height(LiveSongRowHeight)
            .clip(RoundedCornerShape(16.dp))
            .songStartFeedback(song.id, RoundedCornerShape(16.dp))
            .offlineVeil(song, RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = null,
            shape = RoundedCornerShape(12.dp),
            targetSize = SmartImageListTargetSize,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LiveSuggestionRow(
    query: String,
    suggestion: LiveSearchSuggestion,
    onClick: () -> Unit,
    onFill: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon: ImageVector = when (suggestion.kind) {
        LiveSearchSuggestion.Kind.History -> Icons.Rounded.History
        LiveSearchSuggestion.Kind.Query -> Icons.Rounded.Search
        LiveSearchSuggestion.Kind.Artist -> Icons.Rounded.Person
        LiveSearchSuggestion.Kind.Song -> Icons.Rounded.MusicNote
        LiveSearchSuggestion.Kind.Album -> Icons.Rounded.Album
        LiveSearchSuggestion.Kind.Playlist -> Icons.AutoMirrored.Rounded.QueueMusic
    }
    // The typed part in regular weight, the rest of the suggestion bold (what you'd still type).
    val typed = query.trim()
    val label = remember(suggestion.text, typed) {
        buildAnnotatedString {
            val at = suggestion.text.indexOf(typed, ignoreCase = true)
            if (typed.isEmpty() || at < 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(suggestion.text) }
            } else {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(suggestion.text.substring(0, at)) }
                append(suggestion.text.substring(at, at + typed.length))
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(suggestion.text.substring(at + typed.length)) }
            }
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            suggestion.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Put the suggestion in the search bar without searching yet (keep typing from it).
        IconButton(onClick = onFill) {
            Icon(
                Icons.Rounded.NorthWest,
                contentDescription = "Use “${suggestion.text}”",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
