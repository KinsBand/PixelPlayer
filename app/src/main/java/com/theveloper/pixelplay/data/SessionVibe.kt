package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.model.Song
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * What a song (or a blend of songs) feels like, as numbers that can be averaged, moved and
 * compared: energy, valence, danceability, acousticness, instrumentalness, tempo, mood, genre
 * family and sound. Every number carries a confidence (0 = unknown … 1 = measured), so thin
 * metadata can't pull a blend as hard as a measured song, and a guess never looks like a fact.
 */
internal class Feel(
    /** [ENERGY], [VALENCE], [DANCE], [ACOUSTIC], [INSTRUMENTAL], [TEMPO]; NaN = unknown. */
    val values: DoubleArray,
    /** Confidence per value, 0…1. */
    val confidence: DoubleArray,
    /** Mood label → share (a single song is one mood at 1.0; a blend holds a mix). */
    val moods: Map<String, Double>,
    /** Genre family → share. */
    val families: Map<String, Double>,
    /** Unit-length sound embedding, or null. */
    val sound: FloatArray?,
    /** How much the sound embedding counts (0…1). */
    val soundConfidence: Double,
) {
    fun value(index: Int): Double? = values[index].takeUnless { it.isNaN() }
    val energy: Double? get() = value(ENERGY)
    val tempo: Double? get() = value(TEMPO)
    val valence: Double? get() = value(VALENCE)

    /** A copy with one number replaced (used for relative shifts like "calmer"). */
    fun with(index: Int, value: Double, confidence: Double = this.confidence[index].coerceAtLeast(0.6)): Feel {
        val v = values.copyOf()
        val c = this.confidence.copyOf()
        v[index] = if (index == TEMPO) value else value.coerceIn(0.0, 1.0)
        c[index] = confidence
        return Feel(v, c, moods, families, sound, soundConfidence)
    }

    companion object {
        const val ENERGY = 0
        const val VALENCE = 1
        const val DANCE = 2
        const val ACOUSTIC = 3
        const val INSTRUMENTAL = 4
        /** log2 of the BPM folded into 70…140 (half / double tempo read the same). */
        const val TEMPO = 5
        const val SIZE = 6

        /** Confidence of a number guessed from genre / mood rather than measured. */
        const val GUESSED = 0.35

        private fun unit(value: Float?): Double? = value?.takeIf { it.isFinite() && it in 0f..1f }?.toDouble()

        fun foldTempo(bpm: Double): Double {
            var b = bpm
            while (b < 70.0) b *= 2.0
            while (b >= 140.0) b /= 2.0
            return ln(b) / ln(2.0)
        }

        /** Energy by mood word, for songs that were never analysed. */
        private val MOOD_ENERGY = mapOf(
            "energetic" to 0.8, "happy" to 0.68, "uplifting" to 0.7, "euphoric" to 0.8, "party" to 0.82,
            "aggressive" to 0.85, "angry" to 0.82, "intense" to 0.82, "hype" to 0.85, "dark" to 0.55,
            "romantic" to 0.42, "dreamy" to 0.35, "nostalgic" to 0.45, "melancholic" to 0.32, "melancholy" to 0.32,
            "sad" to 0.3, "chill" to 0.32, "calm" to 0.25, "relaxed" to 0.28, "peaceful" to 0.2, "sleepy" to 0.15,
        )

        /** Typical energy by genre family, for songs that were never analysed. */
        private val FAMILY_ENERGY = mapOf(
            "metal" to 0.88, "rock" to 0.72, "electronic" to 0.74, "hip_hop" to 0.66, "latin" to 0.74,
            "pop" to 0.64, "african" to 0.7, "reggae" to 0.55, "soul" to 0.52, "blues" to 0.48,
            "country" to 0.55, "folk" to 0.36, "jazz" to 0.38, "ambient" to 0.25, "classical" to 0.22,
        )

        private fun moodOf(song: Song): String? = song.mixIntelligence.mood?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("unknown", true) && !MixVibe.isMoodEstimated(song) }
            ?.lowercase(java.util.Locale.ROOT)

        fun of(song: Song, embeddings: Map<String, FloatArray> = emptyMap()): Feel {
            val mi = song.mixIntelligence
            val values = DoubleArray(SIZE) { Double.NaN }
            val confidence = DoubleArray(SIZE)
            fun put(index: Int, value: Double?, c: Double = 1.0) {
                if (value == null) return
                values[index] = value
                confidence[index] = c
            }
            put(ENERGY, unit(mi.energy))
            put(VALENCE, unit(mi.valence))
            put(DANCE, unit(mi.danceability))
            put(ACOUSTIC, unit(mi.acousticness))
            put(INSTRUMENTAL, unit(mi.instrumentalness))
            song.musicalFeatures.bpm?.takeIf { it.isFinite() && it in 30f..300f }?.let { put(TEMPO, foldTempo(it.toDouble())) }
            val mood = moodOf(song)
            val family = GenreTaxonomy.match(song.genre)?.family
            // Never analysed: a rough energy from the mood, else from the genre family.
            if (values[ENERGY].isNaN()) {
                val guess = mood?.let { m -> MOOD_ENERGY.entries.firstOrNull { m.contains(it.key) }?.value }
                    ?: family?.let(FAMILY_ENERGY::get)
                put(ENERGY, guess, GUESSED)
            }
            val sound = embeddings[song.id]
            return Feel(values, confidence,
                mood?.let { mapOf(it to 1.0) }.orEmpty(),
                family?.let { mapOf(it to 1.0) }.orEmpty(),
                sound, if (sound != null) 1.0 else 0.0)
        }

        /** The middle of several feels (each counted once). */
        fun average(feels: List<Feel>): Feel? {
            if (feels.isEmpty()) return null
            var result = feels.first()
            feels.drop(1).forEachIndexed { index, feel -> result = toward(result, feel, 1.0 / (index + 2)) }
            return result
        }

        /** [from] moved a share [t] (0…1) of the way toward [to]. */
        fun toward(from: Feel?, to: Feel, t: Double): Feel {
            val share = t.coerceIn(0.0, 1.0)
            if (from == null) {
                // Nothing to start from: the target itself, held only as firmly as the share.
                return Feel(to.values.copyOf(), DoubleArray(SIZE) { to.confidence[it] * share },
                    to.moods, to.families, to.sound, to.soundConfidence * share)
            }
            val values = DoubleArray(SIZE)
            val confidence = DoubleArray(SIZE)
            for (i in 0 until SIZE) {
                val a = from.values[i]
                val b = to.values[i]
                val ca = from.confidence[i]
                val cb = to.confidence[i]
                when {
                    b.isNaN() || cb <= 0.0 -> { values[i] = a; confidence[i] = ca }
                    a.isNaN() || ca <= 0.0 -> { values[i] = b; confidence[i] = cb * share }
                    else -> {
                        // A guess moves a measured value less than a measurement does.
                        val pull = share * (cb / maxOf(cb, ca)).coerceAtMost(1.0)
                        values[i] = a + (b - a) * pull
                        confidence[i] = (ca + (cb - ca) * share).coerceIn(0.0, 1.0)
                    }
                }
            }
            return Feel(values, confidence, mix(from.moods, to.moods, share), mix(from.families, to.families, share),
                soundToward(from.sound, from.soundConfidence, to.sound, share),
                when {
                    to.sound == null -> from.soundConfidence
                    from.sound == null -> to.soundConfidence * share
                    else -> from.soundConfidence + (to.soundConfidence - from.soundConfidence) * share
                })
        }

        /**
         * [from] pushed away from [away] by a share [t]: numbers close to the unwanted song move
         * off it (far ones stay), its mood and genre family lose weight, and the sound loses
         * the part that matches it.
         */
        fun awayFrom(from: Feel?, away: Feel, t: Double): Feel? {
            if (from == null) return null
            val share = t.coerceIn(0.0, 1.0)
            val values = from.values.copyOf()
            for (i in 0 until SIZE) {
                val a = from.values[i]
                val b = away.values[i]
                if (a.isNaN() || b.isNaN()) continue
                val reach = if (i == TEMPO) 0.35 else 0.3
                val gap = abs(a - b)
                if (gap >= reach) continue
                val direction = when {
                    a > b -> 1.0
                    a < b -> -1.0
                    i == TEMPO -> 1.0
                    else -> if (b > 0.5) -1.0 else 1.0
                }
                val moved = a + direction * (reach - gap) * share * away.confidence[i]
                values[i] = if (i == TEMPO) moved else moved.coerceIn(0.0, 1.0)
            }
            fun fade(map: Map<String, Double>, unwanted: Map<String, Double>) =
                map.mapValues { (key, w) -> w * (1.0 - share * (unwanted[key] ?: 0.0)) }.filterValues { it > 0.01 }
            val sound = from.sound?.let { s ->
                val other = away.sound ?: return@let s
                var dot = 0f
                for (i in s.indices) dot += s[i] * other[i]
                normalise(FloatArray(s.size) { s[it] - (share * dot * other[it]).toFloat() })
            }
            return Feel(values, from.confidence.copyOf(), fade(from.moods, away.moods), fade(from.families, away.families),
                sound, from.soundConfidence)
        }

        private fun mix(a: Map<String, Double>, b: Map<String, Double>, t: Double): Map<String, Double> {
            if (b.isEmpty()) return a
            if (a.isEmpty()) return b.mapValues { it.value * t }
            val keys = a.keys + b.keys
            return keys.associateWith { (a[it] ?: 0.0) * (1 - t) + (b[it] ?: 0.0) * t }.filterValues { it > 0.01 }
        }

        private fun soundToward(a: FloatArray?, ca: Double, b: FloatArray?, t: Double): FloatArray? {
            if (b == null) return a
            if (a == null || a.size != b.size || ca <= 0.0) return b
            return normalise(FloatArray(a.size) { (a[it] * (1 - t) + b[it] * t).toFloat() })
        }

        private fun normalise(v: FloatArray): FloatArray? {
            var n = 0.0
            for (x in v) n += x * x
            if (n <= 1e-9) return null
            val s = (1.0 / sqrt(n)).toFloat()
            return FloatArray(v.size) { v[it] * s }
        }

        /**
         * How close [song] is to [target], 0 (nothing alike) … 1 (same feel). Unknown numbers
         * don't count either way; a small prior keeps thinly described songs near average.
         */
        fun fit(song: Feel, target: Feel): Double {
            var sum = MixVibeSimilarity.PRIOR
            var weight = 1.0
            fun add(agreement: Double, w: Double) {
                if (w <= 0.0) return
                sum += agreement.coerceIn(0.0, 1.0) * w
                weight += w
            }
            val featureWeights = doubleArrayOf(1.6, 1.0, 0.8, 0.8, 0.6, 0.8)
            for (i in 0 until SIZE) {
                val a = song.values[i]
                val b = target.values[i]
                if (a.isNaN() || b.isNaN()) continue
                val agreement = if (i == TEMPO) 1.0 - abs(a - b) / 0.35 else 1.0 - abs(a - b) / 0.5
                add(agreement, featureWeights[i] * song.confidence[i] * target.confidence[i])
            }
            song.moods.keys.firstOrNull()?.let { mood ->
                val total = target.moods.values.sum()
                if (total > 0.05) add((target.moods[mood] ?: 0.0) / target.moods.values.max(), 0.8 * total.coerceAtMost(1.0))
            }
            song.families.keys.firstOrNull()?.let { family ->
                val total = target.families.values.sum()
                if (total > 0.05) add((target.families[family] ?: 0.0) / target.families.values.max(), 1.2 * total.coerceAtMost(1.0))
            }
            val a = song.sound
            val b = target.sound
            if (a != null && b != null && a.size == b.size) {
                var dot = 0f
                for (i in a.indices) dot += a[i] * b[i]
                add((dot - 0.55) / 0.4, 2.5 * target.soundConfidence)
            }
            return (sum / weight).coerceIn(0.0, 1.0)
        }
    }
}

