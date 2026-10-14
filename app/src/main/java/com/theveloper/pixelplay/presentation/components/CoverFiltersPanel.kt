package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brightness2
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Lens
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Details
import androidx.compose.material.icons.rounded.HdrOn
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.WbIncandescent
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Tonality
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vignette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared spring for the cover Adjust screen's layout motion (expressive, slightly under-damped). */
internal fun <T> coverEditLayoutSpring() = spring<T>(
    dampingRatio = 0.85f,
    stiffness = Spring.StiffnessMediumLow
)

/** The Adjust tab sits before the preset categories; it isn't a preset category itself. */
private const val ADJUST_TAB_ID = "adjust"

/** What the Adjust screen's slider controls: the preset's strength or one tool. */
sealed interface CoverSliderTarget {
    data object Strength : CoverSliderTarget
    data class Tool(val tool: CoverAdjustTool) : CoverSliderTarget
}

/**
 * Which slider is open, hoisted so the screen can draw the ruler, the value bubble and the reset
 * button around Apply (outside the panel). [shown] keeps the last target while the controls close.
 */
@Stable
class CoverSliderState {
    var target by mutableStateOf<CoverSliderTarget?>(null)
    var shown by mutableStateOf<CoverSliderTarget>(CoverSliderTarget.Strength)
        private set

    fun open(next: CoverSliderTarget?) {
        if (next != null) shown = next
        target = next
    }
}

/** The slider spec for [target] on [layer], or null when there's nothing to adjust. */
fun coverSliderSpec(
    layer: CoverArtLayer?,
    target: CoverSliderTarget,
    onFilterSelect: (String?) -> Unit,
    onStrengthChange: (Int) -> Unit,
    onAdjustChange: (CoverAdjustTool, Int) -> Unit,
): CoverSliderSpec? {
    layer ?: return null
    return when (target) {
        CoverSliderTarget.Strength -> CoverSliderSpec(
            key = "strength",
            value = layer.filterStrength,
            range = 0..100,
            default = 100,
            icon = Icons.Rounded.Tune,
            label = "Strength",
            onValueChange = onStrengthChange,
            // Strength 0 = no preset: clear it once the finger lifts.
            onValueChangeFinished = { settled -> if (settled <= 0) onFilterSelect(null) }
        )
        is CoverSliderTarget.Tool -> CoverSliderSpec(
            key = target.tool.id,
            value = layer.adjust[target.tool],
            range = target.tool.min..target.tool.max,
            default = 0,
            icon = target.tool.icon,
            label = target.tool.label,
            onValueChange = { onAdjustChange(target.tool, it) }
        )
    }
}

val CoverAdjustTool.icon: ImageVector
    get() = when (this) {
        CoverAdjustTool.Brightness -> Icons.Rounded.Brightness6
        CoverAdjustTool.Contrast -> Icons.Rounded.Tonality
        CoverAdjustTool.UltraHdr -> Icons.Rounded.HdrOn
        CoverAdjustTool.Tone -> Icons.Rounded.Landscape
        CoverAdjustTool.WhitePoint -> Icons.Rounded.Lens
        CoverAdjustTool.Highlights -> Icons.Rounded.WbSunny
        CoverAdjustTool.Shadows -> Icons.Rounded.Brightness2
        CoverAdjustTool.BlackPoint -> Icons.Rounded.RadioButtonUnchecked
        CoverAdjustTool.Saturation -> Icons.Rounded.InvertColors
        CoverAdjustTool.Warmth -> Icons.Rounded.WbIncandescent
        CoverAdjustTool.Tint -> Icons.Rounded.Colorize
        CoverAdjustTool.BlueTone -> Icons.Rounded.Cloud
        CoverAdjustTool.Vignette -> Icons.Rounded.Vignette
        CoverAdjustTool.Pop -> Icons.Rounded.AutoAwesome
        CoverAdjustTool.Sharpen -> Icons.Rounded.Details
        CoverAdjustTool.Denoise -> Icons.Rounded.BlurOn
    }

/**
 * Filters panel of the cover Adjust screen, for one layer: section tabs (Adjust + the preset
 * categories), the preset cards or tool tiles, and the value slider under them.
 *
 * Presets: first tap applies one at 100 (the card shows its strength); tapping it again opens
 * the strength slider; "Original" or dragging strength to 0 removes it. Tools: a tap opens the
 * slider for that tool; a tile shows its value once it's been moved. The slider itself (ruler,
 * value and reset) is drawn by the screen around Apply, from [sliderState].
 */
