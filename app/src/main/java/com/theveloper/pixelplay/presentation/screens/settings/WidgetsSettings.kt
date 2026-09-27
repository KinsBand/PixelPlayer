package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.TurntableOptions
import com.theveloper.pixelplay.data.preferences.WidgetAccentSource
import com.theveloper.pixelplay.data.preferences.WidgetAppearance
import com.theveloper.pixelplay.data.preferences.WidgetBackgroundStyle
import com.theveloper.pixelplay.data.preferences.WidgetConfig
import com.theveloper.pixelplay.data.preferences.WidgetKind
import com.theveloper.pixelplay.data.preferences.WidgetProgressStyle
import com.theveloper.pixelplay.data.preferences.WidgetSpinMode
import com.theveloper.pixelplay.presentation.screens.ActionSettingsItem
import com.theveloper.pixelplay.presentation.screens.SettingsItem
import com.theveloper.pixelplay.presentation.screens.SettingsSegmentedSelectorItem
import com.theveloper.pixelplay.presentation.screens.SliderSettingsItem
import com.theveloper.pixelplay.presentation.screens.SwitchSettingItem
import com.theveloper.pixelplay.presentation.screens.ThemeSelectorItem
import com.theveloper.pixelplay.presentation.viewmodel.VisualWidgetsViewModel
import kotlin.math.roundToInt

/**
 * Visual widgets: how the home-screen widgets look.
 *
 * The preview strip at the top doubles as the picker — everything below applies to whichever
 * widget is selected there, which is why there is no separate "which widget" dropdown. The
 * turntable section only appears when the turntable is selected, since none of it means
 * anything for the other four.
 *
 * This category is backed by [VisualWidgetsViewModel] rather than the shared
 * `SettingsViewModel`: nothing here reads the 58-field settings state, and keeping it out of
 * that object means opening this screen does not touch the file explorer or storage
 * enumeration the way the other categories still do.
 */
