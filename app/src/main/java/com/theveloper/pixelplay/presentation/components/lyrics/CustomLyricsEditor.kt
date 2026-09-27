package com.theveloper.pixelplay.presentation.components.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theveloper.pixelplay.data.model.Lyrics
import kotlinx.coroutines.flow.StateFlow

private enum class EditorStep { WRITE, TIME }

/** A tap lands a little after the singer starts; stamps are moved this much earlier. */
private const val TAP_REACTION_MS = 120L

private val LRC_TAG = Regex("""^\s*\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)]\s*(.*)$""")

/**
 * Write lyrics by hand and time them, for songs where no lyrics were found (or to fix a bad
 * set). Step 1: type or paste the text (LRC lines keep their times). Step 2: play the song and
 * tap "Stamp line" as each line starts. Saves LRC when any line is timed, else plain text.
 */
@Composable
fun CustomLyricsEditor(
    initialLyrics: Lyrics?,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffsetMs: Int,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color,
    onAccentColor: Color,
    containerColor: Color,
    contentColor: Color,
) {
    var step by remember { mutableStateOf(EditorStep.WRITE) }
    var text by remember { mutableStateOf(initialText(initialLyrics)) }
    val lines = remember { mutableStateListOf<String>() }
    val times = remember { mutableStateListOf<Long?>() }
    var cursor by remember { mutableIntStateOf(0) }

    fun startTiming() {
        lines.clear()
        times.clear()
        text.lines().forEach { raw ->
            val m = LRC_TAG.find(raw)
            val (time, body) = if (m != null) {
                parseTag(m.groupValues[1], m.groupValues[2]) to m.groupValues[3]
            } else {
                null to raw
            }
            if (body.isBlank()) return@forEach
            lines += body.trim()
            times += time
        }
        cursor = times.indexOfFirst { it == null }.let { if (it < 0) 0 else it }
        step = EditorStep.TIME
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = containerColor,
            contentColor = contentColor,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header: close/back · title · save
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (step == EditorStep.TIME) step = EditorStep.WRITE else onDismiss() }) {
                        Icon(
                            if (step == EditorStep.TIME) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close,
                            contentDescription = if (step == EditorStep.TIME) "Back to text" else "Close",
                        )
                    }
                    Text(
                        if (step == EditorStep.WRITE) "Your lyrics" else "Time the lines",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = text.isNotBlank(),
                        onClick = {
                            val out = if (step == EditorStep.TIME) buildLrc(lines, times) else text.trim()
                            onSave(out)
                        },
                    ) { Text("Save", color = accentColor, fontWeight = FontWeight.Bold) }
                }

                when (step) {
                    EditorStep.WRITE -> {
                        Text(
                            "Type or paste the lyrics, one line per row. LRC lines like [01:23.45] keep their timing.",
                            color = contentColor.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                        )
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            placeholder = { Text("First line\nSecond line\n…") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                cursorColor = accentColor,
                                focusedTextColor = contentColor,
                                unfocusedTextColor = contentColor,
                            ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(
                                label = "Save without timing",
                                bg = contentColor.copy(alpha = 0.08f),
                                fg = contentColor,
                                enabled = text.isNotBlank(),
                                modifier = Modifier.weight(1f),
                            ) { onSave(stripTags(text)) }
                            PillButton(
                                label = "Time the lines",
                                bg = accentColor,
                                fg = onAccentColor,
                                enabled = text.isNotBlank(),
                                modifier = Modifier.weight(1f),
                            ) { startTiming() }
                        }
                    }

                    EditorStep.TIME -> TimingStep(
                        lines = lines,
                        times = times,
                        cursor = cursor,
                        onCursorChange = { cursor = it.coerceIn(0, lines.size) },
                        playbackPositionFlow = playbackPositionFlow,
                        lyricsSyncOffsetMs = lyricsSyncOffsetMs,
                        isPlaying = isPlaying,
                        onPlayPause = onPlayPause,
                        onSeekTo = onSeekTo,
                        accentColor = accentColor,
                        onAccentColor = onAccentColor,
                        contentColor = contentColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimingStep(
    lines: List<String>,
    times: MutableList<Long?>,
    cursor: Int,
    onCursorChange: (Int) -> Unit,
    playbackPositionFlow: StateFlow<Long>,
    lyricsSyncOffsetMs: Int,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    accentColor: Color,
    onAccentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val position by playbackPositionFlow.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(cursor) {
        if (lines.isNotEmpty()) listState.animateScrollToItem((cursor - 2).coerceAtLeast(0))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Play the song and tap Stamp line as each line starts. Tap a line to re-time it, tap its time to jump there.",
            color = contentColor.copy(alpha = 0.65f),
            fontSize = 13.sp,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(lines) { i, line ->
                val isCursor = i == cursor
                val time = times.getOrNull(i)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                isCursor -> accentColor.copy(alpha = 0.22f)
                                else -> contentColor.copy(alpha = 0.05f)
                            },
                        )
                        .clickable { onCursorChange(i) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(76.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (time != null) accentColor.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable(enabled = time != null) {
                                time?.let { onSeekTo((it - lyricsSyncOffsetMs).coerceAtLeast(0L)) }
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            time?.let { formatTime(it) } ?: "--:--.--",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = if (time != null) accentColor else contentColor.copy(alpha = 0.4f),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        line,
                        color = contentColor.copy(alpha = if (isCursor) 1f else 0.8f),
                        fontWeight = if (isCursor) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Text(
            "Now ${formatTime(position + lyricsSyncOffsetMs)} · ${times.count { it != null }} of ${lines.size} timed",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = contentColor.copy(alpha = 0.7f),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundButton(Icons.Rounded.Replay5, "Back 5 seconds", contentColor) {
                onSeekTo((position - 5_000L).coerceAtLeast(0L))
            }
            RoundButton(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (isPlaying) "Pause" else "Play", contentColor, onClick = onPlayPause)
            RoundButton(Icons.AutoMirrored.Rounded.Undo, "Undo last stamp", contentColor, enabled = cursor > 0) {
                val back = cursor - 1
                times[back] = null
                onCursorChange(back)
            }
            PillButton(
                label = if (cursor >= lines.size) "All lines timed" else "Stamp line",
                icon = { Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = onAccentColor, modifier = Modifier.size(20.dp)) },
                bg = accentColor,
                fg = onAccentColor,
                enabled = cursor < lines.size,
                modifier = Modifier.weight(1f),
                height = 60,
            ) {
                val stamp = (position + lyricsSyncOffsetMs - TAP_REACTION_MS).coerceAtLeast(0L)
                // Keep times in order: never earlier than the line before.
                val floor = (0 until cursor).mapNotNull { times[it] }.maxOrNull() ?: 0L
                times[cursor] = stamp.coerceAtLeast(floor)
                onCursorChange(cursor + 1)
            }
        }
    }
}

@Composable
private fun RoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    contentColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(contentColor.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = contentColor.copy(alpha = if (enabled) 1f else 0.35f))
    }
}

@Composable
private fun PillButton(
    label: String,
    bg: Color,
    fg: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    height: Int = 52,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(height.dp)
            .clip(RoundedCornerShape((height / 2).dp))
            .background(if (enabled) bg else bg.copy(alpha = bg.alpha * 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.invoke()
        if (icon != null) Spacer(Modifier.width(8.dp))
        Text(label, color = fg.copy(alpha = if (enabled) 1f else 0.5f), fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ── Pure helpers (kept small so they can be unit tested) ─────────────────────

/** Existing lyrics as editable text: LRC when synced, else plain lines. */
internal fun initialText(lyrics: Lyrics?): String {
    val synced = lyrics?.synced.orEmpty()
    if (synced.isNotEmpty()) return synced.joinToString("\n") { "[${formatTime(it.time.toLong())}] ${it.line}" }
    return lyrics?.plain.orEmpty().joinToString("\n")
}

private fun parseTag(min: String, sec: String): Long? {
    val m = min.toLongOrNull() ?: return null
    val s = sec.replace(':', '.').toDoubleOrNull() ?: return null
    return m * 60_000L + (s * 1000).toLong()
}

internal fun stripTags(text: String): String =
    text.lines().joinToString("\n") { raw -> LRC_TAG.find(raw)?.groupValues?.get(3) ?: raw }.trim()

/** "mm:ss.xx", the LRC time format. */
internal fun formatTime(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val m = clamped / 60_000
    val s = (clamped % 60_000) / 1000
    val cs = (clamped % 1000) / 10
    return "%02d:%02d.%02d".format(m, s, cs)
}

/**
 * LRC text from [lines] and their stamps. With no stamps at all this is plain text. Lines left
 * untimed are spread evenly between their timed neighbours (or 2 s apart after the last one),
 * so every line still shows.
 */
internal fun buildLrc(lines: List<String>, times: List<Long?>): String {
    if (times.none { it != null }) return lines.joinToString("\n")
    val filled = times.toMutableList()
    var i = 0
    while (i < filled.size) {
        if (filled[i] != null) { i++; continue }
        val start = i
        while (i < filled.size && filled[i] == null) i++
        val before = filled.getOrNull(start - 1)
        val after = filled.getOrNull(i)
        val gapCount = i - start + 1
        for (k in start until i) {
            val step = (k - start + 1).toLong()
            filled[k] = when {
                before != null && after != null -> before + (after - before) * step / gapCount
                before != null -> before + 2_000L * step
                after != null -> (after - 2_000L * (i - k)).coerceAtLeast(0L)
                else -> 0L
            }
        }
    }
    return lines.indices.joinToString("\n") { idx -> "[${formatTime(filled[idx] ?: 0L)}]${lines[idx]}" }
}
