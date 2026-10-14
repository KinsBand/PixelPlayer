package com.theveloper.pixelplay.data

/**
 * Which upcoming mix songs a gentle swap replaces after a tune, a pick or a steer. Pure, so it
 * is unit-testable.
 *
 * A song goes when it no longer fits: its fit is in the bottom [PERCENTILE] of the upcoming
 * songs **and** it fell at least [MIN_DROP] since it was last scored (a song never scored
 * before must sit [MIN_DROP] below that line). At most [MAX_SHARE] of the songs go at once (at
 * least one); songs excluded since they were queued always go.
 */
internal object MixNudgePolicy {
    const val PERCENTILE = 0.3
    const val MIN_DROP = 1.5
    const val MAX_SHARE = 0.25

    /** [drop]: ids to swap now; [moreWaiting]: more clashed than one swap may change. */
    data class Result(val drop: Set<String>, val moreWaiting: Boolean)

    /**
     * [ids] = the upcoming mix songs that may move, in queue order; [scores] = fit now by id
     * (a missing id was excluded meanwhile); [before] = fit when last scored.
     */
    fun select(ids: List<String>, scores: Map<String, Double>, before: Map<String, Double>): Result {
        if (ids.isEmpty()) return Result(emptySet(), false)
        val excluded = ids.filterTo(LinkedHashSet()) { it !in scores }
        val ordered = ids.mapNotNull(scores::get).sorted()
        val line = if (ordered.isEmpty()) 0.0 else ordered[((ordered.size - 1) * PERCENTILE).toInt()]
        val clashing = ids.filter { it in scores }.filter { id ->
            val fit = scores.getValue(id)
            fit <= line && (before[id]?.let { it - fit >= MIN_DROP } ?: (fit <= line - MIN_DROP))
        }.sortedBy { scores.getValue(it) }
        val cap = maxOf(1, kotlin.math.ceil(ids.size * MAX_SHARE).toInt())
        return Result(excluded + clashing.take(cap), clashing.size > cap)
    }
}
