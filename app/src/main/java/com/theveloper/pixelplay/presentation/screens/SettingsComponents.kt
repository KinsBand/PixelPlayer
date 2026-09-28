package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.worker.SyncProgress
import androidx.compose.ui.unit.sp
import androidx.core.view.HapticFeedbackConstantsCompat
import com.theveloper.pixelplay.presentation.components.subcomps.TightWrapText
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults

val LocalSettingsContainerColor = androidx.compose.runtime.compositionLocalOf { androidx.compose.ui.graphics.Color.Unspecified }
val LocalHighlightSettingKey = androidx.compose.runtime.compositionLocalOf<String?> { null }

/**
 * Provided by the settings category host. A highlighted row calls this once with its
 * y position in the window so the host can scroll it into view. Null when the content
 * is not inside a scroll container that supports it.
 */
val LocalHighlightScrollRequest =
    androidx.compose.runtime.compositionLocalOf<((Float) -> Unit)?> { null }

@Composable
fun HighlightableSettingItem(
    key: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val targetKey = LocalHighlightSettingKey.current
    val isHighlighted = targetKey != null && key != null && targetKey.equals(key, ignoreCase = true)

    if (!isHighlighted) {
        // Fast path: the 50+ rows that are not the search target allocate no
        // animatable and register no position callback. The Box is kept so that
        // layout is identical whether or not a row happens to be highlighted.
        Box(modifier = modifier.fillMaxWidth()) { content() }
        return
    }

    val scrollRequest = LocalHighlightScrollRequest.current
    val highlightAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    // Guarded so the one-shot scroll cannot re-trigger from layout passes caused by
    // the scroll animation itself.
    var scrollRequested by remember(targetKey) { mutableStateOf(false) }

    LaunchedEffect(targetKey) {
        highlightAlpha.snapTo(HIGHLIGHT_PEAK_ALPHA)
        highlightAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = HIGHLIGHT_FADE_MS, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                if (!scrollRequested && scrollRequest != null) {
                    scrollRequested = true
                    scrollRequest(coordinates.positionInRoot().y)
                }
            }
            .then(
                if (highlightAlpha.value > 0.01f) {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha.value)
                        )
                } else Modifier
            )
    ) {
        content()
    }
}

private const val HIGHLIGHT_FADE_MS = 2200
private const val HIGHLIGHT_PEAK_ALPHA = 0.55f

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
            )
        }
        content()
    }
}

@Composable
fun SettingsItem(
        title: String,
        subtitle: String,
        leadingIcon: @Composable () -> Unit,
        trailingIcon: @Composable () -> Unit = {},
        trailingContent: (@Composable () -> Unit)? = null,
        settingKey: String? = null,
        onClick: () -> Unit
) {
    val containerColor = if (LocalSettingsContainerColor.current != androidx.compose.ui.graphics.Color.Unspecified) {
        LocalSettingsContainerColor.current
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val itemContent = @Composable {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                    color = containerColor,
                    modifier =
                            Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onClick)
            ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth()
                ) {
                    Box(
                            modifier = Modifier.padding(end = 16.dp).size(24.dp),
                            contentAlignment = Alignment.Center
                    ) { leadingIcon() }

                    Column(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotEmpty()) {
                            Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        (trailingContent ?: trailingIcon)()
                    }
                }
            }
            if (LocalSettingsContainerColor.current == androidx.compose.ui.graphics.Color.Transparent) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                )
            }
        }
    }

    if (settingKey != null) {
        HighlightableSettingItem(key = settingKey) { itemContent() }
    } else {
        itemContent()
    }
}

