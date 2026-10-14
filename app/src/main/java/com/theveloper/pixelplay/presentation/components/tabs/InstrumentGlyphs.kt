package com.theveloper.pixelplay.presentation.components.tabs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Filled 24 dp instrument glyphs for the Instruments sheet, drawn to be told apart at a glance:
 * acoustic (round body + sound hole), electric (horns + pickups), bass (long neck, 4 pegs),
 * drum kit. Guitars are drawn upright and turned 45° like the usual guitar icon. Tint with Icon().
 */
internal object InstrumentGlyphs {
    /** Acoustic guitar: figure-eight body with a round sound hole and bridge. */
    val AcousticGuitar: ImageVector by lazy {
        glyph(
            name = "AcousticGuitar",
            rotate = 45.0f, scale = 1.2f, dx = 1.1f, dy = -1.1f,
            true to "M12 8.6C14.3 8.6 15.9 10.1 15.9 12.2C15.9 13.4 15.2 14.1 15.2 14.6C15.2 15.1 17.2 16 17.2 18.6C17.2 21.5 14.9 23.4 12 23.4C9.1 23.4 6.8 21.5 6.8 18.6C6.8 16 8.8 15.1 8.8 14.6C8.8 14.1 8.1 13.4 8.1 12.2C8.1 10.1 9.7 8.6 12 8.6ZM10.3 14.2A1.7 1.7 0 1 0 13.7 14.2A1.7 1.7 0 1 0 10.3 14.2ZM9.9 19.3H14.1V20.3H9.9Z",
            false to "M11.25 9.4V3.4H12.75V9.4Z",
            false to "M10.7 3.8L10.9 0.9C10.92 0.6 11.15 0.4 11.45 0.4H12.55C12.85 0.4 13.08 0.6 13.1 0.9L13.3 3.8Z",
        )
    }

    /** Electric guitar: double-cutaway solid body with two pickups. */
    val ElectricGuitar: ImageVector by lazy {
        glyph(
            name = "ElectricGuitar",
            rotate = 45.0f, scale = 1.2f, dx = 1.1f, dy = -1.1f,
            true to "M10.9 13.4C10.9 12 10.4 10.4 9.4 9.4C8.6 8.7 7.4 9.2 7.6 10.6C7.8 12.2 8.4 13.2 7.6 14.6C6.6 16.4 6.4 19.2 7.6 21C8.8 22.8 11.4 23.4 13.2 23C15.6 22.5 17.4 20.8 17 18.4C16.7 16.6 15.6 15.8 16 14.4C16.3 13.4 16.8 12.6 16.4 11.6C16 10.6 14.8 10.8 14.2 11.6C13.6 12.4 13.1 12.9 13.1 13.4ZM10.1 15.3H13.9V16.4H10.1ZM10.1 17.6H13.9V18.7H10.1ZM10.4 20H13.6V20.9H10.4Z",
            false to "M11.3 14V3.4H12.7V14Z",
            false to "M11.25 3.8L11.05 0.9C11.03 0.45 11.5 0.2 11.95 0.42L13.75 1.35C14.15 1.55 14.15 2.15 13.75 2.35L12.75 2.9V3.8Z",
        )
    }

    /** Electric bass: smaller body, long neck and a four-peg headstock. */
    val ElectricBass: ImageVector by lazy {
        glyph(
            name = "ElectricBass",
            rotate = 45.0f, scale = 1.08f, dx = 0.6f, dy = -0.6f,
            true to "M10.7 15.6C10.7 14.4 10.2 13 9.3 12.1C8.5 11.4 7.3 11.9 7.5 13.2C7.7 14.6 8.2 15.5 7.6 16.7C6.8 18.2 6.7 20.3 7.7 21.7C8.8 23.2 11.2 23.7 13 23.4C15.2 23 16.8 21.6 16.5 19.6C16.3 18.2 15.4 17.6 15.7 16.5C15.9 15.7 16.3 15 16 14.2C15.6 13.3 14.5 13.5 13.9 14.2C13.4 14.8 13.1 15.2 13.1 15.6ZM9.9 17.6H12.2V18.8H9.9ZM11.8 19.1H14.1V20.3H11.8Z",
            false to "M11.15 16.2V3.6H12.85V16.2Z",
            false to "M10.9 4L10.7 1.1C10.7 0.6 11.1 0.25 11.6 0.25H12.4C12.9 0.25 13.3 0.6 13.3 1.1L13.1 4Z",
            false to "M9.3 1.3A0.75 0.75 0 1 0 10.8 1.3A0.75 0.75 0 1 0 9.3 1.3ZM9.3 3.2A0.75 0.75 0 1 0 10.8 3.2A0.75 0.75 0 1 0 9.3 3.2ZM13.2 1.3A0.75 0.75 0 1 0 14.7 1.3A0.75 0.75 0 1 0 13.2 1.3ZM13.2 3.2A0.75 0.75 0 1 0 14.7 3.2A0.75 0.75 0 1 0 13.2 3.2Z",
        )
    }

    /** Drum kit, front view: kick drum, two toms, cymbal and hi-hat on stands. */
    val DrumKit: ImageVector by lazy {
        glyph(
            name = "DrumKit",
            rotate = 0.0f, scale = 1.0f, dx = 0.0f, dy = 0.0f,
            true to "M5.4 16.3A6.6 6.6 0 1 0 18.6 16.3A6.6 6.6 0 1 0 5.4 16.3ZM7.5 16.3A4.5 4.5 0 1 0 16.5 16.3A4.5 4.5 0 1 0 7.5 16.3Z",
            false to "M10.2 16.3A1.8 1.8 0 1 0 13.8 16.3A1.8 1.8 0 1 0 10.2 16.3Z",
            false to "M5.2 6.9C5.2 6.3 5.7 5.8 6.3 5.8H10.4C11 5.8 11.5 6.3 11.5 6.9V9.2C11.5 9.8 11 10.3 10.4 10.3H6.3C5.7 10.3 5.2 9.8 5.2 9.2ZM12.5 6.9C12.5 6.3 13 5.8 13.6 5.8H17.7C18.3 5.8 18.8 6.3 18.8 6.9V9.2C18.8 9.8 18.3 10.3 17.7 10.3H13.6C13 10.3 12.5 9.8 12.5 9.2Z",
            false to "M0.6 4.1C0.6 3.4 2 2.9 3.6 2.9C5.2 2.9 6.6 3.4 6.6 4.1C6.6 4.6 5.2 5 3.6 5C2 5 0.6 4.6 0.6 4.1ZM3.0 4.5H4.2V23H3.0ZM17.4 5.6C17.4 5 18.8 4.5 20.4 4.5C22 4.5 23.4 5 23.4 5.6C23.4 6.1 22 6.5 20.4 6.5C18.8 6.5 17.4 6.1 17.4 5.6ZM19.8 6H21V23H19.8Z",
        )
    }

    private fun glyph(
        name: String,
        rotate: Float,
        scale: Float,
        dx: Float,
        dy: Float,
        vararg paths: Pair<Boolean, String>,
    ): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        // Same transform as the SVG: translate(12+dx, 12+dy) rotate(r) scale(s) translate(-12, -12).
        builder.addGroup(
            name = "$name-group",
            rotate = rotate,
            pivotX = 12f,
            pivotY = 12f,
            scaleX = scale,
            scaleY = scale,
            translationX = dx,
            translationY = dy,
        )
        paths.forEach { (evenOdd, d) ->
            builder.addPath(
                pathData = addPathNodes(d),
                pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                fill = SolidColor(Color.Black),
            )
        }
        builder.clearGroup()
        return builder.build()
    }
}
