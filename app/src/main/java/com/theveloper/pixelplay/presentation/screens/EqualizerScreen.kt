package com.theveloper.pixelplay.presentation.screens

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntAsState // Added
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset // Added
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize // Added
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton // Added
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.TabPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf // Added
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale // Added
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign // Added
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
 // Added
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.ExpressiveTopBarContent
import com.theveloper.pixelplay.presentation.viewmodel.EqualizerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.rounded.Check // Added import for Switch check icon
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import com.theveloper.pixelplay.presentation.components.WavyArcSlider
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.Edit // Added
import androidx.compose.material.icons.rounded.ExpandMore // Added
import androidx.compose.material.icons.rounded.Save // Added
import androidx.compose.material.icons.filled.Star // Added
import androidx.compose.material3.Surface
import com.theveloper.pixelplay.presentation.components.CustomPresetsSheet
import com.theveloper.pixelplay.presentation.components.ReorderPresetsSheet
import com.theveloper.pixelplay.presentation.components.SavePresetDialog
import com.theveloper.pixelplay.presentation.components.RenamePresetDialog
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Scaffold
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetThemeState
import com.theveloper.pixelplay.ui.theme.PixelPlayStatusBarStyle
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.material.icons.rounded.Headphones
import com.theveloper.pixelplay.presentation.components.AutoEqSheet
import com.theveloper.pixelplay.presentation.components.AuditionBar
import com.theveloper.pixelplay.presentation.components.FineTuneDialog
import com.theveloper.pixelplay.presentation.components.EducationalFrequencyMarkers
import com.theveloper.pixelplay.presentation.components.HardwareOutputCard
import com.theveloper.pixelplay.presentation.components.DspAudiophileSection
import androidx.compose.ui.res.stringResource

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EqualizerScreen(
    navController: NavController? = null,
    onBackClick: () -> Unit = { navController?.popBackStack() },
    playerViewModel: PlayerViewModel = hiltViewModel(),
    equalizerViewModel: EqualizerViewModel = hiltViewModel()
) {
    val uiState by equalizerViewModel.uiState.collectAsStateWithLifecycle()

    // Color scheme adapting to current song or active theme preference
    val isDarkTheme = isSystemInDarkTheme()
    val baseColorScheme = MaterialTheme.colorScheme
    val playerThemePreference by playerViewModel.playerThemePreference.collectAsStateWithLifecycle()
    val activePlayerSchemePair by playerViewModel.activePlayerColorSchemePair.collectAsStateWithLifecycle()
    val themedAlbumArtUri by playerViewModel.currentThemedAlbumArtUri.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val currentSong = stablePlayerState.currentSong

    val sheetThemeState = rememberSheetThemeState(
        activePlayerSchemePair = activePlayerSchemePair,
        isDarkTheme = isDarkTheme,
        playerThemePreference = playerThemePreference,
        currentSong = currentSong,
        themedAlbumArtUri = themedAlbumArtUri,
        preparingSongId = null,
        systemColorScheme = baseColorScheme
    )
    val targetColorScheme = sheetThemeState.albumColorScheme

    // Sheet States
    var genrePresetQuery by rememberSaveable { mutableStateOf("") }
    var showCustomPresetsSheet by remember { mutableStateOf(false) }
    var showReorderSheet by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<EqualizerPreset?>(null) }
    
    // Handlers
    if (showSaveDialog) {
        SavePresetDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name -> equalizerViewModel.saveCurrentAsCustomPreset(name) }
        )
    }
    
    renameTarget?.let { preset ->
        RenamePresetDialog(
            currentName = preset.displayName,
            onDismiss = { renameTarget = null },
            onRename = { newName ->
                equalizerViewModel.renameCustomPreset(preset.name, newName)
            }
        )
    }
    
    if (showCustomPresetsSheet) {
        CustomPresetsSheet(
            presets = uiState.customPresets,
            pinnedPresetsNames = uiState.pinnedPresetsNames,
            onPresetSelected = { equalizerViewModel.selectPreset(it) },
            onPinToggled = { equalizerViewModel.togglePinPreset(it.name) },
            onRename = { renameTarget = it },
            onDelete = { equalizerViewModel.deleteCustomPreset(it) },
            onDismiss = { showCustomPresetsSheet = false }
        )
    }
    
    ReorderPresetsSheet(
        visible = showReorderSheet,
        allAvailablePresets = uiState.allAvailablePresets,
        pinnedPresetsNames = uiState.pinnedPresetsNames,
        onSave = { newOrder -> equalizerViewModel.updatePinnedPresetsOrder(newOrder) },
        onReset = { equalizerViewModel.resetPinnedPresetsToDefault() },
        onDismiss = { showReorderSheet = false }
    )

    if (uiState.showAutoEqSheet) {
        AutoEqSheet(
            onProfileSelected = { equalizerViewModel.selectAutoEqProfile(it) },
            onDismiss = { equalizerViewModel.setAutoEqSheetVisible(false) }
        )
    }

    uiState.fineTuneBandIndex?.let { bandIndex ->
        FineTuneDialog(
            bandIndex = bandIndex,
            frequency = EqualizerPreset.BAND_FREQUENCIES.getOrElse(bandIndex) { "" },
            currentLevel = uiState.bandLevels.getOrElse(bandIndex) { 0 },
            onAdjustLevel = { delta -> equalizerViewModel.adjustFineTuneValue(delta) },
            onResetToZero = {
                equalizerViewModel.setBandLevel(bandIndex, 0)
                equalizerViewModel.dismissFineTune()
            },
            onDismiss = { equalizerViewModel.dismissFineTune() }
        )
    }
    
    // Transition animations
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(true) { transitionState.targetState = true }
    
    val transition = rememberTransition(transitionState, label = "EqualizerAppearTransition")
    
    val contentAlpha by transition.animateFloat(
        label = "ContentAlpha",
        transitionSpec = { tween(durationMillis = 500) }
    ) { if (it) 1f else 0f }
    
    val contentOffset by transition.animateDp(
        label = "ContentOffset",
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) }
    ) { if (it) 0.dp else 40.dp }
    
    val lazyListState = rememberLazyListState()

    MaterialTheme(
        colorScheme = targetColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    ) {
        PixelPlayStatusBarStyle(color = targetColorScheme.surface)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffset.toPx()
                }
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = onBackClick,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = stringResource(R.string.settings_category_equalizer_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )

                        // AutoEq headphone profiles button
                        FilledIconButton(
                            onClick = { equalizerViewModel.setAutoEqSheetVisible(true) },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = stringResource(R.string.equalizer_autoeq_title)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Save Preset button: shown only when custom/unpreset values are detected
                        val isUnpreset = remember(uiState.bandLevels, uiState.allAvailablePresets) {
                            uiState.allAvailablePresets.none { it.bandLevels == uiState.bandLevels }
                        }

                        AnimatedVisibility(
                            visible = isUnpreset,
                            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                    scaleIn(initialScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                   scaleOut(targetScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilledIconButton(
                                    onClick = { showSaveDialog = true },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = targetColorScheme.primary,
                                        contentColor = targetColorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Save,
                                        contentDescription = stringResource(R.string.common_save)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                        }

                        // Power toggle
                        val isEnabled = uiState.isEnabled
                        val powerButtonCorner by animateIntAsState(
                            targetValue = if (isEnabled) 50 else 12,
                            label = "PowerButtonShape"
                        )

                        FilledIconToggleButton(
                            checked = isEnabled,
                            onCheckedChange = { equalizerViewModel.toggleEqualizer() },
                            shape = RoundedCornerShape(powerButtonCorner),
                            colors = IconButtonDefaults.filledIconToggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                checkedContainerColor = targetColorScheme.primary,
                                checkedContentColor = targetColorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = if (isEnabled) {
                                    stringResource(R.string.equalizer_disable_cd)
                                } else {
                                    stringResource(R.string.equalizer_enable_cd)
                                }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "adapt_to_genre") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Adapt to song genre", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (!uiState.isEnabled) "Enable the equalizer to hear adaptation"
                                    else if (uiState.adaptToGenre) "Active: ${uiState.currentPreset.displayName}"
                                    else "Follows each song's genre; unknown tags use Flat",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = uiState.adaptToGenre,
                                onCheckedChange = equalizerViewModel::setAdaptToGenre)
                        }
                    }
                    item(key = "genre_presets") {
                        Text("Genre presets", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 20.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = genrePresetQuery,
                            onValueChange = { genrePresetQuery = it },
                            placeholder = { Text("Find a genre") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        val genres = remember(genrePresetQuery) {
                            val query = genrePresetQuery.trim()
                            val alias = com.theveloper.pixelplay.data.equalizer.GenreEqualizerPresets.forGenre(query).name
                            com.theveloper.pixelplay.data.equalizer.GenreEqualizerPresets.all.filter {
                                it.displayName.contains(query, ignoreCase = true) || it.name == alias
                            }
                        }
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(genres.size, key = { genres[it].name }) { index ->
                                val preset = genres[index]
                                FilterChip(
                                    selected = uiState.currentPreset.name == preset.name,
                                    onClick = { equalizerViewModel.selectPreset(preset) },
                                    label = { Text(preset.displayName) }
                                )
                            }
                        }
                        if (genres.isEmpty()) Text("No matching genre", modifier = Modifier.padding(horizontal = 20.dp))
                    }
                    item(key = "preset_tabs") {
                        // All presets: Filter out raw "custom" entry from tabs, replaced by saved Custom presets
                        val visiblePresets = remember(uiState.accessiblePresets) {
                            uiState.accessiblePresets.filter { it.name != "custom" }
                        }
                        
                        PresetTabsRow(
                            presets = visiblePresets,
                            selectedPreset = uiState.currentPreset,
                            onPresetSelected = {
                                equalizerViewModel.selectPreset(it) 
                            },
                            onEditClick = { showReorderSheet = true }
                        )
                    }

                    // A/B Auditioning Bar
                    item(key = "audition_bar") {
                        AuditionBar(
                            auditionState = uiState.auditionState,
                            onSelectSlot = { equalizerViewModel.setAuditionSlot(it) },
                            onCopyToSlot = { equalizerViewModel.copyCurrentToAuditionSlot(it) },
                            onToggleLoudnessComp = { equalizerViewModel.setAuditionLoudnessComp(it) }
                        )
                    }
                    
                    // Band Sliders (Hybrid: frequency response graph above + grouped sliders below)
                    item(key = "band_sliders") {
                        BandSlidersSection(
                            bandLevels = uiState.bandLevels,
                            isEnabled = uiState.isEnabled,
                            onBandLevelChanged = { bandId, level ->
                                equalizerViewModel.setBandLevel(bandId, level)
                            },
                            onOpenFineTune = { bandIndex ->
                                equalizerViewModel.openFineTuneBand(bandIndex)
                            },
                            targetColorScheme = targetColorScheme,
                            isPlaying = stablePlayerState.isPlaying
                        )
                    }

                    // Phase 2: Pro Audiophile DSP Engine Section
                    item(key = "dsp_audiophile") {
                        DspAudiophileSection(
                            autoPreampEnabled = uiState.autoPreampEnabled,
                            manualPreampDb = uiState.manualPreampDb,
                            effectivePreampDb = uiState.effectivePreampDb,
                            dspEngineEnabled = uiState.dspEngineEnabled,
                            isParametricMode = uiState.isParametricMode,
                            parametricBands = uiState.parametricBands,
                            subsonicEnabled = uiState.subsonicEnabled,
                            subsonicCutoffHz = uiState.subsonicCutoffHz,
                            ultrasonicEnabled = uiState.ultrasonicEnabled,
                            ultrasonicCutoffHz = uiState.ultrasonicCutoffHz,
                            crossfeedStrength = uiState.crossfeedStrength,
                            limiterEnabled = uiState.limiterEnabled,
                            softSaturationEnabled = uiState.softSaturationEnabled,
                            onAutoPreampToggled = { equalizerViewModel.setAutoPreampEnabled(it) },
                            onManualPreampChanged = { equalizerViewModel.setManualPreampDb(it) },
                            onDspEngineToggled = { equalizerViewModel.setDspEngineEnabled(it) },
                            onParametricModeToggled = { equalizerViewModel.setParametricMode(it) },
                            onUpdateParametricBand = { equalizerViewModel.updateParametricBand(it) },
                            onAddParametricBand = { equalizerViewModel.addParametricBand() },
                            onRemoveParametricBand = { equalizerViewModel.removeParametricBand(it) },
                            onSubsonicFilterToggled = { enabled, hz -> equalizerViewModel.setSubsonicFilter(enabled, hz) },
                            onUltrasonicFilterToggled = { enabled, hz -> equalizerViewModel.setUltrasonicFilter(enabled, hz) },
                            onCrossfeedChanged = { equalizerViewModel.setCrossfeedStrength(it) },
                            onLimiterSettingsChanged = { limiter, sat -> equalizerViewModel.setLimiterSettings(limiter, sat) }
                        )
                    }

                    // Phase 1: Hardware Output Device Card
                    item(key = "hardware_output") {
                        HardwareOutputCard(
                            device = uiState.currentOutputDevice,
                            currentPresetName = uiState.currentPreset.displayName,
                            devicePresetMap = uiState.devicePresetMap,
                            autoSwitchEnabled = uiState.autoSwitchDeviceEnabled,
                            onToggleAutoSwitch = { equalizerViewModel.setAutoSwitchDeviceEnabled(it) },
                            onAssignPresetToDevice = { equalizerViewModel.assignCurrentPresetToCurrentDevice() }
                        )
                    }
                    
                    // Effect Controls
                    item(key = "effect_controls") {
                        EffectControlsSection(
                            bassBoostEnabled = uiState.bassBoostEnabled,
                            bassBoostStrength = uiState.bassBoostStrength, // Now Float
                            virtualizerEnabled = uiState.virtualizerEnabled,
                            virtualizerStrength = uiState.virtualizerStrength, // Now Float
                            loudnessEnabled = uiState.loudnessEnhancerEnabled,
                            loudnessStrength = uiState.loudnessEnhancerStrength, // Now Float
                            isBassBoostSupported = uiState.isBassBoostSupported,
                            isVirtualizerSupported = uiState.isVirtualizerSupported,
                            isLoudnessEnhancerSupported = uiState.isLoudnessEnhancerSupported,
                            isBassBoostDismissed = uiState.isBassBoostDismissed,
                            isVirtualizerDismissed = uiState.isVirtualizerDismissed,
                            isLoudnessDismissed = uiState.isLoudnessDismissed,
                            onBassBoostEnabledChange = { equalizerViewModel.setBassBoostEnabled(it) },
                            onBassBoostStrengthChange = { equalizerViewModel.setBassBoostStrength(it.roundToInt()) },
                            onVirtualizerEnabledChange = { equalizerViewModel.setVirtualizerEnabled(it) },
                            onVirtualizerStrengthChange = { equalizerViewModel.setVirtualizerStrength(it.roundToInt()) },
                            onLoudnessEnabledChange = { equalizerViewModel.setLoudnessEnhancerEnabled(it) },
                            onLoudnessStrengthChange = { equalizerViewModel.setLoudnessEnhancerStrength(it.roundToInt()) },
                            onDismissBassBoost = { equalizerViewModel.setBassBoostDismissed(true) },
                            onDismissVirtualizer = { equalizerViewModel.setVirtualizerDismissed(true) },
                            onDismissLoudness = { equalizerViewModel.setLoudnessDismissed(true) }
                        )
                    }
                }
            }
        }
    }
}

