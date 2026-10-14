package com.theveloper.pixelplay.presentation.screens.artist

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.theveloper.pixelplay.presentation.components.ExpressiveFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.spotify.web.SpotifyMerchItem
import com.theveloper.pixelplay.data.tour.ArtistShow
import com.theveloper.pixelplay.presentation.components.ConnectedTabGroup
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.screens.radio.RadioMapTiles
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The artist page's tabs. Events and Merch only exist when the artist has some. */
enum class ArtistTab { MUSIC, EVENTS, MERCH, SOCIALS }

/** Height of the artist page's compact tab chips (docked under the top bar when scrolled). */
val ArtistTabRowHeight = 40.dp

/** List or map in the Events tab. */
enum class EventsView { LIST, MAP }

/**
 * Music · Events · Merch as a connected button group (the same look as the friend sheet's tabs).
 * The catalogue status lives in the stats card above, so the tabs get the full width.
 */
@Composable
fun ArtistTabRow(
    tabs: List<ArtistTab>,
    selected: ArtistTab,
    eventCount: Int,
    merchCount: Int,
    onSelect: (ArtistTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = tabs.map { tab ->
        when (tab) {
            ArtistTab.MUSIC -> stringResource(R.string.artist_tab_music)
            ArtistTab.EVENTS -> stringResource(R.string.artist_tab_events) + " $eventCount"
            ArtistTab.MERCH -> stringResource(R.string.artist_tab_merch) + if (merchCount > 0) " $merchCount" else ""
            ArtistTab.SOCIALS -> stringResource(R.string.artist_tab_socials)
        }
    }
    ConnectedTabGroup(
        labels = labels,
        selectedIndex = tabs.indexOf(selected).coerceAtLeast(0),
        onSelect = { onSelect(tabs[it]) },
        modifier = modifier,
        height = ArtistTabRowHeight,
    )
}

/** List · Map toggle, then the country chips (tap one to narrow both). */
@Composable
fun EventsControls(
    view: EventsView,
    countries: List<Pair<String, Int>>,
    country: String?,
    onView: (EventsView) -> Unit,
    onCountry: (String?) -> Unit,
) {
    // The map is always on the page now (compact, expands in place), so there's no List · Map
    // toggle any more: only the country chips. [view] / [onView] stay for callers.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (countries.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                item(key = "all") {
                    ExpressiveFilterChip(
                        label = stringResource(R.string.artist_events_all),
                        selected = country == null,
                        onClick = { onCountry(null) }
                    )
                }
                items(countries, key = { it.first }) { (name, count) ->
                    ExpressiveFilterChip(
                        label = "$name · $count",
                        selected = country == name,
                        onClick = { onCountry(if (country == name) null else name) }
                    )
                }
            }
        }
    }
}

private val eventMonth = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
private val eventDay = DateTimeFormatter.ofPattern("d", Locale.getDefault())
private val eventWeekday = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
private val eventTimeIn = DateTimeFormatter.ofPattern("HH:mm")
private val eventTimeOut = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

/**
 * One show, like Spotify's event list but without the add button: a date block (month over day),
 * the city, then "Tue, 8:00 pm • Venue". Tap opens its tickets / event page.
 */
