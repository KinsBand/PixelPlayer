package com.theveloper.pixelplay.presentation.screens.settings

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.presentation.screens.RefreshLibraryItem
import com.theveloper.pixelplay.presentation.screens.SettingsItem
import com.theveloper.pixelplay.presentation.screens.SliderSettingsItem
import com.theveloper.pixelplay.presentation.viewmodel.SettingsUiState


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun LibrarySettingsContent(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel,
    uiState: SettingsUiState,
    onShowExplorerSheet: () -> Unit
) {
    val context = LocalContext.current

    // Hoisted string resources (for use inside lambdas/callbacks)
    val toastLibrarySyncFinished = stringResource(R.string.settings_toast_library_sync_finished)
    val syncFullRescanLabel = stringResource(R.string.settings_label_sync_full_rescan)
    val toastFullRescanStarted = stringResource(R.string.settings_toast_full_rescan_started)
    val syncIndicatorRebuilding = stringResource(R.string.settings_label_sync_rebuilding)
    val toastRebuildingDatabase = stringResource(R.string.settings_toast_rebuilding_database)

    val isSyncing by settingsViewModel.isSyncing.collectAsStateWithLifecycle()
    val syncProgress by settingsViewModel.syncProgress.collectAsStateWithLifecycle()

    // Local State
    var refreshRequested by remember { mutableStateOf(false) }
    var syncRequestObservedRunning by remember { mutableStateOf(false) }
    var syncIndicatorLabel by remember { mutableStateOf<String?>(null) }
    var showRebuildDatabaseWarning by remember { mutableStateOf(false) }
    var minSongDurationDraft by remember(uiState.minSongDuration) {
        mutableStateOf(uiState.minSongDuration.toFloat())
    }
    var minTracksPerAlbumDraft by remember(uiState.minTracksPerAlbum) {
        mutableStateOf(uiState.minTracksPerAlbum.toFloat())
    }
    var albumArtCacheLimitDraft by remember(uiState.albumArtCacheLimitMb) {
        mutableStateOf(uiState.albumArtCacheLimitMb.toFloat())
    }

    LaunchedEffect(isSyncing, refreshRequested) {
        if (!refreshRequested) return@LaunchedEffect

        if (isSyncing) {
            syncRequestObservedRunning = true
        } else if (syncRequestObservedRunning) {
            Toast.makeText(context, toastLibrarySyncFinished, Toast.LENGTH_SHORT).show()
            refreshRequested = false
            syncRequestObservedRunning = false
            syncIndicatorLabel = null
        }
    }

    SettingsSubsection(title = stringResource(R.string.settings_library_structure_section)) {
        SettingsItem(
            settingKey = "excluded_directories",
            title = stringResource(R.string.settings_excluded_directories_title),
            subtitle = stringResource(R.string.settings_excluded_directories_subtitle),
            leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.settings_cd_open), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = {
                onShowExplorerSheet()
                settingsViewModel.openExplorer()
            }
        )
        SettingsItem(
            settingKey = "artists",
            title = stringResource(R.string.settings_artists_title),
            subtitle = stringResource(R.string.settings_artists_subtitle),
            leadingIcon = { Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.settings_cd_open), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { navController.navigateSafely(Screen.ArtistSettings.route) }
        )
    }

    SettingsSubsection(title = stringResource(R.string.settings_filtering_section)) {
        SliderSettingsItem(
            settingKey = "min_song_duration",
            label = stringResource(R.string.settings_min_song_duration),
            value = minSongDurationDraft,
            valueRange = 0f..120000f,
            steps = 23, // 0, 5, 10, 15, ... 120 seconds (24 positions, 23 steps)
            onValueChange = { minSongDurationDraft = it },
            onValueChangeFinished = {
                val selectedDuration = minSongDurationDraft.toInt()
                if (selectedDuration != uiState.minSongDuration) {
                    settingsViewModel.setMinSongDuration(selectedDuration)
                }
            },
            valueText = { value -> "${(value / 1000).toInt()}s" },
            presets = listOf(0f, 30000f, 60000f, 120000f)
        )
        SliderSettingsItem(
            settingKey = "min_tracks_per_album",
            label = stringResource(R.string.settings_min_tracks_per_album),
            value = minTracksPerAlbumDraft,
            valueRange = 1f..5f,
            steps = 3, // 1, 2, 3, 4, 5
            onValueChange = { minTracksPerAlbumDraft = it },
            onValueChangeFinished = {
                val selectedTracks = minTracksPerAlbumDraft.toInt()
                if (selectedTracks != uiState.minTracksPerAlbum) {
                    settingsViewModel.setMinTracksPerAlbum(selectedTracks)
                }
            },
            valueText = { value -> "${value.toInt()}" },
            presets = listOf(1f, 2f, 3f, 5f)
        )
        SliderSettingsItem(
            settingKey = "cache_limit",
            label = stringResource(R.string.settings_album_art_cache_limit),
            value = albumArtCacheLimitDraft,
            valueRange = 50f..1500f,
            steps = 28, // 50, 100, 150, ... 1500 (30 stops)
            onValueChange = { albumArtCacheLimitDraft = it },
            onValueChangeFinished = {
                val selectedLimit = albumArtCacheLimitDraft.toInt()
                if (selectedLimit != uiState.albumArtCacheLimitMb) {
                    settingsViewModel.setAlbumArtCacheLimitMb(selectedLimit)
                }
            },
            valueText = { value -> "${value.toInt()} MB" },
            presets = listOf(100f, 250f, 500f, 1000f)
        )
    }

    LikedSongsDownloadSection()

    SettingsSubsection(title = stringResource(R.string.settings_sync_scanning_section)) {
        RefreshLibraryItem(
            settingKey = "refresh_library",
            isSyncing = isSyncing,
            syncProgress = syncProgress,
            activeOperationLabel = if (isSyncing) syncIndicatorLabel else null,
            onFullSync = {
                if (isSyncing) return@RefreshLibraryItem
                refreshRequested = true
                syncRequestObservedRunning = false
                syncIndicatorLabel = syncFullRescanLabel
                Toast.makeText(context, toastFullRescanStarted, Toast.LENGTH_SHORT).show()
                settingsViewModel.fullSyncLibrary()
            },
            onRebuild = {
                if (isSyncing) return@RefreshLibraryItem
                showRebuildDatabaseWarning = true
            }
        )
    }

    SettingsConfirmSheet(
        request = if (!showRebuildDatabaseWarning) {
            null
        } else {
            ConfirmRequest(
                title = stringResource(R.string.settings_dialog_rebuild_database_title),
                body = stringResource(R.string.settings_dialog_rebuild_database_body),
                confirmLabel = stringResource(R.string.settings_action_rebuild),
                onConfirm = {
                    refreshRequested = true
                    syncRequestObservedRunning = false
                    syncIndicatorLabel = syncIndicatorRebuilding
                    Toast.makeText(context, toastRebuildingDatabase, Toast.LENGTH_SHORT).show()
                    settingsViewModel.rebuildDatabase()
                }
            )
        },
        onDismiss = { showRebuildDatabaseWarning = false }
    )
}
