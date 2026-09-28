package com.theveloper.pixelplay.presentation.components.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import com.theveloper.pixelplay.data.model.Lyrics
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LoadingIndicator
// import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults // Removed
// import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState // Removed
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.rememberTooltipState
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.DisposableEffect
import com.theveloper.pixelplay.presentation.components.SongVideoPlaybackController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.presentation.components.queueBusyOutline
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnostics
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AlbumArtQuality
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.FullPlayerLoadingTweaks
import com.theveloper.pixelplay.presentation.components.AlbumCarouselSection
import com.theveloper.pixelplay.presentation.components.lyricsFontFamily
import com.theveloper.pixelplay.presentation.components.rememberEffectiveLyricsFont
import com.theveloper.pixelplay.presentation.components.rememberLyricsDisplayPrefs
import com.theveloper.pixelplay.presentation.components.AutoScrollingTextOnDemand
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import com.theveloper.pixelplay.presentation.components.LyricsSheet
import com.theveloper.pixelplay.presentation.components.BottomGestureBar
import com.theveloper.pixelplay.presentation.components.sideSwipeUpGestures
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.theveloper.pixelplay.presentation.components.detectTapAndVerticalDrag
import com.theveloper.pixelplay.presentation.components.scoped.rememberSmoothProgress
import com.theveloper.pixelplay.presentation.components.subcomps.FetchLyricsDialog
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.AudioMetaUtils.mimeTypeToFormat
import com.theveloper.pixelplay.utils.LyricsImportFailureReason
import com.theveloper.pixelplay.utils.LyricsImportSecurity
import com.theveloper.pixelplay.utils.LyricsImportValidationResult
import com.theveloper.pixelplay.utils.ValidatedLyricsImport
import com.theveloper.pixelplay.utils.formatDuration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import java.util.Locale
import kotlin.math.roundToLong
import com.theveloper.pixelplay.presentation.components.WavySliderExpressive
import com.theveloper.pixelplay.presentation.components.ToggleSegmentButton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.LayoutDirection
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.foundation.BorderStroke
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.pixelplay.data.lyrics.LyricsHighlightMode
import com.theveloper.pixelplay.data.preferences.dataStore
import com.theveloper.pixelplay.presentation.components.resolveBrightWarmColor
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow


private const val PREVIOUS_TRACK_RESTART_THRESHOLD_MS = 10_000L
private const val SKIP_COMMAND_GUARD_MS = 96L

private enum class SkipDirection { PREVIOUS, NEXT }

private suspend fun validateLyricsImport(
    context: Context,
    uri: Uri
): LyricsImportValidationResult = withContext(Dispatchers.IO) {
    val contentResolver = context.contentResolver

    var fileName = ""
    var fileSize: Long? = null
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            fileName = if (nameIndex != -1) cursor.getString(nameIndex) else ""
            fileSize = if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                cursor.getLong(sizeIndex)
            } else {
                null
            }
        }
    }

    contentResolver.openInputStream(uri)?.use { inputStream ->
        LyricsImportSecurity.validateImportedLyricsFile(
            fileName = fileName,
            mimeType = contentResolver.getType(uri),
            inputStream = inputStream,
            reportedSizeBytes = fileSize
        )
    } ?: LyricsImportValidationResult.Invalid(LyricsImportFailureReason.EMPTY_CONTENT)
}

