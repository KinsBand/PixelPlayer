package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * "Online & liked": a heart made of clouds, two puffy layers deep, with a solid heart in the
 * middle. Drawn on the same 24×24 grid as Icons.Rounded.Favorite (the core *is* that heart,
 * scaled to 44%), so it sits on the same baseline and reads as part of the same icon family.
 *
 *   OUTER — outer cloud layer (a ring: its inside is cut out where MID sits)
 *   MID   — inner cloud layer (a ring around a thin gap, so the heart stands out on any background)
 *   CORE  — the liked heart
 */
private const val OUTER =
    "M12,19.98C11.03,21.31 7.46,18.69 8.44,17.36C7.24,18.54 4.07,15.31 5.27,14.13C3.81,14.93 1.66,11.01 3.12,10.21" +
        "C1.53,9.86 2.49,5.59 4.08,5.95C3.39,4.47 7.35,2.63 8.04,4.1C8.74,2.63 12.7,4.5 12,5.98C11.3,4.5 15.26,2.63 15.96,4.1" +
        "C16.65,2.62 20.62,4.48 19.93,5.95C21.51,5.6 22.47,9.86 20.88,10.21C22.34,11.01 20.19,14.93 18.73,14.13" +
        "C19.94,15.31 16.77,18.55 15.57,17.37C16.54,18.69 12.98,21.31 12,19.98Z" +
        "M12,18.38C12.91,19.57 16.1,17.12 15.19,15.93C16.33,16.94 19.04,13.88 17.9,12.87C19.35,13.14 20.08,9.24 18.63,8.98" +
        "C19.57,7.88 16.63,5.33 15.68,6.43C15.17,5.06 11.49,6.43 12,7.8C12.51,6.43 8.83,5.06 8.32,6.43C7.38,5.33 4.43,7.87 5.37,8.97" +
        "C3.92,9.24 4.65,13.14 6.1,12.87C4.96,13.88 7.67,16.94 8.81,15.93C7.9,17.12 11.09,19.57 12,18.38Z"

private const val MID =
    "M12,18.38C11.09,19.57 7.9,17.12 8.81,15.93C7.67,16.94 4.96,13.88 6.1,12.87C4.65,13.14 3.92,9.24 5.37,8.97" +
        "C4.43,7.87 7.38,5.33 8.32,6.43C8.83,5.06 12.51,6.43 12,7.8C11.49,6.43 15.17,5.06 15.68,6.43C16.63,5.33 19.57,7.88 18.63,8.98" +
        "C20.08,9.24 19.35,13.14 17.9,12.87C19.04,13.88 16.33,16.94 15.19,15.93C16.1,17.12 12.91,19.57 12,18.38Z" +
        "M12.71,16.97L12.76,16.92C15.52,14.42 17.32,12.78 17.25,10.75C17.22,9.85 16.76,9 16.02,8.49" +
        "C14.64,7.54 12.92,7.99 12,9.07C11.08,7.99 9.36,7.55 7.98,8.49C7.24,9 6.78,9.85 6.75,10.75" +
        "C6.68,12.78 8.48,14.42 11.24,16.91L11.3,16.96C11.7,17.33 12.31,17.33 12.71,16.97Z"

private const val CORE =
    "M12.59,16.28C12.26,16.58 11.74,16.58 11.41,16.27L11.36,16.23C9.05,14.14 7.54,12.77 7.6,11.06" +
        "C7.63,10.32 8.01,9.6 8.63,9.18C9.79,8.38 11.23,8.75 12,9.66C12.77,8.75 14.21,8.38 15.37,9.18" +
        "C15.99,9.6 16.37,10.32 16.4,11.06C16.46,12.77 14.95,14.14 12.64,16.24L12.59,16.28Z"

/** The cloud-heart vector in three explicit colours (outer cloud, inner cloud, heart). */
fun cloudHeartVector(outerCloud: Color, innerCloud: Color, heart: Color): ImageVector =
    ImageVector.Builder(
        name = "CloudHeart",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
        .addPath(pathData = addPathNodes(OUTER), pathFillType = PathFillType.EvenOdd, fill = SolidColor(outerCloud))
        .addPath(pathData = addPathNodes(MID), pathFillType = PathFillType.EvenOdd, fill = SolidColor(innerCloud))
        .addPath(pathData = addPathNodes(CORE), fill = SolidColor(heart))
        .build()

/**
 * Online (not downloaded) and liked, as one glyph. [heartColor] should be whatever colour the
 * plain liked heart uses in the same place, so the two read as the same "liked" mark; the cloud
 * layers are [cloudColor]: the heart-shaped cloud outline at full cloud strength (it reads as the
 * Cloud icon), the inner puff softer, like a cloud's shading.
 */
@Composable
fun CloudHeartIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    heartColor: Color = MaterialTheme.colorScheme.primary,
    cloudColor: Color = MaterialTheme.colorScheme.tertiary,
    contentDescription: String? = "Online & liked"
) {
    val vector = remember(heartColor, cloudColor) {
        cloudHeartVector(
            outerCloud = cloudColor.copy(alpha = cloudColor.alpha * 0.85f),
            innerCloud = cloudColor.copy(alpha = cloudColor.alpha * 0.55f),
            heart = heartColor
        )
    }
    Icon(
        imageVector = vector,
        contentDescription = contentDescription,
        tint = Color.Unspecified,
        modifier = modifier.size(size)
    )
}
