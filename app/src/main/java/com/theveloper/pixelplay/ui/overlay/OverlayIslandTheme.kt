package com.theveloper.pixelplay.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.theveloper.pixelplay.ui.theme.Typography

/**
 * The app's theme for the island: PixelPlayer's typography (Google Sans Rounded) and the
 * song's dark album-art scheme. Every colour role the island uses is animated, so a track
 * change cross-fades the palette instead of snapping from one cover's colours to the next.
 */
@Composable
fun OverlayIslandTheme(
    scheme: ColorScheme?,
    content: @Composable () -> Unit
) {
    val target = scheme ?: IslandFallbackScheme
    val spec: AnimationSpec<Color> = remember { tween(durationMillis = 700, easing = FastOutSlowInEasing) }

    val primary by animateColorAsState(target.primary, spec, label = "islandPrimary")
    val onPrimary by animateColorAsState(target.onPrimary, spec, label = "islandOnPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, spec, label = "islandPrimaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "islandOnPrimaryContainer")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, spec, label = "islandSecondaryContainer")
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, spec, label = "islandOnSecondaryContainer")
    val surface by animateColorAsState(target.surface, spec, label = "islandSurface")
    val onSurface by animateColorAsState(target.onSurface, spec, label = "islandOnSurface")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, spec, label = "islandOnSurfaceVariant")
    val surfaceContainer by animateColorAsState(target.surfaceContainer, spec, label = "islandSurfaceContainer")
    val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, spec, label = "islandSurfaceContainerHigh")
    val surfaceContainerHighest by animateColorAsState(target.surfaceContainerHighest, spec, label = "islandSurfaceContainerHighest")
    val outline by animateColorAsState(target.outline, spec, label = "islandOutline")
    val outlineVariant by animateColorAsState(target.outlineVariant, spec, label = "islandOutlineVariant")
    val tertiary by animateColorAsState(target.tertiary, spec, label = "islandTertiary")

    val animated = target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        surface = surface,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        outline = outline,
        outlineVariant = outlineVariant,
        tertiary = tertiary
    )

    MaterialTheme(colorScheme = animated, typography = Typography, content = content)
}

/** Used until the first album-art scheme is ready: a neutral dark scheme. */
private val IslandFallbackScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    tertiary = Color(0xFFEFB8C8),
    onPrimaryContainer = Color(0xFFEADDFF),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
)
