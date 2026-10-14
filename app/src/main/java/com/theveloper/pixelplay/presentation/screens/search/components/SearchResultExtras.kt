package com.theveloper.pixelplay.presentation.screens.search.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.search.SearchVersionGrouping
import com.theveloper.pixelplay.presentation.components.SkeletonBlock
import com.theveloper.pixelplay.presentation.components.SkeletonSongRow
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SmartImageListTargetSize
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.abs

// ---------------------------------------------------------------------------------------------
// Versions accordion (a song's other cuts, folded by SearchVersionGrouping)
// ---------------------------------------------------------------------------------------------

/** "+3 versions" pill under a song's artist line. Tapping it opens/closes the list below. */
@Composable
fun SearchVersionsPill(
    count: Int,
    songTitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduced = rememberSystemReducedMotion()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduced) tween(0) else tween(250),
        label = "versionsChevron"
    )
    val label = pluralStringResource(R.plurals.search_versions_toggle, count, count)
    val description = if (expanded) stringResource(R.string.search_cd_versions_collapse, songTitle)
    else stringResource(R.string.search_cd_versions_expand, songTitle)
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.primaryContainer.copy(alpha = 0.5f))
            .clickable(onClick = onToggle)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            }
            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onPrimaryContainer
        )
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = colors.onPrimaryContainer,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rotation }
        )
    }
}

/**
 * The folded versions of [canonical], indented under its row. Tap plays a version, hold opens
 * its options. [expanded] animates the list open/closed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchVersionsList(
    expanded: Boolean,
    canonical: Song,
    versions: List<Song>,
    currentSongId: String?,
    onPlay: (Song) -> Unit,
    onOptions: (Song) -> Unit
) {
    val reduced = rememberSystemReducedMotion()
    // Explicit (non-scope) AnimatedVisibility: this is called inside a Column.
    androidx.compose.animation.AnimatedVisibility(
        visible = expanded,
        enter = if (reduced) fadeIn(tween(0)) else expandVertically(tween(250)) + fadeIn(),
        exit = if (reduced) fadeOut(tween(0)) else shrinkVertically(tween(200)) + fadeOut()
    ) {
        val badges = remember(canonical.id, versions) {
            versions.map { SearchVersionGrouping.versionBadge(it, canonical) }
        }
        Column(
            modifier = Modifier.padding(start = 28.dp, top = 4.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            versions.forEachIndexed { index, version ->
                SearchVersionRow(
                    version = version,
                    badge = badges.getOrNull(index),
                    isCurrent = version.id == currentSongId,
                    onClick = { onPlay(version) },
                    onLongClick = { onOptions(version) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchVersionRow(
    version: Song,
    badge: SearchVersionGrouping.VersionBadge?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = remember { AbsoluteSmoothCornerShape(12.dp, 60) }
    val badgeText = badge?.let { versionBadgeText(it, version) }
    val subtitle = remember(version.id) {
        listOfNotNull(version.year.takeIf { it > 0 }?.toString(), version.album.takeIf { it.isNotBlank() })
            .joinToString(" · ")
    }
    val duration = remember(version.duration) { formatDuration(version.duration) }
    val playDescription = stringResource(R.string.search_cd_play_version, version.title, badgeText ?: version.displayArtist)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(
                if (isCurrent) colors.primaryContainer.copy(alpha = 0.45f)
                else colors.surfaceContainerLowest.copy(alpha = 0.65f)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = playDescription,
                onLongClickLabel = stringResource(R.string.search_cd_song_options)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (badgeText != null) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                            .weight(1f, fill = false)
                    )
                }
                Text(
                    text = version.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (duration != null) {
            Text(
                text = duration,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = colors.primary.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun versionBadgeText(badge: SearchVersionGrouping.VersionBadge, version: Song): String? =
    when (badge.kind) {
        SearchVersionGrouping.VersionKind.COVER -> stringResource(R.string.search_version_cover_by, version.displayArtist)
        SearchVersionGrouping.VersionKind.REMASTERED -> stringResource(R.string.search_version_remastered)
        SearchVersionGrouping.VersionKind.ACOUSTIC -> stringResource(R.string.search_version_acoustic)
        SearchVersionGrouping.VersionKind.LIVE -> stringResource(R.string.search_version_live)
        SearchVersionGrouping.VersionKind.REMIX -> stringResource(R.string.search_version_remix)
        SearchVersionGrouping.VersionKind.DEMO -> stringResource(R.string.search_version_demo)
        SearchVersionGrouping.VersionKind.INSTRUMENTAL -> stringResource(R.string.search_version_instrumental)
        SearchVersionGrouping.VersionKind.KARAOKE -> stringResource(R.string.search_version_karaoke)
        SearchVersionGrouping.VersionKind.RADIO_EDIT -> stringResource(R.string.search_version_radio_edit)
        SearchVersionGrouping.VersionKind.EXTENDED -> stringResource(R.string.search_version_extended)
        SearchVersionGrouping.VersionKind.SPED_UP -> stringResource(R.string.search_version_sped_up)
        SearchVersionGrouping.VersionKind.SLOWED -> stringResource(R.string.search_version_slowed)
        SearchVersionGrouping.VersionKind.RE_RECORDED -> stringResource(R.string.search_version_re_recorded)
        // Whatever the title's brackets say ("2011 Mix", "Mono"): song data, not UI text.
        SearchVersionGrouping.VersionKind.OTHER -> badge.rawTag
    }

private fun formatDuration(ms: Long): String? {
    if (ms <= 0L) return null
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

// ---------------------------------------------------------------------------------------------
// Top result hero card
// ---------------------------------------------------------------------------------------------

enum class TopResultKind { ARTIST, SONG, ALBUM }

/**
 * "Top result" card shown first when the query names an artist, song or album (almost)
 * exactly. Tapping the card opens it (a song plays); the round button plays it.
 */
