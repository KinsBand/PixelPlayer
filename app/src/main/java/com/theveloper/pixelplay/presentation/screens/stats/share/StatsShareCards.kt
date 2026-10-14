package com.theveloper.pixelplay.presentation.screens.stats.share

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.screens.stats.ListeningClockDial
import com.theveloper.pixelplay.utils.formatListeningDurationCompact
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ShareCardTemplate(@StringRes val labelRes: Int) {
    TOP_ARTIST(R.string.stats_share_tpl_top_artist),
    TOP_SONGS(R.string.stats_share_tpl_top_songs),
    MINUTES(R.string.stats_share_tpl_minutes),
    RECEIPT(R.string.stats_share_tpl_receipt),
    CLOCK(R.string.stats_share_tpl_clock),
    ALBUM_GRID(R.string.stats_share_tpl_albums),
    SPOTLIGHT(R.string.stats_share_tpl_spotlight),
    SUMMARY(R.string.stats_share_tpl_summary)
}

enum class ShareColorMode(@StringRes val labelRes: Int) {
    ALBUM_ART(R.string.stats_share_color_album),
    DYNAMIC(R.string.stats_share_color_dynamic),
    MONO(R.string.stats_share_color_mono),
    LIGHT(R.string.stats_share_color_light)
}

enum class ShareCardFormat(@StringRes val labelRes: Int, val widthDp: Int, val heightDp: Int) {
    STORY(R.string.stats_share_format_story, 360, 640),
    SQUARE(R.string.stats_share_format_square, 360, 360)
}

/** Everything a share card shows, taken from one stats summary. */
@Immutable
data class StatsShareData(
    val periodLabel: String,
    val previousLabel: String,
    val totalDurationMs: Long,
    val totalPlays: Int,
    val uniqueSongs: Int,
    val uniqueArtists: Int,
    val averageDailyDurationMs: Long,
    val deltaPercent: Int?,
    val topArtists: List<PlaybackStatsRepository.ArtistPlaybackSummary>,
    val topSongs: List<PlaybackStatsRepository.SongPlaybackSummary>,
    val topArtistSongs: List<PlaybackStatsRepository.SongPlaybackSummary>,
    val albums: List<PlaybackStatsRepository.AlbumPlaybackSummary>,
    val timeline: List<PlaybackStatsRepository.TimelineEntry>,
    val hourly: List<Long>,
    val peakHourLabel: String?,
    val eveningPercent: Int,
    val busiestDay: String?,
    val topGenre: String?,
    val listenerTitle: String?
) {
    val topArtist: PlaybackStatsRepository.ArtistPlaybackSummary? get() = topArtists.firstOrNull()
    val topArtistSharePercent: Int
        get() = topArtist?.let { artist ->
            if (totalDurationMs > 0L) (artist.totalDurationMs * 100 / totalDurationMs).toInt() else 0
        } ?: 0

    /** First album art found, used to colour the "Album art" mode. */
    val leadArtUri: String?
        get() = topArtist?.albumArtUri ?: topSongs.firstOrNull()?.albumArtUri ?: albums.firstOrNull()?.albumArtUri

    fun hasDataFor(template: ShareCardTemplate): Boolean = when (template) {
        ShareCardTemplate.TOP_ARTIST, ShareCardTemplate.SPOTLIGHT -> topArtist != null
        ShareCardTemplate.TOP_SONGS, ShareCardTemplate.RECEIPT -> topSongs.isNotEmpty()
        ShareCardTemplate.MINUTES -> totalDurationMs > 0L
        ShareCardTemplate.CLOCK -> hourly.size == 24 && hourly.any { it > 0L }
        ShareCardTemplate.ALBUM_GRID -> albums.size >= 3
        ShareCardTemplate.SUMMARY -> topArtists.isNotEmpty() && topSongs.isNotEmpty()
    }
}

@Immutable
data class ShareCardColors(
    val background: Color,
    val content: Color,
    val accent: Color,
    val highlight: Color
) {
    val muted: Color get() = content.copy(alpha = 0.74f)
    val chip: Color get() = content.copy(alpha = 0.12f)
}