@Composable
fun SwitchSettingItem(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        leadingIcon: @Composable (() -> Unit)? = null,
        enabled: Boolean = true,
        settingKey: String? = null
) {
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current

    val containerColor = if (LocalSettingsContainerColor.current != androidx.compose.ui.graphics.Color.Unspecified) {
        LocalSettingsContainerColor.current
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val itemContent = @Composable {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                    color = containerColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        // toggleable (not clickable) so the row is a single TalkBack
                        // stop announced as a switch with its on/off state. The Switch
                        // itself takes onCheckedChange = null and is purely visual.
                        .toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Switch
                        ) { newValue ->
                            performAppCompatHapticFeedback(
                                view,
                                appHapticsConfig,
                                HapticFeedbackConstantsCompat.GESTURE_START
                            )
                            onCheckedChange(newValue)
                        }
            ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (leadingIcon != null) {
                        Box(
                                modifier = Modifier.padding(end = 4.dp).size(24.dp),
                                contentAlignment = Alignment.Center
                        ) { leadingIcon() }
                    }

                    Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color =
                                        if (enabled) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotEmpty()) {
                            Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                            if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Switch(
                        checked = checked,
                        onCheckedChange = null, // Row handles whole click smoothly
                        enabled = enabled,
                        thumbContent = {
                            AnimatedContent(
                                targetState = checked,
                                transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                                label = "switch_thumb_icon"
                            ) { isChecked ->
                                Icon(
                                    imageVector = if (isChecked) Icons.Rounded.Check else Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedIconColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
            if (LocalSettingsContainerColor.current == androidx.compose.ui.graphics.Color.Transparent) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                )
            }
        }
    }

    if (settingKey != null) {
        HighlightableSettingItem(key = settingKey) { itemContent() }
    } else {
        itemContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectorItem(
        label: String,
        description: String,
        options: Map<String, String>,
        selectedKey: String,
        onSelectionChanged: (String) -> Unit,
        leadingIcon: @Composable () -> Unit,
        settingKey: String? = null
) {
    var showSheet by remember { mutableStateOf(false) }
    val selectedOption = options[selectedKey] ?: selectedKey

    val containerColor = if (LocalSettingsContainerColor.current != androidx.compose.ui.graphics.Color.Unspecified) {
        LocalSettingsContainerColor.current
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val itemContent = @Composable {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                    color = containerColor,
                    modifier =
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                showSheet = true
                            }
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.padding(end = 16.dp).size(24.dp),
                                contentAlignment = Alignment.Center
                        ) { leadingIcon() }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            // Selected Value Badge
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                modifier = Modifier.align(Alignment.Start)
                            ) {
                                Text(
                                     text = selectedOption,
                                     style = MaterialTheme.typography.labelMedium,
                                     color = MaterialTheme.colorScheme.primary,
                                     fontWeight = FontWeight.Bold,
                                     modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (LocalSettingsContainerColor.current == androidx.compose.ui.graphics.Color.Transparent) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                )
            }
        }
    }

    if (settingKey != null) {
        HighlightableSettingItem(key = settingKey) { itemContent() }
    } else {
        itemContent()
    }

    if (showSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
            Column(modifier = Modifier.padding(bottom = 28.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    fontWeight = FontWeight.Bold
                )
                
                val maxSheetHeight = if (options.size <= 3) 240.dp else 420.dp
                LazyColumn(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .heightIn(max = maxSheetHeight),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(options.entries.toList()) { (key, optionLabel) ->
                        val isSelected = key == selectedKey
                        val itemBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                        val itemFg = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        
                        Surface(
                            onClick = {
                                onSelectionChanged(key)
                                showSheet = false
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = itemBg,
                            modifier = Modifier.fillMaxWidth().height(64.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = optionLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = itemFg,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.common_selected),
                                        tint = itemFg
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsSegmentedSelectorItem(
    title: String,
    subtitle: String? = null,
    options: List<T>,
    selectedOption: T,
    optionLabel: @Composable (T) -> String,
    onOptionSelected: (T) -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null,
    settingKey: String? = null
) {
    val containerColor = if (LocalSettingsContainerColor.current != androidx.compose.ui.graphics.Color.Unspecified) {
        LocalSettingsContainerColor.current
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current

    val itemContent = @Composable {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                color = containerColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (leadingIcon != null) {
                            Box(
                                modifier = Modifier.padding(end = 16.dp).size(24.dp),
                                contentAlignment = Alignment.Center
                            ) { leadingIcon() }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!subtitle.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        options.forEachIndexed { index, option ->
                            val isSelected = option == selectedOption
                            SegmentedButton(
                                selected = isSelected,
                                onClick = {
                                    if (!isSelected) {
                                        performAppCompatHapticFeedback(
                                            view,
                                            appHapticsConfig,
                                            HapticFeedbackConstantsCompat.GESTURE_START
                                        )
                                        onOptionSelected(option)
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(
                                    text = optionLabel(option),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            if (LocalSettingsContainerColor.current == androidx.compose.ui.graphics.Color.Transparent) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                )
            }
        }
    }

    if (settingKey != null) {
        HighlightableSettingItem(key = settingKey) { itemContent() }
    } else {
        itemContent()
    }
}

@Composable
fun SliderSettingsItem(
        label: String,
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int = 0,
        onValueChange: (Float) -> Unit,
        onValueChangeFinished: (() -> Unit)? = null,
        valueText: (Float) -> String,
        settingKey: String? = null,
        presets: List<Float>? = null
) {
    val containerColor = if (LocalSettingsContainerColor.current != androidx.compose.ui.graphics.Color.Unspecified) {
        LocalSettingsContainerColor.current
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    var internalValue by remember(value) { mutableStateOf(value) }

    val itemContent = @Composable {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                    color = containerColor,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            Text(
                                    text = valueText(internalValue),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    softWrap = false
                            )
                        }
                    }

                    if (!presets.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            presets.forEach { presetVal ->
                                val isSelected = (internalValue - presetVal).let { kotlin.math.abs(it) < 0.01f }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        performAppCompatHapticFeedback(
                                            view,
                                            appHapticsConfig,
                                            HapticFeedbackConstantsCompat.CLOCK_TICK
                                        )
                                        internalValue = presetVal
                                        onValueChange(presetVal)
                                        onValueChangeFinished?.invoke()
                                    },
                                    label = { Text(valueText(presetVal), style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    Slider(
                        value = internalValue,
                        onValueChange = { newValue ->
                            if (steps > 0 && internalValue != newValue) {
                                performAppCompatHapticFeedback(
                                    view,
                                    appHapticsConfig,
                                    HapticFeedbackConstantsCompat.CLOCK_TICK
                                )
                            }
                            internalValue = newValue
                            onValueChange(newValue)
                        },
                        onValueChangeFinished = onValueChangeFinished,
                        valueRange = valueRange,
                        steps = steps,
                        // Without these, TalkBack reads the slider as a bare percentage
                        // with no idea which setting it belongs to or what the unit is.
                        modifier = Modifier.semantics {
                            contentDescription = label
                            stateDescription = valueText(internalValue)
                        }
                    )
                }
            }
            if (LocalSettingsContainerColor.current == androidx.compose.ui.graphics.Color.Transparent) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                )
            }
        }
    }

    if (settingKey != null) {
        HighlightableSettingItem(key = settingKey) { itemContent() }
    } else {
        itemContent()
    }
}

@Composable
fun RefreshLibraryItem(
        isSyncing: Boolean,
        syncProgress: SyncProgress,
        activeOperationLabel: String? = null,
        onFullSync: () -> Unit,
        onRebuild: () -> Unit,
        settingKey: String? = null
) {
    HighlightableSettingItem(key = settingKey) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier = Modifier.padding(end = 16.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                            text = stringResource(R.string.settings_refresh_library_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            text = stringResource(R.string.settings_refresh_library_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            // Full Rescan button
            FilledTonalButton(
                    onClick = onFullSync,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TightWrapText(
                        text = stringResource(R.string.settings_action_full_rescan),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                        lineHeight = 22.sp,
                    )
                }
            }
             
            Spacer(modifier = Modifier.height(8.dp))
            
            // Rebuild Database button - full width, destructive action
            OutlinedButton(
                    onClick = onRebuild,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TightWrapText(
                        text = stringResource(R.string.settings_action_rebuild_database),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                        lineHeight = 22.sp,
                    )
                }
            }

            if (isSyncing) {
                Spacer(modifier = Modifier.height(12.dp))
                val phaseLabel = activeOperationLabel ?: syncPhaseLabel(syncProgress.phase)
                if (syncProgress.hasProgress) {
                    LinearProgressIndicator(
                            progress = { syncProgress.progress },
                            modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = stringResource(
                                R.string.settings_sync_progress_detailed,
                                phaseLabel,
                                (syncProgress.progress * 100).toInt(),
                                syncProgress.currentCount,
                                syncProgress.totalCount
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = stringResource(
                                R.string.settings_sync_progress_indeterminate,
                                phaseLabel
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun syncPhaseLabel(phase: SyncProgress.SyncPhase): String =
        stringResource(
                when (phase) {
                    SyncProgress.SyncPhase.IDLE -> R.string.settings_sync_phase_preparing
                    SyncProgress.SyncPhase.FETCHING_MEDIASTORE ->
                            R.string.settings_sync_phase_reading_mediastore
                    SyncProgress.SyncPhase.PROCESSING_FILES ->
                            R.string.settings_sync_phase_reading_processing_tracks
                    SyncProgress.SyncPhase.SAVING_TO_DATABASE ->
                            R.string.settings_sync_phase_saving_db
                    SyncProgress.SyncPhase.SCANNING_LRC -> R.string.settings_sync_phase_scanning_lrc
                    SyncProgress.SyncPhase.CLEANING_CACHE ->
                            R.string.settings_sync_phase_cleaning_cache
                    SyncProgress.SyncPhase.SYNCING_CLOUD ->
                            R.string.settings_sync_phase_syncing_cloud
                    SyncProgress.SyncPhase.COMPLETING -> R.string.settings_sync_phase_completing
                }
        )

@Composable
fun ActionSettingsItem(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    enabled: Boolean = true,
    settingKey: String? = null
) {
    HighlightableSettingItem(key = settingKey) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.padding(end = 16.dp).size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }

                Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Action
            FilledTonalButton(
                onClick = onPrimaryAction,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(primaryActionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // Secondary Action (Optional)
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSecondaryAction,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(secondaryActionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    }
}

@Composable
fun AiSystemPromptItem(
    systemPrompt: String,
    defaultPrompt: String,
    onSystemPromptSave: (String) -> Unit,
    onReset: () -> Unit,
    title: String,
    subtitle: String
) {
    var localPrompt by remember(systemPrompt) { mutableStateOf(systemPrompt) }
    val hasChanges = localPrompt != systemPrompt
    val isDefault = systemPrompt == defaultPrompt
    var showSaved by remember { mutableStateOf(false) }
    val presets = listOf(
        stringResource(R.string.settings_preset_professional_curator_name) to
            stringResource(R.string.settings_preset_professional_curator_prompt),
        stringResource(R.string.settings_preset_creative_maverick_name) to
            stringResource(R.string.settings_preset_creative_maverick_prompt),
        stringResource(R.string.settings_preset_strict_librarian_name) to
            stringResource(R.string.settings_preset_strict_librarian_prompt),
        stringResource(R.string.settings_preset_atmospheric_guide_name) to
            stringResource(R.string.settings_preset_atmospheric_guide_prompt),
        stringResource(R.string.settings_preset_sonic_enthusiast_name) to
            stringResource(R.string.settings_preset_sonic_enthusiast_prompt),
        stringResource(R.string.settings_preset_energy_catalyst_name) to
            stringResource(R.string.settings_preset_energy_catalyst_prompt)
    )

    LaunchedEffect(showSaved) {
        if (showSaved) {
            kotlinx.coroutines.delay(2000)
            showSaved = false
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_preset_prompts),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    OutlinedButton(
                        onClick = { 
                            localPrompt = preset.second
                        },
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Text(text = preset.first, maxLines = 1)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = localPrompt,
                onValueChange = { localPrompt = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp),
                placeholder = { Text(stringResource(R.string.settings_system_prompt_placeholder)) },
                minLines = 3,
                maxLines = 6
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        onSystemPromptSave(localPrompt)
                        showSaved = true
                    },
                    enabled = hasChanges
                ) {
                    Text(stringResource(R.string.common_save), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!isDefault) {
                    OutlinedButton(onClick = {
                        onReset()
                    }) {
                        Text(stringResource(R.string.common_reset), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (showSaved) {
                    Text(
                        text = stringResource(R.string.common_saved),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
