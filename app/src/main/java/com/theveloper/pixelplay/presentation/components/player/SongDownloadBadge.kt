package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.youtube.DownloadProgress
import com.theveloper.pixelplay.presentation.viewmodel.PlayerDownloadViewModel

private enum class BadgeState { DOWNLOADED, DOWNLOADING, CAN_DOWNLOAD, NONE }

/**
 * The one place the full player shows whether the current song is saved on the device:
 * a tick when it is (downloaded online song, or a file already on the phone), a download
 * button when it can be downloaded, and a progress ring while it downloads.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    val outer = if (boxed) {
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .then(if (state == BadgeState.CAN_DOWNLOAD) Modifier.clickable { viewModel.download(song) } else Modifier)
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

            BadgeState.CAN_DOWNLOAD -> TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text("Download") } },
                state = rememberTooltipState()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Download song",
                    tint = tint,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { viewModel.download(song) }
                        .padding(2.dp)
                )
            }

            BadgeState.NONE -> Unit
        }
    }
    }
}
