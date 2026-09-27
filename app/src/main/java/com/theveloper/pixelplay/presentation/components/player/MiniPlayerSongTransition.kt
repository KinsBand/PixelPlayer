package com.theveloper.pixelplay.presentation.components.player

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.sin

/*
 * Mini player song-change "wave".
 *
 * When the song changes while the mini player is collapsed, one front sweeps across the card
 * from left to right. Behind the front everything belongs to the new song (card colour, cover,
 * title, artist, button colours); ahead of it, the old song. Every element derives its own
 * local progress from the single front position, so the card moves as one motion.
 *
 * Design choices (see claude/mini-player-song-wave-transition-spec-2026-09-26.md):
 *  - Always physical left→right, for next and previous, LTR and RTL.
 *  - Colour sweeps; content only crossfades with a small nudge, so it never contradicts the
 *    full-player carousel (where the next song arrives from the right).
 *  - Waits up to [MiniWaveColorWaitMs] for the new palette; if it lands later, the swept area
 *    blends to it over [MiniWaveLateBlendMs] (or a colour-only sweep runs if the wave is over).
 *  - Rapid skips stack: a new wave starts from the left while the previous one keeps going,
 *    so nothing on screen jumps. At most [MiniWaveMaxConcurrent] run at once.
 *  - Everything per-frame is read in draw / layer lambdas: 0 recompositions per frame.
 */

internal const val MiniWaveDurationMs = 450
internal const val MiniWaveColorWaitMs = 150L
internal const val MiniWaveLateBlendMs = 150
internal const val MiniWaveMaxConcurrent = 2
internal val MiniWaveFeather = 24.dp
internal val MiniWaveContentNudge = 12.dp
internal const val MiniWaveCoverMinScale = 0.9f
internal const val MiniWaveButtonDip = 0.08f

/** Material 3 "emphasized decelerate". */
internal val MiniWaveEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** The only colours the mini player draws with. Lerping 4 colours per frame, not 36. */
@Immutable
internal data class MiniPalette(
    val container: Color,
    val onContainer: Color,
    val primary: Color,
    val onPrimary: Color
) {
    companion object {
        fun from(scheme: ColorScheme) = MiniPalette(
            container = scheme.primaryContainer,
            onContainer = scheme.onPrimaryContainer,
            primary = scheme.primary,
            onPrimary = scheme.onPrimary
        )
    }
}

internal fun lerpMiniPalette(a: MiniPalette, b: MiniPalette, t: Float): MiniPalette = when {
    t <= 0f -> a
    t >= 1f -> b
    else -> MiniPalette(
        container = lerp(a.container, b.container, t),
        onContainer = lerp(a.onContainer, b.onContainer, t),
        primary = lerp(a.primary, b.primary, t),
        onPrimary = lerp(a.onPrimary, b.onPrimary, t)
    )
}

/** One sweep. [changesContent] = false for a colour-only sweep (palette arrived late). */
@Stable
internal class MiniWave(
    val id: Long,
    song: Song,
    val changesContent: Boolean,
    palette: MiniPalette
) {
    var song by mutableStateOf(song)
    val progress = Animatable(0f)
    var palette by mutableStateOf(palette)
        private set
    private var lateFrom by mutableStateOf(palette)
    private val lateBlend = Animatable(1f)
    internal var job: Job? = null

    /** Palette to draw with right now (includes a late-palette blend). Draw-phase safe. */
    fun currentPalette(): MiniPalette {
        val t = lateBlend.value
        return if (t >= 1f) palette else lerpMiniPalette(lateFrom, palette, t)
    }

    /** The real palette for this song arrived mid-sweep: blend the swept area to it. */
    fun retint(scope: CoroutineScope, newPalette: MiniPalette) {
        if (newPalette == palette) return
        lateFrom = currentPalette()
        palette = newPalette
        scope.launch {
            lateBlend.snapTo(0f)
            lateBlend.animateTo(1f, tween(MiniWaveLateBlendMs))
        }
    }
}

/** Where each mini player element sits, in card-local px. Written at placement, read at draw. */
internal class MiniWaveGeometry {
    var rowRootX = 0f
    var rowWidth = 0f
    private val lefts = FloatArray(SLOT_COUNT)
    private val widths = FloatArray(SLOT_COUNT)

    /** Stores root-space x; [left] converts at read time so placement callback order doesn't matter. */
    fun set(slot: Int, rootX: Float, width: Float) {
        lefts[slot] = rootX
        widths[slot] = width
    }

    fun left(slot: Int) = lefts[slot] - rowRootX
    fun width(slot: Int) = widths[slot]

    companion object {
        const val SLOT_COVER = 0
        const val SLOT_TEXT = 1
        const val SLOT_PREV = 2
        const val SLOT_PLAY = 3
        const val SLOT_NEXT = 4
        const val SLOT_COUNT = 5
    }
}

@Stable
internal class MiniSongWaveState(private val featherPx: Float) {
    /** Setting on/off. When off the mini player uses the legacy (uniform fade) path. */
    var enabled by mutableStateOf(false)
        internal set

