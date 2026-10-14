package com.theveloper.pixelplay.presentation.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.dsp.LiveSpectrumAnalyzer
import kotlinx.coroutines.flow.first
import kotlin.math.max
import kotlin.math.min

/**
 * Live spectrum for the Equalizer graph card. Capture only runs while the screen is in the
 * foreground, this view is showing and something is playing.
 */
@Composable
internal fun EqualizerSpectrum(
    audioSessionId: Int,
    isPlaying: Boolean,
    barColor: Color,
    peakColor: Color,
    modifier: Modifier = Modifier,
    bottomInset: androidx.compose.ui.unit.Dp = 14.dp
) {
    val context = LocalContext.current
    val analyzer = remember { LiveSpectrumAnalyzer(context.applicationContext) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isForeground = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    var permissionTick by remember { mutableIntStateOf(0) }
    var deniedOnce by rememberSaveable { mutableStateOf(false) }
    val granted = remember(lifecycleState, permissionTick) { analyzer.hasPermission() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        if (!ok) deniedOnce = true
        permissionTick++
    }

    LaunchedEffect(audioSessionId, isPlaying, isForeground, granted) {
        analyzer.update(audioSessionId, active = isPlaying && isForeground)
    }
    DisposableEffect(analyzer) { onDispose { analyzer.release() } }

    val bars by analyzer.bars.collectAsState()
    val status by analyzer.status.collectAsState()

    Box(modifier = modifier) {
        SpectrumBars(
            bars = if (status == LiveSpectrumAnalyzer.Status.LIVE) bars else null,
            barColor = barColor,
            peakColor = peakColor,
            bottomInset = bottomInset,
            modifier = Modifier.fillMaxSize()
        )

        val message: String?
        var showAllow = false
        when (status) {
            LiveSpectrumAnalyzer.Status.NEEDS_PERMISSION -> {
                message = stringResource(R.string.equalizer_spectrum_permission)
                showAllow = true
            }
            LiveSpectrumAnalyzer.Status.UNAVAILABLE ->
                message = stringResource(R.string.equalizer_spectrum_unavailable)
            else -> message = if (!isPlaying) stringResource(R.string.equalizer_spectrum_idle) else null
        }

        // Keep the last text during the fade-out.
        var shownMessage by remember { mutableStateOf("") }
        if (message != null) shownMessage = message

        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = shownMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (showAllow) {
                    FilledTonalButton(
                        onClick = {
                            if (deniedOnce) {
                                // The system stops showing the prompt after a denial or two.
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null)
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Text(
                            if (deniedOnce) stringResource(R.string.equalizer_spectrum_open_settings)
                            else stringResource(R.string.equalizer_spectrum_allow)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bars rise fast and fall slowly at display refresh rate (captures arrive ~20x a second),
 * with peak caps that hold briefly and then drop.
 */
@Composable
private fun SpectrumBars(
    bars: FloatArray?,
    barColor: Color,
    peakColor: Color,
    bottomInset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val count = LiveSpectrumAnalyzer.BARS
    val display = remember { FloatArray(count) }
    val peaks = remember { FloatArray(count) }
    val hold = remember { FloatArray(count) }
    var frame by remember { mutableLongStateOf(0L) }
    val latest = rememberUpdatedState(bars)

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            var settled = true
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                val target = latest.value
                for (i in 0 until count) {
                    val t = target?.getOrNull(i) ?: 0f
                    val d = display[i]
                    display[i] = if (t > d) d + (t - d) * min(1f, dt * 30f) else max(t, d - dt * 1.3f)
                    if (display[i] >= peaks[i]) {
                        peaks[i] = display[i]
                        hold[i] = 0.5f
                    } else if (hold[i] > 0f) {
                        hold[i] -= dt
                    } else {
                        peaks[i] = max(display[i], peaks[i] - dt * 0.8f)
                    }
                    if (display[i] > 0.001f || peaks[i] > 0.001f) settled = false
                }
                frame = now
            }
            // Nothing moving and nothing captured: sleep until bars arrive instead of
            // ticking every frame.
            if (settled && latest.value == null) {
                snapshotFlow { latest.value }.first { it != null }
                last = 0L
            }
        }
    }

    Canvas(modifier = modifier) {
        if (frame == Long.MIN_VALUE) return@Canvas // reading frame redraws on every tick
        val inset = bottomInset.toPx()
        val h = (size.height - inset).coerceAtLeast(1f)
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val minBar = 3.dp.toPx()
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
        val brush = Brush.verticalGradient(
            colors = listOf(barColor, barColor.copy(alpha = 0.35f)),
            startY = 0f,
            endY = h
        )
        val capHeight = 2.dp.toPx()

        for (i in 0 until count) {
            val x = i * (barWidth + gap)
            val barHeight = minBar + display[i] * (h - minBar)
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, h - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = radius
            )
            if (peaks[i] > 0.02f) {
                val y = h - (minBar + peaks[i] * (h - minBar)) - capHeight * 2
                drawRoundRect(
                    color = peakColor,
                    topLeft = Offset(x, y.coerceAtLeast(0f)),
                    size = Size(barWidth, capHeight),
                    cornerRadius = CornerRadius(capHeight / 2f, capHeight / 2f)
                )
            }
        }
    }
}
