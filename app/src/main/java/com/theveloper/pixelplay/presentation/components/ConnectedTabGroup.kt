package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion

/** Height of [ConnectedTabGroup]; also a 48dp touch target. */
val ConnectedTabGroupHeight = 48.dp

/**
 * Material 3 Expressive connected button group used as tabs: joined buttons 2dp apart, the outer
 * corners round and the inner ones tight. The selected one fills with primary and becomes fully
 * round. No check icon. Used on the artist page (Music · Events · Merch) and in the friend sheet
 * (History · Top this week) so tabs look the same everywhere.
 */
@Composable
fun ConnectedTabGroup(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Group height; the artist page uses a compact 40 dp so its docked tabs take less room. */
    height: Dp = ConnectedTabGroupHeight,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(height).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            ConnectedTab(
                label = label,
                selected = index == selectedIndex,
                first = index == 0,
                last = index == labels.lastIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val OuterCorner = 24.dp
private val InnerCorner = 8.dp

@Composable
private fun ConnectedTab(
    label: String,
    selected: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberSystemReducedMotion()
    fun corner(outer: Boolean): Dp = if (selected || outer) OuterCorner else InnerCorner
    val spec = if (reduced) snap<Dp>() else tween<Dp>(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized)
    val start by animateDpAsState(corner(first), spec, label = "tabStartCorner")
    val end by animateDpAsState(corner(last), spec, label = "tabEndCorner")
    val colors = MaterialTheme.colorScheme
    val colorSpec = if (reduced) snap<Color>() else
        tween<Color>(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized)
    val container by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh, colorSpec, label = "tabContainer")
    val content by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, colorSpec, label = "tabContent")
    val shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(container)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
