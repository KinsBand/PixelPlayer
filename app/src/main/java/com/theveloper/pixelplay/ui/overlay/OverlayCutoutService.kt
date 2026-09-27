package com.theveloper.pixelplay.ui.overlay

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.MainActivityIntentContract
import com.theveloper.pixelplay.data.media.MediaMapper
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemeProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Android Service that manages the system overlay Window and ComposeView for the
 * Dynamic Cutout Island Widget on Google Pixel 10 Pro and other modern Android devices.
 */
@AndroidEntryPoint
class OverlayCutoutService : Service() {

    @Inject
    lateinit var musicRepository: MusicRepository

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var colorSchemeProcessor: ColorSchemeProcessor

    /** Palette style / accuracy / light-dark mode: the island uses the same album colours as the app. */
    @Inject
    lateinit var themePreferencesRepository: com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository

    @Inject
    lateinit var mediaMapper: MediaMapper

    /** Same-process singleton; only read for its audio session id (for the wave ring). */
    @Inject
    lateinit var playerEngine: DualPlayerEngine

    private val audioReactor by lazy { IslandAudioReactor(applicationContext) }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null
    private var lyricsLoadJob: Job? = null
    private var themeLoadJob: Job? = null

    /** The app's album-colour settings, mirrored so the island's palette matches the player's. */
    private data class IslandPalette(
        val style: AlbumArtPaletteStyle = AlbumArtPaletteStyle.TONAL_SPOT,
        val accuracy: Int = com.theveloper.pixelplay.data.preferences.AlbumArtColorAccuracy.DEFAULT,
        val themeMode: String = com.theveloper.pixelplay.data.preferences.AppThemeMode.FOLLOW_SYSTEM
    )
    private var islandPalette = IslandPalette()

    private var activeWindowManager: WindowManager? = null
    private var overlayComposeView: ComposeView? = null
    /** Transparent window over the camera that takes taps on the closed pill. */
    private var touchView: View? = null
    private var currentLayoutParams: WindowManager.LayoutParams? = null

    /** True when the current window was added through CutoutIslandAccessibilityService. */
    private var usingAccessibilityWindow = false

    private var mediaController: MediaController? = null
    private val lifecycleOwner = OverlayServiceLifecycleOwner()

    private val accessibilityListener: (Boolean) -> Unit = { isRunning ->
        serviceScope.launch(Dispatchers.Main) {
            Timber.d("CutoutIslandAccessibilityService running state changed: $isRunning -> recreating overlay window")
            recreateOverlayWindow()
        }
    }

    private val uiState = MutableStateFlow(OverlayCutoutState())

    /**
     * Playback position, kept out of [uiState] so a tick never recomposes the island.
     * The island interpolates between ticks on the frame clock, so this can stay coarse.
     */
    private val positionFlow = MutableStateFlow(0L)

    /** Size the window currently has. The island layout asks for changes via [applyWindowSpec]. */
    private var windowSpec: OverlayWindowSpec = OverlayWindowSpec.Collapsed
    /**
     * Rotation lock: the island is glued to the camera. Everything is worked out in the
     * display's natural (portrait) frame; in landscape the window is moved onto the camera
     * edge and its content turned (IslandRotationFrame), so it keeps exactly the portrait
     * position, size and look. See [IslandWindowPlacement].
     */
    private var lastCutoutGeometry: CutoutGeometry? = null

    /** Natural-frame display size + current rotation; Compose state so the island follows. */
    private var islandDisplay by androidx.compose.runtime.mutableStateOf(IslandDisplay(1080, 2400))

    private val displayManager by lazy {
        getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
    }

