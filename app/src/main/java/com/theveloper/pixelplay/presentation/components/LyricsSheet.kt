package com.theveloper.pixelplay.presentation.components

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect

import android.widget.Toast
import com.theveloper.pixelplay.data.lyrics.LyricsAttribution
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.R
import androidx.activity.compose.BackHandler
import com.theveloper.pixelplay.presentation.components.scoped.LyricsPredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.ContentScale
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.AutoScrollingText
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.util.lerp
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.consumePositionChange
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.delay
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.presentation.components.subcomps.FetchLyricsDialog
import com.theveloper.pixelplay.presentation.components.subcomps.PlayerSeekBar
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.BubblesLine
import com.theveloper.pixelplay.presentation.components.lyrics.SplitFaceLyricsView
import com.theveloper.pixelplay.presentation.components.lyrics.CustomLyricsEditor
import com.theveloper.pixelplay.presentation.components.lyrics.ConnectedHeader
import com.theveloper.pixelplay.presentation.components.lyrics.CurrentSectionChip
import com.theveloper.pixelplay.presentation.components.lyrics.SongStructureStrip
import com.theveloper.pixelplay.presentation.components.lyrics.CollapsedNowNextBar
import com.theveloper.pixelplay.presentation.components.lyrics.LyricsConfirmationPill
import com.theveloper.pixelplay.presentation.components.lyrics.ReactionCorner
import com.theveloper.pixelplay.presentation.components.lyrics.ReactionSide
import com.theveloper.pixelplay.presentation.components.lyrics.dismissReactionMenuOnOutsideTap
import com.theveloper.pixelplay.presentation.components.lyrics.rememberReactionMenuState
import androidx.compose.animation.SizeTransform
import androidx.compose.ui.layout.onSizeChanged
import com.theveloper.pixelplay.data.lyrics.SongStructure
import com.theveloper.pixelplay.data.lyrics.SongStructureRepository
import com.theveloper.pixelplay.utils.LyricsCreditText
import com.theveloper.pixelplay.presentation.components.snapping.ExperimentalSnapperApi
import com.theveloper.pixelplay.presentation.components.snapping.SnapperLayoutInfo
import com.theveloper.pixelplay.presentation.components.snapping.rememberLazyListSnapperLayoutInfo
import com.theveloper.pixelplay.presentation.components.snapping.rememberSnapperFlingBehavior
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.presentation.components.subcomps.LyricsMoreBottomSheet
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.pixelplay.data.preferences.dataStore

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.utils.MultiLangRomanizer

private object UnsetRelayoutKey
private class RelayoutTracker { var key: Any? = UnsetRelayoutKey }

/** Largest lyric size (sp) full screen, immersive included. */
private const val MAX_LYRIC_FONT_SP = 40f
/** Largest lyric size (sp) in each face-to-face half. */
private const val MAX_SPLIT_LYRIC_FONT_SP = 28f
/** Landscape: the lyrics half has no header above it, just a little air. */
private val LANDSCAPE_LYRICS_TOP_PADDING = 28.dp

internal data class LyricsSheetColors(
    val container: Color,
    val content: Color,
    val controlContainer: Color,
    val controlContent: Color,
    val accent: Color,
    val accentContent: Color,
    val lyricHighlight: Color,
    val warmHighlight: Color,
    val playPauseContainer: Color,
    val playPauseContent: Color,
    val syncButtonContainer: Color,
    val syncButtonContent: Color,
    /** Quieter surface for secondary chrome (the "Next" half of the collapsed bar, reaction triggers). */
    val surfaceSecondary: Color = controlContainer,
    val onSurfaceSecondary: Color = controlContent,
    /** Tint for the positive reaction trigger / selection ring. */
    val reactionPositive: Color = accent,
    /** Tint for the negative reaction trigger / selection ring. */
    val reactionNegative: Color = accent,
    /** Small confirmation pill ("Playing next ✓"). */
    val toastContainer: Color = accent,
    val toastContent: Color = accentContent
)

internal fun lyricsSheetColors(colorScheme: ColorScheme): LyricsSheetColors {
    val container = colorScheme.primaryContainer
    val content = colorScheme.onPrimaryContainer
    val accent = colorScheme.primary
    val accentContent = colorScheme.onPrimary

    val warmHighlight = resolveBrightWarmColor(
        background = container,
        preferredWarm = colorScheme.tertiary
    )

    return LyricsSheetColors(
        container = container,
        content = content,
        controlContainer = colorScheme.surfaceContainerLowest,
        controlContent = colorScheme.onSurface,
        accent = accent,
        accentContent = accentContent,
        lyricHighlight = preferredContrastColor(
            background = container,
            preferred = accent,
            fallback = content
        ),
        warmHighlight = warmHighlight,
        playPauseContainer = colorScheme.tertiaryFixedDim,
        playPauseContent = colorScheme.onTertiaryFixed,
        syncButtonContainer = colorScheme.secondaryFixedDim,
        syncButtonContent = colorScheme.onSecondaryFixed,
        surfaceSecondary = colorScheme.secondaryContainer,
        onSurfaceSecondary = preferredContrastColor(
            background = colorScheme.secondaryContainer,
            preferred = colorScheme.onSecondaryContainer,
            fallback = colorScheme.onSurface
        ),
        reactionPositive = preferredContrastColor(
            background = container,
            preferred = colorScheme.tertiary,
            fallback = accent,
            minContrastRatio = 3.0
        ),
        reactionNegative = preferredContrastColor(
            background = container,
            preferred = colorScheme.secondary,
            fallback = content,
            minContrastRatio = 3.0
        ),
        toastContainer = colorScheme.inverseSurface,
        toastContent = preferredContrastColor(
            background = colorScheme.inverseSurface,
            preferred = colorScheme.inverseOnSurface,
            fallback = colorScheme.inversePrimary
        )
    )
}

internal fun resolveBrightWarmColor(
    background: Color,
    preferredWarm: Color,
    fallbackLightWarm: Color = Color(0xFFFFB74D), // Luminous warm amber
    fallbackDarkWarm: Color = Color(0xFFC85A17)   // Rich warm amber for light backgrounds
): Color {
    if (contrastRatio(preferredWarm, background) >= 4.5) return preferredWarm
    val isDarkBackground = background.relativeLuminance() < 0.4
    // First try the artwork's own colour, pushed lighter (dark background) or darker (light
    // background) until it reads. Keeps the highlight in the cover's hue family instead of
    // jumping to a fixed amber that can fight the artwork.
    val toneShifted = toneShiftForContrast(preferredWarm, background, lighten = isDarkBackground)
    if (toneShifted != null) return toneShifted
    val candidate = if (isDarkBackground) fallbackLightWarm else fallbackDarkWarm
    if (contrastRatio(candidate, background) >= 4.5) return candidate
    return preferredContrastColor(background, fallbackLightWarm, fallbackDarkWarm)
}

/**
 * Blends [color] toward white ([lighten]) or black in small steps and returns the first step that
 * reaches [minContrastRatio] against [background]. Stops before the colour is washed out entirely
 * (max 70 % blend), returning null so the caller can fall back.
 */
internal fun toneShiftForContrast(
    color: Color,
    background: Color,
    lighten: Boolean,
    minContrastRatio: Double = 4.5
): Color? {
    val target = if (lighten) Color.White else Color.Black
    var step = 1
    while (step <= 14) {
        val fraction = step * 0.05f
        val candidate = androidx.compose.ui.graphics.lerp(color, target, fraction)
        if (contrastRatio(candidate, background) >= minContrastRatio) return candidate
        step++
    }
    return null
}