@Composable
fun ArtistEventRow(show: ArtistShow, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val date = show.localDate
    val month = date?.format(eventMonth) ?: show.date.take(7)
    val day = date?.format(eventDay) ?: ""
    val time = show.time?.let { t ->
        runCatching { java.time.LocalTime.parse(t, eventTimeIn).format(eventTimeOut).lowercase(Locale.getDefault()) }.getOrNull()
    }
    val detail = listOfNotNull(
        listOfNotNull(date?.format(eventWeekday), time).joinToString(", ").ifBlank { null },
        show.venue.takeIf { it.isNotBlank() }
    ).joinToString(" • ")
    val spoken = stringResource(R.string.artist_event_row_cd, listOfNotNull(date?.toString(), time).joinToString(" "), show.city, show.venue)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = show.ticketUrl != null, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            // One spoken line for the row; the click stays available to TalkBack.
            .clearAndSetSemantics {
                contentDescription = spoken
                if (show.ticketUrl != null) {
                    role = Role.Button
                    onClick { onClick(); true }
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(month, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(day, style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                show.city,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (show.cancelled) TextDecoration.LineThrough else null
            )
            Text(
                if (show.cancelled) stringResource(R.string.artist_event_cancelled) else detail,
                style = MaterialTheme.typography.bodyMedium,
                color = if (show.cancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The Events map, right on the page: a compact still map (the page scrolls over it). Tapping it
 * grows it in place into a taller, pannable and zoomable map (no new screen); the collapse
 * button shrinks it back. The selected show's pin is highlighted; attribution sits under it.
 */
@Composable
fun EventsMapCard(
    shows: List<ArtistShow>,
    tiles: RadioMapTiles,
    selectedId: String?,
    withoutLocation: Int,
    @Suppress("UNUSED_PARAMETER") onExpand: () -> Unit = {},
    onPinClick: (ArtistShow) -> Unit = {},
) {
    val context = LocalContext.current
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val reduced = com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion()
    val mapHeight by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (expanded) 460.dp else 200.dp,
        animationSpec = if (reduced) androidx.compose.animation.core.snap()
            else androidx.compose.animation.core.tween(
                com.theveloper.pixelplay.ui.theme.MotionTokens.DurationMedium4,
                easing = com.theveloper.pixelplay.ui.theme.MotionTokens.Emphasized
            ),
        label = "eventsMapHeight"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(mapHeight)
                .clip(MaterialTheme.shapes.large)
        ) {
            TourMap(
                shows = shows,
                tiles = tiles,
                interactive = expanded,
                modifier = Modifier.fillMaxSize(),
                selectedId = selectedId,
                onPinClick = onPinClick
            )
            if (!expanded) {
                // Still map: one tap grows it into the interactive one.
                Box(Modifier.fillMaxSize().clickable(onClickLabel = stringResource(R.string.artist_events_open_map)) { expanded = true })
            }
            androidx.compose.material3.FilledTonalIconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
            ) {
                Icon(
                    if (expanded) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                    contentDescription = stringResource(if (expanded) R.string.artist_events_close_map else R.string.artist_events_open_map)
                )
            }
        }
        TourLegend()
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (withoutLocation > 0) {
                Text(
                    stringResource(R.string.artist_events_no_location, withoutLocation),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                RadioMapTiles.ATTRIBUTION,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, RadioMapTiles.ATTRIBUTION_URL.toUri())) }
                    }
                    .padding(4.dp)
            )
        }
    }
}

/** Merch in two columns, like the artist's Spotify shop shelf. Tap opens the item in the browser. */
@Composable
fun MerchPair(
    items: List<SpotifyMerchItem>,
    onOpen: (SpotifyMerchItem) -> Unit,
    /** How a price is shown (the artist page converts to "$X AUD"); default = as the store gives it. */
    formatPrice: (String?) -> String? = { it },
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { item -> MerchCard(item, onOpen = { onOpen(item) }, formatPrice = formatPrice, modifier = Modifier.weight(1f)) }
        if (items.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun MerchCard(
    item: SpotifyMerchItem,
    onOpen: () -> Unit,
    formatPrice: (String?) -> String?,
    modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.artist_merch_open_cd, item.name)
    Column(
        modifier
            .clip(AbsoluteSmoothCornerShape(20.dp, 60))
            .clickable(onClickLabel = label, onClick = onOpen)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(AbsoluteSmoothCornerShape(20.dp, 60))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            SmartImage(model = item.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Text(
            item.name,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        val sub = listOfNotNull(formatPrice(item.price), item.description).joinToString(" · ")
        if (sub.isNotBlank()) {
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/** Opens a web link (tickets, merch) in the browser; ignores a phone without one. */
fun openWebLink(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

