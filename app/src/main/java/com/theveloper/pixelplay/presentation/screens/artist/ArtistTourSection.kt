package com.theveloper.pixelplay.presentation.screens.artist

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.theveloper.pixelplay.data.tour.ArtistShow
import com.theveloper.pixelplay.data.tour.ArtistTour
import com.theveloper.pixelplay.presentation.components.ShimmerBox
import com.theveloper.pixelplay.presentation.components.SkeletonTextLine
import com.theveloper.pixelplay.presentation.screens.radio.RadioMapTiles
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import com.theveloper.pixelplay.ui.theme.MotionTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/*
 * Tour dates at the top of the artist page: a one-line summary while checking or when there's no
 * tour, and when the artist is touring a card with the countries, the next shows and a map whose
 * pins carry each show's short date, coloured by how soon it is.
 */

/** How soon a show is. Drives the pin and date-tile colours, and the legend. */
enum class TourBucket(val label: String) {
    SOON("Next 2 weeks"),
    MONTHS("Next 3 months"),
    LATER("Later"),
    RECENT("Recent");

    companion object {
        fun of(show: ArtistShow, today: LocalDate = LocalDate.now()): TourBucket {
            val date = show.localDate ?: return LATER
            val days = ChronoUnit.DAYS.between(today, date)
            return when {
                days < 0 -> RECENT
                days <= 14 -> SOON
                days <= 90 -> MONTHS
                else -> LATER
            }
        }
    }
}

private data class BucketColors(val container: Color, val content: Color)

@Composable
private fun TourBucket.colors(): BucketColors {
    val s = MaterialTheme.colorScheme
    return when (this) {
        TourBucket.SOON -> BucketColors(s.primary, s.onPrimary)
        TourBucket.MONTHS -> BucketColors(s.secondaryContainer, s.onSecondaryContainer)
        TourBucket.LATER -> BucketColors(s.surfaceContainerHighest, s.onSurfaceVariant)
        TourBucket.RECENT -> BucketColors(s.outlineVariant.copy(alpha = 0.7f), s.onSurfaceVariant.copy(alpha = 0.8f))
    }
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val dayName = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

private fun ArtistShow.shortDateLabel(): String = localDate?.format(shortDate) ?: date
private fun ArtistShow.place(): String = listOfNotNull(city, region?.takeIf { it != city }, country).distinct().joinToString(", ")

/** Height of the summary row, shared by its skeleton so the swap never moves anything. */
private val TourRowHeight = 64.dp
/** Height of the list / map area in the card; both views take exactly this. */
private val TourBodyHeight = 248.dp
/** Shows listed in the card before "All shows". */
private const val TOUR_CARD_ROWS = 3

/**
 * The tour item on the artist page.
 * - Loading: a summary-row skeleton.
 * - No shows: the same row saying so (no layout change from the skeleton).
 * - Touring: the full card.
 */
@Composable
fun ArtistTourSection(
    loaded: Boolean,
    tour: ArtistTour?,
    artistName: String,
    tiles: RadioMapTiles,
) {
    val shows = tour?.shows.orEmpty()
    val upcoming = tour?.upcoming.orEmpty()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    ArtistSectionReveal(
        ready = loaded,
        skeleton = { TourRowSkeleton() },
    ) {
        if (upcoming.isEmpty()) {
            TourSummaryRow(
                title = "No upcoming shows",
                subtitle = if (shows.isNotEmpty()) "Last played ${shows.last().city}, ${shows.last().shortDateLabel()}" else "Checked just now",
                icon = { Icon(Icons.Rounded.EventBusy, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = if (shows.isNotEmpty()) ({ sheetOpen = true }) else null
            )
        } else {
            TourCard(tour = tour!!, onOpenAll = { sheetOpen = true }, tiles = tiles)
        }
    }
    if (sheetOpen && tour != null) {
        TourSheet(tour = tour, artistName = artistName, tiles = tiles, onDismiss = { sheetOpen = false })
    }
}

@Composable
internal fun TourRowSkeleton() {
    Row(
        Modifier
            .fillMaxWidth()
            .height(TourRowHeight)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ShimmerBox(Modifier.size(24.dp).clip(CircleShape))
        Column(Modifier.weight(1f)) {
            SkeletonTextLine(MaterialTheme.typography.titleSmall, fraction = 0.45f)
            SkeletonTextLine(MaterialTheme.typography.bodySmall, fraction = 0.7f)
        }
    }
}

@Composable
private fun TourSummaryRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(TourRowHeight)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, onClickLabel = "Show past shows") else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class TourView { LIST, MAP }

@Composable
private fun TourCard(tour: ArtistTour, onOpenAll: () -> Unit, tiles: RadioMapTiles) {
    val upcoming = tour.upcoming
    val countries = tour.countries
    var country by rememberSaveable { mutableStateOf<String?>(null) }
    var view by rememberSaveable { mutableStateOf(TourView.LIST) }
    val filtered = if (country == null) upcoming else upcoming.filter { it.country == country }
    val next = upcoming.first()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    "On tour",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${upcoming.size} show${if (upcoming.size == 1) "" else "s"} · ${countries.size} countr${if (countries.size == 1) "y" else "ies"} · next ${next.shortDateLabel()}, ${next.city}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onOpenAll) {
                Icon(Icons.Rounded.OpenInFull, contentDescription = "All shows")
            }
        }

        // Countries, most shows first. Tapping one narrows the list and the map.
        if (countries.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "all") {
                    FilterChip(selected = country == null, onClick = { country = null }, label = { Text("All") })
                }
                items(countries, key = { it.first }) { (name, count) ->
                    FilterChip(
                        selected = country == name,
                        onClick = { country = if (country == name) null else name },
                        label = { Text("$name · $count") }
                    )
                }
            }
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            TourView.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = view == option,
                    onClick = { view = option },
                    icon = {},
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TourView.entries.size)
                ) { Text(if (option == TourView.LIST) "List" else "Map") }
            }
        }

        // List ↔ Map: fade through inside a fixed height, so switching never moves the page.
        Box(Modifier.fillMaxWidth().height(TourBodyHeight).padding(horizontal = 16.dp)) {
            AnimatedContent(
                targetState = view,
                transitionSpec = {
                    fadeIn(tween(MotionTokens.DurationShort4, delayMillis = MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                        fadeOut(tween(MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedAccelerate))
                },
                label = "tourView"
            ) { current ->
                when (current) {
                    TourView.LIST -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filtered.take(TOUR_CARD_ROWS).forEach { show -> TourShowRow(show) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onOpenAll, modifier = Modifier.align(Alignment.End)) {
                            Text("All ${filtered.size} shows")
                        }
                    }
                    TourView.MAP -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                        ) {
                            // Preview only: pans and zooms happen in the full map, so the page
                            // keeps scrolling normally over it.
                            TourMap(shows = filtered, tiles = tiles, interactive = false, modifier = Modifier.fillMaxSize())
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clickable(onClickLabel = "Open the tour map", onClick = onOpenAll)
                            )
                        }
                        TourLegend()
                    }
                }
            }
        }
    }
}

