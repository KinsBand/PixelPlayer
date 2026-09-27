package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.core.content.ContextCompat
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.data.preferences.WidgetAccentSource
import com.theveloper.pixelplay.data.preferences.WidgetAppearance

/**
 * The colours the turntable is drawn with.
 *
 * These are plain `Int` colours rather than Glance `ColorProvider`s because most of them end
 * up inside a [android.graphics.Canvas]: a bitmap has already been rasterised by the time the
 * launcher decides whether it is showing a light or dark home screen, so the day/night choice
 * has to be made here, up front, from the widget's own configuration.
 */
data class TurntablePalette(
    val disc: Int,
    val label: Int,
    val labelGlyph: Int,
    val groove: Int,
    val tonearm: Int,
    val playBadgeBackground: Int,
    val playBadgeIcon: Int,
    val favoriteBadgeBackground: Int,
    val favoriteBadgeIcon: Int,
    val title: Int,
    val artist: Int,
) {
    companion object {

        /**
         * The palette the turntable is designed around: a near-black indigo record with a
         * pale lavender transport bubble and a soft pink favourite bubble.
         */
        private const val VINYL_DISC = 0xFF1B0A33.toInt()
        private const val VINYL_LABEL = 0xFFB9AEDA.toInt()
        private const val VINYL_PLAY_BG = 0xFFE4E4FB.toInt()
        private const val VINYL_PLAY_ICON = 0xFF3B49DF.toInt()
        private const val VINYL_FAV_BG = 0xFFFCDCE6.toInt()
        private const val VINYL_FAV_ICON = 0xFFB5297A.toInt()

        fun resolve(
            context: Context,
            appearance: WidgetAppearance,
            playerInfo: PlayerInfo,
        ): TurntablePalette {
            val night = isNightMode(context)
            return when (appearance.accentSource) {
                WidgetAccentSource.ALBUM_ART -> fromAlbumArt(playerInfo, night)
                WidgetAccentSource.MATERIAL_YOU -> fromSystem(context, night)
                WidgetAccentSource.FIXED -> fixed(appearance.fixedAccentColor)
            }
        }

        /**
         * A widget's own `Configuration` reflects the launcher's light/dark state, so this is
         * the one place the day/night decision can honestly be made for a rasterised disc.
         */
        private fun isNightMode(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        private fun fromAlbumArt(playerInfo: PlayerInfo, night: Boolean): TurntablePalette {
            val theme = playerInfo.themeColors ?: return fixed(VINYL_DISC)
            val disc = if (night) theme.darkSurfaceContainer else theme.lightSurfaceContainer
            val onDisc = if (night) theme.darkTitle else theme.lightTitle
            val artist = if (night) theme.darkArtist else theme.lightArtist
            val playBg =
                if (night) theme.darkPlayPauseBackground else theme.lightPlayPauseBackground
            val playIcon = if (night) theme.darkPlayPauseIcon else theme.lightPlayPauseIcon
            val favBg = if (night) theme.darkPrevNextBackground else theme.lightPrevNextBackground
            val favIcon = if (night) theme.darkPrevNextIcon else theme.lightPrevNextIcon

            return TurntablePalette(
                disc = disc,
                label = playBg,
                labelGlyph = playIcon,
                groove = onDisc,
                tonearm = onDisc,
                playBadgeBackground = playBg,
                playBadgeIcon = playIcon,
                favoriteBadgeBackground = favBg,
                favoriteBadgeIcon = favIcon,
                title = onDisc,
                artist = artist,
            )
        }

        private fun fromSystem(context: Context, night: Boolean): TurntablePalette {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return fixed(VINYL_DISC)
            }
            fun color(resId: Int) = ContextCompat.getColor(context, resId)

            val disc = color(
                if (night) android.R.color.system_neutral1_900
                else android.R.color.system_neutral1_100,
            )
            val onDisc = color(
                if (night) android.R.color.system_neutral1_100
                else android.R.color.system_neutral1_900,
            )
            val accentContainer = color(
                if (night) android.R.color.system_accent1_700
                else android.R.color.system_accent1_100,
            )
            val onAccentContainer = color(
                if (night) android.R.color.system_accent1_100
                else android.R.color.system_accent1_900,
            )
            val secondaryContainer = color(
                if (night) android.R.color.system_accent2_700
                else android.R.color.system_accent2_100,
            )
            val onSecondaryContainer = color(
                if (night) android.R.color.system_accent2_100
                else android.R.color.system_accent2_900,
            )

            return TurntablePalette(
                disc = disc,
                label = accentContainer,
                labelGlyph = onAccentContainer,
                groove = onDisc,
                tonearm = onDisc,
                playBadgeBackground = accentContainer,
                playBadgeIcon = onAccentContainer,
                favoriteBadgeBackground = secondaryContainer,
                favoriteBadgeIcon = onSecondaryContainer,
                title = onDisc,
                artist = onDisc,
            )
        }

        private fun fixed(discColor: Int): TurntablePalette = TurntablePalette(
            disc = discColor,
            label = VINYL_LABEL,
            labelGlyph = VINYL_DISC,
            groove = VINYL_LABEL,
            tonearm = VINYL_LABEL,
            playBadgeBackground = VINYL_PLAY_BG,
            playBadgeIcon = VINYL_PLAY_ICON,
            favoriteBadgeBackground = VINYL_FAV_BG,
            favoriteBadgeIcon = VINYL_FAV_ICON,
            title = VINYL_LABEL,
            artist = VINYL_LABEL,
        )
    }
}
