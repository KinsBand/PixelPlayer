package com.theveloper.pixelplay.data.ai.nano

import kotlinx.coroutines.flow.StateFlow

/** Where on-device Gemini Nano stands on this phone. */
sealed interface NanoStatus {
    /** Not checked yet (first read happens on app start / opening AI settings). */
    data object Unknown : NanoStatus
    /** Can't run here; [reason] is shown in Settings. */
    data class Unsupported(val reason: String) : NanoStatus
    /** Supported, model not on the phone yet. */
    data object Downloadable : NanoStatus
    /** [progress] 0..1, or null while the size isn't known yet. */
    data class Downloading(val progress: Float?) : NanoStatus
    /** Downloaded and ready. */
    data object Ready : NanoStatus
    /** Last download or check failed; [message] is shown in Settings. */
    data class Error(val message: String) : NanoStatus
}

/**
 * On-device Gemini Nano (Android AICore through the ML Kit GenAI Prompt API).
 *
 * Every call is short and bounded: one request at a time, a timeout per call, and `null`
 * instead of an exception when the model is busy, over its quota, used from the background,
 * too slow or not ready. Callers always have a local result to fall back on, so nothing in the
 * app waits on or breaks because of Nano.
 *
 * Kept behind this interface so the beta ML Kit API is touched in one file only
 * ([MlKitNanoEngine]) and tests can use a fake.
 */
interface NanoEngine {
    val status: StateFlow<NanoStatus>

    /** Asks AICore again (cheap). */
    suspend fun refreshStatus(): NanoStatus

    /** Starts (or joins) the model download. Progress arrives through [status]. */
    fun startDownload()

    /** Loads the model into memory ahead of a request (call when a Nano feature is about to be used). */
    fun warmUp()

    /** Model name AICore reports (e.g. nano-v3), for Settings. Null when not ready. */
    suspend fun modelName(): String?

    /**
     * One text generation. [prefix] is the fixed instructions (cached by AICore where supported),
     * [input] the per-request part. Returns null on any failure or after [timeoutMs].
     */
    suspend fun generate(
        prefix: String,
        input: String,
        maxOutputTokens: Int = 256,
        temperature: Float = 0.2f,
        timeoutMs: Long = 4_000L,
    ): String?
}