internal fun preferredContrastColor(
    background: Color,
    preferred: Color,
    fallback: Color,
    minContrastRatio: Double = 4.5
): Color {
    if (contrastRatio(preferred, background) >= minContrastRatio) return preferred
    if (contrastRatio(fallback, background) >= minContrastRatio) return fallback

    val blackContrast = contrastRatio(Color.Black, background)
    val whiteContrast = contrastRatio(Color.White, background)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

private fun contrastRatio(foreground: Color, background: Color): Double {
    val foregroundLuminance = foreground.relativeLuminance()
    val backgroundLuminance = background.relativeLuminance()
    val lighter = maxOf(foregroundLuminance, backgroundLuminance)
    val darker = minOf(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

private fun Color.relativeLuminance(): Double {
    val argb = encodedSrgbArgb()
    val red = linearizedChannel((argb shr 16) and 0xFF)
    val green = linearizedChannel((argb shr 8) and 0xFF)
    val blue = linearizedChannel(argb and 0xFF)
    return (0.2126 * red) + (0.7152 * green) + (0.0722 * blue)
}

private fun Color.encodedSrgbArgb(): Int = (value shr 32).toInt()

private fun linearizedChannel(channel: Int): Double {
    val value = channel / 255.0
    return if (value <= 0.03928) {
        value / 12.92
    } else {
        ((value + 0.055) / 1.055).pow(2.4)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsSheet(
    stablePlayerStateFlow: StateFlow<StablePlayerState>,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSearchUiState: LyricsSearchUiState,
    resetLyricsForCurrentSong: () -> Unit,
    onResetAllLyrics: () -> Unit,
    onSearchLyrics: (Boolean) -> Unit,
    onPickResult: (LyricsSearchResult) -> Unit,
    onManualSearch: (String, String?) -> Unit,
    onImportLyrics: () -> Unit,
    onDismissLyricsSearch: () -> Unit,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    lyricsTextStyle: TextStyle,
    colorScheme: ColorScheme,
    onBackClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    immersiveLyricsEnabled: Boolean,
    immersiveLyricsTimeout: Long,
    isImmersiveTemporarilyDisabled: Boolean,
    onSetImmersiveTemporarilyDisabled: (Boolean) -> Unit,
    onSaveLyricsToFile: (Song, Lyrics, Boolean) -> Unit,
    onTranslateViaAi: () -> Unit,
    /** Saves lyrics written in the custom editor (LRC or plain text) for the current song. */
    onSaveCustomLyrics: (String) -> Unit = {},
    // BottomToggleRow Params
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    swipeThreshold: Dp = 100.dp,
    highlightZoneFraction: Float = 0.08f, // Reduced from 0.22 for less padding
    highlightOffsetDp: Dp = 32.dp,
    autoscrollAnimationSpec: AnimationSpec<Float>? = null, // null = auto-detect from preference
    // ── Now / Next, Add Song, confirmations, reactions ──
    /** The song after the current one (collapsed header's right half). */
    nextUpSongFlow: StateFlow<Song?>? = null,
    /** Tap on the collapsed header's Next half: skip to it now. */
    onPlayNextUpNow: () -> Unit = onNext,
    /** The + button: pick a song in Search to play / queue. Hidden when null. */
    onAddSongClick: (() -> Unit)? = null,
    /** "Playing next ✓" style confirmations after Add Song. */
    confirmations: Flow<com.theveloper.pixelplay.presentation.viewmodel.LyricsConfirmation?>? = null,
    onConfirmationShown: () -> Unit = {},
    /** Reactions in the bottom corners. Hidden when null. */
    onReaction: ((com.theveloper.pixelplay.data.SongReaction) -> Unit)? = null
) {
    // ─── Enter / Exit animation state ────────────────────────────────────────
    // Mirrors the player-sheet pattern: a plain Float in state drives graphicsLayer
    // at draw-phase (no recomposition per frame). 0f = fully visible, 1f = dismissed.
    var backProgress by remember { mutableFloatStateOf(1f) }

    // Draw-phase lambda provider — read only inside graphicsLayer so layout is never
    // re-triggered during the gesture (same technique as SheetVisualState).
    val backProgressProvider = rememberUpdatedState(backProgress)

    // Enter animation: slide up from +6 % height + fade in.
    LaunchedEffect(Unit) {
        val anim = Animatable(1f)
        anim.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioLowBouncy
            )
        ) { backProgress = value }
    }

    // Predictive-back (Android 13+) or plain back on older devices.
    LyricsPredictiveBackHandler(
        enabled = true,
        onProgressChanged = { backProgress = it },
        onBack = onBackClick
    )

    val stablePlayerState by stablePlayerStateFlow.collectAsStateWithLifecycle()
    val sheetColors = remember(colorScheme) { lyricsSheetColors(colorScheme) }
    // Sideways: song + controls on one half, lyrics on the other (and face-to-face goes side by side).
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val backgroundColor = sheetColors.controlContainer
    val onBackgroundColor = sheetColors.controlContent
    val containerColor = sheetColors.container
    val contentColor = sheetColors.content
    val accentColor = sheetColors.accent
    val onAccentColor = sheetColors.accentContent
    val lyricHighlightColor = sheetColors.warmHighlight
    val playPauseColor = sheetColors.playPauseContainer
    val onPlayPauseColor = sheetColors.playPauseContent

    val isLoadingLyrics by remember(stablePlayerState) { derivedStateOf { stablePlayerState.isLoadingLyrics } }
    val lyrics by remember(stablePlayerState) { derivedStateOf { stablePlayerState.lyrics } }
    val isPlaying by remember(stablePlayerState) { derivedStateOf { stablePlayerState.isPlaying } }
    val currentSong by remember(stablePlayerState) { derivedStateOf { stablePlayerState.currentSong } }

    val hasTranslatedLyrics = remember(lyrics) {
        // Translated lyrics read same timestamp on the lrc, not possible in plain type lyrics
        lyrics?.synced?.any { !it.translation.isNullOrBlank() } == true
    }

    val hasRomanizedLyrics = remember(lyrics) {
        val hasSynced = lyrics?.synced?.any { !it.romanization.isNullOrBlank() } == true
        val hasPlain = lyrics?.plain?.any { line ->
            MultiLangRomanizer.isScriptThatNeedsRomanization(line)
        } == true
        hasSynced || hasPlain
    }

    val context = LocalContext.current

    // Read lyrics alignment preference internally from DataStore
    val lyricsAlignmentFlow = remember(context) {
        context.dataStore.data.map { it[stringPreferencesKey("lyrics_alignment")] ?: "left" }
    }
    val lyricsAlignment by lyricsAlignmentFlow.collectAsStateWithLifecycle(initialValue = "left")

    // Read lyrics translation preference internally from DataStore
    val showLyricsTranslationFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("show_lyrics_translation")] ?: true }
    }
    val showLyricsTranslation by showLyricsTranslationFlow.collectAsStateWithLifecycle(initialValue = true)

    // Read lyrics romanization preference internally from DataStore
    val showLyricsRomanizationFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("show_lyrics_romanization")] ?: true }
    }
    val showLyricsRomanization by showLyricsRomanizationFlow.collectAsStateWithLifecycle(initialValue = true)

    // Read animated lyrics preference internally from DataStore
    val useAnimatedLyricsFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("use_animated_lyrics")] ?: false }
    }
    val useAnimatedLyrics by useAnimatedLyricsFlow.collectAsStateWithLifecycle(initialValue = false)

    val animatedLyricsBlurEnabledFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("animated_lyrics_blur_enabled")] ?: true }
    }
    val animatedLyricsBlurEnabled by animatedLyricsBlurEnabledFlow.collectAsStateWithLifecycle(initialValue = true)

    val disableBlurAllOverFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("disable_blur_all_over")] ?: false }
    }
    val disableBlurAllOver by disableBlurAllOverFlow.collectAsStateWithLifecycle(initialValue = false)

    val animatedLyricsBlurStrengthFlow = remember(context) {
        context.dataStore.data.map { it[androidx.datastore.preferences.core.floatPreferencesKey("animated_lyrics_blur_strength")] ?: 2.5f }
    }
    val animatedLyricsBlurStrength by animatedLyricsBlurStrengthFlow.collectAsStateWithLifecycle(initialValue = 2.5f)

    // Read keep-screen-on preference from DataStore
    val keepScreenOnFlow = remember(context) {
        context.dataStore.data.map { it[booleanPreferencesKey("keep_screen_on_lyrics")] ?: false }
    }
    var keepScreenOn by remember { mutableStateOf(false) }
    // Sync DataStore → local state
    LaunchedEffect(Unit) {
        keepScreenOnFlow.collect { keepScreenOn = it }
    }
    val coroutineScope = rememberCoroutineScope()

    // Apply FLAG_KEEP_SCREEN_ON via the window when enabled
    val view = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(keepScreenOn, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && keepScreenOn) {
                keepScreenOn = false
                coroutineScope.launch {
                    context.dataStore.edit { prefs ->
                        prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = false
                    }
                }
            }
        }

        if (keepScreenOn) {
            view.keepScreenOn = true
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            view.keepScreenOn = false
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(keepScreenOn, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && keepScreenOn) {
                keepScreenOn = false
                coroutineScope.launch {
                    context.dataStore.edit { prefs ->
                        prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = false
                    }
                }
            }
        }
        
        if (keepScreenOn) {
            view.keepScreenOn = true
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            view.keepScreenOn = false
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val resolvedAutoscrollSpec = autoscrollAnimationSpec ?: if (useAnimatedLyrics) {
        spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        )
    } else {
        tween(durationMillis = 450, easing = FastOutSlowInEasing)
    }

    var showFetchLyricsDialog by remember { mutableStateOf(false) }
    // Flag to prevent dialog from showing briefly after reset
    var wasResetTriggered by remember { mutableStateOf(false) }
    val highlightModeFlow = remember(context) {
        context.dataStore.data.map { prefs ->
            com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode.fromName(prefs[stringPreferencesKey("lyrics_highlight_mode")])
        }
    }
    val highlightMode by highlightModeFlow.collectAsStateWithLifecycle(initialValue = com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode.AUTO)
    // Save lyrics dialog state
    var showSaveLyricsDialog by remember { mutableStateOf(false) }
    // Hand-written / hand-timed lyrics
    var showCustomLyricsEditor by remember { mutableStateOf(false) }
    var showSyncControls by remember { mutableStateOf(false) }
    var previewSeekPositionMs by remember(currentSong?.id) { mutableStateOf<Long?>(null) }
    var performanceView by remember { mutableStateOf(PerformanceView.Lyrics) }
    // The Instruments page shares one tab controller with the practice panel and options menu.
    val tabPractice = if (performanceView == PerformanceView.Instruments) {
        rememberTabPractice(
            title = currentSong?.title ?: "",
            artist = currentSong?.artist ?: "",
            songId = currentSong?.id,
            songUri = currentSong?.contentUriString,
        )
    } else {
        null
    }
    val tabPracticeState = rememberUpdatedState(tabPractice)

    var showSyncedLyrics by remember(lyrics) {
        mutableStateOf(
            when {
                !lyrics?.synced.isNullOrEmpty() -> true
                !lyrics?.plain.isNullOrEmpty() -> false
                else -> null
            }
        )
    }

    val hasSyncedLyrics = remember(lyrics) {
        !lyrics?.synced.isNullOrEmpty()
    }

    // Immersive Mode State
    var immersiveMode by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showMoreSheet by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION")
    val moreSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // Swipe Gesture State
    val hapticFeedback = LocalHapticFeedback.current
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isSwipeActive by remember { mutableStateOf(false) }
    var hasTriggeredAction by remember { mutableStateOf(false) }
    val swipeThresholdPx = with(LocalDensity.current) { swipeThreshold.toPx() }
    val overlayTranslation = remember { Animatable(0f) }
    val swipeProgress = remember { Animatable(0f) }

    // Reset keep-screen-on when the physical screen goes off (power button / OEM sleep gesture).
    // ACTION_SCREEN_OFF is a guaranteed platform broadcast; no OEM can suppress it.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    keepScreenOn = false
                    coroutineScope.launch {
                        context.dataStore.edit { prefs ->
                            prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = false
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Immersive: the bottom controls can be hidden to give the lyrics / tab the whole screen.
    //  - With a delay: they hide by themselves after that long without a touch; any touch,
    //    or a swipe up from the bottom, brings them back.
    //  - Delay off (manual): they never hide or come back by themselves. Swipe them down to
    //    hide, swipe up from the bottom (or tap the arrow) to bring them back.
    val immersivePageAllows = performanceView == PerformanceView.Instruments || showSyncedLyrics == true
    val immersiveActive = immersiveLyricsEnabled && immersivePageAllows && !isImmersiveTemporarilyDisabled
    val immersiveManual = immersiveActive && immersiveLyricsTimeout <= IMMERSIVE_TIMEOUT_OFF
    val immersiveManualState = rememberUpdatedState(immersiveManual)
    LaunchedEffect(immersiveActive, immersiveManual, lastInteractionTime, immersiveLyricsTimeout) {
        when {
            !immersiveActive -> immersiveMode = false
            // Manual: leave the controls exactly as the user put them.
            immersiveManual -> Unit
            else -> {
                delay(immersiveLyricsTimeout)
                immersiveMode = true
            }
        }
    }

    // Font Scaling
    val fontScale by animateFloatAsState(
        targetValue = if (immersiveMode) 1.4f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "fontScale"
    )
    

    /** Any touch: restarts the auto-hide timer. In manual mode it doesn't unhide the controls. */
    fun resetImmersiveTimer() {
        lastInteractionTime = System.currentTimeMillis()
        if (!immersiveManualState.value) immersiveMode = false
    }

    /** Swipe up from the bottom / the arrow button: always brings the controls back. */
    fun showControlsNow() {
        lastInteractionTime = System.currentTimeMillis()
        immersiveMode = false
    }

    /**
     * Leaves face-to-face mode from the split view itself (tap the song playing now): turns the
     * setting off and brings the normal screen back. The shortcut next to the "show controls"
     * arrow turns it on again.
     */
    fun exitFaceToFace() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        showControlsNow()
        coroutineScope.launch {
            context.editLyricsDisplayPrefs { it[LyricsDisplayPrefKeys.SPLIT_FACE_VIEW] = false }
        }
    }

    // Swipe-down-to-hide for the controls.
    val controlsDragOffset = remember { Animatable(0f) }
    val controlsHideThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    // Upward swipe on the controls opens the guitar / drum practice panel.
    var controlsUpDrag by remember { mutableFloatStateOf(0f) }
    fun hideControlsNow() {
        // Don't touch lastInteractionTime: that would restart the auto-hide effect, which
        // turns immersive back off when auto-hide is disabled in settings.
        immersiveMode = true
    }

    // Face-to-face split view: immersive + setting on + synced lyrics on the lyrics page.
    val lyricsDisplayPrefs by rememberLyricsDisplayPrefs()
    val splitActive = immersiveMode &&
        lyricsDisplayPrefs.splitFaceView &&
        showSyncedLyrics == true &&
        performanceView == PerformanceView.Lyrics &&
        !lyrics?.synced.isNullOrEmpty()
    val splitActiveState = rememberUpdatedState(splitActive)
    val isLandscapeState = rememberUpdatedState(isLandscape)
    var swipeFromTopHalf by remember { mutableStateOf(false) }

    // Song structure (Intro, Verse, Chorus…): found once per song and saved with it, then
    // shown under the header (or beside the divider in face-to-face mode).
    val structureRepository = remember(context) { SongStructureRepository.get(context) }
    val songStructure by produceState<SongStructure?>(
        initialValue = currentSong?.id?.let { structureRepository.cached(it) },
        currentSong?.id,
        lyrics
    ) {
        val song = currentSong
        if (song == null || lyrics?.synced.isNullOrEmpty()) {
            value = null
            return@produceState
        }
        value = structureRepository.cached(song.id)
        value = structureRepository.resolve(song, lyrics)
    }
    // Kept while the strip animates out, so it doesn't empty mid-exit.
    var lastSongStructure by remember { mutableStateOf<SongStructure?>(null) }
    LaunchedEffect(songStructure) { songStructure?.let { lastSongStructure = it } }

    // Adaptive expressive typography: each song's own typographic voice (see LyricExpression.kt).
    // It sets the whole song's size, line height, tracking and weight here, each word's look in
    // the lines, and how words and lines move.
    val expressionProfile by com.theveloper.pixelplay.presentation.components.lyrics.rememberSongExpressionProfile(
        song = currentSong,
        lyrics = lyrics,
        structure = songStructure,
        durationMs = stablePlayerState.totalDuration,
        enabled = lyricsDisplayPrefs.expressiveTypography && lyricsDisplayPrefs.adaptiveTypography
    )
    // "Remove animations" in system settings: words and lines stop lifting and bouncing.
    val systemAnimationsOff = remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
    val voicedTextStyle = remember(lyricsTextStyle, expressionProfile) {
        expressionProfile?.applyTo(lyricsTextStyle) ?: lyricsTextStyle
    }

    // Immersive enlarges the text; the user's size (up to Extra large) multiplies with it, so cap
    // the result: a single word must still fit a narrow phone without clipping.
    val immersiveScaleCap = remember(voicedTextStyle.fontSize) {
        val base = voicedTextStyle.fontSize
        if (base.isSp && base.value > 0f) (MAX_LYRIC_FONT_SP / base.value).coerceAtLeast(1f) else 1.4f
    }
    val effectiveFontScale = fontScale.coerceAtMost(immersiveScaleCap)
    val scaledTextStyle = voicedTextStyle.copy(
        fontSize = voicedTextStyle.fontSize * effectiveFontScale,
        lineHeight = voicedTextStyle.lineHeight * effectiveFontScale
    )

    // Face-to-face halves are short: the user's typography applies, enlarged a little but capped
    // lower than full screen so the previous / current / next lines still fit in each half.
    val splitScale = remember(voicedTextStyle.fontSize) {
        val base = voicedTextStyle.fontSize
        if (base.isSp && base.value > 0f) 1.15f.coerceAtMost((MAX_SPLIT_LYRIC_FONT_SP / base.value).coerceAtLeast(0.85f)) else 1.15f
    }
    val splitTextStyle = voicedTextStyle.copy(
        fontSize = voicedTextStyle.fontSize * splitScale,
        lineHeight = voicedTextStyle.lineHeight * splitScale
    )
    val structureTopExtra by animateDpAsState(
        targetValue = if (songStructure != null && lyricsDisplayPrefs.showSongStructure) 44.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "structureTopExtra"
    )
    val onSectionClick: (com.theveloper.pixelplay.data.lyrics.SongSection) -> Unit = { section ->
        onSeekTo(
            resolveSeekPositionMs(
                lineTimeMs = section.startMs,
                lyricsSyncOffsetMs = lyricsSyncOffset
            )
        )
        resetImmersiveTimer()
    }

    // ── Header: expanded card or compact Now / Next bar ──────────────────────────────────
    val headerCollapsed = lyricsDisplayPrefs.headerCollapsed
    fun setHeaderCollapsed(collapsed: Boolean) {
        if (collapsed == lyricsDisplayPrefs.headerCollapsed) return
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        resetImmersiveTimer()
        coroutineScope.launch {
            context.editLyricsDisplayPrefs { it[LyricsDisplayPrefKeys.HEADER_COLLAPSED] = collapsed }
        }
    }
    val noNextUpSong = remember { kotlinx.coroutines.flow.MutableStateFlow<Song?>(null) }
    val nextUpSong by (nextUpSongFlow ?: noNextUpSong).collectAsStateWithLifecycle()
    // The lists start below the header, whatever its size (expanded, collapsed, with or without
    // the structure strip). 36 dp keeps the original gap under the expanded card.
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val measuredHeaderHeight = with(density) { headerHeightPx.toDp() }
    val headerTopTarget = if (headerHeightPx > 0) measuredHeaderHeight + 36.dp else 130.dp + structureTopExtra
    val lyricsTopPadding by animateDpAsState(
        targetValue = headerTopTarget,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lyricsTopPadding"
    )
    // Re-centres the current line when typography or the header changes (not every frame).
    val lyricsRelayoutKey = remember(voicedTextStyle, headerCollapsed, songStructure != null) {
        Triple(voicedTextStyle, headerCollapsed, songStructure != null)
    }

    // The song structure strip under the header (both header forms share it).
    val structureStripSlot: @Composable (Song?) -> Unit = { visibleFor ->
        // Fully qualified: an enclosing Column makes the ColumnScope overload win otherwise.
        androidx.compose.animation.AnimatedVisibility(
            visible = songStructure != null && visibleFor?.id == currentSong?.id && lyricsDisplayPrefs.showSongStructure,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(360)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(200))
        ) {
            (songStructure ?: lastSongStructure)?.let { structure ->
                SongStructureStrip(
                    structure = structure,
                    playbackPositionFlow = playbackPositionFlow,
                    lyricsSyncOffset = lyricsSyncOffset,
                    positionOverrideMs = previewSeekPositionMs,
                    accentColor = accentColor,
                    onAccentColor = onAccentColor,
                    contentColor = onBackgroundColor,
                    onSectionClick = onSectionClick,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }

    // ── Quiet chrome: secondary controls dim while nobody touches the screen ───────────────
    var chromeIdle by remember { mutableStateOf(false) }
    LaunchedEffect(lastInteractionTime) {
        chromeIdle = false
        delay(if (immersiveLyricsTimeout > IMMERSIVE_TIMEOUT_OFF) immersiveLyricsTimeout else DEFAULT_IMMERSIVE_TIMEOUT_MS)
        chromeIdle = true
    }
    val chromeIdleAlpha by animateFloatAsState(
        targetValue = if (chromeIdle) 0.55f else 1f,
        animationSpec = tween(if (chromeIdle) 600 else 200),
        label = "chromeIdleAlpha"
    )

    // ── Reactions (bottom corners) ──────────────────────────────────────────────────────
    val reactionState = rememberReactionMenuState()
    // Reacting counts as interaction (restarts auto-hide) but doesn't pop the controls back up,
    // so the corners don't jump while a finger is on them.
    LaunchedEffect(reactionState.lastInteractionMs) {
        if (reactionState.lastInteractionMs > 0L) lastInteractionTime = System.currentTimeMillis()
    }
    // A new song closes a menu that was opened for the previous one.
    LaunchedEffect(currentSong?.id) { reactionState.close() }

    // A phone lying between two people shouldn't sleep: keep the screen on while split,
    // without touching the saved keep-screen-on preference. Keyed on keepScreenOn too so it
    // re-applies after the preference effect above re-runs.
    DisposableEffect(splitActive, keepScreenOn) {
        if (splitActive) view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = keepScreenOn
        }
    }

    LaunchedEffect(currentSong, lyrics, isLoadingLyrics) {
        if (currentSong != null && lyrics == null && !isLoadingLyrics) {
            // Only show dialog if reset was not just triggered
            if (!wasResetTriggered) {
                showFetchLyricsDialog = true
            }
        } else if (lyrics != null || isLoadingLyrics) {
            showFetchLyricsDialog = false
            wasResetTriggered = false // Reset the flag when lyrics are loaded
        }
    }

    if (showFetchLyricsDialog) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            FetchLyricsDialog(
                uiState = lyricsSearchUiState,
                currentSong = currentSong,
                onConfirm = onSearchLyrics,
                onPickResult = onPickResult,
                onManualSearch = onManualSearch,
                // Stay on the lyrics screen with no lyrics: the Instruments page, the options
                // menu and "Write your own" all still work from there.
                onDismiss = {
                    showFetchLyricsDialog = false
                    onDismissLyricsSearch()
                },
                onImport = onImportLyrics
            )
        }
    }

    if (showCustomLyricsEditor) {
        CustomLyricsEditor(
            initialLyrics = lyrics,
            playbackPositionFlow = playbackPositionFlow,
            lyricsSyncOffsetMs = lyricsSyncOffset,
            isPlaying = isPlaying,
            onPlayPause = onPlayPause,
            onSeekTo = onSeekTo,
            onSave = { text ->
                showCustomLyricsEditor = false
                onSaveCustomLyrics(text)
            },
            onDismiss = { showCustomLyricsEditor = false },
            accentColor = accentColor,
            onAccentColor = onAccentColor,
            containerColor = containerColor,
            contentColor = contentColor
        )
    }

    // Save Lyrics Dialog
    if (showSaveLyricsDialog && lyrics != null && currentSong != null) {
        val hasSynced = !lyrics?.synced.isNullOrEmpty()
        val hasPlain = !lyrics?.plain.isNullOrEmpty()
        
        AlertDialog(
            onDismissRequest = { showSaveLyricsDialog = false },
            title = { Text(stringResource(R.string.lyrics_save_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.lyrics_save_dialog_message))
                    Spacer(modifier = Modifier.height(16.dp))
                    if (hasSynced) {
                        FilledTonalButton(
                            onClick = {
                                showSaveLyricsDialog = false
                                onSaveLyricsToFile(
                                    currentSong!!,
                                    lyrics!!,
                                    true
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.lyrics_save_synced))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (hasPlain) {
                        OutlinedButton(
                            onClick = {
                                showSaveLyricsDialog = false
                                onSaveLyricsToFile(
                                    currentSong!!,
                                    lyrics!!,
                                    false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.lyrics_save_plain))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSaveLyricsDialog = false }) {
                    Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    

    CompositionLocalProvider(
        com.theveloper.pixelplay.presentation.components.lyrics.LocalLyricExpression provides expressionProfile,
        com.theveloper.pixelplay.presentation.components.lyrics.LocalLyricMotion provides when {
            systemAnimationsOff -> com.theveloper.pixelplay.presentation.components.lyrics.LyricMotion.Still
            else -> expressionProfile?.motion ?: com.theveloper.pixelplay.presentation.components.lyrics.LyricMotion.Default
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                // ─── Enter / Predictive-back exit transformation ──────────────────
                // Read backProgressProvider inside graphicsLayer (draw-phase) — no layout
                // pass is triggered per gesture frame, same pattern as SheetVisualState.
                // 0f = fully visible, 1f = fully dismissed.
                // Effect: scale down to 92 % + slide down 8 % of height + fade to 72 % alpha.
                // Matches Android predictive back spec for full-screen destinations and
                // mirrors the scale+alpha treatment used across the rest of the app.
                .graphicsLayer {
                    val p = backProgressProvider.value
                    val scale = lerp(1f, 0.92f, p)
                    scaleX = scale
                    scaleY = scale
                    translationY = lerp(0f, size.height * 0.08f, p)
                }
                .clip(RoundedCornerShape(32.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            isSwipeActive = true
                            hasTriggeredAction = false
                            dragOffset = 0f
                            // In the split view the top half (the left half in landscape) is read
                            // from the other side of the phone, so left/right are reversed for them.
                            swipeFromTopHalf = splitActiveState.value && if (isLandscapeState.value) {
                                startOffset.x < size.width / 2f
                            } else {
                                startOffset.y < size.height / 2f
                            }
                            resetImmersiveTimer()
                            coroutineScope.launch {
                                swipeProgress.snapTo(0f)
                            }
                        },
                        onDragEnd = {
                            isSwipeActive = false
                            val committed = abs(dragOffset) > swipeThresholdPx && !hasTriggeredAction 
                        
                            if (committed) {
                                val goNext = (dragOffset < 0) != swipeFromTopHalf
                                if (goNext) onNext() else onPrev()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }

                            coroutineScope.launch {
                                 swipeProgress.animateTo(0f, tween(200))
                                 dragOffset = 0f
                            }
                        },
                        onDragCancel = {
                            isSwipeActive = false
                            dragOffset = 0f
                            coroutineScope.launch {
                                swipeProgress.animateTo(0f, tween(200))
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            resetImmersiveTimer()
                        
                            if (!hasTriggeredAction) {
                                dragOffset += dragAmount.x
                                val progress = (abs(dragOffset) / swipeThresholdPx).coerceIn(0f, 1f)
                            
                                coroutineScope.launch {
                                    swipeProgress.snapTo(progress)
                                }
                            }
                        }
                    )
                },
            containerColor = containerColor,
            contentColor = contentColor,
            // Removed TopBar and FAB
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // While a reaction menu is open, a tap anywhere else only closes it.
                    .dismissReactionMenuOnOutsideTap(reactionState)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding())
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        resetImmersiveTimer()
                    }
            ) {
                val initialSyncedLineIndex = remember(lyrics, playbackPositionFlow, lyricsSyncOffset) {
                    val syncedLines = withInstrumentalBreaks(lyrics?.synced.orEmpty())
                    val pos = (playbackPositionFlow.value + lyricsSyncOffset).coerceAtLeast(0L)
                    val hasIntro = (syncedLines.firstOrNull()?.time ?: 0) >= 1500 && syncedLines.firstOrNull()?.line?.isNotBlank() == true
                    val introOffset = if (hasIntro) 1 else 0
                    val resolved = resolveCurrentLineIndex(lines = syncedLines, position = pos)
                    if (resolved >= 0) {
                        resolved + introOffset
                    } else {
                        0
                    }
                }
                val syncedListState = rememberLazyListState(
                    initialFirstVisibleItemIndex = initialSyncedLineIndex
                )
                val staticListState = rememberLazyListState()

                LaunchedEffect(showSyncedLyrics) {
                    if (showSyncedLyrics == false) {
                        val syncedList = lyrics?.synced
                        val plainList = lyrics?.plain
                        if (!syncedList.isNullOrEmpty() && !plainList.isNullOrEmpty()) {
                            val currentPos = (playbackPositionFlow.value + lyricsSyncOffset).coerceAtLeast(0L)
                            val activeSyncedIndex = resolveCurrentLineIndex(lines = syncedList, position = currentPos).coerceAtLeast(0)
                            val activeSyncedLine = syncedList.getOrNull(activeSyncedIndex)?.line?.trim().orEmpty()
                            val targetPlainIndex = if (activeSyncedLine.isNotBlank()) {
                                val foundIndex = plainList.indexOfFirst { plainLine ->
                                    val cleanPlain = plainLine.trim()
                                    cleanPlain.equals(activeSyncedLine, ignoreCase = true) ||
                                        (activeSyncedLine.length > 5 && cleanPlain.contains(activeSyncedLine, ignoreCase = true))
                                }
                                if (foundIndex >= 0) foundIndex else activeSyncedIndex.coerceIn(0, plainList.lastIndex)
                            } else {
                                activeSyncedIndex.coerceIn(0, plainList.lastIndex)
                            }
                            staticListState.scrollToItem((targetPlainIndex - 1).coerceAtLeast(0))
                        }
                    }
                }

                // Header, lyrics and controls are slots so the two layouts share them: portrait
                // stacks them (header over the lyrics, controls underneath); landscape puts the
                // song + controls on one half and the lyrics on the other (side chosen in settings).
                val headerBlock: @Composable (Modifier) -> Unit = { headerModifier ->
                    // Track Info Header (Fixed at top). Expanded = the full card (cover, title, artist,
                    // visualiser); collapsed = the compact Now / Next bar. Either way the song
                    // structure strip stays attached underneath. Its measured height drives the lyric
                    // lists' top padding, so collapsing hands the space to the lyrics.
                    Box(
                        modifier = headerModifier
                            .zIndex(2f)
                            .onSizeChanged { headerHeightPx = it.height }
                    ) {
                        AnimatedContent(
                            targetState = headerCollapsed,
                            transitionSpec = {
                                androidx.compose.animation.ContentTransform(
                                    targetContentEnter = fadeIn(tween(220, delayMillis = 60)) +
                                        scaleIn(initialScale = 0.96f, animationSpec = tween(260)),
                                    initialContentExit = fadeOut(tween(140)),
                                    sizeTransform = SizeTransform(clip = false)
                                )
                            },
                            label = "headerCollapse"
                        ) { collapsed ->
                            if (collapsed) {
                                ConnectedHeader(
                                    backgroundColor = backgroundColor,
                                    minBottomWidth = 0.dp,
                                    modifier = Modifier
                                        .padding(top = 4.dp, bottom = 16.dp, start = 18.dp, end = 18.dp)
                                        .fillMaxWidth()
                                        .pointerInput(Unit) {
                                            // Swipe down on the bar = expand, same as its button.
                                            detectVerticalDragGestures { change, amount ->
                                                if (amount > 12f) {
                                                    change.consume()
                                                    setHeaderCollapsed(false)
                                                }
                                            }
                                        },
                                    header = {
                                        CollapsedNowNextBar(
                                            currentSong = currentSong,
                                            nextSong = nextUpSong,
                                            isPlaying = isPlaying,
                                            backgroundColor = Color.Transparent,
                                            contentColor = onBackgroundColor,
                                            nextContainerColor = sheetColors.surfaceSecondary,
                                            nextContentColor = sheetColors.onSurfaceSecondary,
                                            accentColor = accentColor,
                                            onExpand = { setHeaderCollapsed(false) },
                                            onNextClick = {
                                                resetImmersiveTimer()
                                                onPlayNextUpNow()
                                            },
                                            nextAlpha = { chromeIdleAlpha }
                                        )
                                    },
                                    bottom = { structureStripSlot(currentSong) }
                                )
                            } else {
                                AnimatedContent(
                                    targetState = currentSong,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(300)) +
                                         scaleIn(initialScale = 0.9f, animationSpec = tween(300)))
                                        .togetherWith(fadeOut(animationSpec = tween(300)))
                                    },
                                    modifier = Modifier.wrapContentWidth(),
                                    label = "headerAnimation"
                                ) { song ->
                                    // Cover, title, artist and visualiser, with the song's structure attached
                                    // right underneath (no gap) as one connected shape.
                                    ConnectedHeader(
                                        backgroundColor = backgroundColor,
                                        modifier = Modifier
                                            .padding(
                                                top = 4.dp, bottom = 24.dp, start = 18.dp, end = 18.dp
                                            )
                                            .animateContentSize() // Animate width changes
                                            .pointerInput(Unit) {
                                                // Swipe up on the card = collapse to the compact bar.
                                                detectVerticalDragGestures { change, amount ->
                                                    if (amount < -12f) {
                                                        change.consume()
                                                        setHeaderCollapsed(true)
                                                    }
                                                }
                                            },
                                        header = {
                                            LyricsTrackInfo(
                                                song = song,
                                                modifier = Modifier.wrapContentWidth(),
                                                backgroundColor = backgroundColor, // Distinct solid background
                                                contentColor = onBackgroundColor,
                                                isPlaying = isPlaying,
                                                onCollapse = { setHeaderCollapsed(true) }
                                            )
                                        },
                                        bottom = { structureStripSlot(song) }
                                    )
                                }
                            }
                        }
                    }
                }

                val lyricsBlock: @Composable BoxScope.(lyricsTop: Dp) -> Unit = { lyricsTop ->
                    when (performanceView) {
                        PerformanceView.Lyrics -> {
                            AnimatedContent(
                                targetState = showSyncedLyrics,
                                transitionSpec = {
                                    if (initialState == false && targetState == true) {
                                        (fadeIn(animationSpec = tween(380, easing = LinearOutSlowInEasing)) +
                                            scaleIn(initialScale = 0.98f, animationSpec = tween(380, easing = LinearOutSlowInEasing)))
                                            .togetherWith(fadeOut(animationSpec = tween(220, easing = FastOutLinearInEasing)))
                                    } else if (initialState == true && targetState == false) {
                                        (fadeIn(animationSpec = tween(380, easing = LinearOutSlowInEasing)) +
                                            scaleIn(initialScale = 1.01f, animationSpec = tween(380, easing = LinearOutSlowInEasing)))
                                            .togetherWith(fadeOut(animationSpec = tween(220, easing = FastOutLinearInEasing)))
                                    } else {
                                        fadeIn(animationSpec = tween(300)).togetherWith(fadeOut(animationSpec = tween(300)))
                                    }
                                },
                                label = "LyricsModeAnimatedContent",
                                modifier = Modifier.fillMaxSize()
                            ) { currentShowSynced ->
                                when (currentShowSynced) {
                                    null -> {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(top = (lyricsTop - 20.dp).coerceAtLeast(0.dp), bottom = 24.dp, start = 24.dp, end = 24.dp)
                                        ) {
                                            item(key = "loader_or_empty") {
                                                Box(
                                                    modifier = Modifier
                                                        .fillParentMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isLoadingLyrics) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            Text(
                                                                text = stringResource(R.string.lyrics_loading),
                                                                style = MaterialTheme.typography.titleMedium
                                                            )
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            LinearWavyProgressIndicator(
                                                                trackColor = accentColor.copy(alpha = 0.4f),
                                                                color = accentColor,
                                                                modifier = Modifier.width(100.dp)
                                                            )
                                                        }
                                                    } else {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center,
                                                            modifier = Modifier.padding(horizontal = 32.dp)
                                                        ) {
                                                            BubblesLine(
                                                                positionFlow = playbackPositionFlow,
                                                                time = 0,
                                                                color = lyricHighlightColor,
                                                                nextTime = Int.MAX_VALUE,
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.height(12.dp))
                                                            Text(
                                                                text = stringResource(R.string.lyrics_no_lyrics),
                                                                style = MaterialTheme.typography.titleMedium.copy(
                                                                    color = contentColor.copy(alpha = 0.6f),
                                                                    fontWeight = FontWeight.Medium
                                                                ),
                                                                textAlign = TextAlign.Center
                                                            )
                                                            Spacer(modifier = Modifier.height(16.dp))
                                                            // The toolbar below still has Instruments and the options menu.
                                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                                OutlinedButton(onClick = { showFetchLyricsDialog = true }) {
                                                                    Text("Search lyrics", color = contentColor)
                                                                }
                                                                FilledTonalButton(onClick = { showCustomLyricsEditor = true }) {
                                                                    Text("Write your own")
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    true -> {
                                        lyrics?.synced?.let { synced ->
                                            SyncedLyricsList(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 24.dp),
                                                contentPadding = PaddingValues(top = lyricsTop, bottom = 100.dp),
                                                lines = synced,
                                                listState = syncedListState,
                                                playbackPositionFlow = playbackPositionFlow,
                                                lyricsSyncOffset = lyricsSyncOffset,
                                                positionOverrideMs = previewSeekPositionMs,
                                                accentColor = lyricHighlightColor,
                                                textStyle = scaledTextStyle,
                                                onLineClick = { syncedLine -> 
                                                    onSeekTo(
                                                        resolveSeekPositionMs(
                                                            lineTimeMs = syncedLine.time.toLong(),
                                                            lyricsSyncOffsetMs = lyricsSyncOffset
                                                        )
                                                    )
                                                    resetImmersiveTimer()
                                                },
                                                highlightZoneFraction = highlightZoneFraction,
                                                highlightOffsetDp = highlightOffsetDp,
                                                autoscrollAnimationSpec = resolvedAutoscrollSpec,
                                                useAnimatedLyrics = useAnimatedLyrics,
                                                highlightMode = highlightMode,
                                                animatedLyricsBlurEnabled = animatedLyricsBlurEnabled && !disableBlurAllOver,
                                                animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                                                immersiveMode = immersiveMode,
                                                lyricsAlignment = lyricsAlignment,
                                                showTranslation = showLyricsTranslation,
                                                showRomanization = showLyricsRomanization,
                                                relayoutKey = lyricsRelayoutKey,
                                                onSeekTo = { seekMs ->
                                                    onSeekTo(
                                                        resolveSeekPositionMs(
                                                            lineTimeMs = seekMs,
                                                            lyricsSyncOffsetMs = lyricsSyncOffset
                                                        )
                                                    )
                                                    resetImmersiveTimer()
                                                },
                                                footer = {
                                                    // The real provider; Unison's credit shows even on stored copies.
                                                    LyricsAttribution.creditFor(lyrics)?.let { credit ->
                                                        item(key = "provider_text") {
                                                            LyricsCreditText(
                                                                credit = credit,
                                                                textAlign = TextAlign.Center,
                                                                accentColor = lyricHighlightColor,
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    false -> {
                                        lyrics?.plain?.let { plain ->
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                state = staticListState,
                                                contentPadding = PaddingValues(
                                                    start = 24.dp,
                                                    end = 24.dp,
                                                    top = lyricsTop,
                                                    bottom = 24.dp
                                                )
                                            ) {
                                                itemsIndexed(
                                                    items = plain,
                                                    key = { index, line -> "$index-$line" }
                                                ) { _, line ->
                                                    PlainLyricsLine(
                                                        line = line,
                                                        style = voicedTextStyle,
                                                        lyricsAlignment = lyricsAlignment,
                                                        showTranslation = if (hasTranslatedLyrics) showLyricsTranslation else true,
                                                        showRomanization = if (hasRomanizedLyrics) showLyricsRomanization else true,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .animateItem(
                                                                fadeInSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
                                                                fadeOutSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing),
                                                                placementSpec = spring(
                                                                    stiffness = Spring.StiffnessMediumLow,
                                                                    dampingRatio = Spring.DampingRatioNoBouncy
                                                                )
                                                            )
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                }
                                                LyricsAttribution.creditFor(lyrics)?.let { credit ->
                                                    item(key = "provider_text") {
                                                        LyricsCreditText(
                                                            credit = credit,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    PerformanceView.Instruments -> {
                        tabPractice?.let { practice ->
                            InstrumentsPerformanceView(
                                controller = practice,
                                onBackgroundColor = onBackgroundColor,
                                accentColor = accentColor,
                                modifier = Modifier.fillMaxSize(),
                                // A tap while the controls are hidden only brings them back.
                                onUserInteraction = {
                                    val wasHidden = immersiveMode
                                    resetImmersiveTimer()
                                    // Swallow the tap only if it brought the controls back.
                                    wasHidden && !immersiveMode
                                }
                            )
                        }
                    }
                }
                
                    // Top Gradient for fade (follows the header's height)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(lyricsTop.coerceAtLeast(56.dp))
                            .align(Alignment.TopCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(containerColor, Color.Transparent)
                                )
                            )
                    )

                    // Bottom Gradient for fade
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, containerColor)
                                )
                            )
                    )

                    // "Playing next ✓": just under the header, over the lyrics, never blocking them.
                    if (confirmations != null) {
                        LyricsConfirmationPill(
                            confirmations = confirmations,
                            containerColor = sheetColors.toastContainer,
                            contentColor = sheetColors.toastContent,
                            onShown = onConfirmationShown,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(3f)
                                .padding(top = (lyricsTop - 28.dp).coerceAtLeast(8.dp))
                        )
                    }

                    // Reactions in the empty bottom corners: positive left, negative right. They
                    // overlay the fade area and never push or resize the lyrics.
                    val showReactions = onReaction != null &&
                        currentSong != null &&
                        performanceView == PerformanceView.Lyrics &&
                        !splitActive
                    if (showReactions && onReaction != null) {
                        val cornerBottom by animateDpAsState(
                            targetValue = if (immersiveMode) paddingValues.calculateBottomPadding() + 20.dp else 10.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "reactionCornerBottom"
                        )
                        ReactionCorner(
                            side = ReactionSide.POSITIVE,
                            state = reactionState,
                            triggerContainer = sheetColors.surfaceSecondary,
                            triggerContent = sheetColors.onSurfaceSecondary,
                            ringColor = sheetColors.reactionPositive,
                            onSelect = onReaction,
                            idleAlpha = { chromeIdleAlpha },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .zIndex(3f)
                                .padding(start = 16.dp, bottom = cornerBottom)
                        )
                        ReactionCorner(
                            side = ReactionSide.NEGATIVE,
                            state = reactionState,
                            triggerContainer = sheetColors.surfaceSecondary,
                            triggerContent = sheetColors.onSurfaceSecondary,
                            ringColor = sheetColors.reactionNegative,
                            onSelect = onReaction,
                            idleAlpha = { chromeIdleAlpha },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .zIndex(3f)
                                .padding(end = 16.dp, bottom = cornerBottom)
                        )
                    }
                }

                val controlsBlock: @Composable ColumnScope.(compactControls: Boolean) -> Unit = { compactControls ->
                // Controls Section (Auto-hide in immersive mode)
                AnimatedVisibility(
                    visible = !immersiveMode,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Follows the finger while swiping the controls down.
                            .graphicsLayer {
                                translationY = controlsDragOffset.value
                                alpha = 1f - (controlsDragOffset.value / (size.height.coerceAtLeast(1f) * 1.2f)).coerceIn(0f, 0.6f)
                            }
                            .background(containerColor)
                            .padding(bottom = paddingValues.calculateBottomPadding() + 10.dp, end = 16.dp, start = 16.dp)
                            // Swipe the controls down to hide them now instead of waiting for
                            // the auto-hide timer. Tap anywhere to bring them back.
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        val practice = tabPracticeState.value
                                        if (controlsUpDrag < -controlsHideThresholdPx * 0.6f && practice != null) {
                                            practice.panelExpanded = true
                                        }
                                        controlsUpDrag = 0f
                                        val committed = controlsDragOffset.value > controlsHideThresholdPx
                                        if (committed) {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            hideControlsNow()
                                        }
                                        coroutineScope.launch {
                                            controlsDragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                        }
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            controlsDragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                        }
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        if (dragAmount < 0f || controlsUpDrag < 0f) controlsUpDrag += dragAmount
                                        coroutineScope.launch {
                                            controlsDragOffset.snapTo((controlsDragOffset.value + dragAmount).coerceAtLeast(0f))
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        // Reset timer on any touch down or move in this area
                                        if (event.changes.any { it.pressed }) {
                                             resetImmersiveTimer()
                                        }
                                    }
                                }
                            }
                    ) {
                        // Grab handle: hints that the controls can be swiped down.
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 6.dp, bottom = 4.dp)
                                .size(width = 32.dp, height = 4.dp)
                                .background(contentColor.copy(alpha = 0.25f), CircleShape)
                        )
                                    AnimatedVisibility(
                        visible = showSyncedLyrics == true && lyrics?.synced != null && showSyncControls,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        LyricsSyncControls(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            offsetMillis = lyricsSyncOffset,
                            onOffsetChange = onLyricsSyncOffsetChange,
                            backgroundColor = backgroundColor,
                            accentColor = sheetColors.syncButtonContainer,
                            onAccentColor = sheetColors.syncButtonContent,
                            onBackgroundColor = onBackgroundColor
                        )
                    }

                    // Playback Controls Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Play/Pause Button (Smaller)
                        val playPauseCornerRadius by animateDpAsState(
                            targetValue = if (tabPractice?.isPlaying ?: isPlaying) 18.dp else 50.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "playPauseShape"
                        )

                        Box(
                            modifier = Modifier
                                .size(if (compactControls) 60.dp else 78.dp)
                                .clip(RoundedCornerShape(playPauseCornerRadius))
                                .background(playPauseColor)
                                .clickable {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    // Instruments page: play/pause goes through the tab player
                                    // (count-in, loops, Synth, backing tracks).
                                    val practice = tabPractice
                                    if (practice != null) practice.togglePlay(coroutineScope) else onPlayPause()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = tabPractice?.isPlaying ?: isPlaying,
                                label = "playPauseIconAnimation"
                            ) { playing ->
                                if (playing) {
                                    Icon(
                                        modifier = Modifier.size(32.dp),
                                        imageVector = Icons.Rounded.Pause,
                                        contentDescription = "Pause",
                                        tint = onPlayPauseColor
                                    )
                                } else {
                                    Icon(
                                        modifier = Modifier.size(32.dp),
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = stringResource(R.string.common_play),
                                        tint = onPlayPauseColor
                                    )
                                }
                            }
                        }

                        // Progress Bar
                        LyricsPlaybackSeekBar(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            playbackPositionFlow = playbackPositionFlow,
                            backgroundColor = backgroundColor,
                            onBackgroundColor = onBackgroundColor,
                            accentColor = accentColor,
                            totalDuration = stablePlayerState.totalDuration,
                            onSeekTo = onSeekTo,
                            onSeekPreviewChange = { previewSeekPositionMs = it },
                            isPlaying = isPlaying
                        )

                        // Skip forward, same glyph as the full player's next button. Sits right of
                        // the timeline, so the timeline sits centred between play/pause and skip.
                        val skipInteraction = remember { MutableInteractionSource() }
                        val skipPressed by skipInteraction.collectIsPressedAsState()
                        val skipScale by animateFloatAsState(
                            targetValue = if (skipPressed) 0.86f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "skipPressScale"
                        )
                        Box(
                            modifier = Modifier
                                .size(if (compactControls) 48.dp else 58.dp)
                                .graphicsLayer {
                                    scaleX = skipScale
                                    scaleY = skipScale
                                }
                                .clip(CircleShape)
                                .background(sheetColors.surfaceSecondary)
                                .clickable(
                                    interactionSource = skipInteraction,
                                    indication = androidx.compose.material3.ripple()
                                ) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    resetImmersiveTimer()
                                    onNext()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next song",
                                tint = sheetColors.onSurfaceSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                
                    Spacer(modifier = Modifier.height(if (compactControls) 8.dp else 16.dp))

                    // Floating Toolbar
                    LyricsFloatingToolbar(
                        modifier = Modifier.padding(horizontal = 0.dp),
                        showSyncedLyrics = showSyncedLyrics,
                        hasSyncedLyrics = hasSyncedLyrics,
                        onShowSyncedLyricsChange = { showSyncedLyrics = it },
                        onNavigateBack = {
                            onBackClick()
                        },
                        onMoreClick = { showMoreSheet = true },
                        backgroundColor = backgroundColor,
                        onBackgroundColor = onBackgroundColor,
                        accentColor = accentColor,
                        onAccentColor = onAccentColor,
                        performanceView = performanceView,
                        onPerformanceViewChange = { performanceView = it },
                        instrumentsPanelOpen = tabPractice?.panelExpanded == true,
                        onInstrumentsClick = {
                            resetImmersiveTimer()
                            tabPractice?.let { it.panelExpanded = !it.panelExpanded }
                        },
                        // Pass progress so the back button animates with the gesture (draw-phase).
                        backProgressProvider = { backProgressProvider.value },
                        onAddSongClick = onAddSongClick?.let { open ->
                            {
                                resetImmersiveTimer()
                                open()
                            }
                        },
                    )

                    // Guitar / drum practice panel: the toolbar's Instruments button opens it.
                    tabPractice?.let { practice ->
                        com.theveloper.pixelplay.presentation.components.tabs.TabPracticePanel(
                            controller = practice,
                            onBackgroundColor = onBackgroundColor,
                            accentColor = accentColor,
                            onAccentColor = onAccentColor,
                            containerColor = containerColor,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                 }
                }
                }

                if (!isLandscape) {
                    // Lyrics Content (Weight 1)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Start)
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        headerBlock(Modifier.align(Alignment.TopStart))
                        this.lyricsBlock(lyricsTopPadding)
                    }
                    this.controlsBlock(false)
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Song side: the header (expanded or minimised, with the structure strip)
                        // at the top, the controls shrunk to this half's width at the bottom.
                        val songPane: @Composable RowScope.() -> Unit = {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                headerBlock(Modifier)
                                Spacer(modifier = Modifier.weight(1f))
                                this.controlsBlock(true)
                            }
                        }
                        // Lyrics side: the whole height and width of its half.
                        val lyricsPane: @Composable RowScope.() -> Unit = {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                this.lyricsBlock(LANDSCAPE_LYRICS_TOP_PADDING)
                            }
                        }
                        if (lyricsDisplayPrefs.landscapeLyricsOnLeft) {
                            this.lyricsPane()
                            this.songPane()
                        } else {
                            this.songPane()
                            this.lyricsPane()
                        }
                    }
                }
            }

            if (showMoreSheet) {
                MaterialTheme(
                    colorScheme = colorScheme,
                    typography = MaterialTheme.typography,
                    shapes = MaterialTheme.shapes
                ) {
                    LyricsMoreBottomSheet(
                        onDismissRequest = { showMoreSheet = false },
                        sheetState = moreSheetState,
                        lyrics = lyrics,
                        showSyncedLyrics = showSyncedLyrics == true,
                        isSyncControlsVisible = showSyncControls,
                        highlightMode = highlightMode,
                        onHighlightModeChange = { mode ->
                            coroutineScope.launch { context.dataStore.edit { it[stringPreferencesKey("lyrics_highlight_mode")] = mode.name } }
                        },
                        onResetAllLyrics = onResetAllLyrics,
                        onSaveLyricsAsLrc = { showSaveLyricsDialog = true },
                        onResetImportedLyrics = {
                            wasResetTriggered = true
                            resetLyricsForCurrentSong()
                        },
                        onTranslateViaAi = onTranslateViaAi,
                        onToggleSyncControls = {
                            resetImmersiveTimer()
                            showSyncControls = !showSyncControls
                        },
                        isImmersiveTemporarilyDisabled = isImmersiveTemporarilyDisabled,
                        onSetImmersiveTemporarilyDisabled = {
                            resetImmersiveTimer()
                            onSetImmersiveTemporarilyDisabled(it)
                        },
                        keepScreenOn = keepScreenOn,
                        onKeepScreenOnChange = { enabled ->
                            keepScreenOn = enabled
                            coroutineScope.launch {
                                context.dataStore.edit { prefs ->
                                    prefs[booleanPreferencesKey("keep_screen_on_lyrics")] = enabled
                                }
                            }
                        },
                        lyricsAlignment = lyricsAlignment,
                        onLyricsAlignmentChange = { newAlignment ->
                            coroutineScope.launch {
                                context.dataStore.edit { preferences ->
                                    preferences[stringPreferencesKey("lyrics_alignment")] = newAlignment
                                }
                            }
                        },
                        hasTranslatedLyrics = hasTranslatedLyrics,
                        hasRomanizedLyrics = hasRomanizedLyrics,
                        showTranslation = showLyricsTranslation,
                        showRomanization = showLyricsRomanization,
                        onShowTranslationChange = { enabled ->
                            resetImmersiveTimer()
                            coroutineScope.launch {
                                context.dataStore.edit { preferences ->
                                    preferences[booleanPreferencesKey("show_lyrics_translation")] = enabled
                                }
                            }
                        },
                        onShowRomanizationChange = { enabled ->
                            resetImmersiveTimer()
                            coroutineScope.launch {
                                context.dataStore.edit { preferences ->
                                    preferences[booleanPreferencesKey("show_lyrics_romanization")] = enabled
                                }
                            }
                        },
                        immersiveLyricsEnabled = immersiveLyricsEnabled,
                        showImmersiveToggle = showSyncedLyrics == true || performanceView == PerformanceView.Instruments,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        isFavoriteProvider = isFavoriteProvider,
                        onShuffleToggle = {
                            resetImmersiveTimer()
                            onShuffleToggle()
                        },
                        onRepeatToggle = {
                            resetImmersiveTimer()
                            onRepeatToggle()
                        },
                        onFavoriteToggle = {
                            resetImmersiveTimer()
                            onFavoriteToggle()
                        },
                        containerColor = containerColor,
                        contentColor = contentColor,
                        accentColor = accentColor,
                        onAccentColor = onAccentColor,
                        tertiaryColor = colorScheme.tertiary,
                        onTertiaryColor = colorScheme.onTertiary,
                        onWriteCustomLyrics = { showCustomLyricsEditor = true },
                        onFindLyrics = { showFetchLyricsDialog = true },
                        tabOptions = tabPractice?.let { practice ->
                            {
                                com.theveloper.pixelplay.presentation.components.tabs.TabOptionsSection(
                                    controller = practice,
                                    contentColor = contentColor,
                                    accentColor = accentColor,
                                    itemBackgroundColor = contentColor.copy(alpha = 0.08f)
                                )
                            }
                        }
                    )
                }
            }

           // Face-to-face split view (covers the list and header while active)
           AnimatedVisibility(
                visible = splitActive,
                enter = fadeIn(tween(420, easing = LinearOutSlowInEasing)) +
                    scaleIn(initialScale = 0.97f, animationSpec = tween(420, easing = LinearOutSlowInEasing)),
                exit = fadeOut(tween(220, easing = FastOutLinearInEasing)) +
                    scaleOut(targetScale = 0.98f, animationSpec = tween(220, easing = FastOutLinearInEasing)),
                modifier = Modifier.fillMaxSize()
            ) {
                lyrics?.synced?.takeIf { it.isNotEmpty() }?.let { synced ->
                    val chipStructure = songStructure?.takeIf { lyricsDisplayPrefs.showSongStructure }
                    SplitFaceLyricsView(
                        lines = synced,
                        playbackPositionFlow = playbackPositionFlow,
                        lyricsSyncOffset = lyricsSyncOffset,
                        positionOverrideMs = previewSeekPositionMs,
                        accentColor = lyricHighlightColor,
                        textStyle = splitTextStyle,
                        autoscrollAnimationSpec = resolvedAutoscrollSpec,
                        useAnimatedLyrics = useAnimatedLyrics,
                        highlightMode = highlightMode,
                        animatedLyricsBlurEnabled = animatedLyricsBlurEnabled && !disableBlurAllOver,
                        animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                        lyricsAlignment = lyricsAlignment,
                        showTranslation = showLyricsTranslation,
                        showRomanization = showLyricsRomanization,
                        // Jumping to a line keeps the split open: both people are still reading.
                        onLineClick = { syncedLine ->
                            onSeekTo(
                                resolveSeekPositionMs(
                                    lineTimeMs = syncedLine.time.toLong(),
                                    lyricsSyncOffsetMs = lyricsSyncOffset
                                )
                            )
                        },
                        onSeekTo = { seekMs ->
                            onSeekTo(
                                resolveSeekPositionMs(
                                    lineTimeMs = seekMs,
                                    lyricsSyncOffsetMs = lyricsSyncOffset
                                )
                            )
                        },
                        onBackgroundTap = { resetImmersiveTimer() },
                        relayoutKey = voicedTextStyle,
                        // Only the part playing now, beside the centre divider: left of it for the
                        // person at the bottom, right of it (turned) for the person at the top.
                        sectionChip = if (chipStructure == null) null else { chipMaxWidth ->
                                CurrentSectionChip(
                                    structure = chipStructure,
                                    playbackPositionFlow = playbackPositionFlow,
                                    lyricsSyncOffset = lyricsSyncOffset,
                                    positionOverrideMs = previewSeekPositionMs,
                                    accentColor = lyricHighlightColor,
                                    onAccentColor = onAccentColor,
                                    maxWidth = chipMaxWidth
                                )
                        },
                        // The minimised song bar (never the full card here). Tapping the song
                        // playing now leaves face-to-face mode; the Next half still skips to it.
                        headerPill = { pillModifier ->
                            CollapsedNowNextBar(
                                currentSong = currentSong,
                                nextSong = nextUpSong,
                                isPlaying = isPlaying,
                                backgroundColor = backgroundColor,
                                contentColor = onBackgroundColor,
                                nextContainerColor = sheetColors.surfaceSecondary,
                                nextContentColor = sheetColors.onSurfaceSecondary,
                                accentColor = accentColor,
                                onExpand = {},
                                showExpand = false,
                                onNowClick = { exitFaceToFace() },
                                nowClickLabel = "Leave face-to-face lyrics",
                                onNextClick = {
                                    resetImmersiveTimer()
                                    onPlayNextUpNow()
                                },
                                nextAlpha = { chromeIdleAlpha },
                                modifier = pillModifier.padding(horizontal = 18.dp, vertical = 4.dp)
                            )
                        },
                        isPlaying = isPlaying,
                        onPlayPause = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPlayPause()
                        },
                        playPauseContainer = playPauseColor,
                        playPauseContent = onPlayPauseColor,
                        landscape = isLandscape,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(containerColor)
                    )
                }
            }

           // Face-to-face, landscape: the reactions sit on the centre seam instead of the corners.
           // Positive at the top middle (its reactions drop down out of it and rise back up after
           // a pick); negative at the bottom middle (its reactions rise up out of it).
           val splitReactionCallback = onReaction
           val showSplitReactions = splitReactionCallback != null &&
               currentSong != null &&
               performanceView == PerformanceView.Lyrics &&
               splitActive &&
               isLandscape
           AnimatedVisibility(
               visible = showSplitReactions,
               enter = fadeIn(tween(420, easing = LinearOutSlowInEasing)),
               exit = fadeOut(tween(220, easing = FastOutLinearInEasing)),
               modifier = Modifier
                   .align(Alignment.TopCenter)
                   .zIndex(3f)
                   .padding(top = paddingValues.calculateTopPadding() + 12.dp)
           ) {
               if (splitReactionCallback != null) {
                   ReactionCorner(
                       side = ReactionSide.POSITIVE,
                       state = reactionState,
                       triggerContainer = sheetColors.surfaceSecondary,
                       triggerContent = sheetColors.onSurfaceSecondary,
                       ringColor = sheetColors.reactionPositive,
                       onSelect = splitReactionCallback,
                       idleAlpha = { chromeIdleAlpha },
                       expandDownward = true
                   )
               }
           }
           AnimatedVisibility(
               visible = showSplitReactions,
               enter = fadeIn(tween(420, easing = LinearOutSlowInEasing)),
               exit = fadeOut(tween(220, easing = FastOutLinearInEasing)),
               modifier = Modifier
                   .align(Alignment.BottomCenter)
                   .zIndex(3f)
                   .padding(bottom = paddingValues.calculateBottomPadding() + 12.dp)
           ) {
               if (splitReactionCallback != null) {
                   ReactionCorner(
                       side = ReactionSide.NEGATIVE,
                       state = reactionState,
                       triggerContainer = sheetColors.surfaceSecondary,
                       triggerContent = sheetColors.onSurfaceSecondary,
                       ringColor = sheetColors.reactionNegative,
                       onSelect = splitReactionCallback,
                       idleAlpha = { chromeIdleAlpha }
                   )
               }
           }

           // Landscape: the arrow and its shortcuts sit centred under the song half, not on the seam.
           val overlayShiftX = if (isLandscape) {
               (configuration.screenWidthDp.dp / 4) * (if (lyricsDisplayPrefs.landscapeLyricsOnLeft) 1f else -1f)
           } else 0.dp

           // Swipe up from the bottom edge while the controls are hidden to bring them back
           // (works in both timed and manual immersive).
           if (immersiveMode && !splitActive) {
               var revealDrag by remember { mutableFloatStateOf(0f) }
               Box(
                   modifier = Modifier
                       .align(Alignment.BottomCenter)
                       .fillMaxWidth()
                       // Leaves the corners free for the reaction triggers.
                       .padding(horizontal = if (onReaction != null) 72.dp else 0.dp)
                       .height(96.dp)
                       .pointerInput(Unit) {
                           detectVerticalDragGestures(
                               onDragStart = { revealDrag = 0f },
                               onDragEnd = {
                                   if (revealDrag < -controlsHideThresholdPx * 0.6f) {
                                       hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                       showControlsNow()
                                   }
                                   revealDrag = 0f
                               },
                               onDragCancel = { revealDrag = 0f },
                               onVerticalDrag = { change, amount ->
                                   change.consume()
                                   revealDrag += amount
                               }
                           )
                       }
               )
           }

           // Show Controls Button (Overlay) — replaced by the turned header pill in split view
           AnimatedVisibility(
                visible = immersiveMode && !splitActive,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(x = overlayShiftX)
                    .padding(bottom = 32.dp)
            ) {
                FilledIconButton(
                    onClick = { showControlsNow() },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accentColor,
                        contentColor = onAccentColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Show Controls"
                    )
                }
            }

           // Face-to-face shortcut, just right of the arrow. Turns the saved setting on (the split
           // opens straight away because we're already immersive); it then disappears, and the
           // setting is turned back off from the lyrics options / Settings.
           val canOfferSplit = !lyricsDisplayPrefs.splitFaceView &&
               showSyncedLyrics == true &&
               performanceView == PerformanceView.Lyrics &&
               !lyrics?.synced.isNullOrEmpty()
           AnimatedVisibility(
                visible = immersiveMode && !splitActive && canOfferSplit,
                enter = fadeIn() + scaleIn(initialScale = 0.6f) + slideInVertically { it / 2 },
                exit = fadeOut() + scaleOut(targetScale = 0.6f) + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Arrow is 48 dp wide and centred: 24 dp half-width + 10 dp gap + 18 dp half of this.
                    .offset(x = overlayShiftX + 52.dp)
                    .padding(bottom = 38.dp)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        coroutineScope.launch {
                            context.editLyricsDisplayPrefs {
                                it[LyricsDisplayPrefKeys.SPLIT_FACE_VIEW] = true
                            }
                        }
                    },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentColor.copy(alpha = 0.22f),
                        contentColor = accentColor
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ScreenRotation,
                        contentDescription = "Face-to-face lyrics",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
       
           // Add Song shortcut while the controls are hidden, left of the arrow (mirrors the
           // face-to-face shortcut on the right).
           if (onAddSongClick != null) {
               AnimatedVisibility(
                   visible = immersiveMode && !splitActive,
                   enter = fadeIn() + scaleIn(initialScale = 0.6f) + slideInVertically { it / 2 },
                   exit = fadeOut() + scaleOut(targetScale = 0.6f) + slideOutVertically { it / 2 },
                   modifier = Modifier
                       .align(Alignment.BottomCenter)
                       .offset(x = overlayShiftX - 52.dp)
                       .padding(bottom = 38.dp)
               ) {
                   FilledTonalIconButton(
                       onClick = {
                           resetImmersiveTimer()
                           onAddSongClick()
                       },
                       modifier = Modifier
                           .size(36.dp)
                           .graphicsLayer { alpha = chromeIdleAlpha },
                       colors = IconButtonDefaults.filledTonalIconButtonColors(
                           containerColor = accentColor.copy(alpha = 0.22f),
                           contentColor = accentColor
                       )
                   ) {
                       Icon(
                           imageVector = Icons.Rounded.Add,
                           contentDescription = "Add a song",
                           modifier = Modifier.size(18.dp)
                       )
                   }
               }
           }

           // Swipe Feedback Overlay
           if (isSwipeActive || swipeProgress.value > 0f) {
               // The pill appears on the side the finger started from (physical direction);
               // the icon shows what will happen (flipped for the top half in split view).
               val fromRight = dragOffset < 0
               val isNext = fromRight != swipeFromTopHalf
               val overlayAlignment = if (fromRight) Alignment.CenterEnd else Alignment.CenterStart
               val icon = if (isNext) Icons.Rounded.SkipNext else Icons.Rounded.SkipPrevious
           
               Box(
                   modifier = Modifier
                       .align(overlayAlignment)
                       .size(100.dp) // Base size
                       .padding(
                           start = if(fromRight) 0.dp else 6.dp,
                           end = if(fromRight) 6.dp else 0.dp
                       )
                       .graphicsLayer {
                            val widthPx = size.width
                            val initialOffset = if(fromRight) widthPx else -widthPx
                            translationX = initialOffset * (1f - swipeProgress.value)

                            scaleX = 0.8f + (swipeProgress.value * 0.2f)
                            scaleY = 0.8f + (swipeProgress.value * 0.2f)
                       }
                       .background(
                            color = accentColor, // No alpha modulation
                            shape = RoundedCornerShape(
                                topStart = if(fromRight) 360.dp else 8.dp,
                                bottomStart = if(fromRight) 360.dp else 8.dp,
                                topEnd = if(fromRight) 8.dp else 360.dp,
                                bottomEnd = if(fromRight) 8.dp else 360.dp
                            )
                       ),
                   contentAlignment = Alignment.Center
               ) {
                   Icon(
                       imageVector = icon,
                       contentDescription = null,
                       modifier = Modifier
                           .size(48.dp)
                           .graphicsLayer { rotationZ = if (swipeFromTopHalf) 180f else 0f },
                       tint = onAccentColor
                   )
               }
           }

          }
        }
    }
}

