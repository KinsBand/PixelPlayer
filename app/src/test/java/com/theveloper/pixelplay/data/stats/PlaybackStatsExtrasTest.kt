package com.theveloper.pixelplay.data.stats

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.model.Song
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import org.junit.jupiter.api.Test

/** Covers the numbers added for the Stats redesign (previous period, streak, clock, discoveries, custom range). */
class PlaybackStatsExtrasTest {

    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val now = millis(LocalDateTime.of(2026, 10, 11, 12, 0))

    @Test
    fun `previous period covers the same days of last month only`() {
        val events = listOf(
            play("a", LocalDateTime.of(2026, 10, 3, 10, 0), minutes = 10),
            play("a", LocalDateTime.of(2026, 9, 3, 10, 0), minutes = 5),
            // After Sep 11 12:00, so outside the "same days last month" window.
            play("a", LocalDateTime.of(2026, 9, 20, 10, 0), minutes = 30)
        )
        val summary = createRepository().buildSummaryFromEvents(
            range = StatsTimeRange.MONTH,
            songs = listOf(song("a")),
            nowMillis = now,
            allEvents = events
        )
        assertThat(summary.totalDurationMs).isEqualTo(TimeUnit.MINUTES.toMillis(10))
        assertThat(summary.previous).isNotNull()
        assertThat(summary.previous!!.totalDurationMs).isEqualTo(TimeUnit.MINUTES.toMillis(5))
        assertThat(summary.previous!!.timeline.size).isEqualTo(summary.timeline.size)
    }

    @Test
    fun `all time has no previous period`() {
        val summary = createRepository().buildSummaryFromEvents(
            range = StatsTimeRange.ALL,
            songs = listOf(song("a")),
            nowMillis = now,
            allEvents = listOf(play("a", LocalDateTime.of(2026, 10, 3, 10, 0), minutes = 10))
        )
        assertThat(summary.previous).isNull()
        assertThat(summary.discoveries).isNull()
    }

    @Test
    fun `current streak counts back from today and stops at a gap`() {
        val events = listOf(
            play("a", LocalDateTime.of(2026, 10, 11, 9, 0), minutes = 3),
            play("a", LocalDateTime.of(2026, 10, 10, 9, 0), minutes = 3),
            play("a", LocalDateTime.of(2026, 10, 9, 9, 0), minutes = 3),
            play("a", LocalDateTime.of(2026, 10, 7, 9, 0), minutes = 3)
        )
        val summary = createRepository().buildSummaryFromEvents(
            range = StatsTimeRange.WEEK,
            songs = listOf(song("a")),
            nowMillis = now,
            allEvents = events
        )
        assertThat(summary.currentStreakDays).isEqualTo(3)
    }

    @Test
    fun `listening clock puts time in the right hour`() {
        val summary = createRepository().buildSummaryFromEvents(
            range = StatsTimeRange.MONTH,
            songs = listOf(song("a")),
            nowMillis = now,
            allEvents = listOf(play("a", LocalDateTime.of(2026, 10, 3, 22, 10), minutes = 20))
        )
        assertThat(summary.hourlyDurationsMs).hasSize(24)
        assertThat(summary.hourlyDurationsMs[22]).isEqualTo(TimeUnit.MINUTES.toMillis(20))
        assertThat(summary.hourlyDurationsMs.sum()).isEqualTo(TimeUnit.MINUTES.toMillis(20))
    }

    @Test
    fun `discoveries only count songs and artists first played in the range`() {
        val events = listOf(
            play("old", LocalDateTime.of(2026, 8, 1, 10, 0), minutes = 4),
            play("old", LocalDateTime.of(2026, 10, 2, 10, 0), minutes = 4),
            play("fresh", LocalDateTime.of(2026, 10, 5, 10, 0), minutes = 4)
        )
        val summary = createRepository().buildSummaryFromEvents(
            range = StatsTimeRange.MONTH,
            songs = listOf(song("old", artist = "Old Band"), song("fresh", artist = "Fresh Face")),
            nowMillis = now,
            allEvents = events
        )
        val discoveries = summary.discoveries!!
        assertThat(discoveries.newSongs).isEqualTo(1)
        assertThat(discoveries.newArtists).isEqualTo(1)
        assertThat(discoveries.artists.single().artist).isEqualTo("Fresh Face")
        assertThat(discoveries.newSongIds).containsExactly("fresh")
    }

    @Test
    fun `custom range clips to its days and buckets by day`() {
        val start = millis(LocalDate.of(2026, 9, 1).atStartOfDay())
        val end = millis(LocalDate.of(2026, 9, 7).plusDays(1).atStartOfDay()) - 1L
        val events = listOf(
            play("a", LocalDateTime.of(2026, 9, 2, 10, 0), minutes = 10),
            play("a", LocalDateTime.of(2026, 9, 9, 10, 0), minutes = 10)
        )
        val summary = createRepository().buildSummaryFromEvents(
            range = StatsTimeRange.ALL,
            songs = listOf(song("a")),
            nowMillis = now,
            allEvents = events,
            customBounds = start to end
        )
        assertThat(summary.isCustomRange).isTrue()
        assertThat(summary.totalDurationMs).isEqualTo(TimeUnit.MINUTES.toMillis(10))
        assertThat(summary.timeline).hasSize(7)
        assertThat(summary.previous).isNotNull()
    }

    private fun millis(dateTime: LocalDateTime): Long = dateTime.atZone(zoneId).toInstant().toEpochMilli()

    private fun play(songId: String, start: LocalDateTime, minutes: Long): PlaybackStatsRepository.PlaybackEvent {
        val startMs = millis(start)
        val duration = TimeUnit.MINUTES.toMillis(minutes)
        return PlaybackStatsRepository.PlaybackEvent(
            songId = songId,
            timestamp = startMs + duration,
            durationMs = duration,
            startTimestamp = startMs,
            endTimestamp = startMs + duration
        )
    }

    private fun createRepository(): PlaybackStatsRepository {
        val dir = createTempDirectory("playback-stats-extras-${Instant.now().toEpochMilli()}-").toFile()
        val context = mockk<android.content.Context>(relaxed = true)
        every { context.filesDir } returns dir
        return PlaybackStatsRepository(context)
    }

    private fun song(songId: String, artist: String = "Artist"): Song = Song(
        id = songId,
        title = "Song $songId",
        artist = artist,
        artistId = 1L,
        artists = emptyList(),
        album = "Album",
        albumId = 1L,
        path = "/music/$songId.mp3",
        contentUriString = "content://media/external/audio/media/$songId",
        albumArtUriString = null,
        duration = 5 * 60 * 1000L,
        genre = null,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