    /** Song / palette fully on screen once every running wave has finished. */
    var baseSong by mutableStateOf<Song?>(null)
        private set
    var basePalette by mutableStateOf<MiniPalette?>(null)
        private set
    /** Composition key of the base content layer; inherits the committing wave's id so the
     *  incoming layer's composables (and marquee state) carry over without a pop. */
    var baseKey by mutableLongStateOf(0L)
        private set

    val waves = mutableStateListOf<MiniWave>()
    val geometry = MiniWaveGeometry()
    private var nextId = 1L

    val isActive: Boolean get() = waves.isNotEmpty()

    /** The song the mini player is showing / heading to. */
    val displayedSong: Song?
        get() = waves.lastOrNull { it.changesContent }?.song ?: baseSong

    // ── mutations ────────────────────────────────────────────────────────────────────────

    fun snapTo(song: Song?, palette: MiniPalette) {
        waves.forEach { it.job?.cancel() }
        waves.clear()
        if (song?.id != baseSong?.id) baseKey = nextId++
        baseSong = song
        basePalette = palette
    }

    fun updateBasePalette(palette: MiniPalette) {
        basePalette = palette
    }

    fun replaceDisplayedSong(song: Song) {
        val top = waves.lastOrNull { it.changesContent }
        if (top != null) top.song = song else baseSong = song
    }

    /** Finish every running wave instantly (sheet started moving, etc.). */
    fun finishAll() {
        while (waves.isNotEmpty()) commit(waves.removeAt(0).also { it.job?.cancel() })
    }

    fun startWave(scope: CoroutineScope, song: Song, palette: MiniPalette, changesContent: Boolean) {
        while (waves.size >= MiniWaveMaxConcurrent) {
            commit(waves.removeAt(0).also { it.job?.cancel() })
        }
        val wave = MiniWave(nextId++, song, changesContent, palette)
        waves.add(wave)
        wave.job = scope.launch {
            wave.progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(MiniWaveDurationMs, easing = MiniWaveEasing)
            )
            // Waves finish in start order; commit this one and anything older still listed.
            val idx = waves.indexOf(wave)
            if (idx >= 0) repeat(idx + 1) {
                val done = waves.removeAt(0)
                if (done !== wave) done.job?.cancel()
                commit(done)
            }
        }
    }

    private fun commit(wave: MiniWave) {
        basePalette = wave.palette
        if (wave.changesContent) {
            baseSong = wave.song
            baseKey = wave.id
        }
    }

    // ── draw-phase maths (no state writes, safe inside graphicsLayer / drawBehind) ─────────

    private fun front(progress: Float, width: Float) = progress * (width + featherPx)

    /** 0..1 progress of [wave] over an element spanning [left, left+width]. */
    fun local(wave: MiniWave, left: Float, width: Float): Float {
        val span = (width + featherPx).coerceAtLeast(1f)
        return ((front(wave.progress.value, geometry.rowWidth) - left) / span).coerceIn(0f, 1f)
    }

    fun local(wave: MiniWave, slot: Int) = local(wave, geometry.left(slot), geometry.width(slot))

    /** Colours of an element at [slot], folding every running wave over the base palette. */
    fun paletteAt(slot: Int): MiniPalette? {
        var p = basePalette ?: return null
        for (w in waves) p = lerpMiniPalette(p, w.currentPalette(), local(w, slot))
        return p
    }

    /** Button "dip": largest bump among the waves passing the button. */
    fun dipAt(slot: Int): Float {
        var d = 0f
        for (w in waves) {
            if (!w.changesContent) continue
            val s = sin(PI.toFloat() * local(w, slot))
            if (s > d) d = s
        }
        return d
    }

    /** How far the content layer [key] has come in (1 for the base layer). */
    fun layerIn(key: Long, slot: Int): Float {
        if (key == baseKey) return 1f
        val w = waves.firstOrNull { it.id == key } ?: return 0f
        return local(w, slot)
    }

    /** How far later content waves have pushed layer [key] out (0..1). */
    fun layerOut(key: Long, slot: Int): Float {
        var remain = 1f
        var after = key == baseKey
        for (w in waves) {
            if (!w.changesContent) continue
            if (after) remain *= 1f - local(w, slot)
            if (w.id == key) after = true
        }
        return 1f - remain
    }

    fun layerVisibility(key: Long, slot: Int): Float =
        layerIn(key, slot) * (1f - layerOut(key, slot))

    /** Content layers to compose, bottom to top: base, then each content wave. */
    fun contentLayers(): List<Pair<Long, Song>> {
        val out = ArrayList<Pair<Long, Song>>(1 + waves.size)
        baseSong?.let { out += baseKey to it }
        waves.forEach { if (it.changesContent) out += it.id to it.song }
        return out
    }

    /** Palette a content layer's text is drawn with (constant for the layer → no per-frame recomposition). */
    fun layerPalette(key: Long): MiniPalette? =
        if (key == baseKey) basePalette else waves.firstOrNull { it.id == key }?.palette ?: basePalette

    // ── background ────────────────────────────────────────────────────────────────────────

    internal fun drawBackground(scope: DrawScope, shape: Shape, fallback: Color) {
        with(scope) {
            val outline = shape.createOutline(size, layoutDirection, this)
            val base = basePalette
            if (!enabled || base == null || waves.isEmpty()) {
                drawOutline(outline, fallback)
                return
            }
            drawOutline(outline, base.container)
            for (w in waves) {
                val to = w.currentPalette().container
                val f = front(w.progress.value, size.width)
                if (f <= 0f) continue
                if (f - featherPx >= size.width) {
                    drawOutline(outline, to)
                } else {
                    drawOutline(
                        outline,
                        Brush.horizontalGradient(
                            colors = listOf(to, to.copy(alpha = 0f)),
                            startX = f - featherPx,
                            endX = f
                        )
                    )
                }
            }
        }
    }
}

