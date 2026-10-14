package com.theveloper.pixelplay.presentation.screens.settings

import android.content.Context
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.ai.gemma.GemmaDownloadState
import com.theveloper.pixelplay.data.ai.gemma.GemmaModel
import com.theveloper.pixelplay.data.ai.gemma.GemmaModelManager
import com.theveloper.pixelplay.data.ai.gemma.OnDeviceEngine
import com.theveloper.pixelplay.data.ai.nano.MlKitNanoEngine
import com.theveloper.pixelplay.data.ai.nano.NanoStatus
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@HiltViewModel
class OnDeviceAiViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val models: GemmaModelManager,
    nano: MlKitNanoEngine,
) : ViewModel() {
    val nanoStatus = nano.status
    val totalRam: Long = GemmaModel.totalRam(context)
    val recommended: GemmaModel = GemmaModel.recommendedFor(context)
}

/**
 * The top of AI settings: what on-device AI does, which engine runs it (Gemini Nano built into
 * Pixel, or Gemma 4 downloaded to the phone), and for Gemma the model cards (size, memory fit,
 * download with progress, use, pause, delete) plus Wi-Fi only.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun OnDeviceAiSection(viewModel: OnDeviceAiViewModel = hiltViewModel()) {
    val engine by viewModel.models.engine.collectAsStateWithLifecycle()
    val selected by viewModel.models.selected.collectAsStateWithLifecycle()
    val states by viewModel.models.states.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.models.wifiOnly.collectAsStateWithLifecycle()
    val nanoStatus by viewModel.nanoStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val statusLine = when (engine) {
        OnDeviceEngine.GEMINI_NANO -> when (nanoStatus) {
            NanoStatus.Ready -> stringResource(R.string.ai_status_nano_ready)
            is NanoStatus.Downloading -> stringResource(R.string.ai_status_nano_downloading)
            NanoStatus.Downloadable -> stringResource(R.string.ai_status_nano_download)
            else -> stringResource(R.string.ai_status_nano_unavailable)
        }
        OnDeviceEngine.GEMMA -> when (states[selected]?.status) {
            GemmaDownloadState.Status.READY -> stringResource(R.string.ai_status_gemma_ready, selected.displayName)
            GemmaDownloadState.Status.DOWNLOADING -> stringResource(R.string.ai_status_gemma_downloading, selected.displayName)
            else -> stringResource(R.string.ai_status_gemma_download, selected.displayName)
        }
    }
    val ready = (engine == OnDeviceEngine.GEMINI_NANO && nanoStatus == NanoStatus.Ready) ||
        (engine == OnDeviceEngine.GEMMA && states[selected]?.status == GemmaDownloadState.Status.READY)

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Hero: what it is, and whether it's ready.
        Surface(
            shape = AbsoluteSmoothCornerShape(28.dp, 60),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.ai_hero_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape)
                                    .background(if (ready) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(statusLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                Text(
                    stringResource(R.string.ai_hero_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(R.string.ai_use_artist, R.string.ai_use_lyrics, R.string.ai_use_mix).forEach { use ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
                            Text(
                                stringResource(use),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Engine: two tiles side by side.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            EngineTile(
                title = stringResource(R.string.ai_engine_nano),
                subtitle = stringResource(R.string.ai_engine_nano_sub),
                icon = Icons.Rounded.PhoneAndroid,
                selected = engine == OnDeviceEngine.GEMINI_NANO,
                onClick = { viewModel.models.setEngine(OnDeviceEngine.GEMINI_NANO) },
                modifier = Modifier.weight(1f)
            )
            EngineTile(
                title = stringResource(R.string.ai_engine_gemma),
                subtitle = stringResource(R.string.ai_engine_gemma_sub),
                icon = Icons.Rounded.Download,
                selected = engine == OnDeviceEngine.GEMMA,
                onClick = { viewModel.models.setEngine(OnDeviceEngine.GEMMA) },
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedContent(
            targetState = engine,
            transitionSpec = {
                fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                    fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
            },
            label = "aiEngine"
        ) { shown ->
            when (shown) {
                OnDeviceEngine.GEMINI_NANO -> GeminiNanoCard()
                OnDeviceEngine.GEMMA -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GemmaModel.entries.forEach { model ->
                        GemmaModelCard(
                            model = model,
                            state = states[model] ?: GemmaDownloadState(),
                            isSelected = model == selected,
                            isRecommended = model == viewModel.recommended,
                            fits = viewModel.totalRam >= model.minTotalRamBytes,
                            context = context,
                            onUse = { viewModel.models.select(model) },
                            onDownload = { viewModel.models.select(model); viewModel.models.download(model) },
                            onPause = { viewModel.models.pause(model) },
                            onDelete = { viewModel.models.delete(model) },
                        )
                    }
                    Surface(
                        onClick = { viewModel.models.setWifiOnly(!wifiOnly) },
                        shape = AbsoluteSmoothCornerShape(20.dp, 60),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.ai_gemma_wifi_only), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.ai_gemma_wifi_only_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = wifiOnly, onCheckedChange = { viewModel.models.setWifiOnly(it) })
                        }
                    }
                    Text(
                        stringResource(R.string.ai_gemma_credit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = AbsoluteSmoothCornerShape(22.dp, 60),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = 92.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.weight(1f))
                if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded), fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GemmaModelCard(
    model: GemmaModel,
    state: GemmaDownloadState,
    isSelected: Boolean,
    isRecommended: Boolean,
    fits: Boolean,
    context: Context,
    onUse: () -> Unit,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
) {
    val ready = state.status == GemmaDownloadState.Status.READY
    val downloading = state.status == GemmaDownloadState.Status.DOWNLOADING
    val size = remember(model) { Formatter.formatShortFileSize(context, model.sizeBytes) }
    Surface(
        onClick = { if (ready) onUse() },
        enabled = ready,
        shape = AbsoluteSmoothCornerShape(24.dp, 60),
        color = if (isSelected && ready) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            model.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                            fontWeight = FontWeight.Bold
                        )
                        if (isRecommended) {
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                                Text(
                                    stringResource(R.string.ai_gemma_recommended),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(if (model == GemmaModel.E2B) R.string.ai_gemma_e2b_body else R.string.ai_gemma_e4b_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (ready && isSelected) Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.ai_gemma_in_use), tint = MaterialTheme.colorScheme.primary)
            }
            // Size · memory · fit
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoPill(Icons.Rounded.Download, size)
                InfoPill(Icons.Rounded.Memory, stringResource(R.string.ai_gemma_ram, model.workingRamLabel))
                InfoPill(
                    if (fits) Icons.Rounded.CheckCircle else Icons.Rounded.Memory,
                    stringResource(if (fits) R.string.ai_gemma_fits else R.string.ai_gemma_may_not_fit),
                    tint = if (fits) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
            }
            AnimatedVisibility(
                visible = downloading || state.status == GemmaDownloadState.Status.PAUSED,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(
                            R.string.ai_gemma_progress,
                            Formatter.formatShortFileSize(context, state.downloadedBytes),
                            Formatter.formatShortFileSize(context, state.totalBytes.coerceAtLeast(model.sizeBytes)),
                            (state.progress * 100).toInt()
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    ready && !isSelected -> FilledTonalButton(onClick = onUse) { Text(stringResource(R.string.ai_gemma_use)) }
                    ready -> Text(stringResource(R.string.ai_gemma_in_use), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    downloading -> FilledTonalButton(onClick = onPause) {
                        Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ai_gemma_pause))
                    }
                    else -> FilledTonalButton(onClick = onDownload) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (state.status == GemmaDownloadState.Status.PAUSED || state.downloadedBytes > 0) R.string.ai_gemma_resume else R.string.ai_gemma_download))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (ready || state.downloadedBytes > 0) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.ai_gemma_delete, model.displayName))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPill(icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
