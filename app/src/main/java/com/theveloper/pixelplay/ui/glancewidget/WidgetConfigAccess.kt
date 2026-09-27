package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.data.preferences.WidgetAppearance
import com.theveloper.pixelplay.data.preferences.WidgetBackgroundStyle
import com.theveloper.pixelplay.data.preferences.WidgetConfig
import com.theveloper.pixelplay.data.preferences.WidgetKind
import com.theveloper.pixelplay.data.preferences.WidgetPreferencesRepository
import com.theveloper.pixelplay.data.preferences.WidgetProgressStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import kotlin.math.max

/**
 * How every widget reaches its user configuration.
 *
 * Glance widgets are constructed by the framework, not by Hilt, so they cannot take an
 * injected constructor. This entry point is the supported way in, and keeping the one copy
 * here means the five widgets all load their settings identically.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetPreferencesEntryPoint {
    fun widgetPreferencesRepository(): WidgetPreferencesRepository
}

/**
 * Reads one widget's configuration, falling back to its defaults if anything goes wrong.
 *
 * Called from `provideGlance` rather than from composition: it is a suspending DataStore read
 * and the answer cannot change part-way through a frame. A widget that cannot reach its
 * preferences should still render — a missing setting is not worth a blank cell — so every
 * failure resolves to the shipped defaults.
 */
internal suspend fun loadWidgetConfig(context: Context, kind: WidgetKind): WidgetConfig =
    runCatching {
        EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetPreferencesEntryPoint::class.java)
            .widgetPreferencesRepository()
            .readConfigOnce(kind)
    }.onFailure {
        Timber.tag("WidgetConfig").e(it, "Falling back to defaults for %s", kind.storageKey)
    }.getOrDefault(WidgetConfig(kind = kind))

/**
 * Applies the chosen background style and corner radius.
 *
 * `SOLID` and `TRANSPARENT` are pure Glance. `OUTLINED` has no modifier equivalent, so it is
 * drawn by [WidgetSurfaceRenderer] and applied as a background image; if that fails it falls
 * back to transparent rather than to a filled surface, because a filled card is the one thing
 * the user explicitly did not pick.
 */
@Composable
internal fun GlanceModifier.widgetSurface(
    context: Context,
    appearance: WidgetAppearance,
    colors: WidgetColors,
    outlineColor: Int,
    size: DpSize,
): GlanceModifier {
    val radiusDp = appearance.cornerRadiusDp.dp

    return when (appearance.backgroundStyle) {
        WidgetBackgroundStyle.SOLID ->
            this.background(colors.surface).cornerRadius(radiusDp)

        WidgetBackgroundStyle.TRANSPARENT -> this

        WidgetBackgroundStyle.OUTLINED -> {
            val density = context.resources.displayMetrics.density
            val bitmap = WidgetSurfaceRenderer.outline(
                widthPx = (size.width.value * density).toInt(),
                heightPx = (size.height.value * density).toInt(),
                radiusPx = appearance.cornerRadiusDp * density,
                color = outlineColor,
                strokePx = max(1f, density),
            )
            if (bitmap != null) {
                this.background(ImageProvider(bitmap), contentScale = ContentScale.FillBounds)
            } else {
                this
            }
        }
    }
}

/**
 * The colour to draw an `OUTLINED` border in.
 *
 * Reuses [TurntablePalette], which already resolves the album-art / Material You / fixed
 * choice down to plain `Int`s and makes the day-night decision from the widget's own
 * configuration — the same thing a rasterised border needs.
 */
internal fun outlineColorFor(
    context: Context,
    appearance: WidgetAppearance,
    playerInfo: PlayerInfo,
): Int = TurntablePalette.resolve(context, appearance, playerInfo).groove

/**
 * The optional progress bar under a widget's content.
 *
 * Glance's `defaultWeight()` splits space evenly rather than by a fraction, so "37% played"
 * cannot be expressed as layout — the bar is drawn by [WidgetSurfaceRenderer] and shown as an
 * image. Renders nothing at all when the style is `NONE` or the duration is unknown, rather
 * than an empty track that would imply a zero-length song.
 */
@Composable
internal fun WidgetProgressBar(
    context: Context,
    appearance: WidgetAppearance,
    playerInfo: PlayerInfo,
    style: WidgetProgressStyle,
    widthDp: Dp,
) {
    if (style == WidgetProgressStyle.NONE) return
    if (playerInfo.totalDurationMs <= 0L) return

    val density = context.resources.displayMetrics.density
    val wavy = style == WidgetProgressStyle.WAVY
    val heightDp = if (wavy) WAVY_PROGRESS_HEIGHT else LINE_PROGRESS_HEIGHT
    val palette = TurntablePalette.resolve(context, appearance, playerInfo)

    val bitmap = WidgetSurfaceRenderer.progress(
        widthPx = (widthDp.value * density).toInt(),
        heightPx = (heightDp.value * density).toInt(),
        fraction = playerInfo.currentPositionMs.toFloat() / playerInfo.totalDurationMs,
        activeColor = palette.playBadgeBackground,
        trackColor = withAlpha(palette.groove, 0.25f),
        wavy = wavy,
    ) ?: return

    Spacer(GlanceModifier.height(6.dp))
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxWidth().height(heightDp),
        contentScale = ContentScale.FillBounds,
    )
}

private val LINE_PROGRESS_HEIGHT = 4.dp
private val WAVY_PROGRESS_HEIGHT = 10.dp

private fun withAlpha(color: Int, alpha: Float): Int {
    val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    return (a shl 24) or (color and 0x00FFFFFF)
}
