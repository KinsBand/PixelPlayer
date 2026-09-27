package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.model.Song
import java.util.Locale
import kotlin.random.Random

/**
 * Taste clusters (artist, genre, genre family) learned from listening. Unrelated genres never
 * collapse into one averaged vector.
 *
 * Every play is labelled by [MixRewards]. Positive plays build long-term (60-day) and recent
 * (7-day) taste; negative plays only lower *recent* taste, and at half strength, so a few
 * skips shape the current context without becoming a permanent aversion. Plays in the same
 * mix flavour build a 21-day mix taste.
 *
 * Within the current session each cluster is a Beta(1 + positive, 1 + negative) bandit and is
 * scored with one Thompson sample per plan ([sessionId] + [random]); with no random source the
 * posterior mean is used, so tests are deterministic. Clusters with no evidence this session
 * score 0, which keeps exploration to what has actually been tried.
 */
internal class MixTasteProfile(
    catalogue: List<Song>, history: List<MixAttempt>, favorites: Set<String>,
    private val mixId: String, private val now: Long,
    private val sessionId: String? = null,
    private val weights: MixWeights = MixWeights.DEFAULT,
    private val random: Random? = null
) {
    data class Evidence(var positive: Double = 0.0, var negative: Double = 0.0) {
        val confidence get() = (positive + negative) / (positive + negative + 4.0)
        val preference get() = ((positive - negative) / (positive + negative + 2.0)).coerceIn(-1.0, 1.0)
        val value get() = preference * confidence
    }
    private val longTerm = mutableMapOf<String, Evidence>()
    private val recent = mutableMapOf<String, Evidence>()
    private val mix = mutableMapOf<String, Evidence>()
    private val session = mutableMapOf<String, Evidence>()
    private val artistExposure = mutableMapOf<String, Double>()
    /** One Thompson sample per cluster for this plan, drawn on first use. */
    private val banditSamples = mutableMapOf<String, Double>()

    init {
        val songs = catalogue.associateBy { it.id }
        catalogue.filter { it.id in favorites || it.isFavorite }.forEach { song ->
            keys(song).forEach { longTerm.getOrPut(it) { Evidence() }.positive += 2.0 }
        }
        history.filter { it.startedAt <= now }.forEach { attempt ->
            val song = songs[attempt.songId] ?: return@forEach
            if (attempt.activeMs >= 5_000) {
                artistExposure.merge(normalize(song.artist), MixRewards.fade(now, attempt.startedAt, DAY), Double::plus)
            }
            // No label for errors, process death, focus loss, or explicit scoped exclusions.
            val reward = MixRewards.reward(attempt) ?: return@forEach
            if (reward == 0.0) return@forEach
            val thisSession = sessionId != null && attempt.sessionId == sessionId
            keys(song).forEach { key ->
                if (reward > 0) {
                    update(longTerm, key, reward * MixRewards.fade(now, attempt.startedAt, 60 * DAY))
                    update(recent, key, reward * MixRewards.fade(now, attempt.startedAt, 7 * DAY))
                } else {
                    // Skips shape the current context, never permanent genre / artist aversion.
                    update(recent, key, reward * 0.5 * MixRewards.fade(now, attempt.startedAt, 7 * DAY))
                }
                if (attempt.mixId == mixId) update(mix, key, reward * MixRewards.fade(now, attempt.startedAt, 21 * DAY))
                if (thisSession) update(session, key, reward)
            }
        }
    }

    fun components(song: Song): Map<String, Double> {
        val keys = keys(song)
        fun affinity(map: Map<String, Evidence>) = keys.maxOfOrNull { map[it]?.value ?: 0.0 } ?: 0.0
        return linkedMapOf(
            "longTermTaste" to affinity(longTerm) * weights.longTermTaste,
            "recentTaste" to affinity(recent) * weights.recentTaste,
            "mixTaste" to affinity(mix) * weights.mixTaste,
            "sessionBandit" to bandit(keys) * weights.sessionBandit,
            "artistFatigue" to -(artistExposure[normalize(song.artist)] ?: 0.0).coerceAtMost(8.0) * weights.artistFatigue
        )
    }

    /**
     * −1…+1: the Thompson sample (centred) of the song's most specific cluster played this
     * session (its artist, else its genre, else its genre family), or 0 when none has been.
     * Most specific first, so skipping one artist doesn't sink every artist in that genre.
     */
    private fun bandit(keys: List<String>): Double {
        val key = keys.firstOrNull { session.containsKey(it) } ?: return 0.0
        val sample = banditSamples.getOrPut(key) {
            val evidence = session.getValue(key)
            MixRewards.sampleBeta(1.0 + evidence.positive, 1.0 + evidence.negative, random)
        }
        return (sample - 0.5) * 2.0
    }

    fun confidence(song: Song): Double = keys(song).maxOfOrNull { longTerm[it]?.confidence ?: 0.0 } ?: 0.0

    private fun update(map: MutableMap<String, Evidence>, key: String, reward: Double) {
        val evidence = map.getOrPut(key) { Evidence() }
        if (reward > 0) evidence.positive += reward else evidence.negative -= reward
    }

    companion object {
        private const val DAY = 86_400_000.0
        private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT)
        private fun keys(song: Song): List<String> = buildList {
            normalize(song.artist).takeIf { it.isNotEmpty() && it !in setOf("unknown artist", "<unknown>") }?.let { add("artist:$it") }
            song.genre?.let(::normalize)?.takeIf { it.isNotEmpty() && it !in setOf("unknown", "<unknown>", "music") }?.let { tag ->
                val genre = GenreTaxonomy.match(tag)
                add("genre:${genre?.id ?: tag}")
                genre?.let { add("genreFamily:${it.family}") }
            }
        }
    }
}