/**
 * The mix's running sense of "the vibe right now": one target feel that every control moves a
 * measured amount, instead of each control re-planning the queue on its own.
 *
 * - Songs that are actually listened to set the base (a running average, so the vibe follows
 *   what you finish, not what was planned).
 * - Songs you queue pull the target toward them (25 % if they already fit, 15 % if they're a
 *   different vibe, +10 % when they play through), fading by half every 6 songs.
 * - Removals, early skips and dislikes push it away from that song (20 / 10 / 30 %).
 * - Steers (More like this, the song / artist box) pull toward a song, an artist, a blend of
 *   two, or along a path from one to another; relative shifts ("calmer", "faster") move one
 *   number and fade out after [SHIFT_FADE_SONGS] songs.
 *
 * Thread-safe; pure Kotlin (no Android), so it is unit-testable.
 */
internal class SessionVibe {
    enum class Axis { ENERGY, TEMPO, VALENCE }

    /** One thing moving the vibe. Steers have an id the UI can show and remove. */
    class Influence(
        val id: String,
        val feel: Feel?,
        val rate: Double,
        val away: Boolean = false,
        /** Strength halves every this many songs (null = no decay). */
        val halfLifeSongs: Double? = null,
        /** Gone after this many songs (null = until removed). */
        val lastsSongs: Int? = null,
        /** Fades linearly to nothing over this many songs (null = no fade). */
        val fadeSongs: Int? = null,
        /** For a path: where it ends; [feel] is where it starts. */
        val pathTo: Feel? = null,
        val pathSongs: Int = 20,
        /** For a relative shift: which number moves, and by how much at full strength. */
        val shiftAxis: Axis? = null,
        val shift: Double = 0.0,
        val startedAt: Int = 0,
    )

