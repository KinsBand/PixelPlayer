package com.theveloper.pixelplay.presentation.screens.radio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.radio.MapCluster
import com.theveloper.pixelplay.data.radio.RadioGeo
import com.theveloper.pixelplay.data.radio.RadioMapMath
import com.theveloper.pixelplay.data.radio.RadioScope
import com.theveloper.pixelplay.data.radio.RadioStation
import com.theveloper.pixelplay.presentation.viewmodel.RadioUiState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Most zoomed out: the whole world fits the width. Most zoomed in: a few km across. */
private const val MAX_PX_PER_DEGREE = 6000f
private const val CLUSTER_CELL_DP = 44f

/**
 * Stations plotted by their coordinates (plate carrée: longitude across, latitude up).
 * Dots cluster into counts when they crowd; tap a cluster to zoom in, tap a dot to see
 * its stations in the tray below and play one.
 */
@Composable
fun RadioMap(
    state: RadioUiState,
    playingUuid: String?,
    isPlaying: Boolean,
    bottomPadding: Dp,
    onPlay: (RadioStation) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    isFavorite: (String) -> Boolean,
) {
    val stations = state.mapStations
    val home = state.home
    val density = LocalDensity.current
    val cellPx = with(density) { CLUSTER_CELL_DP.dp.toPx() }
    val measurer = rememberTextMeasurer()

    val water = MaterialTheme.colorScheme.surfaceContainerLow
    val grid = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.tertiary
    val cluster = MaterialTheme.colorScheme.primary
    val onCluster = MaterialTheme.colorScheme.onPrimary
    val playingColor = MaterialTheme.colorScheme.error
    val homeColor = MaterialTheme.colorScheme.secondary
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = onCluster, fontWeight = FontWeight.Bold)
    val gridLabelStyle = MaterialTheme.typography.labelSmall.copy(color = grid)

    var selected by remember(stations) { mutableStateOf<MapCluster?>(null) }

    Column(Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val minScale = widthPx / 360f

            // Viewport: map centre in degrees and zoom in px per degree.
            var centerLon by remember { mutableFloatStateOf(0f) }
            var centerLat by remember { mutableFloatStateOf(20f) }
            var scale by remember { mutableFloatStateOf(minScale) }

            fun fit() {
                val v = initialViewport(state.scope, stations, home, widthPx, heightPx)
                centerLon = v.first
                centerLat = v.second
                scale = v.third.coerceIn(minScale, MAX_PX_PER_DEGREE)
            }
            // Scope ↔ zoom: every scope change (or new data) frames the map on that scope.
            LaunchedEffect(state.scope, stations, home, widthPx, heightPx) {
                if (widthPx > 0 && heightPx > 0) fit()
            }

            fun toScreen(lat: Double, lon: Double): Offset = Offset(
                widthPx / 2f + (lon.toFloat() - centerLon) * scale,
                heightPx / 2f - (lat.toFloat() - centerLat) * scale,
            )

            val clusters = remember(stations, centerLon, centerLat, scale, widthPx, heightPx) {
                val margin = cellPx
                RadioMapMath.cluster(stations, cellPx) { s ->
                    val p = toScreen(s.lat!!, s.lon!!)
                    if (p.x < -margin || p.x > widthPx + margin || p.y < -margin || p.y > heightPx + margin) null
                    else p.x to p.y
                }
            }
            val currentClusters by rememberUpdatedState(clusters)

            Surface(shape = RoundedCornerShape(24.dp), color = water, modifier = Modifier.fillMaxSize()) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(minScale) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(minScale, MAX_PX_PER_DEGREE)
                                // Keep the point under the fingers still while zooming.
                                val fx = centerLon + (centroid.x - widthPx / 2f) / scale
                                val fy = centerLat - (centroid.y - heightPx / 2f) / scale
                                centerLon = (fx - (centroid.x - widthPx / 2f) / newScale - pan.x / newScale).coerceIn(-180f, 180f)
                                centerLat = (fy + (centroid.y - heightPx / 2f) / newScale + pan.y / newScale).coerceIn(-85f, 85f)
                                scale = newScale
                            }
                        }
                        .pointerInput(minScale) {
                            detectTapGestures { tap ->
                                val hit = currentClusters.minByOrNull { hypot(it.x - tap.x, it.y - tap.y) }
                                    ?.takeIf { hypot(it.x - tap.x, it.y - tap.y) < cellPx }
                                if (hit == null) {
                                    selected = null
                                } else if (hit.stations.size > 1 && scale < MAX_PX_PER_DEGREE / 2 && spread(hit) > 0.01) {
                                    // Crowded dot: zoom in on it.
                                    centerLon = hit.lon.toFloat()
                                    centerLat = hit.lat.toFloat()
                                    scale = (scale * 3f).coerceAtMost(MAX_PX_PER_DEGREE)
                                    selected = hit
                                } else {
                                    selected = hit
                                }
                            }
                        }
                ) {
                    // Graticule: 30° when zoomed out, finer as you zoom in.
                    val step = when {
                        scale < minScale * 3 -> 30
                        scale < minScale * 12 -> 10
                        scale < minScale * 60 -> 2
                        else -> 1
                    }
                    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                    var lon = -180
                    while (lon <= 180) {
                        val x = widthPx / 2f + (lon - centerLon) * scale
                        if (x in 0f..size.width) {
                            drawLine(grid.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f, pathEffect = dash)
                            drawText(measurer.measure("${lon}°", gridLabelStyle), topLeft = Offset(x + 4f, 4f))
                        }
                        lon += step
                    }
                    var lat = -90
                    while (lat <= 90) {
                        val y = heightPx / 2f - (lat - centerLat) * scale
                        if (y in 0f..size.height) {
                            drawLine(
                                grid.copy(alpha = if (lat == 0) 0.9f else 0.5f),
                                Offset(0f, y), Offset(size.width, y),
                                strokeWidth = if (lat == 0) 2f else 1f,
                                pathEffect = if (lat == 0) null else dash,
                            )
                            drawText(measurer.measure("${lat}°", gridLabelStyle), topLeft = Offset(4f, y + 2f))
                        }
                        lat += step
                    }

                    // Stations / clusters
                    clusters.forEach { c ->
                        val n = c.stations.size
                        val hasPlaying = playingUuid != null && c.stations.any { it.uuid == playingUuid }
                        val isSel = selected?.let { sel -> sel.stations.firstOrNull()?.uuid == c.stations.firstOrNull()?.uuid } == true
                        if (n == 1) {
                            drawCircle(if (hasPlaying) playingColor else dot, radius = if (hasPlaying) 10f else 7f, center = Offset(c.x, c.y))
                        } else {
                            val r = 14f + ln(n.toFloat()) * 6f
                            drawCircle(cluster.copy(alpha = 0.25f), radius = r + 6f, center = Offset(c.x, c.y))
                            drawCircle(if (hasPlaying) playingColor else cluster, radius = r, center = Offset(c.x, c.y))
                            val text = if (n >= 1000) "${n / 1000}k" else "$n"
                            val m = measurer.measure(text, labelStyle)
                            drawText(m, topLeft = Offset(c.x - m.size.width / 2f, c.y - m.size.height / 2f))
                        }
                        if (hasPlaying) {
                            drawCircle(playingColor, radius = 22f + if (isPlaying) 4f else 0f, center = Offset(c.x, c.y), style = Stroke(width = 3f))
                        }
                        if (isSel) {
                            drawCircle(homeColor, radius = 26f, center = Offset(c.x, c.y), style = Stroke(width = 3f))
                        }
                    }

                    // You are here
                    val hLat = home?.lat
                    val hLon = home?.lon
                    if (hLat != null && hLon != null) {
                        val p = toScreen(hLat, hLon)
                        drawCircle(homeColor.copy(alpha = 0.25f), radius = 22f, center = p)
                        drawCircle(homeColor, radius = 9f, center = p)
                        drawCircle(water, radius = 4f, center = p)
                    }
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalIconButton(onClick = { scale = (scale * 2f).coerceAtMost(MAX_PX_PER_DEGREE) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Zoom in")
                }
                FilledTonalIconButton(onClick = { scale = (scale / 2f).coerceAtLeast(minScale) }) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Zoom out")
                }
                FilledTonalIconButton(onClick = { fit() }) {
                    Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "Recenter")
                }
            }

            if (stations.isEmpty()) {
                Text(
                    when {
                        state.loading -> "Loading stations…"
                        state.scopeStations.isNotEmpty() -> "None of these stations list a location. Try the list or dial."
                        else -> "No stations to show here."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
        }

        // Tray: what's under the tapped dot.
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
                    "${stations.size} stations on the map · pinch to zoom · tap a dot",
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
                        IconButton(onClick = { selected = null }) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
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
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Degrees between a cluster's furthest-apart stations; 0 when they share one spot. */
private fun spread(c: MapCluster): Double {
    val lats = c.stations.mapNotNull { it.lat }
    val lons = c.stations.mapNotNull { it.lon }
    if (lats.isEmpty() || lons.isEmpty()) return 0.0
    return max(lats.max() - lats.min(), lons.max() - lons.min())
}

private fun placeName(c: MapCluster): String {
    val first = c.stations.first()
    val states = c.stations.mapNotNull { it.state }.distinct()
    val countries = c.stations.mapNotNull { it.country }.distinct()
    return when {
        states.size == 1 -> states.first()
        countries.size == 1 -> countries.first()
        else -> first.lat?.let { lat -> first.lon?.let { lon -> RadioGeo.continentOf(lat, lon) } } ?: "Here"
    }
}

/** (centerLon, centerLat, px per degree) framing a scope. */
private fun initialViewport(
    scope: RadioScope,
    stations: List<RadioStation>,
    home: com.theveloper.pixelplay.data.radio.RadioHome?,
    widthPx: Float,
    heightPx: Float,
): Triple<Float, Float, Float> {
    val worldScale = widthPx / 360f
    val hLat = home?.lat
    val hLon = home?.lon
    if (scope == RadioScope.LOCAL && hLat != null && hLon != null) {
        // About 250 km across.
        return Triple(hLon.toFloat(), hLat.toFloat(), widthPx / 2.5f)
    }
    if (scope == RadioScope.WORLD) return Triple(0f, 20f, worldScale)
    val b = RadioMapMath.bounds(stations) ?: return if (hLat != null && hLon != null) {
        Triple(hLon.toFloat(), hLat.toFloat(), widthPx / 10f)
    } else Triple(0f, 20f, worldScale)
    val (minLat, minLon, maxLat, maxLon) = listOf(b[0], b[1], b[2], b[3])
    val spanLon = max(maxLon - minLon, 0.5).toFloat()
    val spanLat = max(maxLat - minLat, 0.5).toFloat()
    val s = min(widthPx / spanLon, heightPx / spanLat) * 0.85f
    return Triple(((minLon + maxLon) / 2).toFloat(), ((minLat + maxLat) / 2).toFloat(), s)
}
