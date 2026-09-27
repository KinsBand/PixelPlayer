package com.theveloper.pixelplay.data.analysis

import com.theveloper.pixelplay.data.analysis.dsp.BpmDetector
import com.theveloper.pixelplay.data.analysis.dsp.EnergyLoudness
import com.theveloper.pixelplay.data.analysis.dsp.KeyDetector
import com.theveloper.pixelplay.data.analysis.dsp.OnsetEnvelope
import timber.log.Timber
import javax.inject.Inject

import com.theveloper.pixelplay.data.analysis.dsp.ChordDetector
import com.theveloper.pixelplay.data.analysis.dsp.FadeDetector
import com.theveloper.pixelplay.data.analysis.dsp.SilenceDetector
import com.theveloper.pixelplay.data.analysis.dsp.StructuralSegmenter
import com.theveloper.pixelplay.data.analysis.dsp.TuningDetector

/**
 * Output of one [AudioAnalysisEngine.analyze] pass. Individual descriptors
 * are nullable when they could not be estimated reliably (silence, noise,
 * too little data) — the analysis row can still be stored with the rest.
 */
data class AudioAnalysisResult(
    val bpm: Int?,
    val musicKey: String?,
    val camelot: String?,
    /** RMS dBFS. */
    val loudnessDb: Float?,
    /** EBU R128 integrated loudness in LUFS. */
    val lufsIntegrated: Float?,
    /** Estimated dynamic range (DR14). */
    val dynamicRange: Float?,
    /** ReplayGain adjustment in dB. */
    val replayGain: Float?,
    /** Perceptual-ish energy 0..1 (see EnergyLoudness.energy). */
    val energy: Float?,
    val silenceAtStartMs: Long?,
    val silenceAtEndMs: Long?,
    val tuningHz: Float?,
    val chordProgression: String?,
    val songStructureJson: String?,
    val fadeInEndMs: Long?,
    val fadeOutStartMs: Long?,
    val waveform: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioAnalysisResult) return false
        return bpm == other.bpm &&
            musicKey == other.musicKey &&
            camelot == other.camelot &&
            loudnessDb == other.loudnessDb &&
            lufsIntegrated == other.lufsIntegrated &&
            dynamicRange == other.dynamicRange &&
            replayGain == other.replayGain &&
            energy == other.energy &&
            silenceAtStartMs == other.silenceAtStartMs &&
            silenceAtEndMs == other.silenceAtEndMs &&
            tuningHz == other.tuningHz &&
            chordProgression == other.chordProgression &&
            songStructureJson == other.songStructureJson &&
            fadeInEndMs == other.fadeInEndMs &&
            fadeOutStartMs == other.fadeOutStartMs &&
            waveform.contentEquals(other.waveform)
    }

    override fun hashCode(): Int {
        var result = bpm ?: 0
        result = 31 * result + (musicKey?.hashCode() ?: 0)
        result = 31 * result + (camelot?.hashCode() ?: 0)
        result = 31 * result + (loudnessDb?.hashCode() ?: 0)
        result = 31 * result + (lufsIntegrated?.hashCode() ?: 0)
        result = 31 * result + (dynamicRange?.hashCode() ?: 0)
        result = 31 * result + (replayGain?.hashCode() ?: 0)
        result = 31 * result + (energy?.hashCode() ?: 0)
        result = 31 * result + (silenceAtStartMs?.hashCode() ?: 0)
        result = 31 * result + (silenceAtEndMs?.hashCode() ?: 0)
        result = 31 * result + (tuningHz?.hashCode() ?: 0)
        result = 31 * result + (chordProgression?.hashCode() ?: 0)
        result = 31 * result + (songStructureJson?.hashCode() ?: 0)
        result = 31 * result + (fadeInEndMs?.hashCode() ?: 0)
        result = 31 * result + (fadeOutStartMs?.hashCode() ?: 0)
        result = 31 * result + (waveform?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Stateless facade over the DSP pipeline: spectral-flux onset envelope ->
 * autocorrelation BPM, chromagram key detection, chord detection, structure heuristics,
 * fade detection, EBU R128 loudness/energy, DR, tuning, silence, and peak waveform.
 */
class AudioAnalysisEngine @Inject constructor() {

    fun analyze(pcm: PcmAudio): AudioAnalysisResult {
        val t0 = System.nanoTime()

        val onset = OnsetEnvelope.compute(pcm.samples, pcm.sampleRate)
        val t1 = System.nanoTime()

        val bpm = BpmDetector.detectBpm(onset.envelope, onset.envelopeSampleRate, onset.rawMean)
        val t2 = System.nanoTime()

        val key = KeyDetector.detectKey(pcm.samples, pcm.sampleRate)
        val t3 = System.nanoTime()

        val loudnessDb = EnergyLoudness.rmsDb(pcm.samples)
        val lufsIntegrated = EnergyLoudness.lufsIntegrated(pcm.samples, pcm.sampleRate)
        val dynamicRange = EnergyLoudness.dynamicRange(pcm.samples, pcm.sampleRate)
        val replayGain = EnergyLoudness.calculateReplayGain(lufsIntegrated)
        val energy = EnergyLoudness.energy(pcm.samples)

        val silence = SilenceDetector.detect(pcm.samples, pcm.sampleRate)
        val tuningHz = TuningDetector.detectTuningFrequency(pcm.samples, pcm.sampleRate)

        val (chordProgression, _) = ChordDetector.detectProgression(pcm.samples, pcm.sampleRate)
        val durationMs = (pcm.durationSeconds * 1000f).toLong()
        val structures = StructuralSegmenter.segment(durationMs, onset.envelope, onset.envelopeSampleRate)
        val songStructureJson = structures.joinToString(" | ") { "${it.label} (${it.startMs/1000}s-${it.endMs/1000}s)" }

        val fadeResult = FadeDetector.detectFades(pcm.samples, pcm.sampleRate, durationMs)

        val waveform = WaveformGenerator.generate(pcm.samples)
        val t4 = System.nanoTime()

        Timber.tag(TAG).d(
            "analyze: %d samples @ %d Hz | onset=%dms bpm=%dms(%s) key=%dms(%s) lufs=%.1f LUFS DR=%.1f misc=%dms",
            pcm.samples.size, pcm.sampleRate,
            (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, bpm?.toString() ?: "-",
            (t3 - t2) / 1_000_000, key?.key ?: "-",
            lufsIntegrated, dynamicRange,
            (t4 - t3) / 1_000_000
        )

        return AudioAnalysisResult(
            bpm = bpm,
            musicKey = key?.key,
            camelot = key?.camelot,
            loudnessDb = loudnessDb,
            lufsIntegrated = lufsIntegrated,
            dynamicRange = dynamicRange,
            replayGain = replayGain,
            energy = energy,
            silenceAtStartMs = silence.silenceAtStartMs,
            silenceAtEndMs = silence.silenceAtEndMs,
            tuningHz = tuningHz,
            chordProgression = chordProgression,
            songStructureJson = songStructureJson,
            fadeInEndMs = fadeResult.fadeInEndMs,
            fadeOutStartMs = fadeResult.fadeOutStartMs,
            waveform = waveform
        )
    }

    companion object {
        /**
         * Stored in track_analysis.analysis_version by Phase 4.
         * v4: sample-rate-correct K-weighting (LUFS / ReplayGain), band-limited
         * decimation, and trailing silence measured from a tail decode.
         */
        const val CURRENT_ANALYSIS_VERSION = 4

        private const val TAG = "AudioAnalysisEngine"
    }
}