@androidx.annotation.OptIn(UnstableApi::class)
@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullPlayerContent(
    currentSong: Song?,
    currentPlaybackQueue: ImmutableList<Song>,
    currentQueueSourceName: String,
    currentMediaItemIndex: Int = -1,
    isShuffleEnabled: Boolean,
    shuffleTransitionInProgress: Boolean,
    repeatMode: Int,
    allowRealtimeUpdates: Boolean = true,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    carouselStyle: String,
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean = false,
    playerViewModel: PlayerViewModel, // For stable state like totalDuration and lyrics
    // State Providers
    currentPositionProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    isQueueEndedProvider: () -> Boolean = { false },
    playWhenReadyProvider: () -> Boolean,
    isFavoriteProvider: () -> Boolean,
    repeatModeProvider: () -> Int,
    isShuffleEnabledProvider: () -> Boolean,
    totalDurationProvider: () -> Long,
    lyricsProvider: () -> Lyrics? = { null }, 
    // State
    isCastConnecting: Boolean = false,
    // Event Handlers
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCollapse: () -> Unit,
    onShowQueueClicked: () -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit,
    onShowCastClicked: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onShowOptionsClick: (Song) -> Unit = {},
    onNavigateToEqualizer: () -> Unit = {}
) {
    var retainedSong by remember { mutableStateOf(currentSong) }
    LaunchedEffect(currentSong?.id) {
        if (currentSong != null) {
            retainedSong = currentSong
        }
    }

    val song = currentSong ?: retainedSong ?: return // Keep the player visible while transitioning
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    // A request made while this content wasn't composed (e.g. returning from Add Song with the
    // player collapsed) is kept as a pending flag, so it's never lost.
    LaunchedEffect(playerViewModel) {
        playerViewModel.pendingLyricsOpen.collect { pending ->
            if (pending) {
                showLyricsSheet = true
                playerViewModel.consumePendingLyricsOpen()
            }
        }
    }
    var showArtistPicker by rememberSaveable { mutableStateOf(false) }
    var showMixFeedbackSheet by rememberSaveable { mutableStateOf(false) }
    var playerViewMode by rememberSaveable { mutableStateOf("Song") }
    val videoPlayback = remember(song.id, playerViewModel) {
        SongVideoPlaybackController {
            playerViewModel.suspendAudioForVideo(song.id) { playerViewMode = "Song" }
        }
    }
    fun returnToAudio() {
        // The live position (last report + time since), so audio resumes exactly where the
        // video is now rather than up to a report interval earlier.
        val videoPositionMs = videoPlayback.currentPositionMs()
        videoPlayback.stop(resumeAudio = true, resumePositionMs = videoPositionMs)
        playerViewMode = "Song"
    }
    DisposableEffect(videoPlayback) {
        onDispose { videoPlayback.stop() }
    }
    // Video lookups take a NewPipe search round trip; warm it while the user is looking at the
    // expanded player so switching to Video starts loading the clip immediately.
    val hostContextForWarmup = LocalContext.current
    val songVideoViewModel: com.theveloper.pixelplay.presentation.viewmodel.SongVideoViewModel =
        androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
    LaunchedEffect(song.id, currentSheetState) {
        if (currentSheetState == PlayerSheetState.EXPANDED && song.title.isNotBlank()) {
            kotlinx.coroutines.delay(150)
            songVideoViewModel.prefetch(song)
            // Load the WebView engine ahead of time: its first start is the slowest part of
            // opening Video mode.
            com.theveloper.pixelplay.presentation.components.VideoPlayerWarmup.warm(hostContextForWarmup)
        }
    }
    LaunchedEffect(currentSheetState) {
        if (currentSheetState != PlayerSheetState.EXPANDED && playerViewMode == "Video") returnToAudio()
    }

    // --- Video full screen ---------------------------------------------------------------------
    // Rotates to landscape and hides the system bars while on; restores both when turned off.
    var videoFullscreen by rememberSaveable { mutableStateOf(false) }
    var orientationBeforeFullscreen by rememberSaveable { mutableStateOf<Int?>(null) }
    val hostContext = LocalContext.current
    val hostActivity = remember(hostContext) {
        var c: android.content.Context? = hostContext
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        c as? android.app.Activity
    }
    val systemBarsPrefs = com.theveloper.pixelplay.utils.rememberSystemBarsPrefs()
    val latestSystemBarsPrefs by rememberUpdatedState(systemBarsPrefs)
    val windowFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(playerViewMode, currentSheetState) {
        if (playerViewMode != "Video" || currentSheetState != PlayerSheetState.EXPANDED) videoFullscreen = false
    }
    androidx.activity.compose.BackHandler(enabled = videoFullscreen) { videoFullscreen = false }
    DisposableEffect(videoFullscreen, hostActivity) {
        val activity = hostActivity
        if (videoFullscreen && activity != null) {
            if (orientationBeforeFullscreen == null) orientationBeforeFullscreen = activity.requestedOrientation
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            hideSystemBarsForVideo(activity)
        }
        onDispose {
            // A config-change recreation keeps full screen on (the flag is saved); only a real
            // exit gives the orientation and the user's bar setting back.
            if (videoFullscreen && activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = orientationBeforeFullscreen
                    ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                orientationBeforeFullscreen = null
                com.theveloper.pixelplay.utils.applySystemBarsVisibility(activity, latestSystemBarsPrefs)
            }
        }
    }
    // The app re-applies the user's bar setting on resume / focus (dialogs, share sheet…), which
    // would bring the bars back over the video; hide them again.
    LaunchedEffect(videoFullscreen, windowFocused, systemBarsPrefs) {
        if (videoFullscreen && windowFocused) {
            kotlinx.coroutines.delay(50)
            hostActivity?.let(::hideSystemBarsForVideo)
        }
    }

    // --- Audio <-> Video automatic position sync --------------------------------------------
    var previousViewMode by remember { mutableStateOf(playerViewMode) }
    val latestAudioPosition by rememberUpdatedState(currentPositionProvider)

    LaunchedEffect(playerViewMode) {
        val from = previousViewMode
        previousViewMode = playerViewMode
        if (from == playerViewMode) return@LaunchedEffect
        if (playerViewMode == "Video") {
            videoPlayback.seekTo(latestAudioPosition())
        }
    }
    
    val lyricsSearchUiState by playerViewModel.lyricsSearchUiState.collectAsStateWithLifecycle()
    val isQueueSheetVisible by playerViewModel.isQueueSheetVisible.collectAsStateWithLifecycle()
    val isCastSheetVisible by playerViewModel.isCastSheetVisible.collectAsStateWithLifecycle()
    val activeMixFlavor by playerViewModel.activeMixFlavor.collectAsStateWithLifecycle()

    // Single subscription — replaces 11 independent collectAsStateWithLifecycle calls.
    // distinctUntilChanged in the ViewModel ensures this only emits when something
    // actually changed, batching multiple rapid updates into one recomposition.
    val fullPlayerSlice by playerViewModel.fullPlayerSlice.collectAsStateWithLifecycle()
    val currentSongArtists = fullPlayerSlice.currentSongArtists
    val lyricsSyncOffset = fullPlayerSlice.lyricsSyncOffset
    val albumArtQuality = fullPlayerSlice.albumArtQuality
    val playbackAudioMetadata = fullPlayerSlice.audioMetadata
    val showPlayerFileInfo = fullPlayerSlice.showPlayerFileInfo
    val immersiveLyricsEnabled = fullPlayerSlice.immersiveLyricsEnabled
    val immersiveLyricsTimeout = fullPlayerSlice.immersiveLyricsTimeout
    val isImmersiveTemporarilyDisabled = fullPlayerSlice.isImmersiveTemporarilyDisabled
    val isRemotePlaybackActive = fullPlayerSlice.isRemotePlaybackActive
    LaunchedEffect(isRemotePlaybackActive) {
        if (isRemotePlaybackActive && playerViewMode == "Video") {
            videoPlayback.stop()
            playerViewMode = "Song"
        }
    }
    val selectedRouteName = fullPlayerSlice.selectedRouteName
    val isBluetoothEnabled = fullPlayerSlice.isBluetoothEnabled
    val bluetoothName = fullPlayerSlice.bluetoothName
    val navigationBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val queueGestureBottomExclusion = maxOf(20.dp, navigationBarBottomInset + 8.dp)
    val queueGestureBottomExclusionPx = with(LocalDensity.current) {
        queueGestureBottomExclusion.toPx()
    }

    var showFetchLyricsDialog by remember { mutableStateOf(false) }
    var totalDrag by remember { mutableStateOf(0f) }

    val context = LocalContext.current
    val fileImportScope = rememberCoroutineScope()
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                fileImportScope.launch {
                    try {
                        val validation = validateLyricsImport(context, it)
                        val validatedImport: ValidatedLyricsImport = when (validation) {
                            is LyricsImportValidationResult.Valid -> validation.value
                            is LyricsImportValidationResult.Invalid -> {
                                playerViewModel.sendToast(
                                    LyricsImportSecurity.messageFor(validation.reason)
                                )
                                return@launch
                            }
                        }

                        val currentSongId = currentSong?.id?.toLongOrNull()
                        if (currentSongId == null) {
                            playerViewModel.sendToast("No song selected for lyrics import.")
                            return@launch
                        }

                        playerViewModel.importLyricsFromFile(currentSongId, validatedImport)
                        showFetchLyricsDialog = false
                        showLyricsSheet = true
                    } catch (e: Exception) {
                        Timber.e(e, "Error reading imported lyrics file")
                        playerViewModel.sendToast("Error reading file.")
                    }
                }
            }
        }
    )

    // totalDurationValue is derived from stablePlayerState, so it's fine.
    // OPTIMIZATION: Use passed provider instead of collecting flow
    val totalDurationValue = totalDurationProvider()

    val playerOnBaseColor = LocalMaterialTheme.current.onPrimaryContainer
    val playerAccentColor = LocalMaterialTheme.current.primary
    val playerOnAccentColor = LocalMaterialTheme.current.onPrimary
    val transportPlayPauseColors = expressivePlayPauseButtonColors(LocalMaterialTheme.current)
    val transportSkipColors = expressiveSkipButtonColors(LocalMaterialTheme.current)
    val transportSkipButtonColors = TransportButtonColors(
        container = playerAccentColor,
        content = playerOnAccentColor
    )
    val progressActiveColor = playerOnBaseColor

    val placeholderColor = playerOnBaseColor.copy(alpha = 0.1f)
    val placeholderOnColor = playerOnBaseColor.copy(alpha = 0.2f)

    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val isCoverLyricsEnabled by playerViewModel.coverLyricsEnabled.collectAsStateWithLifecycle()
    val disableBlurAllOver by playerViewModel.disableBlurAllOver.collectAsStateWithLifecycle()

    // Alignment, highlight mode, animated lyrics, blur, translation and font are read by the
    // cover overlay and here from one place (LyricsDisplayPrefs), so the cover, the sheet and
    // the lyrics menu can never disagree.
    val lyricsDisplayPrefs by rememberLyricsDisplayPrefs()
    val effectiveLyricsFont by rememberEffectiveLyricsFont(lyricsProvider(), lyricsDisplayPrefs.font)
    // Timeline scrubbing → cover lyrics. While the slider is dragged the cover lyrics follow
    // the scrub position instead of playback; after release they hold the seek target until
    // playback catches up (so they don't flick back to the old line for a frame).
    val coverScrubMs = remember { kotlinx.coroutines.flow.MutableStateFlow<Long?>(null) }
    val coverScrubScope = rememberCoroutineScope()
    val coverScrubReleaseJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
    val coverLyricsPositionFlow = remember(playerViewModel.currentPlaybackPosition) {
        kotlinx.coroutines.flow.combine(playerViewModel.currentPlaybackPosition, coverScrubMs) { position, scrub ->
            scrub ?: position
        }.stateIn(
            coverScrubScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
            playerViewModel.currentPlaybackPosition.value
        )
    }
    val onCoverScrubPreview: (Long, Boolean) -> Unit = remember {
        { positionMs, finished ->
            coverScrubReleaseJob[0]?.cancel()
            coverScrubMs.value = positionMs
            if (finished) {
                coverScrubReleaseJob[0] = coverScrubScope.launch {
                    kotlinx.coroutines.withTimeoutOrNull(1_500) {
                        playerViewModel.currentPlaybackPosition.first { kotlin.math.abs(it - positionMs) < 1_200 }
                    }
                    coverScrubMs.value = null
                }
            }
        }
    }
    LaunchedEffect(song.id) {
        coverScrubReleaseJob[0]?.cancel()
        coverScrubMs.value = null
    }

    // Work out the song's structure (Intro / Verse / Chorus…) as soon as its lyrics arrive, so
    // the lyrics sheet opens with it. Found once per song and saved with it.
    val structureLyrics = lyricsProvider()
    LaunchedEffect(song.id, structureLyrics) {
        com.theveloper.pixelplay.data.lyrics.SongStructureRepository.get(context)
            .prefetch(song, structureLyrics)
    }

    val currentTheme = LocalMaterialTheme.current
    val coverLyricsAccentColor = remember(currentTheme) {
        resolveBrightWarmColor(
            background = currentTheme.primaryContainer,
            preferredWarm = currentTheme.tertiary
        )
    }

    val onToggleCoverLyrics = {
        playerViewModel.toggleCoverLyrics()
    }

    // Lógica para el botón de Lyrics en el reproductor expandido
    val onLyricsClick = {
        val lyrics = lyricsProvider()
        if (lyrics?.synced.isNullOrEmpty() && lyrics?.plain.isNullOrEmpty()) {
            // Si no hay letra, mostramos el diálogo para buscar
            showFetchLyricsDialog = true
        } else {
            // Si hay letra, mostramos el sheet directamente
            showLyricsSheet = true
        }
    }

    if (showFetchLyricsDialog) {
        MaterialTheme(
            colorScheme = LocalMaterialTheme.current,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            FetchLyricsDialog(
                uiState = lyricsSearchUiState,
                currentSong = song, // Use 'song' which is derived from args/retained
                onConfirm = { forcePick ->
                    // El usuario confirma, iniciamos la búsqueda
                    playerViewModel.fetchLyricsForCurrentSong(forcePick)
                },
                onPickResult = { result ->
                    playerViewModel.acceptLyricsSearchResultForCurrentSong(result)
                },
                onManualSearch = { title, artist ->
                    playerViewModel.searchLyricsManually(title, artist)
                },
                onDismiss = {
                    // El usuario cancela o cierra el diálogo
                    showFetchLyricsDialog = false
                    playerViewModel.resetLyricsSearchState()
                },
                onImport = {
                    filePickerLauncher.launch(com.theveloper.pixelplay.utils.LyricsImportSecurity.pickerMimeTypes())
                }
            )
        }
    }

    // Observador para reaccionar al resultado de la búsqueda de letras
    LaunchedEffect(lyricsSearchUiState) {
        when (val state = lyricsSearchUiState) {
            is LyricsSearchUiState.Success -> {
                if (showFetchLyricsDialog) {
                    showFetchLyricsDialog = false
                    showLyricsSheet = true
                    playerViewModel.resetLyricsSearchState()
                }
            }
            is LyricsSearchUiState.Error -> {
            }
            else -> Unit
        }
    }

    val onAlbumSongSelected: (Song, Int) -> Unit = { newSong, index ->
        playerViewModel.showAndPlaySong(
            song = newSong,
            contextSongs = currentPlaybackQueue,
            queueName = currentQueueSourceName,
            indexInQueue = index
        )
    }

    val onSongMetadataQueueClick = {
        showSongInfoBottomSheet = true
        onShowQueueClicked()
    }

    val onSongMetadataArtistClick = {
        val resolvedArtistId = currentSongArtists.firstOrNull { it.id != 0L && it.id != -1L }?.id ?: song.artistId
        if (currentSongArtists.size > 1) {
            showArtistPicker = true
        } else {
            playerViewModel.triggerArtistNavigationFromPlayer(resolvedArtistId)
        }
    }

    var pendingCarouselIndex by remember { mutableStateOf<Int?>(null) }
    val currentQueueIndex = remember(song.id, currentMediaItemIndex, currentPlaybackQueue) {
        resolveQueueIndex(
            queue = currentPlaybackQueue,
            songId = song.id,
            currentMediaItemIndex = currentMediaItemIndex
        )
    }
    val skipRequests = remember {
        MutableSharedFlow<SkipDirection>(
            extraBufferCapacity = 16
        )
    }
    val latestQueue by rememberUpdatedState(currentPlaybackQueue)
    val latestSongId by rememberUpdatedState(song.id)
    val latestCurrentQueueIndex by rememberUpdatedState(currentQueueIndex)
    val latestRepeatMode by rememberUpdatedState(repeatMode)
    val latestIsRemotePlaybackActive by rememberUpdatedState(isRemotePlaybackActive)
    val latestCurrentPositionProvider by rememberUpdatedState(currentPositionProvider)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrevious by rememberUpdatedState(onPrevious)

    LaunchedEffect(currentQueueIndex, pendingCarouselIndex) {
        if (pendingCarouselIndex == currentQueueIndex) {
            pendingCarouselIndex = null
        }
    }

    LaunchedEffect(pendingCarouselIndex, currentQueueIndex) {
        val targetIndex = pendingCarouselIndex ?: return@LaunchedEffect
        kotlinx.coroutines.delay(900)
        if (pendingCarouselIndex == targetIndex && currentQueueIndex != targetIndex) {
            pendingCarouselIndex = null
        }
    }

    LaunchedEffect(skipRequests) {
        skipRequests.collect { direction ->
            when (direction) {
                SkipDirection.NEXT -> latestOnNext()
                SkipDirection.PREVIOUS -> latestOnPrevious()
            }

            kotlinx.coroutines.delay(SKIP_COMMAND_GUARD_MS)
        }
    }

    fun predictSkipCarouselIndex(direction: SkipDirection): Int? {
        val queueSnapshot = latestQueue
        val baseIndex = pendingCarouselIndex
            ?: latestCurrentQueueIndex
            ?: queueSnapshot.indexOfFirst { it.id == latestSongId }.takeIf { it >= 0 }

        return when (direction) {
            SkipDirection.NEXT -> predictSkipNextCarouselIndex(
                currentIndex = baseIndex,
                queue = queueSnapshot,
                repeatMode = latestRepeatMode,
                isRemotePlaybackActive = latestIsRemotePlaybackActive
            )
            SkipDirection.PREVIOUS -> predictSkipPreviousCarouselIndex(
                currentIndex = baseIndex,
                queue = queueSnapshot,
                currentPositionMs = latestCurrentPositionProvider(),
                repeatMode = latestRepeatMode,
                isRemotePlaybackActive = latestIsRemotePlaybackActive
            )
        }
    }

    fun requestSkip(direction: SkipDirection) {
        val predictedTargetIndex = predictSkipCarouselIndex(direction)
        if (skipRequests.tryEmit(direction) && predictedTargetIndex != null) {
            pendingCarouselIndex = predictedTargetIndex
        }
    }

    val onNextWithOptimisticCarousel = {
        requestSkip(SkipDirection.NEXT)
        Unit
    }

    val onPreviousWithOptimisticCarousel = {
        requestSkip(SkipDirection.PREVIOUS)
        Unit
    }

    // The video section lives in a movable slot so the embedded player (a WebView) moves between
    // the portrait layout, the landscape layout and full screen without reloading.
    val latestVideoSong by rememberUpdatedState(song)
    val latestCarouselStyle by rememberUpdatedState(carouselStyle)
    val latestOnVideoPrevious by rememberUpdatedState(onPreviousWithOptimisticCarousel)
    val latestOnVideoNext by rememberUpdatedState(onNextWithOptimisticCarousel)
    val videoSection = remember(videoPlayback) {
        movableContentOf { sectionModifier: Modifier, fullscreen: Boolean ->
            SongVideoSection(
                song = latestVideoSong,
                playback = videoPlayback,
                carouselStyle = latestCarouselStyle,
                onPrevious = { latestOnVideoPrevious() },
                onNext = { latestOnVideoNext() },
                modifier = sectionModifier,
                fullscreen = fullscreen,
                onToggleFullscreen = { videoFullscreen = !videoFullscreen },
                viewModel = songVideoViewModel
            )
        }
    }

    // Where the album cover ends (root coordinates): the side swipe-up zones start below it.
    var coverBottomInRoot by remember { mutableFloatStateOf(0f) }
    var gestureRootTopInRoot by remember { mutableFloatStateOf(0f) }

    val albumCoverSection: @Composable (Modifier) -> Unit = { baseModifier ->
        val modifier = baseModifier.then(
            Modifier.onGloballyPositionedCover { coverBottomInRoot = it }
        )
        if (playerViewMode == "Video") {
            videoSection(modifier, false)
        } else {
            FullPlayerAlbumCoverSection(
                song = song,
                currentPlaybackQueue = currentPlaybackQueue,
                currentMediaItemIndex = currentQueueIndex ?: currentMediaItemIndex,
                carouselStyle = carouselStyle,
                loadingTweaks = loadingTweaks,
                isSheetDragGestureActive = isSheetDragGestureActive,
                expansionFractionProvider = expansionFractionProvider,
                currentSheetState = currentSheetState,
                isPlayingProvider = isPlayingProvider,
                playWhenReadyProvider = playWhenReadyProvider,
                placeholderColor = placeholderColor,
                placeholderOnColor = placeholderOnColor,
                albumArtQuality = albumArtQuality,
                requestedScrollIndex = pendingCarouselIndex,
                onSongSelected = onAlbumSongSelected,
                onAlbumClick = { albumSong ->
                    playerViewModel.triggerAlbumNavigationFromPlayer(albumSong)
                },
                isCoverLyricsEnabled = isCoverLyricsEnabled,
                lyrics = lyricsProvider(),
                isLoadingLyrics = playerViewModel.stablePlayerState.value.isLoadingLyrics,
                // Follows the timeline while it's being scrubbed, so the cover lyrics cycle
                // through the lines like the lyrics sheet does.
                playbackPositionFlow = coverLyricsPositionFlow,
                lyricsSyncOffset = lyricsSyncOffset,
                onOpenLyricsSheet = { showLyricsSheet = true },
                disableBlurAllOver = disableBlurAllOver,
                coverLyricsAccentColor = coverLyricsAccentColor,
                coverLyricsContentColor = playerOnBaseColor,
                isImmersiveTemporarilyDisabled = isImmersiveTemporarilyDisabled,
                modifier = modifier
            )
        }
    }

    val playerProgressSection: @Composable () -> Unit = {
        // In Video mode the timeline follows the video itself (its own length and position),
        // since a music video rarely lines up 1:1 with the audio track.
        val isVideoMode = playerViewMode == "Video"
        val positionProvider: () -> Long =
            if (isVideoMode) ({ videoPlayback.currentPositionMs() }) else currentPositionProvider
        val seek: (Long) -> Unit = if (isVideoMode) ({ videoPlayback.seekTo(it) }) else onSeek
        // key(): the slider keeps smoothing/held-seek state; don't let audio state bleed into video.
        key(isVideoMode) {
            FullPlayerProgressSection(
                song = song,
                playbackMetadataMediaId = playbackAudioMetadata.mediaId,
                playbackMetadataMimeType = playbackAudioMetadata.mimeType,
                playbackMetadataBitrate = playbackAudioMetadata.bitrate,
                playbackMetadataSampleRate = playbackAudioMetadata.sampleRate,
                currentPositionProvider = positionProvider,
                totalDurationValue = if (isVideoMode) videoPlayback.durationMs else totalDurationValue,
                durationHintMs = if (isVideoMode) 0L else song.duration,
                showPlayerFileInfo = showPlayerFileInfo && !isVideoMode,
                onSeek = seek,
                expansionFractionProvider = expansionFractionProvider,
                isPlayingProvider = if (isVideoMode) ({ videoPlayback.isPlaying }) else isPlayingProvider,
                currentSheetState = currentSheetState,
                progressActiveColor = progressActiveColor,
                playerOnBaseColor = playerOnBaseColor,
                allowRealtimeUpdates = allowRealtimeUpdates,
                isSheetDragGestureActive = isSheetDragGestureActive,
                loadingTweaks = loadingTweaks,
                onNavigateToEqualizer = onNavigateToEqualizer,
                onScrubPreview = if (isVideoMode) ({ _, _ -> }) else onCoverScrubPreview
            )
        }
    }

    val showCastLabel = isCastConnecting || (isRemotePlaybackActive && selectedRouteName != null)
    val isBluetoothActive =
        isBluetoothEnabled && !bluetoothName.isNullOrEmpty() && !isRemotePlaybackActive && !isCastConnecting
    val castIconPainter = when {
        isCastConnecting || isRemotePlaybackActive -> painterResource(R.drawable.rounded_cast_24)
        isBluetoothActive -> painterResource(R.drawable.rounded_bluetooth_24)
        else -> painterResource(R.drawable.rounded_mobile_speaker_24)
    }

    val controlsSection: @Composable () -> Unit = {
        // The transport row is identical in both modes; in Video it simply drives the embedded
        // player instead of the (suspended) audio pipeline.
        val isVideoMode = playerViewMode == "Video"
        FullPlayerControlsSection(
            loadingTweaks = loadingTweaks,
            isSheetDragGestureActive = isSheetDragGestureActive,
            expansionFractionProvider = expansionFractionProvider,
            currentSheetState = currentSheetState,
            placeholderColor = placeholderColor,
            placeholderOnColor = placeholderOnColor,
            isPlayingProvider = if (isVideoMode) ({ videoPlayback.isPlaying }) else isPlayingProvider,
            isQueueEndedProvider = if (isVideoMode) ({ false }) else isQueueEndedProvider,
            onPrevious = onPreviousWithOptimisticCarousel,
            onPlayPause = if (isVideoMode) ({ videoPlayback.togglePlayPause() }) else onPlayPause,
            onNext = onNextWithOptimisticCarousel,
            transportPlayPauseColors = transportPlayPauseColors,
            transportSkipColors = transportSkipButtonColors,
            isShuffleEnabledProvider = isShuffleEnabledProvider,
            shuffleTransitionInProgress = shuffleTransitionInProgress,
            repeatModeProvider = repeatModeProvider,
            isFavoriteProvider = isFavoriteProvider,
            onShuffleToggle = onShuffleToggle,
            onRepeatToggle = onRepeatToggle,
            onFavoriteToggle = onFavoriteToggle,
            onDislike = { showMixFeedbackSheet = true },
            activeMixFlavor = activeMixFlavor,
            onMixCycle = { playerViewModel.cycleMixMode() },
            onSmartMixActivate = { playerViewModel.activateSmartMix() },
            castButton = {
                Spacer(modifier = Modifier.width(48.dp))
            },
            queueButton = {
                Spacer(modifier = Modifier.width(48.dp))
            }
        )
    }

    val portraitSongMetadataSection: @Composable () -> Unit = {
        FullPlayerSongMetadataSection(
            song = song,
            currentSongArtists = currentSongArtists,
            loadingTweaks = loadingTweaks,
            isSheetDragGestureActive = isSheetDragGestureActive,
            expansionFractionProvider = expansionFractionProvider,
            currentSheetState = currentSheetState,
            placeholderColor = placeholderColor,
            placeholderOnColor = placeholderOnColor,
            isLandscape = false,
            onLyricsClick = onLyricsClick,
            isCoverLyricsEnabled = isCoverLyricsEnabled,
            onToggleCoverLyrics = onToggleCoverLyrics,
            playerOnBaseColor = playerOnBaseColor,
            playerViewModel = playerViewModel,
            gradientEdgeColor = LocalMaterialTheme.current.primaryContainer,
            chipColor = playerOnAccentColor.copy(alpha = 0.8f),
            chipContentColor = playerAccentColor,
            playerAccentColor = playerAccentColor,
            onQueueClick = onSongMetadataQueueClick,
            onArtistClick = onSongMetadataArtistClick,
            isPlayingProvider = isPlayingProvider
        )
    }

    val landscapeSongMetadataSection: @Composable () -> Unit = {
        FullPlayerSongMetadataSection(
            song = song,
            currentSongArtists = currentSongArtists,
            loadingTweaks = loadingTweaks,
            isSheetDragGestureActive = isSheetDragGestureActive,
            expansionFractionProvider = expansionFractionProvider,
            currentSheetState = currentSheetState,
            placeholderColor = placeholderColor,
            placeholderOnColor = placeholderOnColor,
            isLandscape = true,
            onLyricsClick = onLyricsClick,
            isCoverLyricsEnabled = isCoverLyricsEnabled,
            onToggleCoverLyrics = onToggleCoverLyrics,
            playerOnBaseColor = playerOnBaseColor,
            playerViewModel = playerViewModel,
            gradientEdgeColor = LocalMaterialTheme.current.primaryContainer,
            chipColor = playerOnAccentColor.copy(alpha = 0.8f),
            chipContentColor = playerAccentColor,
            playerAccentColor = playerAccentColor,
            onQueueClick = onSongMetadataQueueClick,
            onArtistClick = onSongMetadataArtistClick,
            isPlayingProvider = isPlayingProvider
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // MD3: TopAppBar 在竖屏时滑入，横屏时向上滑出淡出
            AnimatedVisibility(
                visible = !isLandscape,
                enter = fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            initialOffsetY = { -it / 2 },
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ),
                exit = fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                       slideOutVertically(
                           targetOffsetY = { -it / 2 },
                           animationSpec = tween(220, easing = FastOutSlowInEasing)
                       )
            ) {
                // Box, not Row: the Song/Video selector stays exactly centred however many
                // buttons sit on the right (download badge + options).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .graphicsLayer {
                            val fraction = expansionFractionProvider()
                            val startThreshold = 0f
                            val endThreshold = 1f
                            alpha = ((fraction - startThreshold) / (endThreshold - startThreshold)).coerceIn(0f, 1f)
                        }
                ) {
                    // Top-Left (Back) — compact rounded square, not edge-flush
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp, top = 4.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(playerOnAccentColor.copy(alpha = 0.55f))
                            .clickable(onClick = onCollapse),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_keyboard_arrow_down_24),
                            contentDescription = stringResource(R.string.player_cd_collapse),
                            tint = playerAccentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Center: Segmented Selector (Song / Video)
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(playerOnAccentColor.copy(alpha = 0.3f))
                            .padding(4.dp)
                    ) {
                        val songTabBg by animateColorAsState(
                            targetValue = if (playerViewMode == "Song") playerAccentColor else Color.Transparent,
                            label = "songTabBg"
                        )
                        val songTabText by animateColorAsState(
                            targetValue = if (playerViewMode == "Song") playerOnAccentColor else playerAccentColor,
                            label = "songTabText"
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(songTabBg)
                                .selectable(
                                    selected = playerViewMode == "Song",
                                    role = Role.Tab,
                                    onClick = { returnToAudio() }
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = "Audio",
                                tint = songTabText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        val videoTabBg by animateColorAsState(
                            targetValue = if (playerViewMode == "Video") playerAccentColor else Color.Transparent,
                            label = "videoTabBg"
                        )
                        val videoTabText by animateColorAsState(
                            targetValue = if (playerViewMode == "Video") playerOnAccentColor else playerAccentColor,
                            label = "videoTabText"
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(videoTabBg)
                                .selectable(
                                    selected = playerViewMode == "Video",
                                    role = Role.Tab,
                                    onClick = {
                                        if (isRemotePlaybackActive) {
                                            playerViewModel.sendToast("Disconnect casting to watch videos on this device.")
                                        } else {
                                            videoPlayback.seekTo(latestAudioPosition())
                                            playerViewMode = "Video"
                                        }
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SmartDisplay,
                                contentDescription = "Video",
                                tint = videoTabText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Top-Right: download / downloaded badge, then Options.
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    // Tick = saved on this device; download icon = tap to download; ring = downloading.
                    if (song != null) {
                        SongDownloadBadge(
                            song = song,
                            tint = playerAccentColor,
                            boxed = true,
                            containerColor = playerOnAccentColor.copy(alpha = 0.55f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(playerOnAccentColor.copy(alpha = 0.55f))
                            .clickable { onShowOptionsClick(song) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = playerAccentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        // Small dot so an active sleep timer is visible without opening the menu.
                        val sleepTimerRunning by playerViewModel.activeTimerValueDisplay.collectAsStateWithLifecycle()
                        if (sleepTimerRunning != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(playerAccentColor)
                            )
                        }
                    }
                    }
                }
            }
        }
    ) { paddingValues ->
        // MD3: 方向变化时先 alpha=0 再淡入新布局，避免双布局同时测量导致错位
        var contentVisible by remember(isLandscape) { mutableStateOf(false) }
        LaunchedEffect(isLandscape) { contentVisible = true }
        val contentAlpha by animateFloatAsState(
            targetValue = if (contentVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
            label = "orientationAlpha"
        )
        val sideSwipeEnabled = !videoFullscreen && currentSheetState == PlayerSheetState.EXPANDED &&
            !isQueueSheetVisible && !isCastSheetVisible && !showLyricsSheet
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha }
                .then(
                    Modifier.onGloballyPositionedCover(top = true) { gestureRootTopInRoot = it }
                )
                // Swipe up on the left / right side below the album cover: audio output /
                // queue. Attached to the root so the cover, buttons and slider keep their own
                // gestures; only an otherwise unused upward drag opens a sheet.
                .sideSwipeUpGestures(
                    enabled = sideSwipeEnabled && expansionFractionProvider() >= 0.99f,
                    zoneTopPx = { height ->
                        val coverBottom = coverBottomInRoot - gestureRootTopInRoot
                        when {
                            isLandscape -> height * 0.5f
                            coverBottom > 0f && coverBottom < height * 0.85f -> coverBottom
                            else -> height * 0.45f
                        }
                    },
                    onOpenAudioOutput = onShowCastClicked,
                    onOpenQueue = onShowQueueClicked,
                    onQueueDragStart = onQueueDragStart,
                    onQueueDrag = onQueueDrag,
                    onQueueRelease = onQueueRelease
                )
        ) {
            if (videoFullscreen && playerViewMode == "Video") {
                videoSection(Modifier.fillMaxSize(), true)
            } else if (isLandscape) {
                FullPlayerLandscapeContent(
                    paddingValues = paddingValues,
                    albumCoverSection = albumCoverSection,
                    songMetadataSection = landscapeSongMetadataSection,
                    playerProgressSection = playerProgressSection,
                    controlsSection = controlsSection
                )
            } else {
                FullPlayerPortraitContent(
                    paddingValues = paddingValues,
                    albumCoverSection = albumCoverSection,
                    songMetadataSection = portraitSongMetadataSection,
                    playerProgressSection = playerProgressSection,
                    controlsSection = controlsSection
                )
            }

            val fraction = expansionFractionProvider()
            if (fraction > 0.01f && !videoFullscreen) {
                val isGestureEnabled = (currentSheetState == PlayerSheetState.EXPANDED && fraction >= 0.99f && !isQueueSheetVisible && !isCastSheetVisible && !showLyricsSheet)

                // (The old full-width gesture strip sat over the middle of the player and took
                // touches from the album cover; side swipes are now handled on the root above.)

                MusicPlayerCornerOverlay(
                    onBluetoothClick = onShowCastClicked,
                    onQueueClick = onSongMetadataQueueClick,
                    onQueueDragStart = onQueueDragStart,
                    onQueueDrag = onQueueDrag,
                    onQueueRelease = onQueueRelease,
                    castIconPainter = castIconPainter,
                    queueBusy = playerViewModel.isQueueBusy.collectAsStateWithLifecycle(),
                    containerColor = playerOnAccentColor.copy(alpha = 0.55f),
                    contentColor = playerAccentColor,
                    enabled = isGestureEnabled,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = fraction
                        }
                )
            }
        }
    }
    AnimatedVisibility(
        visible = showLyricsSheet,
        enter = slideInVertically(
            initialOffsetY = { it / 5 },
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = 160)),
        exit = slideOutVertically(
            targetOffsetY = { it / 6 },
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        LyricsSheet(
            stablePlayerStateFlow = playerViewModel.stablePlayerState,
            playbackPositionFlow = playerViewModel.currentPlaybackPosition,
            lyricsSearchUiState = lyricsSearchUiState,
            onResetAllLyrics = playerViewModel::resetAllLyrics,
            resetLyricsForCurrentSong = {
                showLyricsSheet = false
                playerViewModel.resetLyricsForCurrentSong()
            },
            onSearchLyrics = { forcePick -> playerViewModel.fetchLyricsForCurrentSong(forcePick) },
            onPickResult = { playerViewModel.acceptLyricsSearchResultForCurrentSong(it) },
            onManualSearch = { title, artist -> playerViewModel.searchLyricsManually(title, artist) },
            onImportLyrics = { filePickerLauncher.launch(com.theveloper.pixelplay.utils.LyricsImportSecurity.pickerMimeTypes()) },
            onDismissLyricsSearch = { playerViewModel.resetLyricsSearchState() },
            lyricsSyncOffset = lyricsSyncOffset,
            onLyricsSyncOffsetChange = { currentSong?.id?.let { songId -> playerViewModel.setLyricsSyncOffset(songId, it) } },
            // The user's lyrics font and size (Settings → Lyrics). effectiveLyricsFont falls
            // back to the platform font (fontFamily = null) whenever the chosen font has no
            // glyph for something in this song's lyrics, so extended Unicode (e.g. Icelandic
            // æ ð þ, Cyrillic, CJK) never renders as tofu. (#2427)
            // Weight = the resting weight (the current line is drawn a step heavier, see
            // activeLyricWeight); spacing scales the line height only.
            lyricsTextStyle = MaterialTheme.typography.titleLarge.let { base ->
                val scale = lyricsDisplayPrefs.textSize.multiplier
                base.copy(
                    fontFamily = lyricsFontFamily(effectiveLyricsFont),
                    fontWeight = lyricsDisplayPrefs.fontWeight.fontWeight,
                    fontSize = base.fontSize * scale,
                    lineHeight = base.lineHeight * scale * lyricsDisplayPrefs.lineSpacing.multiplier
                )
            },
            colorScheme = LocalMaterialTheme.current,
            onBackClick = { showLyricsSheet = false },
            onSaveLyricsToFile = playerViewModel::saveLyricsToFile,
            onTranslateViaAi = { playerViewModel.translateLyricsViaAi() },
            // Lyrics written / timed by hand go through the same checks and storage as an
            // imported .lrc file.
            onSaveCustomLyrics = { text ->
                val songId = currentSong?.id?.toLongOrNull()
                when {
                    songId == null -> playerViewModel.sendToast("No song selected for lyrics.")
                    else -> when (val result = LyricsImportSecurity.validateImportedLrcContent(text)) {
                        is LyricsImportValidationResult.Valid -> fileImportScope.launch {
                            playerViewModel.importLyricsFromFile(songId, result.value)
                            playerViewModel.sendToast("Lyrics saved")
                        }
                        is LyricsImportValidationResult.Invalid ->
                            playerViewModel.sendToast(LyricsImportSecurity.messageFor(result.reason))
                    }
                }
            },
            onSeekTo = { playerViewModel.seekTo(it) },
            onPlayPause = {
                playerViewModel.playPause()
            },
            onNext = onNext,
            onPrev = onPrevious,
            immersiveLyricsEnabled = immersiveLyricsEnabled,
            immersiveLyricsTimeout = immersiveLyricsTimeout,
            isImmersiveTemporarilyDisabled = isImmersiveTemporarilyDisabled,
            onSetImmersiveTemporarilyDisabled = { playerViewModel.setImmersiveTemporarilyDisabled(it) },
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            isFavoriteProvider = isFavoriteProvider,
            onShuffleToggle = onShuffleToggle,
            onRepeatToggle = onRepeatToggle,
            onFavoriteToggle = onFavoriteToggle,
            nextUpSongFlow = playerViewModel.nextUpSong,
            onPlayNextUpNow = { playerViewModel.playNextUpNow() },
            onAddSongClick = { playerViewModel.startAddSongFromLyrics() },
            confirmations = playerViewModel.lyricsConfirmations,
            onConfirmationShown = { playerViewModel.consumeLyricsConfirmation() },
            onReaction = { playerViewModel.reactToCurrentSong(it) }
        )
    }

    if (showMixFeedbackSheet) {
        com.theveloper.pixelplay.presentation.components.MixFeedbackBottomSheet(
            player = playerViewModel,
            songTitle = song.title,
            onDismiss = { showMixFeedbackSheet = false }
        )
    }

    val artistPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showArtistPicker && currentSongArtists.isNotEmpty()) {
        PlayerArtistPickerBottomSheet(
            song = song,
            artists = currentSongArtists,
            sheetState = artistPickerSheetState,
            onDismiss = { showArtistPicker = false },
            onArtistClick = { artist ->
                playerViewModel.triggerArtistNavigationFromPlayer(artist.id, artist.name)
                showArtistPicker = false
            }
        )
    }
}


