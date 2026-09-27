package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BlurOff
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.CollagePattern
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.screens.SettingsItem
import com.theveloper.pixelplay.presentation.screens.SettingsSegmentedSelectorItem
import com.theveloper.pixelplay.presentation.screens.SwitchSettingItem
import com.theveloper.pixelplay.presentation.screens.ThemeSelectorItem
import com.theveloper.pixelplay.presentation.viewmodel.SettingsUiState
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

/**
 * Appearance: how the app looks — theme and effects, the navigation bar, and the home
 * collage.
 *
 * Language and full-screen mode moved to General (they change behaviour, not looks), and
 * the player's own look moved to Player & Lyrics.
 */
@Composable
internal fun AppearanceSettingsContent(
    navController: NavController,
    settingsViewModel: SettingsViewModel,
    uiState: SettingsUiState
) {
    val useSmoothCorners by settingsViewModel.useSmoothCorners.collectAsStateWithLifecycle()
    val miniPlayerSongTransition by settingsViewModel.miniPlayerSongTransition.collectAsStateWithLifecycle()

    SettingsSubsection(title = stringResource(R.string.settings_global_theme_section)) {
        SettingsSegmentedSelectorItem(
            title = stringResource(R.string.settings_theme_mode_inline_title),
            options = listOf(AppThemeMode.LIGHT, AppThemeMode.FOLLOW_SYSTEM, AppThemeMode.DARK),
            selectedOption = uiState.appThemeMode,
            optionLabel = { mode: String ->
                when (mode) {
                    AppThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                    AppThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                    else -> stringResource(R.string.settings_theme_follow_system)
                }
            },
            onOptionSelected = { mode: String -> settingsViewModel.setAppThemeMode(mode) },
            leadingIcon = { Icon(Icons.Outlined.LightMode, null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "app_theme"
        )
        SwitchSettingItem(
            title = stringResource(R.string.settings_smooth_corners_title),
            subtitle = stringResource(R.string.settings_smooth_corners_subtitle),
            checked = useSmoothCorners,
            onCheckedChange = settingsViewModel::setUseSmoothCorners,
            leadingIcon = { Icon(painterResource(R.drawable.rounded_rounded_corner_24), null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "smooth_corners"
        )
        SwitchSettingItem(
            title = stringResource(R.string.settings_disable_blur_all_over_title),
            subtitle = stringResource(R.string.settings_disable_blur_all_over_subtitle),
            checked = uiState.disableBlurAllOver,
            onCheckedChange = { settingsViewModel.setDisableBlurAllOver(it) },
            leadingIcon = { Icon(Icons.Rounded.BlurOff, null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "disable_blur"
        )
        SwitchSettingItem(
            title = stringResource(R.string.settings_mini_player_song_transition_title),
            subtitle = stringResource(R.string.settings_mini_player_song_transition_subtitle),
            checked = miniPlayerSongTransition,
            onCheckedChange = settingsViewModel::setMiniPlayerSongTransition,
            leadingIcon = { Icon(Icons.Rounded.Animation, null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "mini_player_song_transition"
        )
        SwitchSettingItem(
            title = stringResource(R.string.settings_show_scrollbar_title),
            subtitle = stringResource(R.string.settings_show_scrollbar_subtitle),
            checked = uiState.showScrollbar,
            onCheckedChange = { settingsViewModel.setShowScrollbar(it) },
            leadingIcon = { Icon(Icons.Rounded.UnfoldMore, null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "show_scrollbar"
        )
    }

    SettingsSubsection(title = stringResource(R.string.settings_navigation_bar_section)) {
        ThemeSelectorItem(
            label = stringResource(R.string.settings_navbar_style_title),
            description = stringResource(R.string.settings_navbar_style_subtitle),
            options = mapOf(
                NavBarStyle.DEFAULT to stringResource(R.string.settings_navbar_style_default),
                NavBarStyle.FULL_WIDTH to stringResource(R.string.settings_navbar_style_full_width)
            ),
            selectedKey = uiState.navBarStyle,
            onSelectionChanged = { settingsViewModel.setNavBarStyle(it) },
            leadingIcon = { Icon(Icons.Outlined.Style, null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "navbar_style"
        )
        SwitchSettingItem(
            title = stringResource(R.string.settings_compact_mode_title),
            subtitle = stringResource(R.string.settings_compact_mode_subtitle),
            checked = uiState.navBarCompactMode,
            onCheckedChange = { settingsViewModel.setNavBarCompactMode(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_view_week_24), null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "compact_mode"
        )
        SettingsItem(
            title = stringResource(R.string.settings_navbar_corner_title),
            subtitle = stringResource(R.string.settings_navbar_corner_subtitle),
            leadingIcon = { Icon(painterResource(R.drawable.rounded_rounded_corner_24), null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { navController.navigateSafely(Screen.NavBarCrRad.route) },
            settingKey = "navbar_corner_radius"
        )
    }

    SettingsSubsection(title = stringResource(R.string.settings_home_collage_section), addBottomSpace = false) {
        ThemeSelectorItem(
            label = stringResource(R.string.settings_collage_pattern_title),
            description = stringResource(R.string.settings_collage_pattern_subtitle),
            options = CollagePattern.entries.associate { it.storageKey to it.label },
            selectedKey = uiState.collagePattern.storageKey,
            onSelectionChanged = { key ->
                settingsViewModel.setCollagePattern(CollagePattern.fromStorageKey(key))
            },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_view_column_24), null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "collage_pattern"
        )
        SwitchSettingItem(
            title = stringResource(R.string.settings_auto_rotate_patterns_title),
            subtitle = stringResource(R.string.settings_auto_rotate_patterns_subtitle),
            checked = uiState.collageAutoRotate,
            onCheckedChange = { settingsViewModel.setCollageAutoRotate(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_shuffle_24), null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "auto_rotate_patterns"
        )
    }
}
