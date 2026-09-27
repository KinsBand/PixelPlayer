package com.theveloper.pixelplay.presentation.components

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.theveloper.pixelplay.presentation.components.tabs.AndroidScorePainter
import com.theveloper.pixelplay.presentation.components.tabs.CursorGlide
import com.theveloper.pixelplay.presentation.components.tabs.DrumMarks
import com.theveloper.pixelplay.presentation.components.tabs.DrumSession
import com.theveloper.pixelplay.presentation.components.tabs.drumMarks
import com.theveloper.pixelplay.presentation.components.tabs.sweepX
import com.theveloper.pixelplay.presentation.components.tabs.LiveCursor
import com.theveloper.pixelplay.presentation.components.tabs.ScoreColors
import com.theveloper.pixelplay.presentation.components.tabs.ScoreLayoutEngine
import com.theveloper.pixelplay.presentation.components.tabs.ScoreMetrics
import com.theveloper.pixelplay.presentation.components.tabs.ScoreRenderer
import com.theveloper.pixelplay.presentation.components.tabs.ScoreSystem
import com.theveloper.pixelplay.presentation.components.tabs.TabPracticeController
import com.theveloper.pixelplay.presentation.components.tabs.TabUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Creates the tab controller for the current song and keeps it loaded and ticking.
 * Hoist it (LyricsSheet does) so the practice panel and the options menu share it.
 */
@Composable
fun rememberTabPractice(
    title: String,
    artist: String,
    songId: String?,
    /** The song's own file (enables gapless loops of the original audio). */
    songUri: String? = null,
): TabPracticeController {
    val context = LocalContext.current
    val controller = remember { TabPracticeController(context) }
    DisposableEffect(controller) {
        controller.attach()
        onDispose { controller.detach() }
    }
    LaunchedEffect(controller, title, artist, songId, songUri) { controller.setSong(title, artist, songId, songUri) }
    LaunchedEffect(controller, controller.title, controller.artist, controller.requestedTrack, controller.reloadKey) {
        controller.load()
    }
    LaunchedEffect(controller) {
        while (true) {
            withFrameMillis { }
            controller.tick()
        }
    }
    return controller
}

private val CURSOR_GREEN = 0xFF34C759.toInt()

/** The Instruments page: any part of the song (guitar, bass, drums…) as notation. */
@Composable
fun InstrumentsPerformanceView(
    controller: TabPracticeController,
    onBackgroundColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onUserInteraction: () -> Boolean = { false },
) {
    TabScoreView(controller, onBackgroundColor, accentColor, modifier, onUserInteraction)
}

