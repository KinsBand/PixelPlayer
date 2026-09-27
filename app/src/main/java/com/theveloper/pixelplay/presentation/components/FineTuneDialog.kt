package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * Educational zone names for 10-band ISO frequencies.
 */
object EducationalFrequencyMarkers {
    fun getZoneName(bandIndex: Int): String {
        return when (bandIndex) {
            0, 1 -> "Sub-Bass"
            2 -> "Bass"
            3 -> "Low Mids"
            4, 5 -> "Midrange"
            6 -> "High Mids"
            7 -> "Presence"
            8, 9 -> "Brilliance"
            else -> "Frequency"
        }
    }

    fun getZoneDescription(bandIndex: Int): String {
        return when (bandIndex) {
            0, 1 -> "Sub-bass rumble and visceral vibration (31-62 Hz)"
            2 -> "Bass punch, kick drum body and bassline weight (125 Hz)"
            3 -> "Warmth, guitar resonance and lower vocal body (250 Hz)"
            4, 5 -> "Core vocal clarity, snare body and instrument definition (500 Hz - 1 kHz)"
            6 -> "Vocal attack, horn bite and acoustic string attack (2 kHz)"
            7 -> "Vocal intimacy, presence and snap (4 kHz)"
            8, 9 -> "Air, shimmer, cymbals and acoustic sparkle (8-16 kHz)"
            else -> ""
        }
    }
}

@Composable
fun FineTuneDialog(
    bandIndex: Int,
    frequency: String,
    currentLevel: Int,
    onAdjustLevel: (Int) -> Unit,
    onResetToZero: () -> Unit,
    onDismiss: () -> Unit
) {
    val zoneName = EducationalFrequencyMarkers.getZoneName(bandIndex)
    val zoneDesc = EducationalFrequencyMarkers.getZoneDescription(bandIndex)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Column {
                Text(
                    text = "$frequency • $zoneName",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (zoneDesc.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = zoneDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Big gain readout
                val displayLevel = if (currentLevel > 0) "+$currentLevel dB" else "$currentLevel dB"
                Text(
                    text = displayLevel,
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = if (currentLevel != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Stepper Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = { onAdjustLevel(-1) },
                        modifier = Modifier.size(54.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(imageVector = Icons.Rounded.Remove, contentDescription = "-1 dB")
                    }

                    FilledIconButton(
                        onClick = { onAdjustLevel(1) },
                        modifier = Modifier.size(54.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(imageVector = Icons.Rounded.Add, contentDescription = "+1 dB")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reset to 0 dB button
                OutlinedButton(
                    onClick = onResetToZero,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.equalizer_reset_band),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = stringResource(R.string.equalizer_fine_tune_done))
            }
        }
    )
}