@Composable
internal fun WidgetsSettingsContent(
    viewModel: VisualWidgetsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val selectedKind by viewModel.selectedKind.collectAsStateWithLifecycle()
    val playerInfo by viewModel.playerInfo.collectAsStateWithLifecycle()

    val config = configs[selectedKind] ?: WidgetConfig(kind = selectedKind)
    val appearance = config.appearance
    val content = config.content
    val turntable = config.turntable
    val widgetName = stringResource(selectedKind.labelRes)

    SettingsSubsection(title = stringResource(R.string.settings_widgets_preview_section)) {
        WidgetPreviewStrip(
            configs = configs,
            playerInfo = playerInfo,
            selectedKind = selectedKind,
            onSelect = viewModel::selectKind,
        )
    }

    SettingsSubsection(title = stringResource(R.string.settings_widgets_appearance_section)) {
        SettingsSegmentedSelectorItem(
            title = stringResource(R.string.settings_widget_background_title),
            subtitle = stringResource(R.string.settings_widget_background_subtitle),
            options = WidgetBackgroundStyle.entries.toList(),
            selectedOption = appearance.backgroundStyle,
            optionLabel = { style: WidgetBackgroundStyle -> stringResource(style.labelRes) },
            onOptionSelected = { viewModel.setBackgroundStyle(selectedKind, it) },
            leadingIcon = {
                Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.secondary)
            },
            settingKey = "widget_background",
        )
        // The adaptive widget picks its own radius per size bucket — the 1x1 layout is a
        // circle — so a single user radius would flatten shapes that are load-bearing.
        if (selectedKind != WidgetKind.ADAPTIVE) {
            SliderSettingsItem(
                label = stringResource(R.string.settings_widget_corner_title),
                value = appearance.cornerRadiusDp.toFloat(),
                valueRange = WidgetAppearance.MIN_CORNER_RADIUS_DP.toFloat()..
                    WidgetAppearance.MAX_CORNER_RADIUS_DP.toFloat(),
                steps = 0,
                onValueChange = { viewModel.setCornerRadius(selectedKind, it.roundToInt()) },
                // valueText is a plain lambda, not a composable one, so the string is
                // resolved through the context rather than with stringResource.
                valueText = { value ->
                    context.getString(R.string.settings_widget_corner_value, value.roundToInt())
                },
                settingKey = "widget_corner",
            )
        }
        SettingsSegmentedSelectorItem(
            title = stringResource(R.string.settings_widget_accent_title),
            subtitle = stringResource(R.string.settings_widget_accent_subtitle),
            options = WidgetAccentSource.entries.toList(),
            selectedOption = appearance.accentSource,
            optionLabel = { source: WidgetAccentSource -> stringResource(source.labelRes) },
            onOptionSelected = { viewModel.setAccentSource(selectedKind, it) },
            leadingIcon = {
                Icon(Icons.Rounded.ColorLens, null, tint = MaterialTheme.colorScheme.secondary)
            },
            settingKey = "widget_accent",
        )
    }

    if (selectedKind == WidgetKind.ADAPTIVE) {
        Text(
            text = stringResource(R.string.settings_widget_corner_fixed_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }

    SettingsSubsection(title = stringResource(R.string.settings_widgets_content_section)) {
        if (selectedKind != WidgetKind.TURNTABLE) {
            SwitchSettingItem(
                title = stringResource(R.string.settings_widget_show_title),
                subtitle = "",
                checked = content.showTitle,
                onCheckedChange = { viewModel.setShowTitle(selectedKind, it) },
                leadingIcon = {
                    Icon(Icons.Rounded.Title, null, tint = MaterialTheme.colorScheme.secondary)
                },
                settingKey = "widget_show_title",
            )
            SwitchSettingItem(
                title = stringResource(R.string.settings_widget_show_artist),
                subtitle = "",
                checked = content.showArtist,
                onCheckedChange = { viewModel.setShowArtist(selectedKind, it) },
                leadingIcon = {
                    Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.secondary)
                },
                settingKey = "widget_show_artist",
            )
        }
        SwitchSettingItem(
            title = stringResource(R.string.settings_widget_show_prev_next),
            subtitle = "",
            checked = content.showPrevNext,
            onCheckedChange = { viewModel.setShowPrevNext(selectedKind, it) },
            leadingIcon = {
                Icon(Icons.Rounded.SkipNext, null, tint = MaterialTheme.colorScheme.secondary)
            },
            settingKey = "widget_show_prev_next",
        )
        SwitchSettingItem(
            title = stringResource(R.string.settings_widget_show_shuffle),
            subtitle = "",
            checked = content.showShuffle,
            onCheckedChange = { viewModel.setShowShuffle(selectedKind, it) },
            leadingIcon = {
                Icon(Icons.Rounded.Shuffle, null, tint = MaterialTheme.colorScheme.secondary)
            },
            settingKey = "widget_show_shuffle",
        )
        SwitchSettingItem(
            title = stringResource(R.string.settings_widget_show_repeat),
            subtitle = "",
            checked = content.showRepeat,
            onCheckedChange = { viewModel.setShowRepeat(selectedKind, it) },
            leadingIcon = {
                Icon(Icons.Rounded.Repeat, null, tint = MaterialTheme.colorScheme.secondary)
            },
            settingKey = "widget_show_repeat",
        )
        SwitchSettingItem(
            title = stringResource(R.string.settings_widget_show_favorite),
            subtitle = "",
            checked = content.showFavorite,
            onCheckedChange = { viewModel.setShowFavorite(selectedKind, it) },
            leadingIcon = {
                Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.secondary)
            },
            settingKey = "widget_show_favorite",
        )
        if (selectedKind != WidgetKind.TURNTABLE) {
            SettingsSegmentedSelectorItem(
                title = stringResource(R.string.settings_widget_progress_title),
                options = WidgetProgressStyle.entries.toList(),
                selectedOption = content.progressStyle,
                optionLabel = { style: WidgetProgressStyle -> stringResource(style.labelRes) },
                onOptionSelected = { viewModel.setProgressStyle(selectedKind, it) },
                leadingIcon = {
                    Icon(Icons.Rounded.Visibility, null, tint = MaterialTheme.colorScheme.secondary)
                },
                settingKey = "widget_progress",
            )
        }
    }

    if (selectedKind == WidgetKind.TURNTABLE) {
        SettingsAdvancedSection(
            title = stringResource(R.string.settings_widgets_turntable_section),
            containedKeys = TURNTABLE_KEYS,
            addBottomSpace = false,
        ) {
            ThemeSelectorItem(
                label = stringResource(R.string.settings_turntable_spin_mode_title),
                description = stringResource(turntable.spinMode.descriptionRes),
                options = WidgetSpinMode.entries.associate {
                    it.storageKey to stringResource(it.labelRes)
                },
                selectedKey = turntable.spinMode.storageKey,
                onSelectionChanged = { key ->
                    viewModel.setSpinMode(WidgetSpinMode.fromStorageKey(key))
                },
                leadingIcon = {
                    Icon(Icons.Rounded.RotateRight, null, tint = MaterialTheme.colorScheme.secondary)
                },
                settingKey = "turntable_spin_mode",
            )
            SliderSettingsItem(
                label = stringResource(R.string.settings_turntable_spin_speed_title),
                value = turntable.spinRpm,
                valueRange = TurntableOptions.MIN_SPIN_RPM..TurntableOptions.MAX_SPIN_RPM,
                onValueChange = viewModel::setSpinRpm,
                valueText = { value ->
                    context.getString(
                        R.string.settings_turntable_spin_speed_value,
                        value.roundToInt(),
                    )
                },
                settingKey = "turntable_spin_speed",
            )
            SettingsSegmentedSelectorItem(
                title = stringResource(R.string.settings_turntable_direction_title),
                options = listOf(true, false),
                selectedOption = turntable.clockwise,
                optionLabel = { clockwise: Boolean ->
                    stringResource(
                        if (clockwise) R.string.settings_turntable_clockwise
                        else R.string.settings_turntable_counter_clockwise,
                    )
                },
                onOptionSelected = viewModel::setClockwise,
                leadingIcon = {
                    Icon(Icons.Rounded.Radio, null, tint = MaterialTheme.colorScheme.secondary)
                },
                settingKey = "turntable_direction",
            )
            SliderSettingsItem(
                label = stringResource(R.string.settings_turntable_disc_scale_title),
                value = turntable.discScale,
                valueRange = TurntableOptions.MIN_DISC_SCALE..TurntableOptions.MAX_DISC_SCALE,
                onValueChange = viewModel::setDiscScale,
                valueText = { value -> context.percentText(value) },
                settingKey = "turntable_disc_size",
            )
            SliderSettingsItem(
                label = stringResource(R.string.settings_turntable_label_scale_title),
                value = turntable.labelScale,
                valueRange = TurntableOptions.MIN_LABEL_SCALE..TurntableOptions.MAX_LABEL_SCALE,
                onValueChange = viewModel::setLabelScale,
                valueText = { value -> context.percentText(value) },
                settingKey = "turntable_label_size",
            )
            SwitchSettingItem(
                title = stringResource(R.string.settings_turntable_badges_title),
                subtitle = stringResource(R.string.settings_turntable_badges_subtitle),
                checked = turntable.showBadges,
                onCheckedChange = viewModel::setShowBadges,
                leadingIcon = {
                    Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.secondary)
                },
                settingKey = "turntable_badges",
            )
            SwitchSettingItem(
                title = stringResource(R.string.settings_turntable_tonearm_title),
                subtitle = "",
                checked = turntable.showTonearm,
                onCheckedChange = viewModel::setShowTonearm,
                leadingIcon = {
                    Icon(Icons.Rounded.Timeline, null, tint = MaterialTheme.colorScheme.secondary)
                },
                settingKey = "turntable_tonearm",
            )
            SwitchSettingItem(
                title = stringResource(R.string.settings_turntable_grooves_title),
                subtitle = "",
                checked = turntable.showGrooves,
                onCheckedChange = viewModel::setShowGrooves,
                leadingIcon = {
                    Icon(Icons.Rounded.Album, null, tint = MaterialTheme.colorScheme.secondary)
                },
                settingKey = "turntable_grooves",
            )
            SwitchSettingItem(
                title = stringResource(R.string.settings_turntable_sheen_title),
                subtitle = "",
                checked = turntable.showSheen,
                onCheckedChange = viewModel::setShowSheen,
                leadingIcon = {
                    Icon(Icons.Rounded.Lightbulb, null, tint = MaterialTheme.colorScheme.secondary)
                },
                settingKey = "turntable_sheen",
            )
        }

        // Smooth spinning is the one option here with a real running cost, so say so where
        // the choice is made rather than burying it in a help page.
        if (turntable.spinMode == WidgetSpinMode.SMOOTH) {
            Text(
                text = stringResource(R.string.settings_widget_battery_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }

    SettingsSubsection(title = stringResource(R.string.settings_widget_reset_title)) {
        ActionSettingsItem(
            title = stringResource(R.string.settings_widget_reset_title),
            subtitle = stringResource(R.string.settings_widget_reset_subtitle, widgetName),
            icon = {
                Icon(Icons.Rounded.Restore, null, tint = MaterialTheme.colorScheme.secondary)
            },
            primaryActionLabel = stringResource(R.string.settings_widget_reset_confirm_action),
            onPrimaryAction = { viewModel.resetKind(selectedKind) },
            settingKey = "widget_reset",
        )
    }
}

/** Turntable rows, so the collapsed section opens itself when search points at one. */
private val TURNTABLE_KEYS = setOf(
    "turntable_spin_mode",
    "turntable_spin_speed",
    "turntable_direction",
    "turntable_disc_size",
    "turntable_label_size",
    "turntable_badges",
    "turntable_tonearm",
    "turntable_grooves",
    "turntable_sheen",
)

/** Formats a 0..1 scale as a whole percentage, e.g. 0.82 becomes "82%". */
private fun android.content.Context.percentText(value: Float): String =
    getString(R.string.settings_turntable_disc_scale_value, (value * 100f).roundToInt())