    private var base: Feel? = null
    private var songs = 0
    private val influences = ArrayList<Influence>()
    private val recentEnergies = ArrayDeque<Double>()
    private var follow: Double? = null
    private var cached: Feel? = null
    private var cachedValid = false

    /** Songs that have started since the session began (drives every fade). */
    @get:Synchronized val songCount: Int get() = songs

    @Synchronized fun reset() {
        base = null
        songs = 0
        influences.clear()
        recentEnergies.clear()
        follow = null
        invalidate()
    }

    /** The feel the session starts from (the seeds), when nothing has been listened to yet. */
    @Synchronized fun seed(feel: Feel?) {
        if (feel == null) return
        base = if (base == null) Feel.toward(null, feel, 0.8) else Feel.toward(base, feel, 0.3)
        feel.energy?.let { e -> if (follow == null) follow = e }
        invalidate()
    }

    /** A new song started. Steers and picks fade by songs, not by minutes. */
    @Synchronized fun advanceSong() {
        songs++
        influences.removeAll { strength(it) <= 0.005 && it.halfLifeSongs == null }
        influences.removeAll { it.halfLifeSongs != null && strength(it) < 0.01 }
        invalidate()
    }

    /**
     * A song was listened to ([reward] from [MixRewards]: ≥ 0.4 past half way, 1.0 finished):
     * the base moves toward it, and Energy Follow takes a capped step toward the last 3.
     */
    @Synchronized fun listened(feel: Feel, reward: Double) {
        val rate = BASE_RATE * reward.coerceIn(0.4, 1.0)
        base = Feel.toward(base, feel, rate)
        feel.energy?.let { e ->
            recentEnergies.addLast(e)
            while (recentEnergies.size > 3) recentEnergies.removeFirst()
            val average = recentEnergies.average()
            follow = follow?.let { it + (average - it).coerceIn(-FOLLOW_STEP, FOLLOW_STEP) } ?: average
        }
        invalidate()
    }

