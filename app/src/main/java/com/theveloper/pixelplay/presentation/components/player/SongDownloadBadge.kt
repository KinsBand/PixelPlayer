package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.youtube.DownloadOption
import com.theveloper.pixelplay.data.youtube.DownloadProgress
import com.theveloper.pixelplay.data.youtube.DownloadQuality
import com.theveloper.pixelplay.presentation.viewmodel.PlayerDownloadViewModel
import java.util.Locale

private enum class BadgeState { DOWNLOADED, DOWNLOADING, CAN_DOWNLOAD, NONE }

/**
 * The one place the full player shows whether the current song is saved on the device:
 * a tick when it is (downloaded online song, or a file already on the phone), a download
 * button when it can be downloaded, and a progress ring while it downloads.
 *
 * Tapping download opens a small quality menu right under the button (High / Medium / Low
 * with size and time); a long press downloads High straight away.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SongDownloadBadge(
    song: Song,
    tint: Color,
    modifier: Modifier = Modifier,
    /** Draw as a 40 dp rounded-square button (full-player top bar, next to Options). */
    boxed: Boolean = false,
    containerColor: Color = Color.Transparent,
    viewModel: PlayerDownloadViewModel = hiltViewModel(),
) {
    val downloadedIds by viewModel.downloadedIds.collectAsStateWithLifecycle()
    val progressMap by viewModel.progress.collectAsStateWithLifecycle()
    val isOnline = remember(song.id, song.youtubeId, song.contentUriString) { viewModel.isOnlineSong(song) }
    val isOnDevice = !isOnline && (song.path.isNotBlank() ||
        song.contentUriString.startsWith("content://") || song.contentUriString.startsWith("file://"))
    val progress = progressMap[song.id]
    val state = when {
        isOnline && (song.isDownloaded || song.id in downloadedIds || progress is DownloadProgress.Completed) -> BadgeState.DOWNLOADED
        isOnline && (progress is DownloadProgress.Resolving || progress is DownloadProgress.Downloading ||
            progress is DownloadProgress.Tagging || progress is DownloadProgress.Scanning) -> BadgeState.DOWNLOADING
        isOnline -> BadgeState.CAN_DOWNLOAD
        isOnDevice -> BadgeState.DOWNLOADED
        else -> BadgeState.NONE
    }
    if (state == BadgeState.NONE) return

    val haptics = LocalHapticFeedback.current
    var menuOpen by remember(song.id) { mutableStateOf(false) }
    val openMenu = { menuOpen = true }
    val quickDownload = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.download(song, DownloadQuality.HIGH)
    }

    val outer = if (boxed) {
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .then(
                if (state == BadgeState.CAN_DOWNLOAD) {
                    Modifier.combinedClickable(onClick = openMenu, onLongClick = quickDownload, onLongClickLabel = "Download in high quality")
                } else Modifier
            )
    } else modifier

    Box(modifier = outer, contentAlignment = Alignment.Center) {
    AnimatedContent(
        targetState = state,
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.7f)) togetherWith fadeOut() },
        label = "SongDownloadBadge"
    ) { target ->
        when (target) {
            BadgeState.DOWNLOADED -> TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(if (isOnline) "Downloaded" else "On this device") } },
                state = rememberTooltipState()
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }

            BadgeState.DOWNLOADING -> Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                val percent = (progress as? DownloadProgress.Downloading)?.percent
                if (percent != null && percent > 0) {
                    CircularProgressIndicator(
                        progress = { percent / 100f },
                        color = tint,
                        trackColor = tint.copy(alpha = 0.25f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                }
            }

            BadgeState.CAN_DOWNLOAD -> Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = "Download song",
                tint = tint,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .then(
                        if (boxed) Modifier
                        else Modifier.combinedClickable(onClick = openMenu, onLongClick = quickDownload)
                    )
                    .padding(2.dp)
            )

            BadgeState.NONE -> Unit
        }
    }
        DownloadQualityMenu(
            expanded = menuOpen && state == BadgeState.CAN_DOWNLOAD,
            song = song,
            viewModel = viewModel,
            onDismiss = { menuOpen = false },
            onPick = { quality ->
                menuOpen = false
                viewModel.download(song, quality)
            }
        )
    }
}

/**
 * Drops down from the download button (top-right of the player, where the thumb already is).
 * Sizes are exact when YouTube reports them; times come from the measured download speed.
 */
@Composable
private fun DownloadQualityMenu(
    expanded: Boolean,
    song: Song,
    viewModel: PlayerDownloadViewModel,
    onDismiss: () -> Unit,
    onPick: (DownloadQuality) -> Unit,
) {
    var options by remember(song.id) { mutableStateOf<List<DownloadOption>?>(null) }
    var failed by remember(song.id) { mutableStateOf(false) }
    LaunchedEffect(expanded, song.id) {
        if (!expanded || options != null) return@LaunchedEffect
        failed = false
        options = runCatching { viewModel.downloadOptions(song) }
            .onFailure { failed = true }
            .getOrNull()
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 8.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.widthIn(min = 260.dp)
    ) {
        Text(
            "Download quality",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val loaded = options
        when {
            loaded == null && !failed -> Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Checking sizes…", style = MaterialTheme.typography.bodyMedium)
            }
            loaded.isNullOrEmpty() -> {
                // Sizes unavailable: still offer the three choices.
                DownloadQuality.entries.forEach { quality ->
                    QualityRow(quality, detail = null, onClick = { onPick(quality) })
                }
            }
            else -> loaded.forEach { option ->
                val seconds = viewModel.estimateSeconds(option.sizeBytes)
                QualityRow(
                    quality = option.quality,
                    detail = "${option.codec} · ${option.bitrateKbps} kbps",
                    size = formatMegabytes(option.sizeBytes),
                    time = formatSeconds(seconds),
                    onClick = { onPick(option.quality) }
                )
            }
        }
    }
}

@Composable
private fun QualityRow(
    quality: DownloadQuality,
    detail: String?,
    size: String? = null,
    time: String? = null,
    onClick: () -> Unit,
) {
    val icon: ImageVector = when (quality) {
        DownloadQuality.HIGH -> Icons.Rounded.HighQuality
        DownloadQuality.MEDIUM -> Icons.Rounded.GraphicEq
        DownloadQuality.LOW -> Icons.Rounded.SdCard
    }
    val trailing: (@Composable () -> Unit)? = if (size != null) {
        {
            Column(horizontalAlignment = Alignment.End) {
                Text(size, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (time != null) {
                    Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else null
    DropdownMenuItem(
        leadingIcon = {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(
                    if (quality == DownloadQuality.HIGH) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, null, Modifier.size(20.dp),
                    tint = if (quality == DownloadQuality.HIGH) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(quality.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        trailingIcon = trailing,
        onClick = onClick
    )
}

private fun formatMegabytes(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)

private fun formatSeconds(seconds: Long): String = when {
    seconds < 60 -> "~${seconds}s"
    else -> "~${seconds / 60}m ${seconds % 60}s"
}
