package com.theveloper.pixelplay.presentation.screens.events

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.ExpressiveFilterChip
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.navigation.popBackStackSafely
import com.theveloper.pixelplay.presentation.viewmodel.LiveEvent
import com.theveloper.pixelplay.presentation.viewmodel.LiveEventsViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Upcoming shows for the artists you play most, by date and grouped by month. Chips filter to
 * one artist. Tap a show for tickets; tap the artist's picture for their page.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiveEventsScreen(
    navController: NavController,
    viewModel: LiveEventsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var artistFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val artists = remember(state.events) { state.events.map { it.artistName }.distinct() }
    val shown = remember(state.events, artistFilter) {
        state.events.filter { artistFilter == null || it.artistName == artistFilter }
    }
    val monthFormat = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    val groups = remember(shown) {
        shown.groupBy { it.show.localDate?.withDayOfMonth(1) }.entries.toList()
    }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = { navController.popBackStackSafely() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.live_events_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    stringResource(R.string.live_events_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedContent(
            targetState = when {
                state.loading -> 0
                state.notConfigured || state.offline || state.noArtists || state.events.isEmpty() -> 1
                else -> 2
            },
            transitionSpec = {
                fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                    fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
            },
            modifier = Modifier.fillMaxSize(),
            label = "liveEventsState"
        ) { mode ->
            when (mode) {
                0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.live_events_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                1 -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(
                                when {
                                    state.notConfigured -> R.string.live_events_not_configured
                                    state.offline -> R.string.live_events_offline
                                    state.noArtists -> R.string.live_events_no_artists
                                    else -> R.string.live_events_empty
                                }
                            ),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!state.notConfigured && !state.noArtists) {
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(onClick = viewModel::refresh) { Text(stringResource(R.string.live_events_retry)) }
                        }
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = MiniPlayerHeight + navInset + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (artists.size > 1) {
                        item(key = "filters") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                                item(key = "all") {
                                    ExpressiveFilterChip(
                                        label = stringResource(R.string.live_events_all),
                                        selected = artistFilter == null,
                                        onClick = { artistFilter = null }
                                    )
                                }
                                items(artists, key = { it }) { name ->
                                    ExpressiveFilterChip(
                                        label = name,
                                        selected = artistFilter == name,
                                        onClick = { artistFilter = if (artistFilter == name) null else name }
                                    )
                                }
                            }
                        }
                    }
                    groups.forEach { (month, events) ->
                        item(key = "month_${month ?: "unknown"}") {
                            Text(
                                month?.format(monthFormat)?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: "",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp).semantics { heading() }
                            )
                        }
                        items(events, key = { it.artistName + "|" + it.show.id }) { event ->
                            LiveEventRow(
                                event = event,
                                onOpenTickets = {
                                    event.show.ticketUrl?.let { url ->
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }
                                    }
                                },
                                onOpenArtist = { navController.navigateSafely(Screen.ArtistDetail.createRoute(event.artistId)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveEventRow(event: LiveEvent, onOpenTickets: () -> Unit, onOpenArtist: () -> Unit) {
    val date = event.show.localDate
    val shape = AbsoluteSmoothCornerShape(22.dp, 60)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = event.show.ticketUrl != null, onClick = onOpenTickets)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Date block: day number over the short month.
            Column(
                Modifier
                    .size(56.dp)
                    .clip(AbsoluteSmoothCornerShape(16.dp, 60))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    date?.dayOfMonth?.toString() ?: "–",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    date?.month?.getDisplayName(TextStyle.SHORT, Locale.getDefault())?.uppercase(Locale.getDefault()).orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onOpenArtist)) {
                        SmartImage(model = event.artistImageUrl, contentDescription = event.artistName, modifier = Modifier.fillMaxSize(), shape = CircleShape)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        event.artistName,
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(event.show.venue.takeIf { it.isNotBlank() }, event.show.city).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(event.show.time, event.show.region, event.show.country.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (event.show.ticketUrl != null) {
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = stringResource(R.string.live_events_tickets_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
