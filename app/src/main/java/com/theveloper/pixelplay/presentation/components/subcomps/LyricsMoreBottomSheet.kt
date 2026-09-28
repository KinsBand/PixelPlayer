package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.Abc
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.theveloper.pixelplay.R
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import com.theveloper.pixelplay.presentation.components.editLyricsDisplayPrefs
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.collectAsState
import com.theveloper.pixelplay.data.preferences.dataStore
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.presentation.components.ToggleSegmentButton
import com.theveloper.pixelplay.presentation.components.player.BottomToggleRow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsMoreBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    lyrics: Lyrics?,
    showSyncedLyrics: Boolean,
    isSyncControlsVisible: Boolean,
    highlightMode: com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode,
    onHighlightModeChange: (com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode) -> Unit,
    onResetAllLyrics: () -> Unit,
    onSaveLyricsAsLrc: () -> Unit,
    onResetImportedLyrics: () -> Unit,
    onTranslateViaAi: () -> Unit,
    onToggleSyncControls: () -> Unit,
    isImmersiveTemporarilyDisabled: Boolean,
    onSetImmersiveTemporarilyDisabled: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    lyricsAlignment: String,
    onLyricsAlignmentChange: (String) -> Unit,
    hasTranslatedLyrics: Boolean,
    hasRomanizedLyrics: Boolean,
    showTranslation: Boolean,
    showRomanization: Boolean,
    onShowTranslationChange: (Boolean) -> Unit,
    onShowRomanizationChange: (Boolean) -> Unit,
    immersiveLyricsEnabled: Boolean,
    /** Show the Immersive switch (synced lyrics page, or the Instruments page). */
    showImmersiveToggle: Boolean = showSyncedLyrics,
    // BottomToggleRow params
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    // Colors
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onAccentColor: Color = MaterialTheme.colorScheme.onPrimary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    onTertiaryColor: Color = MaterialTheme.colorScheme.onTertiary,
    /** Guitar / drum page options; shown first (before the lyrics group) when set. */
    tabOptions: (@Composable () -> Unit)? = null,
    /** Opens the editor to write / time lyrics by hand. */
    onWriteCustomLyrics: () -> Unit = {},
    /** Opens the lyrics search. */
    onFindLyrics: () -> Unit = {}
) {
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var showResetDialog by remember { mutableStateOf(false) }
    var showLyricsActions by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    val settingsViewModel: com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
        dragHandle = { BottomSheetDefaults.DragHandle(color = contentColor.copy(alpha = 0.35f)) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        contentWindowInsets = { WindowInsets(top = 0, bottom = 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp + navigationBarsPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val itemBackgroundColor = contentColor.copy(alpha = 0.08f)

            tabOptions?.invoke()

            // Lyrics Actions Group
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier
                        .padding(start = 6.dp, bottom = 2.dp),
                    text = stringResource(R.string.lyrics_title),
                    color = accentColor,
                    style = MaterialTheme.typography.bodyLargeEmphasized
                )

                // Row 1: Manage lyrics + Keep screen on
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LyricsOptionButton(
                        modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Rounded.Abc, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        text = "Manage lyrics",
                        onClick = { showLyricsActions = true },
                        inactiveContainerColor = itemBackgroundColor,
                        inactiveContentColor = contentColor
                    )
                    LyricsOptionButton(
                        modifier = Modifier.weight(1f),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.BrightnessHigh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        text = stringResource(R.string.lyrics_controls_keep_screen_on),
                        isActive = keepScreenOn,
                        activeContainerColor = accentColor,
                        activeContentColor = onAccentColor,
                        inactiveContainerColor = itemBackgroundColor,
                        inactiveContentColor = contentColor,
                        onClick = { onKeepScreenOnChange(!keepScreenOn) }
                    )
                }
                // Row 2: Highlighting (one button, cycles modes) and Alignment side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val highlightModes = com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode.entries
                    LyricsOptionButton(
                        modifier = Modifier.weight(1f),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Highlight,
                                contentDescription = "Highlighting",
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        text = highlightModeShortLabel(highlightMode),
                        onClick = {
                            val next = highlightModes[(highlightModes.indexOf(highlightMode) + 1) % highlightModes.size]
                            onHighlightModeChange(next)
                        },
                        inactiveContainerColor = itemBackgroundColor,
                        inactiveContentColor = contentColor
                    )

                    val (alignmentIcon, alignmentLabel) = when (lyricsAlignment) {
                        "left" -> Icons.AutoMirrored.Rounded.FormatAlignLeft to stringResource(R.string.lyrics_align_left)
                        "center" -> Icons.Rounded.FormatAlignCenter to stringResource(R.string.lyrics_align_center)
                        "right" -> Icons.AutoMirrored.Rounded.FormatAlignRight to stringResource(R.string.lyrics_align_right)
                        else -> Icons.AutoMirrored.Rounded.FormatAlignLeft to stringResource(R.string.lyrics_align_left)
                    }

                    // Alignment single button cycling through left -> center -> right -> left
                    LyricsOptionButton(
                        modifier = Modifier.weight(1f),
                        icon = {
                            Icon(
                                imageVector = alignmentIcon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        text = alignmentLabel,
                        onClick = {
                            val nextAlignment = when (lyricsAlignment) {
                                "left" -> "center"
                                "center" -> "right"
                                else -> "left"
                            }
                            onLyricsAlignmentChange(nextAlignment)
                        },
                        inactiveContainerColor = itemBackgroundColor,
                        inactiveContentColor = contentColor
                    )
                }
            }

            if (showLyricsActions) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showLyricsActions = false },
                    title = { Text("Manage lyrics") },
                    text = {
                        Column {
                            androidx.compose.material3.TextButton(onClick = {
                                showLyricsActions = false
                                onDismissRequest()
                                onWriteCustomLyrics()
                            }) { Text(if (lyrics == null) "Write your own lyrics" else "Edit lyrics & timings") }
                            androidx.compose.material3.TextButton(onClick = {
                                showLyricsActions = false
                                onDismissRequest()
                                onFindLyrics()
                            }) { Text("Search for lyrics") }
                            androidx.compose.material3.TextButton(enabled = lyrics != null, onClick = {
                                showLyricsActions = false
                                onDismissRequest()
                                onSaveLyricsAsLrc()
                            }) { Text("Save lyrics") }
                            androidx.compose.material3.TextButton(enabled = lyrics != null, onClick = {
                                showLyricsActions = false
                                onDismissRequest()
                                onTranslateViaAi()
                            }) { Text("Translate lyrics") }
                            androidx.compose.material3.TextButton(onClick = {
                                showLyricsActions = false
                                showResetDialog = true
                            }) { Text("Reset this song’s lyrics") }
                        }
                    },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { showLyricsActions = false }) { Text("Done") } }
                )
            }
            if (showResetDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text(stringResource(R.string.lyrics_reset_dialog_title)) },
                    text = { Text(stringResource(R.string.lyrics_reset_dialog_message)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showResetDialog = false
                                onDismissRequest()
                                onResetImportedLyrics()
                            }
                        ) {
                            Text(stringResource(R.string.common_reset), color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showResetDialog = false }
                        ) {
                            Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            }

            // Control Settings Group
            val isSyncVisible = showSyncedLyrics
            val isRomanizationVisible = hasRomanizedLyrics
            val isTranslationVisible = hasTranslatedLyrics
            // Always offered with synced lyrics, so immersive (auto-hide) can be turned on or off here.
            val isImmersiveVisible = showImmersiveToggle

            if (isSyncVisible || isRomanizationVisible || isTranslationVisible || isImmersiveVisible) {
                // Determine first and last items for rounding
                val isRomanizationFirst = isRomanizationVisible && !isSyncVisible
                val isTranslationFirst = isTranslationVisible && !isSyncVisible && !isRomanizationVisible
                val isImmersiveFirst = isImmersiveVisible && !isSyncVisible && !isRomanizationVisible && !isTranslationVisible

                val isSyncLast = isSyncVisible && !isRomanizationVisible && !isTranslationVisible && !isImmersiveVisible
                val isRomanizationLast = isRomanizationVisible && !isTranslationVisible && !isImmersiveVisible
                val isTranslationLast = isTranslationVisible && !isImmersiveVisible
                val isImmersiveLast = isImmersiveVisible

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        modifier = Modifier
                            .padding(start = 6.dp, bottom = 6.dp),
                        text = stringResource(R.string.lyrics_controls_section),
                        color = accentColor,
                        style = MaterialTheme.typography.bodyLargeEmphasized
                    )

                    if (isSyncVisible) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    if (isSyncControlsVisible) {
                                        stringResource(R.string.lyrics_controls_hide_sync)
                                    } else {
                                        stringResource(R.string.lyrics_controls_adjust_sync)
                                    }
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (isSyncLast) 18.dp else 8.dp,
                                        bottomEnd = if (isSyncLast) 18.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable {
                                    onDismissRequest()
                                    onToggleSyncControls()
                                },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    if (isRomanizationVisible) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lyrics_controls_show_romanization)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Abc,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = showRomanization,
                                    onCheckedChange = onShowRomanizationChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = if (isRomanizationFirst) 18.dp else 8.dp,
                                        topEnd = if (isRomanizationFirst) 18.dp else 8.dp,
                                        bottomStart = if (isRomanizationLast) 18.dp else 8.dp,
                                        bottomEnd = if (isRomanizationLast) 18.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable { onShowRomanizationChange(!showRomanization) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    if (isTranslationVisible) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.lyrics_controls_show_translations)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Translate,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = showTranslation,
                                    onCheckedChange = onShowTranslationChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = if (isTranslationFirst) 18.dp else 8.dp,
                                        topEnd = if (isTranslationFirst) 18.dp else 8.dp,
                                        bottomStart = if (isTranslationLast) 18.dp else 8.dp,
                                        bottomEnd = if (isTranslationLast) 18.dp else 8.dp
                                    )
                                )
                                .background(itemBackgroundColor)
                                .clickable { onShowTranslationChange(!showTranslation) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                    }

                    // Immersive Mode Toggle
                    if (isImmersiveVisible) {
                        ListItem(
                            headlineContent = { Text("Immersive") },
                            supportingContent = {
                                Text(
                                    when {
                                        !immersiveLyricsEnabled -> "Off: the bottom controls always stay on screen"
                                        settingsState.immersiveLyricsTimeout <= com.theveloper.pixelplay.presentation.components.IMMERSIVE_TIMEOUT_OFF ->
                                            "Manual: swipe the controls down to hide them, swipe up from the bottom to bring them back"
                                        else -> "Hides the bottom controls after ${settingsState.immersiveLyricsTimeout / 1000} s. Swipe up or tap to bring them back"
                                    },
                                    color = contentColor.copy(alpha = 0.6f)
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.VisibilityOff,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    modifier = Modifier,
                                    checked = immersiveLyricsEnabled && !isImmersiveTemporarilyDisabled,
                                    onCheckedChange = { on ->
                                        settingsViewModel.setImmersiveLyricsEnabled(on)
                                        onSetImmersiveTemporarilyDisabled(false)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = onAccentColor,
                                        checkedTrackColor = accentColor,
                                        uncheckedThumbColor = contentColor,
                                        uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = if (isImmersiveFirst) 18.dp else 8.dp,
                                        topEnd = if (isImmersiveFirst) 18.dp else 8.dp,
                                        bottomStart = 18.dp,
                                        bottomEnd = 18.dp
                                    )
                                )
                                .background(itemBackgroundColor),
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = contentColor,
                                leadingIconColor = contentColor
                            )
                        )
                        // Only there while immersive is on, so it hangs off it as a branch.
                        androidx.compose.animation.AnimatedVisibility(visible = immersiveLyricsEnabled && !isImmersiveTemporarilyDisabled) {
                            SkillTreeBranch(depth = 1, isLast = true, lineColor = accentColor) {
                                ImmersiveDelayRow(
                                    selectedMs = settingsState.immersiveLyricsTimeout,
                                    onSelect = { settingsViewModel.setImmersiveLyricsTimeout(it) },
                                    itemBackgroundColor = itemBackgroundColor,
                                    contentColor = contentColor,
                                    accentColor = accentColor
                                )
                            }
                        }
                    }
                }
            }

            // Sources + Display, folded into one "Advanced settings" dropdown styled like the rest
            // of this menu (they used to be the full Settings-screen rows dropped in here).
            LyricsAdvancedSettings(
                expanded = showAdvanced,
                onExpandedChange = { showAdvanced = it },
                settingsViewModel = settingsViewModel,
                uiState = settingsState,
                onResetAllLyrics = onResetAllLyrics,
                itemBackgroundColor = itemBackgroundColor,
                contentColor = contentColor,
                accentColor = accentColor,
                onAccentColor = onAccentColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Playback Options
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .padding(vertical = 0.dp, horizontal = 0.dp)
            ) {
                 BottomToggleRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .padding(horizontal = 20.dp),
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    isFavoriteProvider = isFavoriteProvider,
                    onShuffleToggle = onShuffleToggle,
                    onRepeatToggle = onRepeatToggle,
                    onFavoriteToggle = onFavoriteToggle,
                    containerColor = itemBackgroundColor,
                    inactiveColor = itemBackgroundColor,
                    activeColorMain = accentColor,
                    onActiveColorMain = onAccentColor
                )
            }
        }
    }
}

