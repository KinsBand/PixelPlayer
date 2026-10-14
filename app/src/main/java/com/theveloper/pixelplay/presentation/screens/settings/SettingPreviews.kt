package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import com.theveloper.pixelplay.ui.theme.MotionTokens
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Small pictures of what a setting does, shown under its text for the current value.
 *
 * Every preview is a static mock-up drawn with colour roles, at one fixed height, so changing the
 * value crossfades the picture without resizing the row. Previews are decorative: TalkBack skips
 * them and reads the setting's title, subtitle and state instead.
 */
internal val SettingPreviewHeight = 88.dp

@Composable
internal fun <T> SettingPreviewFrame(
    value: T,
    indentForIcon: Boolean,
    inset: Boolean = true,
    content: @Composable (T) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (inset) {
                    // Lines up with the title text (16dp padding + 24dp icon + 12dp gap + 4dp).
                    Modifier.padding(start = if (indentForIcon) 56.dp else 16.dp, end = 16.dp, bottom = 14.dp)
                } else {
                    Modifier.padding(top = 12.dp)
                }
            )
            .height(SettingPreviewHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clearAndSetSemantics { }
    ) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                    fadeOut(tween(MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedAccelerate))
            },
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
            label = "settingPreview"
        ) { current ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content(current) }
        }
    }
}

// ---- Shared mock-up pieces --------------------------------------------------------------------

/** A tiny phone screen in the light or dark look, whichever the app is in right now. */
@Composable
private fun MiniScreen(light: Boolean, highlighted: Boolean, modifier: Modifier = Modifier) {
    val appIsDark = LocalPixelPlayDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    // The app's own scheme for its current mode, the inverse roles for the other one.
    val sameAsApp = light != appIsDark
    val bg = if (sameAsApp) scheme.surface else scheme.inverseSurface
    val fg = if (sameAsApp) scheme.onSurface else scheme.inverseOnSurface
    val accent = if (sameAsApp) scheme.primary else scheme.inversePrimary
    Column(
        modifier = modifier
            .fillMaxHeight()
            .aspectRatio(0.62f)
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .then(
                if (highlighted) Modifier.border(2.dp, scheme.primary, MaterialTheme.shapes.small)
                else Modifier.border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.small)
            )
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(MaterialTheme.shapes.extraSmall).background(accent))
        Bar(fg.copy(alpha = 0.8f), 0.8f)
        Bar(fg.copy(alpha = 0.45f), 0.55f)
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(fg.copy(alpha = 0.15f)))
    }
}

@Composable
private fun Bar(color: Color, fraction: Float, height: androidx.compose.ui.unit.Dp = 5.dp) {
    Box(Modifier.fillMaxWidth(fraction).height(height).clip(CircleShape).background(color))
}

/** A mini player strip: cover, two text lines, a play dot. */
@Composable
private fun MiniPlayerStrip(container: Color, content: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(26.dp).clip(MaterialTheme.shapes.small).background(content.copy(alpha = 0.35f)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Bar(content.copy(alpha = 0.85f), 0.7f)
            Bar(content.copy(alpha = 0.5f), 0.45f, 4.dp)
        }
        Box(Modifier.size(22.dp).clip(CircleShape).background(content.copy(alpha = 0.85f)))
    }
}

// ---- Appearance --------------------------------------------------------------------------------