/** One show: a date tile coloured by how soon it is, then the city and venue. */
@Composable
private fun TourShowRow(show: ArtistShow, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val colors = TourBucket.of(show).colors()
    val open = onClick ?: show.ticketUrl?.let { url ->
        { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }; Unit }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .then(if (open != null) Modifier.clickable(onClick = open, onClickLabel = "Tickets") else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = "${show.localDate?.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())) ?: show.date}, ${show.venue}, ${show.place()}"
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(colors.container),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                show.localDate?.dayOfMonth?.toString() ?: "–",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
                color = colors.content
            )
            Text(
                show.localDate?.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())).orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.content
            )
        }
        Column(Modifier.weight(1f)) {
            Text(show.place(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(show.localDate?.format(dayName), show.time, show.venue.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** What the pin colours mean. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun TourLegend() {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TourBucket.entries.forEach { bucket ->
            val c = bucket.colors()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.container)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                Text(bucket.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

// ---- Full tour sheet ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TourSheet(tour: ArtistTour, artistName: String, tiles: RadioMapTiles, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = com.theveloper.pixelplay.presentation.components.rememberAnimatedSheetDismiss(sheetState, onDismiss)
    var view by rememberSaveable { mutableStateOf(TourView.LIST) }
    var selected by remember { mutableStateOf<ArtistShow?>(null) }
    val shows = tour.shows
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(Modifier.fillMaxWidth().height(screenHeight * 0.85f)) {
            Row(Modifier.padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$artistName on tour", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold))
                    Text(
                        "${tour.upcoming.size} upcoming · ${tour.countries.size} countr${if (tour.countries.size == 1) "y" else "ies"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = close) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                TourView.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = view == option,
                        onClick = { view = option },
                        icon = {},
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = TourView.entries.size)
                    ) { Text(if (option == TourView.LIST) "List" else "Map") }
                }
            }
            AnimatedContent(
                targetState = view,
                transitionSpec = {
                    fadeIn(tween(MotionTokens.DurationShort4, delayMillis = MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                        fadeOut(tween(MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedAccelerate))
                },
                modifier = Modifier.weight(1f),
                label = "tourSheetView"
            ) { current ->
                when (current) {
                    TourView.LIST -> LazyColumn(
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        shows.groupBy { it.localDate?.withDayOfMonth(1) }.forEach { (month, inMonth) ->
                            item(key = "m_${month ?: "none"}") {
                                Text(
                                    month?.format(monthYear) ?: "Date to be announced",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                )
                            }
                            items(inMonth, key = { it.id }) { show -> TourShowRow(show) }
                        }
                    }
                    TourView.MAP -> Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.large)) {
                            TourMap(
                                shows = shows,
                                tiles = tiles,
                                interactive = true,
                                selectedId = selected?.id,
                                onPinClick = { selected = it },
                                modifier = Modifier.fillMaxSize()
                            )
                            Text(
                                RadioMapTiles.ATTRIBUTION,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        TourLegend()
                        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
                                val sel = selected
                                if (sel == null) {
                                    Text("Tap a date on the map", style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    TourShowRow(sel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Map ---------------------------------------------------------------------------------------

/** Shows at one place (same city on the map), soonest first. */
private data class TourStop(val key: String, val lat: Double, val lon: Double, val shows: List<ArtistShow>) {
    /** The date the pin shows: the next upcoming one, else the most recent past one. */
    val lead: ArtistShow
        get() {
            val today = LocalDate.now()
            return shows.firstOrNull { it.localDate?.isBefore(today) == false } ?: shows.last()
        }
}

private fun stopsFor(shows: List<ArtistShow>): List<TourStop> =
    shows.filter { it.latitude != null && it.longitude != null }
        .groupBy { "${(it.latitude!! * 20).roundToInt()}_${(it.longitude!! * 20).roundToInt()}" }
        .map { (key, here) -> TourStop(key, here.first().latitude!!, here.first().longitude!!, here.sortedBy { it.date }) }

/**
 * MapLibre base map (the radio map's OpenFreeMap tiles, light or dark with the app) with a
 * Compose pin per stop. Pins are placed from the live camera at layout time, so panning moves them
 * every frame without recomposing. [interactive] = false makes a still preview.
 */
@Composable
internal fun TourMap(
    shows: List<ArtistShow>,
    tiles: RadioMapTiles,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    onPinClick: (ArtistShow) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dark = LocalPixelPlayDarkTheme.current
    val density = LocalDensity.current
    val stops = remember(shows) { stopsFor(shows) }
    val cameraTick = remember { mutableIntStateOf(0) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    val mapView = remember {
        tiles.ensureInitialized()
        val options = MapLibreMapOptions.createFromAttributes(context, null)
            .textureMode(true)
            .logoEnabled(false)
            .attributionEnabled(false)
            .compassEnabled(false)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
            .scrollGesturesEnabled(interactive)
            .zoomGesturesEnabled(interactive)
            .doubleTapGesturesEnabled(interactive)
            .minZoomPreference(RadioMapTiles.MIN_ZOOM)
            .maxZoomPreference(RadioMapTiles.MAX_ZOOM)
        MapView(context, options).apply { onCreate(null) }
    }

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        var disposed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.getMapAsync { m ->
            if (disposed) return@getMapAsync
            m.setStyle(Style.Builder().fromUri(tiles.styleUrl(dark)))
            m.addOnCameraMoveListener { cameraTick.intValue++ }
            m.addOnCameraIdleListener { cameraTick.intValue++ }
            map = m
        }
        onDispose {
            disposed = true
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Frame every stop (again when the country filter changes the set).
    val paddingPx = with(density) { 40.dp.roundToPx() }
    LaunchedEffect(map, stops) {
        val m = map ?: return@LaunchedEffect
        mapView.post {
            when {
                stops.isEmpty() -> Unit
                stops.size == 1 -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(stops[0].lat, stops[0].lon), 5.0))
                else -> {
                    val bounds = LatLngBounds.Builder().apply { stops.forEach { include(LatLng(it.lat, it.lon)) } }.build()
                    runCatching { m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)) }
                }
            }
            cameraTick.intValue++
        }
    }

    Box(modifier.clipToBounds()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Soonest drawn last, so it sits on top where pins overlap.
            stops.sortedByDescending { it.lead.date }.forEach { stop ->
                key(stop.key) {
                    val lead = stop.lead
                    Box(
                        Modifier
                            .layout { measurable, _ ->
                                val placeable = measurable.measure(Constraints())
                                layout(placeable.width, placeable.height) {
                                    cameraTick.intValue // read: re-place on every camera move
                                    val p = map?.projection?.toScreenLocation(LatLng(stop.lat, stop.lon))
                                    if (p != null) placeable.place(p.x.roundToInt() - placeable.width / 2, p.y.roundToInt() - placeable.height)
                                }
                            }
                            .then(
                                if (interactive) Modifier.clickable(onClickLabel = "Show ${lead.city}") { onPinClick(lead) }
                                else Modifier.clearAndSetSemantics { }
                            )
                            .padding(6.dp)
                    ) {
                        TourPin(stop = stop, selected = stop.shows.any { it.id == selectedId })
                    }
                }
            }
        }
    }
}

/** A pin: the lead show's short date in a pill coloured by how soon it is, "+n" for more nights. */
@Composable
private fun TourPin(stop: TourStop, selected: Boolean) {
    val lead = stop.lead
    val colors = TourBucket.of(lead).colors()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(colors.container)
                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(lead.shortDateLabel(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.content, maxLines = 1)
            if (stop.shows.size > 1) {
                Text("+${stop.shows.size - 1}", style = MaterialTheme.typography.labelSmall, color = colors.content.copy(alpha = 0.8f))
            }
        }
        // Pointer.
        Box(Modifier.size(width = 2.dp, height = 6.dp).background(colors.container))
        Box(Modifier.size(6.dp).clip(CircleShape).background(colors.container))
    }
}