@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun FullPlayerAlbumCoverSection(
    song: Song,
    currentPlaybackQueue: ImmutableList<Song>,
    currentMediaItemIndex: Int,
    carouselStyle: String,
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    isPlayingProvider: () -> Boolean,
    playWhenReadyProvider: () -> Boolean,
    placeholderColor: Color,
    placeholderOnColor: Color,
    albumArtQuality: AlbumArtQuality,
    requestedScrollIndex: Int?,
    onSongSelected: (Song, Int) -> Unit,
    onAlbumClick: (Song) -> Unit,
    isCoverLyricsEnabled: Boolean = false,
    lyrics: Lyrics? = null,
    isLoadingLyrics: Boolean = false,
    playbackPositionFlow: StateFlow<Long>? = null,
    lyricsSyncOffset: Int = 0,
    onOpenLyricsSheet: (() -> Unit)? = null,
    disableBlurAllOver: Boolean = false,
    coverLyricsAccentColor: Color = Color.Unspecified,
    coverLyricsContentColor: Color = Color.Unspecified,
    isImmersiveTemporarilyDisabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delayAlbumCarousel
    val shouldApplyPausedScale = !isPlayingProvider() && !playWhenReadyProvider()
    // Use a short deterministic tween instead of spring(StiffnessLow). The original
    // spring took ~1s to settle, producing ~60 frames of graphicsLayer invalidations
    // that overlapped with any subsequent sheet-collapse gesture. A 260 ms tween
    // finishes well before the user can start the next gesture, keeping the album
    // art's "pause squish" visible but removing the long tail of frame work.
    val albumArtScale by animateFloatAsState(
        targetValue = if (shouldApplyPausedScale) 0.95f else 1f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "AlbumArtScale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        val carouselHeight = when (carouselStyle) {
            CarouselStyle.NO_PEEK -> maxWidth
            CarouselStyle.ONE_PEEK -> maxWidth * 0.8f
            CarouselStyle.TWO_PEEK -> maxWidth * 0.6f
            else -> maxWidth * 0.8f
        }

        DelayedContent(
            shouldDelay = shouldDelay,
            showPlaceholders = loadingTweaks.showPlaceholders,
            applyPlaceholderDelayOnClose = loadingTweaks.applyPlaceholdersOnClose,
            switchOnDragRelease = loadingTweaks.switchOnDragRelease,
            isSheetDragGestureActive = isSheetDragGestureActive,
            sharedBoundsModifier = Modifier.fillMaxWidth().height(carouselHeight),
            expansionFractionProvider = expansionFractionProvider,
            isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
            normalStartThreshold = 0.08f,
            delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
            delayCloseThreshold = 1f - (loadingTweaks.contentCloseThresholdPercent / 100f),
            placeholder = {
                if (loadingTweaks.transparentPlaceholders) {
                    Box(
                        Modifier
                            .height(carouselHeight)
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = albumArtScale
                                scaleY = albumArtScale
                            }
                    )
                } else {
                    AlbumPlaceholder(
                        height = carouselHeight,
                        color = placeholderColor,
                        onColor = placeholderOnColor,
                        modifier = Modifier.graphicsLayer {
                            scaleX = albumArtScale
                            scaleY = albumArtScale
                        }
                    )
                }
            }
        ) {
            AlbumCarouselSection(
                currentSong = song,
                queue = currentPlaybackQueue,
                expansionFraction = 1f,
                currentMediaItemIndex = currentMediaItemIndex,
                requestedScrollIndex = requestedScrollIndex,
                onSongSelected = { newSong, index ->
                    if (newSong.id != song.id || index != currentMediaItemIndex) {
                        onSongSelected(newSong, index)
                    }
                },
                onAlbumClick = onAlbumClick,
                carouselStyle = carouselStyle,
                modifier = Modifier
                    .height(carouselHeight)
                    .graphicsLayer {
                        scaleX = albumArtScale
                        scaleY = albumArtScale
                    },
                albumArtQuality = albumArtQuality,
                isCoverLyricsEnabled = isCoverLyricsEnabled,
                lyrics = lyrics,
                isLoadingLyrics = isLoadingLyrics,
                playbackPositionFlow = playbackPositionFlow,
                lyricsSyncOffset = lyricsSyncOffset,
                onOpenLyricsSheet = onOpenLyricsSheet,
                disableBlurAllOver = disableBlurAllOver,
                coverLyricsAccentColor = coverLyricsAccentColor,
                coverLyricsContentColor = coverLyricsContentColor,
                isImmersiveTemporarilyDisabled = isImmersiveTemporarilyDisabled
            )
        }
    }
}

