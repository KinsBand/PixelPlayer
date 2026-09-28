package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BlurOff
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.TouchApp
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
import com.theveloper.pixelplay.utils.setHideNavigationBar
import com.theveloper.pixelplay.utils.setHideStatusBar
import kotlinx.coroutines.launch

/**
 * Appearance: how the app looks — theme and effects, the navigation bar, full screen (hiding
 * the status / gesture bars) and the camera-cutout island.
 *
 * Language moved to General (it changes behaviour, not looks), and the player's own look moved
 * to Player & Lyrics.
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

    FullScreenSettingsSection()

    CameraIslandSettingsSection()
}

/** Full screen: hide the status bar and / or the gesture bar while the app is open. */
@Composable
internal fun FullScreenSettingsSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val systemBarsPrefs = com.theveloper.pixelplay.utils.rememberSystemBarsPrefs()
    // Hide the system bars while the app is open. They stay hidden: anything that brings them
    // back (the back gesture, a dialog, returning to the app) hides them again.
    SettingsSubsection(title = stringResource(R.string.settings_full_screen_section)) {
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

/** The camera-cutout island. App-wide, so it lives with the rest of the app's look. */
@Composable
internal fun CameraIslandSettingsSection(
    viewModel: com.theveloper.pixelplay.presentation.viewmodel.VisualWidgetsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    SettingsSubsection(title = stringResource(R.string.settings_camera_island_section), addBottomSpace = false) {
        val overlayEnabled by viewModel.enableCutoutOverlayFlow.collectAsStateWithLifecycle(initialValue = false)
        // Without the accessibility service the island is a TYPE_APPLICATION_OVERLAY,
        // which Android layers under the status bar - taps on the camera hole never reach it.
        var tapAccessEnabled by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(
                com.theveloper.pixelplay.ui.overlay.OverlayWidgetManager.isAccessibilityServiceEnabled(context)
            )
        }
        androidx.compose.runtime.DisposableEffect(Unit) {
            val listener: (Boolean) -> Unit = { running -> tapAccessEnabled = running }
            com.theveloper.pixelplay.ui.overlay.CutoutIslandAccessibilityService.addStateListener(listener)
            onDispose {
                com.theveloper.pixelplay.ui.overlay.CutoutIslandAccessibilityService.removeStateListener(listener)
            }
        }
        SwitchSettingItem(
            title = stringResource(R.string.settings_camera_island_title),
            subtitle = stringResource(R.string.settings_camera_island_subtitle),
            checked = overlayEnabled,
            onCheckedChange = { isChecked ->
                if (isChecked && !tapAccessEnabled) {
                    // Only the accessibility window can sit above the status bar (time, battery,
                    // notification icons) and take taps on the camera hole; a plain "Appear on
                    // top" overlay is always drawn underneath it. Turn the island on and send the
                    // user straight to the "PixelPlayer Dynamic Island" switch: the island is
                    // rebuilt above the status bar as soon as that service connects.
                    viewModel.setEnableCutoutOverlay(true)
                    com.theveloper.pixelplay.ui.overlay.OverlayWidgetManager.syncOverlayServiceState(context, true)
                    android.widget.Toast.makeText(
                        context,
                        "Turn on \"PixelPlayer Dynamic Island\" so the island shows over the status bar",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    context.startActivity(com.theveloper.pixelplay.ui.overlay.OverlayWidgetManager.createAccessibilitySettingsIntent())
                } else {
                    viewModel.setEnableCutoutOverlay(isChecked)
                    com.theveloper.pixelplay.ui.overlay.OverlayWidgetManager.syncOverlayServiceState(context, isChecked)
                }
            },
            leadingIcon = {
                Icon(Icons.Rounded.Radio, null, tint = MaterialTheme.colorScheme.secondary)
            },
            settingKey = "camera_cutout_island_overlay"
        )
        SettingsItem(
            title = stringResource(R.string.settings_camera_island_tap_title),
            subtitle = if (tapAccessEnabled) {
                stringResource(R.string.settings_camera_island_tap_on)
            } else {
                stringResource(R.string.settings_camera_island_tap_off)
            },
            leadingIcon = {
                Icon(Icons.Rounded.TouchApp, null, tint = MaterialTheme.colorScheme.secondary)
            },
            settingKey = "camera_cutout_island_tap_access",
            onClick = {
                context.startActivity(
                    com.theveloper.pixelplay.ui.overlay.OverlayWidgetManager.createAccessibilitySettingsIntent()
                )
            }
        )
    }
}
