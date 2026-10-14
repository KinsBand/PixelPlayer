package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The one way collections show how many songs they have: a music note, then the number
 * ("[♪] 42"), optionally followed by " · extra" (a duration, a date, a year, an artist).
 * TalkBack reads "42 songs" (plus the extra) instead of the bare number.
 * While [isLoading], shows "Counting songs…" instead.
 */
@Composable
fun CompactCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    isLoading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    if (isLoading) {
        Text(
            stringResource(R.string.compact_count_counting),
            modifier = modifier,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        return
    }
    val spoken = pluralStringResource(R.plurals.compact_count_songs, count, count) +
        (trailing?.let { ", $it" } ?: "")
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = CompactMeta.join(count.toString(), trailing),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

/** The small "Friends" pill shown when you and someone follow each other. */
@Composable
fun MutualFriendsBadge(
    modifier: Modifier = Modifier,
    /** False = just the group icon (the Friends list rows). */
    showLabel: Boolean = true,
) {
    val label = stringResource(R.string.friends_mutual_badge)
    val spoken = stringResource(R.string.friends_mutual_badge_cd)
    Row(
        modifier = modifier
            .height(18.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = if (showLabel) 6.dp else 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Group,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(11.dp)
        )
        if (showLabel) {
        androidx.compose.foundation.layout.Spacer(Modifier.width(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = GoogleSansRounded),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { }
        )
        }
    }
}

/** Pure formatting for compact metadata (unit-tested in CompactMetaTest). */
object CompactMeta {
    /** Separator between metadata parts: a middle dot with a space either side. */
    const val SEPARATOR = " · "

    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")

    /** "42" or "42 · extra" (blank extras are dropped). */
    fun join(vararg parts: String?): String = parts.filterNot { it.isNullOrBlank() }.joinToString(SEPARATOR)

    /** Compact age with no words: 30m, 5h, 3d, 2mo, 1y. Under a minute = "1m". */
    fun relativeTime(at: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = ((now - at) / 60_000L).coerceAtLeast(1L)
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 60 -> "${minutes}m"
            hours < 24 -> "${hours}h"
            days < 30 -> "${days}d"
            days < 365 -> "${days / 30}mo"
            else -> "${days / 365}y"
        }
    }

    /** Total length as "1h 59m", "2h" or "45m" (no words). */
    fun duration(ms: Long): String {
        val totalMinutes = (ms / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

    /** dd/MM/yy in [zone], or null when [epochMs] isn't a real time (0 or negative). */
    fun dateLabel(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
        if (epochMs <= 0L) return null
        return Instant.ofEpochMilli(epochMs).atZone(zone).format(dateFormat)
    }

    /**
     * MediaStore reports album "date added" in seconds; playlists use milliseconds. Values
     * that are clearly seconds (before year 2286 as ms would be ~1e10) are scaled up.
     */
    fun epochMsFromMaybeSeconds(value: Long): Long = if (value in 1 until 10_000_000_000L) value * 1000 else value

    /** The release year as text, or null when unknown (0 or negative). */
    fun yearLabel(year: Int): String? = year.takeIf { it > 0 }?.toString()
}
