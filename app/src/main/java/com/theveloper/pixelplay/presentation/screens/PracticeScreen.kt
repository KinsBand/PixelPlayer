package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.ui.theme.MotionTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.practice.PracticeEntry
import com.theveloper.pixelplay.data.practice.PracticeStage
import com.theveloper.pixelplay.data.practice.PracticeStore
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongPickerBottomSheet
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Songs you're learning to play, laid out like Your Music: a tinted hero with a big Play button,
 * three stage tiles (tap one to show only that stage), then Want to learn → Learning → Finished.
 * Tap a song to play it (the rest of its section follows); the pill moves it on to the next
 * section, the undo arrow moves it back, and ⋮ moves it anywhere or removes it.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { PracticeStore.get(context) }
    val entries by store.entries.collectAsStateWithLifecycle()
    val ids = remember(entries) { entries.map { it.songId } }
    val songsFlow = remember(ids) { if (ids.isEmpty()) flowOf(emptyList()) else playerViewModel.observeSongs(ids) }
    val songs by songsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val songById = remember(songs) { songs.associateBy { it.id } }
    val playerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    // One stage at a time when a tile is selected; null shows all three.
    var focusStage by rememberSaveable { mutableStateOf<PracticeStage?>(null) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()

    val byStage = remember(entries) {
        PracticeStage.entries.associateWith { stage ->
            entries.filter { it.stage == stage }.sortedByDescending { it.movedAt }
        }
    }

    fun play(stage: PracticeStage, start: PracticeEntry?) {
        val list = byStage[stage].orEmpty().mapNotNull { songById[it.songId] }
        if (list.isEmpty()) return
        val first = start?.let { songById[it.songId] } ?: list.first()
        playerViewModel.playSongs(list, first, "Practice · ${stage.label}")
    }

    // The hero's Play button: what you're learning first, then what you want to learn.
    val heroStage = focusStage?.takeIf { byStage[it].orEmpty().isNotEmpty() }
        ?: listOf(PracticeStage.LEARNING, PracticeStage.WANT, PracticeStage.FINISHED)
            .firstOrNull { byStage[it].orEmpty().isNotEmpty() }

    val isHeaderCollapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val colors = MaterialTheme.colorScheme
    val topBarColor by animateColorAsState(
        targetValue = if (isHeaderCollapsed) colors.surfaceContainer else colors.tertiaryContainer,
        animationSpec = tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized),
        label = "practiceTopBar"
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        // ---- Header bar: back (left), title once scrolled, add (right) ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(topBarColor)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.surfaceContainerHigh),
                onClick = onBackClick
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isHeaderCollapsed,
                    enter = fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)),
                    exit = fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
                ) {
                    Text(
                        "Practice",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
            FilledTonalIconButton(
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.surfaceContainerHigh),
                onClick = { showPicker = true }
            ) { Icon(Icons.Rounded.Add, contentDescription = "Add songs") }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = MiniPlayerHeight + bottomInset + 24.dp)
        ) {
            item(key = "practice_hero", contentType = "hero") {
                PracticeHero(
                    subtitle = if (entries.isEmpty()) "Songs you're learning to play"
                    else PracticeStage.entries.joinToString(" · ") { "${byStage[it].orEmpty().size} ${it.shortLabel()}" },
                    playLabel = heroStage?.let { "Play ${it.label}" },
                    onPlay = { heroStage?.let { play(it, null) } },
                    onAdd = { showPicker = true },
                )
            }

            if (entries.isEmpty()) {
                item(key = "practice_empty", contentType = "empty") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Nothing to practise yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Add songs you want to learn. Move them to Learning when you start and to Finished when you've got them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                item(key = "practice_stage_tiles", contentType = "tiles") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PracticeStage.entries.forEach { stage ->
                            StageTile(
                                stage = stage,
                                count = byStage[stage].orEmpty().size,
                                selected = focusStage == stage,
                                onClick = { focusStage = if (focusStage == stage) null else stage },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }

                PracticeStage.entries.filter { focusStage == null || it == focusStage }.forEach { stage ->
                    val list = byStage[stage].orEmpty()
                    val isCollapsed = focusStage == null && stage.name in collapsed
                    item(key = "header_${stage.name}", contentType = "stage_header") {
                        StageHeader(
                            stage = stage,
                            count = list.size,
                            collapsed = isCollapsed,
                            collapsible = focusStage == null,
                            onToggle = { collapsed = if (isCollapsed) collapsed - stage.name else collapsed + stage.name },
                            onPlay = { play(stage, null) },
                            modifier = Modifier.animateItem().padding(horizontal = 12.dp)
                        )
                    }
                    if (!isCollapsed) {
                        if (list.isEmpty()) {
                            item(key = "empty_${stage.name}", contentType = "stage_empty") {
                                Text(
                                    when (stage) {
                                        PracticeStage.WANT -> "Songs you'd like to learn go here."
                                        PracticeStage.LEARNING -> "Tap Start on a song to begin learning it."
                                        PracticeStage.FINISHED -> "Tap Done when you can play a song."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier
                                        .animateItem()
                                        .padding(horizontal = 24.dp, vertical = 6.dp)
                                )
                            }
                        }
                        items(list, key = { "song_${it.songId}" }, contentType = { "practice_song" }) { entry ->
                            PracticeRow(
                                entry = entry,
                                song = songById[entry.songId],
                                isCurrent = playerState.currentSong?.id == entry.songId,
                                isPlaying = playerState.isPlaying,
                                onPlay = { play(stage, entry) },
                                onMove = { store.move(entry.songId, it) },
                                onRemove = { store.remove(entry.songId) },
                                modifier = Modifier.animateItem().padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        SongPickerBottomSheet(
            initiallySelectedSongIds = ids.toSet(),
            onDismiss = { showPicker = false },
            onConfirm = { selected ->
                showPicker = false
                val current = ids.toSet()
                (current - selected).forEach { store.remove(it) }
                val added = (selected - current).toList()
                if (added.isNotEmpty()) scope.launch {
                    val found = runCatching { playerViewModel.getSongs(added) }.getOrDefault(emptyList())
                    val byId = found.associateBy { it.id }
                    store.add(added.map { id -> Triple(id, byId[id]?.title.orEmpty(), byId[id]?.artist.orEmpty()) })
                }
            },
            playerViewModel = playerViewModel
        )
    }
}

/** Playlist-style header, matching Your Music: tinted backdrop, cover, title, counts, big Play. */
@Composable
private fun PracticeHero(subtitle: String, playLabel: String?, onPlay: () -> Unit, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to colors.tertiaryContainer,
                    0.55f to colors.tertiaryContainer.copy(alpha = 0.35f),
                    1f to colors.surface
                )
            )
    ) {
        val coverSize = (maxWidth * 0.42f).coerceAtMost(200.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(coverSize)
                    .shadow(12.dp, MaterialTheme.shapes.large)
                    .clip(MaterialTheme.shapes.large)
                    .background(Brush.linearGradient(listOf(colors.tertiary, colors.primary))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.School, contentDescription = null, tint = colors.onTertiary, modifier = Modifier.size(coverSize * 0.45f))
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Practice",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansRounded),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(16.dp))
            if (playLabel != null) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.onSurface)
                        .clickable(onClick = onPlay)
                        .semantics { contentDescription = playLabel },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.surface, modifier = Modifier.size(34.dp))
                }
            } else {
                FilledTonalButton(onClick = onAdd, contentPadding = PaddingValues(horizontal = 18.dp)) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add songs")
                }
            }
        }
    }
}

