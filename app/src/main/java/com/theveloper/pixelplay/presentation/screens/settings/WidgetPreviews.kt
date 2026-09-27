package com.theveloper.pixelplay.presentation.screens.settings

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.data.preferences.TurntableOptions
import com.theveloper.pixelplay.data.preferences.WidgetBackgroundStyle
import com.theveloper.pixelplay.data.preferences.WidgetConfig
import com.theveloper.pixelplay.data.preferences.WidgetKind
import com.theveloper.pixelplay.data.preferences.WidgetSpinMode
import com.theveloper.pixelplay.ui.glancewidget.TurntablePalette
import com.theveloper.pixelplay.ui.glancewidget.TurntableRenderer
import com.theveloper.pixelplay.ui.glancewidget.artworkKey
import com.theveloper.pixelplay.ui.glancewidget.resolveTurntableArtwork
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Live previews for the Visual Widgets screen.
 *
 * The turntable preview is not a mock-up: it asks [TurntableRenderer] for exactly the bitmap
 * the widget draws, so what is on screen here is what lands on the home screen. The rotation
 * is the one thing done differently — in-app Compose *can* animate, so the disc is spun with
 * a `graphicsLayer` instead of by re-rasterising a frame 8 times a second, with the fixed
 * sheen and tonearm composited over the top exactly as the widget composites them.
 *
 * The other three widgets are Compose approximations. Glance layouts cannot be hosted inside
 * an app, so these reproduce the parts a user is actually choosing between — background,
 * corner radius, accent, and which controls appear — and nothing more.
 */

/** Cell proportions each widget is previewed at, in home-screen cells. */
private val PREVIEW_CELLS: Map<WidgetKind, Pair<Int, Int>> = mapOf(
    WidgetKind.ADAPTIVE to (4 to 2),
    WidgetKind.BAR_4X1 to (4 to 1),
    WidgetKind.CONTROL_4X2 to (4 to 2),
    WidgetKind.GRID_2X2 to (2 to 2),
    WidgetKind.TURNTABLE to (2 to 2),
)

/** One home-screen cell, at preview scale. */
private val PREVIEW_CELL = 42.dp

/** Sizes the turntable is shown at, to make the "adapts to any size" claim checkable. */
private val TURNTABLE_SIZE_STRIP = listOf(1 to 1, 2 to 2, 4 to 2, 3 to 3)

/**
 * A horizontally scrolling strip of every widget, with the selected one outlined.
 * Doubles as the picker — tapping a card is what the appearance controls below apply to.
 */