@Composable
private fun FullPlayerControlsSection(
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    placeholderColor: Color,
    placeholderOnColor: Color,
    isPlayingProvider: () -> Boolean,
    isQueueEndedProvider: () -> Boolean = { false },
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    transportPlayPauseColors: TransportButtonColors,
    transportSkipColors: TransportButtonColors,
    isShuffleEnabledProvider: () -> Boolean,
    shuffleTransitionInProgress: Boolean,
    repeatModeProvider: () -> Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDislike: () -> Unit,
    activeMixFlavor: com.theveloper.pixelplay.data.MixFlavor? = null,
    onMixCycle: () -> Unit = onShuffleToggle,
    onSmartMixActivate: (() -> Unit)? = null,
    castButton: @Composable () -> Unit,
    queueButton: @Composable () -> Unit
) {
    val motionScheme = remember { MotionScheme.expressive() }
    val controlSpatialSpec = remember { motionScheme.fastSpatialSpec<Float>() }
    val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delayControls

    DelayedContent(
        shouldDelay = shouldDelay,
        showPlaceholders = loadingTweaks.showPlaceholders,
        applyPlaceholderDelayOnClose = loadingTweaks.applyPlaceholdersOnClose,
        switchOnDragRelease = loadingTweaks.switchOnDragRelease,
        isSheetDragGestureActive = isSheetDragGestureActive,
        sharedBoundsModifier = Modifier.fillMaxWidth().height(182.dp),
        expansionFractionProvider = expansionFractionProvider,
        isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
        normalStartThreshold = 0.42f,
        delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
        delayCloseThreshold = 1f - (loadingTweaks.contentCloseThresholdPercent / 100f),
        placeholder = {
            if (loadingTweaks.transparentPlaceholders) {
                Box(Modifier.fillMaxWidth().height(182.dp))
            } else {
                ControlsPlaceholder(placeholderColor, placeholderOnColor)
            }
        }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedPlaybackControls(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                isPlayingProvider = isPlayingProvider,
                isQueueEndedProvider = isQueueEndedProvider,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                height = 80.dp,
                pressAnimationSpec = controlSpatialSpec,
                releaseDelay = 220L,
                colorOtherButtons = transportSkipColors.container,
                colorPlayPause = transportPlayPauseColors.container,
                tintPlayPauseIcon = transportPlayPauseColors.content,
                tintOtherIcons = transportSkipColors.content,
                colorPreviousButton = transportSkipColors.container,
                colorNextButton = transportSkipColors.container,
                tintPreviousIcon = transportSkipColors.content,
                tintNextIcon = transportSkipColors.content
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                castButton()
                BottomToggleRow(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 66.dp, max = 86.dp)
                        .padding(horizontal = 4.dp),
                    isShuffleEnabled = isShuffleEnabledProvider(),
                    isShuffleTransitionInProgress = shuffleTransitionInProgress,
                    repeatMode = repeatModeProvider(),
                    isFavoriteProvider = isFavoriteProvider,
                    onShuffleToggle = onShuffleToggle,
                    onRepeatToggle = onRepeatToggle,
                    onFavoriteToggle = onFavoriteToggle,
                    onDislike = onDislike,
                    activeMixFlavor = activeMixFlavor,
                    onMixCycle = onMixCycle,
                    onSmartMixActivate = onSmartMixActivate
                )
                queueButton()
            }
        }
    }
}