@Composable
fun CoverFiltersPanel(
    layer: CoverArtLayer?,
    onFilterSelect: (String?) -> Unit,
    onStrengthChange: (Int) -> Unit,
    onAdjustChange: (CoverAdjustTool, Int) -> Unit,
    sliderState: CoverSliderState,
    modifier: Modifier = Modifier
) {
    val selectedFilterId = layer?.filterId
    var tabId by rememberSaveable {
        mutableStateOf(CoverFilters.byId(selectedFilterId)?.categoryId ?: ADJUST_TAB_ID)
    }
    val sliderTarget = sliderState.target
    // Another layer focused: start over.
    LaunchedEffect(layer?.id) { sliderState.open(null) }
    // The preset went away (Original, strength 0, reset): nothing left to set the strength of.
    LaunchedEffect(selectedFilterId) {
        if (selectedFilterId == null && sliderState.target == CoverSliderTarget.Strength) sliderState.open(null)
    }

    // Small copy for the preset cards so the row never draws the full-resolution photo.
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, layer?.imageBitmap) {
        value = layer?.imageBitmap?.let { source ->
            withContext(Dispatchers.Default) { source.toFilterThumbnail() }
        }
    }
    val tabs = remember { listOf(CoverFilterCategory(ADJUST_TAB_ID, "Adjust")) + CoverFilters.categories }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEach { tab ->
                FilterChip(
                    selected = tab.id == tabId,
                    onClick = {
                        if (tab.id != tabId) {
                            tabId = tab.id
                            sliderState.open(null)
                        }
                    },
                    label = { Text(tab.label) },
                    shape = CircleShape
                )
            }
        }

        // Presets or tools
        AnimatedContent(
            targetState = tabId,
            transitionSpec = {
                (fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)) +
                    slideInHorizontally(animationSpec = coverEditLayoutSpring()) { it / 8 }) togetherWith
                    fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
            },
            label = "filter_panel_row"
        ) { tab ->
            if (tab == ADJUST_TAB_ID) {
                val tools = remember { CoverAdjustTool.available }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tools, key = { it.id }) { tool ->
                        val selected = (sliderTarget as? CoverSliderTarget.Tool)?.tool == tool
                        CoverAdjustToolTile(
                            tool = tool,
                            value = layer?.adjust?.get(tool) ?: 0,
                            selected = selected,
                            enabled = layer != null,
                            onClick = { sliderState.open(if (selected) null else CoverSliderTarget.Tool(tool)) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            } else {
                val presets = remember(tab) { CoverFilters.inCategory(tab) }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "original") {
                        CoverFilterCard(
                            preset = null,
                            thumbnail = thumbnail,
                            isFlipped = layer?.isFlipped ?: false,
                            rotationDegrees = layer?.normalizedRotation ?: 0,
                            selected = selectedFilterId == null,
                            strength = null,
                            onClick = {
                                onFilterSelect(null)
                                sliderState.open(null)
                            }
                        )
                    }
                    items(presets, key = { it.id }) { preset ->
                        val isSelected = preset.id == selectedFilterId
                        CoverFilterCard(
                            preset = preset,
                            thumbnail = thumbnail,
                            isFlipped = layer?.isFlipped ?: false,
                            rotationDegrees = layer?.normalizedRotation ?: 0,
                            selected = isSelected,
                            strength = if (isSelected) layer?.filterStrength else null,
                            onClick = {
                                if (isSelected) {
                                    // Second tap: the strength slider (again: close it).
                                    sliderState.open(if (sliderTarget == CoverSliderTarget.Strength) null else CoverSliderTarget.Strength)
                                } else {
                                    onFilterSelect(preset.id)
                                    sliderState.open(null)
                                }
                            }
                        )
                    }
                }
            }
        }

    }
}

/** One Adjust tool: label above, icon tile (its value instead of the icon once moved). */
@Composable
private fun CoverAdjustToolTile(
    tool: CoverAdjustTool,
    value: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val colorSpec = tween<Color>(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized)
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        animationSpec = colorSpec,
        label = "adjust_tile_container"
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onPrimaryContainer else colors.onSurface,
        animationSpec = colorSpec,
        label = "adjust_tile_content"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(84.dp)
    ) {
        Text(
            text = tool.label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(MaterialTheme.shapes.large)
                .background(container)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Adjust ${tool.label}", onClick = onClick)
                .semantics {
                    stateDescription = if (value != 0) "${tool.label} $value" else "${tool.label}, unchanged"
                },
            contentAlignment = Alignment.Center
        ) {
            if (value != 0) {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = content,
                    maxLines = 1
                )
            } else {
                Icon(tool.icon, contentDescription = null, tint = content)
            }
        }
    }
}

/**
 * Preset card ([preset] null = "Original"). When selected, the thumbnail shows the preset's
 * [strength] over a scrim.
 */
@Composable
private fun CoverFilterCard(
    preset: CoverFilterPreset?,
    thumbnail: ImageBitmap?,
    isFlipped: Boolean,
    rotationDegrees: Int,
    selected: Boolean,
    strength: Int?,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val cardShape = MaterialTheme.shapes.large
    val label = preset?.label ?: "Original"
    val colorFilter = remember(preset) { preset?.let { ColorFilter.colorMatrix(ColorMatrix(it.activeMatrix)) } }
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.primary else Color.Transparent,
        animationSpec = tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized),
        label = "filter_card_border"
    )
    val cardScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "filter_card_scale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .clip(cardShape)
                .background(colors.surfaceContainerHighest)
                .border(3.dp, borderColor, cardShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    stateDescription = when {
                        !selected -> "Not selected"
                        strength != null -> "Selected, strength $strength"
                        else -> "Selected"
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rotationDegrees.toFloat()
                            scaleX = if (isFlipped) -1f else 1f
                        }
                        .coverFilterEffect(preset)
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = selected && strength != null,
                enter = fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                    scaleIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate), initialScale = 0.8f),
                exit = fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)) +
                    scaleOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate), targetScale = 0.8f),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.scrim.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary) {
                        Text(
                            text = (strength ?: 100).toString(),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun ImageBitmap.toFilterThumbnail(maxSide: Int = 220): ImageBitmap {
    val source = asAndroidBitmap()
    val longest = maxOf(source.width, source.height)
    if (longest <= maxSide) return this
    val ratio = maxSide.toFloat() / longest
    return android.graphics.Bitmap.createScaledBitmap(
        source,
        (source.width * ratio).toInt().coerceAtLeast(1),
        (source.height * ratio).toInt().coerceAtLeast(1),
        true
    ).asImageBitmap()
}