@Composable
private fun LyricsOptionButton(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isActive: Boolean = false,
    activeContainerColor: Color = MaterialTheme.colorScheme.primary,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    inactiveContainerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    inactiveContentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = RoundedCornerShape(18.dp)
) {
    val targetBgColor = if (isActive) activeContainerColor else inactiveContainerColor
    val targetContentColor = if (isActive) activeContentColor else inactiveContentColor

    val bgColor by animateColorAsState(
        targetValue = if (enabled) targetBgColor else targetBgColor.copy(alpha = 0.4f),
        animationSpec = tween(durationMillis = 200),
        label = "tileBgColor"
    )
    val contentColorAnimated by animateColorAsState(
        targetValue = if (enabled) targetContentColor else targetContentColor.copy(alpha = 0.38f),
        animationSpec = tween(durationMillis = 200),
        label = "tileContentColor"
    )

    Row(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColorAnimated
        ) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColorAnimated,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun highlightModeShortLabel(mode: com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode): String = when (mode) {
    com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode.AUTO -> "Highlight: Auto"
    com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode.WORD -> "Highlight: Word"
    com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode.PHONEME -> "Highlight: Letter"
    com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode.LINE -> "Highlight: Line"
}

/** One row of the grouped lists in the Advanced dropdown. */
private class AdvancedRow(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String? = null,
    val enabled: Boolean = true,
    /** Switch rows. */
    val checked: Boolean? = null,
    val onCheckedChange: ((Boolean) -> Unit)? = null,
    /** Choice rows: key -> label. */
    val options: List<Pair<String, String>> = emptyList(),
    val selectedKey: String? = null,
    val onSelect: ((String) -> Unit)? = null,
    /** Plain action rows. */
    val onClick: (() -> Unit)? = null,
    /**
     * 0 = a normal row. 1+ = a setting that only shows once its parent is on; drawn indented
     * with a skill-tree branch back to the parent.
     */
    val depth: Int = 0,
    /** Slider rows (e.g. blur strength). */
    val sliderValue: Float? = null,
    val sliderRange: ClosedFloatingPointRange<Float> = 0f..1f,
    val onSliderChange: ((Float) -> Unit)? = null
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsAdvancedSettings(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    settingsViewModel: com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel,
    uiState: com.theveloper.pixelplay.presentation.viewmodel.SettingsUiState,
    onResetAllLyrics: () -> Unit,
    itemBackgroundColor: Color,
    contentColor: Color,
    accentColor: Color,
    onAccentColor: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val displayPrefs by com.theveloper.pixelplay.presentation.components.rememberLyricsDisplayPrefs()
    var confirmResetAll by remember { mutableStateOf(false) }
    fun editPrefs(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch { context.editLyricsDisplayPrefs(block) }
    }
    val wordSourcePrefs by remember(context) {
        context.applicationContext.dataStore.data.map { prefs ->
            Triple(
                prefs[com.theveloper.pixelplay.data.network.lyrics.wordsync.WordLyricsPrefs.WORD_SOURCES_ENABLED] ?: true,
                prefs[com.theveloper.pixelplay.data.network.lyrics.wordsync.WordLyricsPrefs.MUSIXMATCH_ENABLED] ?: false,
                prefs[com.theveloper.pixelplay.data.network.lyrics.wordsync.WordLyricsPrefs.UNISON_ENABLED] ?: true
            )
        }
    }.collectAsState(initial = Triple(true, false, true))
    val chevronRotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "advancedChevron"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header — same shape and colours as the option buttons above.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (expanded) accentColor.copy(alpha = 0.16f) else itemBackgroundColor)
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Advanced settings",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = contentColor,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
            )
        }

        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AdvancedGroup(
                    title = stringResource(R.string.settings_lyrics_sources_section),
                    rows = buildList {
                        add(AdvancedRow(
                            icon = Icons.Rounded.LibraryMusic,
                            title = stringResource(R.string.settings_lyrics_source_priority_title),
                            options = listOf(
                                com.theveloper.pixelplay.data.model.LyricsSourcePreference.EMBEDDED_FIRST.name to stringResource(R.string.settings_lyrics_embedded_first),
                                com.theveloper.pixelplay.data.model.LyricsSourcePreference.API_FIRST.name to stringResource(R.string.settings_lyrics_online_first),
                                com.theveloper.pixelplay.data.model.LyricsSourcePreference.LOCAL_FIRST.name to stringResource(R.string.settings_lyrics_local_first)
                            ),
                            selectedKey = uiState.lyricsSourcePreference.name,
                            onSelect = { key ->
                                settingsViewModel.setLyricsSourcePreference(
                                    com.theveloper.pixelplay.data.model.LyricsSourcePreference.fromName(key)
                                )
                            }
                        ))
                        add(AdvancedRow(
                            icon = Icons.Rounded.Cloud,
                            title = stringResource(R.string.settings_lyrics_lrclib_title),
                            checked = uiState.lyricsIntegrationEnabled,
                            onCheckedChange = { settingsViewModel.setLyricsIntegrationEnabled(it) }
                        ))
                        // Online-only sources: they branch off online lyrics.
                        if (uiState.lyricsIntegrationEnabled) {
                            add(AdvancedRow(
                                icon = Icons.Rounded.Highlight,
                                title = "Word-synced sources",
                                subtitle = "QQ Music, NetEase, Kugou and the AMLL lyrics database",
                                checked = wordSourcePrefs.first,
                                onCheckedChange = { enabled ->
                                    editPrefs { it[com.theveloper.pixelplay.data.network.lyrics.wordsync.WordLyricsPrefs.WORD_SOURCES_ENABLED] = enabled }
                                },
                                depth = 1
                            ))
                            add(AdvancedRow(
                                icon = Icons.Rounded.Cloud,
                                title = "Unison community lyrics",
                                subtitle = "Word and line synced, matched to the exact YouTube video when there is one",
                                checked = wordSourcePrefs.third,
                                onCheckedChange = { enabled ->
                                    editPrefs { it[com.theveloper.pixelplay.data.network.lyrics.wordsync.WordLyricsPrefs.UNISON_ENABLED] = enabled }
                                },
                                depth = 1
                            ))
                            add(AdvancedRow(
                                icon = Icons.Rounded.Abc,
                                title = "Musixmatch word sync",
                                subtitle = "Most English songs. Unofficial and rate limited, so it can stop working",
                                checked = wordSourcePrefs.second,
                                onCheckedChange = { enabled ->
                                    editPrefs { it[com.theveloper.pixelplay.data.network.lyrics.wordsync.WordLyricsPrefs.MUSIXMATCH_ENABLED] = enabled }
                                },
                                depth = 1
                            ))
                        }
                        add(AdvancedRow(
                            icon = Icons.Rounded.Folder,
                            title = stringResource(R.string.settings_auto_scan_lrc_title),
                            checked = uiState.autoScanLrcFiles,
                            onCheckedChange = { settingsViewModel.setAutoScanLrcFiles(it) }
                        ))
                        add(AdvancedRow(
                            icon = Icons.Rounded.ClearAll,
                            title = stringResource(R.string.settings_reset_imported_lyrics_title),
                            onClick = { confirmResetAll = true }
                        ))
                    },
                    itemBackgroundColor = itemBackgroundColor,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    onAccentColor = onAccentColor
                )

                val immersive = uiState.immersiveLyricsEnabled
                AdvancedGroup(
                    title = stringResource(R.string.settings_lyrics_display_section),
                    rows = buildList {
                        add(AdvancedRow(
                            icon = Icons.Rounded.Album,
                            title = stringResource(R.string.settings_cover_lyrics_title),
                            checked = displayPrefs.coverLyricsEnabled,
                            onCheckedChange = { enabled -> editPrefs { it[com.theveloper.pixelplay.presentation.components.LyricsDisplayPrefKeys.COVER_LYRICS_ENABLED] = enabled } }
                        ))
                        add(AdvancedRow(
                            icon = Icons.Rounded.ViewAgenda,
                            title = "Song structure",
                            checked = displayPrefs.showSongStructure,
                            onCheckedChange = { enabled -> editPrefs { it[com.theveloper.pixelplay.presentation.components.LyricsDisplayPrefKeys.SHOW_SONG_STRUCTURE] = enabled } }
                        ))
                        add(AdvancedRow(
                            icon = Icons.Rounded.Fullscreen,
                            title = stringResource(R.string.settings_immersive_lyrics_title),
                            checked = immersive,
                            onCheckedChange = { settingsViewModel.setImmersiveLyricsEnabled(it) }
                        ))
                        if (immersive) {
                            add(AdvancedRow(
                                icon = Icons.Rounded.Timer,
                                title = "Immersive delay",
                                subtitle = if (uiState.immersiveLyricsTimeout <= com.theveloper.pixelplay.presentation.components.IMMERSIVE_TIMEOUT_OFF) "Off: swipe down to hide, swipe up to show" else null,
                                options = immersiveDelayOptions(),
                                selectedKey = uiState.immersiveLyricsTimeout.toString(),
                                onSelect = { settingsViewModel.setImmersiveLyricsTimeout(it.toLong()) },
                                depth = 1
                            ))
                            add(AdvancedRow(
                                icon = Icons.Rounded.ScreenRotation,
                                title = "Face-to-face lyrics",
                                checked = displayPrefs.splitFaceView,
                                onCheckedChange = { enabled -> editPrefs { it[com.theveloper.pixelplay.presentation.components.LyricsDisplayPrefKeys.SPLIT_FACE_VIEW] = enabled } },
                                depth = 1
                            ))
                        }
                        // Expressive typography sits beside Immersive: one switch, and while it's
                        // on, the font / size / weight / spacing choices hang under it.
                        add(AdvancedRow(
                            icon = Icons.Rounded.TextFields,
                            title = stringResource(R.string.settings_lyrics_expressive_typography_title),
                            subtitle = if (!displayPrefs.expressiveTypography) stringResource(R.string.settings_lyrics_expressive_typography_subtitle) else null,
                            checked = displayPrefs.expressiveTypography,
                            onCheckedChange = { enabled -> editPrefs { it[com.theveloper.pixelplay.presentation.components.LyricsDisplayPrefKeys.EXPRESSIVE_TYPOGRAPHY] = enabled } }
                        ))
                        if (displayPrefs.expressiveTypography) {
                            add(AdvancedRow(
                                icon = Icons.Rounded.AutoAwesomeMotion,
                                title = stringResource(R.string.settings_lyrics_adaptive_typography_title),
                                subtitle = stringResource(R.string.settings_lyrics_adaptive_typography_subtitle),
                                checked = displayPrefs.adaptiveTypography,
                                onCheckedChange = { enabled -> editPrefs { it[com.theveloper.pixelplay.presentation.components.LyricsDisplayPrefKeys.ADAPTIVE_TYPOGRAPHY] = enabled } },
                                depth = 1
                            ))
                            add(AdvancedRow(
                                icon = Icons.Rounded.TextFields,
                                title = "Font",
                                options = listOf(
                                    com.theveloper.pixelplay.presentation.components.LyricsFont.SYSTEM.key to stringResource(R.string.settings_lyrics_font_system),
                                    com.theveloper.pixelplay.presentation.components.LyricsFont.GOOGLE_SANS_ROUNDED.key to stringResource(R.string.settings_lyrics_font_google_sans_rounded),
                                    com.theveloper.pixelplay.presentation.components.LyricsFont.GOOGLE_SANS_FLEX.key to stringResource(R.string.settings_lyrics_font_google_sans_flex),
                                    com.theveloper.pixelplay.presentation.components.LyricsFont.ROBOTO_FLEX.key to stringResource(R.string.settings_lyrics_font_roboto_flex),
                                    com.theveloper.pixelplay.presentation.components.LyricsFont.MONTSERRAT.key to stringResource(R.string.settings_lyrics_font_montserrat)
                                ),
                                selectedKey = displayPrefs.chosenFont.key,
                                onSelect = { key -> editPrefs { it[com.theveloper.pixelplay.presentation.components.LyricsDisplayPrefKeys.FONT] = com.theveloper.pixelplay.presentation.components.LyricsFont.fromKey(key).key } },
                                depth = 1
                            ))
                            add(AdvancedRow(
                                icon = Icons.Rounded.FormatSize,
                                title = "Size",
                                options = listOf(
                                    com.theveloper.pixelplay.presentation.components.LyricsTextSize.SMALL.key to stringResource(R.string.settings_lyrics_size_small),
                                    com.theveloper.pixelplay.presentation.components.LyricsTextSize.MEDIUM.key to stringResource(R.string.settings_lyrics_size_medium),
                                    com.theveloper.pixelplay.presentation.components.LyricsTextSize.LARGE.key to stringResource(R.string.settings_lyrics_size_large),
                                    com.theveloper.pixelplay.presentation.components.LyricsTextSize.EXTRA_LARGE.key to stringResource(R.string.settings_lyrics_size_extra_large)
                                ),
                                selectedKey = displayPrefs.chosenTextSize.key,
                                onSelect = { key -> editPrefs { it[com.theveloper.pixelplay.presentation.components.LyricsDisplayPrefKeys.TEXT_SIZE] = com.theveloper.pixelplay.presentation.components.LyricsTextSize.fromKey(key).key } },
                                depth = 1
                            ))
                            add(AdvancedRow(
                                icon = Icons.Rounded.FormatBold,
                                title = "Weight",
                                options = listOf(
                                    com.theveloper.pixelplay.presentation.components.LyricsFontWeight.LIGHT.key to stringResource(R.string.settings_lyrics_weight_light),
                                    com.theveloper.pixelplay.presentation.components.LyricsFontWeight.REGULAR.key to stringResource(R.string.settings_lyrics_weight_regular),
                                    com.theveloper.pixelplay.presentation.components.LyricsFontWeight.MEDIUM.key to stringResource(R.string.settings_lyrics_weight_medium),
                                    com.theveloper.pixelplay.presentation.components.LyricsFontWeight.SEMIBOLD.key to stringResource(R.string.settings_lyrics_weight_semibold)
                                ),
                                selectedKey = displayPrefs.chosenFontWeight.key,
                                onSelect = { key -> editPrefs { it[com.theveloper.pixelplay.presentation.components.LyricsDisplayPrefKeys.FONT_WEIGHT] = com.theveloper.pixelplay.presentation.components.LyricsFontWeight.fromKey(key).key } },
                                depth = 1
                            ))
                            add(AdvancedRow(
                                icon = Icons.Rounded.FormatLineSpacing,
                                title = "Line spacing",
                                options = listOf(
                                    com.theveloper.pixelplay.presentation.components.LyricsLineSpacing.TIGHT.key to stringResource(R.string.settings_lyrics_spacing_tight),
                                    com.theveloper.pixelplay.presentation.components.LyricsLineSpacing.NORMAL.key to stringResource(R.string.settings_lyrics_spacing_normal),
                                    com.theveloper.pixelplay.presentation.components.LyricsLineSpacing.RELAXED.key to stringResource(R.string.settings_lyrics_spacing_relaxed)
                                ),
                                selectedKey = displayPrefs.chosenLineSpacing.key,
                                onSelect = { key -> editPrefs { it[com.theveloper.pixelplay.presentation.components.LyricsDisplayPrefKeys.LINE_SPACING] = com.theveloper.pixelplay.presentation.components.LyricsLineSpacing.fromKey(key).key } },
                                depth = 1
                            ))
                        }
                        add(AdvancedRow(
                            icon = Icons.Rounded.AutoAwesomeMotion,
                            title = stringResource(R.string.settings_exp_animated_lyrics_title),
                            checked = uiState.useAnimatedLyrics,
                            onCheckedChange = { settingsViewModel.setUseAnimatedLyrics(it) }
                        ))
                        if (uiState.useAnimatedLyrics) {
                            add(AdvancedRow(
                                icon = Icons.Rounded.BlurOn,
                                title = stringResource(R.string.settings_exp_lyrics_blur_title),
                                checked = uiState.animatedLyricsBlurEnabled,
                                onCheckedChange = { settingsViewModel.setAnimatedLyricsBlurEnabled(it) },
                                depth = 1
                            ))
                            if (uiState.animatedLyricsBlurEnabled) {
                                add(AdvancedRow(
                                    icon = Icons.Rounded.BlurOn,
                                    title = "Blur strength",
                                    sliderValue = uiState.animatedLyricsBlurStrength.coerceIn(0f, 5f),
                                    sliderRange = 0f..5f,
                                    onSliderChange = { settingsViewModel.setAnimatedLyricsBlurStrength(it) },
                                    depth = 2
                                ))
                            }
                        }
                    },
                    itemBackgroundColor = itemBackgroundColor,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    onAccentColor = onAccentColor
                )
            }
        }
    }

    if (confirmResetAll) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmResetAll = false },
            title = { Text(stringResource(R.string.settings_dialog_reset_imported_lyrics_title)) },
            text = { Text(stringResource(R.string.settings_dialog_reset_imported_lyrics_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmResetAll = false
                    onResetAllLyrics()
                }) { Text(stringResource(R.string.common_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmResetAll = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AdvancedGroup(
    title: String,
    rows: List<AdvancedRow>,
    itemBackgroundColor: Color,
    contentColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    header: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            text = title,
            color = accentColor,
            style = MaterialTheme.typography.bodyLargeEmphasized
        )
        if (header != null) {
            header()
            Spacer(Modifier.height(6.dp))
        }
        rows.forEachIndexed { index, row ->
            val first = index == 0
            val last = index == rows.lastIndex
            // Last child of its parent: the next row isn't at this depth or deeper.
            val lastChild = rows.getOrNull(index + 1)?.let { it.depth < row.depth } ?: true
            var menuOpen by remember { mutableStateOf(false) }
            val rowAlpha = if (row.enabled) 1f else 0.45f
            val selectedLabel = row.options.firstOrNull { it.first == row.selectedKey }?.second
            val shape = if (row.depth > 0) {
                RoundedCornerShape(14.dp)
            } else {
                // A parent with children rounds its bottom less: the branch hangs off it.
                val nextIsChild = (rows.getOrNull(index + 1)?.depth ?: 0) > 0
                val prevWasChild = (rows.getOrNull(index - 1)?.depth ?: 0) > 0
                RoundedCornerShape(
                    topStart = if (first || prevWasChild) 18.dp else 8.dp,
                    topEnd = if (first || prevWasChild) 18.dp else 8.dp,
                    bottomStart = if (last || nextIsChild) 18.dp else 8.dp,
                    bottomEnd = if (last || nextIsChild) 18.dp else 8.dp
                )
            }
            SkillTreeBranch(depth = row.depth, isLast = lastChild, lineColor = accentColor) {
                if (row.sliderValue != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(itemBackgroundColor)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(row.title, style = MaterialTheme.typography.bodyMedium, color = contentColor)
                        androidx.compose.material3.Slider(
                            value = row.sliderValue,
                            onValueChange = { row.onSliderChange?.invoke(it) },
                            valueRange = row.sliderRange,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = contentColor.copy(alpha = 0.2f)
                            )
                        )
                    }
                    return@SkillTreeBranch
                }
                ListItem(
                    headlineContent = { Text(row.title) },
                    supportingContent = if (row.subtitle != null) { { Text(row.subtitle) } } else null,
                    leadingContent = { Icon(row.icon, contentDescription = null) },
                    trailingContent = when {
                        row.checked != null -> ({
                            Switch(
                                checked = row.checked,
                                enabled = row.enabled,
                                onCheckedChange = row.onCheckedChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = onAccentColor,
                                    checkedTrackColor = accentColor,
                                    uncheckedThumbColor = contentColor,
                                    uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                )
                            )
                        })
                        row.options.isNotEmpty() -> ({
                            Box {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = selectedLabel.orEmpty(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = accentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 140.dp)
                                    )
                                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = accentColor)
                                }
                                androidx.compose.material3.DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false }
                                ) {
                                    row.options.forEach { (key, label) ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(label) },
                                            leadingIcon = if (key == row.selectedKey) ({ Icon(Icons.Rounded.Check, null) }) else null,
                                            onClick = {
                                                menuOpen = false
                                                row.onSelect?.invoke(key)
                                            }
                                        )
                                    }
                                }
                            }
                        })
                        else -> null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(itemBackgroundColor)
                        .graphicsLayer { alpha = rowAlpha }
                        .clickable(enabled = row.enabled) {
                            when {
                                row.checked != null -> row.onCheckedChange?.invoke(!row.checked)
                                row.options.isNotEmpty() -> menuOpen = true
                                else -> row.onClick?.invoke()
                            }
                        },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = contentColor,
                        supportingColor = contentColor.copy(alpha = 0.7f),
                        leadingIconColor = contentColor
                    )
                )
            }
        }
    }
}

