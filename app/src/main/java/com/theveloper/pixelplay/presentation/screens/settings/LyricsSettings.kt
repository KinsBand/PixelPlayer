package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.SwipeDown
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Rectangle
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Wifi
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.theveloper.pixelplay.utils.rememberSystemBarsPrefs
import com.theveloper.pixelplay.utils.setHideBarsInLyrics
import kotlin.math.roundToInt
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.screens.SettingsItem
import com.theveloper.pixelplay.presentation.screens.SliderSettingsItem
import com.theveloper.pixelplay.presentation.screens.SwitchSettingItem
import com.theveloper.pixelplay.presentation.screens.ActionSettingsItem
import com.theveloper.pixelplay.presentation.screens.SettingsBranch
import com.theveloper.pixelplay.presentation.screens.rememberSettingsBranchExpanded
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
    val lyricsAutoSyncEnabled by settingsViewModel.lyricsAutoSyncEnabled.collectAsStateWithLifecycle()
    val lyricsWordAlignEnabled by settingsViewModel.lyricsWordAlignEnabled.collectAsStateWithLifecycle()
    val lyricsWordAlignMobileData by settingsViewModel.lyricsWordAlignMobileData.collectAsStateWithLifecycle()

    SettingsSubsection(
        title = stringResource(R.string.settings_lyrics_display_section)
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
            leadingIcon = { Icon(Icons.Rounded.Album, null, tint = MaterialTheme.colorScheme.secondary) },
            preview = { on -> CoverLyricsPreview(on) }
        )

        var immersiveBranchOpen by rememberSettingsBranchExpanded("auto_hide_delay", "lyrics_split_face")
        SwitchSettingItem(
            settingKey = "immersive_lyrics",
            title = stringResource(R.string.settings_immersive_lyrics_title),
            subtitle = stringResource(R.string.settings_immersive_lyrics_subtitle),
            checked = uiState.immersiveLyricsEnabled,
            onCheckedChange = { if (it) immersiveBranchOpen = true; settingsViewModel.setImmersiveLyricsEnabled(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_lyrics_24), null, tint = MaterialTheme.colorScheme.secondary) },
            branchExpanded = if (uiState.immersiveLyricsEnabled) immersiveBranchOpen else null,
            onBranchToggle = { immersiveBranchOpen = !immersiveBranchOpen },
            preview = { on -> ImmersiveLyricsPreview(on) }
        )
        // Settings that only exist while immersive is on hang off it like a skill tree.
        SettingsBranch(visible = uiState.immersiveLyricsEnabled && immersiveBranchOpen) {
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

        // Settings → Lyrics → Animation style (its own screen with a live preview).
        SettingsItem(
            settingKey = "lyrics_animation_style",
            title = stringResource(R.string.settings_lyrics_animation_style_title),
            subtitle = stringResource(R.string.settings_lyrics_animation_style_subtitle),
            leadingIcon = { Icon(Icons.Rounded.AutoAwesomeMotion, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingContent = {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = lyricsDisplayPrefs.animationStyle.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp).size(16.dp)
                    )
                }
            },
            onClick = { navController?.navigate(Screen.LyricsAnimationStyle.route) }
        )

        // Full screen just for lyrics: hides the status and gesture bars while lyrics are open.
        val systemBarsPrefs = rememberSystemBarsPrefs()
        SwitchSettingItem(
            settingKey = "hide_bars_in_lyrics",
            title = stringResource(R.string.settings_hide_bars_in_lyrics_title),
            subtitle = stringResource(R.string.settings_hide_bars_in_lyrics_subtitle),
            checked = systemBarsPrefs.hideBarsInLyrics,
            onCheckedChange = { hide ->
                scope.launch { context.setHideBarsInLyrics(hide) }
            },
            leadingIcon = { Icon(Icons.Rounded.Fullscreen, null, tint = MaterialTheme.colorScheme.secondary) }
        )


        // Landscape lyrics sheet: which half holds the lyrics.
        SwitchSettingItem(
            settingKey = "lyrics_landscape_lyrics_left",
            title = stringResource(R.string.settings_lyrics_landscape_lyrics_left_title),
            subtitle = stringResource(R.string.settings_lyrics_landscape_lyrics_left_subtitle),
            checked = lyricsDisplayPrefs.landscapeLyricsOnLeft,
            onCheckedChange = { enabled ->
                scope.launch {
                    context.editLyricsDisplayPrefs { it[LyricsDisplayPrefKeys.LANDSCAPE_LYRICS_ON_LEFT] = enabled }
                }
            },
            leadingIcon = { Icon(Icons.Rounded.ScreenRotation, null, tint = MaterialTheme.colorScheme.secondary) }
        )

        // Expressive typography: next to Immersive lyrics. Its choices hang off it while it's on.
        var typographyBranchOpen by rememberSettingsBranchExpanded("lyrics_adaptive_typography", "lyrics_font", "lyrics_text_size", "lyrics_font_weight", "lyrics_line_spacing")
        SwitchSettingItem(
            settingKey = "lyrics_expressive_typography",
            title = stringResource(R.string.settings_lyrics_expressive_typography_title),
            subtitle = stringResource(R.string.settings_lyrics_expressive_typography_subtitle),
            checked = lyricsDisplayPrefs.expressiveTypography,
            onCheckedChange = { enabled ->
                if (enabled) typographyBranchOpen = true
                scope.launch {
                    context.editLyricsDisplayPrefs { it[LyricsDisplayPrefKeys.EXPRESSIVE_TYPOGRAPHY] = enabled }
                }
            },
            leadingIcon = { Icon(Icons.Rounded.TextFields, null, tint = MaterialTheme.colorScheme.secondary) },
            branchExpanded = if (lyricsDisplayPrefs.expressiveTypography) typographyBranchOpen else null,
            onBranchToggle = { typographyBranchOpen = !typographyBranchOpen }
        )

        SettingsBranch(visible = lyricsDisplayPrefs.expressiveTypography && typographyBranchOpen) {
            SkillTreeBranch(depth = 1, isLast = false, lineColor = MaterialTheme.colorScheme.primary) {
                SwitchSettingItem(
                    settingKey = "lyrics_adaptive_typography",
                    title = stringResource(R.string.settings_lyrics_adaptive_typography_title),
                    subtitle = stringResource(R.string.settings_lyrics_adaptive_typography_subtitle),
                    checked = lyricsDisplayPrefs.adaptiveTypography,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            context.editLyricsDisplayPrefs { it[LyricsDisplayPrefKeys.ADAPTIVE_TYPOGRAPHY] = enabled }
                        }
                    },
                    leadingIcon = { Icon(Icons.Rounded.TextFields, null, tint = MaterialTheme.colorScheme.secondary) }
                )
            }
            SkillTreeBranch(depth = 1, isLast = false, lineColor = MaterialTheme.colorScheme.primary) {
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
                    selectedKey = lyricsDisplayPrefs.chosenFont.key,
                    onSelectionChanged = { key ->
                        scope.launch {
                            context.editLyricsDisplayPrefs { it[LyricsDisplayPrefKeys.FONT] = LyricsFont.fromKey(key).key }
                        }
                    },
                    leadingIcon = { Icon(Icons.Rounded.TextFields, null, tint = MaterialTheme.colorScheme.secondary) }
                )
            }
            SkillTreeBranch(depth = 1, isLast = false, lineColor = MaterialTheme.colorScheme.primary) {
                ThemeSelectorItem(
                    settingKey = "lyrics_text_size",
                    label = stringResource(R.string.settings_lyrics_size_title),
                    description = stringResource(R.string.settings_lyrics_size_subtitle),
                    options = mapOf(
                        LyricsTextSize.SMALL.key to stringResource(R.string.settings_lyrics_size_small),
                        LyricsTextSize.MEDIUM.key to stringResource(R.string.settings_lyrics_size_medium),
                        LyricsTextSize.LARGE.key to stringResource(R.string.settings_lyrics_size_large),
                        LyricsTextSize.EXTRA_LARGE.key to stringResource(R.string.settings_lyrics_size_extra_large)
                    ),
                    selectedKey = lyricsDisplayPrefs.chosenTextSize.key,
                    onSelectionChanged = { key ->
                        scope.launch {
                            context.editLyricsDisplayPrefs { it[LyricsDisplayPrefKeys.TEXT_SIZE] = LyricsTextSize.fromKey(key).key }
                        }
                    },
                    leadingIcon = { Icon(Icons.Rounded.FormatSize, null, tint = MaterialTheme.colorScheme.secondary) }
                )
            }
            SkillTreeBranch(depth = 1, isLast = false, lineColor = MaterialTheme.colorScheme.primary) {
                ThemeSelectorItem(
                    settingKey = "lyrics_font_weight",
                    label = stringResource(R.string.settings_lyrics_weight_title),
                    description = stringResource(R.string.settings_lyrics_weight_subtitle),
                    options = mapOf(
                        com.theveloper.pixelplay.presentation.components.LyricsFontWeight.LIGHT.key to stringResource(R.string.settings_lyrics_weight_light),
                        com.theveloper.pixelplay.presentation.components.LyricsFontWeight.REGULAR.key to stringResource(R.string.settings_lyrics_weight_regular),
                        com.theveloper.pixelplay.presentation.components.LyricsFontWeight.MEDIUM.key to stringResource(R.string.settings_lyrics_weight_medium),
                        com.theveloper.pixelplay.presentation.components.LyricsFontWeight.SEMIBOLD.key to stringResource(R.string.settings_lyrics_weight_semibold)
                    ),
                    selectedKey = lyricsDisplayPrefs.chosenFontWeight.key,
                    onSelectionChanged = { key ->
                        scope.launch {
                            context.editLyricsDisplayPrefs {
                                it[LyricsDisplayPrefKeys.FONT_WEIGHT] =
                                    com.theveloper.pixelplay.presentation.components.LyricsFontWeight.fromKey(key).key
                            }
                        }
                    },
                    leadingIcon = { Icon(Icons.Rounded.TextFields, null, tint = MaterialTheme.colorScheme.secondary) }
                )
            }
            SkillTreeBranch(depth = 1, isLast = true, lineColor = MaterialTheme.colorScheme.primary) {
                ThemeSelectorItem(
                    settingKey = "lyrics_line_spacing",
                    label = stringResource(R.string.settings_lyrics_spacing_title),
                    description = stringResource(R.string.settings_lyrics_spacing_subtitle),
                    options = mapOf(
                        com.theveloper.pixelplay.presentation.components.LyricsLineSpacing.TIGHT.key to stringResource(R.string.settings_lyrics_spacing_tight),
                        com.theveloper.pixelplay.presentation.components.LyricsLineSpacing.NORMAL.key to stringResource(R.string.settings_lyrics_spacing_normal),
                        com.theveloper.pixelplay.presentation.components.LyricsLineSpacing.RELAXED.key to stringResource(R.string.settings_lyrics_spacing_relaxed)
                    ),
                    selectedKey = lyricsDisplayPrefs.chosenLineSpacing.key,
                    onSelectionChanged = { key ->
                        scope.launch {
                            context.editLyricsDisplayPrefs {
                                it[LyricsDisplayPrefKeys.LINE_SPACING] =
                                    com.theveloper.pixelplay.presentation.components.LyricsLineSpacing.fromKey(key).key
                            }
                        }
                    },
                    leadingIcon = { Icon(Icons.Rounded.FormatSize, null, tint = MaterialTheme.colorScheme.secondary) }
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

    // Sources and sync after the look: they're set once, the display settings get changed often.
    SettingsSubsection(
        title = stringResource(R.string.settings_lyrics_sources_section),
        addBottomSpace = navController != null
    ) {
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
        SwitchSettingItem(
            settingKey = "lyrics_auto_sync",
            title = stringResource(R.string.settings_lyrics_auto_sync_title),
            subtitle = stringResource(R.string.settings_lyrics_auto_sync_subtitle),
            checked = lyricsAutoSyncEnabled,
            onCheckedChange = { settingsViewModel.setLyricsAutoSyncEnabled(it) },
            leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        var wordAlignBranchOpen by rememberSettingsBranchExpanded("lyrics_word_align_mobile_data", "lyrics_word_sync_model")
        SwitchSettingItem(
            settingKey = "lyrics_word_align",
            title = stringResource(R.string.settings_lyrics_word_align_title),
            subtitle = stringResource(R.string.settings_lyrics_word_align_subtitle),
            checked = lyricsWordAlignEnabled,
            onCheckedChange = { if (it) wordAlignBranchOpen = true; settingsViewModel.setLyricsWordAlignEnabled(it) },
            leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null, tint = MaterialTheme.colorScheme.secondary) },
            branchExpanded = if (lyricsWordAlignEnabled) wordAlignBranchOpen else null,
            onBranchToggle = { wordAlignBranchOpen = !wordAlignBranchOpen }
        )
        SettingsBranch(visible = lyricsWordAlignEnabled && wordAlignBranchOpen) {
            // The model: download it now on Wi-Fi or mobile data, see progress, or remove it.
            SkillTreeBranch(depth = 1, isLast = false, lineColor = MaterialTheme.colorScheme.primary) {
                WordSyncModelItem(settingsViewModel)
            }
            SkillTreeBranch(depth = 1, isLast = false, lineColor = MaterialTheme.colorScheme.primary) {
                TrainingExportItem(settingsViewModel)
            }
            SkillTreeBranch(depth = 1, isLast = true, lineColor = MaterialTheme.colorScheme.primary) {
                SwitchSettingItem(
                    settingKey = "lyrics_word_align_mobile_data",
                    title = "Download automatically on mobile data",
                    subtitle = if (lyricsWordAlignMobileData) {
                        "The model can download on Wi-Fi or mobile data the first time a song needs it"
                    } else {
                        "Off: it downloads by itself on Wi-Fi only. The Download button works on either"
                    },
                    checked = lyricsWordAlignMobileData,
                    onCheckedChange = { settingsViewModel.setLyricsWordAlignMobileData(it) },
                    leadingIcon = { Icon(Icons.Rounded.SignalCellularAlt, null, tint = MaterialTheme.colorScheme.secondary) }
                )
            }
        }
        // Rarely needed and destructive, so it sits last.
        SettingsItem(
            settingKey = "reset_lyrics",
            title = stringResource(R.string.settings_reset_imported_lyrics_title),
            subtitle = stringResource(R.string.settings_reset_imported_lyrics_subtitle),
            leadingIcon = { Icon(Icons.Outlined.ClearAll, null, tint = MaterialTheme.colorScheme.secondary) },
            onClick = { showClearLyricsDialog = true }
        )
    }

    // What used to be the separate Experimental screen: short, plain toggles.
    PlayerTweaksSection(settingsViewModel = settingsViewModel, uiState = uiState)

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

/** Lyrics animation, how the full player loads its content, and album art resolution. */
@Composable
private fun PlayerTweaksSection(settingsViewModel: SettingsViewModel, uiState: SettingsUiState) {
    val tweaks = uiState.fullPlayerLoadingTweaks
    val anyDelay = tweaks.delayAll || tweaks.delayAlbumCarousel || tweaks.delaySongMetadata ||
        tweaks.delayProgressBar || tweaks.delayControls
    SettingsSubsection(title = stringResource(R.string.settings_player_tweaks_title), addBottomSpace = false) {
        SwitchSettingItem(
            settingKey = "animated_lyrics",
            title = stringResource(R.string.settings_tweak_animated_lyrics),
            subtitle = "",
            checked = uiState.useAnimatedLyrics,
            onCheckedChange = settingsViewModel::setUseAnimatedLyrics,
            leadingIcon = { Icon(Icons.Rounded.AutoAwesomeMotion, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        if (uiState.useAnimatedLyrics) {
            SwitchSettingItem(
                settingKey = "animated_lyrics_blur",
                title = stringResource(R.string.settings_tweak_lyrics_blur),
                subtitle = "",
                checked = uiState.animatedLyricsBlurEnabled,
                onCheckedChange = settingsViewModel::setAnimatedLyricsBlurEnabled,
                leadingIcon = { Icon(Icons.Rounded.BlurOn, null, tint = MaterialTheme.colorScheme.secondary) }
            )
            if (uiState.animatedLyricsBlurEnabled) {
                SliderSettingsItem(
                    label = stringResource(R.string.settings_tweak_blur_strength),
                    value = uiState.animatedLyricsBlurStrength,
                    valueRange = 0.1f..2.0f,
                    steps = 10,
                    onValueChange = settingsViewModel::setAnimatedLyricsBlurStrength,
                    valueText = { "%.1fx".format(it) },
                    settingKey = "animated_lyrics_blur_strength"
                )
            }
        }
        SwitchSettingItem(
            settingKey = "player_load_after_open",
            title = stringResource(R.string.settings_tweak_load_after_open),
            subtitle = "",
            checked = tweaks.delayAll,
            onCheckedChange = settingsViewModel::setDelayAllFullPlayerContent,
            leadingIcon = { Icon(Icons.Rounded.HourglassBottom, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        if (!tweaks.delayAll) {
            SwitchSettingItem(
                title = "Load album art later", subtitle = "", checked = tweaks.delayAlbumCarousel,
                onCheckedChange = settingsViewModel::setDelayAlbumCarousel,
                leadingIcon = { Icon(Icons.Rounded.Album, null, tint = MaterialTheme.colorScheme.secondary) }
            )
            SwitchSettingItem(
                title = "Load song info later", subtitle = "", checked = tweaks.delaySongMetadata,
                onCheckedChange = settingsViewModel::setDelaySongMetadata,
                leadingIcon = { Icon(Icons.Rounded.TextFields, null, tint = MaterialTheme.colorScheme.secondary) }
            )
            SwitchSettingItem(
                title = "Load progress bar later", subtitle = "", checked = tweaks.delayProgressBar,
                onCheckedChange = settingsViewModel::setDelayProgressBar,
                leadingIcon = { Icon(Icons.Rounded.LinearScale, null, tint = MaterialTheme.colorScheme.secondary) }
            )
            SwitchSettingItem(
                title = "Load controls later", subtitle = "", checked = tweaks.delayControls,
                onCheckedChange = settingsViewModel::setDelayControls,
                leadingIcon = { Icon(Icons.Rounded.PlayCircleOutline, null, tint = MaterialTheme.colorScheme.secondary) }
            )
        }
        SwitchSettingItem(
            settingKey = "player_placeholders",
            title = stringResource(R.string.settings_tweak_placeholders),
            subtitle = "",
            checked = tweaks.showPlaceholders,
            onCheckedChange = settingsViewModel::setFullPlayerPlaceholders,
            leadingIcon = { Icon(Icons.Rounded.Rectangle, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        if (tweaks.showPlaceholders) {
            SwitchSettingItem(
                title = "Transparent placeholders", subtitle = "", checked = tweaks.transparentPlaceholders,
                onCheckedChange = settingsViewModel::setTransparentPlaceholders,
                leadingIcon = { Icon(Icons.Rounded.Opacity, null, tint = MaterialTheme.colorScheme.secondary) }
            )
            if (anyDelay) {
                com.theveloper.pixelplay.presentation.screens.SettingsSegmentedSelectorItem(
                    title = "Show content",
                    options = listOf(false, true),
                    selectedOption = tweaks.switchOnDragRelease,
                    optionLabel = { onRelease: Boolean -> if (onRelease) "On release" else "While opening" },
                    onOptionSelected = settingsViewModel::setFullPlayerSwitchOnDragRelease,
                    leadingIcon = { Icon(Icons.Rounded.SwipeUp, null, tint = MaterialTheme.colorScheme.secondary) }
                )
                if (!tweaks.switchOnDragRelease) {
                    SliderSettingsItem(
                        label = "Show at",
                        value = tweaks.contentAppearThresholdPercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { settingsViewModel.setFullPlayerAppearThreshold(it.roundToInt()) },
                        valueText = { "${it.roundToInt()}%" }
                    )
                }
                SwitchSettingItem(
                    title = "Placeholders when closing", subtitle = "", checked = tweaks.applyPlaceholdersOnClose,
                    onCheckedChange = settingsViewModel::setFullPlayerPlaceholdersOnClose,
                    leadingIcon = { Icon(Icons.Rounded.SwipeDown, null, tint = MaterialTheme.colorScheme.secondary) }
                )
                if (tweaks.applyPlaceholdersOnClose && !tweaks.switchOnDragRelease) {
                    SliderSettingsItem(
                        label = "Hide at",
                        value = tweaks.contentCloseThresholdPercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { settingsViewModel.setFullPlayerCloseThreshold(it.roundToInt()) },
                        valueText = { "${it.roundToInt()}%" }
                    )
                }
            }
        }
        com.theveloper.pixelplay.presentation.screens.SettingsSegmentedSelectorItem(
            title = stringResource(R.string.settings_tweak_album_art_quality),
            options = com.theveloper.pixelplay.data.preferences.AlbumArtQuality.entries.toList(),
            selectedOption = uiState.albumArtQuality,
            optionLabel = { quality: com.theveloper.pixelplay.data.preferences.AlbumArtQuality ->
                quality.label.substringBefore(" ")
            },
            onOptionSelected = settingsViewModel::setAlbumArtQuality,
            leadingIcon = { Icon(Icons.Rounded.HighQuality, null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "album_art_quality"
        )
    }
}

/**
 * The word-sync model row: "Not downloaded · about 95 MB" with a Download button that works on
 * Wi-Fi or mobile data (asks first on mobile data), a progress bar while it downloads (tap ✕ to
 * pause; Download resumes), and "Downloaded · 95 MB" with a delete button once it's on the phone.
 */
/**
 * Export training set: downloaded songs that have synced lyrics (every download saves a .lrc next
 * to the file) are copied with their .lrc to Download/PixelPlay-training, ready to upload as a
 * private dataset for the word-sync fine-tune.
 */
@Composable
private fun TrainingExportItem(settingsViewModel: SettingsViewModel) {
    val state by settingsViewModel.trainingExport.collectAsStateWithLifecycle()
    val current = state
    val running = current?.running == true
    val doneMessage = current?.message
    ActionSettingsItem(
        settingKey = "lyrics_training_export",
        title = "Export training set",
        subtitle = when {
            running && (current?.total ?: 0) > 0 -> "Exporting ${current?.done} of ${current?.total}…"
            running -> "Looking at your downloads…"
            doneMessage != null -> doneMessage
            else -> "Copies your downloaded songs that have synced lyrics, with their .lrc, to " +
                "Download/PixelPlay-training. Upload that folder as a private dataset to train word sync."
        },
        icon = { Icon(Icons.Rounded.AutoFixHigh, null, tint = MaterialTheme.colorScheme.secondary) },
        primaryActionLabel = if (running) "Exporting…" else "Export",
        onPrimaryAction = { settingsViewModel.exportTrainingSet() },
        secondaryActionLabel = if (running) null else "Export all again",
        onSecondaryAction = if (running) null else ({ settingsViewModel.exportTrainingSet(again = true) }),
        enabled = !running
    )
}

@Composable
private fun WordSyncModelItem(settingsViewModel: SettingsViewModel) {
    val state by settingsViewModel.wordSyncModelState.collectAsStateWithLifecycle()
    var confirmMobileData by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val current = state
    val status = current?.status ?: com.theveloper.pixelplay.data.lyrics.align.AlignmentModelManager.Status.NOT_DOWNLOADED
    fun mb(bytes: Long): String = "%.0f MB".format(bytes / (1024f * 1024f))
    fun startDownload() {
        if (settingsViewModel.isOnMobileData()) confirmMobileData = true
        else settingsViewModel.downloadWordSyncModel()
    }

    androidx.compose.material3.Surface(
        color = colors.surfaceContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    if (status == com.theveloper.pixelplay.data.lyrics.align.AlignmentModelManager.Status.READY) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                    contentDescription = null,
                    tint = colors.secondary,
                    modifier = Modifier.size(24.dp)
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text("Word sync model", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(
                        text = when (status) {
                            com.theveloper.pixelplay.data.lyrics.align.AlignmentModelManager.Status.READY ->
                                "Downloaded · ${mb(current?.totalBytes ?: 0L)} on this phone"
                            com.theveloper.pixelplay.data.lyrics.align.AlignmentModelManager.Status.DOWNLOADING ->
                                "Downloading… ${mb(current?.downloadedBytes ?: 0L)} of ${mb(current?.totalBytes ?: 0L)}"
                            com.theveloper.pixelplay.data.lyrics.align.AlignmentModelManager.Status.PAUSED ->
                                (current?.message ?: "Paused") + " · ${mb(current?.downloadedBytes ?: 0L)} of ${mb(current?.totalBytes ?: 0L)}"
                            com.theveloper.pixelplay.data.lyrics.align.AlignmentModelManager.Status.FAILED ->
                                current?.message ?: "Download failed. Tap Download to try again"
                            com.theveloper.pixelplay.data.lyrics.align.AlignmentModelManager.Status.NOT_DOWNLOADED ->
                                "Not downloaded · about 95 MB. Download on Wi-Fi or mobile data"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                when (status) {
                    com.theveloper.pixelplay.data.lyrics.align.AlignmentModelManager.Status.READY ->
                        androidx.compose.material3.IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete word sync model")
                        }
                    com.theveloper.pixelplay.data.lyrics.align.AlignmentModelManager.Status.DOWNLOADING ->
                        androidx.compose.material3.IconButton(onClick = { settingsViewModel.cancelWordSyncModelDownload() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Pause download")
                        }
                    else ->
                        androidx.compose.material3.FilledTonalButton(onClick = { startDownload() }) {
                            Text(
                                if (status == com.theveloper.pixelplay.data.lyrics.align.AlignmentModelManager.Status.PAUSED) "Resume" else "Download"
                            )
                        }
                }
            }
            if (status == com.theveloper.pixelplay.data.lyrics.align.AlignmentModelManager.Status.DOWNLOADING ||
                status == com.theveloper.pixelplay.data.lyrics.align.AlignmentModelManager.Status.PAUSED
            ) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { current?.progress ?: 0f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (confirmMobileData) {
        AlertDialog(
            onDismissRequest = { confirmMobileData = false },
            icon = { Icon(Icons.Rounded.SignalCellularAlt, null) },
            title = { Text("Download on mobile data?") },
            text = { Text("The word sync model is about 95 MB. Your carrier may charge for this data.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmMobileData = false
                    settingsViewModel.downloadWordSyncModel()
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { confirmMobileData = false }) { Text("Cancel") }
            }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete the word sync model?") },
            text = { Text("Songs you've already synced keep their word timing. New songs need the model again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    settingsViewModel.deleteWordSyncModel()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}