/** The whole part as notation, wrapped to the screen (or the uploaded PDF). */
@Composable
fun TabScoreView(
    controller: TabPracticeController,
    onBackgroundColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    /**
     * Called on every touch of the music. Return true to swallow a tap (used to bring the
     * immersive-hidden controls back instead of jumping to a bar).
     */
    onUserInteraction: () -> Boolean = { false },
) {
    // No default instrument yet: the instruments sheet opens by itself over this. Once it's
    // closed without a pick, this button brings it back.
    if (controller.awaitingPick && controller.state is TabUiState.Loading) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🎸", fontSize = 40.sp)
                Text(
                    "Pick an instrument to see its tab.",
                    color = onBackgroundColor.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center,
                )
                TextButton(onClick = controller::openPicker) {
                    Text("Choose instrument", color = accentColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        return
    }
    val pdf = controller.pdfUri
    if (pdf != null) {
        Box(modifier = modifier.fillMaxSize().padding(top = 110.dp)) {
            PdfViewerComponent(pdfUri = pdf, onBackgroundColor = onBackgroundColor)
        }
        return
    }
    when (val state = controller.state) {
        TabUiState.Loading -> TabSkeleton(onBackgroundColor, modifier)
        is TabUiState.Failed -> Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🎸", fontSize = 40.sp)
                Text(state.message, color = onBackgroundColor.copy(alpha = 0.6f), fontSize = 13.sp, textAlign = TextAlign.Center)
                TextButton(onClick = controller::retry) {
                    Text("Retry", color = accentColor, fontWeight = FontWeight.SemiBold)
                }
                if (controller.source != null) {
                    TextButton(onClick = controller::resetSource) {
                        Text("Back to the usual tab", color = accentColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        is TabUiState.Ready -> TabScore(controller, state, onBackgroundColor, accentColor, modifier, onUserInteraction)
    }
}

@Composable
private fun TabScore(
    controller: TabPracticeController,
    ready: TabUiState.Ready,
    onBg: Color,
    accent: Color,
    modifier: Modifier,
    onUserInteraction: () -> Boolean,
) {
    val density = LocalDensity.current
    val metrics = remember(density.density, density.fontScale) { ScoreMetrics(density.density, density.density * density.fontScale) }
    val colors = remember(onBg, accent) {
        ScoreColors(
            ink = onBg.copy(alpha = 0.92f).toArgb(),
            faint = onBg.copy(alpha = 0.5f).toArgb(),
            accent = accent.toArgb(),
            cursor = CURSOR_GREEN,
            loop = (CURSOR_GREEN and 0x00FFFFFF) or 0x2E000000,
        )
    }
    val painter = remember { AndroidScorePainter() }
    val renderer = remember(ready.track, metrics, colors) { ScoreRenderer(ready.track, metrics, colors) }
    val listState = rememberLazyListState()
    val drums = controller.drums
    val marks = remember(drums, colors) { drums.drumMarks(colors) }
    val glide = remember(density.density) { CursorGlide(density.density) }
    /** The cursor(s) to draw this frame: one, or two while it moves to another line. */
    var cursorMarks by remember { mutableStateOf<List<CursorGlide.Mark>>(emptyList()) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val expanded = controller.expandedSections
        val layout = remember(ready, widthPx, metrics, expanded) {
            ScoreLayoutEngine.layout(ready.track, ready.sections, widthPx, metrics, painter, expanded)
        }

        // Follow the cursor, unless the user scrolled in the last few seconds.
        var lastUserScroll by remember { mutableStateOf(0L) }
        var autoScrolling by remember { mutableStateOf(false) }
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                if (scrolling && !autoScrolling) {
                    lastUserScroll = SystemClock.uptimeMillis()
                    onUserInteraction()
                }
            }
        }
        // Starts a beat before the line ends (follows where the music will be), with a soft spring.
        LaunchedEffect(layout) {
            val topPx = with(density) { 150.dp.toPx() }
            snapshotFlow { (controller.lookahead ?: controller.cursor)?.measure?.let { layout.placement[it]?.first } }
                .distinctUntilChanged()
                .collect { sys ->
                    if (sys == null || !controller.isPlaying) return@collect
                    if (SystemClock.uptimeMillis() - lastUserScroll < 3500) return@collect
                    autoScrolling = true
                    runCatching {
                        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == sys }
                        if (item != null) {
                            listState.animateScrollBy(
                                item.offset - topPx,
                                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                            )
                        } else {
                            listState.animateScrollToItem(sys, with(density) { -150.dp.roundToPx() })
                        }
                    }
                    autoScrolling = false
                }
        }

        // The cursor: a smooth sweep through each bar (crossing into the next bar on the same
        // line), gliding to the next line or back to a loop's start.
        LaunchedEffect(layout, ready) {
            val tl = ready.timeline
            while (true) {
                withFrameMillis { now ->
                    val pos = controller.cursor
                    val place = pos?.let { layout.placement[it.measure] }
                    val sys = place?.let { layout.systems.getOrNull(it.first) }
                    val pm = if (place != null && sys != null) sys.measures.getOrNull(place.second) else null
                    cursorMarks = if (pos == null || place == null || sys == null || pm == null) {
                        glide.update(null, 0f, now, controller.seekSerial, false)
                    } else {
                        val len = tl.track.measures[pos.measure].lengthTicks.toDouble()
                        val nextMeasure = controller.nextEntry(pos.entry)?.let { tl.entries.getOrNull(it)?.measure }
                        val nextPlace = nextMeasure?.let { layout.placement[it] }
                        val endX = if (
                            nextPlace != null && nextPlace.first == place.first && nextPlace.second == place.second + 1 &&
                            pm.realMeasures.none { it == nextMeasure }
                        ) {
                            sys.measures[nextPlace.second].let { n -> n.slots.firstOrNull()?.x ?: n.x }
                        } else null
                        val x = pm.sweepX(pos.fraction * len, pos.measure, endX)
                        glide.update(place.first, x, now, controller.seekSerial, controller.isPlaying)
                    }
                }
            }
        }

        // A "same as" section plays: open it out so the cursor stays where the music is.
        LaunchedEffect(layout) {
            snapshotFlow { controller.cursor?.measure }
                .distinctUntilChanged()
                .collect { real ->
                    if (real == null || !controller.isPlaying) return@collect
                    val sec = ready.sections.firstOrNull { real >= it.startMeasure && real < it.endMeasure } ?: return@collect
                    if (sec.sameAs != null && sec.index !in controller.expandedSections) {
                        controller.expandedSections = controller.expandedSections + sec.index
                    }
                }
        }

        // ── Hold and drag across bars to set a loop ──
        val haptics = LocalHapticFeedback.current
        val edgePx = with(density) { 150.dp.toPx() }
        val viewportH = with(density) { maxHeight.toPx() }
        var dragAnchor by remember { mutableStateOf<Int?>(null) }
        var dragItem by remember { mutableStateOf(-1) }
        var dragLocal by remember { mutableStateOf(Offset.Zero) }
        var edgeDir by remember { mutableStateOf(0) }
        var lastDragEnd by remember { mutableStateOf(0L) }

        /** Real bar under a point given relative to list item [itemIndex]. */
        fun barAt(itemIndex: Int, local: Offset): Int? {
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            val me = items.firstOrNull { it.index == itemIndex } ?: return null
            val y = me.offset + local.y
            val target = items.firstOrNull { y >= it.offset && y < it.offset + it.size }
                ?: if (y < (items.firstOrNull()?.offset ?: 0)) items.firstOrNull() else items.lastOrNull()
            val sys = target?.let { layout.systems.getOrNull(it.index) } ?: return null
            if (sys.isCollapsed || sys.measures.isEmpty()) return null
            val pm = sys.measures.firstOrNull { local.x >= it.x && local.x < it.right }
                ?: if (local.x < sys.left) sys.measures.first() else sys.measures.last()
            return pm.realMeasures.first()
        }

        fun dragUpdate() {
            val anchor = dragAnchor ?: return
            val bar = barAt(dragItem, dragLocal) ?: return
            controller.setLoop(anchor, bar, commit = false)
            val me = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == dragItem }
            val y = (me?.offset ?: 0) - listState.layoutInfo.viewportStartOffset + dragLocal.y
            edgeDir = when {
                y < edgePx -> -1
                y > viewportH - edgePx -> 1
                else -> 0
            }
        }

        fun finishDrag() {
            val a = dragAnchor
            if (a != null) {
                val b = barAt(dragItem, dragLocal) ?: controller.loopTo ?: a
                controller.setLoop(a, b, commit = true)
            }
            dragAnchor = null
            edgeDir = 0
            lastDragEnd = SystemClock.uptimeMillis()
        }

        LaunchedEffect(edgeDir) {
            while (edgeDir != 0 && dragAnchor != null) {
                runCatching { listState.scrollBy(edgeDir * 14f) }
                // The line the drag started on scrolled away (its gesture ends with it): keep the loop.
                if (listState.layoutInfo.visibleItemsInfo.none { it.index == dragItem }) {
                    finishDrag()
                    break
                }
                dragUpdate()
                delay(16)
            }
        }

        val loopDrag = remember(layout) {
            LoopDragHandler(
                onStart = { item, local ->
                    val bar = barAt(item, local)
                    if (bar != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragAnchor = bar
                        dragItem = item
                        dragLocal = local
                        controller.setLoop(bar, bar, commit = false)
                    }
                },
                onMove = { local ->
                    dragLocal = local
                    dragUpdate()
                },
                onEnd = { finishDrag() },
                recentlyDragged = { SystemClock.uptimeMillis() - lastDragEnd < 350 },
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 110.dp, bottom = 160.dp),
        ) {
            items(layout.systems, key = { "s${it.index}-${it.sectionIndex}" }) { s ->
                if (s.isCollapsed) {
                    CollapsedSection(s, onBg, accent) {
                        controller.expandedSections = controller.expandedSections + s.sectionIndex
                    }
                } else {
                    SystemCanvas(s, controller, renderer, painter, onUserInteraction, loopDrag, { cursorMarks }, marks)
                }
            }
        }

        DrumOverlays(
            drums = drums,
            onApplyTiming = controller::applyTimingSuggestion,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = if (controller.loopSelecting) 156.dp else 116.dp),
        )

        if (controller.loopSelecting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 116.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(CURSOR_GREEN))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    if (controller.loopFrom == null) "Tap the first bar — or hold and drag across bars" else "Now tap the last bar",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SystemCanvas(
    s: ScoreSystem,
    controller: TabPracticeController,
    renderer: ScoreRenderer,
    painter: AndroidScorePainter,
    onUserInteraction: () -> Boolean,
    loopDrag: LoopDragHandler,
    cursorMarks: () -> List<CursorGlide.Mark>,
    drumMarks: DrumMarks,
) {
    val density = LocalDensity.current
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { s.height.toDp() })
            .pointerInput(s, loopDrag) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        onUserInteraction()
                        loopDrag.onStart(s.index, offset)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        loopDrag.onMove(change.position)
                    },
                    onDragEnd = { loopDrag.onEnd() },
                    onDragCancel = { loopDrag.onEnd() },
                )
            }
            .pointerInput(s) {
                detectTapGestures { offset ->
                    if (loopDrag.recentlyDragged()) return@detectTapGestures
                    if (onUserInteraction()) return@detectTapGestures
                    val pm = s.measures.firstOrNull { offset.x >= it.x && offset.x < it.right } ?: return@detectTapGestures
                    val cur = controller.cursor?.measure
                    val real = pm.realMeasures.firstOrNull { it == cur } ?: pm.realMeasures.first()
                    controller.onMeasureTapped(real)
                }
            },
    ) {
        drawIntoCanvas { canvas ->
            painter.canvas = canvas.nativeCanvas
            val from = controller.loopFrom
            val to = controller.loopTo ?: if (controller.loopSelecting) from else null
            if (from != null && to != null) renderer.drawLoop(painter, s, minOf(from, to), maxOf(from, to))
            val pos = controller.cursor
            val live = if (pos != null && controller.isPlaying) {
                LiveCursor(pos.measure) { closing -> controller.tabRepeatPass(pos.entry, closing) }
            } else null
            val drums = controller.drums
            // Reading the version redraws this line when a hit lands.
            val showMarks = drums.version >= 0 && drums.hasScore
            renderer.draw(painter, s, live, if (showMarks) drumMarks else null)
            for (m in cursorMarks()) {
                if (m.system == s.index) renderer.drawCursor(painter, s, m.x, m.alpha)
            }
            painter.canvas = null
        }
    }
}