@Composable
private fun FullPlayerProgressSection(
    song: Song,
    playbackMetadataMediaId: String?,
    playbackMetadataMimeType: String?,
    playbackMetadataBitrate: Int?,
    playbackMetadataSampleRate: Int?,
    currentPositionProvider: () -> Long,
    totalDurationValue: Long,
    showPlayerFileInfo: Boolean,
    onSeek: (Long) -> Unit,
    expansionFractionProvider: () -> Float,
    isPlayingProvider: () -> Boolean,
    currentSheetState: PlayerSheetState,
    progressActiveColor: Color,
    playerOnBaseColor: Color,
    allowRealtimeUpdates: Boolean,
    isSheetDragGestureActive: Boolean,
    loadingTweaks: FullPlayerLoadingTweaks,
    onNavigateToEqualizer: () -> Unit,
    durationHintMs: Long = song.duration,
    onScrubPreview: (positionMs: Long, finished: Boolean) -> Unit = { _, _ -> }
) {
    val isMetadataForCurrentSong = playbackMetadataMediaId == song.id
    val audioMimeType = if (isMetadataForCurrentSong) {
        playbackMetadataMimeType ?: song.mimeType
    } else {
        song.mimeType
    }
    val audioBitrate = if (isMetadataForCurrentSong) {
        playbackMetadataBitrate ?: song.bitrate
    } else {
        song.bitrate
    }
    val audioSampleRate = if (isMetadataForCurrentSong) {
        playbackMetadataSampleRate ?: song.sampleRate
    } else {
        song.sampleRate
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        PlayerProgressBarSection(
            songId = song.id,
            currentPositionProvider = currentPositionProvider,
            totalDurationValue = totalDurationValue,
            songDurationHintMs = durationHintMs,
            audioMimeType = audioMimeType,
            audioBitrate = audioBitrate,
            audioSampleRate = audioSampleRate,
            showAudioFileInfo = showPlayerFileInfo,
            onSeek = onSeek,
            expansionFractionProvider = expansionFractionProvider,
            isPlayingProvider = isPlayingProvider,
            currentSheetState = currentSheetState,
            activeTrackColor = progressActiveColor,
            inactiveTrackColor = playerOnBaseColor.copy(alpha = 0.2f),
            thumbColor = progressActiveColor,
            timeTextColor = playerOnBaseColor,
            allowRealtimeUpdates = allowRealtimeUpdates,
            isSheetDragGestureActive = isSheetDragGestureActive,
            loadingTweaks = loadingTweaks,
            onNavigateToEqualizer = onNavigateToEqualizer,
            onScrubPreview = onScrubPreview
        )
    }
}

private fun resolveQueueIndex(
    queue: ImmutableList<Song>,
    songId: String,
    currentMediaItemIndex: Int
): Int? {
    if (currentMediaItemIndex in queue.indices && queue[currentMediaItemIndex].id == songId) {
        return currentMediaItemIndex
    }
    return queue.indexOfFirst { it.id == songId }.takeIf { it >= 0 }
}

private fun predictSkipNextCarouselIndex(
    currentIndex: Int?,
    queue: ImmutableList<Song>,
    repeatMode: Int,
    isRemotePlaybackActive: Boolean
): Int? {
    if (isRemotePlaybackActive || queue.size <= 1) return null
    val safeCurrentIndex = currentIndex?.takeIf { it in queue.indices } ?: return null

    return when {
        safeCurrentIndex < queue.lastIndex -> safeCurrentIndex + 1
        repeatMode == Player.REPEAT_MODE_ALL -> 0
        else -> null
    }
}

private fun predictSkipPreviousCarouselIndex(
    currentIndex: Int?,
    queue: ImmutableList<Song>,
    currentPositionMs: Long,
    repeatMode: Int,
    isRemotePlaybackActive: Boolean
): Int? {
    if (isRemotePlaybackActive || queue.size <= 1) return null
    if (currentPositionMs > PREVIOUS_TRACK_RESTART_THRESHOLD_MS) return null
    val safeCurrentIndex = currentIndex?.takeIf { it in queue.indices } ?: return null

    return when {
        safeCurrentIndex > 0 -> safeCurrentIndex - 1
        repeatMode == Player.REPEAT_MODE_ALL -> queue.lastIndex
        else -> null
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun FullPlayerSongMetadataSection(
    song: Song,
    currentSongArtists: List<Artist>,
    loadingTweaks: FullPlayerLoadingTweaks,
    isSheetDragGestureActive: Boolean,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    placeholderColor: Color,
    placeholderOnColor: Color,
    isLandscape: Boolean,
    onLyricsClick: () -> Unit,
    isCoverLyricsEnabled: Boolean = false,
    onToggleCoverLyrics: () -> Unit = {},
    playerOnBaseColor: Color,
    playerViewModel: PlayerViewModel,
    gradientEdgeColor: Color,
    chipColor: Color,
    chipContentColor: Color,
    playerAccentColor: Color = chipContentColor,
    onQueueClick: () -> Unit,
    onArtistClick: () -> Unit,
    isPlayingProvider: () -> Boolean = { true }
) {
    val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delaySongMetadata

    DelayedContent(
        shouldDelay = shouldDelay,
        showPlaceholders = loadingTweaks.showPlaceholders,
        applyPlaceholderDelayOnClose = loadingTweaks.applyPlaceholdersOnClose,
        switchOnDragRelease = loadingTweaks.switchOnDragRelease,
        isSheetDragGestureActive = isSheetDragGestureActive,
        sharedBoundsModifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
        expansionFractionProvider = expansionFractionProvider,
        isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
        normalStartThreshold = 0.20f,
        delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
        delayCloseThreshold = 1f - (loadingTweaks.contentCloseThresholdPercent / 100f),
        placeholder = {
            if (loadingTweaks.transparentPlaceholders) {
                Box(Modifier.fillMaxWidth().height(70.dp))
            } else {
                MetadataPlaceholder(
                    expansionFractionProvider = expansionFractionProvider,
                    color = placeholderColor,
                    onColor = placeholderOnColor,
                    showQueueButtons = isLandscape
                )
            }
        }
    ) {
        SongMetadataDisplaySection(
            modifier = Modifier
                .padding(start = 0.dp),
            onClickLyrics = onLyricsClick,
            isCoverLyricsEnabled = isCoverLyricsEnabled,
            onToggleCoverLyrics = onToggleCoverLyrics,
            playerAccentColor = playerAccentColor,
            song = song,
            currentSongArtists = currentSongArtists,
            expansionFractionProvider = expansionFractionProvider,
            textColor = playerOnBaseColor,
            artistTextColor = playerOnBaseColor.copy(alpha = 0.7f),
            playerViewModel = playerViewModel,
            gradientEdgeColor = gradientEdgeColor,
            chipColor = chipColor,
            chipContentColor = chipContentColor,
            showQueueButton = isLandscape,
            onClickQueue = onQueueClick,
            onClickArtist = onArtistClick,
            isPlayingProvider = isPlayingProvider
        )
    }
}

@Composable
private fun FullPlayerPortraitContent(
    paddingValues: PaddingValues,
    albumCoverSection: @Composable (Modifier) -> Unit,
    songMetadataSection: @Composable () -> Unit,
    playerProgressSection: @Composable () -> Unit,
    controlsSection: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(
                horizontal = 24.dp,
                vertical = 0.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        albumCoverSection(Modifier)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.align(Alignment.Start)) {
                songMetadataSection()
            }
            playerProgressSection()
        }

        controlsSection()
    }
}

@Composable
private fun FullPlayerLandscapeContent(
    paddingValues: PaddingValues,
    albumCoverSection: @Composable (Modifier) -> Unit,
    songMetadataSection: @Composable () -> Unit,
    playerProgressSection: @Composable () -> Unit,
    controlsSection: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(
                horizontal = 24.dp,
                vertical = 0.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        albumCoverSection(
            Modifier
                .fillMaxHeight()
                .weight(1f)
        )
        Spacer(Modifier.width(9.dp))
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .padding(
                    horizontal = 0.dp,
                    vertical = 0.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            songMetadataSection()
            playerProgressSection()
            controlsSection()
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SongMetadataDisplaySection(
    song: Song?,
    currentSongArtists: List<Artist>,
    expansionFractionProvider: () -> Float,
    textColor: Color,
    artistTextColor: Color,
    gradientEdgeColor: Color,
    playerViewModel: PlayerViewModel,
    chipColor: Color,
    chipContentColor: Color,
    onClickLyrics: () -> Unit,
    isCoverLyricsEnabled: Boolean = false,
    onToggleCoverLyrics: () -> Unit = {},
    playerAccentColor: Color = chipContentColor,
    showQueueButton: Boolean,
    onClickQueue: () -> Unit,
    onClickArtist: () -> Unit,
    modifier: Modifier = Modifier,
    isPlayingProvider: () -> Boolean = { true }
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        song?.let { currentSong ->
            PlayerSongInfo(
                song = currentSong,
                title = currentSong.title,
                artist = currentSong.displayArtistWithGenre,
                artistId = currentSong.artistId,
                artists = currentSongArtists,
                expansionFractionProvider = expansionFractionProvider,
                textColor = textColor,
                artistTextColor = artistTextColor,
                gradientEdgeColor = gradientEdgeColor,
                playerViewModel = playerViewModel,
                onClickArtist = onClickArtist,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                isPlayingProvider = isPlayingProvider
            )
        }
        
        val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
        val isBuffering = stablePlayerState.isBuffering


        AnimatedVisibility(
            visible = isBuffering,
            enter = scaleIn(
                initialScale = 0.85f,
                animationSpec = tween(
                    durationMillis = 400,
                    delayMillis = 80,
                    easing = FastOutSlowInEasing
                )
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 300,
                    delayMillis = 80
                )
            ),
            exit = scaleOut(
                targetScale = 0.85f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = FastOutSlowInEasing
                )
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 200
                )
            )
        ) {
            Surface(
                shape = CircleShape,
                color = chipColor,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier.padding(10.dp), 
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(28.dp),
                        color = chipContentColor
                    )
                }
            }
        }

        if (showQueueButton) {
            val haptic = LocalHapticFeedback.current
            val landscapeLyricsShape = RoundedCornerShape(
                topStart = 50.dp,
                topEnd = 6.dp,
                bottomStart = 50.dp,
                bottomEnd = 6.dp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(height = 42.dp, width = 50.dp)
                        .clip(landscapeLyricsShape)
                        .then(
                            if (isCoverLyricsEnabled) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = playerAccentColor,
                                    shape = landscapeLyricsShape
                                )
                            } else Modifier
                        )
                        .background(chipColor)
                        .combinedClickable(
                            onClick = onClickLyrics,
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleCoverLyrics()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_lyrics_24),
                        contentDescription = stringResource(R.string.common_lyrics),
                        tint = chipContentColor
                    )
                }
                val queueBusy by playerViewModel.isQueueBusy.collectAsStateWithLifecycle()
                val queueButtonShape = RoundedCornerShape(
                    topStart = 6.dp,
                    topEnd = 50.dp,
                    bottomStart = 6.dp,
                    bottomEnd = 50.dp
                )
                Box(
                    modifier = Modifier
                        .size(height = 42.dp, width = 50.dp)
                        .queueBusyOutline(
                            active = queueBusy,
                            shape = queueButtonShape,
                            color = playerAccentColor
                        )
                        .clip(queueButtonShape)
                        .background(chipColor)
                        .clickable { onClickQueue() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_queue_music_24),
                        contentDescription = stringResource(R.string.player_cd_open_queue),
                        tint = chipContentColor
                    )
                }
            }
        } else {
            // Portrait Mode: Just the Lyrics button (Queue is in TopBar)
            val haptic = LocalHapticFeedback.current
            val lyricsButtonShape = CircleShape
            val lyricsButtonBorder = if (isCoverLyricsEnabled) {
                BorderStroke(2.dp, playerAccentColor)
            } else null

            Surface(
                modifier = Modifier
                    .size(width = 48.dp, height = 48.dp)
                    .clip(lyricsButtonShape)
                    .combinedClickable(
                        onClick = onClickLyrics,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleCoverLyrics()
                        }
                    ),
                shape = lyricsButtonShape,
                color = chipColor,
                contentColor = chipContentColor,
                border = lyricsButtonBorder
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_lyrics_24),
                        contentDescription = stringResource(R.string.common_lyrics)
                    )
                }
            }
        }
    }
}

