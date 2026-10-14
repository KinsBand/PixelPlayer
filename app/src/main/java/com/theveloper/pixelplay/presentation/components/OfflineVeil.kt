package com.theveloper.pixelplay.presentation.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.offline.isPlayableOffline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val noIds = MutableStateFlow<Set<String>>(emptySet())
private val noBlurSetting = MutableStateFlow(false)

/** True while the device is offline and [song] can't play from the device. */
@Composable
fun rememberSongOfflineBlocked(song: Song?): Boolean {
    if (song == null) return false
    val online by rememberIsOnlineState()
    if (online) return false
    val ui = LocalConnectivityUi.current
    val ids by (ui?.offlinePlayableIds ?: noIds).collectAsState()
    return remember(song.id, song.youtubeId, song.downloadState, ids) { !song.isPlayableOffline(ids) }
}

/** True while offline, for things that are always online (radio, online-only sections). */
@Composable
fun rememberNeedsOnlineBlocked(): Boolean = !rememberIsOnlineState().value

/** The card's veil for [song]: blurred + "Offline" while it can't play. */
@Composable
fun Modifier.offlineVeil(song: Song?, shape: Shape = RectangleShape): Modifier =
    offlineVeil(rememberSongOfflineBlocked(song), shape)

/**
 * Offline veil for one card or row. While [active]:
 * - the content is lightly blurred (API 31+, blur setting on) so cover, title and artist stay
 *   faintly readable, with a soft surface scrim and a small centred "Offline" pill on top;
 *   on API 30 or with blur off, the scrim is a little stronger instead;
 * - a tap doesn't reach the card: it shakes slightly with a "reject" haptic. Long-press
 *   still reaches the card, so its menu (and Download) stays available.
 * Going online animates everything back; nothing is re-laid out.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.offlineVeil(active: Boolean, shape: Shape = RectangleShape): Modifier {
    val ui = LocalConnectivityUi.current
    val blurSetting: State<Boolean> = (ui?.blurDisabled ?: noBlurSetting).collectAsState()
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !blurSetting.value
    val amount by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "offlineVeil"
    )
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val longPressMs = LocalViewConfiguration.current.longPressTimeoutMillis
    val latestActive by rememberUpdatedState(active)
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = colors.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val label = remember(measurer, labelStyle) { measurer.measure("Offline", labelStyle) }
    val pillColor = colors.surfaceContainerHigh
    val scrimColor = colors.surface
    val scrimAlpha = if (canBlur) 0.35f else 0.55f
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    return this
        .graphicsLayer { translationX = shake.value }
        .pointerInput(longPressMs) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!latestActive) return@awaitEachGesture
                val start = down.uptimeMillis
                val slop = viewConfiguration.touchSlop
                var up: androidx.compose.ui.input.pointer.PointerInputChange? = null
                while (up == null) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    // A drag (list scroll) is not a tap.
                    if ((change.position - down.position).getDistance() > slop) return@awaitEachGesture
                    if (!change.pressed) up = change
                }
                // A long press already went to the card (menu); only a plain tap is blocked.
                val tap = up!!
                if (tap.uptimeMillis - start >= longPressMs) return@awaitEachGesture
                tap.consume()
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                scope.launch {
                    val d = 6.dp.toPx()
                    for (x in floatArrayOf(d, -d, d * 0.6f, -d * 0.6f, 0f)) {
                        shake.animateTo(x, spring(dampingRatio = 1f, stiffness = 3000f))
                    }
                    shake.animateTo(0f, spatial)
                }
            }
        }
        // Overlay first, blur after: the blurred layer is the card, the pill stays sharp.
        .drawWithContent {
            drawContent()
            if (amount <= 0f) return@drawWithContent
            drawOutline(shape.createOutline(size, layoutDirection, this), scrimColor, alpha = scrimAlpha * amount)
            val padH = 8.dp.toPx()
            val padV = 3.dp.toPx()
            val w = label.size.width + padH * 2
            val h = label.size.height + padV * 2
            if (w > size.width || h > size.height) return@drawWithContent
            val topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f)
            drawRoundRect(pillColor, topLeft, Size(w, h), CornerRadius(h / 2f), alpha = 0.9f * amount)
            drawText(label, topLeft = Offset(topLeft.x + padH, topLeft.y + padV), alpha = amount)
        }
        .then(
            if (canBlur && amount > 0f) {
                Modifier.blur((2.5f * amount).dp, BlurredEdgeTreatment(shape))
            } else Modifier
        )
        .semantics { if (active) stateDescription = "Offline, needs internet" }
}

/** True while offline and none of [songs] can play from the device (a whole mix is unplayable). */
@Composable
fun rememberSongsOfflineBlocked(songs: List<Song>): Boolean {
    val online by rememberIsOnlineState()
    if (online || songs.isEmpty()) return false
    val ui = LocalConnectivityUi.current
    val ids by (ui?.offlinePlayableIds ?: noIds).collectAsState()
    return remember(songs, ids) { songs.none { it.isPlayableOffline(ids) } }
}
