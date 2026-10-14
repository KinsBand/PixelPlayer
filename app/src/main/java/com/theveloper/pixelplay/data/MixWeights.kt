package com.theveloper.pixelplay.data

import kotlin.math.roundToInt

/**
 * Every weight the mix engine uses, in one place and versioned. The planner, the taste profile
 * and the transition scores all read from here, and [version] is written into each logged
 * recommendation (via [MixSequencePlanner.MODEL_VERSION]), so an offline replay
 * ([MixReplay]) can compare one set of weights against another on real listening.
 *
 * Change a weight → bump [version].
 */
internal data class MixWeights(
    val version: String = "w3",

    // ── Relevance to what's playing ──────────────────────────────────────────────
    /**
     * Multiplies [MixVibe.sessionFit] (0…12: same artist 6, same genre 4, same album 2).
     * Lowered from 1.0 in w3 so artist / genre metadata no longer outweighs how a song feels.
     */
    val sessionFit: Double = 0.6,
    /**
     * How close a song feels to the session vibe ([SessionVibe]): energy, mood, tempo, sound…
     * Applied to (fit − prior), so an average song scores about 0 and a close match about +4.
     */
    val feelFit: Double = 6.0,
    /**
     * A song whose fit can't be judged (no recognised genre, no shared artist) gets this share
     * of the batch's average fit instead of 0, so thin metadata isn't read as "doesn't fit".
     */
    val unknownFitShare: Double = 0.75,
    /** Liked / favourite song. */
    val favorite: Double = 3.0,
    /** Liked song that doesn't fit what's playing (or a locked vibe): capped lower. */
    val favoriteLowFit: Double = 1.5,
    /** Raw session fit below this counts as "doesn't fit" for [favoriteLowFit]. */
    val lowFitThreshold: Double = 4.0,
    /** Vibe fit boost below this counts as "doesn't fit the locked vibe". */
    val lowVibeBoost: Double = 1.6,

    // ── Listening history ────────────────────────────────────────────────────────
    /** Per unit of positive reward (see [MixRewards]), fading over 30 days, capped. */
    val enjoyment: Double = 1.0,
    val enjoymentCap: Double = 6.0,
    /** Enjoyed before, but not in the last 14 days. */
    val rediscovery: Double = 3.0,
    /** Per play in the last day (fading), so the same song doesn't come round too soon. */
    val fatiguePerPlay: Double = 3.0,
    /** Rejections of this song in this mix, fading over 30 minutes, capped. */
    val recentRejectionCap: Double = 3.0,

    // ── Taste ([MixTasteProfile]) ────────────────────────────────────────────────
    val longTermTaste: Double = 4.0,
    val recentTaste: Double = 3.0,
    val mixTaste: Double = 3.0,
    /** Session bandit: a Thompson sample per artist / genre, centred, times this. */
    val sessionBandit: Double = 3.0,
    val artistFatigue: Double = 0.35,
    /** Songs you finished in the same sessions as the current seeds. */
    val coListen: Double = 3.0,

    // ── Sequencing ───────────────────────────────────────────────────────────────
    /** Soft penalty for the same artist 1 / 2 songs back (beyond the hard gap). */
    val artistRepeat: Double = 7.0,
    val albumRepeat: Double = 2.0,
    /** Hard rule: an artist can't return within this many songs (unless nothing else fits). */
    val minArtistGap: Int = 4,
    /** At most this many songs by one artist in any 20 in a row (Focused 5 … Varied 1). */
    val artistCapPer20: Int = 4,
    /** A discovery is preferred (to meet the Familiar ↔ Discover balance) within this many points of the best pick. */
    val discoveryTolerance: Double = 2.5,
    /** A friend's song is preferred (to meet the friends' share) within this many points of the best pick. */
    val friendTolerance: Double = 2.5,
    /** A song counts as familiar for this long after it was last really listened to. */
    val familiarDays: Double = 60.0,

    // ── Transitions ([MixVibe]) ──────────────────────────────────────────────────
    val energy: Double = 4.0,
    val valence: Double = 2.0,
    val dance: Double = 1.5,
    val acoustic: Double = 1.5,
    val vocal: Double = 1.0,
    val tempo: Double = 1.5,
    val mood: Double = 1.0,
    /** Camelot-wheel key compatibility between neighbours ([MixHarmony]). */
    val harmonic: Double = 1.5,

    // ── Sound and energy (Phase 4) ───────────────────────────────────────────────
    /** Sounds like the seeds: on-device VGGish embeddings, as a within-batch percentile. */
    val soundAlike: Double = 4.0,
    /** Distance from the Energy setting (Calm / Steady / Hype), when one is chosen. */
    val energyTarget: Double = 3.0,
) {
    /**
     * The Variety setting (0 = focused, 1 = varied) sets the artist gap: 2 songs when focused,
     * up to 8 when varied; the default 0.35 gives 4. It also caps how many songs one artist gets in
     * any 20 (Focused 5 … Varied 1; the default gives 4).
     */
    fun withVariety(variety: Double): MixWeights {
        val v = variety.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: DEFAULT_VARIETY
        return copy(
            minArtistGap = (2 + v * 6).roundToInt().coerceIn(2, 8),
            // Focused 5 songs per artist in any 20 … Varied 1.
            artistCapPer20 = (5 - v * 4).roundToInt().coerceIn(1, 5),
        )
    }

    companion object {
        val DEFAULT = MixWeights()
        const val DEFAULT_VARIETY = 0.35
    }
}