@Composable
fun SearchTopResultCard(
    kind: TopResultKind,
    title: String,
    subtitle: String,
    imageModel: Any?,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val brush = remember(colors.surfaceContainerHighest, colors.surfaceContainerHigh) {
        Brush.linearGradient(listOf(colors.surfaceContainerHighest, colors.surfaceContainerHigh))
    }
    val pill = stringResource(
        when (kind) {
            TopResultKind.ARTIST -> R.string.search_top_result_artist
            TopResultKind.SONG -> R.string.search_top_result_song
            TopResultKind.ALBUM -> R.string.search_top_result_album
        }
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp)
            .clip(shape)
            .background(brush)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartImage(
            model = imageModel,
            contentDescription = null,
            shape = if (kind == TopResultKind.ARTIST) CircleShape else RoundedCornerShape(16.dp),
            targetSize = SmartImageListTargetSize,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(color = colors.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                Text(
                    text = pill,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimaryContainer,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onPlay,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.search_cd_top_result_play, title)
            )
        }
    }
}

/** Short "Added to queue" pill that floats over the results after a swipe. */
@Composable
fun SearchQueueFeedbackPill(message: String?, modifier: Modifier = Modifier) {
    val reduced = rememberSystemReducedMotion()
    // Keep showing the last text while the pill animates out.
    val lastMessage = remember { arrayOf<String?>(null) }
    if (message != null) lastMessage[0] = message
    androidx.compose.animation.AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = if (reduced) fadeIn(tween(0)) else fadeIn(tween(150)) + slideInVertically(tween(200)) { it / 2 },
        exit = if (reduced) fadeOut(tween(0)) else fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 }
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.semantics { stateDescription = lastMessage[0].orEmpty() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(lastMessage[0].orEmpty(), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Loading skeleton for a new query
// ---------------------------------------------------------------------------------------------

/** What the results area shows while a new query's first results load. */
@Composable
fun SearchResultsSkeleton(showHero: Boolean, modifier: Modifier = Modifier) {
    val loadingLabel = stringResource(R.string.search_cd_loading_results)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics { contentDescription = loadingLabel },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showHero) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                shape = RoundedCornerShape(24.dp)
            )
        }
        repeat(6) { index ->
            SkeletonSongRow(titleFraction = if (index % 2 == 0) 0.62f else 0.5f)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Filter chip with a result count
// ---------------------------------------------------------------------------------------------

/**
 * One filter chip: 36 dp tall, 10 dp corners. The selected one takes secondaryContainer
 * and bold text weight.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchCountFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.theveloper.pixelplay.presentation.components.ExpressiveFilterChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier
    )
}
