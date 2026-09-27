package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.TrackVideo
import com.theveloper.pixelplay.data.model.VideoType
import com.theveloper.pixelplay.data.repository.VideoRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SongVideoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<VideoRepository>(relaxed = true)
    private val first = TrackVideo("first", "First", "Artist", "")
    private val second = TrackVideo("second", "Second", "Artist", "")
    private fun song(identifier: String) = mockk<Song> {
        every { id } returns identifier
        every { title } returns identifier
        every { displayArtist } returns "Artist"
    }
    @BeforeEach fun setup() = Dispatchers.setMain(dispatcher)
    @AfterEach fun cleanup() = Dispatchers.resetMain()

    @Test
    fun `late result from previous song cannot replace current song`() = runTest(dispatcher) {
        coEvery { repository.searchVideosForTrack("Artist", "old", any(), any()) } coAnswers {
            withContext(NonCancellable) { delay(1000); listOf(first) }
        }
        coEvery { repository.searchVideosForTrack("Artist", "new", any(), any()) } returns listOf(second)
        val model = SongVideoViewModel(repository)
        model.search(song("old"))
        runCurrent()
        model.search(song("new"))
        advanceUntilIdle()
        assertEquals("new", model.state.value.songId)
        assertEquals(second, model.state.value.selected)
    }

    @Test
    fun `embed failures try each candidate once then report an error`() = runTest(dispatcher) {
        coEvery { repository.searchVideosForTrack(any(), any(), any(), any()) } returns listOf(first, second)
        val model = SongVideoViewModel(repository)
        model.search(song("track"), VideoType.LIVE_PERFORMANCE)
        advanceUntilIdle()
        model.playbackFailed("first", "Blocked", true)
        assertEquals(second, model.state.value.selected)
        model.playbackFailed("first", "Stale callback", true)
        assertEquals(second, model.state.value.selected)
        model.playbackFailed("second", "Blocked", true)
        assertNull(model.state.value.selected)
        assertEquals("Blocked", model.state.value.error)
    }

    @Test
    fun `manual choice is remembered separately for selected preset`() = runTest(dispatcher) {
        coEvery { repository.searchVideosForTrack(any(), any(), any(), any()) } returns listOf(first, second)
        val model = SongVideoViewModel(repository)
        model.search(song("track"), VideoType.ACOUSTIC)
        advanceUntilIdle()
        model.select(second)
        verify { repository.rememberChoice("Artist", "track", VideoType.ACOUSTIC, "second") }
        assertEquals(second, model.state.value.selected)
    }

    @Test
    fun `search failure is recoverable through retry`() = runTest(dispatcher) {
        coEvery { repository.searchVideosForTrack(any(), any(), any(), false) } throws java.io.IOException()
        coEvery { repository.searchVideosForTrack(any(), any(), any(), true) } returns listOf(first)
        val model = SongVideoViewModel(repository)
        model.search(song("track"))
        advanceUntilIdle()
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.loading)
        model.search(song("track"), refresh = true)
        advanceUntilIdle()
        assertEquals(first, model.state.value.selected)
        assertNull(model.state.value.error)
    }
}
