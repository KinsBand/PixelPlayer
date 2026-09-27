package com.theveloper.pixelplay.data

/**
 * "You finished these together": a small graph of songs played through in the same listening
 * session, built from the attempts history on each plan (no extra storage).
 *
 * Within a session, songs with a positive reward ([MixRewards]) are taken in play order and
 * each is linked to the next [WINDOW] − 1 of them, weighted by 1 / distance and faded over
 * [FADE_DAYS]. [affinity] is a saturating 0…1 score of a candidate against the current seeds,
 * so it can suggest songs no metadata connects (a different genre you always play after
 * another).
 */
internal class MixCoListen(history: List<MixAttempt>, keyById: Map<String, String>, now: Long) {
    private val edges = HashMap<String, HashMap<String, Double>>()

    init {
        history.asSequence()
            .filter { it.sessionId != null && (MixRewards.reward(it) ?: 0.0) > 0.0 }
            .groupBy { it.sessionId!! }
            .values.forEach { plays ->
                val ordered = plays.sortedBy { it.startedAt }
                    .mapNotNull { play -> keyById[play.songId]?.let { it to play.startedAt } }
                    .distinctBy { it.first }
                for (i in ordered.indices) {
                    for (j in i + 1 until minOf(i + WINDOW, ordered.size)) {
                        val weight = MixRewards.fade(now, ordered[j].second, FADE_DAYS * 86_400_000.0) / (j - i)
                        add(ordered[i].first, ordered[j].first, weight)
                        add(ordered[j].first, ordered[i].first, weight)
                    }
                }
            }
    }

    private fun add(from: String, to: String, weight: Double) {
        if (from == to) return
        edges.getOrPut(from) { HashMap() }.merge(to, weight, Double::plus)
    }

    val isEmpty: Boolean get() = edges.isEmpty()

    /** 0…1: how strongly [key] was played through alongside any of [seedKeys]. */
    fun affinity(seedKeys: List<String>, key: String): Double {
        val strongest = seedKeys.maxOfOrNull { edges[it]?.get(key) ?: 0.0 } ?: 0.0
        return strongest / (strongest + 1.0)
    }

    private companion object {
        const val WINDOW = 5
        const val FADE_DAYS = 60.0
    }
}
