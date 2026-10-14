package com.theveloper.pixelplay.presentation.components.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion

/** How the Instruments (tab) page is coloured: like the rest of the app, or always light / dark. */
enum class TabAppearance(val key: String, val label: String) {
    FollowApp("follow", "Follow app"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    companion object {
        fun fromKey(key: String?): TabAppearance = entries.firstOrNull { it.key == key } ?: FollowApp
    }
}

/** "Tab appearance": Follow app · Light · Dark, in the Instruments options. */
@Composable
internal fun TabAppearanceOption(
    appearance: TabAppearance,
    onAppearanceChange: (TabAppearance) -> Unit,
    contentColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    itemBackgroundColor: Color,
) {
    val reducedMotion = rememberSystemReducedMotion()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(itemBackgroundColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text("Tab appearance", color = contentColor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Colours of this page only",
                color = contentColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TabAppearance.entries.forEach { option ->
                val selected = option == appearance
                val spec = if (reducedMotion) snap<Color>() else tween<Color>(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized)
                val bg by animateColorAsState(if (selected) accentColor else contentColor.copy(alpha = 0.06f), spec, label = "tabAppearanceBg")
                val fg by animateColorAsState(if (selected) onAccentColor else contentColor, spec, label = "tabAppearanceFg")
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .semantics {
                            role = Role.RadioButton
                            this.selected = selected
                        }
                        .clickable { onAppearanceChange(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(option.label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