    private fun defaultDisplay(): android.view.Display? =
        displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)

    private fun readIslandDisplay(): IslandDisplay {
        val display = defaultDisplay()
        val size = android.graphics.Point()
        @Suppress("DEPRECATION")
        display?.getRealSize(size)
        if (size.x <= 0 || size.y <= 0) {
            size.set(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        }
        // The screen's own corner radius, so the open island follows the display's curve.
        val cornerRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && display != null) {
            listOf(
                android.view.RoundedCorner.POSITION_TOP_LEFT,
                android.view.RoundedCorner.POSITION_TOP_RIGHT,
                android.view.RoundedCorner.POSITION_BOTTOM_LEFT,
                android.view.RoundedCorner.POSITION_BOTTOM_RIGHT
            ).maxOf { display.getRoundedCorner(it)?.radius ?: 0 }
        } else 0
        return IslandDisplay(
            naturalWidthPx = minOf(size.x, size.y),
            naturalHeightPx = maxOf(size.x, size.y),
            rotation = display?.rotation ?: android.view.Surface.ROTATION_0,
            cornerRadiusPx = cornerRadius
        )
    }

    /**
     * Camera geometry in the natural frame, from whatever rotation the screen is in now.
     * Falls back to the last good measurement, then to a centred punch-hole.
     */
    private fun resolveNaturalGeometry(
        cutout: android.view.DisplayCutout?,
        display: IslandDisplay
    ): CutoutGeometry {
        val density = Density(resources.displayMetrics.density)
        val rects = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && cutout != null) {
            cutout.boundingRects.map { IslandWindowPlacement.toNatural(it, display) }
        } else emptyList()
        val resolved = OverlayCutoutDimensions.resolveNaturalCutoutGeometry(rects, density)
            ?: lastCutoutGeometry
            ?: OverlayCutoutDimensions.fallbackGeometry(density, display.naturalWidthPx)
        lastCutoutGeometry = resolved
        return resolved
    }

    private fun currentCutout(): android.view.DisplayCutout? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) defaultDisplay()?.cutout else null

    /**
     * Safety net for the landscape direction: the camera must map back onto the natural top
     * edge. If it only does with the opposite quarter-turn, this device reports rotation the
     * other way round, so use that one (otherwise the island would land on the wrong side).
     */
    private fun calibrated(display: IslandDisplay, cutout: android.view.DisplayCutout?): IslandDisplay {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || cutout == null) return display
        if (display.rotation != android.view.Surface.ROTATION_90 && display.rotation != android.view.Surface.ROTATION_270) return display
        fun cameraOnTop(d: IslandDisplay) = cutout.boundingRects
            .map { IslandWindowPlacement.toNatural(it, d) }
            .any { !it.isEmpty && it.top < 150 }
        if (cameraOnTop(display)) return display
        val flipped = display.copy(
            rotation = if (display.rotation == android.view.Surface.ROTATION_90) android.view.Surface.ROTATION_270
            else android.view.Surface.ROTATION_90
        )
        return if (cameraOnTop(flipped)) flipped else display
    }

    /** Re-reads rotation + cutout and moves the window with them. */
    private fun refreshDisplay() {
        val display = calibrated(readIslandDisplay(), currentCutout())
        val geometry = resolveNaturalGeometry(currentCutout(), display)
        val changed = display != islandDisplay || geometry != currentCutoutGeometry
        islandDisplay = display
        currentCutoutGeometry = geometry
        if (changed) applyWindowSpec(windowSpec, force = true)
    }

    // Catches every rotation, including portrait → upside-down, which is not a configuration change.
    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == android.view.Display.DEFAULT_DISPLAY) refreshDisplay()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshDisplay()
        // Following the system theme: a light/dark switch swaps the island's palette half too.
        if (islandPalette.themeMode == com.theveloper.pixelplay.data.preferences.AppThemeMode.FOLLOW_SYSTEM) {
            uiState.value.song?.let(::loadColorSchemeForSong)
        }
    }

    private var currentCutoutGeometry by androidx.compose.runtime.mutableStateOf(CutoutGeometry(
        centerXDp = OverlayCutoutDimensions.DEFAULT_SCREEN_WIDTH / 2,
        centerYDp = OverlayCutoutDimensions.DEFAULT_CUTOUT_CENTER_Y,
        topDp = 8.dp,
        widthDp = OverlayCutoutDimensions.DEFAULT_CUTOUT_DIAMETER,
        heightDp = OverlayCutoutDimensions.DEFAULT_CUTOUT_DIAMETER,
        bottomDp = 30.dp,
        isRealHardwareCutout = false
    ))

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            handleMediaItemChanged(mediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            publishPosition()
            uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startPlaybackTicker()
            } else {
                stopPlaybackTicker()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // Seeks (including taps on a lyric line) move the roll immediately.
            publishPosition()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                if (mediaController?.currentMediaItem == null) {
                    uiState.update { it.copy(isVisible = false) }
                    stopPlaybackTicker()
                }
            } else {
                uiState.update { it.copy(isVisible = true) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()
        CutoutIslandAccessibilityService.addStateListener(accessibilityListener)

        initMediaController()
        observePreferences()
        displayManager.registerDisplayListener(displayListener, android.os.Handler(android.os.Looper.getMainLooper()))
        setupOverlayView()
        observeVisibility()
        observeAudio()
        observePalettePrefs()
    }

    private fun observePalettePrefs() {
        serviceScope.launch {
            combine(
                themePreferencesRepository.albumArtPaletteStyleFlow,
                themePreferencesRepository.albumArtColorAccuracyFlow,
                themePreferencesRepository.appThemeModeFlow
            ) { style, accuracy, mode -> IslandPalette(style, accuracy, mode) }
                .distinctUntilChanged()
                .collect { palette ->
                    val changed = palette != islandPalette
                    islandPalette = palette
                    if (changed) uiState.value.song?.let(::loadColorSchemeForSong)
                }
        }
    }

    private fun islandUsesDarkScheme(): Boolean = when (islandPalette.themeMode) {
        com.theveloper.pixelplay.data.preferences.AppThemeMode.DARK -> true
        com.theveloper.pixelplay.data.preferences.AppThemeMode.LIGHT -> false
        else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /** The wave ring's audio capture runs only while a song is actually playing and shown. */
    private fun observeAudio() {
        serviceScope.launch {
            combine(
                playerEngine.activeAudioSessionId,
                uiState.map { it.isPlaying && it.isVisible }.distinctUntilChanged()
            ) { session, active -> session to active }
                .collect { (session, active) -> audioReactor.update(session, active) }
        }
    }

    /**
     * The window stays attached while nothing is playing, so it must stop taking
     * touches while hidden - otherwise an invisible strip over the cutout swallows
     * status-bar pulls. Re-applies the layout params whenever visibility flips.
     */
    private fun observeVisibility() {
        serviceScope.launch {
            uiState.map { it.isVisible }.distinctUntilChanged().collect { visible ->
                if (!visible) {
                    // Nothing is drawn while hidden, so the layout can't settle the window itself.
                    uiState.update { it.copy(expansionLevel = CutoutExpansionLevel.COLLAPSED) }
                    windowSpec = OverlayWindowSpec.Collapsed
                }
                applyWindowSpec(windowSpec, force = true)
            }
        }
        serviceScope.launch {
            uiState.map { it.isVisible to it.expansionLevel }.distinctUntilChanged().collect {
                updateTouchWindow()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        CutoutIslandAccessibilityService.removeStateListener(accessibilityListener)
        runCatching { displayManager.unregisterDisplayListener(displayListener) }
        stopPlaybackTicker()
        audioReactor.release()
        serviceScope.cancel()
        removeOverlayView()
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        lifecycleOwner.onDestroy()
    }

    private fun initMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                controller.addListener(playerListener)

                handleMediaItemChanged(controller.currentMediaItem)
                val isPlaying = controller.isPlaying
                uiState.update {
                    it.copy(
                        isPlaying = isPlaying,
                        isVisible = controller.currentMediaItem != null
                    )
                }
                if (isPlaying) startPlaybackTicker()
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect to MediaController in OverlayCutoutService")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observePreferences() {
        serviceScope.launch {
            userPreferencesRepository.enableCutoutOverlayFlow.collectLatest { enabled ->
                if (!enabled) {
                    stopSelf()
                }
            }
        }
        // Per-song lyrics sync offset, so the island's lines change at exactly the same moment
        // as the lyrics sheet and the album cover (which both add this offset).
        serviceScope.launch {
            uiState
                .map { it.song?.id }
                .distinctUntilChanged()
                .flatMapLatest { songId ->
                    if (songId == null) flowOf(0) else userPreferencesRepository.getLyricsSyncOffsetFlow(songId)
                }
                .distinctUntilChanged()
                .collect { offset ->
                    uiState.update { it.copy(lyricsSyncOffsetMs = offset) }
                    publishPosition()
                }
        }
    }

    private fun handleMediaItemChanged(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            uiState.update { it.copy(song = null, lyrics = null, activeLineIndex = -1, isVisible = false) }
            return
        }

        val song = mediaMapper.resolveSongFromMediaItem(mediaItem)
        publishPosition()
        uiState.update {
            // Drop the previous song's lyrics straight away so its lines never roll under the
            // new title. The colour scheme is kept until the new one is ready, so the island
            // animates from the old palette to the new one instead of flashing a default.
            it.copy(
                song = song,
                lyrics = if (it.song?.id == song?.id) it.lyrics else null,
                activeLineIndex = -1,
                isVisible = true
            )
        }

        if (song != null) {
            loadLyricsForSong(song)
            loadColorSchemeForSong(song)
        }
    }

    private fun loadLyricsForSong(song: Song) {
        lyricsLoadJob?.cancel()
        lyricsLoadJob = serviceScope.launch {
            try {
                val lyrics = musicRepository.getLyrics(song)
                val synced = lyrics?.synced.orEmpty()
                uiState.update {
                    if (it.song?.id != song.id) return@update it
                    it.copy(
                        lyrics = lyrics,
                        activeLineIndex = resolveActiveLineIndex(synced, positionFlow.value + it.lyricsSyncOffsetMs)
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to load lyrics for overlay")
                uiState.update { it.copy(lyrics = null, activeLineIndex = -1) }
            }
        }
    }

    private fun loadColorSchemeForSong(song: Song) {
        themeLoadJob?.cancel()
        themeLoadJob = serviceScope.launch {
            try {
                val uri = song.albumArtUriString ?: ""
                val palette = islandPalette
                // Same palette style, accuracy and light/dark half as the full player, so the
                // island's surface and lyrics read exactly like the album cover and lyrics sheet.
                val schemePair = colorSchemeProcessor.getOrGenerateColorScheme(
                    albumArtUri = uri,
                    paletteStyle = palette.style,
                    colorAccuracyLevel = palette.accuracy
                )
                if (schemePair != null) {
                    val scheme = if (islandUsesDarkScheme()) schemePair.dark else schemePair.light
                    // The layout animates every role to the new values.
                    uiState.update {
                        if (it.song?.id != song.id) it else it.copy(colorScheme = scheme)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to extract color scheme for overlay")
            }
        }
    }

    private fun startPlaybackTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (true) {
                publishPosition()
                delay(250)
            }
        }
    }

    private fun stopPlaybackTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /** Pushes the controller's position; touches [uiState] only when the line changes. */
    private fun publishPosition() {
        val pos = mediaController?.currentPosition ?: return
        positionFlow.value = pos
        val current = uiState.value
        val newIndex = resolveActiveLineIndex(current.syncedLines, pos + current.lyricsSyncOffsetMs)
        if (newIndex != current.activeLineIndex) {
            uiState.update { it.copy(activeLineIndex = newIndex) }
        }
    }

    private fun resolveActiveLineIndex(lines: List<SyncedLine>, posMs: Long): Int {
        if (lines.isEmpty()) return -1
        return lines.withIndex().lastOrNull { (index, line) ->
            val nextTime = lines.getOrNull(index + 1)?.time?.toLong() ?: Long.MAX_VALUE
            posMs in line.time.toLong()..<nextTime
        }?.index ?: -1
    }

    private fun recreateOverlayWindow() {
        // Always rebuild. Skipping the rebuild while nothing was playing left the
        // service with no window at all, so the island never came back once a song started.
        removeOverlayView()
        setupOverlayView()
    }

    @SuppressLint("InflateParams")
    private fun setupOverlayView() {
        if (overlayComposeView != null) return

        val isAccessibility = CutoutIslandAccessibilityService.isRunning
        if (!Settings.canDrawOverlays(this) && !isAccessibility) {
            Timber.w("Cannot draw overlay: SYSTEM_ALERT_WINDOW permission missing and accessibility service not running")
            stopSelf()
            return
        }

        islandDisplay = calibrated(readIslandDisplay(), currentCutout())
        currentCutoutGeometry = resolveNaturalGeometry(currentCutout(), islandDisplay)

        val accessibilityService = CutoutIslandAccessibilityService.instance
        val contextToUse: Context = accessibilityService ?: this
        val wm = contextToUse.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        activeWindowManager = wm
        usingAccessibilityWindow = accessibilityService != null
        uiState.update { it.copy(coversStatusBar = usingAccessibilityWindow) }
        if (!usingAccessibilityWindow) {
            Timber.w(
                "Cutout overlay is using TYPE_APPLICATION_OVERLAY: it sits under the status bar, " +
                    "so taps on the camera hole go to SystemUI. Enable the PixelPlayer Dynamic Island " +
                    "accessibility service to make it pressable."
            )
        }

        val composeView = ComposeView(contextToUse).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@OverlayCutoutService.lifecycleOwner)
            setViewTreeViewModelStoreOwner(this@OverlayCutoutService.lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(this@OverlayCutoutService.lifecycleOwner)

            setOnApplyWindowInsetsListener { _, insets ->
                // The window's own insets are relative to the window, not the screen, so re-read
                // the display's cutout instead of using insets.displayCutout.
                refreshDisplay()
                insets
            }

            // Taps on the closed pill are handled by the separate touch window (see
            // [setupTouchWindow]); this window only takes touches while the island is open.

            setContent {
                val state by uiState.collectAsState()
                if (state.isVisible) {
                    OverlayIslandTheme(scheme = state.colorScheme) {
                      IslandRotationFrame(degrees = IslandWindowPlacement.contentRotation(islandDisplay)) {
                        OverlayCutoutWidgetLayout(
                            state = state,
                            positionFlow = positionFlow.asStateFlow(),
                            audioLevels = audioReactor.levels,
                            geometry = currentCutoutGeometry,
                            display = islandDisplay,
                            onLevelChange = { level ->
                                uiState.update { it.copy(expansionLevel = level) }
                            },
                            onWindowSpec = { spec -> applyWindowSpec(spec) },
                            onOpenApp = {
                                openMainActivity()
                            },
                            onOpenFullScreenPlayer = {
                                openMainActivity(MainActivityIntentContract.ACTION_OPEN_PLAYER)
                            },
                            onOpenArtist = { artistId ->
                                openMainActivity(MainActivityIntentContract.ACTION_OPEN_ARTIST) {
                                    putExtra(MainActivityIntentContract.EXTRA_ARTIST_ID, artistId)
                                }
                            },
                            onOpenLyricsScreen = {
                                openMainActivity(MainActivityIntentContract.ACTION_OPEN_LYRICS)
                            },
                            onSeekTo = { timestamp ->
                                mediaController?.seekTo(timestamp)
                                positionFlow.value = timestamp
                            },
                            onPlayPauseToggle = {
                                if (mediaController?.isPlaying == true) {
                                    mediaController?.pause()
                                } else {
                                    mediaController?.play()
                                }
                            },
                            onSkipNext = {
                                mediaController?.seekToNext()
                            },
                            onSkipPrevious = {
                                mediaController?.seekToPrevious()
                            }
                        )
                      }
                    }
                }
            }
        }

        overlayComposeView = composeView
        windowSpec = OverlayWindowSpec.Collapsed
        val params = createLayoutParams(windowSpec)
        currentLayoutParams = params

        try {
            wm.addView(composeView, params)
        } catch (e: Exception) {
            Timber.e(e, "Error adding overlay view to WindowManager")
        }
        setupTouchWindow(contextToUse, wm)
    }

    /**
     * A small transparent window over the camera that takes taps on the closed pill.
     *
     * The island itself is drawn in a window that always starts at the left edge and spans the
     * full width; only its height changes. When one window had to move sideways (a strip on
     * the camera while closed, full width while open), Android showed the previous frame at
     * the new position for a frame on every open and close - the pill flicked to the left.
     * Keeping the drawing window's origin fixed removes that, and this window keeps the
     * closed island from blocking touches across the whole top of the screen.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchWindow(context: Context, wm: WindowManager) {
        val view = View(context)
        var downX = 0f
        var downY = 0f
        var downTime = 0L
        var handled = false
        val slop = 55f
        val openDrag = 28f * resources.displayMetrics.density
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downTime = System.currentTimeMillis()
                    handled = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    // Pull down from the camera to open, like dragging the island.
                    if (!handled && event.rawY - downY > openDrag) {
                        handled = true
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                        uiState.update { it.copy(expansionLevel = CutoutExpansionLevel.LEVEL_1_SINGLE) }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!handled) {
                        val duration = System.currentTimeMillis() - downTime
                        val dx = kotlin.math.abs(event.rawX - downX)
                        val dy = kotlin.math.abs(event.rawY - downY)
                        if (dx < slop && dy < slop) {
                            if (duration >= 450) {
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                openMainActivity()
                            } else {
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                                uiState.update { it.copy(expansionLevel = CutoutExpansionLevel.LEVEL_1_SINGLE) }
                            }
                        }
                    }
                    true
                }
                else -> true
            }
        }
        touchView = view
        try {
            wm.addView(view, createTouchParams())
        } catch (e: Exception) {
            Timber.e(e, "Error adding island touch window")
            touchView = null
        }
    }

    /** Touchable only while the island is shown and closed; otherwise touches pass through. */
    private fun updateTouchWindow() {
        val view = touchView ?: return
        val wm = activeWindowManager ?: return
        try {
            wm.updateViewLayout(view, createTouchParams())
        } catch (e: Exception) {
            Timber.w(e, "Error updating island touch window")
        }
    }

    private fun createTouchParams(): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val natural = IslandWindowPlacement.naturalRect(
            OverlayWindowSpec.Collapsed, currentCutoutGeometry, islandDisplay, density
        )
        val screen = IslandWindowPlacement.toScreen(natural, islandDisplay)
        val state = uiState.value
        val touchable = state.isVisible && state.expansionLevel == CutoutExpansionLevel.COLLAPSED &&
            windowSpec == OverlayWindowSpec.Collapsed
        return baseParams(screen, touchable)
    }

    /**
     * Resizes the window. The island animates inside a window that is already big enough
     * and only asks for the final (smaller) size once it has settled, so the window is never
     * relaid out per animation frame.
     */
    private fun applyWindowSpec(spec: OverlayWindowSpec, force: Boolean = false) {
        if (!force && spec == windowSpec) return
        windowSpec = spec
        val view = overlayComposeView ?: return
        val wm = activeWindowManager ?: return
        val newParams = createLayoutParams(spec)
        currentLayoutParams = newParams
        try {
            wm.updateViewLayout(view, newParams)
        } catch (e: Exception) {
            Timber.e(e, "Error updating overlay layout")
        }
        updateTouchWindow()
    }

    private fun createLayoutParams(spec: OverlayWindowSpec): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        // Exact pixel rect, worked out in the natural (portrait) frame and mapped onto the
        // screen. The drawing window always starts at the natural top-left corner and is full
        // width; only its height changes (see [setupTouchWindow] for why).
        val natural = IslandWindowPlacement.drawingRect(spec, currentCutoutGeometry, islandDisplay, density)
        val screen = IslandWindowPlacement.toScreen(natural, islandDisplay)
        // Closed, the full-width strip must not swallow taps along the top of the screen; the
        // touch window over the camera handles the pill instead.
        val touchable = uiState.value.isVisible && spec != OverlayWindowSpec.Collapsed
        return baseParams(screen, touchable)
    }

    private fun baseParams(screen: android.graphics.Rect, touchable: Boolean): WindowManager.LayoutParams {
        // Non-modal: touches outside the window pass through to the app underneath.
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val touchFlags = if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        // Must match the WindowManager the view was added through: the accessibility
        // overlay type is only valid on the accessibility service's own WindowManager,
        // so read the flag recorded at add time rather than the live service state.
        val windowType = if (usingAccessibilityWindow) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            screen.width(),
            screen.height(),
            windowType,
            flags or touchFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = screen.left
            y = screen.top
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // By default Android 11+ fits every window inside the system bars, which pushed
                // the island down below the status bar (time, battery) instead of over it. The
                // island is drawn at exact pixels from the very top, over the bars.
                fitInsetsTypes = 0
                fitInsetsSides = 0
                isFitInsetsIgnoringVisibility = true
            }
        }
    }

    private fun removeOverlayView() {
        touchView?.let { view ->
            try {
                activeWindowManager?.removeView(view)
            } catch (e: Exception) {
                Timber.w(e, "Error removing island touch window")
            }
        }
        touchView = null
        overlayComposeView?.let { view ->
            try {
                activeWindowManager?.removeView(view)
            } catch (e: Exception) {
                Timber.w(e, "Error removing overlay view")
            }
        }
        overlayComposeView = null
        activeWindowManager = null
        usingAccessibilityWindow = false
    }

    private fun openMainActivity(action: String? = null, extras: (Intent.() -> Unit)? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (action != null) this.action = action
            extras?.invoke(this)
        }
        startActivity(intent)
    }
}

/**
 * Lightweight custom LifecycleOwner, ViewModelStoreOwner, and SavedStateRegistryOwner
 * to power ComposeView inside an Android Service window without requiring an Activity.
 */
private class OverlayServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
