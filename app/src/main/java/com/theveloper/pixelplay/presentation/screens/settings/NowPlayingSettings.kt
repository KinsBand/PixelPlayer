package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.screens.SettingsItem
import com.theveloper.pixelplay.presentation.screens.SwitchSettingItem
import com.theveloper.pixelplay.presentation.screens.ThemeSelectorItem
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsUiState
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

/**
 * Player & Lyrics: everything about the now-playing screen in one place — how the player
 * looks, where lyrics come from, how they are shown, and the experimental player tweaks.
 *
 * The lyrics half is [LyricsSettingsContent], unchanged, because it is also used outside
 * settings.
 */
@Composable
internal fun PlayerLyricsSettingsContent(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel,
    uiState: SettingsUiState
) {
    SettingsSubsection(title = stringResource(R.string.settings_player_look_section)) {
        ThemeSelectorItem(
            label = stringResource(R.string.settings_player_theme_title),
            description = stringResource(R.string.settings_player_theme_subtitle),
            options = mapOf(
                ThemePreference.ALBUM_ART to stringResource(R.string.settings_player_theme_album_art),
                ThemePreference.DYNAMIC to stringResource(R.string.settings_player_theme_dynamic)
            ),
            selectedKey = uiState.playerThemePreference,
            onSelectionChanged = { settingsViewModel.setPlayerThemePreference(it) },
            leadingIcon = { Icon(Icons.Outlined.PlayCircle, null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "player_theme"
        )
        SettingsItem(
            title = stringResource(R.string.settings_album_art_palette_title),
            subtitle = stringResource(R.string.settings_album_art_palette_subtitle, uiState.albumArtPaletteStyle.label),
            leadingIcon = { Icon(Icons.Outlined.Style, null, tint = MaterialTheme.colorScheme.secondary) },
            trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { navController.navigateSafely(Screen.PaletteStyle.route) },
            settingKey = "palette_style"
        )
        ThemeSelectorItem(
            label = stringResource(R.string.settings_carousel_style_title),
            description = stringResource(R.string.settings_carousel_style_subtitle),
            options = mapOf(
                CarouselStyle.NO_PEEK to stringResource(R.string.settings_carousel_no_peek),
                CarouselStyle.ONE_PEEK to stringResource(R.string.settings_carousel_one_peek)
            ),
            selectedKey = uiState.carouselStyle,
            onSelectionChanged = { settingsViewModel.setCarouselStyle(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_view_carousel_24), null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "carousel_style"
        )
        SwitchSettingItem(
            title = stringResource(R.string.settings_show_player_file_info_title),
            subtitle = stringResource(R.string.settings_show_player_file_info_subtitle),
            checked = uiState.showPlayerFileInfo,
            onCheckedChange = { settingsViewModel.setShowPlayerFileInfo(it) },
            leadingIcon = { Icon(painterResource(R.drawable.rounded_attach_file_24), null, tint = MaterialTheme.colorScheme.secondary) },
            settingKey = "show_player_file_info"
        )
    }

    LyricsSettingsContent(
        playerViewModel = playerViewModel,
        settingsViewModel = settingsViewModel,
        uiState = uiState,
        navController = navController
    )
}
