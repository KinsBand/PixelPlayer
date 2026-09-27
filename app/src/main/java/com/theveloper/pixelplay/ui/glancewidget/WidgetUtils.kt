package com.theveloper.pixelplay.ui.glancewidget

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.data.preferences.WidgetAccentSource
import com.theveloper.pixelplay.data.preferences.WidgetAppearance

object AlbumArtBitmapCache {
    private const val CACHE_SIZE_BYTES = 4 * 1024 * 1024 // 4 MiB
    private val lruCache = object : LruCache<String, Bitmap>(CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    fun getBitmap(key: String): Bitmap? = lruCache.get(key)

    fun putBitmap(key: String, bitmap: Bitmap) {
        if (getBitmap(key) == null) {
            lruCache.put(key, bitmap)
        }
    }

    fun getKey(byteArray: ByteArray): String {
        return byteArray.contentHashCode().toString()
    }
}

data class WidgetColors(
    val surface: ColorProvider,
    val onSurface: ColorProvider,
    val artist: ColorProvider,
    val playPauseBackground: ColorProvider,
    val playPauseIcon: ColorProvider,
    val prevNextBackground: ColorProvider,
    val prevNextIcon: ColorProvider
)

/**
 * Colours for a widget, honouring the user's accent choice.
 *
 * [WidgetAccentSource.ALBUM_ART] is the historical behaviour and stays the default: use the
 * palette the service extracted from the current cover, falling back to the system theme when
 * there is none. The other two let the user opt out of colours that change every track.
 */
@Composable
fun PlayerInfo.getWidgetColors(appearance: WidgetAppearance): WidgetColors = when (appearance.accentSource) {
    WidgetAccentSource.ALBUM_ART -> getWidgetColors()
    WidgetAccentSource.MATERIAL_YOU -> systemWidgetColors()
    WidgetAccentSource.FIXED -> fixedWidgetColors(appearance.fixedAccentColor)
}

/** The system's own dynamic colour, ignoring whatever is playing. */
@Composable
private fun systemWidgetColors(): WidgetColors = WidgetColors(
    surface = GlanceTheme.colors.surface,
    onSurface = GlanceTheme.colors.onSurface,
    artist = GlanceTheme.colors.onSurfaceVariant,
    playPauseBackground = GlanceTheme.colors.primaryContainer,
    playPauseIcon = GlanceTheme.colors.onPrimaryContainer,
    prevNextBackground = GlanceTheme.colors.secondaryContainer,
    prevNextIcon = GlanceTheme.colors.onSecondaryContainer
)

/**
 * One colour the user picked, plus derived tints.
 *
 * The same value is used for day and night: the point of choosing a fixed colour is that it
 * does not change, and quietly substituting a lighter one after dark would defeat that.
 */
@Composable
private fun fixedWidgetColors(accent: Int): WidgetColors {
    val surface = Color(accent)
    val onSurface = if (isLightColor(accent)) Color(0xFF12121A) else Color(0xFFF2EFFA)
    val container = blend(surface, onSurface, 0.16f)
    return WidgetColors(
        surface = ColorProvider(day = surface, night = surface),
        onSurface = ColorProvider(day = onSurface, night = onSurface),
        artist = ColorProvider(
            day = onSurface.copy(alpha = 0.7f),
            night = onSurface.copy(alpha = 0.7f)
        ),
        playPauseBackground = ColorProvider(day = onSurface, night = onSurface),
        playPauseIcon = ColorProvider(day = surface, night = surface),
        prevNextBackground = ColorProvider(day = container, night = container),
        prevNextIcon = ColorProvider(day = onSurface, night = onSurface)
    )
}

private fun isLightColor(color: Int): Boolean {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    return (0.299f * r + 0.587f * g + 0.114f * b) / 255f > 0.5f
}

private fun blend(base: Color, towards: Color, amount: Float): Color = Color(
    red = base.red + (towards.red - base.red) * amount,
    green = base.green + (towards.green - base.green) * amount,
    blue = base.blue + (towards.blue - base.blue) * amount,
    alpha = 1f
)

@Composable
fun PlayerInfo.getWidgetColors(): WidgetColors {
    val theme = this.themeColors
    
    return if (theme != null) {
        WidgetColors(
            surface = ColorProvider(
                day = Color(theme.lightSurfaceContainer),
                night = Color(theme.darkSurfaceContainer)
            ),
            onSurface = ColorProvider(
                day = Color(theme.lightTitle),
                night = Color(theme.darkTitle)
            ),
            artist = ColorProvider(
                day = Color(theme.lightArtist),
                night = Color(theme.darkArtist)
            ),
            playPauseBackground = ColorProvider(
                day = Color(theme.lightPlayPauseBackground),
                night = Color(theme.darkPlayPauseBackground)
            ),
            playPauseIcon = ColorProvider(
                day = Color(theme.lightPlayPauseIcon),
                night = Color(theme.darkPlayPauseIcon)
            ),
            prevNextBackground = ColorProvider(
                day = Color(theme.lightPrevNextBackground),
                night = Color(theme.darkPrevNextBackground)
            ),
            prevNextIcon = ColorProvider(
                day = Color(theme.lightPrevNextIcon),
                night = Color(theme.darkPrevNextIcon)
            )
        )
    } else {
        WidgetColors(
            surface = GlanceTheme.colors.surface,
            onSurface = GlanceTheme.colors.onSurface,
            artist = GlanceTheme.colors.onSurface,
            playPauseBackground = GlanceTheme.colors.primaryContainer,
            playPauseIcon = GlanceTheme.colors.onPrimaryContainer,
            prevNextBackground = GlanceTheme.colors.secondaryContainer,
            prevNextIcon = GlanceTheme.colors.onSecondaryContainer
        )
    }
}