/** Each template takes a different pair of roles from [scheme] so a set of cards doesn't look identical. */
fun shareCardColors(template: ShareCardTemplate, scheme: ColorScheme): ShareCardColors = when (template) {
    ShareCardTemplate.TOP_ARTIST, ShareCardTemplate.TOP_SONGS ->
        ShareCardColors(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary, scheme.tertiary)
    ShareCardTemplate.MINUTES ->
        ShareCardColors(scheme.primary, scheme.onPrimary, scheme.onPrimary, scheme.primaryContainer)
    ShareCardTemplate.RECEIPT ->
        ShareCardColors(scheme.surfaceContainerHighest, scheme.onSurface, scheme.primary, scheme.tertiary)
    ShareCardTemplate.CLOCK ->
        ShareCardColors(scheme.surface, scheme.onSurface, scheme.primary, scheme.tertiary)
    ShareCardTemplate.ALBUM_GRID ->
        ShareCardColors(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary, scheme.tertiary)
    ShareCardTemplate.SPOTLIGHT, ShareCardTemplate.SUMMARY ->
        ShareCardColors(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary, scheme.primary)
}

private val ReceiptPaper = Color(0xFFFBF8F1)
private val ReceiptInk = Color(0xFF1C1B1F)

/** One shareable card at its export size ([ShareCardFormat.widthDp] x [ShareCardFormat.heightDp]). */
@Composable
fun StatsShareCard(
    template: ShareCardTemplate,
    data: StatsShareData,
    colors: ShareCardColors,
    format: ShareCardFormat,
    showPlayCounts: Boolean,
    modifier: Modifier = Modifier
) {
    val story = format == ShareCardFormat.STORY
    Box(
        modifier = modifier
            .size(format.widthDp.dp, format.heightDp.dp)
            .background(colors.background)
    ) {
        when (template) {
            ShareCardTemplate.TOP_ARTIST -> TopArtistCard(data, colors, story, showPlayCounts)
            ShareCardTemplate.TOP_SONGS -> TopSongsCard(data, colors, story, showPlayCounts)
            ShareCardTemplate.MINUTES -> MinutesCard(data, colors, story)
            ShareCardTemplate.RECEIPT -> ReceiptCard(data, story, showPlayCounts)
            ShareCardTemplate.CLOCK -> ClockCard(data, colors, story)
            ShareCardTemplate.ALBUM_GRID -> AlbumGridCard(data, colors, story)
            ShareCardTemplate.SPOTLIGHT -> SpotlightCard(data, colors, story, showPlayCounts)
            ShareCardTemplate.SUMMARY -> SummaryCard(data, colors, story)
        }
    }
}

// ---- building blocks ----------------------------------------------------------------------

@Composable
private fun Kicker(text: String, color: Color) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        color = color,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
    )
}

@Composable
private fun Footer(colors: ShareCardColors, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.accent)
        )
        Text(
            text = stringResource(R.string.app_name),
            color = colors.muted,
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.padding(start = 8.dp).weight(1f)
        )
        if (trailing != null) {
            Text(trailing, color = colors.muted, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold))
        }
    }
}

@Composable
private fun Chip(text: String, colors: ShareCardColors) {
    Text(
        text = text,
        color = colors.content,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.chip)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun Art(uri: String?, size: Dp, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier = Modifier) {
    SmartImage(
        model = uri,
        contentDescription = null,
        shape = shape,
        modifier = modifier.size(size)
    )
}

@Composable
private fun CardColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp, vertical = 30.dp),
        content = content
    )
}

private val numberFormat: NumberFormat get() = NumberFormat.getIntegerInstance()

// ---- templates ------------------------------------------------------------------------------

@Composable
private fun TopArtistCard(data: StatsShareData, colors: ShareCardColors, story: Boolean, showPlays: Boolean) {
    val artist = data.topArtist ?: return
    CardColumn {
        Kicker(stringResource(R.string.stats_share_kicker_top_artist), colors.highlight)
        Text(data.periodLabel, color = colors.muted, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(if (story) 30.dp else 14.dp))
        Art(
            uri = artist.albumArtUri,
            size = if (story) 240.dp else 120.dp,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(if (story) 26.dp else 12.dp))
        Text(
            text = artist.artist,
            color = colors.content,
            style = TextStyle(fontSize = if (story) 46.sp else 32.sp, fontWeight = FontWeight.Black, lineHeight = if (story) 48.sp else 34.sp),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            Chip(formatListeningDurationCompact(artist.totalDurationMs), colors)
            if (showPlays) Chip(stringResource(R.string.stats_share_n_plays, numberFormat.format(artist.playCount)), colors)
            if (story) Chip(stringResource(R.string.stats_share_percent_of_listening, data.topArtistSharePercent), colors)
        }
        Spacer(Modifier.weight(1f))
        Footer(colors)
    }
}