@Composable
private fun LyricsPlaybackSeekBar(
    playbackPositionFlow: StateFlow<Long>,
    backgroundColor: Color,
    onBackgroundColor: Color,
    accentColor: Color,
    totalDuration: Long,
    onSeekTo: (Long) -> Unit,
    onSeekPreviewChange: (Long?) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val playbackPosition by playbackPositionFlow.collectAsStateWithLifecycle()

    PlayerSeekBar(
        backgroundColor = backgroundColor,
        onBackgroundColor = onBackgroundColor,
        primaryColor = accentColor,
        currentPosition = playbackPosition,
        totalDuration = totalDuration,
        onSeek = onSeekTo,
        onSeekPreview = onSeekPreviewChange,
        isPlaying = isPlaying,
        modifier = modifier
    )
}

@OptIn(ExperimentalSnapperApi::class)
@Composable
fun SyncedLyricsList(
    lines: List<SyncedLine>,
    listState: LazyListState,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffset: Int,
    positionOverrideMs: Long? = null,
    accentColor: Color,
    textStyle: TextStyle,
    onLineClick: (SyncedLine) -> Unit,
    highlightZoneFraction: Float,
    highlightOffsetDp: Dp,
    autoscrollAnimationSpec: AnimationSpec<Float>,
    useAnimatedLyrics: Boolean = false,
    highlightMode: com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode = com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode.AUTO,
    animatedLyricsBlurEnabled: Boolean = true,
    animatedLyricsBlurStrength: Float = 2.5f,
    immersiveMode: Boolean = false,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onSeekTo: ((Long) -> Unit)? = null,
    /**
     * Changes when the list's layout changes for a reason other than playback (user typography,
     * header collapsed / expanded). Must not change every frame (not the animated style).
     */
    relayoutKey: Any? = null,
    footer: LazyListScope.() -> Unit = {}
) {
    // Long pauses get their own music-note row; short ones keep the previous line lit.
    val lines = remember(lines) { withInstrumentalBreaks(lines) }
    val density = LocalDensity.current
    val playbackPosition by playbackPositionFlow.collectAsStateWithLifecycle()
    val position = remember(playbackPosition, lyricsSyncOffset, positionOverrideMs) {
        positionOverrideMs ?: (playbackPosition + lyricsSyncOffset).coerceAtLeast(0L)
    }
    val isPreviewSeeking = positionOverrideMs != null
    // Per-frame position for the word / letter fill (read only in draw lambdas).
    val lyricsClock = com.theveloper.pixelplay.presentation.components.lyrics.rememberLyricsClock(
        positionFlow = playbackPositionFlow,
        syncOffsetMs = lyricsSyncOffset,
        positionOverrideMs = positionOverrideMs
    )

    val firstLine = remember(lines) { lines.firstOrNull() }
    val lastLine = remember(lines) { lines.lastOrNull() }
    val hasExplicitIntroLine = remember(firstLine) { firstLine?.line?.isBlank() == true }
    val hasIntro = remember(firstLine, hasExplicitIntroLine) {
        firstLine != null && firstLine.time >= 1500 && !hasExplicitIntroLine
    }
    val introOffset = if (hasIntro) 1 else 0
    val introEndTime = firstLine?.time ?: 0

    val hasExplicitOutroLine = remember(lastLine) { lastLine?.line?.isBlank() == true }
    val hasOutro = remember(lastLine, hasExplicitOutroLine) {
        lastLine != null && !hasExplicitOutroLine
    }
    val lastLineEndTime = remember(lastLine) {
        lastLine?.let { resolveLineEndTimeMs(it, Int.MAX_VALUE) } ?: 0L
    }
    val outroItemIndex = lines.size + introOffset

    val currentLineIndex by remember(position, lines) {
        derivedStateOf {
            resolveCurrentLineIndex(lines = lines, position = position)
        }
    }

    val activeLazyIndex by remember(
        position,
        lines,
        currentLineIndex,
        hasIntro,
        introEndTime,
        hasOutro,
        lastLineEndTime,
        outroItemIndex,
        introOffset
    ) {
        derivedStateOf {
            when {
                hasIntro && position < introEndTime -> 0
                currentLineIndex in lines.indices -> currentLineIndex + introOffset
                hasOutro && position >= lastLineEndTime -> outroItemIndex
                else -> -1
            }
        }
    }

    var hasAlignedInitialLine by remember(lines) { mutableStateOf(false) }
    var lastAutoScrolledLineIndex by remember(lines) { mutableIntStateOf(-1) }
    // Plain holder (not state): only read / written inside the autoscroll effect.
    val relayoutTracker = remember { RelayoutTracker() }
    var highlightBloomTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        highlightBloomTrigger = true
    }

    BoxWithConstraints(modifier = modifier) {
        val metrics = remember(maxHeight, highlightZoneFraction, highlightOffsetDp) {
            calculateHighlightMetrics(maxHeight, highlightZoneFraction, highlightOffsetDp)
        }
        val highlightOffsetPx = remember(highlightOffsetDp, density) { with(density) { highlightOffsetDp.toPx() } }

        val snapperLayoutInfo = rememberLazyListSnapperLayoutInfo(
            lazyListState = listState,
            snapOffsetForItem = { layoutInfo, item ->
                val viewportHeight = layoutInfo.endScrollOffset - layoutInfo.startScrollOffset
                highlightSnapOffsetPx(viewportHeight, item.size, highlightOffsetPx)
            }
        )
        val flingBehavior = rememberSnapperFlingBehavior(layoutInfo = snapperLayoutInfo)

        // Typography and top padding are keys too: a new font / size / spacing or the header
        // collapsing re-centres the current line straight away instead of on the next line.
        LaunchedEffect(activeLazyIndex, lines.size, metrics, isPreviewSeeking, relayoutKey) {
            if (lines.isEmpty()) return@LaunchedEffect
            if (activeLazyIndex < 0) return@LaunchedEffect
            if (listState.layoutInfo.totalItemsCount == 0) return@LaunchedEffect

            if (relayoutKey != relayoutTracker.key) {
                val firstRun = relayoutTracker.key === UnsetRelayoutKey
                relayoutTracker.key = relayoutKey
                if (!firstRun && hasAlignedInitialLine && !listState.isScrollInProgress) {
                    // Let the padding / text size animation settle, then re-centre once.
                    delay(360)
                    animateToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = activeLazyIndex,
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    )
                    lastAutoScrolledLineIndex = activeLazyIndex
                    return@LaunchedEffect
                }
            }

            if (!hasAlignedInitialLine) {
                if (activeLazyIndex > 0) {
                    listState.scrollToItem((activeLazyIndex - 1).coerceAtLeast(0))
                } else {
                    listState.scrollToItem(0)
                }
                animateToSnapIndex(
                    listState = listState,
                    layoutInfo = snapperLayoutInfo,
                    targetIndex = activeLazyIndex,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                )
                hasAlignedInitialLine = true
                lastAutoScrolledLineIndex = activeLazyIndex
                return@LaunchedEffect
            }

            if (listState.isScrollInProgress && !isPreviewSeeking) return@LaunchedEffect

            val lineJumpDistance = if (lastAutoScrolledLineIndex >= 0) {
                abs(activeLazyIndex - lastAutoScrolledLineIndex)
            } else {
                0
            }

            if (isPreviewSeeking) {
                if (lineJumpDistance > 2) {
                    listState.scrollToItem(activeLazyIndex)
                    snapToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = activeLazyIndex
                    )
                } else {
                    animateToSnapIndex(
                        listState = listState,
                        layoutInfo = snapperLayoutInfo,
                        targetIndex = activeLazyIndex,
                        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
                    )
                }
                lastAutoScrolledLineIndex = activeLazyIndex
                return@LaunchedEffect
            }

            // Music Style Dynamic Velocity
            val dynamicAnimationSpec = if (useAnimatedLyrics) {
                val currentLineTime = when {
                    hasIntro && activeLazyIndex == 0 -> 0
                    hasOutro && activeLazyIndex == outroItemIndex -> lastLineEndTime.toInt()
                    else -> lines.getOrNull(activeLazyIndex - introOffset)?.time ?: 0
                }
                val nextLineTime = when {
                    hasIntro && activeLazyIndex == 0 -> introEndTime
                    hasOutro && activeLazyIndex == outroItemIndex -> (lastLineEndTime + 3000L).toInt()
                    else -> lines.getOrNull(activeLazyIndex - introOffset + 1)?.time ?: (currentLineTime + 1000)
                }
                val timeDiff = (nextLineTime - currentLineTime).coerceIn(250, 2000)

                tween<Float>(
                    durationMillis = timeDiff,
                    easing = FastOutSlowInEasing
                )
            } else {
                autoscrollAnimationSpec
            }

            animateToSnapIndex(
                listState = listState,
                layoutInfo = snapperLayoutInfo,
                targetIndex = activeLazyIndex,
                animationSpec = dynamicAnimationSpec
            )
            lastAutoScrolledLineIndex = activeLazyIndex
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = contentPadding
            ) {
                if (hasIntro) {
                    item(key = "intro_music_notes") {
                        val parallaxModifier = if (useAnimatedLyrics) {
                            Modifier.graphicsLayer {
                                val currentLayoutInfo = listState.layoutInfo
                                val itemInfo = currentLayoutInfo.visibleItemsInfo.find { it.index == 0 }
                                val itemCenter = itemInfo?.let { it.offset + (it.size / 2f) }
                                val viewportCenter = currentLayoutInfo.viewportEndOffset / 2f
                                val distanceFromCenter = itemCenter?.let { it - viewportCenter } ?: 0f
                                val maxTranslation = 40f
                                val distanceRatio = (distanceFromCenter / viewportCenter).coerceIn(-1f, 1f)
                                translationY = distanceRatio * distanceRatio * distanceRatio * maxTranslation
                            }
                        } else Modifier

                        BubblesLine(
                            positionFlow = playbackPositionFlow,
                            positionOffsetMs = lyricsSyncOffset,
                            time = 0,
                            color = accentColor,
                            nextTime = introEndTime,
                            modifier = parallaxModifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp)
                                .testTag("intro_music_notes"),
                            onClick = onSeekTo?.let { seek -> { seek(0L) } }
                        )
                    }
                }

                itemsIndexed(
                    items = lines,
                    key = { index, item -> "${item.time}_$index" }
                ) { index, line ->
                    val lineItemIndex = index + introOffset
                    val nextTime = lines.getOrNull(index + 1)?.time ?: Int.MAX_VALUE
                    val distanceFromCurrent = if (activeLazyIndex != -1) abs(activeLazyIndex - lineItemIndex) else 100

                    val parallaxModifier = if (useAnimatedLyrics) {
                        Modifier.graphicsLayer {
                            // Calculate translation dynamically inside graphicsLayer to avoid recomposing the row during scroll
                            val currentLayoutInfo = listState.layoutInfo
                            val lineItemInfo = currentLayoutInfo.visibleItemsInfo.find { it.index == lineItemIndex }
                            val itemCenter = lineItemInfo?.let { it.offset + (it.size / 2f) }
                            val viewportCenter = currentLayoutInfo.viewportEndOffset / 2f

                            val distanceFromCenter = itemCenter?.let { it - viewportCenter } ?: 0f

                            val maxTranslation = 40f
                            val distanceRatio = (distanceFromCenter / viewportCenter).coerceIn(-1f, 1f)
                            translationY = distanceRatio * distanceRatio * distanceRatio * maxTranslation
                        }
                    } else Modifier

                    if (line.line.isNotBlank()) {
                        LyricLineRow(
                            line = line,
                            nextTime = nextTime,
                            position = position,
                            clock = lyricsClock,
                            distanceFromCurrent = distanceFromCurrent,
                            useAnimatedLyrics = useAnimatedLyrics,
                            highlightMode = highlightMode,
                            animatedLyricsBlurEnabled = animatedLyricsBlurEnabled,
                            animatedLyricsBlurStrength = animatedLyricsBlurStrength,
                            immersiveMode = immersiveMode,
                            lyricsAlignment = lyricsAlignment,
                            showTranslation = showTranslation,
                            showRomanization = showRomanization,
                            accentColor = accentColor,
                            style = textStyle,
                            highlightBloomTrigger = highlightBloomTrigger,
                            modifier = parallaxModifier
                                .fillMaxWidth()
                                .testTag("synced_line_${line.time}"),
                            onClick = { onLineClick(line) }
                        )
                    } else {
                        BubblesLine(
                            positionFlow = playbackPositionFlow,
                            positionOffsetMs = lyricsSyncOffset,
                            time = line.time,
                            color = accentColor,
                            nextTime = nextTime,
                            modifier = parallaxModifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            onClick = onSeekTo?.let { seek -> { seek(line.time.toLong()) } }
                        )
                    }
                }

                if (hasOutro) {
                    item(key = "outro_music_notes") {
                        val parallaxModifier = if (useAnimatedLyrics) {
                            Modifier.graphicsLayer {
                                val currentLayoutInfo = listState.layoutInfo
                                val itemInfo = currentLayoutInfo.visibleItemsInfo.find { it.index == outroItemIndex }
                                val itemCenter = itemInfo?.let { it.offset + (it.size / 2f) }
                                val viewportCenter = currentLayoutInfo.viewportEndOffset / 2f
                                val distanceFromCenter = itemCenter?.let { it - viewportCenter } ?: 0f
                                val maxTranslation = 40f
                                val distanceRatio = (distanceFromCenter / viewportCenter).coerceIn(-1f, 1f)
                                translationY = distanceRatio * distanceRatio * distanceRatio * maxTranslation
                            }
                        } else Modifier

                        BubblesLine(
                            positionFlow = playbackPositionFlow,
                            positionOffsetMs = lyricsSyncOffset,
                            time = lastLineEndTime.toInt(),
                            color = accentColor,
                            nextTime = Int.MAX_VALUE,
                            modifier = parallaxModifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp)
                                .testTag("outro_music_notes"),
                            onClick = onSeekTo?.let { seek -> { seek(lastLineEndTime) } }
                        )
                    }
                }

                footer()
            }

