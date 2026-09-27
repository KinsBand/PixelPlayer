package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlin.math.roundToInt

/**
 * Opened from the broken-heart button in the player. Replaces the old "Mix controls" text button
 * that sat above the song title.
 *
 * Layout: discovery slider on top, the real "exclude from this mix" button in the middle with the
 * other per-song feedback options either side, and the housekeeping actions along the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixFeedbackBottomSheet(
    player: PlayerViewModel,
    songTitle: String?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    var discovery by remember { mutableFloatStateOf(player.mixDiscoveryBalance.toFloat()) }
    var variety by remember { mutableFloatStateOf(player.mixVariety.toFloat()) }
    var energy by remember { mutableStateOf(player.mixEnergyTarget) }
    var qualityReport by remember { mutableStateOf("") }
    var showWhy by remember { mutableStateOf(false) }
    /** "exclusions" or "history" while its confirmation dialog is showing. */
    var confirmReset by remember { mutableStateOf<String?>(null) }
    val activeFlavor by player.activeMixFlavor.collectAsStateWithLifecycle()
    val isLive = activeFlavor != null

    fun act(block: () -> Unit) {
        block()
        onDismiss()
    }

    // Both resets wipe what the mix has learned and can't be undone, so they ask first.
    confirmReset?.let { which ->
        val exclusions = which == "exclusions"
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text(if (exclusions) "Clear all exclusions?" else "Delete mix history?") },
            text = {
                Text(
                    if (exclusions) "Songs you excluded or marked \"heard too much\" can play in mixes again. This can't be undone."
                    else "Mixes forget what you've played, skipped and finished, and start learning again. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = null
                    act(if (exclusions) player::resetMixExclusions else player::resetMixLearning)
                }) { Text(if (exclusions) "Clear" else "Delete", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text("Cancel") } }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tune this mix",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (!songTitle.isNullOrBlank()) {
                Text(
                    text = songTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // --- Live status: tuning only reshapes the queue while a mix is running ---
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = { if (!isLive) player.activateSmartMix() },
                enabled = !isLive,
                shape = RoundedCornerShape(50),
                color = if (isLive) colors.tertiaryContainer else colors.surfaceContainerHighest,
                contentColor = if (isLive) colors.onTertiaryContainer else colors.onSurface
            ) {
                Text(
                    text = if (isLive) "Live · ${activeFlavor?.title} — upcoming songs update as you tune"
                    else "No mix playing · tap to start Smart Mix",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            // --- Top row: Familiar <-> Discover slider ---
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Familiar", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${(discovery * 100).toInt()}% discovery",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary
                        )
                        Text("Discover", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = discovery,
                        onValueChange = { discovery = it },
                        onValueChangeFinished = {
                            player.mixDiscoveryBalance = discovery.toDouble()
                            player.sendToast(
                                if (isLive) "Discovery ${(discovery * 100).toInt()}% · upcoming songs refreshed"
                                else "Discovery ${(discovery * 100).toInt()}% · applies when a mix starts"
                            )
                        }
                    )
                }
            }

            // --- Focused <-> Varied: how soon an artist may return ---
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Focused", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Same artist after ${(2 + variety * 6).roundToInt()} songs",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary
                        )
                        Text("Varied", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = variety,
                        onValueChange = { variety = it },
                        onValueChangeFinished = {
                            player.mixVariety = variety.toDouble()
                            if (isLive) player.sendToast("Variety updated · upcoming songs refreshed")
                        }
                    )
                }
            }

            // --- Energy: follow the music, or hold it calm / steady / hype ---
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Energy", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 4.dp))
                listOf<Pair<String, Double?>>("Follow" to null, "Calm" to 0.25, "Steady" to 0.55, "Hype" to 0.85)
                    .forEach { (label, value) ->
                        androidx.compose.material3.FilterChip(
                            selected = energy == value,
                            onClick = {
                                energy = value
                                player.mixEnergyTarget = value
                                if (isLive) player.sendToast("Energy: $label · upcoming songs refreshed")
                            },
                            label = { Text(label, maxLines = 1) }
                        )
                    }
            }

            // --- Middle: dislike in the centre, other song feedback around it ---
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MixFeedbackTile(
                        painter = rememberVectorPainter(Icons.Rounded.AutoAwesome),
                        label = "More like this",
                        onClick = { act(player::moreLikeCurrentSong) }
                    )
                    MixFeedbackTile(
                        painter = rememberVectorPainter(Icons.Rounded.Snooze),
                        label = "Heard too much",
                        onClick = { act(player::heardCurrentSongTooMuch) }
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = { act(player::dislikeCurrentSong) },
                        shape = CircleShape,
                        color = colors.errorContainer,
                        contentColor = colors.onErrorContainer,
                        modifier = Modifier.size(104.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Rounded.HeartBroken,
                                contentDescription = "Exclude from this mix and skip",
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Not for\nthis mix",
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MixFeedbackTile(
                        painter = rememberVectorPainter(Icons.Rounded.Block),
                        label = "Exclude everywhere",
                        destructive = true,
                        onClick = { act(player::dislikeCurrentSongEverywhere) }
                    )
                    MixFeedbackTile(
                        painter = rememberVectorPainter(Icons.AutoMirrored.Rounded.Undo),
                        label = "Undo last",
                        onClick = { act(player::undoMixFeedback) }
                    )
                }
            }

            // --- Bottom row: explain + housekeeping ---
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MixFeedbackTile(
                    painter = rememberVectorPainter(Icons.Rounded.Insights),
                    label = "Why these?",
                    compact = true,
                    active = showWhy,
                    modifier = Modifier.weight(1f),
                    onClick = { showWhy = !showWhy }
                )
                MixFeedbackTile(
                    painter = rememberVectorPainter(Icons.Rounded.RestartAlt),
                    label = "Clear exclusions",
                    compact = true,
                    modifier = Modifier.weight(1f),
                    onClick = { confirmReset = "exclusions" }
                )
                MixFeedbackTile(
                    painter = rememberVectorPainter(Icons.Rounded.DeleteOutline),
                    label = "Delete history",
                    compact = true,
                    destructive = true,
                    modifier = Modifier.weight(1f),
                    onClick = { confirmReset = "history" }
                )
            }

            AnimatedVisibility(visible = showWhy) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    // How the mix has been received lately, from real plays (loaded when opened).
                    androidx.compose.runtime.LaunchedEffect(showWhy) {
                        if (showWhy) qualityReport = player.mixQualityReport()
                    }
                    Text(
                        text = listOf(qualityReport, player.mixDecisionSummary())
                            .filter { it.isNotBlank() }
                            .joinToString("\n\n")
                            .ifBlank { "Start a mix to see its latest decisions." },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // --- Your feedback: every exclusion, snooze and removal, each undoable on its own ---
            val feedbackLog by player.mixFeedbackLog.collectAsStateWithLifecycle()
            if (feedbackLog.isNotEmpty()) {
                var showLog by remember { mutableStateOf(false) }
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = { showLog = !showLog },
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "Your feedback · ${feedbackLog.size}" + if (showLog) "" else " · tap to review",
                            style = MaterialTheme.typography.labelLarge
                        )
                        AnimatedVisibility(visible = showLog) {
                            Column(Modifier.padding(top = 8.dp)) {
                                feedbackLog.take(12).forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = item.title.ifBlank { "Unknown song" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.label,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                        TextButton(onClick = { player.undoMixFeedbackItem(item.id) }) { Text("Undo") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Discovery uses available candidates. Normal Mix stays in your library.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MixFeedbackTile(
    painter: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    compact: Boolean = false,
    destructive: Boolean = false,
    active: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        active -> colors.primaryContainer
        else -> colors.surfaceContainerHigh
    }
    val content = when {
        active -> colors.onPrimaryContainer
        destructive -> colors.error
        else -> colors.onSurface
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (compact) 18.dp else 22.dp),
        color = container,
        contentColor = content,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = if (compact) 10.dp else 14.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(painter, contentDescription = null, modifier = Modifier.size(if (compact) 20.dp else 24.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