@Composable
private fun immersiveDelayOptions(): List<Pair<String, String>> = listOf(
    "0" to "Off (manual)",
    "3000" to stringResource(R.string.settings_auto_hide_delay_3s),
    "4000" to stringResource(R.string.settings_auto_hide_delay_4s),
    "5000" to stringResource(R.string.settings_auto_hide_delay_5s),
    "6000" to stringResource(R.string.settings_auto_hide_delay_6s)
)

/**
 * Indents a setting that only appears once its parent is on, and draws a skill-tree branch
 * back up to the parent: a line down the left, a short arm across, and a node where it meets
 * the row. [isLast] stops the line at this row instead of running on to the next sibling.
 */
@Composable
internal fun SkillTreeBranch(
    depth: Int,
    isLast: Boolean,
    lineColor: Color,
    content: @Composable () -> Unit
) {
    if (depth <= 0) {
        content()
        return
    }
    val branchWidth = 26.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .padding(top = 2.dp)
    ) {
        if (depth > 1) Spacer(Modifier.width(branchWidth * (depth - 1)))
        Box(
            modifier = Modifier
                .width(branchWidth)
                .fillMaxHeight()
                .drawBehind {
                    val stroke = 2.dp.toPx()
                    val x = 13.dp.toPx()
                    val mid = size.height / 2f
                    val color = lineColor.copy(alpha = 0.55f)
                    // Reach up past the gap to the parent row above.
                    val top = -6.dp.toPx()
                    val bottom = if (isLast) mid else size.height + 4.dp.toPx()
                    drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = stroke, cap = StrokeCap.Round)
                    drawLine(color, Offset(x, mid), Offset(size.width - 4.dp.toPx(), mid), strokeWidth = stroke, cap = StrokeCap.Round)
                    drawCircle(lineColor, radius = 3.5.dp.toPx(), center = Offset(size.width - 4.dp.toPx(), mid))
                }
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

/** Immersive delay picker hanging off the Immersive switch in the Controls group. */
@Composable
private fun ImmersiveDelayRow(
    selectedMs: Long,
    onSelect: (Long) -> Unit,
    itemBackgroundColor: Color,
    contentColor: Color,
    accentColor: Color
) {
    var menuOpen by remember { mutableStateOf(false) }
    val options = immersiveDelayOptions()
    val selectedLabel = options.firstOrNull { it.first == selectedMs.toString() }?.second.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(itemBackgroundColor)
            .clickable { menuOpen = true }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Timer, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            "Immersive delay",
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(selectedLabel, style = MaterialTheme.typography.labelLarge, color = accentColor, maxLines = 1)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = accentColor)
            }
            androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                options.forEach { (key, label) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = if (key == selectedMs.toString()) ({ Icon(Icons.Rounded.Check, null) }) else null,
                        onClick = {
                            menuOpen = false
                            onSelect(key.toLong())
                        }
                    )
                }
            }
        }
    }
}
