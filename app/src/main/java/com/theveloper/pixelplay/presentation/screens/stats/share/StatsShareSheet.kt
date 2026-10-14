package com.theveloper.pixelplay.presentation.screens.stats.share

import android.os.Build
import android.text.format.DateFormat as AndroidDateFormat
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository.PlaybackStatsSummary
import com.theveloper.pixelplay.data.stats.StatsInsights
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import com.theveloper.pixelplay.presentation.screens.stats.hourLabel
import com.theveloper.pixelplay.presentation.screens.stats.listenerProfile
import com.theveloper.pixelplay.presentation.screens.stats.listenerTypeTitle
import com.theveloper.pixelplay.presentation.screens.stats.previousPeriodLabel
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

private const val UNKNOWN_GENRE = "Unknown Genre"

/** Short label for the summary's period, e.g. "October 2026", "Week of Oct 5", "Sep 1 – Sep 7". */
@Composable
fun statsPeriodLabel(summary: PlaybackStatsSummary): String {
    val zone = ZoneId.systemDefault()
    val end = Instant.ofEpochMilli(summary.endTimestamp).atZone(zone).toLocalDate()
    val start = summary.startTimestamp?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    if (summary.isCustomRange && start != null) {
        return stringResource(R.string.stats_share_period_custom, dayFormat.format(start), dayFormat.format(end))
    }
    return when (summary.range) {
        StatsTimeRange.DAY -> stringResource(R.string.stats_range_today)
        StatsTimeRange.WEEK -> stringResource(R.string.stats_share_period_week, dayFormat.format(start ?: end))
        StatsTimeRange.MONTH -> DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()).format(end)
        StatsTimeRange.YEAR -> end.year.toString()
        StatsTimeRange.ALL -> stringResource(R.string.stats_range_all_time)
    }
}

@Composable
fun rememberStatsShareData(summary: PlaybackStatsSummary): StatsShareData {
    val periodLabel = statsPeriodLabel(summary)
    val previousLabel = previousPeriodLabel(summary)
    val use24Hour = AndroidDateFormat.is24HourFormat(LocalContext.current)
    val profile = remember(summary) { summary.listenerProfile() }
    val listenerTitle = profile?.let { listenerTypeTitle(it) }
    return remember(summary, periodLabel, previousLabel, use24Hour, listenerTitle) {
        val topArtist = summary.topArtists.firstOrNull()
        val peak = StatsInsights.peakHour(summary.hourlyDurationsMs)
        StatsShareData(
            periodLabel = periodLabel,
            previousLabel = previousLabel,
            totalDurationMs = summary.totalDurationMs,
            totalPlays = summary.totalPlayCount,
            uniqueSongs = summary.uniqueSongs,
            uniqueArtists = summary.uniqueArtists,
            averageDailyDurationMs = summary.averageDailyDurationMs,
            deltaPercent = summary.previous?.let { StatsInsights.percentChange(summary.totalDurationMs, it.totalDurationMs) },
            topArtists = summary.topArtists,
            topSongs = summary.topSongs,
            topArtistSongs = if (topArtist == null) emptyList() else summary.songs.filter {
                it.artist.contains(topArtist.artist, ignoreCase = true)
            },
            albums = summary.albumGrid,
            timeline = summary.timeline,
            hourly = summary.hourlyDurationsMs,
            peakHourLabel = peak?.let { hourLabel(it, use24Hour) },
            eveningPercent = (StatsInsights.eveningShare(summary.hourlyDurationsMs) * 100).toInt(),
            busiestDay = summary.peakDayLabel,
            topGenre = summary.topGenres.firstOrNull()?.genre?.takeIf { it != UNKNOWN_GENRE },
            listenerTitle = listenerTitle
        )
    }
}

private val MonoScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF111111),
    onPrimaryContainer = Color(0xFFF2F2F2),
    secondary = Color(0xFFBDBDBD),
    secondaryContainer = Color(0xFF1C1C1C),
    onSecondaryContainer = Color(0xFFF2F2F2),
    tertiary = Color(0xFFE0E0E0),
    tertiaryContainer = Color(0xFF161616),
    onTertiaryContainer = Color(0xFFF2F2F2),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF2F2F2),
    surfaceContainerHighest = Color(0xFF202020)
)

