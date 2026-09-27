package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.dsp.CrossfeedStrength
import com.theveloper.pixelplay.data.dsp.FilterType
import com.theveloper.pixelplay.data.dsp.ParametricBand
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlin.math.roundToInt

@Composable
fun DspAudiophileSection(
    autoPreampEnabled: Boolean,
    manualPreampDb: Float,
    effectivePreampDb: Float,
    dspEngineEnabled: Boolean,
    isParametricMode: Boolean,
    parametricBands: List<ParametricBand>,
    subsonicEnabled: Boolean,
    subsonicCutoffHz: Float,
    ultrasonicEnabled: Boolean,
    ultrasonicCutoffHz: Float,
    crossfeedStrength: CrossfeedStrength,
    limiterEnabled: Boolean,
    softSaturationEnabled: Boolean,
    onAutoPreampToggled: (Boolean) -> Unit,
    onManualPreampChanged: (Float) -> Unit,
    onDspEngineToggled: (Boolean) -> Unit,
    onParametricModeToggled: (Boolean) -> Unit,
    onUpdateParametricBand: (ParametricBand) -> Unit,
    onAddParametricBand: () -> Unit,
    onRemoveParametricBand: (Int) -> Unit,
    onSubsonicFilterToggled: (Boolean, Float) -> Unit,
    onUltrasonicFilterToggled: (Boolean, Float) -> Unit,
    onCrossfeedChanged: (CrossfeedStrength) -> Unit,
    onLimiterSettingsChanged: (Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.equalizer_dsp_engine_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (dspEngineEnabled) "32-bit Float Pipeline • Active" else "Engine Bypassed",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dspEngineEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = dspEngineEnabled,
                        onCheckedChange = onDspEngineToggled
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Auto-Preamp / Headroom Compensation Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.equalizer_auto_preamp),
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.equalizer_auto_preamp_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = autoPreampEnabled,
                            onCheckedChange = onAutoPreampToggled
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pre-Amp Slider & Value Readout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.equalizer_preamp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val preampText = if (autoPreampEnabled) {
                            stringResource(R.string.equalizer_preamp_auto, effectivePreampDb)
                        } else {
                            stringResource(R.string.equalizer_preamp_manual, manualPreampDb)
                        }
                        Text(
                            text = preampText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = if (autoPreampEnabled) effectivePreampDb else manualPreampDb,
                        onValueChange = { onManualPreampChanged(it) },
                        valueRange = -15f..6f,
                        enabled = !autoPreampEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Expandable Audiophile DSP Tools
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // EQ Mode Selector (10-Band Graphic vs Parametric PEQ)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isParametricMode) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { onParametricModeToggled(false) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.equalizer_peq_mode_graphic),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (!isParametricMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isParametricMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isParametricMode) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { onParametricModeToggled(true) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.equalizer_peq_mode_parametric),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isParametricMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (isParametricMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Parametric Band List (shown when PEQ is active)
                    if (isParametricMode) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Parametric Filters (${parametricBands.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold
                            )
                            FilledTonalButton(
                                onClick = onAddParametricBand,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Add Band", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        parametricBands.forEach { band ->
                            ParametricBandEditorRow(
                                band = band,
                                onUpdateBand = onUpdateParametricBand,
                                onRemoveBand = { onRemoveParametricBand(band.id) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Subsonic Rumble Filter
                    DspToggleCard(
                        icon = Icons.Rounded.Waves,
                        title = stringResource(R.string.equalizer_subsonic_filter),
                        description = stringResource(R.string.equalizer_subsonic_desc),
                        checked = subsonicEnabled,
                        onCheckedChange = { onSubsonicFilterToggled(it, subsonicCutoffHz) }
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(20f, 25f, 30f, 40f).forEach { cutoff ->
                                FilterChip(
                                    selected = subsonicCutoffHz == cutoff,
                                    onClick = { onSubsonicFilterToggled(subsonicEnabled, cutoff) },
                                    label = { Text("${cutoff.toInt()} Hz", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Ultrasonic Smoothing Filter
                    DspToggleCard(
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(R.string.equalizer_ultrasonic_filter),
                        description = stringResource(R.string.equalizer_ultrasonic_desc),
                        checked = ultrasonicEnabled,
                        onCheckedChange = { onUltrasonicFilterToggled(it, ultrasonicCutoffHz) }
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(18000f, 20000f, 22000f).forEach { cutoff ->
                                FilterChip(
                                    selected = ultrasonicCutoffHz == cutoff,
                                    onClick = { onUltrasonicFilterToggled(ultrasonicEnabled, cutoff) },
                                    label = { Text("${(cutoff / 1000).toInt()} kHz", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stereo Crossfeed
                    DspToggleCard(
                        icon = Icons.Rounded.Headphones,
                        title = stringResource(R.string.equalizer_crossfeed),
                        description = stringResource(R.string.equalizer_crossfeed_desc),
                        checked = crossfeedStrength != CrossfeedStrength.OFF,
                        onCheckedChange = {
                            onCrossfeedChanged(if (it) CrossfeedStrength.MEDIUM else CrossfeedStrength.OFF)
                        }
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(CrossfeedStrength.SUBTLE, CrossfeedStrength.MEDIUM, CrossfeedStrength.STRONG).forEach { str ->
                                FilterChip(
                                    selected = crossfeedStrength == str,
                                    onClick = { onCrossfeedChanged(str) },
                                    label = { Text(str.displayName, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // True Peak Limiter & Soft Saturation
                    DspToggleCard(
                        icon = Icons.Rounded.Shield,
                        title = stringResource(R.string.equalizer_limiter),
                        description = stringResource(R.string.equalizer_limiter_desc),
                        checked = limiterEnabled,
                        onCheckedChange = { onLimiterSettingsChanged(it, softSaturationEnabled) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.equalizer_soft_saturation),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = softSaturationEnabled,
                                onCheckedChange = { onLimiterSettingsChanged(limiterEnabled, it) },
                                modifier = Modifier.size(width = 38.dp, height = 24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DspToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }

            if (checked && extraContent != null) {
                Spacer(modifier = Modifier.height(10.dp))
                extraContent()
            }
        }
    }
}

@Composable
private fun ParametricBandEditorRow(
    band: ParametricBand,
    onUpdateBand: (ParametricBand) -> Unit,
    onRemoveBand: () -> Unit
) {
    var expandedTypeMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.clickable { expandedTypeMenu = true }) {
                        Text(
                            text = "[${band.type.name}]",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        DropdownMenu(
                            expanded = expandedTypeMenu,
                            onDismissRequest = { expandedTypeMenu = false }
                        ) {
                            FilterType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        onUpdateBand(band.copy(type = type))
                                        expandedTypeMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "${band.frequency.toInt()} Hz • ${if (band.gainDb > 0) "+" else ""}${band.gainDb.roundToInt()} dB • Q: ${String.format("%.2f", band.q)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onRemoveBand, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Remove band",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Gain slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Gain", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = band.gainDb,
                    onValueChange = { onUpdateBand(band.copy(gainDb = it)) },
                    valueRange = -15f..15f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
