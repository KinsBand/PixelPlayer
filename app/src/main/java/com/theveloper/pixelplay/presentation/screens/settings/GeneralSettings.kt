package com.theveloper.pixelplay.presentation.screens.settings

import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import com.theveloper.pixelplay.utils.rememberSystemBarsPrefs
import com.theveloper.pixelplay.utils.setHideNavigationBar
import com.theveloper.pixelplay.utils.setHideStatusBar
import kotlinx.coroutines.launch

/**
 * General: how the app behaves, independent of how it looks — language, which screen it
 * opens on, gestures and haptics, and full-screen mode.
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
    val scope = rememberCoroutineScope()
    val systemBarsPrefs = rememberSystemBarsPrefs()

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

    SettingsSubsection(title = stringResource(R.string.settings_gestures_haptics_section)) {
        SwitchSettingItem(
            title = stringResource(R.string.settings_haptic_feedback_title),
            subtitle = stringResource(R.string.settings_haptic_feedback_subtitle),
            checked = uiState.hapticsEnabled,
            onCheckedChange = { settingsViewModel.setHapticsEnabled(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_touch_app_24), null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "haptic_feedback"
        )
    }

    // Hide the system bars while the app is open. Swiping in from the edge shows them
    // briefly; they hide again on their own.
    SettingsSubsection(title = stringResource(R.string.settings_full_screen_section), addBottomSpace = false) {
        SwitchSettingItem(
            title = stringResource(R.string.settings_hide_status_bar_title),
            subtitle = stringResource(R.string.settings_hide_status_bar_subtitle),
            checked = systemBarsPrefs.hideStatusBar,
            onCheckedChange = { hide -> scope.launch { context.setHideStatusBar(hide) } },
            leadingIcon = { Icon(Icons.Rounded.Fullscreen, null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "hide_status_bar"
        )
        SwitchSettingItem(
            title = stringResource(R.string.settings_hide_gesture_bar_title),
            subtitle = stringResource(R.string.settings_hide_gesture_bar_subtitle),
            checked = systemBarsPrefs.hideNavigationBar,
            onCheckedChange = { hide -> scope.launch { context.setHideNavigationBar(hide) } },
            leadingIcon = { Icon(Icons.Rounded.Fullscreen, null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "hide_navigation_bar"
        )
    }
}
