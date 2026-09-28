package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
 * [WidgetsPinnedHeader] (big live preview + a row of widget names) stays fixed at the top;
 * everything here scrolls under it and applies to the widget picked there. Appearance comes
 * first; on/off options are icon toggles (tap the icon). The turntable section only appears
 * when the turntable is selected. Reset lives in the top bar ([WidgetResetAction]).
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

    val config = configs[selectedKind] ?: WidgetConfig(kind = selectedKind)
    val appearance = config.appearance
    val content = config.content
    val turntable = config.turntable

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
        WidgetToggleRow {
            if (selectedKind != WidgetKind.TURNTABLE) {
                WidgetIconToggle(
                    icon = Icons.Rounded.Title,
                    label = stringResource(R.string.settings_widget_show_title),
                    checked = content.showTitle,
                    onCheckedChange = { viewModel.setShowTitle(selectedKind, it) },
                    settingKey = "widget_show_title",
                )
                WidgetIconToggle(
                    icon = Icons.Rounded.Person,
                    label = stringResource(R.string.settings_widget_show_artist),
                    checked = content.showArtist,
                    onCheckedChange = { viewModel.setShowArtist(selectedKind, it) },
                    settingKey = "widget_show_artist",
                )
            }
            WidgetIconToggle(
                icon = Icons.Rounded.SkipNext,
                label = stringResource(R.string.settings_widget_show_prev_next),
                checked = content.showPrevNext,
                onCheckedChange = { viewModel.setShowPrevNext(selectedKind, it) },
                settingKey = "widget_show_prev_next",
            )
            WidgetIconToggle(
                icon = Icons.Rounded.Shuffle,
                label = stringResource(R.string.settings_widget_show_shuffle),
                checked = content.showShuffle,
                onCheckedChange = { viewModel.setShowShuffle(selectedKind, it) },
                settingKey = "widget_show_shuffle",
            )
            WidgetIconToggle(
                icon = Icons.Rounded.Repeat,
                label = stringResource(R.string.settings_widget_show_repeat),
                checked = content.showRepeat,
                onCheckedChange = { viewModel.setShowRepeat(selectedKind, it) },
                settingKey = "widget_show_repeat",
            )
            WidgetIconToggle(
                icon = Icons.Rounded.Favorite,
                label = stringResource(R.string.settings_widget_show_favorite),
                checked = content.showFavorite,
                onCheckedChange = { viewModel.setShowFavorite(selectedKind, it) },
                settingKey = "widget_show_favorite",
            )
        }
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
            WidgetToggleRow {
                WidgetIconToggle(
                    icon = Icons.Rounded.Tune,
                    label = stringResource(R.string.settings_turntable_badges_title),
                    checked = turntable.showBadges,
                    onCheckedChange = viewModel::setShowBadges,
                    settingKey = "turntable_badges",
                )
                WidgetIconToggle(
                    icon = Icons.Rounded.Timeline,
                    label = stringResource(R.string.settings_turntable_tonearm_title),
                    checked = turntable.showTonearm,
                    onCheckedChange = viewModel::setShowTonearm,
                    settingKey = "turntable_tonearm",
                )
                WidgetIconToggle(
                    icon = Icons.Rounded.Album,
                    label = stringResource(R.string.settings_turntable_grooves_title),
                    checked = turntable.showGrooves,
                    onCheckedChange = viewModel::setShowGrooves,
                    settingKey = "turntable_grooves",
                )
                WidgetIconToggle(
                    icon = Icons.Rounded.Lightbulb,
                    label = stringResource(R.string.settings_turntable_sheen_title),
                    checked = turntable.showSheen,
                    onCheckedChange = viewModel::setShowSheen,
                    settingKey = "turntable_sheen",
                )
            }
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

/** Fixed top of the Widgets page: the selected widget, large, and the row of widget names. */
@Composable
internal fun WidgetsPinnedHeader(
    modifier: Modifier = Modifier,
    viewModel: VisualWidgetsViewModel = hiltViewModel(),
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val selectedKind by viewModel.selectedKind.collectAsStateWithLifecycle()
    val playerInfo by viewModel.playerInfo.collectAsStateWithLifecycle()
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        WidgetMainPreview(
            kind = selectedKind,
            config = configs[selectedKind] ?: WidgetConfig(kind = selectedKind),
            playerInfo = playerInfo,
        )
        WidgetKindSelector(selectedKind = selectedKind, onSelect = viewModel::selectKind)
    }
}

/** Reset for the selected widget: an icon in the top bar, with a confirmation. */
@Composable
internal fun WidgetResetAction(viewModel: VisualWidgetsViewModel = hiltViewModel()) {
    val selectedKind by viewModel.selectedKind.collectAsStateWithLifecycle()
    var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val widgetName = stringResource(selectedKind.labelRes)
    androidx.compose.material3.IconButton(onClick = { confirm = true }) {
        Icon(Icons.Rounded.Restore, contentDescription = stringResource(R.string.settings_widget_reset_title))
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.Restore, null) },
            title = { Text(stringResource(R.string.settings_widget_reset_confirm_title, widgetName)) },
            text = { Text(stringResource(R.string.settings_widget_reset_confirm_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirm = false
                    viewModel.resetKind(selectedKind)
                }) { Text(stringResource(R.string.settings_widget_reset_confirm_action)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/** Icon toggles wrap onto as many lines as they need. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WidgetToggleRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) { content() }
}

/**
 * An on/off option as just its icon: filled when on, outlined when off. The label is read by
 * TalkBack and shown on long press, so the row stays compact.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WidgetIconToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    settingKey: String,
) {
    val highlighted = com.theveloper.pixelplay.presentation.screens.LocalHighlightSettingKey.current
        ?.equals(settingKey, ignoreCase = true) == true
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above
        ),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        androidx.compose.material3.FilledIconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            colors = androidx.compose.material3.IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier
                .size(56.dp)
                .then(
                    if (highlighted) Modifier.border(
                        2.dp, MaterialTheme.colorScheme.primary,
                        androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                    ) else Modifier
                )
                .semantics { stateDescription = if (checked) "Shown" else "Hidden" },
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}