/** Colour scheme the cards are drawn with for [mode]. */
@Composable
fun shareColorScheme(mode: ShareColorMode, albumColors: ColorSchemePair?): ColorScheme = when (mode) {
    ShareColorMode.ALBUM_ART -> albumColors?.dark ?: MaterialTheme.colorScheme
    ShareColorMode.DYNAMIC -> MaterialTheme.colorScheme
    ShareColorMode.MONO -> MonoScheme
    ShareColorMode.LIGHT -> albumColors?.light ?: lightColorScheme()
}

private enum class ShareAction { SHARE, SAVE, COPY }

/** Card drawn at full export size, scaled to [scale], recorded into [layer] for export. */
@Composable
private fun CardPreview(
    template: ShareCardTemplate,
    data: StatsShareData,
    scheme: ColorScheme,
    format: ShareCardFormat,
    showPlayCounts: Boolean,
    scale: Float,
    layer: GraphicsLayer?
) {
    Box(
        modifier = Modifier
            .requiredSize(format.widthDp.dp, format.heightDp.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shape = RoundedCornerShape(24.dp)
                clip = true
            }
            .then(
                if (layer != null) {
                    Modifier.drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                } else {
                    Modifier
                }
            )
    ) {
        StatsShareCard(
            template = template,
            data = data,
            colors = shareCardColors(template, scheme),
            format = format,
            showPlayCounts = showPlayCounts
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsShareSheet(
    data: StatsShareData,
    initialTemplate: ShareCardTemplate,
    albumColors: ColorSchemePair?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val templates = remember(data) { ShareCardTemplate.entries.filter { data.hasDataFor(it) } }
    if (templates.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var template by rememberSaveable { mutableStateOf(initialTemplate.takeIf { it in templates } ?: templates.first()) }
    var colorMode by rememberSaveable {
        mutableStateOf(if (albumColors != null) ShareColorMode.ALBUM_ART else ShareColorMode.DYNAMIC)
    }
    var format by rememberSaveable { mutableStateOf(ShareCardFormat.STORY) }
    var showPlayCounts by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val layer = rememberGraphicsLayer()
    val scheme = shareColorScheme(colorMode, albumColors)
    val chooserTitle = stringResource(R.string.stats_share_chooser_title)
    val savedText = stringResource(R.string.stats_share_saved)
    val copiedText = stringResource(R.string.stats_share_copied)
    val failedText = stringResource(R.string.stats_share_failed)

    fun export(action: ShareAction) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val bitmap = StatsShareExporter.toSoftware(layer.toImageBitmap().asAndroidBitmap())
                val name = "pixelplayer-stats-${template.name.lowercase(Locale.ROOT)}-${System.currentTimeMillis()}"
                when (action) {
                    ShareAction.SHARE -> {
                        val uri = StatsShareExporter.writeToCache(context, bitmap, name)
                        StatsShareExporter.share(context, uri, chooserTitle)
                    }
                    ShareAction.SAVE -> {
                        val ok = StatsShareExporter.saveToGallery(context, bitmap, name)
                        Toast.makeText(context, if (ok) savedText else failedText, Toast.LENGTH_SHORT).show()
                    }
                    ShareAction.COPY -> {
                        val uri = StatsShareExporter.writeToCache(context, bitmap, name)
                        StatsShareExporter.copyToClipboard(context, uri, chooserTitle)
                        // Android 13+ shows its own clipboard confirmation.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Stats card export failed")
                Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.stats_share_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = data.periodLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                contentAlignment = Alignment.Center
            ) {
                val scale = min(340f / format.heightDp, (maxWidth.value - 48f) / format.widthDp).coerceAtMost(1f)
                AnimatedContent(
                    targetState = Triple(template, format, colorMode),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "shareCardPreview"
                ) { (shownTemplate, shownFormat, _) ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        val isCurrent = shownTemplate == template && shownFormat == format
                        CardPreview(
                            template = shownTemplate,
                            data = data,
                            scheme = scheme,
                            format = shownFormat,
                            showPlayCounts = showPlayCounts,
                            scale = scale,
                            layer = if (isCurrent) layer else null
                        )
                    }
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(templates, key = { it.name }) { option ->
                    FilterChip(
                        selected = option == template,
                        onClick = { template = option },
                        label = { Text(stringResource(option.labelRes)) }
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ShareColorMode.entries, key = { it.name }) { mode ->
                    FilterChip(
                        selected = mode == colorMode,
                        onClick = { colorMode = mode },
                        enabled = mode != ShareColorMode.ALBUM_ART || albumColors != null,
                        label = { Text(stringResource(mode.labelRes)) }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    ShareCardFormat.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == format,
                            onClick = { format = option },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ShareCardFormat.entries.size)
                        ) {
                            Text(stringResource(option.labelRes), maxLines = 1)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .toggleable(
                            value = showPlayCounts,
                            role = Role.Switch,
                            onValueChange = { showPlayCounts = it }
                        )
                        .padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.stats_share_play_counts), style = MaterialTheme.typography.labelLarge)
                    Switch(checked = showPlayCounts, onCheckedChange = null)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { export(ShareAction.SHARE) },
                    enabled = !busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.stats_share_action_share))
                }
                FilledTonalIconButton(
                    onClick = { export(ShareAction.SAVE) },
                    enabled = !busy,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.stats_share_action_save))
                }
                FilledTonalIconButton(
                    onClick = { export(ShareAction.COPY) },
                    enabled = !busy,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.stats_share_action_copy))
                }
            }
        }
    }
}