    /** Pulls toward [feel] by [rate], fading by half every [halfLife] songs. */
    @Synchronized fun pull(id: String, feel: Feel, rate: Double, halfLife: Double = PICK_HALF_LIFE) {
        influences.removeAll { it.id == id }
        influences += Influence(id, feel, rate, halfLifeSongs = halfLife, startedAt = songs)
        invalidate()
    }

    /** Pushes away from [feel] by [rate], fading by half every [halfLife] songs. */
    @Synchronized fun push(id: String, feel: Feel, rate: Double, halfLife: Double = PICK_HALF_LIFE) {
        influences.removeAll { it.id == id }
        influences += Influence(id, feel, rate, away = true, halfLifeSongs = halfLife, startedAt = songs)
        invalidate()
    }

    /** Adds (or replaces, by id) a steer; see [Influence]. */
    @Synchronized fun steer(influence: Influence) {
        influences.removeAll { it.id == influence.id }
        influences += Influence(influence.id, influence.feel, influence.rate, influence.away, influence.halfLifeSongs,
            influence.lastsSongs, influence.fadeSongs, influence.pathTo, influence.pathSongs, influence.shiftAxis,
            influence.shift, startedAt = songs)
        invalidate()
    }

    @Synchronized fun remove(id: String): Boolean = influences.removeAll { it.id == id }.also { if (it) invalidate() }

    @Synchronized fun removeAll(predicate: (String) -> Boolean) {
        if (influences.removeAll { predicate(it.id) }) invalidate()
    }

