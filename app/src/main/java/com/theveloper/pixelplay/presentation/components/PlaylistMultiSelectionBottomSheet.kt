package com.theveloper.pixelplay.presentation.components

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.MicExternalOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.accounts.MusicSources
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.presentation.viewmodel.PlaybackDispatchStateHolder
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.utils.resolvePlaylistCoverContentColor
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Batch actions for selected playlists.
 *
 * - Header: the selected covers in an overlapping strip. Tap covers to pick any of them (each
 *   picked one gets an accent outline); picking all of them, or none, acts on every selected one.
 * - Queue · Next · Soon for the active playlists. Soon opens the same "after which song" panel as
 *   a playlist's own options (slider, the song it plays after, tick to confirm).
 * - Merge (2+ active) and Share. Share opens in place into the platform link (Spotify / YouTube
 *   Music / Apple Music playlists), Share file (.m3u) or Share ZIP (2+), Download (saves .m3u
 *   files to the device) and Back.
 * - Delete sits alone at the very bottom, away from the everyday actions.
 *
 * Every action applies to the active playlists: the picked ones, or all selected.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistMultiSelectionBottomSheet(
    selectedPlaylists: List<Playlist>,
    onDismiss: () -> Unit,
    onDelete: (List<Playlist>) -> Unit,
    onMerge: (List<Playlist>) -> Unit,
    /** Shares the playlists as one .m3u (one playlist) or a .zip of .m3u files (several). */
    onShareFiles: (List<Playlist>) -> Unit,
    /** Queues the active playlists as one block at [PlaybackDispatchStateHolder.PlaylistQueuePlacement]. */
    onQueue: (List<Playlist>, PlaybackDispatchStateHolder.PlaylistQueuePlacement) -> Unit,
    /** Soon's shuffle button: queue the active playlists as one block in a random order. */
    onQueueShuffled: ((List<Playlist>, PlaybackDispatchStateHolder.PlaylistQueuePlacement) -> Unit)? = null,
    /** Plays the active playlists as one queue, in the order shown. */
    onPlay: (List<Playlist>) -> Unit,
    /** Saves the playlists as .m3u files on the device; null hides the Download button. */
    onExport: ((List<Playlist>) -> Unit)? = null,
    /** For Soon's queue panel; null = Soon queues after the next [SOON_AFTER_SONGS] songs. */
    playerViewModel: com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel? = null,
) {
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val evenCornerRadius = 18.dp
    val buttonShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = evenCornerRadius, smoothnessAsPercentBR = 60,
        cornerRadiusBR = evenCornerRadius, smoothnessAsPercentTL = 60,
        cornerRadiusTL = evenCornerRadius, smoothnessAsPercentBL = 60,
        cornerRadiusBL = evenCornerRadius, smoothnessAsPercentTR = 60
    )
    var focusedIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var shareExpanded by rememberSaveable { mutableStateOf(false) }
    var soonOpen by rememberSaveable { mutableStateOf(false) }
    // Picked playlists that left the selection drop out (none left = all of them again).
    LaunchedEffect(selectedPlaylists) {
        val present = selectedPlaylists.mapTo(HashSet()) { it.id }
        if (focusedIds.any { it !in present }) focusedIds = focusedIds.filterTo(HashSet()) { it in present }
    }
    val active = remember(selectedPlaylists, focusedIds) {
        if (focusedIds.isEmpty() || focusedIds.size == selectedPlaylists.size) {
            selectedPlaylists
        } else {
            selectedPlaylists.filter { it.id in focusedIds }.ifEmpty { selectedPlaylists }
        }
    }
    val single = active.singleOrNull()
    val context = LocalContext.current

    val dismissSheetAnimated = rememberAnimatedSheetDismiss(sheetState, onDismiss)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(MotionTokens.DurationMedium1, easing = MotionTokens.Emphasized))
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---- Header: interactive covers + "N playlists selected" ----
            InteractivePlaylistCovers(
                playlists = selectedPlaylists,
                focusedIds = focusedIds,
                onCoverClick = { id ->
                    focusedIds = when {
                        focusedIds.isEmpty() -> setOf(id)
                        id in focusedIds -> focusedIds - id
                        else -> {
                            val next = focusedIds + id
                            if (next.size == selectedPlaylists.size) emptySet() else next
                        }
                    }
                    shareExpanded = false
                }
            )
            Text(
                text = if (active.size == 1) stringResource(R.string.playlist_multi_one_selected)
                    else pluralStringResource(R.plurals.playlist_multi_selected, active.size, active.size),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (single != null) {
                Text(
                    text = single.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // ---- Queue · Play · Next (the active playlists, as one block); hold Next = Soon ----
            QueuePlayNextRow(
                onQueue = {
                    onQueue(active, PlaybackDispatchStateHolder.PlaylistQueuePlacement.End)
                    dismissSheetAnimated()
                },
                onPlay = {
                    onPlay(active)
                    dismissSheetAnimated()
                },
                onNext = {
                    onQueue(active, PlaybackDispatchStateHolder.PlaylistQueuePlacement.Next)
                    dismissSheetAnimated()
                },
                onNextLongClick = {
                    if (playerViewModel != null) {
                        soonOpen = !soonOpen
                    } else {
                        // No player to read Up next from: the usual 3-song window.
                        onQueue(active, PlaybackDispatchStateHolder.PlaylistQueuePlacement.AfterUpcoming(SOON_AFTER_SONGS))
                        dismissSheetAnimated()
                    }
                },
                soonOpen = soonOpen,
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = soonOpen && playerViewModel != null,
                enter = expandVertically(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)) +
                    fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
                exit = shrinkVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)) +
                    fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)),
            ) {
                if (playerViewModel != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        PlaylistSoonPanel(
                            playerViewModel = playerViewModel,
                            onBack = { soonOpen = false },
                            onConfirm = { after ->
                                onQueue(active, PlaybackDispatchStateHolder.PlaylistQueuePlacement.AfterUpcoming(after))
                                dismissSheetAnimated()
                            },
                            onConfirmShuffled = onQueueShuffled?.let { queueShuffled ->
                                { after: Int ->
                                    queueShuffled(active, PlaybackDispatchStateHolder.PlaylistQueuePlacement.AfterUpcoming(after))
                                    dismissSheetAnimated()
                                }
                            },
                            plural = active.size > 1,
                        )
                    }
                }
            }

            // ---- Merge | Share, or the expanded share options ----
            val platformUrl = single?.let { platformPlaylistUrl(it) }
            AnimatedContent(
                targetState = shareExpanded,
                transitionSpec = {
                    (fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)) +
                        scaleIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate), initialScale = 0.92f)) togetherWith
                        fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
                },
                label = "playlistShareRow"
            ) { expanded ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!expanded) {
                        if (active.size >= 2) {
                            SheetActionButton(
                                label = stringResource(R.string.common_merge),
                                icon = rememberVectorPainter(Icons.Rounded.Merge),
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = buttonShape,
                                horizontal = true,
                                modifier = Modifier.weight(1f),
                            ) {
                                onMerge(active)
                                dismissSheetAnimated()
                            }
                        }
                        SheetActionButton(
                            label = stringResource(R.string.common_share),
                            icon = rememberVectorPainter(Icons.Rounded.Share),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = buttonShape,
                            horizontal = true,
                            modifier = Modifier.weight(if (active.size >= 2) 1.4f else 1f),
                        ) { shareExpanded = true }
                    } else {
                        if (platformUrl != null && single != null) {
                            val platformName = platformName(single.source)
                            val shareOn = stringResource(R.string.playlist_multi_share_on, platformName)
                            FilledTonalIconButton(
                                onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, platformUrl)
                                        putExtra(Intent.EXTRA_SUBJECT, single.name)
                                    }
                                    context.startActivity(Intent.createChooser(send, shareOn))
                                    dismissSheetAnimated()
                                },
                                modifier = Modifier.size(58.dp).semantics { contentDescription = shareOn },
                                shape = buttonShape,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                SourceBadge(source = single.source)
                            }
                        }
                        SheetActionButton(
                            label = stringResource(if (active.size == 1) R.string.playlist_multi_share_file else R.string.playlist_multi_share_zip),
                            icon = rememberVectorPainter(if (active.size == 1) Icons.Rounded.Description else Icons.Rounded.Archive),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = buttonShape,
                            horizontal = true,
                            modifier = Modifier.weight(1f),
                        ) {
                            onShareFiles(active)
                            dismissSheetAnimated()
                        }
                        // Download: saves the .m3u file(s) to the device instead of sharing them.
                        if (onExport != null) {
                            val downloadLabel = stringResource(R.string.playlist_multi_download)
                            FilledTonalIconButton(
                                onClick = {
                                    onExport(active)
                                    dismissSheetAnimated()
                                },
                                modifier = Modifier.size(58.dp).semantics { contentDescription = downloadLabel },
                                shape = buttonShape,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null)
                            }
                        }
                        FilledTonalIconButton(
                            onClick = { shareExpanded = false },
                            modifier = Modifier.size(58.dp),
                            shape = buttonShape
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                        }
                    }
                }
            }

            // ---- Delete: alone at the very bottom ----
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = buttonShape,
                onClick = {
                    onDelete(active)
                    dismissSheetAnimated()
                }
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.song_info_action_delete), fontFamily = GoogleSansRounded, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Soon for a whole playlist: it starts after this many upcoming songs. */
private const val SOON_AFTER_SONGS = 3