@Composable
private fun TopSongsCard(data: StatsShareData, colors: ShareCardColors, story: Boolean, showPlays: Boolean) {
    CardColumn {
        Kicker(stringResource(R.string.stats_share_kicker_top_songs), colors.highlight)
        Text(
            text = data.periodLabel,
            color = colors.content,
            style = TextStyle(fontSize = if (story) 36.sp else 26.sp, fontWeight = FontWeight.Black)
        )
        Spacer(Modifier.height(if (story) 26.dp else 12.dp))
        data.topSongs.take(if (story) 5 else 3).forEachIndexed { index, song ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = if (story) 8.dp else 5.dp)
            ) {
                Text(
                    text = (index + 1).toString(),
                    color = colors.accent,
                    style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Black),
                    modifier = Modifier.width(30.dp)
                )
                Art(song.albumArtUri, if (story) 56.dp else 44.dp, RoundedCornerShape(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, color = colors.content, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = colors.muted, style = TextStyle(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (showPlays) {
                    Text(stringResource(R.string.stats_share_times, song.playCount), color = colors.muted, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Footer(colors, trailing = if (showPlays) stringResource(R.string.stats_share_n_plays, numberFormat.format(data.totalPlays)) else null)
    }
}

@Composable
private fun MinutesCard(data: StatsShareData, colors: ShareCardColors, story: Boolean) {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(data.totalDurationMs)
    CardColumn {
        Kicker(data.periodLabel, colors.content)
        Spacer(Modifier.weight(1f))
        Text(
            text = numberFormat.format(minutes),
            color = colors.content,
            style = TextStyle(fontSize = if (story) 100.sp else 76.sp, fontWeight = FontWeight.Black, letterSpacing = (-3).sp, lineHeight = if (story) 96.sp else 74.sp),
            maxLines = 1
        )
        Text(
            text = stringResource(R.string.stats_share_minutes_of_music),
            color = colors.content,
            style = TextStyle(fontSize = if (story) 26.sp else 20.sp, fontWeight = FontWeight.Black)
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            data.deltaPercent?.let { percent ->
                val text = if (percent >= 0) {
                    stringResource(R.string.stats_x_delta_up_percent, percent)
                } else {
                    stringResource(R.string.stats_x_delta_down_percent, -percent)
                }
                Chip("$text ${data.previousLabel}".trim(), colors)
            }
            Chip(stringResource(R.string.stats_share_per_day, formatListeningDurationCompact(data.averageDailyDurationMs)), colors)
        }
        if (story && data.timeline.size > 1) {
            Spacer(Modifier.height(28.dp))
            val max = data.timeline.maxOf { it.totalDurationMs }.coerceAtLeast(1L)
            Row(
                modifier = Modifier.fillMaxWidth().height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                data.timeline.forEach { entry ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight((entry.totalDurationMs.toFloat() / max).coerceAtLeast(0.03f))
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.content)
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Footer(colors)
    }
}

private fun hms(ms: Long): String {
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
}

@Composable
private fun DashedRule() {
    Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = ReceiptInk,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
        )
    }
}

@Composable
private fun ReceiptCard(data: StatsShareData, story: Boolean, showPlays: Boolean) {
    val mono = TextStyle(fontFamily = FontFamily.Monospace, color = ReceiptInk, fontSize = 11.sp)
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = if (story) 30.dp else 16.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .rotate(-1.2f)
                .fillMaxWidth()
                .background(ReceiptPaper)
                .padding(horizontal = 18.dp, vertical = if (story) 20.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name).uppercase(Locale.getDefault()),
                style = mono.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.stats_share_receipt_header, data.periodLabel).uppercase(Locale.getDefault()),
                style = mono.copy(fontSize = 10.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            DashedRule()
            Row {
                Text(stringResource(R.string.stats_share_receipt_qty), style = mono.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(28.dp))
                Text(stringResource(R.string.stats_share_receipt_item), style = mono.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Text(stringResource(R.string.stats_share_receipt_time), style = mono.copy(fontWeight = FontWeight.Bold))
            }
            DashedRule()
            data.topSongs.take(if (story) 10 else 4).forEach { song ->
                Row {
                    Text(
                        text = if (showPlays) String.format(Locale.US, "%02d", song.playCount) else "--",
                        style = mono,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        text = "${song.title} – ${song.artist}".uppercase(Locale.getDefault()),
                        style = mono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Text(hms(song.totalDurationMs), style = mono)
                }
            }
            DashedRule()
            ReceiptLine(stringResource(R.string.stats_share_receipt_songs), numberFormat.format(data.uniqueSongs), mono)
            ReceiptLine(stringResource(R.string.stats_share_receipt_artists), numberFormat.format(data.uniqueArtists), mono)
            ReceiptLine(stringResource(R.string.stats_share_receipt_total), hms(data.totalDurationMs), mono.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            if (story) {
                Spacer(Modifier.height(8.dp))
                DashedRule()
                Spacer(Modifier.height(8.dp))
                Barcode(seed = data.periodLabel.hashCode() xor data.totalPlays)
                Text(
                    text = stringResource(R.string.stats_share_receipt_thanks).uppercase(Locale.getDefault()),
                    style = mono.copy(fontSize = 10.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ReceiptLine(label: String, value: String, style: TextStyle) {
    Row {
        Text(label.uppercase(Locale.getDefault()), style = style, modifier = Modifier.weight(1f))
        Text(value, style = style)
    }
}

@Composable
private fun Barcode(seed: Int) {
    Canvas(modifier = Modifier.fillMaxWidth().height(34.dp)) {
        val random = java.util.Random(seed.toLong())
        var x = size.width * 0.12f
        val end = size.width * 0.88f
        while (x < end) {
            val bar = (1 + random.nextInt(3)) * 1.6f
            drawRect(color = ReceiptInk, topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(bar, size.height))
            x += bar + (1 + random.nextInt(2)) * 1.6f
        }
    }
}

@Composable
private fun ClockCard(data: StatsShareData, colors: ShareCardColors, story: Boolean) {
    CardColumn {
        Kicker(stringResource(R.string.stats_share_kicker_clock), colors.highlight)
        Text(
            text = data.listenerTitle ?: stringResource(R.string.stats_x_clock_title),
            color = colors.content,
            style = TextStyle(fontSize = if (story) 40.sp else 28.sp, fontWeight = FontWeight.Black, lineHeight = if (story) 42.sp else 30.sp)
        )
        Spacer(Modifier.height(if (story) 22.dp else 8.dp))
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(if (story) 290.dp else 190.dp),
            contentAlignment = Alignment.Center
        ) {
            ListeningClockDial(
                hourly = data.hourly,
                peakHour = data.hourly.indices.maxByOrNull { data.hourly[it] },
                barColor = colors.accent.copy(alpha = 0.75f),
                peakColor = colors.highlight,
                trackColor = colors.chip,
                barWidth = if (story) 10.dp else 7.dp,
                modifier = Modifier.fillMaxSize()
            )
            data.peakHourLabel?.let { peak ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(peak, color = colors.content, style = TextStyle(fontSize = if (story) 24.sp else 16.sp, fontWeight = FontWeight.Black))
                    Text(stringResource(R.string.stats_x_clock_peak), color = colors.muted, style = TextStyle(fontSize = 11.sp))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (story) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ClockTile(stringResource(R.string.stats_x_percent, data.eveningPercent), stringResource(R.string.stats_share_after_dark), colors, Modifier.weight(1f))
                data.busiestDay?.let { ClockTile(it, stringResource(R.string.stats_share_busiest_day), colors, Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(18.dp))
        }
        Footer(colors, trailing = data.periodLabel)
    }
}

@Composable
private fun ClockTile(value: String, label: String, colors: ShareCardColors, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.chip)
            .padding(12.dp)
    ) {
        Text(value, color = colors.content, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = colors.muted, style = TextStyle(fontSize = 12.sp))
    }
}

@Composable
private fun AlbumGridCard(data: StatsShareData, colors: ShareCardColors, story: Boolean) {
    val albums = data.albums.take(if (story) 9 else 6)
    CardColumn {
        Kicker(stringResource(R.string.stats_share_kicker_albums, albums.size), colors.content)
        Text(data.periodLabel, color = colors.content, style = TextStyle(fontSize = if (story) 36.sp else 24.sp, fontWeight = FontWeight.Black))
        Spacer(Modifier.height(if (story) 24.dp else 10.dp))
        albums.chunked(3).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                for (column in 0 until 3) {
                    val album = row.getOrNull(column)
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                        if (album != null) {
                            SmartImage(
                                model = album.albumArtUri,
                                contentDescription = null,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxSize()
                            )
                            RankBadge(rowIndex * 3 + column + 1)
                        }
                    }
                }
            }
        }
        if (story) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = albums.take(3).mapIndexed { index, album -> "${index + 1} ${album.album}" }.joinToString("  ·  "),
                color = colors.muted,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.weight(1f))
        Footer(colors)
    }
}

@Composable
private fun BoxScope.RankBadge(rank: Int) {
    Text(
        text = rank.toString(),
        color = Color.White,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black),
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(5.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

@Composable
private fun SpotlightCard(data: StatsShareData, colors: ShareCardColors, story: Boolean, showPlays: Boolean) {
    val artist = data.topArtist ?: return
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(if (story) 280.dp else 130.dp)) {
            SmartImage(
                model = artist.albumArtUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .padding(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.background.copy(alpha = 0.8f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Kicker(stringResource(R.string.stats_share_kicker_spotlight), colors.content)
            }
        }
        Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 26.dp, vertical = if (story) 22.dp else 12.dp)) {
            Text(artist.artist, color = colors.content, style = TextStyle(fontSize = if (story) 40.sp else 28.sp, fontWeight = FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val stats = buildList {
                add(formatListeningDurationCompact(artist.totalDurationMs))
                if (showPlays) add(stringResource(R.string.stats_share_n_plays, numberFormat.format(artist.playCount)))
                add(stringResource(R.string.stats_share_n_songs, artist.uniqueSongs))
            }.joinToString(" · ")
            Text(stats, color = colors.muted, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            if (story && data.topArtistSongs.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Kicker(stringResource(R.string.stats_share_most_played), colors.muted)
                data.topArtistSongs.take(3).forEachIndexed { index, song ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        Text((index + 1).toString(), color = colors.content, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black), modifier = Modifier.width(24.dp))
                        Text(song.title, color = colors.content, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (showPlays) Text(stringResource(R.string.stats_share_times, song.playCount), color = colors.muted, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Footer(colors, trailing = data.periodLabel)
        }
    }
}

@Composable
private fun SummaryCard(data: StatsShareData, colors: ShareCardColors, story: Boolean) {
    val count = if (story) 5 else 3
    CardColumn {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Art(data.leadArtUri, if (story) 110.dp else 64.dp, RoundedCornerShape(22.dp))
            Column {
                Kicker(data.periodLabel, colors.muted)
                Text(stringResource(R.string.stats_share_in_music), color = colors.content, style = TextStyle(fontSize = if (story) 30.sp else 22.sp, fontWeight = FontWeight.Black))
            }
        }
        Spacer(Modifier.height(if (story) 26.dp else 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SummaryList(stringResource(R.string.stats_share_top_artists), data.topArtists.take(count).map { it.artist }, colors, Modifier.weight(1f))
            SummaryList(stringResource(R.string.stats_share_top_songs), data.topSongs.take(count).map { it.title }, colors, Modifier.weight(1f))
        }
        Spacer(Modifier.height(if (story) 22.dp else 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Kicker(stringResource(R.string.stats_share_minutes), colors.muted)
                Text(numberFormat.format(TimeUnit.MILLISECONDS.toMinutes(data.totalDurationMs)), color = colors.content, style = TextStyle(fontSize = if (story) 32.sp else 24.sp, fontWeight = FontWeight.Black))
            }
            Column(modifier = Modifier.weight(1f)) {
                Kicker(stringResource(R.string.stats_share_top_genre), colors.muted)
                Text(data.topGenre ?: stringResource(R.string.stats_em_dash), color = colors.content, style = TextStyle(fontSize = if (story) 24.sp else 18.sp, fontWeight = FontWeight.Black), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.weight(1f))
        Footer(colors)
    }
}

@Composable
private fun SummaryList(title: String, items: List<String>, colors: ShareCardColors, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Kicker(title, colors.muted)
        items.forEachIndexed { index, item ->
            Text(
                text = "${index + 1} $item",
                color = colors.content,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