private const val RECAP_PAGE_MS = 5_000L

/** Full-screen, story-style walk through every card that has data. */
@Composable
fun StatsRecapStory(
    data: StatsShareData,
    albumColors: ColorSchemePair?,
    onShare: (ShareCardTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    val pages = remember(data) { ShareCardTemplate.entries.filter { data.hasDataFor(it) } }
    if (pages.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    val scheme = shareColorScheme(if (albumColors != null) ShareColorMode.ALBUM_ART else ShareColorMode.DYNAMIC, albumColors)
    val page = pages[index.coerceIn(0, pages.lastIndex)]

    // Frame-clock timer instead of an animation, so animator scale 0 doesn't skip every page.
    LaunchedEffect(index) {
        progress = 0f
        val start = withFrameMillis { it }
        while (true) {
            val elapsed = withFrameMillis { it } - start
            progress = (elapsed.toFloat() / RECAP_PAGE_MS).coerceIn(0f, 1f)
            if (elapsed >= RECAP_PAGE_MS) break
        }
        if (index < pages.lastIndex) index++ else onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .pointerInput(pages.size) {
                    detectTapGestures { offset ->
                        if (offset.x < size.width / 3f) {
                            index = (index - 1).coerceAtLeast(0)
                        } else if (index < pages.lastIndex) {
                            index++
                        } else {
                            onDismiss()
                        }
                    }
                }
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val scale = min(
                    (maxWidth.value - 24f) / ShareCardFormat.STORY.widthDp,
                    (maxHeight.value - 140f) / ShareCardFormat.STORY.heightDp
                )
                CardPreview(
                    template = page,
                    data = data,
                    scheme = scheme,
                    format = ShareCardFormat.STORY,
                    showPlayCounts = true,
                    scale = scale,
                    layer = null
                )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    pages.forEachIndexed { pageIndex, _ ->
                        val fill = when {
                            pageIndex < index -> 1f
                            pageIndex == index -> progress
                            else -> 0f
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f))
                                .drawWithContent {
                                    drawContent()
                                    drawProgress(fill)
                                }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.stats_x_cd_close_recap))
                    }
                }
            }
            Button(
                onClick = { onShare(page) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.stats_share_this_card))
            }
        }
    }
}

private fun DrawScope.drawProgress(fraction: Float) {
    if (fraction <= 0f) return
    drawRect(color = Color.White, size = size.copy(width = size.width * fraction.coerceIn(0f, 1f)))
}