// EqualizerTopBar removed, replaced by CollapsibleCommonTopBar

@Composable
private fun PresetTabsRow(
    presets: List<EqualizerPreset>,
    selectedPreset: EqualizerPreset,
    onPresetSelected: (EqualizerPreset) -> Unit,
    onEditClick: () -> Unit
) {
    val showTabIndicator = false
    val selectedIndex = remember(presets, selectedPreset) {
        if (selectedPreset.isCustom || selectedPreset.name == "custom") {
             presets.indexOfLast { it.name == "Custom" || it.name == "custom" } // Match the placeholder
        } else {
             presets.indexOfFirst { it.name == selectedPreset.name }.coerceAtLeast(0)
        }
    }.coerceAtLeast(0)
    val coroutineScope = rememberCoroutineScope()
    
    // We don't use a Pager, so we need a manual scroll state if we wanted to auto-scroll.
    // Standard ScrollableTabRow handles scrolling to selected index automatically.
    
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 12.dp,
        containerColor = Color.Transparent,
        divider = {},
        indicator = {
            if (showTabIndicator) {
                 TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedTabIndex = selectedIndex),
                    height = 3.dp,
                    width = 20.dp, // Fixed width for expressive dot? Or default width? Library used default.
                    // Library code: Modifier.tabIndicatorOffset(selectedTabIndex = pagerState.currentPage), height = 3.dp
                    // Let's stick to default width (match content) but custom height/color.
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.primary
                 )
            }
        },
        modifier = Modifier.fillMaxWidth().height(56.dp) // Reduced height? Standard is often 48-64. 56 is good.
    ) {
        presets.forEachIndexed { index, preset ->
            val isPinnedCustom = preset.isCustom
            
            TabAnimation(
                index = index,
                title = preset.name,
                unselectedColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                selectedIndex = selectedIndex,
                onClick = { onPresetSelected(preset) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = preset.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium
                    )
                    if (isPinnedCustom) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = stringResource(R.string.equalizer_custom_preset_cd),
                            modifier = Modifier.size(10.dp), // Slightly smaller
                            tint = if (selectedIndex == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary 
                            // Note: TabAnimation handles content color usually, but Icon tint might need explicit handling or use LocalContentColor.
                            // TabAnimation uses: selectedContentColor = contentColor.
                            // So if I don't set tint, it will use LocalContentColor which is animated. 
                            // So remove manual tint or use LocalContentColor.current.
                        )
                    }
                }
            }
        }
        
        // Edit Button as a specific Tab (unselectable)
        TabAnimation(
            index = -1,
            title = stringResource(R.string.equalizer_edit_tab_title),
            unselectedColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            selectedIndex = selectedIndex,
            onClick = onEditClick 
        ) {
             Icon(
                Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.equalizer_edit_presets_cd),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}


