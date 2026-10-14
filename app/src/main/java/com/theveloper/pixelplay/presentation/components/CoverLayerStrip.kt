package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

private val ThumbSize = 48.dp
private val ThumbShape = RoundedCornerShape(14.dp)
private val ChipShape = RoundedCornerShape(18.dp)
/** Overlay chips have a fixed width so a drag can be turned into "moved N places". */
private val OverlayChipWidth = 84.dp
private val ChipSpacing = 8.dp

/**
 * Layer strip above the cover: Layer 1 (the photo) on the left, a divider, then the overlays
 * bottom → top. Tap a chip to focus that layer; drag an overlay by its handle to re-stack it;
 * the focused overlay shows a remove badge; [onAddLayer] adds a "+" tile at the end.
 */
@Composable
fun CoverLayerStrip(
    base: CoverArtLayer?,
    overlays: List<CoverArtLayer>,
    focusedLayerId: String?,
    onFocus: (String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddLayer: (() -> Unit)? = null,
    isAddingLayer: Boolean = false,
    /** Side padding inside the scrolling row (0 lines the chips up with the cover's edges). */
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp
) {
    val density = LocalDensity.current
    val stridePx = with(density) { (OverlayChipWidth + ChipSpacing).toPx() }
    val currentOverlays by rememberUpdatedState(overlays)
    val currentOnMove by rememberUpdatedState(onMove)
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState(), enabled = draggingId == null)
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (base != null) {
            LayerChip(
                layer = base,
                number = 1,
                selected = base.id == focusedLayerId,
                onClick = { onFocus(base.id) }
            )
        }

        VerticalDivider(
            modifier = Modifier
                .height(40.dp)
                .padding(horizontal = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        overlays.forEachIndexed { index, layer ->
            key(layer.id) {
                val isDragging = draggingId == layer.id
                val lift by animateFloatAsState(if (isDragging) 1f else 0f, label = "layer_chip_lift")
                LayerChip(
                    layer = layer,
                    number = index + 2,
                    selected = layer.id == focusedLayerId,
                    onClick = { onFocus(layer.id) },
                    onRemove = { onRemove(layer.id) },
                    modifier = Modifier
                        .width(OverlayChipWidth)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationX = if (isDragging) dragOffset else 0f
                            scaleX = 1f + 0.06f * lift
                            scaleY = 1f + 0.06f * lift
                            shadowElevation = 8.dp.toPx() * lift
                            shape = ChipShape
                            clip = false
                        },
                    dragHandle = {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(22.dp)
                                .pointerInput(layer.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingId = layer.id
                                            dragOffset = 0f
                                            onFocus(layer.id)
                                        },
                                        onDragEnd = { draggingId = null; dragOffset = 0f },
                                        onDragCancel = { draggingId = null; dragOffset = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.x
                                            val from = currentOverlays.indexOfFirst { it.id == layer.id }
                                            if (from < 0) return@detectDragGestures
                                            // Past half a chip: swap with the neighbour and
                                            // re-base the offset so the chip stays under the finger.
                                            if (dragOffset > stridePx / 2f && from < currentOverlays.lastIndex) {
                                                currentOnMove(from, from + 1)
                                                dragOffset -= stridePx
                                            } else if (dragOffset < -stridePx / 2f && from > 0) {
                                                currentOnMove(from, from - 1)
                                                dragOffset += stridePx
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.DragIndicator,
                                contentDescription = "Drag to reorder layer ${index + 2}",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }

        if (onAddLayer != null) {
            FilledTonalIconButton(
                onClick = onAddLayer,
                enabled = !isAddingLayer,
                modifier = Modifier.size(ThumbSize),
                shape = ThumbShape
            ) {
                if (isAddingLayer) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Add, contentDescription = "Add layer")
                }
            }
        }
    }
}

@Composable
private fun LayerChip(
    layer: CoverArtLayer,
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    dragHandle: (@Composable () -> Unit)? = null
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "layer_chip_container"
    )
    // No bounce here: an under-damped spring overshoots below 0 dp on the way out, and a
    // negative padding throws (that was the crash when adding a layer, which moves the focus).
    val ringWidthAnimated by animateDpAsState(
        targetValue = if (selected) 2.5.dp else 0.dp,
        animationSpec = spring(dampingRatio = 1f, stiffness = 600f),
        label = "layer_chip_ring"
    )
    val ringWidth = ringWidthAnimated.coerceAtLeast(0.dp)
    val look = remember(layer.filterId, layer.filterStrength, layer.adjust) { layer.look }
    val colorFilter = remember(look) {
        look.matrixOrNull?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
    }
    Row(
        modifier = modifier
            .height(ThumbSize + 12.dp)
            .clip(ChipShape)
            .background(container)
            .padding(PaddingValues(start = if (dragHandle != null) 2.dp else 6.dp, end = 6.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dragHandle?.invoke()
        Box(
            modifier = Modifier
                .size(ThumbSize)
                .then(
                    if (ringWidth > 0.dp) Modifier.border(ringWidth, MaterialTheme.colorScheme.primary, ThumbShape)
                    else Modifier
                )
                .padding(ringWidth)
                .clip(ThumbShape)
                .clickable(onClick = onClick)
        ) {
            Image(
                bitmap = layer.imageBitmap,
                contentDescription = "Layer $number",
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = if (layer.isFlipped) -1f else 1f }
                    .coverFilterEffect(look)
            )
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f))
                    .padding(horizontal = 5.dp)
            )
            if (onRemove != null) {
                // Remove badge pops in on the focused overlay. (Plain animated scale rather than
                // AnimatedVisibility: inside the chip's Row the RowScope overload would be picked.)
                val removeBadge by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = spring(dampingRatio = 0.6f),
                    label = "layer_remove_badge"
                )
                if (removeBadge > 0.01f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = removeBadge
                                scaleY = removeBadge
                                alpha = removeBadge.coerceIn(0f, 1f)
                            }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .clickable(enabled = selected, onClick = onRemove),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Remove layer $number",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
