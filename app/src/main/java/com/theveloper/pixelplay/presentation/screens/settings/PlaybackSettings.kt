package com.theveloper.pixelplay.presentation.screens.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.screens.SettingsItem
import com.theveloper.pixelplay.presentation.screens.SettingsSegmentedSelectorItem
import com.theveloper.pixelplay.presentation.screens.SliderSettingsItem
import com.theveloper.pixelplay.presentation.screens.SwitchSettingItem
import com.theveloper.pixelplay.presentation.viewmodel.SettingsUiState
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

@Composable
internal fun PlaybackSettingsContent(
    settingsViewModel: SettingsViewModel,
    uiState: SettingsUiState,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val setListenEnabled = com.theveloper.pixelplay.presentation.components.rememberListenAction()

    // Hoisted string resources (for use inside lambdas/callbacks)
    val toastBatteryAlreadyDisabled = stringResource(R.string.settings_toast_battery_already_disabled)
    val toastBatterySettingsUnavailable = stringResource(R.string.settings_toast_battery_settings_unavailable)

    // 1. Sound Quality & Audio Output
    SettingsSubsection(title = stringResource(R.string.settings_sound_quality_section)) {
        if (navController != null) {
            SettingsItem(
                settingKey = "equalizer",
                title = stringResource(R.string.settings_equalizer_shortcut_title),
                subtitle = stringResource(R.string.settings_equalizer_shortcut_subtitle),
                onClick = { navController.navigate(Screen.Equalizer.route) },
                leadingIcon = { Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.secondary) },
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
        SwitchSettingItem(
            settingKey = "hifi_mode",
            title = stringResource(R.string.settings_hifi_mode_title),
            subtitle = if (uiState.hiFiModeDeviceSupported)
                stringResource(R.string.settings_hifi_mode_supported_subtitle)
            else
                stringResource(R.string.settings_hifi_mode_unsupported_subtitle),
            checked = uiState.hiFiModeEnabled,
            onCheckedChange = { settingsViewModel.setHiFiModeEnabled(it) },
            enabled = uiState.hiFiModeDeviceSupported,
            leadingIcon = { Icon(painterResource(R.drawable.outline_high_quality_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            settingKey = "replaygain",
            title = stringResource(R.string.settings_replaygain_enable_title),
            subtitle = stringResource(R.string.settings_replaygain_enable_subtitle),
            checked = uiState.replayGainEnabled,
            onCheckedChange = { settingsViewModel.setReplayGainEnabled(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_volume_down_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        AnimatedVisibility(
            visible = uiState.replayGainEnabled,
            enter = expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(animationSpec = spring(stiffness = 400f)),
            exit = shrinkVertically(animationSpec = spring(stiffness = 500f)) + fadeOut(animationSpec = spring(stiffness = 500f))
        ) {
            SettingsSegmentedSelectorItem(
                title = stringResource(R.string.settings_gain_mode_title),
                subtitle = stringResource(R.string.settings_gain_mode_subtitle),
                options = listOf("track", "album"),
                selectedOption = if (uiState.replayGainUseAlbumGain) "album" else "track",
                optionLabel = { mode: String ->
                    if (mode == "album") stringResource(R.string.settings_gain_mode_album)
                    else stringResource(R.string.settings_gain_mode_track)
                },
                onOptionSelected = { mode: String -> settingsViewModel.setReplayGainUseAlbumGain(mode == "album") },
                leadingIcon = { Icon(painterResource(R.drawable.rounded_volume_down_24), null, tint = MaterialTheme.colorScheme.secondary) },
                settingKey = "replaygain_mode"
            )
        }
    }

    // 2. Queue & Transitions
    SettingsSubsection(title = stringResource(R.string.settings_queue_transitions_section)) {
        SwitchSettingItem(
            settingKey = "crossfade",
            title = stringResource(R.string.settings_crossfade_title),
            subtitle = stringResource(R.string.settings_crossfade_subtitle),
            checked = uiState.isCrossfadeEnabled,
            onCheckedChange = { settingsViewModel.setCrossfadeEnabled(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_align_justify_space_even_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        AnimatedVisibility(
            visible = uiState.isCrossfadeEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SliderSettingsItem(
                settingKey = "crossfade_duration",
                label = stringResource(R.string.settings_crossfade_duration_title),
                value = uiState.crossfadeDuration.toFloat(),
                valueRange = 1000f..12000f,
                steps = 10,
                onValueChange = { settingsViewModel.setCrossfadeDuration(it.toInt()) },
                valueText = { value -> "${(value / 1000).toInt()}s" },
                presets = listOf(2000f, 4000f, 8000f, 12000f)
            )
        }
        SwitchSettingItem(
            settingKey = "persistent_shuffle",
            title = stringResource(R.string.settings_persistent_shuffle_title),
            subtitle = stringResource(R.string.settings_persistent_shuffle_subtitle),
            checked = uiState.persistentShuffleEnabled,
            onCheckedChange = { settingsViewModel.setPersistentShuffleEnabled(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_shuffle_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            settingKey = "queue_history",
            title = stringResource(R.string.settings_show_queue_history_title),
            subtitle = stringResource(R.string.settings_show_queue_history_subtitle),
            checked = uiState.showQueueHistory,
            onCheckedChange = { settingsViewModel.setShowQueueHistory(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_queue_music_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    // 3. Audio Output & Controls
    SettingsSubsection(title = stringResource(R.string.settings_playback_audio_output_section)) {
        SwitchSettingItem(
            settingKey = "cast_autoplay",
            title = stringResource(R.string.settings_cast_autoplay_title),
            subtitle = stringResource(R.string.settings_cast_autoplay_subtitle),
            checked = !uiState.disableCastAutoplay,
            onCheckedChange = { settingsViewModel.setDisableCastAutoplay(!it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_cast_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            settingKey = "pause_on_zero",
            title = stringResource(R.string.settings_pause_on_volume_zero),
            subtitle = stringResource(R.string.settings_pause_on_volume_zero_desc),
            checked = uiState.pauseOnVolumeZero,
            onCheckedChange = { settingsViewModel.setPauseOnVolumeZero(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_volume_down_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            settingKey = "headphones_resume",
            title = stringResource(R.string.settings_headphones_resume_title),
            subtitle = stringResource(R.string.settings_headphones_resume_subtitle),
            checked = uiState.resumeOnHeadsetReconnect,
            onCheckedChange = { settingsViewModel.setResumeOnHeadsetReconnect(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_headphones_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    // 4. Background & Battery
    SettingsSubsection(title = stringResource(R.string.settings_background_playback_section)) {
        SwitchSettingItem(
            settingKey = "keep_playing",
            title = stringResource(R.string.settings_keep_playing_title),
            subtitle = stringResource(R.string.settings_keep_playing_subtitle),
            checked = uiState.keepPlayingInBackground,
            onCheckedChange = { settingsViewModel.setKeepPlayingInBackground(it) },
            leadingIcon = { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SettingsItem(
            settingKey = "battery_optimization",
            title = stringResource(R.string.settings_battery_optimization_title),
            subtitle = stringResource(R.string.settings_battery_optimization_subtitle),
            onClick = {
                val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                    Toast.makeText(context, toastBatteryAlreadyDisabled, Toast.LENGTH_SHORT).show()
                    return@SettingsItem
                }
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        context.startActivity(fallbackIntent)
                    } catch (e2: Exception) {
                        Toast.makeText(context, toastBatterySettingsUnavailable, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_all_inclusive_24), null, tint = MaterialTheme.colorScheme.secondary) }
        )
    }

    // Scrobbling (ListenBrainz) moved to Accounts & Services (ServicesSettings.kt).

    // 5. Ambient Voice Suggestions
    SettingsSubsection(title = stringResource(R.string.settings_ambient_suggestions_section)) {
        SwitchSettingItem(
            settingKey = "ambient_suggestions",
            title = stringResource(R.string.settings_ambient_suggestions_title),
            subtitle = stringResource(R.string.settings_ambient_suggestions_subtitle),
            checked = uiState.ambientSuggestionsEnabled,
            onCheckedChange = setListenEnabled,
            leadingIcon = { Icon(Icons.Rounded.Hearing, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        AnimatedVisibility(
            visible = uiState.ambientSuggestionsEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SwitchSettingItem(
                settingKey = "ambient_chime",
                title = stringResource(R.string.settings_ambient_chime_title),
                subtitle = stringResource(R.string.settings_ambient_chime_subtitle),
                checked = uiState.ambientAudioChimeEnabled,
                onCheckedChange = { settingsViewModel.setAmbientAudioChimeEnabled(it) },
                leadingIcon = { Icon(painterResource(R.drawable.rounded_volume_down_24), null, tint = MaterialTheme.colorScheme.secondary) }
            )
        }
    }
}