@Composable
private fun BandSlidersSection(
    bandLevels: List<Int>,
    isEnabled: Boolean,
    onBandLevelChanged: (Int, Int) -> Unit,
    onOpenFineTune: (Int) -> Unit = {},
    targetColorScheme: ColorScheme,
    isPlaying: Boolean
) {
    val frequencies = EqualizerPreset.BAND_FREQUENCIES
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HybridBandSliders(
                bandLevels = bandLevels,
                isEnabled = isEnabled,
                frequencies = frequencies,
                onBandLevelChanged = onBandLevelChanged,
                onOpenFineTune = onOpenFineTune,
                targetColorScheme = targetColorScheme,
                isPlaying = isPlaying
            )
        }
    }
}

@Composable
private fun GraphBandSliders(
    bandLevels: List<Int>,
    isEnabled: Boolean,
    frequencies: List<String>,
    onBandLevelChanged: (Int, Int) -> Unit,
    onOpenFineTune: (Int) -> Unit = {}
) {
    val hapticFeedback = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp) // Slightly taller for graph headroom
    ) {
        val density = LocalDensity.current
        
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            bandLevels.forEachIndexed { index, level ->
                val zoneName = EducationalFrequencyMarkers.getZoneName(index)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(isEnabled) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (isEnabled) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onBandLevelChanged(index, 0)
                                    }
                                }
                            )
                        }
                ) {
                    // Value Text (Top) - tap opens fine-tune dialog
                    Text(
                        text = if (level > 0) "+$level" else "$level",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = isEnabled) { onOpenFineTune(index) }
                    )
                    

                    CustomVerticalSlider(
                        value = level.toFloat(),
                        onValueChange = { onBandLevelChanged(index, it.roundToInt()) },
                        valueRange = -15f..15f,
                        enabled = isEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        activeTrackColor = Color.Transparent, 
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        thumbColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        trackThickness = 4.dp, 
                        thumbSize = 16.dp,
                        thumbShape = CircleShape
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Educational zone tag
                    Text(
                        text = zoneName,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                    )

                    // Frequency
                    Text(
                        text = frequencies.getOrElse(index) { "" }.replace("Hz", "").replace("k", "k"),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Graph Canvas Overlay
        if (isEnabled) {
            val primaryColor = MaterialTheme.colorScheme.primary
            
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                val widthPerBand = size.width / bandLevels.size
                val path = Path()
                
                // Constants from CustomVerticalSlider
                val sliderTopPadding = 20.dp.toPx()
                val sliderBottomPadding = 24.dp.toPx()
                val thumbSize = 16.dp.toPx() // Updated to 16dp
                val verticalPadding = 4.dp.toPx()
                val availableHeight = size.height - sliderTopPadding - sliderBottomPadding
                val trackHeight = availableHeight - thumbSize - (verticalPadding * 2)
                val topOffset = sliderTopPadding + verticalPadding + (thumbSize / 2)
                
                val points = bandLevels.mapIndexed { index, level ->
                    val x = (widthPerBand * index) + (widthPerBand / 2)
                    val normalized = ((level - (-15f)) / (15f - -15f)).coerceIn(0f, 1f)
                    val yNormalized = 1f - normalized
                    val y = topOffset + (yNormalized * trackHeight)
                    Offset(x, y)
                }
                
                if (points.isNotEmpty()) {
                    path.moveTo(points[0].x, points[0].y)
                    
                    for (i in 0 until points.size - 1) {
                        val p0 = points[maxOf(0, i - 1)]
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val p3 = points[minOf(points.size - 1, i + 2)]
                        
                        val cp1X = p1.x + (p2.x - p0.x) * 0.2f
                        val cp1Y = p1.y + (p2.y - p0.y) * 0.2f
                        val cp2X = p2.x - (p3.x - p1.x) * 0.2f
                        val cp2Y = p2.y - (p3.y - p1.y) * 0.2f
                        
                        path.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
                    }
                    
                    // Draw Line
                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    
                    // Draw Fill
                    val fillPath = Path()
                    fillPath.addPath(path)
                    fillPath.lineTo(points.last().x, size.height - sliderBottomPadding)
                    fillPath.lineTo(points.first().x, size.height - sliderBottomPadding)
                    fillPath.close()
                    
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.3f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height
                        )
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VerticalBandSlider(
    frequency: String,
    level: Int,
    isEnabled: Boolean,
    onLevelChanged: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
    ) {
        // Level indicator
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (isEnabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (level >= 0) "+$level" else "$level",
                style = MaterialTheme.typography.labelSmall,
                color = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Custom vertical slider
        CustomVerticalSlider(
            value = level.toFloat(),
            onValueChange = { onLevelChanged(it.roundToInt()) },
            valueRange = -15f..15f,
            enabled = isEnabled,
            modifier = Modifier
                .weight(1f)
                .width(40.dp),
            activeTrackColor = if (isEnabled) MaterialTheme.colorScheme.primary 
                              else MaterialTheme.colorScheme.onSurfaceVariant,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            thumbColor = if (isEnabled) MaterialTheme.colorScheme.onPrimary // Contrast for thick slider
                        else MaterialTheme.colorScheme.onSurfaceVariant
            // Default params used: trackThickness = Unspecified (fill), thumbSize = 24.dp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Frequency label
        Text(
            text = frequency,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}



// ... imports ...


@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun CustomVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    thumbColor: Color,
    trackThickness: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    thumbSize: androidx.compose.ui.unit.Dp = 24.dp,
    thumbShape: androidx.compose.ui.graphics.Shape? = null
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val view = LocalView.current
    val thumbSizePx = with(density) { thumbSize.toPx() }
    val thumbRadiusPx = thumbSizePx / 2
    
    // Geometry correction: Adding padding so thumb doesn't touch the absolute container edges
    val verticalPaddingDp = 4.dp
    val verticalPaddingPx = with(density) { verticalPaddingDp.toPx() }
    
    // Normalize value to 0..1 range
    val normalizedValue = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    
    // Track previous integer value for haptic feedback
    var lastHapticValue by remember { mutableIntStateOf(value.roundToInt()) }
    var isInteracting by remember { mutableStateOf(false) }
    var dragNormalizedValue by remember { mutableFloatStateOf(normalizedValue) }

    LaunchedEffect(normalizedValue, isInteracting) {
        if (!isInteracting) {
            dragNormalizedValue = normalizedValue
        }
    }
    
    // Create the Path
    val starShape = remember { com.theveloper.pixelplay.utils.shapes.RoundedStarShape(sides = 8, curve = 0.1) }
    val finalShape = thumbShape ?: starShape
    
    val thumbPath = remember(thumbSizePx, finalShape) {
        val outline = finalShape.createOutline(
            androidx.compose.ui.geometry.Size(thumbSizePx, thumbSizePx),
            androidx.compose.ui.unit.LayoutDirection.Ltr,
            density
        )
        when (outline) {
            is androidx.compose.ui.graphics.Outline.Generic -> outline.path
            is androidx.compose.ui.graphics.Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is androidx.compose.ui.graphics.Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        }
    }

    // Colors for "inside" look
    val actualActiveTrackColor = if (enabled) activeTrackColor else activeTrackColor.copy(alpha = 0.3f)
    val actualInactiveTrackColor = inactiveTrackColor
    val actualThumbColor = if (enabled) thumbColor else MaterialTheme.colorScheme.onSurfaceVariant

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val heightPx = with(density) { maxHeight.toPx() }
        
        // Usable track height (center of thumb travels within this range, respecting padding)
        val trackHeight = heightPx - thumbSizePx - (verticalPaddingPx * 2)
        val safeTrackHeight = trackHeight.coerceAtLeast(1f)
        val displayNormalizedValue = if (isInteracting) dragNormalizedValue else normalizedValue
        
        // thumb Y position (center)
        val thumbCenterY = heightPx - verticalPaddingPx - thumbRadiusPx - (displayNormalizedValue * safeTrackHeight)
        
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(50))
                .pointerInput(enabled, valueRange.start, valueRange.endInclusive, safeTrackHeight, heightPx) {
                    if (!enabled) return@pointerInput
                    fun dispatchValue(touchY: Float, forceHaptic: Boolean = false) {
                        val trackTopY = verticalPaddingPx + thumbRadiusPx
                        val relativeY = (touchY - trackTopY).coerceIn(0f, safeTrackHeight)
                        val newNormalized = 1f - (relativeY / safeTrackHeight)
                        dragNormalizedValue = newNormalized
                        val newValue = valueRange.start + newNormalized * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)

                        val newInt = newValue.roundToInt()
                        if (forceHaptic || newInt != lastHapticValue) {
                            val crossedZero = (lastHapticValue < 0 && newInt >= 0) || (lastHapticValue > 0 && newInt <= 0) || newInt == 0
                            if (crossedZero && newInt == 0) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            lastHapticValue = newInt
                        }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        isInteracting = true
                        down.consume()
                        dispatchValue(down.position.y, forceHaptic = true)

                        var activePointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointerChange = event.changes.firstOrNull { it.id == activePointerId }
                                ?: event.changes.firstOrNull { it.pressed }?.also { activePointerId = it.id }
                                ?: break

                            if (!pointerChange.pressed) {
                                pointerChange.consume()
                                break
                            }

                            if (pointerChange.position.y != pointerChange.previousPosition.y) {
                                pointerChange.consume()
                                dispatchValue(pointerChange.position.y)
                            }
                        }

                        isInteracting = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
        ) {
            val centerX = size.width / 2
            
            // Determine track drawing width
            val trackWidth = if (trackThickness != androidx.compose.ui.unit.Dp.Unspecified) {
                with(density) { trackThickness.toPx() }
            } else {
                size.width
            }
            // If explicit thickness, center it. If fill, left is 0.
            val trackLeft = if (trackThickness != androidx.compose.ui.unit.Dp.Unspecified) {
                centerX - (trackWidth / 2)
            } else {
                0f
            }
            
            // 1. Draw Inactive Track
            drawRoundRect(
                color = actualInactiveTrackColor,
                topLeft = androidx.compose.ui.geometry.Offset(trackLeft, 0f), 
                size = androidx.compose.ui.geometry.Size(trackWidth, size.height), // Use height not size.width
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2)
            )
            
            // 2. Draw Active Track
            // Cap at thumb center
            drawCircle(
                color = actualActiveTrackColor,
                radius = trackWidth / 2, // Use trackWidth
                center = androidx.compose.ui.geometry.Offset(centerX, thumbCenterY)
            )
            
            val activeRectTop = thumbCenterY
            val activeRectHeight = size.height - activeRectTop
            
            if (activeRectHeight > 0f) {
                val activeTrackPath = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            rect = androidx.compose.ui.geometry.Rect(
                                offset = androidx.compose.ui.geometry.Offset(trackLeft, activeRectTop),
                                size = androidx.compose.ui.geometry.Size(trackWidth, activeRectHeight)
                            ),
                            topLeft = androidx.compose.ui.geometry.CornerRadius.Zero,
                            topRight = androidx.compose.ui.geometry.CornerRadius.Zero,
                            bottomLeft = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2),
                            bottomRight = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2)
                        )
                    )
                }
                drawPath(
                    path = activeTrackPath,
                    color = actualActiveTrackColor
                )
            }

            // 3. Draw Thumb
            translate(
                left = centerX - thumbRadiusPx, 
                top = thumbCenterY - thumbRadiusPx
            ) {
                // Rotate thumb based on normalized value (0 at bottom -> 360 at top)
                rotate(
                    degrees = displayNormalizedValue * 360f,
                    pivot = androidx.compose.ui.geometry.Offset(thumbRadiusPx, thumbRadiusPx)
                ) {
                    drawPath(
                        path = thumbPath,
                        color = actualThumbColor
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectControlsSection(
    bassBoostEnabled: Boolean,
    bassBoostStrength: Float, // Int -> Float
    virtualizerEnabled: Boolean,
    virtualizerStrength: Float, // Int -> Float
    loudnessEnabled: Boolean,
    loudnessStrength: Float, // Int -> Float
    isBassBoostSupported: Boolean,
    isVirtualizerSupported: Boolean,
    isLoudnessEnhancerSupported: Boolean,
    isBassBoostDismissed: Boolean = false,
    isVirtualizerDismissed: Boolean = false,
    isLoudnessDismissed: Boolean = false,
    onBassBoostEnabledChange: (Boolean) -> Unit,
    onBassBoostStrengthChange: (Float) -> Unit, // Int -> Float
    onVirtualizerEnabledChange: (Boolean) -> Unit,
    onVirtualizerStrengthChange: (Float) -> Unit, // Int -> Float
    onLoudnessEnabledChange: (Boolean) -> Unit,
    onLoudnessStrengthChange: (Float) -> Unit, // Int -> Float
    onDismissBassBoost: () -> Unit,
    onDismissVirtualizer: () -> Unit,
    onDismissLoudness: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .height(androidx.compose.foundation.layout.IntrinsicSize.Max) // Ensure equal heights
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Bass Boost
        if (isBassBoostSupported) {
            EffectCard(
                title = stringResource(R.string.equalizer_bass_boost),
                value = bassBoostStrength, // Already Float
                valueRange = 0f..1000f, // Keeping range as is, assuming VM handles 0-100 normalization? 
                // Wait, if VM stores 0-100 Float, but repo uses 0-1000 Int.
                // If I incorrectly changed VM to store 0-100 Float, I must match UI ranges.
                // Originally ranges were 0..1000. 
                // Let's assume VM exposes raw 0..1000 as Float to match slider requirements.
                isEnabled = bassBoostEnabled,
                onValueChange = { onBassBoostStrengthChange(it) }, // Pass Float directly
                onEnabledChange = onBassBoostEnabledChange
            )
        } else if (!isBassBoostDismissed) {
             UnsupportedEffectCard(
                title = stringResource(R.string.equalizer_bass_boost),
                onDismiss = onDismissBassBoost
            )
        }
        
        // Virtualizer
        if (isVirtualizerSupported) {
            EffectCard(
                title = stringResource(R.string.equalizer_virtualizer),
                value = virtualizerStrength,
                valueRange = 0f..1000f,
                isEnabled = virtualizerEnabled,
                onValueChange = { onVirtualizerStrengthChange(it) },
                onEnabledChange = onVirtualizerEnabledChange
            )
        } else if (!isVirtualizerDismissed) {
             UnsupportedEffectCard(
                title = stringResource(R.string.equalizer_virtualizer),
                onDismiss = onDismissVirtualizer
            )
        }

        // Loudness Enhancer
        if (isLoudnessEnhancerSupported) {
            EffectCard(
                title = stringResource(R.string.equalizer_loudness),
                value = loudnessStrength,
                valueRange = 0f..1000f,
                isEnabled = loudnessEnabled,
                onValueChange = { onLoudnessStrengthChange(it) },
                onEnabledChange = onLoudnessEnabledChange
            )
        } else if (!isLoudnessDismissed) {
             UnsupportedEffectCard(
                title = stringResource(R.string.equalizer_loudness),
                onDismiss = onDismissLoudness
            )
        }
    }
}

@Composable
private fun EffectCard(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    isEnabled: Boolean,
    onValueChange: (Float) -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.width(150.dp).fillMaxHeight(), // Match parent intrinsic height
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp) // Reduced spacing
        ) {

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(150.dp)
                    .height(110.dp) 
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(150.dp) // Force render size
                        .offset(y = (5).dp), // Shift UP slightly to center clearer
                    contentAlignment = Alignment.Center
                ) {
                    WavyArcSlider(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxSize(),
                        enabled = isEnabled,
                        valueRange = valueRange,
                        activeTrackColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        thumbColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        waveAmplitude = 3.dp
                    )
                    
                    // Percentage Text
                    val percentage = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start) * 100).toInt()
                    Text(
                        text = stringResource(R.string.common_percentage_text, percentage),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.scale(0.8f) 
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun UnsupportedEffectCard(
    title: String,
    onDismiss: () -> Unit
) {
     Card(
        modifier = Modifier.width(150.dp).fillMaxHeight(), // Match parent intrinsic height
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.common_dismiss),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.equalizer_effect_not_supported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun UnsupportedEffectRow(
    title: String,
    message: String = "",
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) // Subtle warning
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        val resolvedMessage = if (message.isNotEmpty()) {
            message
        } else {
            stringResource(R.string.equalizer_effect_not_supported_device)
        }
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = resolvedMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.common_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndividualEffectRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isEnabled: Boolean,
    strength: Int,
    onEnabledChange: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    maxStrength: Int = 1000
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChange,
                thumbContent = if (isEnabled) {
                    {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else null
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = strength.toFloat(),
            onValueChange = { onStrengthChange(it.roundToInt()) },
            valueRange = 0f..maxStrength.toFloat(),
            enabled = isEnabled,
            modifier = Modifier.fillMaxWidth(),
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(36.dp)
                )
            }
        )
    }
}


