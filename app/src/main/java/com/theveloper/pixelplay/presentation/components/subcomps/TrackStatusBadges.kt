package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.DownloadState
import com.theveloper.pixelplay.data.model.TrackSource

@Composable
fun TrackStatusBadges(
    isFavorite: Boolean,
    source: TrackSource,
    downloadState: DownloadState,
    modifier: Modifier = Modifier,
    iconSize: Dp = 14.dp,
    spacing: Dp = 4.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFavorite) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Favorite",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.width(spacing))
        }

        when {
            downloadState == DownloadState.DOWNLOADED -> {
                Icon(
                    imageVector = Icons.Rounded.DownloadDone,
                    contentDescription = "Downloaded Offline",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(spacing))
            }
            downloadState == DownloadState.DOWNLOADING || downloadState == DownloadState.QUEUED -> {
                Icon(
                    imageVector = Icons.Rounded.Downloading,
                    contentDescription = "Downloading",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(spacing))
            }
            downloadState == DownloadState.FAILED -> {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = "Download Failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(spacing))
            }
            source == TrackSource.LOCAL -> {
                Icon(
                    imageVector = Icons.Rounded.PhoneAndroid,
                    contentDescription = "Local File",
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(spacing))
            }
            else -> {
                Icon(
                    imageVector = Icons.Rounded.Cloud,
                    contentDescription = "Cloud Song",
                    tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                    modifier = Modifier.size(iconSize)
                )
                Spacer(modifier = Modifier.width(spacing))
            }
        }
    }
}
