package com.theveloper.pixelplay.presentation.screens.stats

import android.text.format.DateFormat as AndroidDateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository.PlaybackStatsSummary
import com.theveloper.pixelplay.data.stats.StatsInsights
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.utils.formatListeningDurationCompact
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val SectionShape = RoundedCornerShape(28.dp)
private val TileShape = RoundedCornerShape(20.dp)

// ---------------------------------------------------------------------------------------------
// Overview additions
// ---------------------------------------------------------------------------------------------

/** "vs last month" style label for the summary's comparison period. */
@Composable
internal fun previousPeriodLabel(summary: PlaybackStatsSummary): String {
    if (summary.isCustomRange) {
        val days = summary.dailyTotals.size.coerceAtLeast(1)
        return stringResource(R.string.stats_x_vs_previous_days, days)
    }
    return when (summary.range) {
        StatsTimeRange.DAY -> stringResource(R.string.stats_x_vs_yesterday)
        StatsTimeRange.WEEK -> stringResource(R.string.stats_x_vs_last_week)
        StatsTimeRange.MONTH -> stringResource(R.string.stats_x_vs_last_month)
        StatsTimeRange.YEAR -> stringResource(R.string.stats_x_vs_last_year)
        StatsTimeRange.ALL -> ""
    }
}

@Composable
private fun deltaText(current: Long, previous: Long): String? {
    val percent = StatsInsights.percentChange(current, previous) ?: return null
    return when {
        percent > 0 -> stringResource(R.string.stats_x_delta_up_percent, percent)
        percent < 0 -> stringResource(R.string.stats_x_delta_down_percent, -percent)
        else -> stringResource(R.string.stats_x_delta_same)
    }
}

@Composable
private fun countDeltaText(current: Int, previous: Int): String = when {
    current > previous -> stringResource(R.string.stats_x_delta_up_count, current - previous)
    current < previous -> stringResource(R.string.stats_x_delta_down_count, previous - current)
    else -> stringResource(R.string.stats_x_delta_same)
}

/** Deltas against the previous period plus Songs / Artists / Day streak tiles. Sits under the hero. */
@Composable
internal fun StatsComparisonSection(
    summary: PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    if (summary == null || (summary.totalDurationMs <= 0L && summary.totalPlayCount == 0)) return
    val previous = summary.previous
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (previous != null) {
            val listeningDelta = deltaText(summary.totalDurationMs, previous.totalDurationMs)
            val playsDelta = deltaText(summary.totalPlayCount.toLong(), previous.totalPlayCount.toLong())
            if (listeningDelta != null || playsDelta != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listeningDelta?.let {
                        DeltaPill(
                            text = stringResource(R.string.stats_x_delta_listening, it),
                            positive = summary.totalDurationMs >= previous.totalDurationMs
                        )
                    }
                    playsDelta?.let {
                        DeltaPill(
                            text = stringResource(R.string.stats_x_delta_plays, it),
                            positive = summary.totalPlayCount >= previous.totalPlayCount
                        )
                    }
                }
                Text(
                    text = previousPeriodLabel(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.stats_x_tile_songs),
                value = numberFormat.format(summary.uniqueSongs),
                supporting = previous?.let { countDeltaText(summary.uniqueSongs, it.uniqueSongs) },
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.stats_x_tile_artists),
                value = numberFormat.format(summary.uniqueArtists),
                supporting = previous?.let { countDeltaText(summary.uniqueArtists, it.uniqueArtists) },
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = stringResource(R.string.stats_x_tile_streak),
                value = numberFormat.format(summary.currentStreakDays),
                supporting = stringResource(R.string.stats_x_tile_streak_best, summary.longestStreakDays),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DeltaPill(text: String, positive: Boolean) {
    val container = if (positive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (positive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    supporting: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Banner that opens the story-style recap of the selected range. */
@Composable
internal fun StatsRecapBanner(
    title: String,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SectionShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(width = 60.dp, height = 70.dp)) {
            Box(
                modifier = Modifier
                    .offset(x = 0.dp, y = 8.dp)
                    .size(width = 42.dp, height = 58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
            )
            Box(
                modifier = Modifier
                    .offset(x = 16.dp, y = 2.dp)
                    .size(width = 42.dp, height = 58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.stats_x_recap_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledIconButton(
            onClick = onShare,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.stats_x_cd_share))
        }
        FilledIconButton(
            onClick = onPlay,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.stats_x_cd_play_recap))
        }
    }
}

/** Small "▲ 2" / "▼ 1" / "NEW" / "–" label for chart rows. */
@Composable
internal fun RankChangeLabel(change: StatsInsights.RankChange, modifier: Modifier = Modifier) {
    val (text, color, description) = when (change) {
        is StatsInsights.RankChange.Up -> Triple(
            stringResource(R.string.stats_x_delta_up_count, change.places),
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.stats_x_cd_rank_up, change.places)
        )
        is StatsInsights.RankChange.Down -> Triple(
            stringResource(R.string.stats_x_delta_down_count, change.places),
            MaterialTheme.colorScheme.error,
            stringResource(R.string.stats_x_cd_rank_down, change.places)
        )
        StatsInsights.RankChange.New -> Triple(
            stringResource(R.string.stats_x_rank_new),
            MaterialTheme.colorScheme.tertiary,
            stringResource(R.string.stats_x_cd_rank_new)
        )
        StatsInsights.RankChange.Same -> Triple(
            stringResource(R.string.stats_x_delta_same),
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.stats_x_cd_rank_same)
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier.semantics { contentDescription = description }
    )
}

// ---------------------------------------------------------------------------------------------
// Insights
// ---------------------------------------------------------------------------------------------

@Composable
private fun InsightCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SectionShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailing?.invoke()
        }
        content()
    }
}

