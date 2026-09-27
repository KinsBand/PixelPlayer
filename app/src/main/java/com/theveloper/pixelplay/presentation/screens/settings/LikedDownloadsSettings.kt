package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.youtube.DownloadCoordinator
import com.theveloper.pixelplay.presentation.screens.SettingsItem
import com.theveloper.pixelplay.presentation.viewmodel.PlayerDownloadViewModel
import kotlinx.coroutines.launch

/**
 * Settings → Library → Downloads. "Download all liked songs" asks for approval once, here, and
 * only when the user taps it — nothing about bulk downloading ever pops up anywhere else.
 * Approved downloads are collected in the "Liked songs (downloaded)" playlist.
 */
@Composable
internal fun LikedSongsDownloadSection(
    viewModel: PlayerDownloadViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val bulk by viewModel.bulkState.collectAsStateWithLifecycle()
    var pendingCount by remember { mutableIntStateOf(-1) }
    var downloadedCount by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }

    // Refresh the counts on entry and whenever a bulk run finishes.
    LaunchedEffect(bulk?.running) {
        pendingCount = viewModel.likedSongsToDownloadCount()
        downloadedCount = viewModel.likedSongsDownloadedCount()
    }

    val context = LocalContext.current
    val running = bulk?.running == true
    val subtitle = when {
        running -> bulk!!.let {
            stringResource(R.string.settings_download_liked_progress, it.completed + it.failed, it.total) +
                (if (it.failed > 0) stringResource(R.string.settings_download_liked_failed_suffix, it.failed) else "") +
                stringResource(R.string.settings_download_liked_tap_to_stop)
        }
        pendingCount < 0 -> stringResource(R.string.settings_download_liked_checking)
        pendingCount == 0 && downloadedCount > 0 ->
            stringResource(R.string.settings_download_liked_all_done, downloadedCount)
        pendingCount == 0 -> stringResource(R.string.settings_download_liked_none)
        else -> stringResource(
            R.string.settings_download_liked_pending,
            pendingCount,
            DownloadCoordinator.LIKED_DOWNLOADS_PLAYLIST_NAME
        )
    }

    SettingsSubsection(title = stringResource(R.string.settings_downloads_section)) {
        SettingsItem(
            settingKey = "download_all_liked_songs",
            title = stringResource(R.string.settings_download_liked_title),
            subtitle = subtitle,
            leadingIcon = { Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = {
                if (running) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            onClick = {
                if (running) {
                    confirm = ConfirmRequest(
                        title = context.getString(R.string.settings_download_liked_stop_title),
                        body = context.getString(R.string.settings_download_liked_stop_body),
                        confirmLabel = context.getString(R.string.settings_download_liked_stop_action),
                        severity = ConfirmRequest.Severity.Warning,
                        onConfirm = { viewModel.cancelBulk() }
                    )
                    return@SettingsItem
                }
                scope.launch {
                    val count = viewModel.likedSongsToDownloadCount()
                    pendingCount = count
                    confirm = if (count == 0) null else ConfirmRequest(
                        title = context.getString(R.string.settings_download_liked_confirm_title, count),
                        body = context.getString(
                            R.string.settings_download_liked_confirm_body,
                            DownloadCoordinator.LIKED_DOWNLOADS_PLAYLIST_NAME
                        ),
                        confirmLabel = context.getString(R.string.settings_download_liked_confirm_action),
                        severity = ConfirmRequest.Severity.Warning,
                        onConfirm = { viewModel.downloadAllLiked() }
                    )
                }
            }
        )
    }

    SettingsConfirmSheet(request = confirm, onDismiss = { confirm = null })
}
