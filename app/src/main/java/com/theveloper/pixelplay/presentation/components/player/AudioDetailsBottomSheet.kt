package com.theveloper.pixelplay.presentation.components.player

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SkeletonSongRow
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.AudioDetailsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.AudioScanState
import com.theveloper.pixelplay.presentation.viewmodel.OnlineVersionsState
import com.theveloper.pixelplay.presentation.viewmodel.SongVersion
import com.theveloper.pixelplay.presentation.viewmodel.SongVersionsState
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.utils.AudioMetaUtils.mimeTypeToFormat
import kotlinx.coroutines.launch
import java.util.Locale

private val LossyFormats = setOf("mp3", "aac", "m4a", "ogg", "opus", "wma", "amr", "3gp", "webm")
private const val UnknownValue = "—"

/**
 * Audio details for the current track: a "Versions" toggle that reveals other versions of the
 * song from the library (original first) followed by every version found online, and a 2×2 grid
 * of quality cards
 * (sample rate, bitrate, format, bit depth).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDetailsBottomSheet(
    song: Song,
    audioMimeType: String?,
    audioBitrate: Int?,
    audioSampleRate: Int?,
    onPlayVersion: (Song) -> Unit,
    onDismiss: () -> Unit,
    viewModel: AudioDetailsViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val versionsState by viewModel.versions.collectAsStateWithLifecycle()
    val onlineVersionsState by viewModel.onlineVersions.collectAsStateWithLifecycle()
    val coverArtists by viewModel.coverArtists.collectAsStateWithLifecycle()
    val coverVersionsState by viewModel.coverVersions.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val scanned = (scanState as? AudioScanState.Done)?.result

    LaunchedEffect(song.id) { viewModel.load(song) }

    // Always leave through hide() so the exit animation plays before the sheet is removed.
    val hideThenDismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        AudioDetailsSheetContent(
            song = song,
            audioMimeType = audioMimeType ?: scanned?.mimeType,
            audioBitrate = audioBitrate?.takeIf { it > 0 } ?: scanned?.bitrate,
            audioSampleRate = audioSampleRate?.takeIf { it > 0 } ?: scanned?.sampleRate,
            audioBitDepth = song.audioTech.bitDepth?.takeIf { it > 0 } ?: scanned?.bitDepth,
            scanState = scanState,
            onScan = { viewModel.scanSong(song) },
            versionsState = versionsState,
            onlineVersionsState = onlineVersionsState,
            onVersionsShown = { viewModel.loadOnline(song) },
            onRetryOnline = { viewModel.loadOnline(song, force = true) },
            coverArtists = coverArtists,
            coverVersionsState = coverVersionsState,
            onCoverArtistsChange = { viewModel.setCoverArtists(song, it) },
            onVersionClick = { version ->
                if (version.song.id != song.id) onPlayVersion(version.song)
                hideThenDismiss()
            }
        )
    }
}

@Composable
private fun AudioDetailsSheetContent(
    song: Song,
    audioMimeType: String?,
    audioBitrate: Int?,
    audioSampleRate: Int?,
    audioBitDepth: Int?,
    scanState: AudioScanState,
    onScan: () -> Unit,
    versionsState: SongVersionsState,
    onlineVersionsState: OnlineVersionsState,
    onVersionsShown: () -> Unit,
    onRetryOnline: () -> Unit,
    coverArtists: List<String>,
    coverVersionsState: OnlineVersionsState,
    onCoverArtistsChange: (List<String>) -> Unit,
    onVersionClick: (SongVersion) -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    var versionsExpanded by rememberSaveable(song.id) { mutableStateOf(false) }
    // The online search only runs once the user actually looks at the versions.
    LaunchedEffect(versionsExpanded, song.id) {
        if (versionsExpanded) onVersionsShown()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        VersionsToggleButton(
            expanded = versionsExpanded,
            versionCount = (versionsState as? SongVersionsState.Loaded)?.versions?.size,
            reduceMotion = reduceMotion,
            onClick = { versionsExpanded = !versionsExpanded }
        )

        val expandDuration = MotionTokens.DurationMedium2
        // Exit ≈ 67% of enter (300ms → 200ms).
        val collapseDuration = MotionTokens.DurationShort4
        AnimatedVisibility(
            visible = versionsExpanded,
            enter = if (reduceMotion) EnterTransition.None else {
                expandVertically(
                    animationSpec = tween(expandDuration, easing = MotionTokens.EmphasizedDecelerate),
                    expandFrom = Alignment.Top
                ) + fadeIn(tween(expandDuration, easing = MotionTokens.EmphasizedDecelerate))
            },
            exit = if (reduceMotion) ExitTransition.None else {
                shrinkVertically(
                    animationSpec = tween(collapseDuration, easing = MotionTokens.EmphasizedAccelerate),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(tween(collapseDuration, easing = MotionTokens.EmphasizedAccelerate))
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VersionsList(
                    currentSongId = song.id,
                    state = versionsState,
                    reduceMotion = reduceMotion,
                    onVersionClick = onVersionClick
                )
                OnlineVersionsList(
                    currentSongId = song.id,
                    songArtist = song.artist,
                    state = onlineVersionsState,
                    reduceMotion = reduceMotion,
                    onRetry = onRetryOnline,
                    coverArtists = coverArtists,
                    coverState = coverVersionsState,
                    onCoverArtistsChange = onCoverArtistsChange,
                    onVersionClick = onVersionClick
                )
            }
        }

        AudioQualityGrid(
            mimeType = audioMimeType,
            bitrate = audioBitrate,
            sampleRate = audioSampleRate,
            bitDepth = audioBitDepth,
            scanState = scanState,
            onScan = onScan
        )

        Spacer(Modifier.heightIn(min = 8.dp))
    }
}

@Composable
private fun VersionsToggleButton(
    expanded: Boolean,
    versionCount: Int?,
    reduceMotion: Boolean,
    onClick: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) snap() else {
            tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized)
        },
        label = "versionsChevron"
    )
    val countDescription = versionCount?.let { if (it == 1) "1 version" else "$it versions" }

    FilledTonalButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
                countDescription?.let { contentDescription = "Versions, $it" }
            }
    ) {
        Text(
            text = "Versions",
            style = MaterialTheme.typography.titleSmall
        )
        if (versionCount != null && versionCount > 1) {
            Spacer(Modifier.width(8.dp))
            Badge(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                Text(versionCount.toString())
            }
        }
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
        )
    }
}

@Composable
private fun VersionsList(
    currentSongId: String,
    state: SongVersionsState,
    reduceMotion: Boolean,
    onVersionClick: (SongVersion) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Versions load after the list is revealed; grow smoothly instead of jumping.
            .animateContentSize(
                animationSpec = if (reduceMotion) snap() else {
                    tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized)
                }
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // "Local" header, matching "Online versions" below: icon, title and how many were found.
        VersionsSectionHeader(
            icon = Icons.Rounded.PhoneAndroid,
            title = "Local",
            count = (state as? SongVersionsState.Loaded)?.versions?.size
        )
        when (state) {
            SongVersionsState.Loading -> {
                repeat(2) { SkeletonSongRow() }
            }
            is SongVersionsState.Loaded -> {
                state.versions.forEach { version ->
                    VersionRow(
                        version = version,
                        isPlaying = version.song.id == currentSongId,
                        onClick = { onVersionClick(version) }
                    )
                }
            }
        }
    }
}

/** Section title row for the versions lists: icon, title, and a count badge once known. */
@Composable
private fun VersionsSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int?,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {
                    count?.let { contentDescription = "$title, $it found" }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (count != null && count > 0) {
                Spacer(Modifier.width(8.dp))
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) { Text(count.toString()) }
            }
        }
        trailing?.invoke()
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun OnlineVersionsList(
    currentSongId: String,
    songArtist: String,
    state: OnlineVersionsState,
    reduceMotion: Boolean,
    onRetry: () -> Unit,
    coverArtists: List<String>,
    coverState: OnlineVersionsState,
    onCoverArtistsChange: (List<String>) -> Unit,
    onVersionClick: (SongVersion) -> Unit
) {
    var showCoverPicker by rememberSaveable { mutableStateOf(false) }
    // Picker suggestions: other artists already found covering this song.
    val suggestions = remember(state, songArtist) {
        val own = coverArtistName(songArtist).lowercase(Locale.ROOT)
        (state as? OnlineVersionsState.Loaded)?.versions.orEmpty()
            .map { coverArtistName(it.song.artist) }
            .filter { it.isNotBlank() && it.lowercase(Locale.ROOT) != own }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }
    val showingCovers = coverArtists.isNotEmpty()
    val shownState = if (showingCovers) coverState else state
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = if (reduceMotion) snap() else {
                    tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized)
                }
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        VersionsSectionHeader(
            icon = Icons.Rounded.Cloud,
            title = if (showingCovers) "Covers" else "Online versions",
            count = (shownState as? OnlineVersionsState.Loaded)?.versions?.size,
            trailing = {
                // Pick several artists to search covers by.
                IconButton(onClick = { showCoverPicker = true }) {
                    BadgedBox(
                        badge = {
                            if (coverArtists.isNotEmpty()) Badge { Text(coverArtists.size.toString()) }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PersonSearch,
                            contentDescription = if (coverArtists.isEmpty()) "Search covers by artists"
                            else "Cover artists, ${coverArtists.size} picked"
                        )
                    }
                }
            }
        )
        // Picked artists: tap one to drop it; Clear goes back to every online version.
        if (showingCovers) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                coverArtists.forEach { artist ->
                    InputChip(
                        selected = true,
                        onClick = { onCoverArtistsChange(coverArtists - artist) },
                        label = { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove $artist", modifier = Modifier.size(16.dp))
                        }
                    )
                }
                TextButton(onClick = { onCoverArtistsChange(emptyList()) }) { Text("Clear") }
            }
        }
        if (showCoverPicker) {
            CoverArtistsDialog(
                picked = coverArtists,
                suggestions = suggestions,
                onConfirm = { artists ->
                    showCoverPicker = false
                    onCoverArtistsChange(artists)
                },
                onDismiss = { showCoverPicker = false }
            )
        }
        when (val state = shownState) {
            OnlineVersionsState.Idle, OnlineVersionsState.Loading -> {
                repeat(3) { SkeletonSongRow() }
            }
            OnlineVersionsState.Failed -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Couldn't search online",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { if (showingCovers) onCoverArtistsChange(coverArtists) else onRetry() }) { Text("Retry") }
                }
            }
            is OnlineVersionsState.Loaded -> {
                if (state.versions.isEmpty()) {
                    Text(
                        text = if (showingCovers) "No covers found by these artists" else "No other versions found online",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                } else {
                    state.versions.forEach { version ->
                        VersionRow(
                            version = version,
                            isPlaying = version.song.id == currentSongId,
                            showArtist = true,
                            onClick = { onVersionClick(version) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(
    version: SongVersion,
    isPlaying: Boolean,
    showArtist: Boolean = false,
    onClick: () -> Unit
) {
    val song = version.song
    val subtitle = buildList {
        if (showArtist && song.artist.isNotBlank()) add(song.artist)
        version.tag?.takeIf { showArtist }?.let { add(it) }
        if (song.album.isNotBlank()) add(song.album)
        if (song.year > 0) add(song.year.toString())
    }.joinToString(" · ")
    val a11yLabel = buildString {
        append(song.title)
        if (version.isOriginal) append(", original")
        if (subtitle.isNotEmpty()) append(", ").append(subtitle)
        if (isPlaying) append(", now playing")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = a11yLabel }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = null,
            shape = MaterialTheme.shapes.small,
            songId = song.id,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (version.isOriginal) {
                Text(
                    text = "Original",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isPlaying) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AudioQualityGrid(
    mimeType: String?,
    bitrate: Int?,
    sampleRate: Int?,
    bitDepth: Int?,
    scanState: AudioScanState,
    onScan: () -> Unit
) {
    val format = mimeTypeToFormat(mimeType).takeIf { it != "-" }
    val isLossy = format != null && format.lowercase(Locale.ROOT) in LossyFormats

    val sampleRateValue = sampleRate?.takeIf { it > 0 }
        ?.let { String.format(Locale.US, "%.1f kHz", it / 1000.0) } ?: UnknownValue
    val bitrateValue = bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" } ?: UnknownValue
    val formatValue = format?.uppercase(Locale.ROOT) ?: UnknownValue
    val bitDepthValue = bitDepth?.takeIf { it > 0 && !isLossy }?.let { "$it-bit" } ?: UnknownValue

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QualityCard(
                label = "Sample rate",
                scanState = scanState,
                onScan = onScan,
                value = sampleRateValue,
                spokenValue = sampleRateValue.replace("kHz", "kilohertz"),
                modifier = Modifier.weight(1f)
            )
            QualityCard(
                label = "Bitrate",
                scanState = scanState,
                onScan = onScan,
                value = bitrateValue,
                spokenValue = bitrateValue.replace("kbps", "kilobits per second"),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QualityCard(
                label = "Format",
                scanState = scanState,
                onScan = onScan,
                value = formatValue,
                modifier = Modifier.weight(1f)
            )
            QualityCard(
                label = "Bit depth",
                value = bitDepthValue,
                helper = if (isLossy) "Lossy" else null,
                // Lossy files have no bit depth, so there is nothing to scan for.
                scanState = scanState,
                onScan = if (isLossy) null else onScan,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QualityCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    spokenValue: String = value,
    helper: String? = null,
    scanState: AudioScanState = AudioScanState.Idle,
    onScan: (() -> Unit)? = null
) {
    val isMissing = value == UnknownValue
    val canScan = isMissing && onScan != null
    val isScanning = canScan && scanState == AudioScanState.Scanning
    val scanHelper = when {
        !canScan -> helper
        isScanning -> "Scanning…"
        scanState is AudioScanState.Done -> "Not found · Tap to retry"
        else -> "Tap to scan"
    }
    val spoken = if (isMissing) "unknown" else spokenValue
    val cardDescription = buildString {
        append(label).append(", ").append(spoken)
        scanHelper?.let { append(", ").append(it) }
    }
    val clickModifier = if (canScan && !isScanning) {
        Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(
                onClickLabel = "Scan this song",
                role = Role.Button,
                onClick = onScan!!
            )
    } else {
        Modifier
    }
    Card(
        modifier = modifier
            .then(clickModifier)
            .clearAndSetSemantics {
                contentDescription = cardDescription
                if (canScan && !isScanning) {
                    onClick(label = "Scan this song") { onScan!!(); true }
                }
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Crossfade(
                targetState = if (isScanning) null else value,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "qualityValue"
            ) { shown ->
                if (shown == null) {
                    // Keeps the card height steady while the scan runs.
                    Box(Modifier.heightIn(min = 32.dp), contentAlignment = Alignment.CenterStart) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        text = shown,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            scanHelper?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (canScan) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** True when the system animator duration scale is 0 (animations turned off). */
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}

/** The main credited artist of a song ("A, B", "A & B", "A feat. B" → "A"). */
private fun coverArtistName(artist: String): String =
    artist.split(Regex(""",|\s&\s|\sfeat\.?\s|\sft\.?\s|\sx\s""", RegexOption.IGNORE_CASE))
        .firstOrNull().orEmpty().trim()

/**
 * Pick the artists to search covers by: tick any found already covering the song, or type a name
 * and add it. Several can be picked; Search looks each one up.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CoverArtistsDialog(
    picked: List<String>,
    suggestions: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(picked) { mutableStateOf(picked) }
    var typed by rememberSaveable { mutableStateOf("") }
    val options = remember(selected, suggestions) {
        (selected + suggestions).distinctBy { it.lowercase(Locale.ROOT) }
    }
    fun addTyped() {
        val name = typed.trim()
        if (name.isEmpty()) return
        if (selected.none { it.equals(name, ignoreCase = true) }) selected = selected + name
        typed = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.PersonSearch, contentDescription = null) },
        title = { Text("Covers by") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Artist name") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { addTyped() }),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { addTyped() }, enabled = typed.isNotBlank()) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add artist")
                    }
                }
                if (options.isEmpty()) {
                    Text(
                        "Type an artist and add them. Artists already covering this song show up here once the online search finishes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        options.forEach { artist ->
                            val isOn = selected.any { it.equals(artist, ignoreCase = true) }
                            FilterChip(
                                selected = isOn,
                                onClick = {
                                    selected = if (isOn) selected.filterNot { it.equals(artist, ignoreCase = true) }
                                    else selected + artist
                                },
                                label = { Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Anything still typed counts too.
                    val name = typed.trim()
                    val final = if (name.isNotEmpty() && selected.none { it.equals(name, ignoreCase = true) }) selected + name else selected
                    onConfirm(final)
                }
            ) { Text(if (selected.isEmpty() && typed.isBlank()) "Show all" else "Search") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
