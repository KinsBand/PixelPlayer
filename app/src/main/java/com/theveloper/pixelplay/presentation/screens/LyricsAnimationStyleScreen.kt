package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.presentation.components.LyricsDisplayPrefKeys
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SyncedLyricsList
import com.theveloper.pixelplay.presentation.components.editLyricsDisplayPrefs
import com.theveloper.pixelplay.presentation.components.lyrics.LocalLyricAnimationStyle
import com.theveloper.pixelplay.presentation.components.lyrics.LocalLyricReducedMotion
import com.theveloper.pixelplay.presentation.components.lyrics.LyricsAnimationStyle
import com.theveloper.pixelplay.presentation.components.lyrics.LyricsPreviewSample
import com.theveloper.pixelplay.presentation.components.lyricsFontFamily
import com.theveloper.pixelplay.presentation.components.lyricsSheetColors
import com.theveloper.pixelplay.presentation.components.rememberLyricsDisplayPrefs
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Settings → Lyrics → Animation style: a pinned live Preview (the real lyric renderer) above
 * the ten presets. Picking one applies and saves it at once and keeps this screen open; it never
 * touches playback (song, position, queue and play state stay as they are).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsAnimationStyleScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by rememberLyricsDisplayPrefs()
    // Local selection so the radio moves on the same frame as the tap; the saved value follows.
    var selected by remember { mutableStateOf<LyricsAnimationStyle?>(null) }
    val current = selected ?: prefs.animationStyle
    LaunchedEffect(prefs.animationStyle) { if (selected == prefs.animationStyle) selected = null }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val stable by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val bottomPadding = bottomInset + if (stable.currentSong != null) MiniPlayerHeight + 12.dp else 16.dp

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Animation style", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBackClick, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { inner ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(inner)) {
            val landscape = maxWidth > maxHeight
            val boxMaxHeight = maxHeight
            val presetList: @Composable (Modifier) -> Unit = { mod ->
                LazyColumn(
                    modifier = mod.selectableGroup(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding)
                ) {
                    items(LyricsAnimationStyle.entries, key = { it.key }) { style ->
                        PresetRow(
                            style = style,
                            selected = style == current,
                            onSelect = {
                                selected = style
                                scope.launch {
                                    context.editLyricsDisplayPrefs { it[LyricsDisplayPrefKeys.ANIMATION_STYLE] = style.key }
                                }
                            }
                        )
                    }
                }
            }
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    LyricsAnimationPreview(
                        playerViewModel = playerViewModel,
                        style = current,
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, end = 8.dp, bottom = 16.dp)
                    )
                    presetList(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    // Pinned above the list, small enough not to dominate short screens.
                    LyricsAnimationPreview(
                        playerViewModel = playerViewModel,
                        style = current,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((boxMaxHeight * 0.3f).coerceIn(150.dp, 230.dp))
                            .padding(horizontal = 16.dp)
                    )
                    presetList(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PresetRow(style: LyricsAnimationStyle, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.large)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(style.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(style.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The Preview: the player's own [SyncedLyricsList] with [style]. Follows the playing song when it
 * has synced lyrics (it freezes when paused); otherwise plays a short silent sample on its own
 * clock, only while the screen is visible. Input is blocked, so it can't seek or scroll.
 */
@Composable
private fun LyricsAnimationPreview(
    playerViewModel: PlayerViewModel,
    style: LyricsAnimationStyle,
    modifier: Modifier = Modifier
) {
    val stable by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val slice by playerViewModel.fullPlayerSlice.collectAsStateWithLifecycle()
    val albumPair by playerViewModel.currentAlbumArtColorSchemePair.collectAsStateWithLifecycle()
    val prefs by rememberLyricsDisplayPrefs()
    val dark = LocalPixelPlayDarkTheme.current
    val baseScheme = MaterialTheme.colorScheme
    val scheme = remember(albumPair, dark, baseScheme) { albumPair?.let { if (dark) it.dark else it.light } ?: baseScheme }
    val colors = remember(scheme) { lyricsSheetColors(scheme) }
    val reduced = rememberSystemReducedMotion()

    val songLines = stable.lyrics?.synced?.takeIf { it.isNotEmpty() }
    val useSong = songLines != null

    // Silent sample clock, advanced only while this screen is RESUMED.
    val sampleClock = remember { MutableStateFlow(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(useSong, lifecycleOwner) {
        if (useSong) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = -1L
            while (true) {
                withFrameMillis { frame ->
                    if (last >= 0) {
                        sampleClock.value = (sampleClock.value + (frame - last)) % LyricsPreviewSample.LOOP_MS
                    }
                    last = frame
                }
            }
        }
    }

    val textStyle = MaterialTheme.typography.titleLarge.let { base ->
        val scale = prefs.textSize.multiplier
        base.copy(
            fontFamily = lyricsFontFamily(prefs.font),
            fontWeight = prefs.fontWeight.fontWeight,
            fontSize = base.fontSize * scale,
            lineHeight = base.lineHeight * scale * prefs.lineSpacing.multiplier
        )
    }

    Surface(
        color = colors.container,
        contentColor = colors.content,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
    ) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = useSong,
                transitionSpec = {
                    if (reduced) EnterTransition.None togetherWith ExitTransition.None
                    else fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                        fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
                },
                label = "lyricsPreviewSource"
            ) { song ->
                val lines: List<SyncedLine> = if (song) songLines ?: LyricsPreviewSample.lines else LyricsPreviewSample.lines
                val positionFlow: StateFlow<Long> = if (song) playerViewModel.currentPlaybackPosition else sampleClock
                val offset = if (song) slice.lyricsSyncOffset else 0
                val listState = rememberLazyListState()
                CompositionLocalProvider(
                    LocalLyricAnimationStyle provides style,
                    LocalLyricReducedMotion provides reduced,
                    LocalContentColor provides colors.content
                ) {
                    SyncedLyricsList(
                        lines = lines,
                        listState = listState,
                        playbackPositionFlow = positionFlow,
                        lyricsSyncOffset = offset,
                        accentColor = colors.lyricHighlight,
                        textStyle = textStyle,
                        onLineClick = {},
                        highlightZoneFraction = 0.08f,
                        highlightOffsetDp = 8.dp,
                        autoscrollAnimationSpec = tween(MotionTokens.DurationMedium4),
                        highlightMode = prefs.highlightMode,
                        lyricsAlignment = prefs.alignment.key,
                        showTranslation = false,
                        showRomanization = false,
                        contentPadding = PaddingValues(top = 36.dp, bottom = 48.dp, start = 12.dp, end = 12.dp),
                        onSeekTo = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clearAndSetSemantics { contentDescription = "Preview of ${style.title} lyric animation" }
                    )
                }
            }
            // Blocks taps and scrolls: the preview never seeks or moves the list by hand.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            )
            Text(
                "Preview",
                style = MaterialTheme.typography.labelMedium,
                color = colors.accentContent,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.accent)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
