package com.theveloper.pixelplay.presentation.screens.radio

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.size.Size
import com.theveloper.pixelplay.data.radio.RadioGeo
import com.theveloper.pixelplay.data.radio.RadioMapSpot
import com.theveloper.pixelplay.data.radio.RadioStation
import com.theveloper.pixelplay.data.radio.RadioStationCache
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.RadioUiState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.roundToInt

private val PinShape = RoundedCornerShape(14.dp)
private val PinInnerShape = RoundedCornerShape(11.dp)

/** At most this many pins are composed at once (the most popular ones on screen). */
private const val MAX_VISIBLE_PINS = 220

/**
 * A real, pannable map (OpenFreeMap tiles of OpenStreetMap data, drawn by MapLibre; no API key)
 * with a pin for every station that lists its location. Pins are the station's logo; crowded
 * pins group into a cluster showing the biggest station and a count. Tap a cluster to zoom in,
 * tap a pin to see its stations in the tray below and play one.
 */
@Composable
fun RadioMap(
    controller: RadioMapController,
    state: RadioUiState,
    playingUuid: String?,
    isPlaying: Boolean,
    bottomPadding: Dp,
    /** Extra room under the zoom / recentre buttons (the screen's list/map button sits there). */
    controlsBottomPadding: Dp = 0.dp,
    onPlay: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
    onMore: ((RadioStation) -> Unit)? = null,
) {
    val dark = LocalPixelPlayDarkTheme.current
    val context = LocalContext.current
    val home = state.home
    var mapReady by remember { mutableStateOf(controller.isAttached) }

    LaunchedEffect(playingUuid, isPlaying) {
        controller.playingUuid.value = playingUuid
        controller.isPlaying.value = isPlaying
    }
    LaunchedEffect(state.mapStations, home) { controller.showStations(state.mapStations, home) }

    var selected by remember { mutableStateOf(controller.spot(controller.selectedSpotId.value)) }
    DisposableEffect(controller) {
        controller.onSpotSelected = { selected = it }
        onDispose { controller.onSpotSelected = {} }
    }
    // A refreshed list can drop the selected spot.
    LaunchedEffect(state.mapStations) {
        if (selected != null && controller.spot(selected?.id) == null) selected = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding)
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            val paddingPx = with(LocalDensity.current) { 48.dp.roundToPx() }

            // Each scope frames itself once (when its stations first arrive); after that the
            // camera is yours, even across list ↔ map switches and background refreshes.
            val frameKey = listOf(
                state.scope,
                home?.let { RadioStationCache.keyFor(state.scope, it) },
                state.mapStations.isNotEmpty(),
            )
            LaunchedEffect(frameKey, mapReady) {
                if (!mapReady) return@LaunchedEffect
                if (state.focusRequest != controller.handledFocusRequest) return@LaunchedEffect
                if (controller.framedKey != frameKey) {
                    val animate = controller.framedKey != null
                    controller.framedKey = frameKey
                    controller.frame(state.scope, state.mapStations, home, paddingPx, animate)
                }
            }
            // "Show this station on the map" (from the player): fly there once per request.
            LaunchedEffect(state.focusRequest, mapReady) {
                val station = state.focusStation ?: return@LaunchedEffect
                if (!mapReady || controller.handledFocusRequest == state.focusRequest) return@LaunchedEffect
                controller.handledFocusRequest = state.focusRequest
                controller.framedKey = frameKey
                controller.showStations(state.mapStations, home)
                controller.focusStation(station)
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))) {
                    MapLibreBaseMap(
                        controller = controller,
                        dark = dark,
                        onReady = { mapReady = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                    RadioPinsOverlay(
                        controller = controller,
                        playingUuid = playingUuid,
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            val progressAlpha by animateFloatAsState(
                if (state.refreshing || state.loading) 1f else 0f,
                label = "radioMapLoading"
            )
            if (progressAlpha > 0f) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 28.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = progressAlpha }
                        .clip(CircleShape)
                )
            }

            Text(
                RadioMapTiles.ATTRIBUTION,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                    .clickable {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, RadioMapTiles.ATTRIBUTION_URL.toUri())) }
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 10.dp + controlsBottomPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val buttonColors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
                FilledTonalIconButton(onClick = { controller.zoomBy(1.0) }, colors = buttonColors) {
                    Icon(Icons.Rounded.Add, contentDescription = "Zoom in")
                }
                FilledTonalIconButton(onClick = { controller.zoomBy(-1.0) }, colors = buttonColors) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Zoom out")
                }
                FilledTonalIconButton(
                    onClick = { controller.frame(state.scope, state.mapStations, home, paddingPx, animate = true) },
                    colors = buttonColors
                ) {
                    Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "Show the whole ${state.scope.label.lowercase()} area")
                }
            }

            if (state.mapStations.isEmpty() && !state.loading) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                ) {
                    Text(
                        when {
                            state.scopeStations.isNotEmpty() -> "None of these stations list a location. Try the list."
                            else -> "No stations to show here."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // Tray: the stations at the tapped pin.
        val sel = selected
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (sel == null) {
                Text(
                    "${state.mapStations.size} stations · tap a pin to tune in",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            } else {
                Column(Modifier.padding(top = 6.dp)) {
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            placeName(sel) + " · ${sel.stations.size} station${if (sel.stations.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { controller.clearSelection() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }
                    LazyColumn(
                        Modifier.heightIn(max = 230.dp),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(sel.stations.take(50), key = { it.uuid }) { s ->
                            StationRow(
                                station = s,
                                distanceKm = home?.let { RadioGeo.distanceKm(it, s) },
                                isCurrent = s.uuid == playingUuid,
                                isPlaying = isPlaying,
                                isFavorite = isFavorite(s.uuid),
                                onClick = { onPlay(s) },
                                onToggleFavorite = { onToggleFavorite(s) },
                                onMore = onMore?.let { more -> { more(s) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * MapLibre's MapView inside Compose, following the screen's lifecycle. Rotation and tilt are
 * off (the pin overlay assumes a north-up, flat map). The view is created per visit; the
 * camera is carried over by the controller.
 */
@Composable
private fun MapLibreBaseMap(
    controller: RadioMapController,
    dark: Boolean,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val thresholdPx = with(LocalDensity.current) { 58.dp.toPx() }
    val latestOnReady by rememberUpdatedState(onReady)
    val mapView = remember {
        controller.tiles.ensureInitialized()
        val options = MapLibreMapOptions.createFromAttributes(context, null)
            .textureMode(true)
            .logoEnabled(false)
            .attributionEnabled(false)
            .compassEnabled(false)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
            .minZoomPreference(RadioMapTiles.MIN_ZOOM)
            .maxZoomPreference(RadioMapTiles.MAX_ZOOM)
        controller.savedCamera?.let { options.camera(it) }
        MapView(context, options).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

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
        // Replays START/RESUME if the screen is already showing.
        lifecycle.addObserver(observer)
        mapView.getMapAsync { m ->
            if (disposed) return@getMapAsync
            m.setStyle(Style.Builder().fromUri(controller.tiles.styleUrl(dark)))
            controller.attach(m, thresholdPx)
            map = m
            latestOnReady()
        }
        onDispose {
            disposed = true
            lifecycle.removeObserver(observer)
            controller.detach()
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    // Light / dark base map, following the app theme.
    var styledDark by remember { mutableStateOf(dark) }
    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        if (styledDark != dark) {
            styledDark = dark
            m.setStyle(Style.Builder().fromUri(controller.tiles.styleUrl(dark)))
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * The pins, drawn in Compose over the map. Which pins exist is decided when the map settles
 * (the visible clusters plus a screen of margin); where each one sits is read at layout time
 * from the live camera, so panning moves them every frame without recomposing anything.
 */
@Composable
private fun RadioPinsOverlay(
    controller: RadioMapController,
    playingUuid: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val settled by controller.settled
    val clusters by controller.clusters
    val selectedId by controller.selectedSpotId
    val home by controller.home
    BoxWithConstraints(modifier.clipToBounds()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val visible = remember(settled, clusters, w, h) {
            val t = settled ?: return@remember emptyList()
            clusters.asSequence()
                .filter { c ->
                    val x = t.screenX(c.x)
                    val y = t.screenY(c.y)
                    x in -w..(2 * w) && y in -h..(2 * h)
                }
                .sortedByDescending { c -> c.spots.sumOf { it.totalClicks } }
                .take(MAX_VISIBLE_PINS)
                .toList()
                // Most popular drawn last (on top).
                .reversed()
        }
        visible.forEach { cluster ->
            key(cluster.id) {
                Box(
                    Modifier
                        .mapAnchor(controller, cluster.x, cluster.y, bottom = cluster.isSingle)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClickLabel = if (cluster.isSingle) "Show ${cluster.lead.lead.name}" else "Zoom in",
                        ) { controller.select(cluster) }
                ) {
                    if (cluster.isSingle) {
                        RadioStationPin(
                            spot = cluster.lead,
                            selected = selectedId == cluster.lead.id,
                            playingUuid = playingUuid,
                            isPlaying = isPlaying,
                        )
                    } else {
                        RadioClusterPin(spots = cluster.spots, playingUuid = playingUuid)
                    }
                }
            }
        }
        val hLat = home?.lat
        val hLon = home?.lon
        if (hLat != null && hLon != null) {
            Box(
                Modifier.mapAnchor(
                    controller,
                    com.theveloper.pixelplay.data.radio.RadioMapMath.normX(hLon),
                    com.theveloper.pixelplay.data.radio.RadioMapMath.normY(hLat),
                    bottom = false,
                )
            ) { HomeLocationDot() }
        }
    }
}

/**
 * Places the content at a map position: bottom-centre on it ([bottom], for pins with a pointer)
 * or centred. Reads the live camera during placement only.
 */
private fun Modifier.mapAnchor(controller: RadioMapController, normX: Double, normY: Double, bottom: Boolean): Modifier =
    layout { measurable, _ ->
        val placeable = measurable.measure(Constraints())
        layout(placeable.width, placeable.height) {
            val t = controller.transform.value
            if (t != null) {
                val x = t.screenX(normX).roundToInt() - placeable.width / 2
                val y = t.screenY(normY).roundToInt() - if (bottom) placeable.height else placeable.height / 2
                placeable.place(x, y)
            }
        }
    }

/** One station pin: the station's logo in a rounded card on a little pointer. */
@Composable
internal fun RadioStationPin(
    spot: RadioMapSpot,
    selected: Boolean,
    playingUuid: String?,
    isPlaying: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val hasPlaying = playingUuid != null && spot.stations.any { it.uuid == playingUuid }
    val rim = when {
        hasPlaying -> colors.primary
        selected -> colors.tertiary
        else -> colors.surface
    }
    val grow by animateFloatAsState(if (selected || hasPlaying) 1.18f else 1f, label = "radioPinScale")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            scaleX = grow
            scaleY = grow
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(Modifier.padding(top = 7.dp, end = 7.dp)) {
                StationCover(
                    station = spot.lead,
                    rim = rim,
                    size = 46.dp,
                    overlay = if (hasPlaying) {
                        { PlayingOverlay(isPlaying) }
                    } else null
                )
            }
            if (spot.stations.size > 1) CountBadge(spot.stations.size)
        }
        PinPointer(rim)
    }
}

/** Several pins close together: the biggest station on top of a little stack, with a count. */
@Composable
internal fun RadioClusterPin(spots: List<RadioMapSpot>, playingUuid: String?) {
    val colors = MaterialTheme.colorScheme
    val total = spots.sumOf { it.stations.size }
    val lead = spots.maxByOrNull { it.lead.clickCount }?.lead ?: return
    val hasPlaying = playingUuid != null && spots.any { spot -> spot.stations.any { it.uuid == playingUuid } }
    Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer {
                    rotationZ = -11f
                    translationX = -7.dp.toPx()
                }
                .clip(PinShape)
                .background(colors.primaryContainer)
                .border(2.dp, colors.surface, PinShape)
        )
        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer {
                    rotationZ = 9f
                    translationX = 7.dp.toPx()
                }
                .clip(PinShape)
                .background(colors.tertiaryContainer)
                .border(2.dp, colors.surface, PinShape)
        )
        StationCover(station = lead, rim = if (hasPlaying) colors.primary else colors.surface, size = 50.dp)
        Box(Modifier.align(Alignment.BottomEnd)) {
            CountBadge(total, big = true)
        }
    }
}

/** "You are here". */
@Composable
internal fun HomeLocationDot() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(13.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.primary)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

@Composable
private fun StationCover(
    station: RadioStation,
    rim: Color,
    size: Dp,
    overlay: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size)
            .shadow(6.dp, PinShape)
            .clip(PinShape)
            .background(rim)
            .padding(2.5.dp)
            .clip(PinInnerShape)
            .background(colors.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Radio,
            contentDescription = null,
            tint = colors.onSecondaryContainer,
            modifier = Modifier.size(size * 0.45f)
        )
        if (station.favicon != null) {
            SmartImage(
                model = station.favicon,
                contentDescription = station.name,
                modifier = Modifier.fillMaxSize(),
                shape = PinInnerShape,
                contentScale = ContentScale.Crop,
                targetSize = Size(144, 144),
                placeHolderBackgroundColor = colors.secondaryContainer,
            )
        }
        overlay?.invoke()
    }
}

@Composable
private fun PlayingOverlay(isPlaying: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isPlaying) Icons.Rounded.Equalizer else Icons.Rounded.Radio,
            contentDescription = if (isPlaying) "Playing" else "Paused",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun CountBadge(count: Int, big: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(CircleShape)
            .background(colors.surface)
            .padding(2.dp)
            .clip(CircleShape)
            .background(colors.primary)
            .padding(horizontal = if (big) 7.dp else 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            when {
                count >= 10_000 -> "${count / 1000}k"
                count >= 1000 -> "%.1fk".format(count / 1000f)
                else -> "$count"
            },
            style = if (big) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun PinPointer(color: Color) {
    Canvas(Modifier.size(width = 14.dp, height = 8.dp)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
            close()
        }
        drawPath(path, color)
        drawCircle(color, radius = 1.5.dp.toPx(), center = Offset(size.width / 2f, size.height))
    }
}

private fun placeName(spot: RadioMapSpot): String {
    val first = spot.stations.first()
    val states = spot.stations.mapNotNull { it.shortState }.distinct()
    val countries = spot.stations.mapNotNull { it.country }.distinct()
    return when {
        states.size == 1 -> states.first()
        countries.size == 1 -> countries.first()
        else -> first.lat?.let { lat -> first.lon?.let { lon -> RadioGeo.continentOf(lat, lon) } } ?: "Here"
    }
}
