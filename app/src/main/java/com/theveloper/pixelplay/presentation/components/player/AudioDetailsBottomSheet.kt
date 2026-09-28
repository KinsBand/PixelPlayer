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
                    state = onlineVersionsState,
                    reduceMotion = reduceMotion,
                    onRetry = onRetryOnline,
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
                if (state.versions.size <= 1) {
                    Text(
                        text = "No other versions in your library · searching online below",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineVersionsList(
    currentSongId: String,
    state: OnlineVersionsState,
    reduceMotion: Boolean,
    onRetry: () -> Unit,
    onVersionClick: (SongVersion) -> Unit
) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Online versions",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (state is OnlineVersionsState.Loaded && state.versions.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) { Text(state.versions.size.toString()) }
            }
        }
        when (state) {
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
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
            is OnlineVersionsState.Loaded -> {
                if (state.versions.isEmpty()) {
                    Text(
                        text = "No other versions found online",
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
