package com.theveloper.pixelplay.presentation.screens.settings

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.presentation.screens.ActionSettingsItem
import com.theveloper.pixelplay.presentation.screens.SettingsItem
import com.theveloper.pixelplay.presentation.viewmodel.StatsViewModel


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeveloperSettingsContent(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel,
    statsViewModel: StatsViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Hoisted string resources (for use inside lambdas/callbacks)
    val dialogPaletteRegeneratedFormat = stringResource(R.string.settings_dialog_palette_regenerated)
    val dialogPaletteRegenerateFailedFormat = stringResource(R.string.settings_dialog_palette_regenerate_failed)
    val toastRegeneratedPalettesAllFormat = stringResource(R.string.settings_toast_regenerated_palettes_all)
    val toastRegeneratedPalettesPartialFormat = stringResource(R.string.settings_toast_regenerated_palettes_partial)

    val paletteRegenerateTargets by playerViewModel.paletteRegenerationTargets.collectAsStateWithLifecycle()

    // Local State
    var showCrashConfirmDialog by remember { mutableStateOf(false) }
    var showResetSetupConfirm by remember { mutableStateOf(false) }
    var showRegenerateDailyMixDialog by remember { mutableStateOf(false) }
    var showRegenerateStatsDialog by remember { mutableStateOf(false) }
    var showRegenerateAllPalettesDialog by remember { mutableStateOf(false) }
    var showPaletteRegenerateSheet by remember { mutableStateOf(false) }
    var isPaletteRegenerateRunning by remember { mutableStateOf(false) }
    var isPaletteBulkRegenerateRunning by remember { mutableStateOf(false) }
    var paletteBulkCompletedCount by remember { mutableStateOf(0) }
    var paletteBulkTotalCount by remember { mutableStateOf(0) }
    var paletteSongSearchQuery by remember { mutableStateOf("") }
    @Suppress("DEPRECATION")
    val paletteRegenerateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isAnyPaletteRegenerateRunning = isPaletteRegenerateRunning || isPaletteBulkRegenerateRunning
    val filteredPaletteSongs = remember(paletteRegenerateTargets, paletteSongSearchQuery) {
        val query = paletteSongSearchQuery.trim()
        if (query.isBlank()) {
            paletteRegenerateTargets
        } else {
            paletteRegenerateTargets.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                    song.displayArtist.contains(query, ignoreCase = true) ||
                    song.album.contains(query, ignoreCase = true)
            }
        }
    }

    SettingsSubsection(title = stringResource(R.string.settings_experiments_section)) {
        // The Experimental screen is reached from Player & Lyrics > Advanced > Player tweaks.
        SettingsItem(
            settingKey = "dev_test_setup",
            title = stringResource(R.string.settings_test_setup_title),
            subtitle = stringResource(R.string.settings_test_setup_subtitle),
            leadingIcon = { Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.tertiary) },
            // Confirmed first: this used to run on a single tap, while the same action on
            // the old settings root asked. The root row is gone; this is its only home.
            onClick = { showResetSetupConfirm = true }
        )
    }

    SettingsSubsection(title = stringResource(R.string.settings_maintenance_section)) {
        ActionSettingsItem(
            settingKey = "dev_daily_mix",
            title = stringResource(R.string.settings_force_daily_mix_title),
            subtitle = stringResource(R.string.settings_force_daily_mix_subtitle),
            icon = { Icon(painterResource(R.drawable.rounded_instant_mix_24), null, tint = MaterialTheme.colorScheme.secondary) },
            primaryActionLabel = stringResource(R.string.settings_action_regenerate_daily_mix),
            onPrimaryAction = { showRegenerateDailyMixDialog = true }
        )
        ActionSettingsItem(
            settingKey = "dev_stats",
            title = stringResource(R.string.settings_force_stats_title),
            subtitle = stringResource(R.string.settings_force_stats_subtitle),
            icon = { Icon(painterResource(R.drawable.rounded_monitoring_24), null, tint = MaterialTheme.colorScheme.secondary) },
            primaryActionLabel = stringResource(R.string.settings_action_regenerate_stats),
            onPrimaryAction = { showRegenerateStatsDialog = true }
        )
        ActionSettingsItem(
            settingKey = "dev_palette",
            title = stringResource(R.string.settings_force_palette_title),
            subtitle = if (paletteRegenerateTargets.isEmpty()) {
                stringResource(R.string.settings_force_palette_empty)
            } else {
                stringResource(R.string.settings_force_palette_subtitle)
            },
            icon = { Icon(Icons.Outlined.Style, null, tint = MaterialTheme.colorScheme.secondary) },
            primaryActionLabel = if (isPaletteBulkRegenerateRunning) stringResource(R.string.settings_regenerating) else stringResource(R.string.settings_action_regenerate_all),
            onPrimaryAction = { showRegenerateAllPalettesDialog = true },
            secondaryActionLabel = stringResource(R.string.settings_action_choose_song),
            onSecondaryAction = { showPaletteRegenerateSheet = true },
            enabled = paletteRegenerateTargets.isNotEmpty() && !isAnyPaletteRegenerateRunning
        )
    }

    SettingsSubsection(
        title = stringResource(R.string.settings_diagnostics_section),
        addBottomSpace = false
    ) {
        SettingsItem(
            settingKey = "dev_trigger_crash",
            title = stringResource(R.string.settings_trigger_crash_title),
            subtitle = stringResource(R.string.settings_trigger_crash_subtitle),
            leadingIcon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { showCrashConfirmDialog = true }
        )
    }

    SettingsConfirmSheet(
        request = if (!showResetSetupConfirm) {
            null
        } else {
            ConfirmRequest(
                title = stringResource(R.string.settings_dialog_test_setup_title),
                body = stringResource(R.string.settings_dialog_test_setup_body),
                confirmLabel = stringResource(R.string.settings_action_reset),
                onConfirm = { settingsViewModel.resetSetupFlow() }
            )
        },
        onDismiss = { showResetSetupConfirm = false }
    )

    if (showPaletteRegenerateSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!isAnyPaletteRegenerateRunning) {
                    showPaletteRegenerateSheet = false
                    paletteSongSearchQuery = ""
                }
            },
            sheetState = paletteRegenerateSheetState
        ) {
            com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
            PaletteRegenerateSongSheetContent(
                songs = filteredPaletteSongs,
                isRunning = isPaletteRegenerateRunning,
                searchQuery = paletteSongSearchQuery,
                onSearchQueryChange = { paletteSongSearchQuery = it },
                onClearSearch = { paletteSongSearchQuery = "" },
                onSongClick = { song ->
                    if (isAnyPaletteRegenerateRunning) return@PaletteRegenerateSongSheetContent
                    isPaletteRegenerateRunning = true
                    coroutineScope.launch {
                        val success = playerViewModel.forceRegenerateAlbumPaletteForSong(song)
                        isPaletteRegenerateRunning = false
                        if (success) {
                            showPaletteRegenerateSheet = false
                            paletteSongSearchQuery = ""
                            Toast.makeText(
                                context,
                                dialogPaletteRegeneratedFormat.format(song.title),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                dialogPaletteRegenerateFailedFormat.format(song.title),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        }
    }

    if (showRegenerateAllPalettesDialog) {
        AlertDialog(
            icon = {
                Icon(
                    Icons.Outlined.Style,
                    null,
                    tint = if (isPaletteBulkRegenerateRunning) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            },
            title = {
                Text(
                    if (isPaletteBulkRegenerateRunning) {
                        stringResource(R.string.settings_dialog_regenerating_palettes_title)
                    } else {
                        stringResource(R.string.settings_dialog_regenerate_palettes_title)
                    }
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isPaletteBulkRegenerateRunning) {
                            stringResource(R.string.settings_dialog_regenerating_palettes_body, paletteBulkTotalCount)
                        } else {
                            stringResource(R.string.settings_dialog_regenerate_palettes_body, paletteRegenerateTargets.size)
                        }
                    )

                    if (isPaletteBulkRegenerateRunning) {
                        val progress = if (paletteBulkTotalCount > 0) {
                            paletteBulkCompletedCount.toFloat() / paletteBulkTotalCount.toFloat()
                        } else {
                            0f
                        }

                        LinearWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )

                        Text(
                            text = stringResource(R.string.settings_dialog_palette_progress_format, paletteBulkCompletedCount, paletteBulkTotalCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            onDismissRequest = {
                if (!isPaletteBulkRegenerateRunning) {
                    showRegenerateAllPalettesDialog = false
                }
            },
            confirmButton = {
                if (isPaletteBulkRegenerateRunning) {
                    TextButton(
                        onClick = {},
                        enabled = false
                    ) {
                        Text(stringResource(R.string.settings_working_label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    TextButton(
                        onClick = {
                            isPaletteBulkRegenerateRunning = true
                            paletteBulkCompletedCount = 0
                            paletteBulkTotalCount = paletteRegenerateTargets.size

                            coroutineScope.launch {
                                var successCount = 0
                                paletteRegenerateTargets.forEachIndexed { index, song ->
                                    if (playerViewModel.forceRegenerateAlbumPaletteForSong(song)) {
                                        successCount++
                                    }
                                    paletteBulkCompletedCount = index + 1
                                }

                                isPaletteBulkRegenerateRunning = false
                                showRegenerateAllPalettesDialog = false

                                val totalCount = paletteRegenerateTargets.size
                                Toast.makeText(
                                    context,
                                    if (successCount == totalCount) {
                                        toastRegeneratedPalettesAllFormat.format(successCount)
                                    } else {
                                        toastRegeneratedPalettesPartialFormat.format(successCount, totalCount)
                                    },
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.settings_action_regenerate))
                    }
                }
            },
            dismissButton = {
                if (!isPaletteBulkRegenerateRunning) {
                    TextButton(
                        onClick = { showRegenerateAllPalettesDialog = false }
                    ) {
                        Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        )
    }

    if (showRegenerateDailyMixDialog) {
        val toastDailyMixRegenerationStarted = stringResource(R.string.settings_toast_daily_mix_regeneration_started)
        AlertDialog(
            icon = { Icon(painterResource(R.drawable.rounded_instant_mix_24), null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.settings_dialog_regenerate_daily_mix_title)) },
            text = { Text(stringResource(R.string.settings_dialog_regenerate_daily_mix_body)) },
            onDismissRequest = { showRegenerateDailyMixDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRegenerateDailyMixDialog = false
                        playerViewModel.forceUpdateDailyMix()
                        Toast.makeText(context, toastDailyMixRegenerationStarted, Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.settings_action_regenerate), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = { TextButton(onClick = { showRegenerateDailyMixDialog = false }) { Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        )
    }

    if (showRegenerateStatsDialog) {
        val toastStatsRegenerationStarted = stringResource(R.string.settings_toast_stats_regeneration_started)
        AlertDialog(
            icon = { Icon(painterResource(R.drawable.rounded_monitoring_24), null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.settings_dialog_regenerate_stats_title)) },
            text = { Text(stringResource(R.string.settings_dialog_regenerate_stats_body)) },
            onDismissRequest = { showRegenerateStatsDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRegenerateStatsDialog = false
                        statsViewModel.forceRegenerateStats()
                        Toast.makeText(context, toastStatsRegenerationStarted, Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.settings_action_regenerate), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = { TextButton(onClick = { showRegenerateStatsDialog = false }) { Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        )
    }

    if (showCrashConfirmDialog) {
        AlertDialog(
            icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.settings_dialog_trigger_crash_title)) },
            text = { Text(stringResource(R.string.settings_dialog_trigger_crash_body)) },
            onDismissRequest = { showCrashConfirmDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCrashConfirmDialog = false
                        settingsViewModel.triggerTestCrash()
                    }
                ) {
                    Text(
                        stringResource(R.string.settings_action_crash),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCrashConfirmDialog = false }) {
                    Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }
}

@Composable
private fun PaletteRegenerateSongSheetContent(
    songs: List<Song>,
    isRunning: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_force_palette_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.settings_force_palette_partial_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isRunning,
            placeholder = { Text(stringResource(R.string.settings_search_title_artist_album_placeholder)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = onClearSearch,
                        enabled = !isRunning
                    ) {
                        Icon(Icons.Outlined.ClearAll, contentDescription = stringResource(R.string.common_clear_search))
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )

        if (isRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(R.string.settings_palette_regenerating_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (songs.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.settings_search_no_songs_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(songs, key = { it.id }) { song ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isRunning) { onSongClick(song) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.displayArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (song.album.isNotBlank()) {
                                Text(
                                    text = song.album,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
