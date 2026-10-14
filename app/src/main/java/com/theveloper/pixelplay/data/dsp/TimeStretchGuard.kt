package com.theveloper.pixelplay.data.dsp

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Keeps speed- and pitch-changed playback clean.
 *
 * Media3's [DefaultAudioSink.DefaultAudioProcessorChain] always runs its custom processors first,
 * then silence skipping, then Sonic (time-stretch + resample), so the app's own DSP limiter sits
 * *before* Sonic. [buildSink] wraps that chain as:
 *
 *   custom processors → [PreStretchConditioner] → silence skipping → Sonic → [PostStretchLimiter]
 *
 * Both extra stages only work while speed or pitch ≠ 1; at 1× they copy the bytes through untouched.
 * - Before Sonic: [HEADROOM_GAIN] (−2 dB), ramped across a buffer so it never steps; and, when
 *   pitch > 1, a 4th-order Butterworth low-pass at 0.45 · sampleRate / pitch. Sonic resamples with
 *   plain linear interpolation and no anti-alias filter, so without it the top octave folds back
 *   down as harsh, metallic aliasing when pitching up (Key Lock off, or the pitch slider).
 * - After Sonic: a [TruePeakLimiter] with soft saturation, plus a [FADE_MS] fade-in after a flush
 *   so a restarted stretched stream doesn't click.
 *
 * Pitch / speed are always rendered in software (`setEnableAudioOutputPlaybackParameters(false)`),
 * never by the device's AudioTrack / HAL.
 */
@OptIn(UnstableApi::class)
object TimeStretchGuard {
    /** −2 dB of headroom fed into Sonic while stretching. */
    const val HEADROOM_GAIN = 0.794f

    /** Pitch above which the anti-alias low-pass runs (≈ +0.17 semitones). */
    internal const val ALIAS_PITCH_THRESHOLD = 1.01f

    /** Fade-in after a flush of the stretched stream. */
    internal const val FADE_MS = 4

    fun buildSink(
        context: Context,
        enableFloatOutput: Boolean,
        vararg customProcessors: AudioProcessor,
    ): AudioSink {
        val state = TimeStretchState()
        val chain = GuardedProcessorChain(
            DefaultAudioSink.DefaultAudioProcessorChain(*customProcessors, PreStretchConditioner(state)),
            state,
            PostStretchLimiter(state),
        )
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(false)
            .setAudioProcessorChain(chain)
            .build()
    }

    /** A plain audio [ExoPlayer] (default renderers) whose sink is [buildSink]. */
    fun newPlayer(context: Context): ExoPlayer =
        ExoPlayer.Builder(context, renderersFactory(context)).build()

    private fun renderersFactory(context: Context): DefaultRenderersFactory =
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink = TimeStretchGuard.buildSink(context, enableFloatOutput)
        }
}

/** The playback parameters Sonic is currently applying, shared by one sink's stages. */
internal class TimeStretchState {
    @Volatile var speed: Float = 1f
    @Volatile var pitch: Float = 1f

    val stretching: Boolean
        get() = abs(speed - 1f) > EPSILON || abs(pitch - 1f) > EPSILON

    private companion object {
        const val EPSILON = 1e-3f
    }
}

/** [DefaultAudioSink.DefaultAudioProcessorChain] plus a stage after Sonic. */
@OptIn(UnstableApi::class)
private class GuardedProcessorChain(
    private val inner: DefaultAudioSink.DefaultAudioProcessorChain,
    private val state: TimeStretchState,
    post: AudioProcessor,
) : AudioProcessorChain by inner {
    private val processors: Array<AudioProcessor> = arrayOf(*inner.audioProcessors, post)

    override fun getAudioProcessors(): Array<AudioProcessor> = processors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
        val applied = inner.applyPlaybackParameters(playbackParameters)
        state.speed = applied.speed
        state.pitch = applied.pitch
        return applied
    }
}

/**
 * A 16-bit / float PCM processor that works in float and copies bytes straight through while
 * [shouldProcess] is false. Single output buffer, like the app's other processors.
 */
@OptIn(UnstableApi::class)
private abstract class FloatPcmStage : AudioProcessor {
    protected var channels = 0
        private set
    protected var sampleRate = 0
        private set
    private var isFloat = false
    private var format: AudioFormat = AudioFormat.NOT_SET
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var work = FloatArray(4096)

    protected abstract fun shouldProcess(): Boolean
    protected abstract fun process(buf: FloatArray, frames: Int)
    protected open fun onConfigured() {}
    protected open fun onFlushed() {}

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        val supported = (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) &&
            inputAudioFormat.channelCount in 1..MAX_CHANNELS &&
            inputAudioFormat.sampleRate > 0
        if (!supported) {
            // Pass unsupported formats through (inactive), like the app's other processors.
            format = AudioFormat.NOT_SET
            return inputAudioFormat
        }
        format = inputAudioFormat
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        onConfigured()
        return inputAudioFormat
    }

    override fun isActive(): Boolean = format != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) return
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!shouldProcess()) {
            val out = ensureBuffer(remaining)
            out.put(inputBuffer)
            out.flip()
            outputBuffer = out
            return
        }

        val bytesPerSample = if (isFloat) 4 else 2
        val frames = remaining / (bytesPerSample * channels)
        val samples = frames * channels
        if (work.size < samples) work = FloatArray(samples)

        val src = inputBuffer.duplicate().order(NATIVE_ORDER)
        if (isFloat) {
            src.asFloatBuffer().get(work, 0, samples)
        } else {
            val shorts = src.asShortBuffer()
            for (i in 0 until samples) work[i] = shorts.get() * INV_SHORT
        }
        inputBuffer.position(inputBuffer.limit())

        process(work, frames)

        val byteCount = samples * bytesPerSample
        val out = ensureBuffer(byteCount)
        if (isFloat) {
            out.asFloatBuffer().put(work, 0, samples)
        } else {
            val shorts = out.asShortBuffer()
            for (i in 0 until samples) {
                shorts.put((work[i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
            }
        }
        out.position(0)
        out.limit(byteCount)
        outputBuffer = out
    }

    private fun ensureBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(NATIVE_ORDER)
        } else {
            buffer.clear()
        }
        return buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        onFlushed()
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        format = AudioFormat.NOT_SET
        channels = 0
        sampleRate = 0
    }

    protected companion object {
        const val MAX_CHANNELS = 8
        val NATIVE_ORDER: ByteOrder = ByteOrder.nativeOrder()
        const val INV_SHORT = 1f / 32768f
    }
}

