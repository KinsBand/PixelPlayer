package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.presentation.components.LyricsDisplayPrefKeys
import com.theveloper.pixelplay.presentation.components.LyricsFont
import com.theveloper.pixelplay.presentation.components.LyricsTextSize
import com.theveloper.pixelplay.presentation.components.editLyricsDisplayPrefs
import com.theveloper.pixelplay.presentation.components.rememberLyricsDisplayPrefs
import com.theveloper.pixelplay.presentation.components.IMMERSIVE_TIMEOUT_OFF
import com.theveloper.pixelplay.presentation.components.subcomps.SkillTreeBranch
import kotlinx.coroutines.launch
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.screens.SettingsItem
import com.theveloper.pixelplay.presentation.screens.SwitchSettingItem
import com.theveloper.pixelplay.presentation.screens.ThemeSelectorItem
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsUiState
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

@Composable
internal fun LyricsSettingsContent(
    playerViewModel: PlayerViewModel?,
    settingsViewModel: SettingsViewModel,
    uiState: SettingsUiState,
    navController: NavController? = null,
    onResetAllLyrics: () -> Unit = { playerViewModel?.resetAllLyrics() }
) {
    // Local State
    var showClearLyricsDialog by remember { mutableStateOf(false) }

    // Cover lyrics, font and size live in LyricsDisplayPrefs — the same source the player,
    // the cover overlay and the lyrics sheet read — so a change here applies everywhere.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lyricsDisplayPrefs by rememberLyricsDisplayPrefs()

    SettingsSubsection(title = stringResource(R.string.settings_lyrics_sources_section)) {
        ThemeSelectorItem(
            settingKey = "lyrics_priority",
            label = stringResource(R.string.settings_lyrics_source_priority_title),
            description = stringResource(R.string.settings_lyrics_source_priority_subtitle),
            options = mapOf(
                LyricsSourcePreference.EMBEDDED_FIRST.name to stringResource(R.string.settings_lyrics_embedded_first),
                LyricsSourcePreference.API_FIRST.name to stringResource(R.string.settings_lyrics_online_first),
                LyricsSourcePreference.LOCAL_FIRST.name to stringResource(R.string.settings_lyrics_local_first)
            ),
            selectedKey = uiState.lyricsSourcePreference.name,
            onSelectionChanged = { key ->
                settingsViewModel.setLyricsSourcePreference(
                    LyricsSourcePreference.fromName(key)
                )
            },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_lyrics_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SettingsItem(
            settingKey = "reset_lyrics",
            title = stringResource(R.string.settings_reset_imported_lyrics_title),
            subtitle = stringResource(R.string.settings_reset_imported_lyrics_subtitle),
            leadingIcon = { Icon(Icons.Outlined.ClearAll, null, tint = MaterialTheme.colorScheme.secondary) },
            onClick = { showClearLyricsDialog = true }
        )
        SwitchSettingItem(
            settingKey = "auto_scan_lrc",
            title = stringResource(R.string.settings_auto_scan_lrc_title),
            subtitle = stringResource(R.string.settings_auto_scan_lrc_subtitle),
            checked = uiState.autoScanLrcFiles,
            onCheckedChange = { settingsViewModel.setAutoScanLrcFiles(it) },
            leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            settingKey = "lyrics_integration",
            title = stringResource(R.string.settings_lyrics_lrclib_title),
            subtitle = stringResource(R.string.settings_lyrics_lrclib_subtitle),
            checked = uiState.lyricsIntegrationEnabled,
            onCheckedChange = { settingsViewModel.setLyricsIntegrationEnabled(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_music_note_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    SettingsSubsection(
        title = stringResource(R.string.settings_lyrics_display_section),
        addBottomSpace = navController != null
    ) {
        SwitchSettingItem(
            settingKey = "cover_lyrics",
            title = stringResource(R.string.settings_cover_lyrics_title),
            subtitle = stringResource(R.string.settings_cover_lyrics_subtitle),
            checked = lyricsDisplayPrefs.coverLyricsEnabled,
            onCheckedChange = { enabled ->
                scope.launch {
                    context.editLyricsDisplayPrefs { it[LyricsDisplayPrefKeys.COVER_LYRICS_ENABLED] = enabled }
                }
            },
            leadingIcon = { Icon(Icons.Rounded.Album, null, tint = MaterialTheme.colorScheme.secondary) }
        )

        ThemeSelectorItem(
            settingKey = "lyrics_font",
            label = stringResource(R.string.settings_lyrics_font_title),
            description = stringResource(R.string.settings_lyrics_font_subtitle),
            options = mapOf(
                LyricsFont.SYSTEM.key to stringResource(R.string.settings_lyrics_font_system),
                LyricsFont.GOOGLE_SANS_ROUNDED.key to stringResource(R.string.settings_lyrics_font_google_sans_rounded),
                LyricsFont.GOOGLE_SANS_FLEX.key to stringResource(R.string.settings_lyrics_font_google_sans_flex),
                LyricsFont.ROBOTO_FLEX.key to stringResource(R.string.settings_lyrics_font_roboto_flex),
                LyricsFont.MONTSERRAT.key to stringResource(R.string.settings_lyrics_font_montserrat)
            ),
            selectedKey = lyricsDisplayPrefs.font.key,
            onSelectionChanged = { key ->
                scope.launch {
                    context.editLyricsDisplayPrefs { it[LyricsDisplayPrefKeys.FONT] = LyricsFont.fromKey(key).key }
                }
            },
            leadingIcon = { Icon(Icons.Rounded.TextFields, null, tint = MaterialTheme.colorScheme.secondary) }
        )

        ThemeSelectorItem(
            settingKey = "lyrics_text_size",
            label = stringResource(R.string.settings_lyrics_size_title),
            description = stringResource(R.string.settings_lyrics_size_subtitle),
            options = mapOf(
                LyricsTextSize.SMALL.key to stringResource(R.string.settings_lyrics_size_small),
                LyricsTextSize.MEDIUM.key to stringResource(R.string.settings_lyrics_size_medium),
                LyricsTextSize.LARGE.key to stringResource(R.string.settings_lyrics_size_large)
            ),
            selectedKey = lyricsDisplayPrefs.textSize.key,
            onSelectionChanged = { key ->
                scope.launch {
                    context.editLyricsDisplayPrefs { it[LyricsDisplayPrefKeys.TEXT_SIZE] = LyricsTextSize.fromKey(key).key }
                }
            },
            leadingIcon = { Icon(Icons.Rounded.FormatSize, null, tint = MaterialTheme.colorScheme.secondary) }
        )

        SwitchSettingItem(
            settingKey = "immersive_lyrics",
            title = stringResource(R.string.settings_immersive_lyrics_title),
            subtitle = stringResource(R.string.settings_immersive_lyrics_subtitle),
            checked = uiState.immersiveLyricsEnabled,
            onCheckedChange = { settingsViewModel.setImmersiveLyricsEnabled(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_lyrics_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )

        // Settings that only exist while immersive is on hang off it like a skill tree.
        if (uiState.immersiveLyricsEnabled) {
            SkillTreeBranch(depth = 1, isLast = false, lineColor = MaterialTheme.colorScheme.primary) {
                ThemeSelectorItem(
                    settingKey = "auto_hide_delay",
                    label = stringResource(R.string.settings_auto_hide_delay_title),
                    description = if (uiState.immersiveLyricsTimeout <= IMMERSIVE_TIMEOUT_OFF) {
                        "Off: swipe the controls down to hide them and up to bring them back"
                    } else {
                        stringResource(R.string.settings_auto_hide_delay_subtitle)
                    },
                    options = mapOf(
                        "0" to "Off (manual)",
                        "3000" to stringResource(R.string.settings_auto_hide_delay_3s),
                        "4000" to stringResource(R.string.settings_auto_hide_delay_4s),
                        "5000" to stringResource(R.string.settings_auto_hide_delay_5s),
                        "6000" to stringResource(R.string.settings_auto_hide_delay_6s)
                    ),
                    selectedKey = uiState.immersiveLyricsTimeout.toString(),
                    onSelectionChanged = { settingsViewModel.setImmersiveLyricsTimeout(it.toLong()) },
                    leadingIcon = { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.secondary) }
                )
            }
            SkillTreeBranch(depth = 1, isLast = true, lineColor = MaterialTheme.colorScheme.primary) {
                SwitchSettingItem(
                    settingKey = "lyrics_split_face",
                    title = stringResource(R.string.settings_lyrics_split_face_title),
                    subtitle = stringResource(R.string.settings_lyrics_split_face_subtitle),
                    checked = lyricsDisplayPrefs.splitFaceView,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            context.editLyricsDisplayPrefs { it[LyricsDisplayPrefKeys.SPLIT_FACE_VIEW] = enabled }
                        }
                    },
                    leadingIcon = { Icon(Icons.Rounded.ScreenRotation, null, tint = MaterialTheme.colorScheme.secondary) }
                )
            }
        }

        SwitchSettingItem(
            settingKey = "lyrics_song_structure",
            title = stringResource(R.string.settings_lyrics_song_structure_title),
            subtitle = stringResource(R.string.settings_lyrics_song_structure_subtitle),
            checked = lyricsDisplayPrefs.showSongStructure,
            onCheckedChange = { enabled ->
                scope.launch {
                    context.editLyricsDisplayPrefs { it[LyricsDisplayPrefKeys.SHOW_SONG_STRUCTURE] = enabled }
                }
            },
            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Rounded.ViewAgenda, null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    // The experimental player screen used to be linked from both here and Developer.
    // This is now its only entry point, at the bottom of Player & Lyrics.
    if (navController != null) {
        SettingsSubsection(
            title = stringResource(R.string.settings_advanced_section),
            addBottomSpace = false
        ) {
            SettingsItem(
                settingKey = "lyrics_experimental",
                title = stringResource(R.string.settings_player_tweaks_title),
                subtitle = stringResource(R.string.settings_player_tweaks_subtitle),
                onClick = { navController.navigate(Screen.Experimental.route) },
                leadingIcon = { Icon(Icons.Rounded.AutoAwesomeMotion, null, tint = MaterialTheme.colorScheme.secondary) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            )
        }
    }

    SettingsConfirmSheet(
        request = if (!showClearLyricsDialog) {
            null
        } else {
            ConfirmRequest(
                title = stringResource(R.string.settings_dialog_reset_imported_lyrics_title),
                body = stringResource(R.string.settings_dialog_reset_imported_lyrics_body),
                confirmLabel = stringResource(R.string.common_confirm),
                onConfirm = onResetAllLyrics
            )
        },
        onDismiss = { showClearLyricsDialog = false }
    )
}

