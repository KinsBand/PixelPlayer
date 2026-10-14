package com.theveloper.pixelplay.data.stats

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class StatsInsightsTest {

    @Test
    fun `rank change reports new, same, up and down`() {
        assertThat(StatsInsights.rankChange(currentRank = 3, previousRank = null)).isEqualTo(StatsInsights.RankChange.New)
        assertThat(StatsInsights.rankChange(currentRank = 2, previousRank = 2)).isEqualTo(StatsInsights.RankChange.Same)
        assertThat(StatsInsights.rankChange(currentRank = 1, previousRank = 3)).isEqualTo(StatsInsights.RankChange.Up(2))
        assertThat(StatsInsights.rankChange(currentRank = 4, previousRank = 1)).isEqualTo(StatsInsights.RankChange.Down(3))
    }

    @Test
    fun `percent change is null without a previous value instead of dividing by zero`() {
        assertThat(StatsInsights.percentChange(current = 100L, previous = 0L)).isNull()
        assertThat(StatsInsights.percentChange(current = 2292L, previous = 1940L)).isEqualTo(18)
        assertThat(StatsInsights.percentChange(current = 50L, previous = 100L)).isEqualTo(-50)
    }

    @Test
    fun `next milestone is strictly above the value and progress starts at the previous rung`() {
        assertThat(StatsInsights.nextMilestone(38L, StatsInsights.HOUR_MILESTONES)).isEqualTo(50L)
        assertThat(StatsInsights.nextMilestone(50L, StatsInsights.HOUR_MILESTONES)).isEqualTo(100L)
        assertThat(StatsInsights.nextMilestone(10_000L, StatsInsights.HOUR_MILESTONES)).isNull()
        assertThat(StatsInsights.milestoneProgress(38L, 50L, StatsInsights.HOUR_MILESTONES)).isWithin(0.001f).of(0.52f)
        assertThat(StatsInsights.milestoneProgress(0L, 1L, StatsInsights.HOUR_MILESTONES)).isEqualTo(0f)
    }

    @Test
    fun `listener profile needs enough plays`() {
        val profile = StatsInsights.listenerProfile(
            hourlyMs = List(24) { 1_000L },
            totalDurationMs = 24_000L,
            topArtistsDurationMs = listOf(10_000L),
            totalPlays = StatsInsights.MIN_PLAYS_FOR_PROFILE - 1,
            uniqueSongs = 5,
            uniqueArtists = 2,
            newSongsDurationMs = null
        )
        assertThat(profile).isNull()
    }

    @Test
    fun `late listening with few artists reads as night owl loyalist`() {
        val hourly = List(24) { hour -> if (hour in 20..23) 3_600_000L else 60_000L }
        val profile = StatsInsights.listenerProfile(
            hourlyMs = hourly,
            totalDurationMs = hourly.sum(),
            topArtistsDurationMs = listOf(hourly.sum() / 2, hourly.sum() / 4),
            totalPlays = 200,
            uniqueSongs = 120,
            uniqueArtists = 8,
            newSongsDurationMs = 0L
        )!!
        assertThat(profile.timeOfDay).isEqualTo(StatsInsights.TimeOfDay.NIGHT_OWL)
        assertThat(profile.style).isEqualTo(StatsInsights.ListeningStyle.LOYALIST)
        assertThat(profile.night).isGreaterThan(0.62f)
    }

    @Test
    fun `peak hour is null for an empty clock`() {
        assertThat(StatsInsights.peakHour(List(24) { 0L })).isNull()
        assertThat(StatsInsights.peakHour(List(24) { if (it == 22) 5L else 1L })).isEqualTo(22)
    }
}