private fun formatAudioMetaLabel(mimeType: String?, bitrate: Int?, sampleRate: Int?): String? {
    val formatLabel = mimeTypeToFormat(mimeType)
        .takeIf { it != "-" }
        ?.uppercase(Locale.getDefault())

    val parts = buildList {
        sampleRate?.takeIf { it > 0 }?.let { add(String.format(Locale.US, "%.1f kHz", it / 1000.0)) }
        bitrate?.takeIf { it > 0 }?.let { bitrateValue ->
            val kbpsLabel = "${bitrateValue / 1000} kbps"
            if (formatLabel != null) {
                add("$kbpsLabel \u2022 $formatLabel")
            } else {
                add(kbpsLabel)
            }
        } ?: formatLabel?.let { add(it) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" \u2022 ")
}

@Composable
private fun PlayerProgressBarSection(
    songId: String,
    currentPositionProvider: () -> Long,
    totalDurationValue: Long,
    songDurationHintMs: Long,
    audioMimeType: String?,
    audioBitrate: Int?,
    audioSampleRate: Int?,
    showAudioFileInfo: Boolean,
    onSeek: (Long) -> Unit,
    expansionFractionProvider: () -> Float,
    isPlayingProvider: () -> Boolean,
    currentSheetState: PlayerSheetState,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    thumbColor: Color,
    timeTextColor: Color,
    allowRealtimeUpdates: Boolean = true,
    isSheetDragGestureActive: Boolean = false,
    loadingTweaks: FullPlayerLoadingTweaks? = null,
    onNavigateToEqualizer: () -> Unit = {},
    /** Scrub position while the timeline is dragged (finished = released and seeked). */
    onScrubPreview: (positionMs: Long, finished: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val progressSectionHorizontalInset = 0.dp
    val isVisible by remember(expansionFractionProvider) {
        derivedStateOf { expansionFractionProvider() > 0.01f }
    }
    val isExpanded by remember(currentSheetState, expansionFractionProvider) {
        derivedStateOf {
            currentSheetState == PlayerSheetState.EXPANDED && expansionFractionProvider() >= 0.995f
        }
    }
    val shouldRunRealtimeUpdates = allowRealtimeUpdates && isVisible
    val shouldSampleProgress = isVisible

    val reportedDuration = totalDurationValue.coerceAtLeast(0L)
    val hintDuration = songDurationHintMs.coerceAtLeast(0L)
    val displayDurationValue = when {
        reportedDuration <= 0L && hintDuration <= 0L -> 0L
        reportedDuration <= 0L -> hintDuration
        hintDuration <= 0L -> reportedDuration
        kotlin.math.abs(reportedDuration - hintDuration) <= 1500L -> reportedDuration
        else -> minOf(reportedDuration, hintDuration)
    }
    val audioMetaLabel = remember(showAudioFileInfo, audioMimeType, audioBitrate, audioSampleRate) {
        if (showAudioFileInfo) {
            formatAudioMetaLabel(
                mimeType = audioMimeType,
                bitrate = audioBitrate,
                sampleRate = audioSampleRate
            )
        } else {
            null
        }
    }
    var displayAudioMetaLabel by remember(songId) { mutableStateOf<String?>(null) }
    LaunchedEffect(songId, audioMetaLabel, showAudioFileInfo) {
        if (!showAudioFileInfo) {
            displayAudioMetaLabel = null
        } else if (!audioMetaLabel.isNullOrBlank()) {
            displayAudioMetaLabel = audioMetaLabel
        } else {
            kotlinx.coroutines.delay(500)
            displayAudioMetaLabel = null
        }
    }
    val durationForCalc = displayDurationValue.coerceAtLeast(1L)
    
    // Pass isVisible to rememberSmoothProgress
    val (smoothProgressState, _) = rememberSmoothProgress(
        isPlayingProvider = isPlayingProvider,
        currentPositionProvider = currentPositionProvider,
        totalDuration = displayDurationValue,
        sampleWhilePlayingMs = if (shouldRunRealtimeUpdates && isExpanded) 180L else 500L,
        sampleWhilePausedMs = 800L,
        isVisible = shouldSampleProgress
    )

    var sliderDragValue by remember { mutableStateOf<Float?>(null) }
    // Held seek target (fraction) — mirrors PlayerSeekBar so the slider stays where the user
    // dropped it until real playback catches up. Fraction-based so it survives duration drift.
    var targetSeekFraction by remember { mutableFloatStateOf(-1f) }
    var lastSeekFinishedTime by remember { mutableLongStateOf(0L) }

    // Reset seek state on song change to avoid stale position from previous song.
    LaunchedEffect(songId) {
        sliderDragValue = null
        targetSeekFraction = -1f
        lastSeekFinishedTime = 0L
    }

    // Release the held target once smooth progress catches up (within 4%) or after a 5 s
    // safety net — same thresholds as the LyricsSheet PlayerSeekBar. Re-keying on songId
    // restarts the snapshotFlow so the new song's progress drives the catch-up cleanly.
    LaunchedEffect(songId) {
        snapshotFlow { smoothProgressState.value }.collect { progress ->
            if (sliderDragValue != null) return@collect
            val target = targetSeekFraction
            if (target < 0f) return@collect
            val timeSinceSeek = System.currentTimeMillis() - lastSeekFinishedTime
            val diff = kotlin.math.abs(progress - target)
            if (timeSinceSeek > 5000L || diff < 0.04f) {
                targetSeekFraction = -1f
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val shouldAnimateWavyProgress by remember(shouldRunRealtimeUpdates, isPlayingProvider) {
        derivedStateOf { shouldRunRealtimeUpdates && isPlayingProvider() }
    }

    // Always drive the thumb from smoothed progress to avoid visual jumps from 500ms raw ticks.
    val animatedProgressState = remember(smoothProgressState) {
        derivedStateOf {
            when {
                sliderDragValue != null -> sliderDragValue!!
                targetSeekFraction >= 0f -> targetSeekFraction
                else -> smoothProgressState.value
            }
        }
    }

    // No LaunchedEffect/snapshotFlow needed anymore. 
    // smoothProgressState is already 60fps animated.

    val effectivePositionState = remember(durationForCalc, animatedProgressState, isVisible, displayDurationValue) {
        derivedStateOf {
             val progress = animatedProgressState.value
             (progress * durationForCalc).roundToLong().coerceIn(0L, displayDurationValue)
        }
    }

    val shouldDelay = loadingTweaks?.let { it.delayAll || it.delayProgressBar } ?: false

    val placeholderColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.25f)
    val placeholderOnColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.2f)

    DelayedContent(
        shouldDelay = shouldDelay,
        showPlaceholders = loadingTweaks?.showPlaceholders ?: false,
        applyPlaceholderDelayOnClose = loadingTweaks?.applyPlaceholdersOnClose ?: true,
        switchOnDragRelease = loadingTweaks?.switchOnDragRelease ?: false,
        isSheetDragGestureActive = isSheetDragGestureActive,
        sharedBoundsModifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
        expansionFractionProvider = expansionFractionProvider,
        isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
        normalStartThreshold = 0.08f,
        delayAppearThreshold = (loadingTweaks?.contentAppearThresholdPercent ?: 0) / 100f,
        delayCloseThreshold = 1f - ((loadingTweaks?.contentCloseThresholdPercent ?: 0) / 100f),
        placeholder = {
             if (loadingTweaks?.transparentPlaceholders == true) {
                 Box(Modifier.fillMaxWidth().heightIn(min = 70.dp))
             } else {
                 ProgressPlaceholder(
                     color = placeholderColor,
                     onColor = placeholderOnColor,
                     showAudioMetaChip = showAudioFileInfo && !displayAudioMetaLabel.isNullOrBlank()
                 )
             }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 70.dp)
        ) {
            // Isolated Slider Component
            // Wrapped in a Box with detectVerticalDragGestures to prevent the outer
            // playerSheetVerticalDragGesture from intercepting slider touches. If the
            // user's drag has a vertical component, the inner handler absorbs it (consuming
            // the events) so the sheet-collapse gesture never activates in this area.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(onVerticalDrag = { _, _ -> })
                    }
            ) {
                EfficientSlider(
                    valueState = animatedProgressState,
                    onValueChange = {
                        sliderDragValue = it
                        onScrubPreview((it * durationForCalc).roundToLong(), false)
                    },
                    onValueCommit = { finalValue ->
                        val targetMs = (finalValue * durationForCalc).roundToLong()
                        targetSeekFraction = finalValue
                        lastSeekFinishedTime = System.currentTimeMillis()
                        AdvancedPerformanceDiagnostics.recordEventIfEnabled(
                            type = AdvancedPerformanceDiagnostics.EventTypes.UI,
                            name = "player_seek_commit"
                        ) {
                            mapOf(
                                "targetMs" to targetMs.toString(),
                                "durationMs" to displayDurationValue.toString()
                            )
                        }
                        onSeek(targetMs)
                        onScrubPreview(targetMs, true)
                        sliderDragValue = null
                    },
                    thumbColor = thumbColor,
                    activeTrackColor = activeTrackColor,
                    inactiveTrackColor = inactiveTrackColor,
                    interactionSource = interactionSource,
                    isPlaying = shouldAnimateWavyProgress,
                    isVisible = isVisible,
                    trackEdgePadding = progressSectionHorizontalInset
                )
            }

            // Isolated Time Labels
            EfficientTimeLabels(
                positionState = effectivePositionState,
                duration = displayDurationValue,
                isVisible = isVisible,
                textColor = timeTextColor,
                audioMetaLabel = displayAudioMetaLabel,
                horizontalTrackInset = progressSectionHorizontalInset,
                onNavigateToEqualizer = onNavigateToEqualizer
            )
        }
    }
}