internal fun hourLabel(hour: Int, use24Hour: Boolean): String {
    return if (use24Hour) {
        String.format(Locale.getDefault(), "%02d:00", hour)
    } else {
        LocalTime.of(hour.coerceIn(0, 23), 0).format(DateTimeFormatter.ofPattern("h a", Locale.getDefault()))
    }
}

/** Radial 24-hour clock. [hourly] must have 24 entries; [peakHour] is drawn in [peakColor]. */
@Composable
internal fun ListeningClockDial(
    hourly: List<Long>,
    peakHour: Int?,
    barColor: Color,
    peakColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    barWidth: Dp = 8.dp
) {
    Canvas(modifier = modifier) {
        val max = hourly.maxOrNull()?.takeIf { it > 0L } ?: return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val outer = size.minDimension / 2f - barWidth.toPx()
        val inner = outer * 0.38f
        val span = outer - inner
        drawCircle(color = trackColor, radius = inner * 0.85f, center = center)
        for (hour in 0 until 24) {
            val angle = Math.toRadians(hour / 24.0 * 360.0 - 90.0)
            val length = (hourly[hour].toFloat() / max.toFloat()) * span
            val startR = inner
            val endR = inner + length.coerceAtLeast(barWidth.toPx() * 0.25f)
            drawLine(
                color = if (hour == peakHour) peakColor else barColor,
                start = Offset(center.x + startR * cos(angle).toFloat(), center.y + startR * sin(angle).toFloat()),
                end = Offset(center.x + endR * cos(angle).toFloat(), center.y + endR * sin(angle).toFloat()),
                strokeWidth = barWidth.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
internal fun ListeningClockCard(summary: PlaybackStatsSummary?, modifier: Modifier = Modifier) {
    if (summary == null || summary.hourlyDurationsMs.size != 24) return
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val hourly = when (mode) {
        1 -> summary.weekdayHourlyDurationsMs
        2 -> summary.weekendHourlyDurationsMs
        else -> summary.hourlyDurationsMs
    }
    val peak = StatsInsights.peakHour(hourly)
    val use24Hour = AndroidDateFormat.is24HourFormat(LocalContext.current)
    val options = listOf(
        stringResource(R.string.stats_x_clock_all),
        stringResource(R.string.stats_x_clock_weekdays),
        stringResource(R.string.stats_x_clock_weekend)
    )
    InsightCard(title = stringResource(R.string.stats_x_clock_title), modifier = modifier) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = mode == index,
                    onClick = { mode = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Text(label, maxLines = 1)
                }
            }
        }
        val peakText = peak?.let { hourLabel(it, use24Hour) } ?: stringResource(R.string.stats_em_dash)
        val clockDescription = stringResource(R.string.stats_x_cd_clock, peakText)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics {
                    contentDescription = clockDescription
                },
            contentAlignment = Alignment.Center
        ) {
            ListeningClockDial(
                hourly = hourly,
                peakHour = peak,
                barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                peakColor = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = peakText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.stats_x_clock_peak),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ClockLabel(hourLabel(0, use24Hour), Alignment.TopCenter)
            ClockLabel(hourLabel(6, use24Hour), Alignment.CenterEnd)
            ClockLabel(hourLabel(12, use24Hour), Alignment.BottomCenter)
            ClockLabel(hourLabel(18, use24Hour), Alignment.CenterStart)
        }
        if (peak != null) {
            Text(
                text = stringResource(
                    R.string.stats_x_clock_caption,
                    hourLabel(peak, use24Hour),
                    (StatsInsights.eveningShare(hourly) * 100).toInt()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ClockLabel(text: String, alignment: Alignment) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(alignment)
    )
}

private fun intensityLevel(ms: Long, max: Long): Int {
    if (ms <= 0L || max <= 0L) return 0
    val ratio = ms.toFloat() / max.toFloat()
    return when {
        ratio > 0.75f -> 4
        ratio > 0.5f -> 3
        ratio > 0.25f -> 2
        else -> 1
    }
}

@Composable
private fun levelColor(level: Int): Color = when (level) {
    0 -> MaterialTheme.colorScheme.surfaceContainerHighest
    1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    else -> MaterialTheme.colorScheme.primary
}

/** Month calendar (short ranges) or a scrollable weeks × days heatmap (long ranges). */
@Composable
internal fun ListeningCalendarCard(summary: PlaybackStatsSummary?, modifier: Modifier = Modifier) {
    if (summary == null || summary.range == StatsTimeRange.DAY && !summary.isCustomRange) return
    val totals = summary.dailyTotals
    if (totals.isEmpty()) return
    val byDate = remember(totals) { totals.associate { it.date to it.durationMs } }
    val max = remember(totals) { totals.maxOf { it.durationMs } }
    val today = remember { LocalDate.now(ZoneId.systemDefault()) }
    val activeDays = totals.count { it.durationMs > 0L }
    val title = if (summary.range == StatsTimeRange.MONTH && !summary.isCustomRange) {
        YearMonth.from(today).month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    } else {
        stringResource(R.string.stats_x_calendar_title)
    }
    InsightCard(
        title = title,
        subtitle = stringResource(R.string.stats_x_calendar_active, activeDays, totals.size),
        modifier = modifier
    ) {
        if (totals.size <= 42) {
            val dates = if (summary.range == StatsTimeRange.MONTH && !summary.isCustomRange) {
                val month = YearMonth.from(totals.first().date)
                (1..month.lengthOfMonth()).map { month.atDay(it) }
            } else {
                totals.map { it.date }
            }
            val leading = (dates.first().dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
            val cells: List<LocalDate?> = List(leading) { null } + dates
            val weekLabels = remember {
                (0 until 7).map {
                    DayOfWeek.MONDAY.plus(it.toLong()).getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekLabels.forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                cells.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (index in 0 until 7) {
                            val date = week.getOrNull(index)
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (date != null) {
                                    val future = date.isAfter(today)
                                    val level = intensityLevel(byDate[date] ?: 0L, max)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (future) Color.Transparent else levelColor(level)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = date.dayOfMonth.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                future -> MaterialTheme.colorScheme.outline
                                                level >= 3 -> MaterialTheme.colorScheme.onPrimary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val first = totals.first().date
            val leading = (first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
            val cells: List<LocalDate?> = List(leading) { null } + totals.map { it.date }
            val weeks = remember(totals) { cells.chunked(7) }
            val scrollState = rememberScrollState()
            LaunchedEffect(weeks.size) { scrollState.scrollTo(scrollState.maxValue) }
            Row(
                modifier = Modifier.horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (index in 0 until 7) {
                            val date = week.getOrNull(index)
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (date == null) Color.Transparent else levelColor(intensityLevel(byDate[date] ?: 0L, max))
                                    )
                            )
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StreakTile(
                value = stringResource(R.string.stats_x_days_value, summary.currentStreakDays),
                label = stringResource(R.string.stats_x_streak_current),
                showFlame = true,
                modifier = Modifier.weight(1f)
            )
            StreakTile(
                value = stringResource(R.string.stats_x_days_value, summary.longestStreakDays),
                label = stringResource(R.string.stats_x_streak_longest),
                showFlame = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StreakTile(value: String, label: String, showFlame: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showFlame) {
            Icon(
                imageVector = Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ArtCircle(uri: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    if (uri != null) {
        SmartImage(
            model = uri,
            contentDescription = null,
            shape = CircleShape,
            modifier = modifier.size(size)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().take(1).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun shortDate(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

@Composable
internal fun DiscoveriesCard(
    summary: PlaybackStatsSummary?,
    onPlayNewFinds: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val discoveries = summary?.discoveries ?: return
    if (discoveries.newSongs == 0 && discoveries.newArtists == 0) return
    val share = if (summary.totalDurationMs > 0L) {
        (discoveries.newDurationMs * 100 / summary.totalDurationMs).toInt().coerceIn(0, 100)
    } else 0
    InsightCard(title = stringResource(R.string.stats_x_new_title), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberTile(discoveries.newSongs.toString(), stringResource(R.string.stats_x_new_songs), highlighted = true, modifier = Modifier.weight(1f))
            NumberTile(discoveries.newArtists.toString(), stringResource(R.string.stats_x_new_artists), highlighted = false, modifier = Modifier.weight(1f))
            NumberTile(stringResource(R.string.stats_x_percent, share), stringResource(R.string.stats_x_new_share), highlighted = false, modifier = Modifier.weight(1f))
        }
        discoveries.artists.forEach { find ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArtCircle(find.albumArtUri, find.artist, 44.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(find.artist, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = stringResource(R.string.stats_x_first_played, shortDate(find.firstPlayedAt), find.playCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (onPlayNewFinds != null && discoveries.newSongIds.isNotEmpty()) {
            OutlinedButton(onClick = onPlayNewFinds, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.stats_x_play_new_finds))
            }
        }
    }
}

@Composable
private fun NumberTile(value: String, label: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    val container = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (highlighted) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(TileShape)
            .background(container)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = content, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun OnRepeatCard(
    summary: PlaybackStatsSummary?,
    onPlay: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val song = summary?.onRepeat ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SectionShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SmartImage(
            model = song.albumArtUri,
            contentDescription = null,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.size(88.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.stats_x_on_repeat),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = stringResource(R.string.stats_x_on_repeat_plays, song.recentPlays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val maxPlays = (song.dailyPlays.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(
                modifier = Modifier.height(24.dp).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                song.dailyPlays.forEach { plays ->
                    Box(
                        modifier = Modifier
                            .width(7.dp)
                            .fillMaxHeight((plays.toFloat() / maxPlays).coerceAtLeast(0.1f))
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.tertiary)
                    )
                }
            }
        }
        if (onPlay != null) {
            FilledIconButton(onClick = { onPlay(song.songId) }) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.stats_x_cd_play_song, song.title))
            }
        }
    }
}

@Composable
internal fun MonthlyTopArtistsCard(summary: PlaybackStatsSummary?, modifier: Modifier = Modifier) {
    val months = summary?.monthlyTopArtists.orEmpty()
    if (months.size < 2) return
    InsightCard(
        title = stringResource(R.string.stats_x_monthly_title),
        modifier = modifier
    ) {
        months.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (index in 0 until 4) {
                    val month = row.getOrNull(index)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (month != null) {
                            ArtCircle(month.albumArtUri, month.artist, 52.dp)
                            Text(month.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = month.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun listenerTypeTitle(profile: StatsInsights.ListenerProfile): String {
    val time = when (profile.timeOfDay) {
        StatsInsights.TimeOfDay.NIGHT_OWL -> stringResource(R.string.stats_x_type_night_owl)
        StatsInsights.TimeOfDay.EARLY_BIRD -> stringResource(R.string.stats_x_type_early_bird)
        StatsInsights.TimeOfDay.DAYTIME -> stringResource(R.string.stats_x_type_daytime)
    }
    val style = when (profile.style) {
        StatsInsights.ListeningStyle.LOYALIST -> stringResource(R.string.stats_x_type_loyalist)
        StatsInsights.ListeningStyle.EXPLORER -> stringResource(R.string.stats_x_type_explorer)
        StatsInsights.ListeningStyle.REPEATER -> stringResource(R.string.stats_x_type_repeater)
        StatsInsights.ListeningStyle.MIXER -> stringResource(R.string.stats_x_type_mixer)
    }
    return stringResource(R.string.stats_x_type_format, time, style)
}

internal fun PlaybackStatsSummary.listenerProfile(): StatsInsights.ListenerProfile? = StatsInsights.listenerProfile(
    hourlyMs = hourlyDurationsMs,
    totalDurationMs = totalDurationMs,
    topArtistsDurationMs = topArtists.map { it.totalDurationMs },
    totalPlays = totalPlayCount,
    uniqueSongs = uniqueSongs,
    uniqueArtists = uniqueArtists,
    newSongsDurationMs = discoveries?.newDurationMs
)

@Composable
internal fun ListenerTypeCard(summary: PlaybackStatsSummary?, modifier: Modifier = Modifier) {
    val profile = remember(summary) { summary?.listenerProfile() } ?: return
    val description = when (profile.style) {
        StatsInsights.ListeningStyle.LOYALIST -> stringResource(R.string.stats_x_type_loyalist_desc)
        StatsInsights.ListeningStyle.EXPLORER -> stringResource(R.string.stats_x_type_explorer_desc)
        StatsInsights.ListeningStyle.REPEATER -> stringResource(R.string.stats_x_type_repeater_desc)
        StatsInsights.ListeningStyle.MIXER -> stringResource(R.string.stats_x_type_mixer_desc)
    }
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SectionShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.stats_x_type_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = content.copy(alpha = 0.85f)
        )
        Text(listenerTypeTitle(profile), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = content)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.9f))
        Spacer(Modifier.height(6.dp))
        TraitBar(stringResource(R.string.stats_x_trait_explorer), stringResource(R.string.stats_x_trait_loyalist), profile.loyalty)
        TraitBar(stringResource(R.string.stats_x_trait_early), stringResource(R.string.stats_x_trait_night), profile.night)
        TraitBar(stringResource(R.string.stats_x_trait_few), stringResource(R.string.stats_x_trait_many), profile.variety)
        TraitBar(stringResource(R.string.stats_x_trait_new), stringResource(R.string.stats_x_trait_repeat), profile.repeat)
    }
}

@Composable
private fun TraitBar(left: String, right: String, position: Float) {
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    val description = stringResource(R.string.stats_x_cd_trait, left, right, (position * 100).toInt())
    Column(
        modifier = Modifier.padding(top = 8.dp).semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row {
            Text(left, style = MaterialTheme.typography.labelMedium, color = content, modifier = Modifier.weight(1f))
            Text(right, style = MaterialTheme.typography.labelMedium, color = content)
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(content.copy(alpha = 0.18f))
            )
            val dot = 16.dp
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - dot) * position.coerceIn(0f, 1f))
                    .size(dot)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
internal fun MilestonesCard(summary: PlaybackStatsSummary?, modifier: Modifier = Modifier) {
    if (summary == null) return
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val rows = buildList {
        val hours = summary.totalDurationMs / 3_600_000L
        StatsInsights.nextMilestone(hours, StatsInsights.HOUR_MILESTONES)?.let { target ->
            add(
                Triple(
                    stringResource(R.string.stats_x_milestone_hours, target.toInt()),
                    stringResource(R.string.stats_x_milestone_progress, numberFormat.format(hours), numberFormat.format(target)),
                    StatsInsights.milestoneProgress(hours, target, StatsInsights.HOUR_MILESTONES)
                )
            )
        }
        val topArtist = summary.topArtists.firstOrNull()
        if (topArtist != null && summary.topArtistAllTimePlays > 0) {
            val plays = summary.topArtistAllTimePlays.toLong()
            StatsInsights.nextMilestone(plays, StatsInsights.PLAY_MILESTONES)?.let { target ->
                add(
                    Triple(
                        stringResource(R.string.stats_x_milestone_artist_plays, numberFormat.format(target), topArtist.artist),
                        stringResource(R.string.stats_x_milestone_progress, numberFormat.format(plays), numberFormat.format(target)),
                        StatsInsights.milestoneProgress(plays, target, StatsInsights.PLAY_MILESTONES)
                    )
                )
            }
        }
        val days = summary.activeDaysThisYear.toLong()
        if (days > 0L) {
            StatsInsights.nextMilestone(days, StatsInsights.DAY_MILESTONES)?.let { target ->
                add(
                    Triple(
                        stringResource(R.string.stats_x_milestone_days, target.toInt(), LocalDate.now().year),
                        stringResource(R.string.stats_x_milestone_progress, numberFormat.format(days), numberFormat.format(target)),
                        StatsInsights.milestoneProgress(days, target, StatsInsights.DAY_MILESTONES)
                    )
                )
            }
        }
    }
    if (rows.isEmpty()) return
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)
    InsightCard(title = stringResource(R.string.stats_x_milestones_title), modifier = modifier) {
        rows.forEachIndexed { index, (title, note, progress) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                    color = colors[index % colors.size],
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}