/** One of the three stage tiles under the hero (same card style as the Playlists tab tiles). */
@Composable
private fun StageTile(
    stage: PracticeStage,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = stageColor(stage)
    val container by animateColorAsState(
        targetValue = if (selected) tint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized),
        label = "stageTile"
    )
    Card(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (selected) 0.28f else 0.16f)),
                contentAlignment = Alignment.Center
            ) { Icon(stage.icon(), null, Modifier.size(22.dp), tint = tint) }
            Spacer(Modifier.height(6.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold
            )
            Text(
                stage.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun PracticeStage.shortLabel() = when (this) {
    PracticeStage.WANT -> "want"
    PracticeStage.LEARNING -> "learning"
    PracticeStage.FINISHED -> "finished"
}

private fun PracticeStage.icon(): ImageVector = when (this) {
    PracticeStage.WANT -> Icons.Rounded.Add
    PracticeStage.LEARNING -> Icons.Rounded.School
    PracticeStage.FINISHED -> Icons.Rounded.Check
}

@Composable
private fun stageColor(stage: PracticeStage): Color = when (stage) {
    PracticeStage.WANT -> MaterialTheme.colorScheme.secondary
    PracticeStage.LEARNING -> MaterialTheme.colorScheme.primary
    PracticeStage.FINISHED -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun StageHeader(
    stage: PracticeStage,
    count: Int,
    collapsed: Boolean,
    collapsible: Boolean,
    onToggle: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "stageArrow")
    val tint = stageColor(stage)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = collapsible, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Icon(stage.icon(), null, Modifier.size(18.dp), tint = tint) }
        Spacer(Modifier.width(10.dp))
        Text(
            stage.label,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
            fontWeight = FontWeight.Bold
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
        if (collapsible) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (collapsed) "Show" else "Hide",
                modifier = Modifier
                    .padding(start = 2.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        if (count > 0) {
            FilledTonalIconButton(onClick = onPlay, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${stage.label}")
            }
        }
    }
}

@Composable
private fun PracticeRow(
    entry: PracticeEntry,
    song: Song?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onMove: (PracticeStage) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val next = entry.stage.next
    val back = entry.stage.previous
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .clickable(enabled = song != null, onClick = onPlay)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            SmartImage(
                model = song?.albumArtUriString,
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(14.dp)
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    PlayingEqIcon(
                        Modifier.size(width = 18.dp, height = 16.dp),
                        color = Color.White,
                        isPlaying = isPlaying,
                        phaseDurationMillis = 2400
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song?.title ?: entry.title.ifBlank { "Unavailable song" },
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song?.displayArtist ?: entry.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Back one section (Finished → Learning → Want).
        if (back != null) {
            IconButton(onClick = { onMove(back) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = "Back to ${back.label}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // On to the next section: Start (→ Learning) or Done (→ Finished).
        if (next != null) {
            Surface(
                onClick = { onMove(next) },
                shape = RoundedCornerShape(50),
                color = stageColor(next),
                contentColor = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(next.icon(), null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (next == PracticeStage.LEARNING) "Start" else "Done",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (song != null) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                        onClick = { menuOpen = false; onPlay() }
                    )
                }
                PracticeStage.entries.filter { it != entry.stage }.forEach { stage ->
                    DropdownMenuItem(
                        text = { Text("Move to ${stage.label}") },
                        leadingIcon = { Icon(stage.icon(), null) },
                        onClick = { menuOpen = false; onMove(stage) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Remove from Practice") },
                    onClick = { menuOpen = false; onRemove() }
                )
            }
        }
    }
}

// Used by the Playlists tab button: "3 want · 1 learning · 5 finished".
fun practiceSummary(entries: List<PracticeEntry>): String =
    if (entries.isEmpty()) "Songs you're learning to play"
    else PracticeStage.entries.joinToString(" · ") { stage -> "${entries.count { it.stage == stage }} ${stage.shortLabel()}" }
