package com.theveloper.pixelplay.presentation.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.MixPromptParser
import com.theveloper.pixelplay.data.ai.nano.NanoEngine
import com.theveloper.pixelplay.data.ai.nano.NanoMixIntentReader
import com.theveloper.pixelplay.data.ai.nano.NanoStatus
import com.theveloper.pixelplay.ui.theme.MotionTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Result of the Settings "Test" button: what Nano understood and how long it took. */
data class NanoTestResult(val labels: List<String>, val millis: Long, val ok: Boolean)

@HiltViewModel
class GeminiNanoSettingsViewModel @Inject constructor(
    // This card is about Gemini Nano itself, not whichever engine is picked.
    private val engine: com.theveloper.pixelplay.data.ai.nano.MlKitNanoEngine,
    private val reader: NanoMixIntentReader,
) : ViewModel() {
    val status: StateFlow<NanoStatus> = engine.status

    private val _modelName = MutableStateFlow<String?>(null)
    val modelName: StateFlow<String?> = _modelName.asStateFlow()

    private val _test = MutableStateFlow<NanoTestResult?>(null)
    val test: StateFlow<NanoTestResult?> = _test.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            if (engine.refreshStatus() == NanoStatus.Ready) _modelName.value = engine.modelName()
        }
    }

    fun download() = engine.startDownload()

    fun runTest() {
        if (_testing.value) return
        _testing.value = true
        viewModelScope.launch {
            engine.warmUp()
            val started = System.currentTimeMillis()
            val intent = reader.read(TEST_PROMPT, timeoutMs = 8_000L)
            val took = System.currentTimeMillis() - started
            _test.value = if (intent != null) {
                NanoTestResult(MixPromptParser.parse(TEST_PROMPT).mergedWith(intent).labels, took, ok = true)
            } else {
                NanoTestResult(emptyList(), took, ok = false)
            }
            _testing.value = false
            if (_modelName.value == null) _modelName.value = engine.modelName()
        }
    }

    companion object {
        const val TEST_PROMPT = "upbeat 80s rock like Queen"
    }
}

/**
 * "On-device AI (Gemini Nano)": status, download with progress, and a quick test that reads a
 * sample queue prompt and shows what was understood and how fast.
 */
@Composable
internal fun GeminiNanoCard(
    modifier: Modifier = Modifier,
    viewModel: GeminiNanoSettingsViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val modelName by viewModel.modelName.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    val context = LocalContext.current
    val reducedMotion = android.provider.Settings.Global.getFloat(
        context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    ) == 0f

    val statusText = when (val s = status) {
        NanoStatus.Unknown -> "Checking…"
        is NanoStatus.Unsupported -> s.reason
        NanoStatus.Downloadable -> "Not downloaded yet. It runs fully on your phone, offline."
        is NanoStatus.Downloading -> s.progress?.let { "Downloading… ${(it * 100).toInt()}%" } ?: "Downloading…"
        NanoStatus.Ready -> "Ready on this phone" + (modelName?.let { " · $it" } ?: "")
        is NanoStatus.Error -> "${s.message}. Tap Download to try again."
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "On-device AI (Gemini Nano)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Reads what you type in the queue prompt and AI playlists, and translates lyrics. Your music never leaves the phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            // Status line + action swap with a fade-through.
            AnimatedContent(
                targetState = status,
                contentKey = { it::class },
                transitionSpec = {
                    if (reducedMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(MotionTokens.DurationMedium2, delayMillis = MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                            fadeOut(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate))
                    }
                },
                label = "nanoStatus"
            ) { current ->
                Column {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (current) {
                            NanoStatus.Ready -> MaterialTheme.colorScheme.primary
                            is NanoStatus.Error, is NanoStatus.Unsupported -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    if (current is NanoStatus.Downloading) {
                        Spacer(Modifier.height(8.dp))
                        val progress = current.progress
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Downloading Gemini Nano, ${(progress * 100).toInt()} percent" }
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Downloading Gemini Nano" }
                            )
                        }
                    }
                    if (current is NanoStatus.Downloadable || current is NanoStatus.Error) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::download, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Download", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (current == NanoStatus.Ready) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = viewModel::runTest,
                                enabled = !testing,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(if (testing) "Testing…" else "Test", style = MaterialTheme.typography.labelLarge)
                            }
                            Text(
                                text = "\"${GeminiNanoSettingsViewModel.TEST_PROMPT}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Test result row.
            AnimatedVisibility(
                visible = test != null && status == NanoStatus.Ready,
                enter = if (reducedMotion) EnterTransition.None else
                    expandVertically(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)) +
                        fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
                exit = if (reducedMotion) ExitTransition.None else
                    shrinkVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)) +
                        fadeOut(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)),
            ) {
                val result = test
                if (result != null) {
                    Text(
                        text = if (result.ok) {
                            "Understood: ${result.labels.joinToString(" · ")} (${result.millis} ms)"
                        } else {
                            "No answer this time (${result.millis} ms). The local reader still works; try again in a moment."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        }
    }
}
