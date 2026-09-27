package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.dsp.AuditionSlot
import com.theveloper.pixelplay.data.dsp.AuditionState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

@Composable
fun AuditionBar(
    auditionState: AuditionState,
    onSelectSlot: (AuditionSlot) -> Unit,
    onCopyToSlot: (AuditionSlot) -> Unit,
    onToggleLoudnessComp: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title & Loudness Compensation Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CompareArrows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.equalizer_audition_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Loudness Match Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.equalizer_loudness_comp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = auditionState.isLoudnessCompEnabled,
                        onCheckedChange = onToggleLoudnessComp,
                        modifier = Modifier.size(width = 38.dp, height = 24.dp),
                        thumbContent = {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Slot Selector Segment
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AuditionSlotPill(
                    title = stringResource(R.string.equalizer_audition_slot_a),
                    isSelected = auditionState.activeSlot == AuditionSlot.SLOT_A,
                    onClick = { onSelectSlot(AuditionSlot.SLOT_A) },
                    onCopyCurrent = { onCopyToSlot(AuditionSlot.SLOT_A) },
                    showCopy = true,
                    modifier = Modifier.weight(1f)
                )

                AuditionSlotPill(
                    title = stringResource(R.string.equalizer_audition_slot_b),
                    isSelected = auditionState.activeSlot == AuditionSlot.SLOT_B,
                    onClick = { onSelectSlot(AuditionSlot.SLOT_B) },
                    onCopyCurrent = { onCopyToSlot(AuditionSlot.SLOT_B) },
                    showCopy = true,
                    modifier = Modifier.weight(1f)
                )

                AuditionSlotPill(
                    title = stringResource(R.string.equalizer_audition_bypass),
                    isSelected = auditionState.activeSlot == AuditionSlot.BYPASS,
                    onClick = { onSelectSlot(AuditionSlot.BYPASS) },
                    onCopyCurrent = {},
                    showCopy = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AuditionSlotPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onCopyCurrent: () -> Unit,
    showCopy: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "AuditionBgColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "AuditionTextColor"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )

        if (showCopy) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = "Copy current",
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(14.dp)
                    .clickable(onClick = onCopyCurrent)
            )
        }
    }
}
