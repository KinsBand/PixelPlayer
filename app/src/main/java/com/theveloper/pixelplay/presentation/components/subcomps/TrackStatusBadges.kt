package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.DownloadState
import com.theveloper.pixelplay.data.model.TrackSource

/**
 * Status glyphs shown before the artist line of a song row.
 *
 * Rules:
 *  - Songs that live on this device (local files or finished downloads) carry no storage
 *    indicator at all — a clean title/artist is the default.
 *  - Only transient download states (queued/downloading, failed) get a storage icon.
 *  - Cloud (not downloaded) + liked collapses into one condensed cloud-with-heart badge
 *    instead of two icons side by side.
 */
@Composable
fun TrackStatusBadges(
    isFavorite: Boolean,
    source: TrackSource,
    downloadState: DownloadState,
    modifier: Modifier = Modifier,
    iconSize: Dp = 14.dp,
    spacing: Dp = 4.dp
) {
    val isOnDevice = source == TrackSource.LOCAL || downloadState == DownloadState.DOWNLOADED
    val isTransferring = downloadState == DownloadState.DOWNLOADING || downloadState == DownloadState.QUEUED
    val isFailed = downloadState == DownloadState.FAILED
    val isCloudOnly = !isOnDevice && !isTransferring && !isFailed

    // Nothing to show: on-device and not liked.
    if (!isFavorite && !isTransferring && !isFailed && !isCloudOnly) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            isCloudOnly && isFavorite -> {
                CloudLikedBadge(size = iconSize + 2.dp)
                Spacer(modifier = Modifier.width(spacing))
            }
            isCloudOnly -> {
                Icon(
                    imageVector = Icons.Rounded.Cloud,
                    contentDescription = "Cloud Song",
                    tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(spacing))
            }
            else -> {
                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(iconSize)
                    )
                    Spacer(modifier = Modifier.width(spacing))
                }
                if (isTransferring) {
                    Icon(
                        imageVector = Icons.Rounded.Downloading,
                        contentDescription = "Downloading",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(iconSize)
                    )
                    Spacer(modifier = Modifier.width(spacing))
                } else if (isFailed) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = "Download Failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(iconSize)
                    )
                    Spacer(modifier = Modifier.width(spacing))
                }
            }
        }
    }
}

/** Online (cloud) and liked as one glyph: a heart of stacked clouds around the liked heart. */
@Composable
fun CloudLikedBadge(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    CloudHeartIcon(
        modifier = modifier,
        size = size,
        heartColor = MaterialTheme.colorScheme.primary,
        cloudColor = MaterialTheme.colorScheme.tertiary,
        contentDescription = "Cloud & Liked"
    )
}
