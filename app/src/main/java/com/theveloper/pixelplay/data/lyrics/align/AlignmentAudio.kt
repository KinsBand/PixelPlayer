package com.theveloper.pixelplay.data.lyrics.align

import com.theveloper.pixelplay.data.analysis.dsp.Resampler
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * A CTC acoustic model: 16 kHz mono audio in, per-frame log-probabilities out.
 * Frame `i` of the output covers the samples from `i × hopSamples`.
 */
interface CtcAcousticModel : AutoCloseable {
    val vocabSize: Int
    val hopSamples: Int
    val sampleRate: Int

    /** Row-major `frames × vocabSize` log-probabilities for [samples] (first [length] entries). */
    fun logProbs(samples: FloatArray, length: Int): FloatArray
}

/**
 * Streaming band-limited resampler to [targetRate] for the decoder's chunks.
 *
 * Resampling each decoder chunk on its own would leave a seam every few thousand samples. This
 * keeps a margin of source audio on both sides of every block it resamples and only emits the
 * block's middle, at positions where source and target samples line up exactly, so the output is
 * identical to resampling the whole song at once (apart from the very first and last samples).
 */
class StreamingResampler(
    private val sourceRate: Int,
    private val targetRate: Int,
    private val sink: (samples: FloatArray, count: Int) -> Unit
) {
    private val gcd = gcd(sourceRate, targetRate)
    /** Source samples per alignment period, and the target samples they become. */
    private val periodIn = sourceRate / gcd
    private val periodOut = targetRate / gcd
    private val margin = ceil(MIN_MARGIN.toDouble() / periodIn).toInt() * periodIn
    private val block = ((sourceRate * BLOCK_SECONDS) / periodIn).coerceAtLeast(1) * periodIn
    private var buffer = FloatArray(margin + block + margin + 8192)
    private var length = 0
    /** Where the next block to emit starts inside [buffer]. */
    private var core = 0

    fun push(samples: FloatArray, offset: Int, count: Int) {
        if (sourceRate == targetRate) { sink(samples.copyOfRange(offset, offset + count), count); return }
        var read = 0
        while (read < count) {
            if (length == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
            val n = minOf(count - read, buffer.size - length)
            System.arraycopy(samples, offset + read, buffer, length, n)
            length += n
            read += n
            while (length >= core + block + margin) emit(core + block + margin, last = false)
        }
    }

    fun finish() {
        if (sourceRate == targetRate) return
        if (length > core) emit(length, last = true)
        length = 0; core = 0
    }

    private fun emit(upTo: Int, last: Boolean) {
        val out = Resampler.resample(buffer, upTo, sourceRate, targetRate)
        val from = core / periodIn * periodOut
        val to = if (last) out.size else (core + block) / periodIn * periodOut
        if (to > from) sink(out.copyOfRange(from, minOf(to, out.size)), minOf(to, out.size) - from)
        if (last) return
        // Keep a margin of history in front of the next block.
        val keepFrom = core + block - margin
        System.arraycopy(buffer, keepFrom, buffer, 0, length - keepFrom)
        length -= keepFrom
        core = margin
    }

    private companion object {
        const val MIN_MARGIN = 64
        const val BLOCK_SECONDS = 2
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}

/**
 * Runs [model] over a whole song in fixed chunks as the audio arrives and keeps only the
 * log-probabilities (about 5 MB for 10 minutes), never the audio.
 *
 * Each chunk is [coreFrames] frames of audio plus [contextFrames] of context on each side; only
 * the core's frames are kept, so every kept frame saw the same amount of surrounding audio as in
 * one long pass. Chunk boundaries are whole frames, so frame indices match the song's timeline.
 */
class ChunkedEmissions(
    private val model: CtcAcousticModel,
    private val coreFrames: Int = 1_000,   // 20 s at 20 ms
    private val contextFrames: Int = 50,   // 1 s each side
    private val maxFrames: Int = 36_000    // 12 min
) {
    private val hop = model.hopSamples
    private val vocab = model.vocabSize
    private val coreSamples = coreFrames * hop
    private val contextSamples = contextFrames * hop
    private val audio = FloatArray(contextSamples + coreSamples + contextSamples + RECEPTIVE_EXTRA)
    private var audioLength = 0
    /** Absolute sample index of `audio[0]`. */
    private var audioStart = 0L
    /** Absolute sample index of the next core to run. */
    private var nextCore = 0L
    private var out = FloatArray(coreFrames * vocab * 4)
    var frames = 0
        private set
    val isFull: Boolean get() = frames >= maxFrames

    fun push(samples: FloatArray, count: Int) {
        var read = 0
        while (read < count && !isFull) {
            val n = minOf(count - read, audio.size - audioLength)
            System.arraycopy(samples, read, audio, audioLength, n)
            audioLength += n
            read += n
            if (audioLength == audio.size) runChunk(last = false)
        }
    }

    fun finish(frameMs: Double): CtcEmissions {
        if (!isFull && audioStart + audioLength > nextCore) runChunk(last = true)
        return CtcEmissions(out, frames, vocab, frameMs)
    }

    private fun runChunk(last: Boolean) {
        // Too little audio for even one model frame (a song ending just past a chunk boundary).
        if (audioLength < hop * 2) return
        val input = audio.copyOf(audioLength)
        normalize(input)
        val logits = model.logProbs(input, input.size)
        val produced = logits.size / vocab
        // The core starts this many frames into the chunk.
        val skip = ((nextCore - audioStart) / hop).toInt()
        val wanted = if (last) produced - skip else minOf(coreFrames, produced - skip)
        val keep = minOf(wanted, maxFrames - frames).coerceAtLeast(0)
        ensureCapacity((frames + keep) * vocab)
        System.arraycopy(logits, skip * vocab, out, frames * vocab, keep * vocab)
        frames += keep
        if (last) return
        // Slide: the next chunk starts contextSamples before the next core.
        nextCore += coreSamples
        val newStart = nextCore - contextSamples
        val drop = (newStart - audioStart).toInt()
        System.arraycopy(audio, drop, audio, 0, audioLength - drop)
        audioLength -= drop
        audioStart = newStart
    }

    private fun ensureCapacity(size: Int) {
        if (out.size >= size) return
        var cap = out.size
        while (cap < size) cap *= 2
        out = out.copyOf(cap)
    }

    companion object {
        /** wav2vec2 frames see 400 samples, 80 more than the hop. */
        const val RECEPTIVE_EXTRA = 80

        /** Zero mean, unit variance, as wav2vec2's feature extractor does (`do_normalize`). */
        fun normalize(x: FloatArray) {
            if (x.isEmpty()) return
            var mean = 0.0
            for (v in x) mean += v
            mean /= x.size
            var variance = 0.0
            for (v in x) { val d = v - mean; variance += d * d }
            variance /= x.size
            val scale = 1.0 / sqrt(variance + 1e-7)
            for (i in x.indices) x[i] = ((x[i] - mean) * scale).toFloat()
        }

        /** In-place log-softmax over each row of `rows × cols` [logits]. */
        fun logSoftmaxRows(logits: FloatArray, rows: Int, cols: Int) {
            for (r in 0 until rows) {
                val base = r * cols
                var m = logits[base]
                for (c in 1 until cols) if (logits[base + c] > m) m = logits[base + c]
                var sum = 0.0
                for (c in 0 until cols) sum += exp((logits[base + c] - m).toDouble())
                val lse = m + ln(sum).toFloat()
                for (c in 0 until cols) logits[base + c] -= lse
            }
        }
    }
}