/** The playlist's page on its platform (Spotify / YouTube Music / Apple Music), or null for local ones. */
private fun platformPlaylistUrl(playlist: Playlist): String? {
    val source = playlist.source.uppercase()
    if (source != MusicSources.SPOTIFY && source != MusicSources.YOUTUBE_MUSIC && source != MusicSources.APPLE_MUSIC) return null
    val remoteId = playlist.id.takeIf { it.startsWith("$source:") }?.substringAfter(':') ?: return null
    return MusicSources.playlistUrl(source, remoteId)
}

private fun platformName(source: String): String = when (source.uppercase()) {
    MusicSources.SPOTIFY -> "Spotify"
    MusicSources.YOUTUBE_MUSIC -> "YouTube Music"
    MusicSources.APPLE_MUSIC -> "Apple Music"
    else -> source
}

/** A tonal action button: icon over label (stacked) or icon beside label ([horizontal]). */
@Composable
private fun SheetActionButton(
    label: String,
    icon: Painter,
    container: Color,
    content: Color,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        modifier = modifier.heightIn(min = 58.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        shape = shape,
        onClick = onClick
    ) {
        if (horizontal) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(label, fontFamily = GoogleSansRounded, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, fontFamily = GoogleSansRounded,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The selected covers, overlapping. Tapping one picks it (accent outline, raised to the front);
 * several can be picked; tapping a picked one un-picks it.
 */
@Composable
private fun InteractivePlaylistCovers(
    playlists: List<Playlist>,
    focusedIds: Set<String>,
    onCoverClick: (String) -> Unit,
) {
    val imageSize = 66.dp
    val step = 40.dp
    val ringColor = MaterialTheme.colorScheme.surfaceContainerLow
    Box(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
    ) {
        Box(Modifier.width(step * (playlists.size - 1).coerceAtLeast(0) + imageSize).height(imageSize)) {
            playlists.forEachIndexed { index, playlist ->
                val focused = playlist.id in focusedIds
                val outline by animateDpAsState(
                    targetValue = if (focused) 2.5.dp else 0.dp,
                    animationSpec = tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized),
                    label = "coverFocusOutline"
                )
                val label = if (focused) stringResource(R.string.playlist_multi_unpick, playlist.name)
                    else stringResource(R.string.playlist_multi_focus, playlist.name)
                Box(
                    modifier = Modifier
                        .offset(x = step * index)
                        .zIndex(if (focused) playlists.size + 1f else (playlists.size - index).toFloat())
                        .size(imageSize)
                        .clip(CircleShape)
                        .background(ringColor)
                        .then(if (outline > 0.dp) Modifier.border(outline, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                        .clickable(onClickLabel = label, role = Role.Button) { onCoverClick(playlist.id) }
                        .semantics { selected = focused; contentDescription = playlist.name }
                        .padding(3.dp)
                ) {
                    PlaylistCover(playlist)
                }
            }
        }
    }
}

@Composable
private fun PlaylistCover(playlist: Playlist) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (playlist.coverImageUri != null) {
            SmartImage(
                model = playlist.coverImageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(60.dp)
            )
        } else if (playlist.coverColorArgb != null) {
            val contentColor = resolvePlaylistCoverContentColor(playlist.coverColorArgb, MaterialTheme.colorScheme)
            Box(
                modifier = Modifier.size(60.dp).background(Color(playlist.coverColorArgb)),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.coverIconName != null) {
                    Icon(
                        imageVector = getPlaylistIconByName(playlist.coverIconName) ?: Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = playlist.name.firstOrNull()?.toString()?.uppercase() ?: "P",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.size(60.dp).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = playlist.name.firstOrNull()?.toString()?.uppercase() ?: "P",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

private fun getPlaylistIconByName(name: String?): androidx.compose.ui.graphics.vector.ImageVector? {
    return when (name) {
        "MusicNote" -> Icons.Rounded.MusicNote
        "Headphones" -> Icons.Rounded.Headphones
        "Album" -> Icons.Rounded.Album
        "Mic" -> Icons.Rounded.MicExternalOn
        "Speaker" -> Icons.Rounded.Speaker
        "Favorite" -> Icons.Rounded.Favorite
        "Piano" -> Icons.Rounded.Piano
        "Queue" -> Icons.AutoMirrored.Rounded.QueueMusic
        else -> Icons.Rounded.MusicNote
    }
}
