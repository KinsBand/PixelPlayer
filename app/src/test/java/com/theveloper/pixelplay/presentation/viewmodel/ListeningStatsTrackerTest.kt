package com.theveloper.pixelplay.presentation.viewmodel

import android.os.SystemClock
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ListeningStatsTrackerTest {

    private val learning: com.theveloper.pixelplay.data.MixLearning = mockk(relaxed = true)
    private val dailyMixManager: DailyMixManager = mockk(relaxed = true)
    private val playbackStatsRepository: PlaybackStatsRepository = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkStatic(SystemClock::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun `unobserved wall time is not counted as playback`() {
        val tracker = ListeningStatsTracker(
            dailyMixManager = dailyMixManager,
            playbackStatsRepository = playbackStatsRepository,
            mixLearning = learning, scrobbler = mockk(relaxed = true)
        )
        val song = song(
            songId = "looped-song",
            durationMs = TimeUnit.MINUTES.toMillis(3)
        )
        val listenedMs = TimeUnit.MINUTES.toMillis(12)

        every { SystemClock.elapsedRealtime() } returnsMany listOf(
            1_000L,
            1_000L + listenedMs
        )

        tracker.onSongChanged(
            song = song,
            positionMs = 0L,
            durationMs = song.duration,
            isPlaying = true
        )
        tracker.finalizeCurrentSession(forceSynchronousPersistence = true)

        coVerify(exactly = 0) { dailyMixManager.recordPlay(any(), any(), any()) }
        coVerify(exactly = 0) { playbackStatsRepository.recordPlayback(any(), any(), any()) }
    }

    @Test
    fun `onProgress accumulates incremental listening time`() {
        val tracker = ListeningStatsTracker(
            dailyMixManager = dailyMixManager,
            playbackStatsRepository = playbackStatsRepository,
            mixLearning = learning, scrobbler = mockk(relaxed = true)
        )
        val song = song(songId = "song-1")
        val firstChunkMs = 7_000L
        val secondChunkMs = 8_000L
        val expectedDurationMs = firstChunkMs + secondChunkMs

        every { SystemClock.elapsedRealtime() } returnsMany listOf(
            5_000L,
            5_000L + firstChunkMs,
            5_000L + firstChunkMs + secondChunkMs
        )

        tracker.onSongChanged(
            song = song,
            positionMs = 0L,
            durationMs = song.duration,
            isPlaying = true
        )
        tracker.onProgress(positionMs = firstChunkMs, isPlaying = true)
        tracker.onProgress(positionMs = expectedDurationMs, isPlaying = false)
        tracker.finalizeCurrentSession(forceSynchronousPersistence = true)

        coVerify(timeout = 2_000) {
            playbackStatsRepository.recordPlayback(song.id, expectedDurationMs, any())
        }
        assertThat(expectedDurationMs).isGreaterThan(TimeUnit.SECONDS.toMillis(5))
    }

    @Test
    fun `error remains error when a following transition reports a skip`() {
        every { SystemClock.elapsedRealtime() } returnsMany listOf(0, 10_000, 10_000)
        val tracker = ListeningStatsTracker(dailyMixManager, playbackStatsRepository, learning, mockk(relaxed = true))
        tracker.onSongChanged(song("error"), 0, 200_000, true)
        tracker.onProgress(10_000, false)
        tracker.markEndReason(com.theveloper.pixelplay.data.MixEndReason.ERROR)
        tracker.markEndReason(com.theveloper.pixelplay.data.MixEndReason.SKIP)
        tracker.finalizeCurrentSession()
        io.mockk.verify { learning.record(match { it.endReason == "ERROR" && it.uniqueMs == 10_000L }) }
        io.mockk.verify(exactly = 0) { learning.record(match { it.endReason == "SKIP" }) }
    }

    @Test
    fun `repeating the same song creates a distinct playback attempt`() {
        every { SystemClock.elapsedRealtime() } returns 0L
        val records = mutableListOf<com.theveloper.pixelplay.data.MixAttempt>()
        every { learning.record(capture(records)) } returns Unit
        val tracker = ListeningStatsTracker(dailyMixManager, playbackStatsRepository, learning, mockk(relaxed = true))
        tracker.onSongChanged(song("repeat"), 0, 200_000, true)
        tracker.markEndReason(com.theveloper.pixelplay.data.MixEndReason.NATURAL)
        tracker.onSongChanged(song("repeat"), 0, 200_000, true)
        tracker.finalizeCurrentSession()
        assertThat(records.map { it.id }.distinct()).hasSize(2)
        assertThat(records.count { it.endReason == "NATURAL" }).isEqualTo(1)
    }

    private fun song(songId: String, durationMs: Long = 5 * 60 * 1000L): Song = Song(
        id = songId,
        title = "Song $songId",
        artist = "Artist",
        artistId = 1L,
        album = "Album",
        albumId = 1L,
        path = "/music/$songId.mp3",
        contentUriString = "content://media/external/audio/media/$songId",
        albumArtUriString = null,
        duration = durationMs,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