    @Synchronized fun has(id: String): Boolean = influences.any { it.id == id }

    /** How strongly [influence] counts now (0 = expired). */
    @Synchronized fun strength(influence: Influence): Double {
        val age = (songs - influence.startedAt).coerceAtLeast(0)
        var s = influence.rate
        influence.halfLifeSongs?.let { s *= 0.5.pow(age / it) }
        influence.lastsSongs?.let { lasts -> if (age >= lasts) s *= (1.0 - (age - lasts + 1) / 2.0).coerceAtLeast(0.0) }
        influence.fadeSongs?.let { fade -> s *= (1.0 - age.toDouble() / fade).coerceAtLeast(0.0) }
        return s
    }

    /** The vibe the mix aims for right now, or null when nothing is known yet. */
    @Synchronized fun target(): Feel? {
        if (cachedValid) return cached
        var t = base
        for (influence in influences) {
            val s = strength(influence)
            if (s <= 0.0) continue
            val axis = influence.shiftAxis
            if (axis != null) {
                val current = t ?: continue
                val index = when (axis) {
                    Axis.ENERGY -> Feel.ENERGY
                    Axis.TEMPO -> Feel.TEMPO
                    Axis.VALENCE -> Feel.VALENCE
                }
                val now = current.value(index) ?: if (axis == Axis.TEMPO) continue else 0.5
                // The shift's own strength is already in [shift]; [rate] is 1 for shifts.
                t = current.with(index, now + influence.shift * (s / influence.rate.coerceAtLeast(1e-6)))
                continue
            }
            val start = influence.feel ?: continue
            val goal = influence.pathTo?.let { end ->
                val age = (songs - influence.startedAt).coerceAtLeast(0)
                Feel.toward(start, end, (age.toDouble() / influence.pathSongs).coerceIn(0.0, 1.0))
            } ?: start
            t = if (influence.away) Feel.awayFrom(t, goal, s) else Feel.toward(t, goal, s)
        }
        cached = t
        cachedValid = true
        return t
    }

    /** 0…1 fit of [feel] to the current target ([MixVibeSimilarity.PRIOR] when there is none). */
    fun fit(feel: Feel): Double = target()?.let { Feel.fit(feel, it) } ?: MixVibeSimilarity.PRIOR

    /** Energy Follow: the average energy of the last 3 listened songs, moving ≤ 0.08 a song. */
    @get:Synchronized val followEnergy: Double? get() = follow

    /** The total of active energy shifts ("calmer", "more energy"), at their current strength. */
    @Synchronized fun energyShiftNow(): Double = influences.filter { it.shiftAxis == Axis.ENERGY }
        .sumOf { it.shift * (strength(it) / it.rate.coerceAtLeast(1e-6)) }

    /** Ids of every influence still active (steers that expired are gone). */
    @Synchronized fun activeIds(): Set<String> = influences.mapTo(HashSet()) { it.id }

    private fun invalidate() { cachedValid = false }

    companion object {
        const val BASE_RATE = 0.3
        const val PICK_HALF_LIFE = 6.0
        const val FOLLOW_STEP = 0.08
        /** Relative shifts from the prompt box ("calmer", "faster") fade out over this many songs. */
        const val SHIFT_FADE_SONGS = 10
        /** "For a few songs". */
        const val TEMPORARY_SONGS = 6
        /** Pick pulls: fits what's playing / a different vibe / played through. */
        const val PICK_FITS = 0.25
        const val PICK_DIFFERENT = 0.15
        const val PICK_PLAYED = 0.10
        const val PICK_FIT_THRESHOLD = 0.6
        /** Push-aways: removed from the queue / skipped early / disliked. */
        const val REMOVED = 0.20
        const val SKIPPED = 0.10
        const val DISLIKED = 0.30
        /** Steer strengths: "a little" / default / "a lot" or "only". */
        const val STEER_LITTLE = 0.15
        const val STEER_DEFAULT = 0.35
        const val STEER_STRONG = 0.60
        /** Relative shift sizes at full strength. */
        const val ENERGY_SHIFT = 0.15
        const val VALENCE_SHIFT = 0.15
        /** +10 % tempo, in log2 units. */
        val TEMPO_SHIFT = ln(1.1) / ln(2.0)
    }
}
