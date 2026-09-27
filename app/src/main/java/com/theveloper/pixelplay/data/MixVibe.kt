package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.model.Song
import kotlin.math.abs
import kotlin.math.ln

/**
 * Transition scores between two neighbouring songs.
 *
 * Missing data is scored as *average*, not as a perfect match. Every real comparison can only
 * score at or below 0, so treating "unknown" as 0 used to rank unanalysed streams above
 * analysed songs. A missing value now costs what an average pair of songs costs.
 */
internal object MixVibe {
    /** Typical |a − b| of a 0..1 feature between two random library songs. */
    private const val MEAN_FEATURE_GAP = 0.25
    /** Typical tempo distance (|ln ratio| after octave folding) between two random songs. */
    private const val MEAN_TEMPO_GAP = 0.2
    /** Chance that two random songs share a mood label (≈ 1 in 7 labels). */
    private const val MOOD_BASE_RATE = 0.15

    /**
     * True when a song's mood was only guessed from its genre and BPM (the metadata gatherer
     * marks these). A guessed mood would just repeat the genre signal, so it counts as unknown.
     * Set once by [AdaptiveMix]; defaults to "nothing is estimated".
     */
    @Volatile var isMoodEstimated: (Song) -> Boolean = { false }

    private fun unit(value: Float?) = value?.takeIf { it.isFinite() && it in 0f..1f }
    private fun distance(a: Float?, b: Float?, weight: Double): Double {
        val left = unit(a)
        val right = unit(b)
        if (left == null || right == null) return -MEAN_FEATURE_GAP * weight
        return -abs(left - right) * weight
    }

    private fun bpm(song: Song) = song.musicalFeatures.bpm?.takeIf { it.isFinite() && it in 30f..300f }

    private fun mood(song: Song): String? = song.mixIntelligence.mood?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("unknown", true) && !isMoodEstimated(song) }

    fun components(previous: Song?, next: Song, weights: MixWeights = MixWeights.DEFAULT): Map<String, Double> {
        if (previous == null) return emptyMap()
        val a = previous.mixIntelligence
        val b = next.mixIntelligence
        val bpmA = bpm(previous)
        val bpmB = bpm(next)
        // Catalogue BPMs (Deezer) are sometimes half or double the felt tempo; fold octaves.
        val tempoGap = if (bpmA != null && bpmB != null)
            minOf(abs(ln(bpmB * 0.5 / bpmA)), abs(ln(bpmB * 1.0 / bpmA)), abs(ln(bpmB * 2.0 / bpmA))).coerceAtMost(1.0) else MEAN_TEMPO_GAP
        val moodA = mood(previous)
        val moodB = mood(next)
        return mapOf(
            "energyProgression" to distance(unit(a.outroEnergy) ?: a.energy, unit(b.introEnergy) ?: b.energy, weights.energy),
            "moodContinuity" to weights.mood * if (moodA != null && moodB != null) (if (moodA.equals(moodB, true)) 1.0 else 0.0) else MOOD_BASE_RATE,
            "valenceContinuity" to distance(a.valence, b.valence, weights.valence),
            "danceContinuity" to distance(a.danceability, b.danceability, weights.dance),
            "acousticContinuity" to distance(a.acousticness, b.acousticness, weights.acoustic),
            "vocalContinuity" to distance(a.instrumentalness, b.instrumentalness, weights.vocal),
            "tempoCompatibility" to -tempoGap * weights.tempo,
            // Harmonic mixing: neighbours on the Camelot wheel blend; clashing keys don't.
            // Centred on a random pair's compatibility, so an unknown key scores 0 (average).
            "harmonicMix" to (MixHarmony.compatibility(previous, next)?.let { (it - MixHarmony.RANDOM_PAIR) * weights.harmonic } ?: 0.0)
        )
    }

    fun sessionFit(song: Song, seeds: List<Song>): Double {
        val recent = seeds.takeLast(4)
        if (recent.isEmpty()) return 0.0
        val weights = recent.indices.map { 1 shl it }
        return recent.mapIndexed { index, seed -> mixAffinity(song, listOf(seed)) * weights[index].toDouble() }
            .sum() / weights.sum()
    }
}

/**
 * Camelot-wheel key handling. Analysed keys arrive as Camelot codes ("8A") or key names
 * ("A minor", "C#m", "Db major"); both are read.
 */
internal object MixHarmony {
    /** Average [compatibility] of two random keys (1 same, 3 × 0.75 neighbours, 2 × 0.25 of 24). */
    const val RANDOM_PAIR = (1.0 + 3 * 0.75 + 2 * 0.25) / 24.0

    private val CAMELOT = Regex("^(1[0-2]|[1-9])\\s*([ABab])$")
    private val NOTE = Regex("^([A-Ga-g])([#♯b♭]?)\\s*(.*)$")
    private val PITCH = mapOf("C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11)
    /** Camelot number for each pitch class, major (B) and minor (A). */
    private val MAJOR = intArrayOf(8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1)
    private val MINOR = intArrayOf(5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10)

    /** Parsed keys: a library only has a few dozen distinct key strings, parsed once each. */
    private val parsed = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Boolean>>()
    private val UNREADABLE = 0 to false

    /** (number 1–12, minor?) or null when the key can't be read. */
    fun camelot(key: String?, mode: String? = null): Pair<Int, Boolean>? {
        if (key.isNullOrBlank()) return null
        if (parsed.size > 512) parsed.clear()
        val cached = parsed.getOrPut(key + "\u0000" + mode.orEmpty()) { parse(key, mode) ?: UNREADABLE }
        return cached.takeIf { it !== UNREADABLE }
    }

    private fun parse(key: String, mode: String?): Pair<Int, Boolean>? {
        val text = key.trim().takeIf { it.isNotEmpty() } ?: return null
        CAMELOT.matchEntire(text)?.let { return it.groupValues[1].toInt() to it.groupValues[2].equals("A", true) }
        val match = NOTE.matchEntire(text) ?: return null
        var pitch = PITCH[match.groupValues[1].uppercase()] ?: return null
        when (match.groupValues[2]) { "#", "♯" -> pitch += 1; "b", "♭" -> pitch -= 1 }
        pitch = (pitch + 12) % 12
        val rest = match.groupValues[3].lowercase()
        val minor = when {
            rest.startsWith("min") || rest == "m" || rest.startsWith("m ") -> true
            rest.startsWith("maj") -> false
            else -> mode?.lowercase()?.startsWith("min") == true
        }
        return (if (minor) MINOR[pitch] else MAJOR[pitch]) to minor
    }

    /** 1 same key, 0.75 a neighbour (±1, or relative major/minor), 0.25 two steps, else 0; null if unknown. */
    fun compatibility(a: Song, b: Song): Double? {
        val x = camelot(a.musicalFeatures.key, a.musicalFeatures.mode) ?: return null
        val y = camelot(b.musicalFeatures.key, b.musicalFeatures.mode) ?: return null
        val steps = minOf((x.first - y.first + 12) % 12, (y.first - x.first + 12) % 12)
        return when {
            steps == 0 && x.second == y.second -> 1.0
            steps == 0 -> 0.75
            steps == 1 && x.second == y.second -> 0.75
            steps == 2 && x.second == y.second -> 0.25
            else -> 0.0
        }
    }
}
