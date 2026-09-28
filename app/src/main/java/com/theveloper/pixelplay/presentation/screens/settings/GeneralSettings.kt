package com.theveloper.pixelplay.presentation.screens.settings

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.presentation.screens.SettingsItem
import com.theveloper.pixelplay.utils.AppLock
import com.theveloper.pixelplay.utils.findHostActivity
import com.theveloper.pixelplay.utils.rememberAppLockEnabled
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.AppLanguage
import com.theveloper.pixelplay.data.preferences.LaunchTab
import com.theveloper.pixelplay.data.preferences.LibraryNavigationMode
import com.theveloper.pixelplay.presentation.screens.SettingsSegmentedSelectorItem
import com.theveloper.pixelplay.presentation.screens.SwitchSettingItem
import com.theveloper.pixelplay.presentation.screens.ThemeSelectorItem
import com.theveloper.pixelplay.presentation.viewmodel.SettingsUiState
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

/**
 * General: how the app behaves, independent of how it looks — language, which screen it
 * opens on, and gestures and haptics. Full-screen mode lives in Appearance.
 *
 * Collects what used to be split between Appearance (language, full screen) and the old
 * Navigation page (starting tab, library layout, gestures, haptics).
 */
@Composable
internal fun GeneralSettingsContent(
    settingsViewModel: SettingsViewModel,
    uiState: SettingsUiState
) {
    val context = LocalContext.current

    SettingsSubsection(title = stringResource(R.string.settings_language_section)) {
        ThemeSelectorItem(
            label = stringResource(R.string.settings_app_language_title),
            description = stringResource(R.string.settings_app_language_subtitle),
            options = AppLanguage.getLanguageOptions(context),
            selectedKey = uiState.appLanguageTag,
            onSelectionChanged = {
                settingsViewModel.setAppLanguage(it)
                (context as? Activity)?.recreate()
            },
            leadingIcon = { Icon(Icons.Outlined.Language, null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "app_language"
        )
    }

    SettingsSubsection(title = stringResource(R.string.settings_starting_screen_section)) {
        SettingsSegmentedSelectorItem(
            title = stringResource(R.string.settings_default_tab_title),
            subtitle = stringResource(R.string.settings_default_tab_subtitle),
            options = listOf(LaunchTab.HOME, LaunchTab.SEARCH, LaunchTab.LIBRARY),
            selectedOption = uiState.launchTab,
            optionLabel = { tab: String ->
                when (tab) {
                    LaunchTab.HOME -> stringResource(R.string.settings_default_tab_home)
                    LaunchTab.SEARCH -> stringResource(R.string.common_search)
                    else -> stringResource(R.string.settings_default_tab_library)
                }
            },
            onOptionSelected = { tab: String -> settingsViewModel.setLaunchTab(tab) },
            leadingIcon = { Icon(painterResource(R.drawable.tab_24), null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "default_tab"
        )
        ThemeSelectorItem(
            label = stringResource(R.string.settings_library_navigation_title),
            description = stringResource(R.string.settings_library_navigation_subtitle),
            options = mapOf(
                LibraryNavigationMode.TAB_ROW to stringResource(R.string.settings_library_nav_tab_row),
                LibraryNavigationMode.COMPACT_PILL to stringResource(R.string.settings_library_nav_compact_pill)
            ),
            selectedKey = uiState.libraryNavigationMode,
            onSelectionChanged = { settingsViewModel.setLibraryNavigationMode(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_library_music_24), null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "library_navigation"
        )
    }

    AppLockSettingsSection()

    SettingsSubsection(title = stringResource(R.string.settings_gestures_haptics_section), addBottomSpace = false) {
        SwitchSettingItem(
            title = stringResource(R.string.settings_haptic_feedback_title),
            subtitle = stringResource(R.string.settings_haptic_feedback_subtitle),
            checked = uiState.hapticsEnabled,
            onCheckedChange = { settingsViewModel.setHapticsEnabled(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_touch_app_24), null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "haptic_feedback"
        )
    }
}

/**
 * Group & security → Lock App to Screen. Turning it on or off, and ending a locked session, all
 * go through Android's biometric prompt (see [AppLock]); a failed or cancelled prompt changes
 * nothing, so the switch simply stays where it was.
 */
@Composable
private fun AppLockSettingsSection() {
    val context = LocalContext.current
    val activity = remember(context) { context.findHostActivity() }
    val scope = rememberCoroutineScope()
    val enabled = rememberAppLockEnabled()
    val pinned by AppLock.pinned.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }

    SettingsSubsection(title = stringResource(R.string.settings_app_lock_section)) {
        SwitchSettingItem(
            title = stringResource(R.string.settings_app_lock_title),
            subtitle = stringResource(R.string.settings_app_lock_subtitle),
            checked = enabled,
            enabled = activity != null && !busy,
            onCheckedChange = { wanted ->
                val host = activity ?: return@SwitchSettingItem
                busy = true
                scope.launch {
                    try {
                        AppLock.setEnabled(host, wanted)
                    } finally {
                        busy = false
                    }
                }
            },
            leadingIcon = { Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "app_lock"
        )
        if (enabled && activity != null) {
            SettingsItem(
                title = stringResource(
                    if (pinned) R.string.settings_app_lock_active_title else R.string.settings_app_lock_paused_title
                ),
                subtitle = stringResource(
                    if (pinned) R.string.settings_app_lock_active_subtitle else R.string.settings_app_lock_paused_subtitle
                ),
                leadingIcon = {
                    Icon(
                        if (pinned) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = {
                    if (busy) return@SettingsItem
                    if (pinned) {
                        busy = true
                        scope.launch {
                            try {
                                AppLock.endSession(activity)
                            } finally {
                                busy = false
                            }
                        }
                    } else {
                        AppLock.lockNow(activity)
                    }
                }
            )
            // Android blocks opening its settings while the app is pinned, so this only shows
            // while the lock is paused.
            if (!pinned) {
                SettingsItem(
                    title = stringResource(R.string.settings_app_lock_pinning_title),
                    subtitle = stringResource(R.string.settings_app_lock_pinning_subtitle),
                    leadingIcon = { Icon(Icons.Rounded.PushPin, null, tint = MaterialTheme.colorScheme.secondary) },
                    trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
            }
        }
    }
}