/** Light · Follow · Dark: the screens that mode uses, outlined. Follow shows both. */
@Composable
internal fun ThemeModePreview(mode: String) {
    Row(
        modifier = Modifier.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val light = mode == AppThemeMode.LIGHT || mode == AppThemeMode.FOLLOW_SYSTEM
        val dark = mode == AppThemeMode.DARK || mode == AppThemeMode.FOLLOW_SYSTEM
        MiniScreen(light = true, highlighted = light)
        if (mode == AppThemeMode.FOLLOW_SYSTEM) {
            Text(
                "↔",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MiniScreen(light = false, highlighted = dark)
    }
}

/** Smooth corners on: a squircle card. Off: a plain rounded one. */
@Composable
internal fun SmoothCornersPreview(on: Boolean) {
    val shape = if (on) AbsoluteSmoothCornerShape(24.dp, 100) else RoundedCornerShape(24.dp)
    Box(
        Modifier
            .size(width = 120.dp, height = 64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(2.dp, MaterialTheme.colorScheme.primary, shape)
    )
}

/** Blur on: a frosted card over colour. Turned off: the same card, flat. */
@Composable
internal fun BlurPreview(blurDisabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (blurDisabled) Modifier else Modifier.blur(18.dp))
        ) {
            Box(Modifier.size(56.dp).offset(x = 40.dp, y = 8.dp).clip(CircleShape).background(scheme.primary))
            Box(Modifier.size(64.dp).offset(x = 150.dp, y = 20.dp).clip(CircleShape).background(scheme.tertiary))
        }
        Box(
            Modifier
                .size(width = 150.dp, height = 44.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    if (blurDisabled) scheme.surfaceContainerHigh
                    else scheme.surfaceContainerHigh.copy(alpha = 0.55f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Bar(scheme.onSurface.copy(alpha = 0.7f), 0.6f)
        }
    }
}

/** Song transition on: the new song's colour sweeps in from the left. Off: it just swaps. */
@Composable
internal fun MiniPlayerTransitionPreview(on: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        if (on) {
            Box(Modifier.fillMaxWidth().height(40.dp).clip(CircleShape)) {
                MiniPlayerStrip(scheme.primaryContainer, scheme.onPrimaryContainer, Modifier.fillMaxWidth())
                // The new song, half way through its sweep.
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(40.dp)
                        .background(
                            Brush.horizontalGradient(
                                0.85f to scheme.tertiaryContainer,
                                1f to scheme.tertiaryContainer.copy(alpha = 0f)
                            )
                        )
                )
            }
        } else {
            MiniPlayerStrip(scheme.tertiaryContainer, scheme.onTertiaryContainer, Modifier.fillMaxWidth())
        }
    }
}

/** Status bar: shown with time and icons, or gone. */
@Composable
internal fun StatusBarPreview(hidden: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().height(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (!hidden) {
                Text("12:30", style = MaterialTheme.typography.labelSmall, color = scheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { Box(Modifier.size(8.dp).clip(CircleShape).background(scheme.onSurface.copy(alpha = 0.7f))) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.small).background(scheme.surfaceContainerHighest))
    }
}

/** Gesture bar: the handle at the bottom, or gone. */
@Composable
internal fun GestureBarPreview(hidden: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.small).background(scheme.surfaceContainerHighest))
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .width(72.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(if (hidden) Color.Transparent else scheme.onSurface.copy(alpha = 0.7f))
        )
    }
}

// ---- Player & Lyrics ---------------------------------------------------------------------------

/** Lyrics on cover: synced lines over the artwork, current line bright. */
@Composable
internal fun CoverLyricsPreview(on: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .padding(vertical = 8.dp)
            .fillMaxHeight()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
        contentAlignment = Alignment.Center
    ) {
        if (on) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(scheme.scrim.copy(alpha = 0.35f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)
            ) {
                Bar(scheme.onPrimary.copy(alpha = 0.45f), 0.7f)
                Bar(scheme.onPrimary, 0.9f, 7.dp)
                Bar(scheme.onPrimary.copy(alpha = 0.45f), 0.6f)
            }
        }
    }
}

/** File info: codec, bit depth and sample rate under the seek bar. */
@Composable
internal fun FileInfoPreview(on: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(scheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(0.4f).fillMaxHeight().clip(CircleShape).background(scheme.primary))
        }
        Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            if (on) {
                Text(
                    "FLAC · 24-bit · 96 kHz",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(scheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/** Immersive lyrics: controls hidden and bigger text, or normal lyrics with controls. */
@Composable
internal fun ImmersiveLyricsPreview(on: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (on) {
            Spacer(Modifier.weight(1f))
            Bar(scheme.onSurface, 0.85f, 10.dp)
            Bar(scheme.onSurface.copy(alpha = 0.4f), 0.65f, 10.dp)
            Spacer(Modifier.weight(1f))
        } else {
            Bar(scheme.onSurface, 0.7f, 6.dp)
            Bar(scheme.onSurface.copy(alpha = 0.4f), 0.55f, 6.dp)
            Bar(scheme.onSurface.copy(alpha = 0.4f), 0.6f, 6.dp)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(scheme.onSurfaceVariant))
                Box(Modifier.size(22.dp).clip(CircleShape).background(scheme.primary))
                Box(Modifier.size(14.dp).clip(CircleShape).background(scheme.onSurfaceVariant))
            }
        }
    }
}
