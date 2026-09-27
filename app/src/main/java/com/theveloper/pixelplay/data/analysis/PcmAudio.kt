package com.theveloper.pixelplay.data.analysis

/**
 * Decoded mono PCM audio.
 *
 * [samples] are normalized to roughly [-1, 1]; [sampleRate] is the rate of
 * the samples (after any downsampling the decoder applied). Pure Kotlin —
 * no Android dependencies, usable from local JVM unit tests.
 */
data class PcmAudio(
    val samples: FloatArray,
    val sampleRate: Int,
    val bitDepth: Int? = null,
    val channelCount: Int? = null,
    val mimeType: String? = null
) {

    val durationSeconds: Float
        get() = if (sampleRate > 0) samples.size.toFloat() / sampleRate else 0f

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PcmAudio) return false
        return sampleRate == other.sampleRate &&
            bitDepth == other.bitDepth &&
            channelCount == other.channelCount &&
            mimeType == other.mimeType &&
            samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + (bitDepth ?: 0)
        result = 31 * result + (channelCount ?: 0)
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        return result
    }
}

