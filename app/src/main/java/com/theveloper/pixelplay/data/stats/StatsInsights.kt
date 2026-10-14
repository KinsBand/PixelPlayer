package com.theveloper.pixelplay.data.stats

import kotlin.math.roundToInt

/**
 * Pure helpers behind the Stats screen insights and share cards. No Android types, so they are
 * unit-testable (see StatsInsightsTest).
 */
object StatsInsights {

    /** How an item's rank changed against the previous period. */
    sealed interface RankChange {
        data object New : RankChange
        data object Same : RankChange
        data class Up(val places: Int) : RankChange
        data class Down(val places: Int) : RankChange
    }

    /** [previousRank] null means the item wasn't ranked last period. */
    fun rankChange(currentRank: Int, previousRank: Int?): RankChange = when {
        previousRank == null -> RankChange.New
        previousRank == currentRank -> RankChange.Same
        previousRank > currentRank -> RankChange.Up(previousRank - currentRank)
        else -> RankChange.Down(currentRank - previousRank)
    }

    /** Whole-percent change, or null when there is nothing to compare with. */
    fun percentChange(current: Long, previous: Long): Int? {
        if (previous <= 0L) return null
        return (((current - previous).toDouble() / previous.toDouble()) * 100.0).roundToInt()
    }

    val HOUR_MILESTONES: List<Long> = listOf(1, 5, 10, 25, 50, 100, 250, 500, 1_000, 2_500, 5_000)
    val PLAY_MILESTONES: List<Long> = listOf(10, 25, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 25_000)
    val DAY_MILESTONES: List<Long> = listOf(7, 14, 30, 50, 100, 150, 200, 250, 300, 365)

    /** Next milestone strictly above [value], or null once past the ladder. */
    fun nextMilestone(value: Long, ladder: List<Long>): Long? = ladder.firstOrNull { it > value }

    /** Fraction (0..1) of the way from the previous milestone (or 0) to [target]. */
    fun milestoneProgress(value: Long, target: Long, ladder: List<Long>): Float {
        val floor = ladder.lastOrNull { it < target } ?: 0L
        val span = (target - floor).coerceAtLeast(1L)
        return ((value - floor).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    enum class TimeOfDay { EARLY_BIRD, DAYTIME, NIGHT_OWL }
    enum class ListeningStyle { LOYALIST, EXPLORER, REPEATER, MIXER }

    /**
     * Each trait is 0..1 along its axis:
     * loyalty (explorer -> loyalist), night (early bird -> night owl),
     * variety (few artists -> many artists), repeat (always new -> on repeat).
     */
    data class ListenerProfile(
        val timeOfDay: TimeOfDay,
        val style: ListeningStyle,
        val loyalty: Float,
        val night: Float,
        val variety: Float,
        val repeat: Float
    )

    const val MIN_PLAYS_FOR_PROFILE = 20

    fun listenerProfile(
        hourlyMs: List<Long>,
        totalDurationMs: Long,
        topArtistsDurationMs: List<Long>,
        totalPlays: Int,
        uniqueSongs: Int,
        uniqueArtists: Int,
        newSongsDurationMs: Long?
    ): ListenerProfile? {
        if (totalPlays < MIN_PLAYS_FOR_PROFILE || totalDurationMs <= 0L || hourlyMs.size != 24) return null

        val topShare = topArtistsDurationMs.take(5).sum().toFloat() / totalDurationMs.toFloat()
        val loyalty = scale(topShare, low = 0.15f, high = 0.70f)

        val evening = (18..23).sumOf { hourlyMs[it] } + (0..2).sumOf { hourlyMs[it] }
        val morning = (5..11).sumOf { hourlyMs[it] }
        val night = if (evening + morning > 0L) evening.toFloat() / (evening + morning).toFloat() else 0.5f

        val variety = scale(uniqueArtists.toFloat() / totalPlays.toFloat(), low = 0.02f, high = 0.30f)
        val repeat = (1f - uniqueSongs.toFloat() / totalPlays.toFloat()).coerceIn(0f, 1f)
        val newShare = newSongsDurationMs?.let { it.toFloat() / totalDurationMs.toFloat() } ?: 0f

        val timeOfDay = when {
            night >= 0.62f -> TimeOfDay.NIGHT_OWL
            night <= 0.38f -> TimeOfDay.EARLY_BIRD
            else -> TimeOfDay.DAYTIME
        }
        val style = when {
            repeat >= 0.75f -> ListeningStyle.REPEATER
            loyalty >= 0.6f -> ListeningStyle.LOYALIST
            variety >= 0.6f || newShare >= 0.3f -> ListeningStyle.EXPLORER
            else -> ListeningStyle.MIXER
        }
        return ListenerProfile(timeOfDay, style, loyalty, night, variety, repeat)
    }

    /** Hour (0..23) with the most listening, or null when the clock is empty. */
    fun peakHour(hourlyMs: List<Long>): Int? {
        if (hourlyMs.none { it > 0L }) return null
        return hourlyMs.indices.maxByOrNull { hourlyMs[it] }
    }

    /** Share (0..1) of listening between 18:00 and 05:59. */
    fun eveningShare(hourlyMs: List<Long>): Float {
        val total = hourlyMs.sum()
        if (total <= 0L || hourlyMs.size != 24) return 0f
        val evening = (18..23).sumOf { hourlyMs[it] } + (0..5).sumOf { hourlyMs[it] }
        return evening.toFloat() / total.toFloat()
    }

    private fun scale(value: Float, low: Float, high: Float): Float =
        ((value - low) / (high - low)).coerceIn(0f, 1f)
}