/** Replaces `.background(color, shape)` on the player card. Falls back to [fallback] when no wave runs. */
internal fun Modifier.miniSongWaveBackground(
    state: MiniSongWaveState,
    shape: Shape,
    fallback: () -> Color
): Modifier = drawBehind { state.drawBackground(this, shape, fallback()) }

/** Animator duration scale 0 = the user turned animations off. */
private fun animationsDisabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

/**
 * Drives the wave. [canAnimateLayout] must only read snapshot state (it is observed): true
 * while the sheet is collapsed, idle, not dragged and not being swiped away.
 */
@Composable
internal fun rememberMiniSongWaveState(
    song: Song?,
    targetScheme: ColorScheme,
    targetReady: Boolean,
    enabled: Boolean,
    canAnimateLayout: () -> Boolean
): MiniSongWaveState {
    val featherPx = with(LocalDensity.current) { MiniWaveFeather.toPx() }
    val state = remember(featherPx) { MiniSongWaveState(featherPx) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val targetPalette = remember(targetScheme) { MiniPalette.from(targetScheme) }
    val latestPalette by rememberUpdatedState(targetPalette)
    val latestReady = rememberUpdatedState(targetReady)
    val latestCanAnimateLayout by rememberUpdatedState(canAnimateLayout)

    state.enabled = enabled
    // Seed synchronously so the very first frame already renders through the wave row
    // (no one-frame legacy row → wave row swap on first appearance).
    if (song != null && state.baseSong == null && !state.isActive) {
        state.snapTo(song, targetPalette)
    }

    fun canAnimateNow(): Boolean =
        latestCanAnimateLayout() &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            !animationsDisabled(context)

    // Song changes → content wave (or snap).
    LaunchedEffect(song?.id, enabled) {
        val s = song
        val shownId = state.displayedSong?.id
        when {
            s == null -> state.snapTo(null, latestPalette)
            !enabled || shownId == null -> state.snapTo(s, latestPalette)      // off / first appearance
            shownId == s.id -> state.replaceDisplayedSong(s)
            !canAnimateNow() -> state.snapTo(s, latestPalette)                 // expanded, dragging, backgrounded…
            else -> {
                // Give the palette a moment so colour and content move together. Cancelled
                // (and so coalesced) if another skip lands inside the window.
                withTimeoutOrNull(MiniWaveColorWaitMs) {
                    snapshotFlow { latestReady.value }.filter { it }.first()
                }
                if (canAnimateNow()) {
                    state.startWave(scope, s, latestPalette, changesContent = true)
                } else {
                    state.snapTo(s, latestPalette)
                }
            }
        }
    }

    // Same song, new metadata (online enrichment, stream titles) → just update the text.
    LaunchedEffect(song) {
        val s = song ?: return@LaunchedEffect
        if (s.id == state.displayedSong?.id) state.replaceDisplayedSong(s)
    }

    // Palette changes for the song on screen (late extraction, theme / palette-style change).
    LaunchedEffect(targetPalette, enabled) {
        if (!enabled) {
            state.updateBasePalette(targetPalette)
            return@LaunchedEffect
        }
        val songId = song?.id ?: return@LaunchedEffect
        val top = state.waves.lastOrNull()
        if (top != null) {
            if (top.song.id == songId) top.retint(scope, targetPalette)
            return@LaunchedEffect   // a different song is pending: its wave picks the palette up
        }
        val base = state.baseSong ?: return@LaunchedEffect
        if (base.id != songId || state.basePalette == targetPalette) return@LaunchedEffect
        if (canAnimateNow()) {
            state.startWave(scope, base, targetPalette, changesContent = false)
        } else {
            state.updateBasePalette(targetPalette)
        }
    }

    // Sheet starts moving / gets dragged / swiped mid-wave → finish instantly.
    LaunchedEffect(state) {
        snapshotFlow { latestCanAnimateLayout() }
            .distinctUntilChanged()
            .collect { ok -> if (!ok && state.isActive) state.finishAll() }
    }

    return state
}
