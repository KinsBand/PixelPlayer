package com.theveloper.pixelplay.presentation.screens.radio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.TransformOrigin
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.radio.RadioLabels
import com.theveloper.pixelplay.data.radio.RadioStation
import com.theveloper.pixelplay.data.radio.RadioStore
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private val EvenCorner = 26.dp

private fun smoothShape(radius: androidx.compose.ui.unit.Dp) = AbsoluteSmoothCornerShape(
    cornerRadiusTR = radius, smoothnessAsPercentBR = 60, cornerRadiusBR = radius,
    smoothnessAsPercentTL = 60, cornerRadiusTL = radius, smoothnessAsPercentBL = 60,
    cornerRadiusBL = radius, smoothnessAsPercentTR = 60
)

/**
 * The station menu on the Radio screen (hold a station). Same layout as the player's menu for a
 * station (SongInfoBottomSheet): cover + title, then two pages switched by the OPTIONS / INFO bar
 * at the bottom. Options: presets, Website (+ Map), then Like | Play | Share. Info: the details.
 * Every action slides the sheet down first, then runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioStationOptionsSheet(
    station: RadioStation,
    presets: List<RadioStation?>,
    isFavorite: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetPreset: (Int) -> Unit,
    onOpenHomepage: (() -> Unit)?,
    onShowOnMap: (() -> Unit)?,
    onDismiss: () -> Unit,
    /** Queue the station at the end of Up next. */
    onQueue: () -> Unit,
    /** Queue the station right after the song playing now. */
    onNext: () -> Unit,
    /** Soon confirmed: after the k-th song of Up next. */
    onSoon: (afterUpcoming: Int) -> Unit,
    /** For the Soon slider's view of Up next. */
    soonPlayer: com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel,
) {
    var soonOpen by androidx.compose.runtime.saveable.rememberSaveable(station.uuid) { androidx.compose.runtime.mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = soonOpen) { soonOpen = false }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(pageCount = { 2 })
    /** Slide the sheet away, then run [action] (the sheet is gone by then). */
    val closeThen: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.padding(horizontal = 16.dp)) { RadioStationHeader(station) }
                Spacer(Modifier.height(16.dp))
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (page == 0) {
                            // Queue · Next · Soon. Off for the station that's playing (it would add a
                            // second copy of the live stream). Soon swaps the rows below for its slider.
                            com.theveloper.pixelplay.presentation.components.QueueNextSoonRow(
                                onQueue = { closeThen(onQueue) },
                                onNext = { closeThen(onNext) },
                                onSoon = { soonOpen = !soonOpen },
                                soonOpen = soonOpen,
                                enabled = !isCurrent,
                            )
                            androidx.compose.animation.AnimatedContent(
                                targetState = soonOpen && !isCurrent,
                                transitionSpec = {
                                    if (targetState) {
                                        (androidx.compose.animation.slideInVertically(tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationMedium2, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedDecelerate)) { it } +
                                            androidx.compose.animation.fadeIn(tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationMedium2, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedDecelerate))) togetherWith
                                            androidx.compose.animation.fadeOut(tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort3, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedAccelerate))
                                    } else {
                                        androidx.compose.animation.fadeIn(tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationMedium1, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedDecelerate)) togetherWith
                                            (androidx.compose.animation.slideOutVertically(tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort4, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedAccelerate)) { it } +
                                                androidx.compose.animation.fadeOut(tween(com.theveloper.pixelplay.ui.theme.MotionTokens.DurationShort4, easing = com.theveloper.pixelplay.ui.theme.MotionTokens.EmphasizedAccelerate)))
                                    }
                                },
                                label = "radioSoon"
                            ) { showSoon ->
                                if (showSoon) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainer)
                                    ) {
                                        // Only composed while the slider shows, so recording is only
                                        // registered for a station that's about to be queued.
                                        com.theveloper.pixelplay.presentation.components.PlaylistSoonPanel(
                                            playerViewModel = soonPlayer,
                                            onBack = { soonOpen = false },
                                            onConfirm = { k -> closeThen { onSoon(k) } },
                                            forSong = remember(station.uuid) { com.theveloper.pixelplay.data.radio.RadioPlayback.toSong(station) },
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        RadioPresetPicker(station = station, presets = presets, onSetPreset = onSetPreset)
                                        RadioWebsiteMapRow(
                                            station = station,
                                            onWebsite = onOpenHomepage?.let { open -> { closeThen(open) } },
                                            onMap = onShowOnMap?.let { show -> { closeThen(show) } },
                                        )
                                        RadioActionsRow(
                                            isFavorite = isFavorite,
                                            playLabel = when {
                                                isCurrent && isPlaying -> "Pause"
                                                isCurrent -> "Resume"
                                                else -> "Play"
                                            },
                                            playIcon = if (isCurrent && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            onPlay = { closeThen(onPlay) },
                                            onToggleFavorite = onToggleFavorite,
                                            onShare = { shareStation(context, station) },
                                        )
                                    }
                                }
                            }
                            // Room for the OPTIONS / INFO bar that floats over the bottom.
                            Spacer(Modifier.height(80.dp))
                        } else {
                            RadioStationDetails(station)
                            Spacer(Modifier.height(88.dp))
                        }
                    }
                }
            }
            RadioSheetTabBar(
                selectedPage = pagerState.currentPage,
                onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/** OPTIONS | INFO, the same bar as the player's song menu. */
@Composable
private fun RadioSheetTabBar(selectedPage: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            Triple("Options", Icons.Rounded.Menu, TransformOrigin(0f, 0.5f)),
            Triple("Info", Icons.Rounded.Info, TransformOrigin(1f, 0.5f)),
        ).forEachIndexed { index, (label, icon, origin) ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                TabAnimation(
                    index = index,
                    title = label,
                    selectedIndex = selectedPage,
                    onClick = { onSelect(index) },
                    transformOrigin = origin
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = label, modifier = Modifier.padding(horizontal = 4.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(label.uppercase(), fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * "Website" with a map button to its right (both menus: Radio screen and player). With only one
 * of the two, that one fills the row; with neither, nothing is shown.
 */
@Composable
fun RadioWebsiteMapRow(
    station: RadioStation,
    onWebsite: (() -> Unit)?,
    onMap: (() -> Unit)?,
) {
    val website = station.homepage?.takeIf { onWebsite != null }
    val map = onMap?.takeIf { station.hasGeo }
    val place = listOfNotNull(station.state, station.country).joinToString(", ")
    when {
        website != null && map != null -> Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioOptionCard(
                icon = Icons.Rounded.Language,
                title = "Website",
                subtitle = displayHost(website),
                onClick = onWebsite,
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(
                onClick = map,
                shape = RoundedCornerShape(18.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxHeight().width(72.dp)
            ) {
                Icon(Icons.Rounded.Map, contentDescription = "Show ${station.name} on map", modifier = Modifier.size(26.dp))
            }
        }
        website != null -> RadioOptionCard(Icons.Rounded.Language, "Website", displayHost(website), onWebsite)
        map != null -> RadioOptionCard(Icons.Rounded.Map, "Map", place.ifBlank { "Where the station is" }, map)
    }
}

private fun displayHost(url: String) = url.removePrefix("https://").removePrefix("http://").trimEnd('/')

/** Cover, name and "NSW · Comm · 128 kbps", sized like the song menu's header. */
@Composable
fun RadioStationHeader(station: RadioStation) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StationLogo(station, Modifier.size(80.dp).clip(smoothShape(EvenCorner)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                station.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 24.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(
                    station.subtitle.takeIf { it.isNotBlank() },
                    station.frequency?.let { "${it.band.label} ${frequencyNumber(it)}" },
                    station.bitrate.takeIf { it > 0 }?.let { "$it kbps" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Like | Play | Share, the same shapes and colours as the song menu's bottom row. */
@Composable
fun RadioActionsRow(
    isFavorite: Boolean,
    playLabel: String,
    playIcon: ImageVector,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
) {
    val favoriteCorner by animateDpAsState(if (isFavorite) EvenCorner else 60.dp, tween(300), label = "radioFavCorner")
    val favoriteContainer by animateColorAsState(
        if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, tween(300), label = "radioFavBg"
    )
    val favoriteContent by animateColorAsState(
        if (isFavorite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, tween(300), label = "radioFavFg"
    )
    val evenShape = remember { smoothShape(EvenCorner) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilledIconButton(
            modifier = Modifier.weight(0.27f).fillMaxHeight(),
            onClick = onToggleFavorite,
            shape = smoothShape(favoriteCorner),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = favoriteContainer, contentColor = favoriteContent),
        ) {
            Icon(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favourites" else "Add to favourites",
                modifier = Modifier.size(36.dp),
            )
        }
        FilledTonalButton(
            modifier = Modifier.weight(0.46f).heightIn(min = 80.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 10.dp),
            shape = evenShape,
            onClick = onPlay,
        ) {
            Icon(playIcon, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(playLabel, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
        FilledTonalIconButton(
            modifier = Modifier.weight(0.27f).fillMaxHeight(),
            onClick = onShare,
            shape = evenShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
        ) {
            Icon(Icons.Rounded.Share, contentDescription = "Share station", modifier = Modifier.size(36.dp))
        }
    }
}

/** "Save to preset": the six buttons, the ones holding this station highlighted. */
@Composable
fun RadioPresetPicker(
    station: RadioStation,
    presets: List<RadioStation?>,
    onSetPreset: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                "Save to preset",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Tap a button. Presets play with one tap on the Radio screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(RadioStore.PRESET_COUNT) { slot ->
                    val holder = presets.getOrNull(slot)
                    val isThis = holder?.uuid == station.uuid
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.9f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isThis) 0f else 1f), RoundedCornerShape(14.dp))
                            .clickable(onClickLabel = "Save to preset ${slot + 1}") { onSetPreset(slot) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${slot + 1}",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                                fontWeight = FontWeight.Bold,
                                color = if (isThis) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                holder?.let { shortName(it) } ?: "empty",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isThis) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One menu card, the same style as the song menu's detail cards. */
@Composable
fun RadioOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Where, what, how it sounds; and the stream link (tap to copy). */
@Composable
fun RadioStationDetails(station: RadioStation) {
    val context = LocalContext.current
    val place = listOfNotNull(
        station.state?.let { s -> RadioLabels.shortState(s, station.countryCode)?.takeIf { it != s }?.let { "$s ($it)" } ?: s },
        station.country,
    ).joinToString(", ")
    if (place.isNotBlank()) RadioOptionCard(Icons.Rounded.Place, "Location", place)
    if (station.tags.isNotEmpty()) {
        RadioOptionCard(
            Icons.Rounded.Sell,
            "Genres",
            station.tags.joinToString(" · ") { tag -> RadioLabels.shortTag(tag).let { short -> if (short.equals(tag, true)) tag else "$tag ($short)" } }
        )
    }
    station.language?.takeIf { it.isNotBlank() }?.let {
        RadioOptionCard(Icons.Rounded.Translate, "Language", it.replaceFirstChar { c -> c.uppercase() })
    }
    val quality = listOfNotNull(station.codec, station.bitrate.takeIf { it > 0 }?.let { "$it kbps" }, if (station.isHls) "HLS" else null)
    if (quality.isNotEmpty()) RadioOptionCard(Icons.Rounded.GraphicEq, "Stream", quality.joinToString(" · "))
    RadioOptionCard(Icons.Rounded.ContentCopy, "Copy stream link", station.streamUrl, onClick = {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText(station.name, station.streamUrl))
        Toast.makeText(context, "Stream link copied", Toast.LENGTH_SHORT).show()
    })
}

/** Shares the station's website (or stream link) as text. */
fun shareStation(context: Context, station: RadioStation) {
    val link = station.homepage ?: station.streamUrl
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, station.name)
        putExtra(Intent.EXTRA_TEXT, "${station.name} — $link")
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share station").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Opens the station's website, if it has one. */
fun openStationHomepage(context: Context, station: RadioStation): Boolean {
    val url = station.homepage ?: return false
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