@Composable
private fun EfficientSlider(
    valueState: androidx.compose.runtime.State<Float>,
    onValueChange: (Float) -> Unit,
    onValueCommit: (Float) -> Unit,
    thumbColor: Color,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    interactionSource: MutableInteractionSource,
    isPlaying: Boolean,
    isVisible: Boolean,
    trackEdgePadding: Dp
) {
    val haptics = LocalHapticFeedback.current
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val currentHaptics = rememberUpdatedState(haptics)
    val lastHapticStep = remember { intArrayOf(-1) }
    val onValueChangeWithHaptics = remember {
        { newValue: Float ->
            val quantized = (newValue.coerceIn(0f, 1f) * 20f).toInt()
            if (quantized != lastHapticStep[0]) {
                lastHapticStep[0] = quantized
                currentHaptics.value.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            currentOnValueChange.value(newValue)
        }
    }

    WavySliderExpressive(
        value = { valueState.value },
        onValueChange = onValueChangeWithHaptics,
        onValueCommit = onValueCommit,
        interactionSource = interactionSource,
        activeTrackColor = activeTrackColor,
        inactiveTrackColor = inactiveTrackColor,
        thumbColor = thumbColor,
        isPlaying = isPlaying,
        isVisible = isVisible,
        trackEdgePadding = trackEdgePadding,
        semanticsLabel = "Playback position",
        // Same 40 dp footprint as before (24 dp track + 8 dp padding above/below), but the
        // whole band is now touchable: the old padding sat outside the gesture area, so
        // touches that landed a few px off the thin track were eaten by the drag guard and
        // the scrub never started.
        minTouchHeight = 40.dp,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EfficientTimeLabels(
    positionState: androidx.compose.runtime.State<Long>,
    duration: Long,
    isVisible: Boolean,
    textColor: Color,
    audioMetaLabel: String?,
    horizontalTrackInset: Dp,
    onNavigateToEqualizer: () -> Unit
) {
    val coarsePositionMs by remember(isVisible, positionState) {
        derivedStateOf {
            if (!isVisible) 0L
            else (positionState.value.coerceAtLeast(0L) / 1000L) * 1000L
        }
    }
    val posStr by remember(isVisible, coarsePositionMs) {
        derivedStateOf { if (isVisible) formatDuration(coarsePositionMs) else "--:--" }
    }
    val durStr = remember(isVisible, duration) {
        if (isVisible) formatDuration(duration.coerceAtLeast(0L)) else "--:--"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalTrackInset)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                posStr,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Text(
                durStr,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }

        if (!audioMetaLabel.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 44.dp),
                shape = RoundedCornerShape(999.dp),
                color = textColor.copy(alpha = 0.12f),
                contentColor = textColor.copy(alpha = 0.96f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Explicit E Badge — styled small pill
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                            .widthIn(min = 16.dp)
                            .heightIn(min = 16.dp)
                            .background(textColor.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "E",
                            color = textColor,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }

                    // Dot separator
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = textColor.copy(alpha = 0.5f)
                    )

                    // Audio specs text
                    Text(
                        text = audioMetaLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp,
                            letterSpacing = 0.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Dot separator
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = textColor.copy(alpha = 0.5f)
                    )

                    // Equalizer icon — tappable, styled to match E badge visual weight
                    Box(
                        modifier = Modifier
                            .padding(start = 0.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onNavigateToEqualizer() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_monitoring_24),
                            contentDescription = "Equalizer",
                            tint = textColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DelayedContent(
    shouldDelay: Boolean,
    showPlaceholders: Boolean,
    applyPlaceholderDelayOnClose: Boolean,
    switchOnDragRelease: Boolean,
    isSheetDragGestureActive: Boolean,
    sharedBoundsModifier: Modifier = Modifier,
    expansionFractionProvider: () -> Float,
    isExpandedOverride: Boolean = false,
    normalStartThreshold: Float,
    delayAppearThreshold: Float,
    delayCloseThreshold: Float,
    placeholder: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val appearThreshold = delayAppearThreshold.coerceIn(0f, 1f)
    val closeThreshold = delayCloseThreshold.coerceIn(0f, 1f)
    var isDelayGateOpen by remember(shouldDelay) { mutableStateOf(!shouldDelay) }

    LaunchedEffect(
        shouldDelay,
        appearThreshold,
        closeThreshold,
        applyPlaceholderDelayOnClose,
        switchOnDragRelease,
        isSheetDragGestureActive,
        isExpandedOverride,
        expansionFractionProvider
    ) {
        if (!shouldDelay) {
            isDelayGateOpen = true
            return@LaunchedEffect
        }

        if (switchOnDragRelease) {
            if (isSheetDragGestureActive) {
                return@LaunchedEffect
            }

            if (isExpandedOverride) {
                isDelayGateOpen = true
            } else {
                snapshotFlow { expansionFractionProvider().coerceIn(0f, 1f) }
                    .first { fraction -> fraction <= 0.001f }
                isDelayGateOpen = false
            }
            return@LaunchedEffect
        }

        var previousExpansionFraction = expansionFractionProvider().coerceIn(0f, 1f)
        var previousExpandedOverride = isExpandedOverride

        snapshotFlow {
            val rawExpansionFraction = expansionFractionProvider().coerceIn(0f, 1f)
            val effectiveExpansionFraction =
                if (isExpandedOverride && rawExpansionFraction >= 0.985f) 1f else rawExpansionFraction
            DelayedContentFrame(
                rawExpansionFraction = rawExpansionFraction,
                effectiveExpansionFraction = effectiveExpansionFraction,
                isExpandedOverride = isExpandedOverride
            )
        }.collect { frame ->
            val isCollapsingByFraction =
                frame.rawExpansionFraction < previousExpansionFraction - 0.001f
            val isExpandingByFraction =
                frame.rawExpansionFraction > previousExpansionFraction + 0.001f
            val justStartedCollapsing =
                previousExpandedOverride && !frame.isExpandedOverride
            val justStartedExpanding =
                !previousExpandedOverride && frame.isExpandedOverride
            val isCollapsing = isCollapsingByFraction || justStartedCollapsing
            val isExpanding = isExpandingByFraction || justStartedExpanding
            val isFullyExpanded =
                frame.isExpandedOverride && frame.effectiveExpansionFraction >= 0.985f

            if (frame.effectiveExpansionFraction <= 0.001f && !frame.isExpandedOverride) {
                isDelayGateOpen = false
            } else if (isFullyExpanded) {
                isDelayGateOpen = true
            } else if (isDelayGateOpen) {
                if (applyPlaceholderDelayOnClose &&
                    isCollapsing &&
                    frame.effectiveExpansionFraction <= closeThreshold
                ) {
                    isDelayGateOpen = false
                }
            } else if (
                frame.effectiveExpansionFraction >= appearThreshold &&
                    (!applyPlaceholderDelayOnClose || isExpanding || frame.isExpandedOverride)
            ) {
                isDelayGateOpen = true
            }

            previousExpansionFraction = frame.rawExpansionFraction
            previousExpandedOverride = frame.isExpandedOverride
        }
    }

    val baseAlphaProvider = remember(normalStartThreshold, expansionFractionProvider) {
        {
            ((expansionFractionProvider().coerceIn(0f, 1f) - normalStartThreshold) /
                (1f - normalStartThreshold).coerceAtLeast(0.001f))
                .coerceIn(0f, 1f)
        }
    }
    val contentBlendAlpha by animateFloatAsState(
        targetValue = if (isDelayGateOpen) 1f else 0f,
        animationSpec = if (isDelayGateOpen) {
            tween(durationMillis = 260, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 140, easing = FastOutSlowInEasing)
        },
        label = "DelayedContentBlendAlpha"
    )
    val placeholderBlendAlpha by animateFloatAsState(
        targetValue = if (isDelayGateOpen) 0f else 1f,
        animationSpec = if (isDelayGateOpen) {
            tween(durationMillis = 360, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 140, easing = FastOutSlowInEasing)
        },
        label = "DelayedPlaceholderBlendAlpha"
    )

    if (shouldDelay) {
        Box(modifier = sharedBoundsModifier) {
            val shouldComposeContent = isDelayGateOpen

            if (shouldComposeContent) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = contentBlendAlpha * baseAlphaProvider()
                    }
                ) {
                    content()
                }
            }
            if (showPlaceholders && placeholderBlendAlpha > 0.001f) {
                Box(
                    modifier = Modifier.graphicsLayer { alpha = placeholderBlendAlpha }
                ) {
                    placeholder()
                }
            }
        }
    } else {
        Box(
            modifier = sharedBoundsModifier.graphicsLayer { alpha = baseAlphaProvider() }
        ) {
            content()
        }
    }
}

private data class DelayedContentFrame(
    val rawExpansionFraction: Float,
    val effectiveExpansionFraction: Float,
    val isExpandedOverride: Boolean
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlayerSongInfo(
    song: Song? = null,
    title: String,
    artist: String,
    artistId: Long,
    artists: List<Artist>,
    expansionFractionProvider: () -> Float,
    textColor: Color,
    artistTextColor: Color,
    gradientEdgeColor: Color,
    playerViewModel: PlayerViewModel,
    onClickArtist: () -> Unit,
    modifier: Modifier = Modifier,
    isPlayingProvider: () -> Boolean = { true }
) {
    val coroutineScope = rememberCoroutineScope()
    var isNavigatingToArtist by remember { mutableStateOf(false) }
    val resolvedArtistId by remember(artists, artistId) {
        derivedStateOf { artists.firstOrNull { it.id != 0L && it.id != -1L }?.id ?: artistId }
    }
    val titleStyle = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        fontFamily = GoogleSansRounded,
        color = textColor
    )

    val artistStyle = MaterialTheme.typography.titleMedium.copy(
        letterSpacing = 0.sp,
        color = artistTextColor
    )

    Column(
        horizontalAlignment = Alignment.Start,
            modifier = modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth()
            .graphicsLayer {
                val fraction = expansionFractionProvider()
                alpha = fraction // Or apply specific fade logic if desired
                translationY = (1f - fraction) * 24f
            }
    ) {
        // We pass 1f to AutoScrollingTextOnDemand because the alpha/translation is now handled by the parent Column graphicsLayer
        // and we want it "fully rendered" but hidden/moved by the layer.
        // Actually, AutoScrollingTextOnDemand uses expansionFraction to start scrolling only when fully expanded?
        // Let's check AutoScrollingTextOnDemand. Assuming it uses it for scrolling trigger.
        // If we want to avoid recomposition, we might need to pass the provider or just 1f if scrolling logic handles itself.
        // For now, let's pass the current value from provider for logic correctness, but ideally this component should be optimized too.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            AutoScrollingTextOnDemand(
                text = title,
                style = titleStyle,
                gradientEdgeColor = gradientEdgeColor,
                expansionFractionProvider = expansionFractionProvider,
                modifier = Modifier.weight(1f, fill = false),
                canScroll = isPlayingProvider()
            )
        }
        Spacer(modifier = Modifier.height(2.dp))



        AutoScrollingTextOnDemand(
            text = artist,
            style = artistStyle,
            gradientEdgeColor = gradientEdgeColor,
            expansionFractionProvider = expansionFractionProvider,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isNavigatingToArtist) return@combinedClickable
                        coroutineScope.launch {
                            isNavigatingToArtist = true
                            try {
                                onClickArtist()
                            } finally {
                                isNavigatingToArtist = false
                            }
                        }
                    },

                onLongClick = {
                    if (isNavigatingToArtist) return@combinedClickable
                    coroutineScope.launch {
                        isNavigatingToArtist = true
                        try {
                            playerViewModel.triggerArtistNavigationFromPlayer(resolvedArtistId)
                        } finally {
                            isNavigatingToArtist = false
                        }
                    }
                }
            ),
            canScroll = isPlayingProvider()
        )
    }
}

@Composable
private fun PlaceholderBox(
    modifier: Modifier,
    cornerRadius: Dp = 12.dp,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = color,
        tonalElevation = 0.dp
    ) {}
}

@Composable
private fun AlbumPlaceholder(
    height: Dp,
    color: Color,
    onColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(18.dp),
        color = color,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                modifier = Modifier.size(86.dp),
                painter = painterResource(R.drawable.pixelplay_base_monochrome),
                contentDescription = null,
                tint = onColor
            )
        }
    }
}

@Composable
private fun MetadataPlaceholder(
    expansionFractionProvider: () -> Float,
    color: Color,
    onColor: Color,
    showQueueButtons: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .graphicsLayer {
                val expansionFraction = expansionFractionProvider().coerceIn(0f, 1f)
                alpha = expansionFraction.coerceIn(0f, 1f)
                translationY = (1f - expansionFraction.coerceIn(0f, 1f)) * 24f
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(6.dp) //2.dp
        ) {
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(27.dp), //30.dp
                cornerRadius = 8.dp,
                color = color
            )
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth(0.46f)
                    .height(17.dp), //20.dp
                cornerRadius = 8.dp,
                color = onColor
            )
        }

        if (showQueueButtons) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(height = 42.dp, width = 50.dp),
                    shape = RoundedCornerShape(
                        topStart = 50.dp,
                        topEnd = 6.dp,
                        bottomStart = 50.dp,
                        bottomEnd = 6.dp
                    ),
                    color = onColor,
                    tonalElevation = 0.dp
                ) {}
                Surface(
                    modifier = Modifier.size(height = 42.dp, width = 50.dp),
                    shape = RoundedCornerShape(
                        topStart = 6.dp,
                        topEnd = 50.dp,
                        bottomStart = 6.dp,
                        bottomEnd = 50.dp
                    ),
                    color = onColor,
                    tonalElevation = 0.dp
                ) {}
            }
        } else {
            PlaceholderBox(
                modifier = Modifier.size(width = 48.dp, height = 48.dp),
                cornerRadius = 24.dp,
                color = onColor
            )
        }
    }
}

@Composable
private fun ProgressPlaceholder(
    color: Color,
    onColor: Color,
    showAudioMetaChip: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                cornerRadius = 3.dp,
                color = onColor.copy(alpha = 0.15f)
            )
            // Keep active segment in the layout tree but invisible to avoid visual noise.
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth(0.34f)
                    .height(6.dp)
                    .graphicsLayer { alpha = 0f },
                cornerRadius = 3.dp,
                color = color
            )
            // Keep thumb slot aligned but fully transparent.
            PlaceholderBox(
                modifier = Modifier
                    .padding(start = 92.dp)
                    .size(14.dp)
                    .graphicsLayer { alpha = 0f },
                cornerRadius = 7.dp,
                color = onColor
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaceholderBox(
                    modifier = Modifier
                        .width(34.dp)
                        .height(12.dp),
                    cornerRadius = 2.dp,
                    color = onColor
                )
                PlaceholderBox(
                    modifier = Modifier
                        .width(34.dp)
                        .height(12.dp),
                    cornerRadius = 2.dp,
                    color = onColor
                )
            }

            if (showAudioMetaChip) {
                PlaceholderBox(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(min = 96.dp, max = 180.dp)
                        .height(18.dp),
                    cornerRadius = 999.dp,
                    color = onColor.copy(alpha = 0.15f)
                )
            }
        }
    }
}