/**
 * Drum-kit messages over the tab: the timing suggestion, the summary after a run or loop pass,
 * and a note when a pad was learned.
 */
@Composable
private fun DrumOverlays(drums: DrumSession, onApplyTiming: () -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val suggestion = drums.suggestionMs
        AnimatedVisibility(suggestion != null, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
            val ms = suggestion ?: 0
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xF0202124))
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tab seems ${kotlin.math.abs(ms)} ms ${if (ms > 0) "early" else "late"}",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(99.dp)).background(Color(CURSOR_GREEN)).clickable(onClick = onApplyTiming)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) { Text("Fix", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.clip(RoundedCornerShape(99.dp)).clickable { drums.dismissSuggestion() }.padding(horizontal = 10.dp, vertical = 7.dp),
                ) { Text("✕", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp) }
            }
        }

        val summary = drums.summary
        LaunchedEffect(summary) {
            if (summary != null) {
                delay(if (summary.loopPass) 6000L else 12000L)
                if (drums.summary === summary) drums.summary = null
            }
        }
        AnimatedVisibility(summary != null, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
            summary?.let { DrumSummaryCard(it) { drums.summary = null } }
        }

        val learned = drums.learnedToast
        LaunchedEffect(learned) {
            if (learned != null) {
                delay(2500)
                if (drums.learnedToast == learned) drums.learnedToast = null
            }
        }
        AnimatedVisibility(learned != null, enter = fadeIn(), exit = fadeOut()) {
            learned?.let { (note, piece) ->
                Box(
                    Modifier.clip(RoundedCornerShape(99.dp)).background(Color(0xE0202124)).padding(horizontal = 14.dp, vertical = 7.dp),
                ) { Text("Learned: pad $note → ${piece.label}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun DrumSummaryCard(summary: DrumSession.Summary, onDismiss: () -> Unit) {
    val st = summary.stats
    val pct = (st.accuracy * 100).toInt()
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xF0202124))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            (if (summary.loopPass) "Loop pass · " else "Run · ") + "$pct%",
            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("● ${st.good}", color = Color(0xFF34C759), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("● ${st.amber}", color = Color(0xFFFFB020), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("● ${st.missed}", color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (st.extra > 0) Text("+${st.extra} extra", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        }
        val mean = st.meanErrorMs
        Text(
            buildString {
                append(
                    when {
                        kotlin.math.abs(mean) < 5 -> "Right on time"
                        mean > 0 -> "On average ${mean.toInt()} ms late"
                        else -> "On average ${(-mean).toInt()} ms early"
                    },
                )
                st.worstPiece?.let { (piece, rate) -> append(" · Weakest: ${piece.label} ${(rate * 100).toInt()}%") }
            },
            color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center,
        )
    }
}

/** Hold-and-drag loop selection, shared by every line of music (a drag can cross lines). */
internal class LoopDragHandler(
    val onStart: (item: Int, local: Offset) -> Unit,
    val onMove: (local: Offset) -> Unit,
    val onEnd: () -> Unit,
    val recentlyDragged: () -> Boolean,
)

@Composable
private fun CollapsedSection(s: ScoreSystem, onBg: Color, accent: Color, onExpand: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(onBg.copy(alpha = 0.06f))
            .clickable(onClick = onExpand)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(s.sectionLabel.orEmpty(), color = onBg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            s.collapsedText.orEmpty(),
            color = onBg.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("Show", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

internal fun tuningName(tuning: List<Int>): String? {
    if (tuning.isEmpty()) return null
    if (tuning == listOf(64, 59, 55, 50, 45, 40) || tuning == listOf(43, 38, 33, 28)) return "Standard tuning"
    return tuning.reversed().joinToString(" ") { NOTE_NAMES[((it % 12) + 12) % 12] }
}

@Composable
private fun TabSkeleton(onBg: Color, modifier: Modifier) {
    val pulse = rememberInfiniteTransition(label = "tab_skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Column(
        modifier = modifier.fillMaxSize().padding(top = 120.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(onBg.copy(alpha = alpha)),
            )
        }
    }
}
