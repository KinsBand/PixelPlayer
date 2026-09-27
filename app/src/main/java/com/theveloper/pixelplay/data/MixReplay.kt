package com.theveloper.pixelplay.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.random.Random

/**
 * Offline evaluation of the mix on real listening, from the two tables the app already keeps:
 * every recommendation (with its score components, selection probability and model version)
 * and every play (joined through the play's decision id).
 *
 * - [summarize]: per model version, how its picks were received — early-skip rate, finish rate,
 *   mean reward ([MixRewards]), and a self-normalised inverse-propensity estimate of the reward
 *   of the exploration picks (reward ÷ selection probability), which corrects for how rarely
 *   the planner chose them.
 * - [auc]: ranking accuracy — the chance a pick the listener liked scored above one they
 *   rejected. Re-weighting the logged components ([scale]) answers "would these weights have
 *   ranked real outcomes better?" before anything ships.
 */
internal object MixReplay {
    data class Summary(
        val modelVersion: String,
        val plays: Int,
        val earlySkipRate: Double,
        val finishRate: Double,
        val meanReward: Double,
        /** Self-normalised IPS estimate of exploration-pick reward; null with < 5 explored plays. */
        val explorationReward: Double?
    )

    private data class Outcome(val recommendation: MixRecommendation, val attempt: MixAttempt, val reward: Double)

    private fun join(recommendations: List<MixRecommendation>, attempts: List<MixAttempt>, since: Long): List<Outcome> {
        val byId = recommendations.filter { it.plannedAt >= since }.associateBy { it.id }
        return attempts.asSequence()
            .filter { it.endReason != MixEndReason.UNKNOWN.name }
            .mapNotNull { attempt ->
                val recommendation = attempt.decisionId?.let(byId::get) ?: return@mapNotNull null
                val reward = MixRewards.reward(attempt) ?: return@mapNotNull null
                Outcome(recommendation, attempt, reward)
            }
            // One outcome per recommendation (the final attempt for that decision).
            .groupBy { it.recommendation.id }.values.map { outcomes -> outcomes.maxByOrNull { it.attempt.startedAt }!! }
    }

    fun summarize(recommendations: List<MixRecommendation>, attempts: List<MixAttempt>, since: Long): List<Summary> =
        join(recommendations, attempts, since).groupBy { it.recommendation.modelVersion }.map { (version, outcomes) ->
            val explored = outcomes.filter { it.recommendation.explored && it.recommendation.selectionProbability > 0.0 }
            val ips = if (explored.size < 5) null else {
                val weights = explored.map { 1.0 / it.recommendation.selectionProbability }
                explored.zip(weights).sumOf { (o, w) -> o.reward * w } / weights.sum()
            }
            Summary(
                modelVersion = version,
                plays = outcomes.size,
                earlySkipRate = outcomes.count { MixRewards.isEarlySkip(it.attempt) }.toDouble() / outcomes.size,
                finishRate = outcomes.count { it.reward >= 1.0 }.toDouble() / outcomes.size,
                meanReward = outcomes.map { it.reward }.average(),
                explorationReward = ips
            )
        }.sortedByDescending { it.plays }

    private val componentType = object : TypeToken<Map<String, Double>>() {}.type

    /**
     * Probability that a liked pick (reward > 0) outscored a rejected one (reward < 0) under the
     * logged components, each multiplied by [scale] (missing = 1). 0.5 = no better than chance.
     * Null when there are fewer than 10 of either. At most 400 of each are compared.
     */
    fun auc(
        recommendations: List<MixRecommendation>, attempts: List<MixAttempt>, since: Long,
        scale: Map<String, Double> = emptyMap()
    ): Double? {
        val gson = Gson()
        val scored = join(recommendations, attempts, since).mapNotNull { outcome ->
            val parsed: Map<String, Double>? = try {
                gson.fromJson<Map<String, Double>>(outcome.recommendation.scoreComponents, componentType)
            } catch (_: Exception) {
                null
            }
            val components = parsed?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            components.entries.sumOf { (name, value) -> value * (scale[name] ?: 1.0) } to outcome.reward
        }
        val random = Random(42)
        val liked = scored.filter { it.second > 0 }.map { it.first }.shuffled(random).take(400)
        val rejected = scored.filter { it.second < 0 }.map { it.first }.shuffled(random).take(400)
        if (liked.size < 10 || rejected.size < 10) return null
        var wins = 0.0
        for (l in liked) for (r in rejected) wins += when {
            l > r -> 1.0
            l == r -> 0.5
            else -> 0.0
        }
        return wins / (liked.size * rejected.size)
    }
}
