package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight


import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.screens.LocalHighlightSettingKey
import com.theveloper.pixelplay.presentation.screens.LocalSettingsContainerColor

@Composable
internal fun SettingsSubsectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

@Composable
internal fun SettingsSubsection(
    title: String,
    addBottomSpace: Boolean = true,
    content: @Composable () -> Unit
) {
    SettingsSubsectionHeader(title)
    SettingsCard(content = content)
    if (addBottomSpace) {
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** The rounded container every settings group sits in. */
@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            CompositionLocalProvider(LocalSettingsContainerColor provides Color.Transparent) {
                content()
            }
        }
    }
}

/**
 * A group of rarely used settings, collapsed by default so each page leads with the
 * everyday options.
 *
 * Opens on its own when search deep-links to one of [containedKeys], otherwise the
 * highlighted row would be hidden inside a closed section. The open/closed state
 * survives rotation and process death.
 */
@Composable
internal fun SettingsAdvancedSection(
    title: String,
    containedKeys: Set<String> = emptySet(),
    addBottomSpace: Boolean = true,
    content: @Composable () -> Unit
) {
    val highlightKey = LocalHighlightSettingKey.current
    var expanded by rememberSaveable {
        mutableStateOf(highlightKey != null && highlightKey in containedKeys)
    }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "SettingsAdvancedChevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { expanded = !expanded }
            // Framework-localised expand/collapse actions, so TalkBack announces the
            // state in every locale without new strings.
            .semantics {
                heading()
                if (expanded) {
                    collapse { expanded = false; true }
                } else {
                    expand { expanded = true; true }
                }
            }
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            if (!expanded) {
                Text(
                    text = stringResource(R.string.settings_advanced_show),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(rotation)
        )
    }

    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column {
            Spacer(modifier = Modifier.height(4.dp))
            SettingsCard(content = content)
        }
    }
    if (addBottomSpace) {
        Spacer(modifier = Modifier.height(16.dp))
    }
}