/** Before Sonic: headroom and, when pitching up, the anti-alias low-pass. */
@OptIn(UnstableApi::class)
private class PreStretchConditioner(private val state: TimeStretchState) : FloatPcmStage() {
    private var gain = 1f
    private var filterOn = false
    private var filterPitch = 0f
    private var s1 = BiquadCoefficients.BYPASS
    private var s2 = BiquadCoefficients.BYPASS
    // Direct Form II Transposed state, two cascaded sections per channel.
    private val z1a = FloatArray(MAX_CHANNELS)
    private val z2a = FloatArray(MAX_CHANNELS)
    private val z1b = FloatArray(MAX_CHANNELS)
    private val z2b = FloatArray(MAX_CHANNELS)

    override fun shouldProcess(): Boolean = state.stretching || abs(gain - 1f) > 1e-4f

    override fun onConfigured() {
        filterPitch = 0f // recompute coefficients for the new sample rate
        clearFilter()
    }

    override fun onFlushed() {
        clearFilter()
    }

    private fun clearFilter() {
        z1a.fill(0f); z2a.fill(0f); z1b.fill(0f); z2b.fill(0f)
    }

    override fun process(buf: FloatArray, frames: Int) {
        val ch = channels
        val stretching = state.stretching
        val pitch = state.pitch

        // Anti-alias low-pass: Sonic's resampler raises every frequency by `pitch`.
        val wantFilter = stretching && pitch > TimeStretchGuard.ALIAS_PITCH_THRESHOLD
        if (wantFilter) {
            if (pitch != filterPitch) {
                val sr = sampleRate.toFloat()
                val fc = 0.45f * sr / pitch
                // 4th-order Butterworth = two biquads with Q 0.5412 and 1.3066.
                s1 = BiquadCoefficients.calculateLowPass(sr, fc, 0.5412f)
                s2 = BiquadCoefficients.calculateLowPass(sr, fc, 1.3066f)
                filterPitch = pitch
            }
            if (!filterOn) {
                clearFilter()
                filterOn = true
            }
        } else {
            filterOn = false
        }

        if (filterOn) {
            val a = s1
            val b = s2
            var idx = 0
            for (f in 0 until frames) {
                for (c in 0 until ch) {
                    val x = buf[idx]
                    val y1 = a.b0 * x + z1a[c]
                    z1a[c] = a.b1 * x - a.a1 * y1 + z2a[c]
                    z2a[c] = a.b2 * x - a.a2 * y1
                    val y2 = b.b0 * y1 + z1b[c]
                    z1b[c] = b.b1 * y1 - b.a1 * y2 + z2b[c]
                    z2b[c] = b.b2 * y1 - b.a2 * y2
                    buf[idx] = if (y2.isNaN() || y2.isInfinite()) 0f else y2
                    idx++
                }
            }
        }

        // Headroom, ramped across the buffer so the gain change never clicks.
        val target = if (stretching) TimeStretchGuard.HEADROOM_GAIN else 1f
        if (gain != target || target != 1f) {
            val step = if (frames > 0) (target - gain) / frames else 0f
            var g = gain
            var idx = 0
            for (f in 0 until frames) {
                g += step
                for (c in 0 until ch) {
                    buf[idx] *= g
                    idx++
                }
            }
            gain = target
        }
    }
}

/** After Sonic: catches any overshoot from the stretched stream and de-clicks restarts. */
@OptIn(UnstableApi::class)
private class PostStretchLimiter(private val state: TimeStretchState) : FloatPcmStage() {
    private val limiter = TruePeakLimiter(isEnabled = true, isSoftSaturationEnabled = true)
    private var fadeLen = 1
    private var fadeLeft = 0

    override fun shouldProcess(): Boolean {
        if (state.stretching) return true
        fadeLeft = 0 // a pending fade must not fire later, mid-stream
        return false
    }

    override fun onConfigured() {
        limiter.setSampleRate(sampleRate.toFloat())
        limiter.reset()
        fadeLen = max(1, sampleRate * TimeStretchGuard.FADE_MS / 1000)
    }

    override fun onFlushed() {
        limiter.reset()
        fadeLeft = fadeLen
    }

    override fun process(buf: FloatArray, frames: Int) {
        limiter.processInterleaved(buf, frames, channels)
        if (fadeLeft <= 0) return
        val ch = channels
        var idx = 0
        for (f in 0 until frames) {
            if (fadeLeft <= 0) break
            val g = 1f - fadeLeft.toFloat() / fadeLen
            for (c in 0 until ch) {
                buf[idx] *= g
                idx++
            }
            fadeLeft--
        }
    }
}