@Composable
private fun ControlsPlaceholder(color: Color, onColor: Color) {
    val rowCorners = 60.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(80.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaceholderBox(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    cornerRadius = 60.dp,
                    color = onColor
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = rowCorners,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBL = rowCorners,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusTR = rowCorners,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBR = rowCorners,
                        smoothnessAsPercentBR = 60
                    ),
                    color = color,
                    tonalElevation = 0.dp
                ) {}
                PlaceholderBox(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    cornerRadius = 60.dp,
                    color = onColor
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 66.dp, max = 86.dp)
                .padding(horizontal = 26.dp)
                .padding(bottom = 6.dp)
                .background(
                    color = onColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(rowCorners)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    PlaceholderBox(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        cornerRadius = rowCorners,
                        color = onColor.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

private data class TransportButtonColors(
    val container: Color,
    val content: Color
)

private fun expressivePlayPauseButtonColors(colorScheme: ColorScheme): TransportButtonColors {
    return TransportButtonColors(
        container = colorScheme.tertiaryFixedDim,
        content = colorScheme.onTertiaryFixed
    )
}

private fun expressiveSkipButtonColors(colorScheme: ColorScheme): TransportButtonColors {
    return TransportButtonColors(
        container = colorScheme.secondaryFixedDim,
        content = colorScheme.onSecondaryFixed
    )
}

@Composable
private fun BottomToggleRow(
    modifier: Modifier,
    isShuffleEnabled: Boolean,
    isShuffleTransitionInProgress: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDislike: () -> Unit,
    activeMixFlavor: com.theveloper.pixelplay.data.MixFlavor? = null,
    onMixCycle: () -> Unit = onShuffleToggle,
    onSmartMixActivate: (() -> Unit)? = null
) {
    val isFavorite = isFavoriteProvider()
    val rowCorners = 60.dp
    val inactiveBg = LocalMaterialTheme.current.onSurface.copy(alpha = 0.07f)
    val inactiveContentColor = LocalMaterialTheme.current.onSurface
    val isSmart = activeMixFlavor == com.theveloper.pixelplay.data.MixFlavor.SMART
    val isMixActive = activeMixFlavor != null || isShuffleEnabled


    Box(
        modifier = modifier.background(
            color = LocalMaterialTheme.current.surfaceContainerLowest.copy(alpha = 0.7f),
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusBL = rowCorners,
                smoothnessAsPercentTR = 60,
                cornerRadiusBR = rowCorners,
                smoothnessAsPercentBL = 60,
                cornerRadiusTL = rowCorners,
                smoothnessAsPercentBR = 60,
                cornerRadiusTR = rowCorners,
                smoothnessAsPercentTL = 60
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .clip(
                    AbsoluteSmoothCornerShape(
                        cornerRadiusBL = rowCorners,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBR = rowCorners,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusTL = rowCorners,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusTR = rowCorners,
                        smoothnessAsPercentTL = 60
                    )
                )
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val commonModifier = Modifier.weight(1f)

            ToggleSegmentButton(
                modifier = commonModifier,
                active = isMixActive,
                enabled = !isShuffleTransitionInProgress,
                activeColor = if (isSmart) LocalMaterialTheme.current.tertiaryFixed else LocalMaterialTheme.current.primaryFixed,
                activeCornerRadius = rowCorners,
                activeContentColor = if (isSmart) LocalMaterialTheme.current.onTertiaryFixed else LocalMaterialTheme.current.onPrimaryFixed,
                inactiveColor = inactiveBg,
                inactiveContentColor = inactiveContentColor,
                onClick = onMixCycle,
                onLongClick = onSmartMixActivate,
                painter = if (isSmart) rememberVectorPainter(Icons.Rounded.AutoAwesome) else painterResource(R.drawable.rounded_shuffle_24),
                contentDesc = when {
                    isSmart -> "Smart Mix"
                    activeMixFlavor == com.theveloper.pixelplay.data.MixFlavor.NORMAL -> "Normal Mix"
                    isShuffleEnabled -> "Aleatorio"
                    else -> "No Mix"
                }
            )
            // Order: Mix | Dislike | Like (Like on the right).
            ToggleSegmentButton(
                modifier = commonModifier,
                active = false,
                activeColor = LocalMaterialTheme.current.errorContainer,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onErrorContainer,
                inactiveColor = inactiveBg,
                inactiveContentColor = inactiveContentColor,
                onClick = onDislike,
                // Same broken heart as "Unlike" in playlist select mode.
                painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(androidx.compose.material.icons.Icons.Rounded.HeartBroken),
                contentDesc = "Mix feedback"
            )
            ToggleSegmentButton(
                modifier = commonModifier,
                active = isFavorite,
                activeColor = LocalMaterialTheme.current.tertiaryFixed,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onTertiaryFixed,
                inactiveColor = inactiveBg,
                inactiveContentColor = inactiveContentColor,
                onClick = onFavoriteToggle,
                iconId = if (isFavorite) R.drawable.round_favorite_24 else R.drawable.rounded_favorite_24,
                contentDesc = "Favorite"
            )
        }
    }
}

// --- 1. Custom Organic Shapes ---

/**
 * Top-Left: Asymmetrical teardrop/leaf shape.
 * The shape hugs the top-left corner and features a sharper taper pointing towards the bottom-right.
 */
val TopLeftTeardropShape: Shape = GenericShape { size: Size, _ ->
    val w = size.width
    val h = size.height
    
    moveTo(0f, 0f) // Top-left corner
    
    // Curve 1: Bulges towards top-right, tapering down to the bottom-right point
    cubicTo(
        w * 0.6f, 0f,         // Control point 1 (pulls top edge to the right)
        w * 0.9f, h * 0.6f,   // Control point 2 (pulls right edge down)
        w, h                  // End point (Bottom-right tip)
    )
    
    // Curve 2: Organic wide curve returning to the origin
    cubicTo(
        w * 0.4f, h * 0.9f,   // Control point 1 (pulls bottom edge to the right)
        0f, h * 0.2f,         // Control point 2 (pulls left edge down)
        0f, 0f                // End point (Top-left origin)
    )
    close()
}

/**
 * Top-Right: Mirrored asymmetrical teardrop/leaf shape.
 * The shape hugs the top-right corner and tapers sharply towards the bottom-left.
 */
val TopRightTeardropShape: Shape = GenericShape { size: Size, _ ->
    val w = size.width
    val h = size.height
    
    moveTo(w, 0f) // Top-right corner
    
    // Curve 1: Bulges towards top-left, tapering down to the bottom-left point
    cubicTo(
        w * 0.4f, 0f,         // Control point 1
        w * 0.1f, h * 0.6f,   // Control point 2
        0f, h                 // End point (Bottom-left tip)
    )
    
    // Curve 2: Organic wide curve returning to the origin
    cubicTo(
        w * 0.6f, h * 0.9f,   // Control point 1
        w, h * 0.2f,          // Control point 2
        w, 0f                 // End point (Top-right origin)
    )
    close()
}

/**
 * Bottom-Left: Corner-hugging fluid shape.
 * Clips cleanly against left and bottom display edges while maintaining an organic inner boundary.
 */
val BottomLeftPebbleShape: Shape = GenericShape { size: Size, _ ->
    val w = size.width
    val h = size.height
    
    moveTo(0f, 0f)       // Top-left
    lineTo(0f, h)         // Left edge straight down
    lineTo(w, h)          // Bottom edge straight across
    
    // Organic inner boundary bulging towards the top-right
    cubicTo(
        w, h * 0.6f,      // Control point 1
        w * 0.4f, 0f,      // Control point 2
        0f, 0f             // End point (Top-left origin)
    )
    close()
}

/**
 * Bottom-Right: Corner-hugging fluid shape.
 * Mirrors the Bottom-Left pebble, clipping cleanly against right and bottom display edges.
 */
val BottomRightPebbleShape: Shape = GenericShape { size: Size, _ ->
    val w = size.width
    val h = size.height
    
    moveTo(w, 0f)        // Top-right
    lineTo(w, h)          // Right edge straight down
    lineTo(0f, h)         // Bottom edge straight across
    
    // Organic inner boundary bulging towards the top-left
    cubicTo(
        0f, h * 0.6f,     // Control point 1
        w * 0.6f, 0f,      // Control point 2
        w, 0f              // End point (Top-right origin)
    )
    close()
}

// --- 2. Overlay Composable ---

@Composable
fun MusicPlayerCornerOverlay(
    onBluetoothClick: () -> Unit,
    onQueueClick: () -> Unit,
    castIconPainter: Painter,
    modifier: Modifier = Modifier,
    containerColor: Color = LocalMaterialTheme.current.onPrimary.copy(alpha = 0.55f),
    contentColor: Color = LocalMaterialTheme.current.primary,
    onQueueDragStart: () -> Unit = {},
    onQueueDrag: (Float) -> Unit = {},
    onQueueRelease: (Float, Float) -> Unit = { _, _ -> },
    enabled: Boolean = true,
    /** Mixing / songs being added: the queue button's outline animates. */
    queueBusy: androidx.compose.runtime.State<Boolean>? = null
) {
    // Root Box for absolute positioning of the 2 bottom corners
    Box(modifier = modifier.fillMaxSize()) {
        
        // Bottom-Left: Bluetooth
        CornerControlButton(
            modifier = Modifier.align(Alignment.BottomStart),
            shape = BottomLeftPebbleShape,
            painter = castIconPainter,
            contentDescription = "Bluetooth",
            iconOffsetX = (-8).dp,
            iconOffsetY = 8.dp,
            containerColor = containerColor,
            contentColor = contentColor,
            onClick = onBluetoothClick,
            onSwipeUp = onBluetoothClick,
            enabled = enabled
        )

        // Bottom-Right: Queue/Playlist music
        CornerControlButton(
            modifier = Modifier.align(Alignment.BottomEnd),
            shape = BottomRightPebbleShape,
            painter = painterResource(R.drawable.rounded_queue_music_24),
            contentDescription = "Queue",
            iconOffsetX = 8.dp,
            iconOffsetY = 8.dp,
            containerColor = containerColor,
            contentColor = contentColor,
            onClick = onQueueClick,
            onDragStart = onQueueDragStart,
            onVerticalDrag = onQueueDrag,
            onDragEnd = onQueueRelease,
            enabled = enabled,
            busy = queueBusy?.value == true
        )
    }
}

// --- 3. Reusable Isolated Component ---

/**
 * Handles the custom background clipping, click bounds, icon visual offsetting,
 * and unified tap / vertical drag gestures so interactions never leak to the player sheet.
 */
@Composable
private fun CornerControlButton(
    modifier: Modifier = Modifier,
    shape: Shape,
    painter: Painter,
    contentDescription: String,
    iconOffsetX: Dp,
    iconOffsetY: Dp,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    onSwipeUp: (() -> Unit)? = null,
    onDragStart: (() -> Unit)? = null,
    onVerticalDrag: ((Float) -> Unit)? = null,
    onDragEnd: ((Float, Float) -> Unit)? = null,
    enabled: Boolean = true,
    busy: Boolean = false
) {
    val buttonSize = 72.dp // Dimensions that give the organic curves enough space to breathe
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 32.dp.toPx() } }

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnVerticalDrag by rememberUpdatedState(onVerticalDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentEnabled by rememberUpdatedState(enabled)

    val buttonSizePx = with(density) { Size(buttonSize.toPx(), buttonSize.toPx()) }
    val shapeRegion = remember(shape, buttonSizePx, density) {
        when (val outline = shape.createOutline(buttonSizePx, LayoutDirection.Ltr, density)) {
            is Outline.Generic -> {
                val androidPath = outline.path.asAndroidPath()
                val bounds = RectF()
                androidPath.computeBounds(bounds, true)
                Region().apply {
                    setPath(
                        androidPath,
                        Region(
                            (bounds.left - 1).toInt(),
                            (bounds.top - 1).toInt(),
                            (bounds.right + 1).toInt(),
                            (bounds.bottom + 1).toInt()
                        )
                    )
                }
            }
            else -> null
        }
    }

    Box(
        modifier = modifier
            .size(buttonSize)
            // Drawn over the clipped button, so the running light sits on the pebble's edge.
            .queueBusyOutline(
                active = busy,
                shape = shape,
                color = contentColor,
                strokeWidth = 3.dp
            )
            .clip(shape) // Clips both the background drawing AND the clickable interaction bounds
            .background(containerColor)
            .semantics {
                this.contentDescription = contentDescription
                this.onClick(label = contentDescription) {
                    if (enabled) {
                        currentOnClick()
                        true
                    } else {
                        false
                    }
                }
            }
            // Not keyed on `enabled`: dragging the queue pebble opens the queue sheet, which
            // disables these buttons. Restarting the pointerInput then cancelled the drag before
            // release and left the queue half open. Enabled is checked when a gesture starts.
            .pointerInput(shape) {
                detectTapAndVerticalDrag(
                    onTap = { currentOnClick() },
                    onDragStart = { currentOnDragStart?.invoke() },
                    onVerticalDrag = { _, dragAmount -> currentOnVerticalDrag?.invoke(dragAmount) },
                    onDragEnd = { totalDragY, velocityY ->
                        if (currentOnDragEnd != null) {
                            currentOnDragEnd?.invoke(totalDragY, velocityY)
                        } else if (currentOnSwipeUp != null) {
                            val isSwipeThresholdMet = totalDragY < -swipeThresholdPx
                            val isFlick = velocityY < -450f
                            if (isSwipeThresholdMet || isFlick) {
                                currentOnSwipeUp?.invoke()
                            }
                        }
                    },
                    filterStart = { position ->
                        if (!currentEnabled) {
                            false
                        } else if (position.x < 0f || position.x > buttonSizePx.width || position.y < 0f || position.y > buttonSizePx.height) {
                            false
                        } else if (shapeRegion != null) {
                            shapeRegion.contains(position.x.toInt(), position.y.toInt())
                        } else {
                            true
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier
                .size(24.dp)
                .offset(x = iconOffsetX, y = iconOffsetY) // Nudges icon towards the visual center of mass
        )
    }
}

/** Hides status + navigation bars for full-screen video; an edge swipe shows them briefly. */
private fun hideSystemBarsForVideo(activity: android.app.Activity) {
    val window = activity.window ?: return
    val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior =
        androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
}

/** Reports the bottom (or, with [top], the top) of this element in root coordinates, in px. */
private fun Modifier.onGloballyPositionedCover(top: Boolean = false, onEdge: (Float) -> Unit): Modifier =
    this.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        onEdge(if (top) bounds.top else bounds.bottom)
    }
