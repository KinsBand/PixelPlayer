package com.theveloper.pixelplay.data

import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ContinuousMixRuntimeTest {
    private val gatherer = mockk<com.theveloper.pixelplay.data.metadata.SongMetadataGatherer>(relaxed = true) {
        every { applyCached(any()) } answers { firstArg() }
        coEvery { gather(any(), any()) } answers { firstArg() }
    }
    private val adaptive = mockk<AdaptiveMix>(relaxed = true)
    private val music = mockk<MusicRepository>(relaxed = true)
    private val runtime = ContinuousMixRuntime(adaptive, music, mockk(relaxed = true), mockk(relaxed = true), gatherer)
    private val queue = mutableListOf<MediaItem>()
    private val player = mockk<Player>(relaxed = true) {
        every { currentMediaItemIndex } returns 0
        every { mediaItemCount } answers { queue.size }
        every { getMediaItemAt(any()) } answers { queue[firstArg()] }
        every { removeMediaItem(any()) } answers { queue.removeAt(firstArg()); Unit }
    }
    private val seed = Song.emptySong().copy(id = "seed", title = "seed", artist = "artist", contentUriString = "content://seed")
    @BeforeEach fun setup() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0
        every { adaptive.revision } returns 0
        every { music.getSongsByIds(any()) } returns flowOf(listOf(seed))
    }
    @AfterEach fun teardown() { unmockkStatic(SystemClock::class) }
    private fun item(id: String, automatic: Boolean): MediaItem {
        val extras = mockk<Bundle> { every { getBoolean(MixQueueMetadata.AUTOMATIC, false) } returns automatic }
        val metadata = MediaMetadata.Builder().setExtras(extras).build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
    }

    @Test fun `refresh preserves a manually queued copy of an automatic song and committed next`() = runTest {
        val current = item("current", false)
        val committed = item("committed", true)
        val manual = item("same-song", false)
        queue += listOf(current, committed, item("same-song", true), manual, item("later", true))
        runtime.attach(backgroundScope, { player }, { false })
        runtime.start(MixFlavor.SMART, listOf(seed))
        runtime.refreshFuture()
        assertEquals(listOf(current, committed, manual), queue)
    }

    @Test fun `feedback arriving during retrieval invalidates entire stale result`() = runTest {
        queue += item("seed", false)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { adaptive.next(any(), any(), any(), any(), any()) } coAnswers {
            started.complete(Unit)
            release.await()
            listOf(seed.copy(id = "new", title = "new"))
        }
        runtime.attach(backgroundScope, { player }, { false })
        runtime.start(MixFlavor.SMART, listOf(seed))
        val refill = async { runtime.refill() }
        started.await()
        every { adaptive.revision } returns 1
        release.complete(Unit)
        refill.await()
        verify(exactly = 0) { player.addMediaItem(any()) }
    }

    @Test fun `service detachment cancels pending recommendation work`() = runTest {
        queue += item("seed", false)
        runtime.attach(backgroundScope, { player }, { false })
        runtime.start(MixFlavor.NORMAL, listOf(seed))
        runtime.detach()
        advanceTimeBy(5000)
        assertNull(runtime.flavor.value)
        coVerify(exactly = 0) { adaptive.next(any(), any(), any(), any(), any()) }
    }

    @Test fun `remote playback does not mutate the inactive local queue`() = runTest {
        queue += item("seed", false)
        runtime.attach(backgroundScope, { player }, { true })
        runtime.start(MixFlavor.SMART, listOf(seed))
        advanceTimeBy(5000)
        coVerify(exactly = 0) { adaptive.next(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { player.addMediaItem(any()) }
    }
}
