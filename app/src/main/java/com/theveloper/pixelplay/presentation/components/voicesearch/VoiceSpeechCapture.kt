package com.theveloper.pixelplay.presentation.components.voicesearch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import timber.log.Timber

enum class VoiceCapturePhase { Idle, NeedsPermission, Listening, Hearing, Processing, Done, Error, Unavailable }

@Stable
class VoiceSpeechCaptureState {
    var phase by mutableStateOf(VoiceCapturePhase.Idle)
        internal set
    var transcript by mutableStateOf("")
        internal set
    /** 0..1 microphone level, for the pulsing mic. */
    var level by mutableFloatStateOf(0f)
        internal set
    var errorMessage by mutableStateOf<String?>(null)
        internal set

    internal var sessionToken by mutableIntStateOf(0)
    internal var onRetry: () -> Unit = {}

    /** Start a fresh listening session (re-asks for the mic permission if it was denied). */
    fun retry() = onRetry()
}

/**
 * Speech-to-text for the voice search sheet.
 *
 * While [active] is true it makes sure RECORD_AUDIO is granted (asking the user the first
 * time), then runs a [SpeechRecognizer] session that streams partial results into
 * [VoiceSpeechCaptureState.transcript] and [onPartialTranscript] (the search bar shows it live).
 * The final transcript goes to [onFinalTranscript].
 * The recognizer is torn down as soon as [active] turns false or the composable leaves.
 */
@Composable
fun rememberVoiceSpeechCapture(
    active: Boolean,
    onPartialTranscript: (String) -> Unit = {},
    onFinalTranscript: (String) -> Unit
): VoiceSpeechCaptureState {
    val context = LocalContext.current
    val state = remember { VoiceSpeechCaptureState() }
    val latestOnFinal by rememberUpdatedState(onFinalTranscript)
    val latestOnPartial by rememberUpdatedState(onPartialTranscript)
    var hasPermission by remember { mutableStateOf(context.hasMicPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            state.sessionToken++
        } else {
            state.transcript = ""
            state.phase = VoiceCapturePhase.Error
            state.errorMessage = "Microphone access is needed to hear you. Tap the mic to allow it."
        }
    }

    state.onRetry = {
        hasPermission = context.hasMicPermission()
        if (!hasPermission) {
            state.phase = VoiceCapturePhase.NeedsPermission
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            state.sessionToken++
        }
    }

    // Ask for the microphone as soon as the sheet becomes active.
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        state.transcript = ""
        state.errorMessage = null
        hasPermission = context.hasMicPermission()
        if (!hasPermission) {
            state.phase = VoiceCapturePhase.NeedsPermission
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(active, hasPermission, state.sessionToken) {
        if (!active || !hasPermission) {
            if (!active) {
                state.level = 0f
                if (state.phase == VoiceCapturePhase.Listening || state.phase == VoiceCapturePhase.Hearing) {
                    state.phase = VoiceCapturePhase.Idle
                }
            }
            return@DisposableEffect onDispose { }
        }

        var recognizer: SpeechRecognizer? = null
        var disposed = false
        var usingOnDevice = false

        fun start(preferOnDevice: Boolean) {
            recognizer?.destroy()
            val created = createRecognizer(context, preferOnDevice)
            if (created == null) {
                state.phase = VoiceCapturePhase.Unavailable
                state.errorMessage = "Speech recognition isn't available. Install or enable a voice input app."
                return
            }
            usingOnDevice = created.second
            recognizer = created.first.apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        state.phase = VoiceCapturePhase.Listening
                        state.errorMessage = null
                    }

                    override fun onBeginningOfSpeech() {
                        state.phase = VoiceCapturePhase.Hearing
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        state.level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        state.level = 0f
                        state.phase = VoiceCapturePhase.Processing
                    }

                    override fun onError(error: Int) {
                        if (disposed) return
                        state.level = 0f
                        val languageMissing = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                        if (usingOnDevice && languageMissing) {
                            // On-device pack missing: fall back to the regular recognizer (still offline-preferred).
                            start(preferOnDevice = false)
                            return
                        }
                        state.phase = VoiceCapturePhase.Error
                        state.errorMessage = errorText(error)
                    }

                    override fun onResults(results: Bundle?) {
                        if (disposed) return
                        val text = results.firstResult()
                        state.level = 0f
                        if (text.isNullOrBlank()) {
                            state.phase = VoiceCapturePhase.Error
                            state.errorMessage = "Didn't catch that. Tap the mic to try again."
                        } else {
                            state.transcript = text
                            state.phase = VoiceCapturePhase.Done
                            latestOnFinal(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = partialResults.firstResult()
                        if (!text.isNullOrBlank()) {
                            state.transcript = text
                            latestOnPartial(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
            try {
                state.transcript = ""
                state.errorMessage = null
                state.phase = VoiceCapturePhase.Listening
                recognizer?.startListening(buildRecognizerIntent(context))
            } catch (e: Exception) {
                Timber.w(e, "VoiceSpeechCapture: startListening failed")
                state.phase = VoiceCapturePhase.Error
                state.errorMessage = "Couldn't start the microphone. Tap to try again."
            }
        }

        start(preferOnDevice = true)

        onDispose {
            disposed = true
            state.level = 0f
            try {
                recognizer?.cancel()
                recognizer?.destroy()
            } catch (e: Exception) {
                Timber.w(e, "VoiceSpeechCapture: release failed")
            }
            recognizer = null
        }
    }

    return state
}

private fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** Returns the recognizer and whether it is the on-device one. */
private fun createRecognizer(context: Context, preferOnDevice: Boolean): Pair<SpeechRecognizer, Boolean>? {
    if (preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val onDevice = runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        if (onDevice) {
            runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
                .getOrNull()?.let { return it to true }
        }
    }
    if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
    return runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()?.let { it to false }
}

private fun buildRecognizerIntent(context: Context): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

private fun Bundle?.firstResult(): String? =
    this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()

private fun errorText(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Tap the mic to try again."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone access is needed. Tap the mic to allow it."
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_SERVER -> "Offline speech isn't set up. Download a language pack in system speech settings."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is busy. Tap to try again."
    SpeechRecognizer.ERROR_AUDIO -> "Couldn't record audio. Tap to try again."
    else -> "Something went wrong. Tap the mic to try again."
}