@Composable
internal fun WidgetPreviewStrip(
    configs: Map<WidgetKind, WidgetConfig>,
    playerInfo: PlayerInfo,
    selectedKind: WidgetKind,
    onSelect: (WidgetKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WidgetKind.entries.forEach { kind ->
                val (cw, ch) = PREVIEW_CELLS.getValue(kind)
                WidgetPreviewCard(
                    kind = kind,
                    config = configs[kind] ?: WidgetConfig(kind = kind),
                    playerInfo = playerInfo,
                    width = PREVIEW_CELL * cw,
                    height = PREVIEW_CELL * ch,
                    selected = kind == selectedKind,
                    onClick = { onSelect(kind) },
                )
            }
        }

        if (selectedKind == WidgetKind.TURNTABLE) {
            Spacer(Modifier.height(4.dp))
            TurntableSizeStrip(
                config = configs[WidgetKind.TURNTABLE] ?: WidgetConfig(kind = WidgetKind.TURNTABLE),
                playerInfo = playerInfo,
            )
        }

        Text(
            text = stringResource(R.string.settings_widgets_preview_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/**
 * The selected widget, large, on a wallpaper-like stage: the fixed top of the Widgets page.
 * The stage is always two cells tall so switching widgets never moves what's below it.
 */
@Composable
internal fun WidgetMainPreview(
    kind: WidgetKind,
    config: WidgetConfig,
    playerInfo: PlayerInfo,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cell = minOf((maxWidth - 48.dp) / 4, 68.dp)
        Surface(
            shape = RoundedCornerShape(28.dp),
            // Stands in for a wallpaper, so transparent backgrounds are visibly transparent.
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(cell * 2 + 40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedContent(
                    targetState = kind,
                    label = "widgetMainPreview",
                ) { shown ->
                    val (w, h) = PREVIEW_CELLS.getValue(shown)
                    Box(Modifier.width(cell * w).height(cell * h), contentAlignment = Alignment.Center) {
                        WidgetPreview(kind = shown, config = if (shown == kind) config else WidgetConfig(kind = shown), playerInfo = playerInfo)
                    }
                }
            }
        }
    }
}

/** A fixed row of widget names under the main preview: picks which widget is being edited. */
@Composable
internal fun WidgetKindSelector(
    selectedKind: WidgetKind,
    onSelect: (WidgetKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WidgetKind.entries.forEach { kind ->
            val selected = kind == selectedKind
            Surface(
                onClick = { onSelect(kind) },
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = stringResource(kind.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun WidgetPreviewCard(
    kind: WidgetKind,
    config: WidgetConfig,
    playerInfo: PlayerInfo,
    width: Dp,
    height: Dp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            // Stands in for a wallpaper, so transparent backgrounds are visibly transparent.
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = border,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick),
        ) {
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .width(width)
                    .height(height),
                contentAlignment = Alignment.Center,
            ) {
                WidgetPreview(kind = kind, config = config, playerInfo = playerInfo)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(kind.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(width),
        )
    }
}

/** The turntable at four cell shapes, so the responsive claim can be eyeballed. */
@Composable
private fun TurntableSizeStrip(config: WidgetConfig, playerInfo: PlayerInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TURNTABLE_SIZE_STRIP.forEach { (cw, ch) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .width(PREVIEW_CELL * cw)
                        .height(PREVIEW_CELL * ch),
                    contentAlignment = Alignment.Center,
                ) {
                    TurntablePreview(config = config, playerInfo = playerInfo)
                }
                Text(
                    text = "${cw}x$ch",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WidgetPreview(kind: WidgetKind, config: WidgetConfig, playerInfo: PlayerInfo) {
    when (kind) {
        WidgetKind.TURNTABLE -> TurntablePreview(config = config, playerInfo = playerInfo)
        else -> ContentWidgetPreview(kind = kind, config = config, playerInfo = playerInfo)
    }
}

// -------------------------------------------------------------------------------------
// Turntable
// -------------------------------------------------------------------------------------

@Composable
private fun TurntablePreview(config: WidgetConfig, playerInfo: PlayerInfo) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val options = config.turntable

    BoxWithStage { stage ->
        val discDp = stage * options.discScale.coerceIn(
            TurntableOptions.MIN_DISC_SCALE,
            TurntableOptions.MAX_DISC_SCALE,
        )
        val discPx = with(density) { discDp.toPx() }.toInt()
            .coerceIn(1, TurntableRenderer.MAX_RENDER_PX)
        val labelPx = max(1, (discPx * options.labelScale).toInt())

        val palette = remember(config.appearance, playerInfo.themeColors, context) {
            TurntablePalette.resolve(context, config.appearance, playerInfo)
        }

        val spec = remember(discPx, options, palette, playerInfo.albumArtUri) {
            TurntableRenderer.DiscSpec(
                sizePx = discPx,
                discColor = palette.disc,
                labelColor = palette.label,
                labelGlyphColor = palette.labelGlyph,
                grooveColor = palette.groove,
                labelFraction = options.labelScale,
                showGrooves = options.showGrooves,
                showSheen = options.showSheen,
                showTonearm = options.showTonearm,
                tonearmColor = palette.tonearm,
                artworkKey = artworkKey(playerInfo),
            )
        }

        val base: Bitmap? = remember(spec) {
            TurntableRenderer.renderBase(spec) {
                TurntableRenderer.prepareArtwork(
                    source = resolveTurntableArtwork(context, playerInfo, labelPx),
                    labelPx = labelPx,
                )
            }
        }
        val overlay: Bitmap? = remember(spec) { TurntableRenderer.renderOverlay(spec) }

        val angle = rememberSpinAngle(options)

        Box(modifier = Modifier.size(stage), contentAlignment = Alignment.Center) {
            if (base != null) {
                androidx.compose.foundation.Image(
                    bitmap = base.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    // Read inside the layer block, so a turning record costs a layer update
                    // per frame rather than a recomposition per frame.
                    modifier = Modifier
                        .size(discDp)
                        .graphicsLayer { rotationZ = angle() },
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(discDp)
                        .clip(CircleShape)
                        .background(Color(config.appearance.fixedAccentColor)),
                )
            }
            if (overlay != null) {
                androidx.compose.foundation.Image(
                    bitmap = overlay.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(discDp),
                )
            }

            if (options.showBadges) {
                val favourite = discDp * 0.26f
                val play = discDp * 0.32f
                if (config.content.showFavorite) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = edgeInset(stage, discDp, favourite),
                                end = edgeInset(stage, discDp, favourite),
                            ),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        PreviewBadge(
                            size = favourite,
                            shape = CircleShape,
                            background = Color(palette.favoriteBadgeBackground),
                        ) {
                            Icon(
                                Icons.Rounded.FavoriteBorder,
                                contentDescription = null,
                                tint = Color(palette.favoriteBadgeIcon),
                                modifier = Modifier.size(favourite * 0.52f),
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            bottom = edgeInset(stage, discDp, play),
                            start = edgeInset(stage, discDp, play),
                        ),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    PreviewBadge(
                        size = play,
                        shape = RoundedCornerShape(play * 0.3f),
                        background = Color(palette.playBadgeBackground),
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color(palette.playBadgeIcon),
                            modifier = Modifier.size(play * 0.5f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The preview's rotation.
 *
 * Returned as a lambda rather than a `Float` so callers read it inside a `graphicsLayer`
 * block: that defers the snapshot read to the layer phase, and a spinning preview then costs
 * no recomposition at all. Returns a constant when the record is set not to turn, which also
 * avoids leaving an infinite animation running behind a static disc.
 */
@Composable
private fun rememberSpinAngle(options: TurntableOptions): () -> Float {
    if (options.spinMode == WidgetSpinMode.OFF) return { 0f }

    val transition = rememberInfiniteTransition(label = "turntable")
    // A full turn takes 60/rpm seconds, so the preview runs at the speed the widget will.
    val turnMillis = (60_000f / options.spinRpm.coerceAtLeast(0.5f)).toInt().coerceAtLeast(500)
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (options.clockwise) 360f else -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = turnMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "turntableAngle",
    )
    return { angle }
}

@Composable
private fun PreviewBadge(
    size: Dp,
    shape: androidx.compose.ui.graphics.Shape,
    background: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Mirrors `TurntableWidget.edgeInset`: the distance from the stage's corner at which a bubble
 * sits centred on the record's 45° edge. Duplicated rather than shared because the widget's
 * copy works in Glance's `Dp` and this one in Compose's, and the formula is two lines.
 */
private fun edgeInset(stage: Dp, disc: Dp, badge: Dp): Dp {
    val projected = (disc.value / 2f) / sqrt(2f)
    return max(0f, stage.value / 2f - projected - badge.value / 2f).dp
}

// -------------------------------------------------------------------------------------
// The other widgets
// -------------------------------------------------------------------------------------

@Composable
private fun ContentWidgetPreview(
    kind: WidgetKind,
    config: WidgetConfig,
    playerInfo: PlayerInfo,
) {
    val appearance = config.appearance
    val content = config.content
    val shape = RoundedCornerShape(appearance.cornerRadiusDp.dp)

    val surface = when (appearance.backgroundStyle) {
        WidgetBackgroundStyle.SOLID -> MaterialTheme.colorScheme.surfaceContainer
        WidgetBackgroundStyle.TRANSPARENT -> Color.Transparent
        WidgetBackgroundStyle.OUTLINED -> Color.Transparent
    }
    val outline = if (appearance.backgroundStyle == WidgetBackgroundStyle.OUTLINED) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(surface)
            .then(outline)
            .padding(8.dp),
    ) {
        val compact = kind == WidgetKind.BAR_4X1
        val square = kind == WidgetKind.GRID_2X2

        if (square) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ArtworkTile(Modifier.weight(1f).fillMaxSize())
                    TransportTile(Modifier.weight(1f).fillMaxSize(), Icons.Rounded.PlayArrow)
                }
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TransportTile(Modifier.weight(1f).fillMaxSize(), Icons.Rounded.SkipPrevious)
                    TransportTile(Modifier.weight(1f).fillMaxSize(), Icons.Rounded.SkipNext)
                }
            }
            return@Box
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArtworkTile(Modifier.size(if (compact) 24.dp else 40.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (content.showTitle) {
                    Text(
                        text = playerInfo.songTitle.ifEmpty {
                            stringResource(R.string.widget_preview_song_title)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (content.showArtist) {
                    Text(
                        text = playerInfo.artistName.ifEmpty {
                            stringResource(R.string.widget_preview_artist)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (content.showPrevNext) {
                TransportTile(Modifier.size(if (compact) 22.dp else 28.dp), Icons.Rounded.SkipPrevious)
            }
            TransportTile(Modifier.size(if (compact) 22.dp else 28.dp), Icons.Rounded.PlayArrow)
            if (content.showPrevNext) {
                TransportTile(Modifier.size(if (compact) 22.dp else 28.dp), Icons.Rounded.SkipNext)
            }
        }
    }
}

@Composable
private fun ArtworkTile(modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun TransportTile(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Gives its content the shorter edge of the available space, which is the same quantity the
 * widget derives every one of its dimensions from.
 */
@Composable
private fun BoxWithStage(content: @Composable (stage: Dp) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        content(minOf(maxWidth, maxHeight))
    }
}