@Composable
private fun HybridBandSliders(
    bandLevels: List<Int>,
    isEnabled: Boolean,
    frequencies: List<String>,
    onBandLevelChanged: (Int, Int) -> Unit,
    onOpenFineTune: (Int) -> Unit = {},
    targetColorScheme: ColorScheme,
    isPlaying: Boolean
) {
    val bandBass = stringResource(R.string.equalizer_band_bass)
    val bandLowMids = stringResource(R.string.equalizer_band_low_mids)
    val bandHighMids = stringResource(R.string.equalizer_band_high_mids)
    val bandTreble = stringResource(R.string.equalizer_band_treble)
    val bandBassLow = stringResource(R.string.equalizer_band_bass_low)
    val bandMidHigh = stringResource(R.string.equalizer_band_mid_high)

    var showRealtimeVisualizer by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Static Compact Graph / Realtime Audio Visualizer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp)
        ) {
            // Header Row: Frequency Response title + Visualizer Toggle Button + Live status badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (showRealtimeVisualizer) {
                            stringResource(R.string.equalizer_visualizer_realtime)
                        } else {
                            stringResource(R.string.equalizer_frequency_response)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = targetColorScheme.primary
                    )

                    // Button right of the frequency response text
                    IconButton(
                        onClick = { showRealtimeVisualizer = !showRealtimeVisualizer },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (showRealtimeVisualizer) targetColorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                    ) {
                        Icon(
                            imageVector = if (showRealtimeVisualizer) Icons.AutoMirrored.Rounded.ShowChart else Icons.Rounded.GraphicEq,
                            contentDescription = if (showRealtimeVisualizer) {
                                stringResource(R.string.equalizer_switch_to_response_curve)
                            } else {
                                stringResource(R.string.equalizer_switch_to_realtime_visualizer)
                            },
                            tint = if (showRealtimeVisualizer) targetColorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Live status pill when visualizer is active
                AnimatedVisibility(
                    visible = showRealtimeVisualizer,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isEnabled && isPlaying) targetColorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val pulseAlpha by rememberInfiniteTransition(label = "LivePulse").animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "PulseAlpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isEnabled && isPlaying) targetColorScheme.primary.copy(alpha = pulseAlpha)
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                            )
                            Text(
                                text = if (isEnabled && isPlaying) stringResource(R.string.equalizer_live_audio) else stringResource(R.string.equalizer_preview_mode),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp,
                                color = if (isEnabled && isPlaying) targetColorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
             
            Box(modifier = Modifier.fillMaxSize().padding(top = 28.dp)) {
                AnimatedContent(
                    targetState = showRealtimeVisualizer,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, delayMillis = 40)) +
                                scaleIn(initialScale = 0.96f, animationSpec = tween(220)))
                            .togetherWith(
                                fadeOut(animationSpec = tween(160)) +
                                        scaleOut(targetScale = 0.96f, animationSpec = tween(160))
                            )
                    },
                    label = "FrequencyVisualizerContent"
                ) { isVisualizer ->
                    if (isVisualizer) {
                        RealtimeFrequencyAudioVisualizer(
                            bandLevels = bandLevels,
                            isEnabled = isEnabled,
                            isPlaying = isPlaying,
                            targetColorScheme = targetColorScheme,
                            frequencies = frequencies
                        )
                    } else {
                        HybridFrequencyResponseGraph(
                            bandLevels = bandLevels,
                            isEnabled = isEnabled,
                            primaryColor = targetColorScheme.primary
                        )
                    }
                }
                 
                // Draw simplistic X axis labels
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).offset(y = 4.dp), 
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val labels = if (bandLevels.size > 5) {
                        listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
                    } else {
                         listOf("60", "230", "910", "4k", "14k")
                    }
                    labels.forEach { 
                        Text(it, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Tabs & Sliders
        // Calculate pages (max 3 per page)
        val itemsPerPage = 3
        val pageCount = (bandLevels.size + itemsPerPage - 1) / itemsPerPage
        
        // Dynamic Tabs
        val tabs = if (pageCount == 4) {
            listOf(bandBass, bandLowMids, bandHighMids, bandTreble)
        } else if (pageCount == 2) {
            listOf(bandBassLow, bandMidHigh)
        } else {
            (1..pageCount).map { stringResource(R.string.equalizer_page_n, it) }
        }

        val pagerState = rememberPagerState(pageCount = { pageCount })
        val coroutineScope = rememberCoroutineScope()
        
        // Use pagerState.currentPage as the source of truth to avoid feedback loops
        val selectedTabIndex = pagerState.currentPage
        val showBandPageTabIndicator = false

        Column(modifier = Modifier.padding(horizontal = 0.dp)) {
            // Tabs Row (Matching PresetTabsRow style)
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 12.dp,
                divider = {},
                indicator = {
                    if (showBandPageTabIndicator) {
                         TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedTabIndex = selectedTabIndex),
                            height = 3.dp,
                            width = 20.dp,
                            shape = RoundedCornerShape(3.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { 
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { 
                            Text(
                                title, 
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium
                            ) 
                        },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

             Spacer(modifier = Modifier.height(24.dp))

             HorizontalPager(
                 state = pagerState, 
                 modifier = Modifier.fillMaxWidth(),
                 userScrollEnabled = true,
                 verticalAlignment = Alignment.Top
             ) { page ->
                 // Content for this page
                 val start = page * itemsPerPage
                 val end = minOf(start + itemsPerPage, bandLevels.size)
                 val indices = start until end
                 
                 Column(
                     verticalArrangement = Arrangement.spacedBy(16.dp), // Reduced from 24.dp
                     modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                 ) {
                     indices.forEach { index ->
                         if (index < bandLevels.size) {
                            HybridHorizontalSlider(
                                bandIndex = index,
                                frequency = frequencies.getOrElse(index) { "" },
                                level = bandLevels[index],
                                isEnabled = isEnabled,
                                onLevelChanged = { onBandLevelChanged(index, it) },
                                onOpenFineTune = { onOpenFineTune(index) }
                            )
                         }
                     }
                 }
             }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HybridHorizontalSlider(
    bandIndex: Int,
    frequency: String,
    level: Int,
    isEnabled: Boolean,
    onLevelChanged: (Int) -> Unit,
    onOpenFineTune: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    var lastHapticValue by remember { mutableIntStateOf(level) }
    val zoneName = EducationalFrequencyMarkers.getZoneName(bandIndex)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(isEnabled) {
                detectTapGestures(
                    onDoubleTap = {
                        if (isEnabled) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLevelChanged(0)
                        }
                    }
                )
            }
    ) {
        // Frequency & Educational Zone Label (clickable to open fine tune)
        Column(
            modifier = Modifier
                .width(58.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = isEnabled) { onOpenFineTune() }
                .padding(vertical = 2.dp)
        ) {
            val freqVal = frequency.replace("Hz", "").replace("k", "k")
            Text(
                text = freqVal,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = zoneName,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium
            )
        }

        // Horizontal Slider (Thick Track)
        Box(modifier = Modifier.weight(1f)) {
            androidx.compose.material3.Slider(
                value = level.toFloat(),
                onValueChange = { 
                    val intVal = it.roundToInt()
                    if (intVal != lastHapticValue) {
                        val crossedZero = (lastHapticValue < 0 && intVal >= 0) || (lastHapticValue > 0 && intVal <= 0) || intVal == 0
                        if (crossedZero && intVal == 0) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        lastHapticValue = intVal
                    }
                    onLevelChanged(intVal) 
                },
                valueRange = -15f..15f,
                steps = 0,
                enabled = isEnabled,
                modifier = Modifier.fillMaxWidth(),
                track = { sliderState ->
                     SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(36.dp)
                    )
                }
            )
        }

        // Value Label (clickable to open fine tune)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (level != 0 && isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = isEnabled) { onOpenFineTune() }
        ) {
            Text(
                text = (if (level > 0) "+$level" else "$level") + "dB",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (level != 0 && isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .width(52.dp)
                    .padding(vertical = 4.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun HybridFrequencyResponseGraph(
    bandLevels: List<Int>,
    isEnabled: Boolean,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    frequencies: List<String>? = null
) {
     val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
     
     androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val widthPerBand = size.width / bandLevels.size
        val path = Path()
        
        // Graph Metrics
        val trackHeight = size.height * 0.7f 
        val topOffset = size.height * 0.15f
        
        // Draw Grid Lines (Horizontal)
        // Range -15 to +15. Grid at -10, -5, 0, 5, 10
        val gridLevels = listOf(-10, -5, 0, 5, 10)
        gridLevels.forEach { lvl ->
            val normalized = ((lvl - (-15f)) / (15f - -15f)).coerceIn(0f, 1f)
            val yNormalized = 1f - normalized
            val y = topOffset + (yNormalized * trackHeight)
            
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )
        }
        
        val points = bandLevels.mapIndexed { index, level ->
            val x = (widthPerBand * index) + (widthPerBand / 2)
            val normalized = ((level - (-15f)) / (15f - -15f)).coerceIn(0f, 1f)
            val yNormalized = 1f - normalized
            val y = topOffset + (yNormalized * trackHeight)
            Offset(x, y)
        }
        
        if (points.isNotEmpty()) {
            path.moveTo(points[0].x, points[0].y)
            
            for (i in 0 until points.size - 1) {
                val p0 = points[maxOf(0, i - 1)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[minOf(points.size - 1, i + 2)]
                
                val cp1X = p1.x + (p2.x - p0.x) * 0.2f
                val cp1Y = p1.y + (p2.y - p0.y) * 0.2f
                val cp2X = p2.x - (p3.x - p1.x) * 0.2f
                val cp2Y = p2.y - (p3.y - p1.y) * 0.2f
                
                path.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
            }
            
            // Draw Line
            drawPath(
                path = path,
                color = if (isEnabled) primaryColor else primaryColor.copy(alpha=0.5f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            
            // Draw Dots
            points.forEach { point ->
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = point
                )
            }
        }
     }
}

@Composable
private fun RealtimeFrequencyAudioVisualizer(
    bandLevels: List<Int>,
    isEnabled: Boolean,
    isPlaying: Boolean,
    targetColorScheme: ColorScheme,
    frequencies: List<String>,
    modifier: Modifier = Modifier
) {
    val primaryColor = targetColorScheme.primary
    val tertiaryColor = targetColorScheme.tertiary
    val surfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = surfaceVariant.copy(alpha = 0.15f)

    // Hardware-accelerated continuous waveform & phase clock
    val infiniteTransition = rememberInfiniteTransition(label = "RealtimeAudioVisualizerClock")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AudioPhaseClock"
    )

    // Beat pulse for dynamic low-end punch
    val beatPulse by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BeatPulse"
    )

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val count = bandLevels.size.coerceAtLeast(1)
        val bandWidth = size.width / count
        val trackHeight = size.height * 0.72f
        val topOffset = size.height * 0.12f
        val baseY = topOffset + trackHeight

        // 1. Draw horizontal dB reference grid lines (-10dB, -5dB, 0dB, +5dB, +10dB)
        val gridLevels = listOf(-10, -5, 0, 5, 10)
        gridLevels.forEach { lvl ->
            val norm = ((lvl - (-15f)) / (15f - -15f)).coerceIn(0f, 1f)
            val y = topOffset + ((1f - norm) * trackHeight)
            drawLine(
                color = if (lvl == 0) gridColor.copy(alpha = 0.35f) else gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = if (lvl == 0) 1.5.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (lvl == 0) null else androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )
        }

        // 2. Compute live real-time audio points across all frequency bands
        val points = ArrayList<Offset>(count)
        val barRects = ArrayList<Pair<Offset, Offset>>(count) // (topLeft, bottomRight)
        val bandGains = ArrayList<Float>(count)

        for (i in 0 until count) {
            val bandLevel = bandLevels.getOrElse(i) { 0 }
            bandGains.add(bandLevel.toFloat())

            // Frequency-specific natural audio oscillation
            // Bass bands (lower index): deep rhythmic thumps
            // Mid bands: rich harmonic cycles
            // High bands: fast transients and shimmering air
            val normIndex = i.toFloat() / (count - 1).coerceAtLeast(1)
            val speed = 1.2f + (normIndex * 3.5f)
            val phaseOffset = i * 0.75f

            val rawOscillation = if (isPlaying) {
                // Multi-harmonic synthesized audio signal
                val primaryWave = Math.sin((time * speed + phaseOffset).toDouble()).toFloat()
                val subWave = Math.cos((time * (speed * 0.5f) + phaseOffset * 1.5f).toDouble()).toFloat()
                val rhythmFactor = if (i < 3) beatPulse else 1f
                ((Math.abs(primaryWave) * 0.6f + Math.abs(subWave) * 0.4f) * rhythmFactor).coerceIn(0.15f, 1.0f)
            } else {
                // Gentle resting ambient shimmer when paused
                0.22f + 0.08f * Math.sin((time * 0.8f + phaseOffset).toDouble()).toFloat()
            }

            // Real-time slider effect on audio:
            // When slider is 0dB: unity gain (1.0x)
            // When slider is +15dB: boosted (up to 2.35x)
            // When slider is -15dB: cut/attenuated (down to ~0.12x)
            val sliderGain = if (isEnabled) {
                (1f + (bandLevel / 15f) * 1.15f).coerceIn(0.12f, 2.35f)
            } else {
                1.0f
            }

            val dynamicEnergy = (rawOscillation * sliderGain).coerceIn(0.04f, 1.1f)
            val barHeight = (dynamicEnergy * trackHeight).coerceIn(6.dp.toPx(), trackHeight)

            val centerX = (bandWidth * i) + (bandWidth / 2f)
            val barTopY = (baseY - barHeight).coerceAtLeast(topOffset * 0.5f)

            points.add(Offset(centerX, barTopY))

            val halfBarWidth = (bandWidth * 0.32f).coerceIn(4.dp.toPx(), 16.dp.toPx())
            barRects.add(
                Pair(
                    Offset(centerX - halfBarWidth, barTopY),
                    Offset(centerX + halfBarWidth, baseY)
                )
            )
        }

        // 3. Draw ambient vertical glow lanes for bands with positive gain
        for (i in 0 until count) {
            val gain = bandGains[i]
            if (isEnabled && gain > 0f) {
                val alpha = (gain / 15f) * 0.15f
                val colLeft = bandWidth * i
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = alpha), Color.Transparent),
                        startY = topOffset,
                        endY = baseY
                    ),
                    topLeft = Offset(colLeft, topOffset),
                    size = Size(bandWidth, trackHeight)
                )
            }
        }

        // 4. Draw continuous glowing audio envelope wave connecting the peaks
        if (points.isNotEmpty()) {
            val wavePath = Path()
            val fillPath = Path()

            wavePath.moveTo(points[0].x, points[0].y)
            fillPath.moveTo(points[0].x, baseY)
            fillPath.lineTo(points[0].x, points[0].y)

            for (i in 0 until points.size - 1) {
                val p0 = points[maxOf(0, i - 1)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[minOf(points.size - 1, i + 2)]

                val cp1X = p1.x + (p2.x - p0.x) * 0.22f
                val cp1Y = p1.y + (p2.y - p0.y) * 0.22f
                val cp2X = p2.x - (p3.x - p1.x) * 0.22f
                val cp2Y = p2.y - (p3.y - p1.y) * 0.22f

                wavePath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
                fillPath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
            }

            fillPath.lineTo(points.last().x, baseY)
            fillPath.close()

            // Translucent glowing fill under wave
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        (if (isEnabled) primaryColor else surfaceVariant).copy(alpha = if (isPlaying) 0.35f else 0.18f),
                        Color.Transparent
                    ),
                    startY = topOffset,
                    endY = baseY
                )
            )

            // Outer glow / stroke of the active audio energy line
            drawPath(
                path = wavePath,
                color = if (isEnabled) primaryColor else surfaceVariant.copy(alpha = 0.5f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 5. Draw the real-time audio spectrum bars with gradient & floating peak caps
        val cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        for (i in 0 until count) {
            val (topLeft, bottomRight) = barRects[i]
            val barW = bottomRight.x - topLeft.x
            val barH = bottomRight.y - topLeft.y
            val gain = bandGains[i]

            // Vibrant gradient tailored to slider boost/cut
            val barGradient = if (isEnabled) {
                val topColor = if (gain > 0f) tertiaryColor else primaryColor
                Brush.verticalGradient(
                    colors = listOf(topColor, primaryColor.copy(alpha = 0.6f)),
                    startY = topLeft.y,
                    endY = bottomRight.y
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(surfaceVariant.copy(alpha = 0.4f), surfaceVariant.copy(alpha = 0.15f)),
                    startY = topLeft.y,
                    endY = bottomRight.y
                )
            }

            // Spectrum Bar
            drawRoundRect(
                brush = barGradient,
                topLeft = topLeft,
                size = Size(barW, barH),
                cornerRadius = cornerRadius
            )

            // Floating Peak Cap Line
            val capY = (topLeft.y - 4.dp.toPx()).coerceAtLeast(topOffset * 0.3f)
            val capColor = if (isEnabled) {
                if (gain > 0f) tertiaryColor else Color.White
            } else surfaceVariant.copy(alpha = 0.7f)

            drawLine(
                color = capColor,
                start = Offset(topLeft.x - 1.dp.toPx(), capY),
                end = Offset(bottomRight.x + 1.dp.toPx(), capY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