//            if (metrics.zoneHeight > 0.dp) {
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .offset(y = metrics.topPadding)
//                        .height(metrics.zoneHeight)
//                        .align(Alignment.TopCenter)
//                        .clip(RoundedCornerShape(18.dp))
//                        .background(accentColor.copy(alpha = 0.12f))
//                        .testTag("synced_highlight_zone")
//                )
//            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricLineRow(
    line: SyncedLine,
    nextTime: Int,
    position: Long,
    clock: com.theveloper.pixelplay.presentation.components.lyrics.LyricsClock? = null,
    distanceFromCurrent: Int = 100,
    useAnimatedLyrics: Boolean = false,
    highlightMode: com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode = com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode.AUTO,
    animatedLyricsBlurEnabled: Boolean = true,
    animatedLyricsBlurStrength: Float = 2.5f,
    immersiveMode: Boolean = false,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    accentColor: Color,
    style: TextStyle,
    highlightBloomTrigger: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val sanitizedLine = remember(line.line) { sanitizeLyricLineText(line.line) }
    val sanitizedWords = remember(line.words) {
        line.words?.let(::sanitizeSyncedWords)
    }
    val sanitizedWordClusters = remember(sanitizedWords) {
        sanitizedWords?.takeIf { it.isNotEmpty() }?.let(::clusterSyncedWords)
    }
    val lineEndTime = remember(line, nextTime) {
        resolveLineEndTimeMs(line, nextTime)
    }
    // The fill follows the singing; the line itself stays lit until the next row starts.
    val fillEndTime = remember(line, nextTime) {
        resolveLineSingEndMs(line, nextTime)
    }
    val isCurrentLineRaw by remember(position, line.time, lineEndTime) {
        derivedStateOf { position in line.time.toLong()..<lineEndTime }
    }
    val isCurrentLine = isCurrentLineRaw && highlightBloomTrigger
    val unhighlightedColor = LocalContentColor.current.copy(alpha = 0.38f)
    val lineColor by animateColorAsState(
        targetValue = if (isCurrentLine) accentColor else unhighlightedColor,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "lineColor"
    )
    val activePillColor by animateColorAsState(
        targetValue = if (isCurrentLine) accentColor.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "lineActivePillColor"
    )
    // 0 → 1 as the line becomes current, 1 → 0 as it leaves. Drives the normal↔bold crossfade
    // and how strongly the sung colour shows, so nothing snaps on a line change.
    val lineEmphasis by animateFloatAsState(
        targetValue = if (isCurrentLine) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "lineEmphasis"
    )
    val emphasisProvider: () -> Float = { lineEmphasis }

    // Line → line: the new current line rises into place and lands with a small spring pop
    // (bounce and speed follow the song's motion), while the line it replaces eases back.
    val lineMotion = com.theveloper.pixelplay.presentation.components.lyrics.LocalLyricMotion.current
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(isCurrentLine) {
        if (isCurrentLine) {
            arrival.animateTo(
                1f,
                spring(dampingRatio = lineMotion.lineDamping, stiffness = lineMotion.lineStiffness)
            )
        } else {
            arrival.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    val arrivalRisePx = with(LocalDensity.current) { lineMotion.lineRise.dp.toPx() }
    val arrivalPivotX = when (lyricsAlignment) {
        "center" -> 0.5f
        "right" -> 1f
        else -> 0f
    }
    val arrivalModifier = Modifier.graphicsLayer {
        val a = arrival.value
        if (isCurrentLine) {
            // 0.965 → 1 (a spring overshoot swells it just past 1 before it settles).
            val s = 0.965f + 0.035f * a
            scaleX = s
            scaleY = s
            translationY = (1f - a).coerceAtLeast(0f) * arrivalRisePx
        } else {
            // Leaving (and resting) lines recede a touch.
            val s = 0.985f + 0.015f * a
            scaleX = s
            scaleY = s
        }
        transformOrigin = TransformOrigin(arrivalPivotX, 0.5f)
    }

    // Animated mode: fisheye scaling + alpha based on distance from current line
    val targetScale = if (useAnimatedLyrics) when (distanceFromCurrent) {
        0 -> if (immersiveMode) 1.02f else 1.06f; 1 -> 0.96f; else -> 0.88f
    } else 1f
    // Card padding follows the text: bigger / airier text gets a proportionally roomier card.
    val paddingScale = remember(style) {
        val lh = style.lineHeight
        if (lh.isSp) (lh.value / 28f).coerceIn(0.8f, 1.8f) else 1f
    }
    val targetPadding = (if (useAnimatedLyrics) when (distanceFromCurrent) {
        0 -> 20.dp; 1 -> 14.dp; else -> 8.dp
    } else 10.dp) * paddingScale
    val targetAlpha = if (useAnimatedLyrics) when (distanceFromCurrent) {
        0 -> 1.0f; 1 -> 0.55f; else -> 0.30f
    } else 1f

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (useAnimatedLyrics) spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ) else tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "lineScale"
    )
    val verticalPadding by animateDpAsState(
        targetValue = targetPadding,
        animationSpec = if (useAnimatedLyrics) spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ) else tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "linePadding"
    )
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = if (useAnimatedLyrics) spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ) else tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "lineAlpha"
    )

    // Blur Effect
    val targetBlur = if (useAnimatedLyrics && animatedLyricsBlurEnabled && distanceFromCurrent > 0) {
        (distanceFromCurrent * animatedLyricsBlurStrength).coerceAtMost(10f).dp
    } else 0.dp

    val lineBlurCache = remember { LyricLineBlurCache() }
    val blurRadius by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = if (useAnimatedLyrics) tween(durationMillis = 400) else tween(durationMillis = 200),
        label = "lineBlur"
    )

    // Animated mode: apply graphicsLayer for scale/alpha transforms
    val baseModifier = if (useAnimatedLyrics && !immersiveMode) {
        when (lyricsAlignment) {
            "center" -> modifier.padding(horizontal = 36.dp)
            "right" -> modifier.padding(start = 36.dp)
            else -> modifier.padding(end = 36.dp)
        }
    } else {
        modifier
    }
    val animatedModifier = if (useAnimatedLyrics) {
        baseModifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                transformOrigin = TransformOrigin(
                    pivotFractionX = when (lyricsAlignment) {
                        "center" -> 0.5f
                        "right" -> 1f
                        else -> 0f
                    },
                    pivotFractionY = 0.5f
                )
                // Blur is applied here (draw phase) instead of Modifier.blur(blurRadius):
                // reading the animated radius in composition recomposed every visible line on
                // every frame of the 400 ms line-change animation. Same look (clamped, clipped).
                // Whole-pixel steps + cache: a new RenderEffect is built only when the rounded
                // radius changes, not on every animation frame.
                val radiusPx = kotlin.math.round(blurRadius.toPx())
                if (radiusPx >= 1f) {
                    renderEffect = lineBlurCache.effectFor(radiusPx)
                    clip = true
                } else {
                    renderEffect = null
                    clip = false
                }
            }
    } else baseModifier

    // Roman or Translate Logic
    val translationText = line.translation
    val romanizationText = line.romanization

    val secondaryStyle = remember(style) {
        style.copy(
            fontSize = (style.fontSize.value * 0.75f).sp,
            fontWeight = FontWeight.Normal
        )
    }
    // Resting weight comes from the style (Settings → Lyrics weight); the current line is always
    // a clear step heavier. The reserve layer uses the active weight so a line never reflows
    // when it lights up, whatever font / weight / size is chosen.
    val restLineStyle = remember(style) { style.copy(fontWeight = style.fontWeight ?: FontWeight.Normal) }
    val activeLineStyle = remember(style) { style.copy(fontWeight = activeLyricWeight(style.fontWeight)) }

    val romanizationColor = lineColor.copy(alpha = lineColor.alpha * 0.85f)
    val translationColor = lineColor.copy(alpha = lineColor.alpha * 0.55f)

    val horizontalAlignment = when (lyricsAlignment) {
        "center" -> Alignment.CenterHorizontally
        "right" -> Alignment.End
        else -> Alignment.Start
    }

    val textAlign = when (lyricsAlignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> TextAlign.Left
    }

    val boxAlignment = when (lyricsAlignment) {
        "center" -> Alignment.TopCenter
        "right" -> Alignment.TopEnd
        else -> Alignment.TopStart
    }

    val rowShape = RoundedCornerShape(16.dp)
    // Adaptive expressive typography: this song's profile (null when off). Per-word styles are
    // built once per line and reused for every frame.
    val expressionProfile = com.theveloper.pixelplay.presentation.components.lyrics.LocalLyricExpression.current

    if (highlightMode == com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode.LINE || sanitizedWordClusters.isNullOrEmpty()) {
        Column(
            modifier = animatedModifier
                .then(arrivalModifier)
                .fillMaxWidth()
                .clip(rowShape)
                .background(activePillColor)
                .clickable { onClick() }
                .padding(vertical = verticalPadding, horizontal = 12.dp),
            horizontalAlignment = horizontalAlignment
        ) {
            // Whole-line text in three layers (bold reserve, regular at rest, bold current) that
            // share one set of line breaks, cross-fading with the line's emphasis.
            val lineLength = sanitizedLine.length.toFloat()
            val lineExpression = remember(sanitizedLine, expressionProfile, restLineStyle.fontWeight, line.time, lineEndTime) {
                expressionProfile?.let { profile ->
                    com.theveloper.pixelplay.presentation.components.lyrics.buildLineExpression(
                        text = sanitizedLine,
                        words = com.theveloper.pixelplay.presentation.components.lyrics.expressionWords(
                            text = sanitizedLine, line = line, lineEndMs = lineEndTime, words = null, layout = null
                        ),
                        lineStartMs = line.time.toLong(),
                        profile = profile,
                        restWeight = restLineStyle.fontWeight ?: FontWeight.Normal,
                        activeFor = ::activeLyricWeight
                    )
                }
            }
            com.theveloper.pixelplay.presentation.components.lyrics.LyricLineLayers(
                text = sanitizedLine,
                reserveStyle = activeLineStyle,
                restStyle = restLineStyle,
                activeStyle = activeLineStyle,
                restSpans = lineExpression?.restSpans.orEmpty(),
                activeSpans = lineExpression?.activeSpans.orEmpty(),
                restColor = unhighlightedColor,
                unsungColor = accentColor,
                highlightColor = accentColor,
                textAlign = textAlign,
                emphasis = emphasisProvider,
                sungChars = { lineLength },
                modifier = Modifier.fillMaxWidth()
            )

            if (showRomanization && !romanizationText.isNullOrBlank()) {
                Text(
                    text = romanizationText,
                    style = secondaryStyle,
                    color = romanizationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (showTranslation && !translationText.isNullOrBlank()) {
                Text(
                    text = translationText,
                    style = secondaryStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    } else {
        // Where the line sits relative to now: past lines stay fully sung while they fade
        // out, upcoming lines are unsung; only the current line reads the per-frame clock.
        val linePast = position >= lineEndTime
        val lineStart = line.time.toLong()

        Column(
            modifier = animatedModifier
                .then(arrivalModifier)
                .fillMaxWidth()
                .clip(rowShape)
                .background(activePillColor)
                .clickable { onClick() }
                .padding(vertical = verticalPadding, horizontal = 12.dp),
            horizontalAlignment = horizontalAlignment
        ) {
            // The whole line as one text (natural spacing, apostrophes and kerning), with the
            // sung part painted over it word by word / letter by letter.
            val wordLayout = remember(sanitizedLine, sanitizedWords) {
                com.theveloper.pixelplay.presentation.components.lyrics.buildLyricWordLayout(
                    sanitizedLine,
                    requireNotNull(sanitizedWords)
                )
            }
            val fullLength = wordLayout.text.length.toFloat()
            val lineExpression = remember(wordLayout, expressionProfile, restLineStyle.fontWeight, lineEndTime) {
                expressionProfile?.let { profile ->
                    com.theveloper.pixelplay.presentation.components.lyrics.buildLineExpression(
                        text = wordLayout.text,
                        words = com.theveloper.pixelplay.presentation.components.lyrics.expressionWords(
                            text = wordLayout.text, line = line, lineEndMs = lineEndTime,
                            words = sanitizedWords, layout = wordLayout
                        ),
                        lineStartMs = line.time.toLong(),
                        profile = profile,
                        restWeight = restLineStyle.fontWeight ?: FontWeight.Normal,
                        activeFor = ::activeLyricWeight
                    )
                }
            }
            com.theveloper.pixelplay.presentation.components.lyrics.LyricLineLayers(
                text = wordLayout.text,
                reserveStyle = activeLineStyle,
                restStyle = restLineStyle,
                activeStyle = activeLineStyle,
                restSpans = lineExpression?.restSpans.orEmpty(),
                activeSpans = lineExpression?.activeSpans.orEmpty(),
                restColor = unhighlightedColor,
                unsungColor = unhighlightedColor,
                highlightColor = accentColor,
                textAlign = textAlign,
                emphasis = emphasisProvider,
                wordLayout = wordLayout,
                sungChars = {
                    val now = clock?.now() ?: position
                    when {
                        linePast || now >= fillEndTime -> fullLength
                        now < lineStart -> 0f
                        else -> com.theveloper.pixelplay.presentation.components.lyrics.sungCharsInLine(
                            layout = wordLayout,
                            words = requireNotNull(sanitizedWords),
                            positionMs = now,
                            lineEndMs = fillEndTime,
                            mode = highlightMode
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (showRomanization && !romanizationText.isNullOrBlank()) {
                Text(
                    text = romanizationText,
                    style = secondaryStyle,
                    color = romanizationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (showTranslation && !translationText.isNullOrBlank()) {
                Text(
                    text = translationText,
                    style = secondaryStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun LyricWordSpan(
    word: SyncedWord,
    isHighlighted: Boolean,
    useAnimatedLyrics: Boolean = false,
    style: TextStyle,
    highlightedColor: Color,
    unhighlightedColor: Color,
    modifier: Modifier = Modifier,
    highlightedRanges: List<IntRange>? = null,
    progress: Float? = null
) {
    if (highlightedRanges != null) {
        val text = androidx.compose.ui.text.buildAnnotatedString {
            append(word.word)
            highlightedRanges.forEach { range ->
                addStyle(androidx.compose.ui.text.SpanStyle(color = highlightedColor), range.first, range.last + 1)
            }
        }
        Text(text = text, style = style, color = unhighlightedColor, fontWeight = FontWeight.Bold, modifier = modifier)
        return
    }
    val amount = progress ?: if (isHighlighted) 1f else 0f
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = word.word, style = style, color = unhighlightedColor, fontWeight = FontWeight.Bold)
        Text(text = word.word, style = style, color = highlightedColor, fontWeight = FontWeight.Bold,
            modifier = Modifier.drawWithContent {
                val width = size.width * amount.coerceIn(0f, 1f)
                if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) {
                    clipRect(left = size.width - width) { this@drawWithContent.drawContent() }
                } else {
                    clipRect(right = width) { this@drawWithContent.drawContent() }
                }
            })
    }
}

@Composable
fun PlainLyricsLine(
    line: String,
    style: TextStyle,
    lyricsAlignment: String = "left",
    showTranslation: Boolean = true,
    showRomanization: Boolean = true,
    modifier: Modifier = Modifier
) {
    val sanitizedLines = remember(line) { line.split("\n") }
    val primaryText = remember(sanitizedLines) { if (sanitizedLines.isNotEmpty()) sanitizeLyricLineText(sanitizedLines[0]) else "" }

    val isRomanizedScript = remember(primaryText) {
        MultiLangRomanizer.isScriptThatNeedsRomanization(primaryText)
    }

    val translationText = remember(sanitizedLines, primaryText, isRomanizedScript) {
        if (sanitizedLines.size > 1) {
            val firstExtra = sanitizedLines[1]
            val rest = if (sanitizedLines.size > 2) sanitizedLines.drop(2).joinToString("\n") { sanitizeLyricLineText(it) } else ""
            
            val isLatin = firstExtra.any { it.code in 32..126 } 
            val isFirstRomanization = isRomanizedScript && isLatin

            if (isFirstRomanization) rest else sanitizedLines.drop(1).joinToString("\n") { sanitizeLyricLineText(it) }
        } else ""
    }

    val romanizationText = remember(sanitizedLines, primaryText, isRomanizedScript) {
         if (sanitizedLines.size > 1) {
            val firstExtra = sanitizedLines[1]
            val isLatin = firstExtra.any { it.code in 32..126 } 
            val isFirstRomanization = isRomanizedScript && isLatin
            
            if (isFirstRomanization) sanitizeLyricLineText(firstExtra) else ""
        } else ""
    }
    val textAlign = when (lyricsAlignment) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.Right
        else -> TextAlign.Left
    }

    val horizontalAlignment = when (lyricsAlignment) {
        "center" -> Alignment.CenterHorizontally
        "right" -> Alignment.End
        else -> Alignment.Start
    }

    val translationStyle = remember(style) {
        style.copy(
            fontSize = (style.fontSize.value * 0.75f).sp,
            fontWeight = FontWeight.Normal
        )
    }
    val dullColor = LocalContentColor.current.copy(alpha = 0.50f)
    val translationColor = LocalContentColor.current.copy(alpha = 0.36f)

    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        if (primaryText.isNotBlank()) {
            Text(
                text = primaryText,
                style = style.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.2.sp
                ),
                color = dullColor,
                textAlign = textAlign
            )

            if (showRomanization && romanizationText.isNotBlank()) {
                Text(
                    text = romanizationText,
                    style = translationStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (showTranslation && translationText.isNotBlank()) {
                Text(
                    text = translationText,
                    style = translationStyle,
                    color = translationColor,
                    textAlign = textAlign,
                    modifier = Modifier.padding(top = if (showRomanization && romanizationText.isNotBlank()) 2.dp else 4.dp)
                )
            }
        }
    }
}

private val LeadingTagRegex = Regex("^v\\d+:\\s*", RegexOption.IGNORE_CASE)

internal fun sanitizeLyricLineText(raw: String): String =
    LyricsUtils.stripLrcTimestamps(raw).replace(LeadingTagRegex, "").trimStart()

internal fun sanitizeSyncedWords(words: List<SyncedWord>): List<SyncedWord> =
    buildList {
        words.forEachIndexed { index, word ->
            val sanitized = if (index == 0) LeadingTagRegex.replace(word.word, "") else word.word
            val normalized = sanitized.trim()
            if (normalized.isEmpty()) return@forEachIndexed

            add(
                word.copy(
                    word = normalized,
                    phonemes = word.phonemes?.mapNotNull { phone ->
                        val removedPrefix = word.word.indexOf(normalized).coerceAtLeast(0)
                        val start = phone.characterStart - removedPrefix
                        val end = phone.characterEnd - removedPrefix
                        if (start >= 0 && end <= normalized.length && start < end)
                            phone.copy(characterStart = start, characterEnd = end) else null
                    },
                    startsNewWord = if (isEmpty()) true else word.startsNewWord
                )
            )
        }
    }

internal data class SyncedWordCluster(
    val startIndex: Int,
    val words: List<SyncedWord>
)

internal fun clusterSyncedWords(words: List<SyncedWord>): List<SyncedWordCluster> {
    if (words.isEmpty()) return emptyList()

    val clusters = mutableListOf<SyncedWordCluster>()
    var currentWords = mutableListOf<SyncedWord>()
    var currentStartIndex = 0

    words.forEachIndexed { index, word ->
        if (word.startsNewWord && currentWords.isNotEmpty()) {
            clusters += SyncedWordCluster(startIndex = currentStartIndex, words = currentWords.toList())
            currentWords = mutableListOf()
            currentStartIndex = index
        } else if (currentWords.isEmpty()) {
            currentStartIndex = index
        }

        currentWords += word
    }

    if (currentWords.isNotEmpty()) {
        clusters += SyncedWordCluster(startIndex = currentStartIndex, words = currentWords.toList())
    }

    return clusters
}

internal fun normalizeWordEndTime(
    currentWordTimeMs: Long,
    nextWordTimeMs: Long,
    lineEndTimeMs: Long
): Long {
    val minEnd = currentWordTimeMs + 1L
    val boundedLineEnd = lineEndTimeMs.coerceAtLeast(minEnd)
    return nextWordTimeMs.coerceIn(minEnd, boundedLineEnd)
}

internal fun resolveOutroStartTimeMs(lastLine: SyncedLine): Long {
    if (lastLine.endTime != null) {
        return lastLine.endTime.toLong()
    }
    val wordsEndTime = lastLine.words?.maxOfOrNull {
        it.endTime?.toLong() ?: (it.time.toLong() + 800L)
    }
    if (wordsEndTime != null && wordsEndTime > lastLine.time) {
        return wordsEndTime + 500L
    }
    // Estimate singing duration of last line based on text length: ~150ms per character, clamped between 3.5s and 7s
    val estimatedDurationMs = (lastLine.line.trim().length * 150L).coerceIn(3500L, 7000L)
    return lastLine.time.toLong() + estimatedDurationMs
}

/**
 * When the line stops being the current one: when the next row starts. A short pause after a
 * line keeps it lit (no flicker to "nothing" between lines); long pauses get their own
 * music-note row from [withInstrumentalBreaks], so the line ends where that row begins.
 * The last line ends when its singing ends (then the outro notes take over).
 */
internal fun resolveLineEndTimeMs(line: SyncedLine, nextLineStartMs: Int): Long {
    if (nextLineStartMs == Int.MAX_VALUE) return resolveLineSingEndMs(line, nextLineStartMs)
    val lastWordStart = line.words?.maxOfOrNull { it.time.toLong() } ?: line.time.toLong()
    return maxOf(nextLineStartMs.toLong(), lastWordStart + 1L)
}

/** When the singing of [line] ends (its own end time when the source has one); drives the fill. */
internal fun resolveLineSingEndMs(line: SyncedLine, nextLineStartMs: Int): Long {
    val baseEnd = if (nextLineStartMs == Int.MAX_VALUE) {
        resolveOutroStartTimeMs(line)
    } else {
        line.endTime?.toLong()?.coerceAtMost(nextLineStartMs.toLong()) ?: nextLineStartMs.toLong()
    }
    val lastWordStart = line.words?.maxOfOrNull { it.time.toLong() } ?: line.time.toLong()
    return maxOf(baseEnd, lastWordStart + 1L)
}

/** A pause at least this long between two sung lines shows music notes. */
internal const val INSTRUMENTAL_BREAK_MIN_MS = 5_000L

/**
 * Adds a blank (music-note) row wherever the singing pauses for [INSTRUMENTAL_BREAK_MIN_MS] or
 * more: instrumental breaks, solos and long pauses that the lyrics file doesn't mark. The row
 * starts when the previous line's singing ends (its end time, its last word, or an estimate
 * from its length) and lasts until the next line. Shorter pauses add nothing, so the previous
 * line simply stays current until the next one. Lines the file already separates with a blank
 * line are left alone.
 */
internal fun withInstrumentalBreaks(lines: List<SyncedLine>): List<SyncedLine> {
    if (lines.size < 2) return lines
    val result = ArrayList<SyncedLine>(lines.size + 4)
    var added = false
    for (index in lines.indices) {
        val line = lines[index]
        result.add(line)
        val next = lines.getOrNull(index + 1) ?: continue
        if (line.line.isBlank() || next.line.isBlank()) continue
        val singEnd = resolveOutroStartTimeMs(line).coerceAtLeast(line.time.toLong() + 1_000L)
        if (next.time - singEnd >= INSTRUMENTAL_BREAK_MIN_MS) {
            result.add(SyncedLine(time = singEnd.toInt(), line = ""))
            added = true
        }
    }
    // Same instance when nothing changes, so remember() keys stay stable.
    return if (added) result else lines
}

internal fun resolveHighlightedWordIndex(
    words: List<SyncedWord>,
    positionMs: Long,
    lineStartTimeMs: Long,
    lineEndTimeMs: Long
): Int {
    if (positionMs < lineStartTimeMs || positionMs >= lineEndTimeMs) return -1
    val index = words.indexOfLast { it.time.toLong() <= positionMs }
    return index.takeIf { it >= 0 && (words[it].endTime == null || positionMs < words[it].endTime!!) } ?: -1
}

internal fun resolveSeekPositionMs(
    lineTimeMs: Long,
    lyricsSyncOffsetMs: Int
): Long = (lineTimeMs - lyricsSyncOffsetMs.toLong()).coerceAtLeast(0L)

internal data class HighlightZoneMetrics(
    val topPadding: Dp,
    val bottomPadding: Dp,
    val zoneHeight: Dp,
    val centerFromTop: Dp
)

internal fun calculateHighlightMetrics(
    containerHeight: Dp,
    highlightZoneFraction: Float,
    highlightOffset: Dp
): HighlightZoneMetrics {
    val container = containerHeight.value
    val zoneHeight = (containerHeight * highlightZoneFraction).value.coerceAtLeast(0f)
    val offset = highlightOffset.value
    val minCenter = zoneHeight / 2f
    val maxCenter = (container - zoneHeight / 2f).coerceAtLeast(minCenter)
    val unclampedCenter = container / 2f - offset
    val center = unclampedCenter.coerceIn(minCenter, maxCenter)
    val topPadding = (center - zoneHeight / 2f).coerceAtLeast(0f)
    val bottomPadding = (container - center - zoneHeight / 2f).coerceAtLeast(0f)

    return HighlightZoneMetrics(
        topPadding = topPadding.dp,
        bottomPadding = bottomPadding.dp,
        zoneHeight = zoneHeight.dp,
        centerFromTop = center.dp
    )
}

internal fun highlightSnapOffsetPx(
    viewportHeight: Int,
    itemSize: Int,
    highlightOffsetPx: Float
): Int {
    if (viewportHeight <= 0 || itemSize <= 0) return 0
    if (itemSize >= viewportHeight) return 0
    val viewport = viewportHeight.toFloat()
    val halfItem = itemSize / 2f
    val targetCenter = (viewport / 2f) - highlightOffsetPx
    val clampedCenter = targetCenter.coerceIn(halfItem, viewport - halfItem)
    return (clampedCenter - halfItem).roundToInt()
}

internal suspend fun animateToSnapIndex(
    listState: LazyListState,
    layoutInfo: SnapperLayoutInfo,
    targetIndex: Int,
    animationSpec: AnimationSpec<Float>
) {
    val distance = layoutInfo.distanceToIndexSnap(targetIndex)
    if (distance == 0) return

    listState.scroll {
        var previous = 0f
        AnimationState(initialValue = 0f).animateTo(
            targetValue = distance.toFloat(),
            animationSpec = animationSpec
        ) {
            val delta = value - previous
            val consumed = scrollBy(delta)
            previous = value
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
    }
}

internal suspend fun snapToSnapIndex(
    listState: LazyListState,
    layoutInfo: SnapperLayoutInfo,
    targetIndex: Int
) {
    val distance = layoutInfo.distanceToIndexSnap(targetIndex)
    if (distance == 0) return

    listState.scroll {
        scrollBy(distance.toFloat())
    }
}

internal fun resolveCurrentLineIndex(
    lines: List<SyncedLine>,
    position: Long
): Int {
    if (lines.isEmpty()) return -1

    return lines.withIndex().lastOrNull { (index, line) ->
        val nextTime = lines.getOrNull(index + 1)?.time ?: Int.MAX_VALUE
        val lineEndTime = resolveLineEndTimeMs(line, nextTime)
        position in line.time.toLong()..<lineEndTime
    }?.index ?: -1
}

@Composable
internal fun LyricsTrackInfo(
    song: Song?,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    contentColor: Color,
    isPlaying: Boolean,
    /** Shows a small collapse chevron at the end (lyrics sheet header only). */
    onCollapse: (() -> Unit)? = null
) {
    if (song == null) return

    val albumShape = CircleShape

    // Helper state to stop rotation when paused, but we want it to pause in place?
    // Using infiniteTransition.animateFloat will reset on recomposition if spec changes or stops.
    // For a realistic vinyl pause, we need a manual Animatable that loops.
    // But for simplicity requested: "Animate the cover art to rotate... when music is playing".
    // If we just use conditional Modifier.graphicsLayer rotation, it might jump.
    // Let's use a simpler approach: if isPlaying, rotate.
    
    // Better approach for pausing rotation in place is non-trivial without a dedicated running time state.
    // Given the constraints, I will use a simple AnimatedVisibility or just let it reset, OR
    // use a monotonic clock if possible.
    // Let's stick to infinite transition for running, and maybe 0f for static?
    // Actually, user said "simulate a vinyl record". This implies continuous storage of rotation?
    // I'll try to implement continuous rotation.
    
    val currentRotation = remember { Animatable(0f) }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // Spin forever. 8s per revolution halves the effective per-second animation work
            // vs the original 4s cadence — visually still clearly a rotating "vinyl", but
            // drives fewer Compose invalidations during long listening sessions.
            while (true) {
                currentRotation.animateTo(
                    targetValue = currentRotation.value + 360f,
                    animationSpec = tween(8000, easing = LinearEasing)
                )
            }
        } else {
             currentRotation.stop()
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SmartImage(
            model = song.albumArtUriString ?: R.drawable.rounded_album_24,
            shape = albumShape,
            contentDescription = "Cover Art",
            modifier = Modifier
                .size(66.dp)
                .padding(6.dp)
                .graphicsLayer {
                    rotationZ = currentRotation.value % 360f
                }
                .clip(albumShape),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = false) // Allow shrinking if content is small
                .padding(vertical = 6.dp)
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    //textGeometricTransform = TextGeometricTransform(scaleX = (0.9f)),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = contentColor.copy(alpha = 0.7f),
                    //textGeometricTransform = TextGeometricTransform(scaleX = (0.9f)),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        PlayingEqIcon(
            modifier = Modifier
                .padding(start = 8.dp, end = if (onCollapse != null) 4.dp else 18.dp)
                .size(width = 18.dp, height = 16.dp),
            color = contentColor,
            isPlaying = isPlaying
        )

        if (onCollapse != null) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Collapse song details",
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Reuses the last BlurEffect while the (whole-pixel) radius is unchanged. */
private class LyricLineBlurCache {
    private var radiusPx = -1f
    private var effect: BlurEffect? = null

    fun effectFor(radius: Float): BlurEffect {
        val cached = effect
        if (cached != null && radius == radiusPx) return cached
        return BlurEffect(radius, radius, TileMode.Clamp).also {
            effect = it
            radiusPx = radius
        }
    }
}
