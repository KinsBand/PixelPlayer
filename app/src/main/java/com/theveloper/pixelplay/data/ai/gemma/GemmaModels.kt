package com.theveloper.pixelplay.data.ai.gemma

import android.app.ActivityManager
import android.content.Context

/**
 * The phone builds of Google's Gemma 4 that PixelPlayer can download and run on-device with
 * LiteRT-LM. Both come from the litert-community repos on Hugging Face (Apache-2.0, no sign-in).
 * Sizes are the published .litertlm files; RAM is what the model needs while it runs.
 */
enum class GemmaModel(
    val id: String,
    val displayName: String,
    val repo: String,
    val fileName: String,
    /** Download size as published (bytes, approximate). */
    val sizeBytes: Long,
    /** Phone RAM below which the model is marked "may not fit" (total RAM, bytes). */
    val minTotalRamBytes: Long,
    /** Free working memory it needs while answering (for the card). */
    val workingRamLabel: String,
) {
    E2B(
        id = "gemma-4-e2b",
        displayName = "Gemma 4 E2B",
        repo = "litert-community/gemma-4-E2B-it-litert-lm",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_590_000_000L,
        minTotalRamBytes = 5L * GB, // 6 GB phones report a little under 6 GB
        workingRamLabel = "~2 GB",
    ),
    E4B(
        id = "gemma-4-e4b",
        displayName = "Gemma 4 E4B",
        repo = "litert-community/gemma-4-E4B-it-litert-lm",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_660_000_000L,
        minTotalRamBytes = 10L * GB, // 12 GB phones report ~11 GB
        workingRamLabel = "~4 GB",
    );

    val downloadUrl: String get() = "https://huggingface.co/$repo/resolve/main/$fileName"

    companion object {
        fun byId(id: String?): GemmaModel? = entries.firstOrNull { it.id == id }

        /** Total RAM of this phone (bytes). */
        fun totalRam(context: Context): Long {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
        }

        /** The best model for this phone: E4B on 12 GB+ phones, else E2B. */
        fun recommendedFor(context: Context): GemmaModel =
            if (totalRam(context) >= E4B.minTotalRamBytes) E4B else E2B
    }
}

private const val GB = 1_000_000_000L
