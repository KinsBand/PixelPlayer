package com.theveloper.pixelplay.data.lyrics.align

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer

/**
 * wav2vec2 CTC model run through ONNX Runtime on the CPU.
 *
 * Input: one 16 kHz mono utterance (`[1, samples]`, already normalised by [ChunkedEmissions]).
 * Output: `[1, frames, vocab]` logits, turned into log-probabilities here. One frame per 320
 * samples (20 ms).
 *
 * Holds the model in native memory (≈100–200 MB with the int8 model, depending on the phone);
 * [close] it as soon as a song is done.
 */
class OnnxCtcModel(modelFile: File, threads: Int = 2) : CtcAcousticModel {

    override val vocabSize: Int = CtcVocabulary.WAV2VEC2_960H.size
    override val hopSamples: Int = 320
    override val sampleRate: Int = 16_000

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String

    init {
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads)
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            // The arena keeps peak buffers allocated between runs; chunks are all the same size,
            // so reuse is fine, but give memory back when the session closes.
            setMemoryPatternOptimization(true)
        }
        session = env.createSession(modelFile.absolutePath, options)
        options.close()
        inputName = session.inputNames.first()
    }

    override fun logProbs(samples: FloatArray, length: Int): FloatArray {
        val input = OnnxTensor.createTensor(env, FloatBuffer.wrap(samples, 0, length), longArrayOf(1, length.toLong()))
        input.use {
            session.run(mapOf(inputName to input)).use { result ->
                val tensor = result.get(0) as OnnxTensor
                val shape = tensor.info.shape // [1, frames, vocab]
                require(shape.size == 3 && shape[2].toInt() == vocabSize) { "Unexpected model output ${shape.contentToString()}" }
                val frames = shape[1].toInt()
                val out = FloatArray(frames * vocabSize)
                tensor.floatBuffer.get(out)
                ChunkedEmissions.logSoftmaxRows(out, frames, vocabSize)
                return out
            }
        }
    }

    override fun close() {
        runCatching { session.close() }
    }
}
