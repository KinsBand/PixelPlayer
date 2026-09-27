package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
 * Songs you're learning to play, in three sections: Want to learn → Learning → Finished.
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
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Practice", fontFamily = GoogleSansRounded, maxLines = 1)
                        Text(
                            PracticeStage.entries.joinToString(" · ") { "${byStage[it].orEmpty().size} ${it.shortLabel()}" },
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = GoogleSansRounded),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        onClick = onBackClick
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showPicker = true },
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add songs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.School, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text("Nothing to practise yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add songs you want to learn. Move them to Learning when you start and to Finished when you've got them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                    )
                    FilledTonalButton(onClick = { showPicker = true }) { Text("Add songs") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = MiniPlayerHeight + bottomInset + 24.dp)
            ) {
                PracticeStage.entries.forEach { stage ->
                    val list = byStage[stage].orEmpty()
                    val isCollapsed = stage.name in collapsed
                    item(key = "header_${stage.name}") {
                        StageHeader(
                            stage = stage,
                            count = list.size,
                            collapsed = isCollapsed,
                            onToggle = { collapsed = if (isCollapsed) collapsed - stage.name else collapsed + stage.name },
                            onPlay = { play(stage, null) },
                            modifier = Modifier.animateItem()
                        )
                    }
                    if (!isCollapsed) {
                        if (list.isEmpty()) {
                            item(key = "empty_${stage.name}") {
                                Text(
                                    when (stage) {
                                        PracticeStage.WANT -> "Songs you'd like to learn go here."
                                        PracticeStage.LEARNING -> "Tap Start on a song to begin learning it."
                                        PracticeStage.FINISHED -> "Tap Done when you can play a song."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .animateItem()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        items(list, key = { "song_${it.songId}" }) { entry ->
                            PracticeRow(
                                entry = entry,
                                song = songById[entry.songId],
                                isCurrent = playerState.currentSong?.id == entry.songId,
                                onPlay = { play(stage, entry) },
                                onMove = { store.move(entry.songId, it) },
                                onRemove = { store.remove(entry.songId) },
                                modifier = Modifier.animateItem()
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
            .clickable(onClick = onToggle)
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
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (collapsed) "Show" else "Hide",
            modifier = Modifier
                .padding(start = 2.dp)
                .rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .clickable(enabled = song != null, onClick = onPlay)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartImage(
            model = song?.albumArtUriString,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song?.title ?: entry.title.ifBlank { "Unavailable song" },
                style = MaterialTheme.typography.titleSmall,
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
