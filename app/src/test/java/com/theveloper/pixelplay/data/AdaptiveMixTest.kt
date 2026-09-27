package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.accounts.*
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.youtube.YouTubeRepository
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AdaptiveMixTest {
    private fun song(id: String, title: String = id) = Song(id, title, "Artist", 1,
        album = "Album", albumId = 1, path = "", contentUriString = "youtube://$id", albumArtUriString = null, duration = 200000)
    private val gatherer = mockk<com.theveloper.pixelplay.data.metadata.SongMetadataGatherer>(relaxed = true) {
        every { applyCached(any()) } answers { firstArg() }
    }
    private val music = mockk<MusicRepository> {
        coEvery { getAllSongsOnce() } returns listOf(song("local"))
        every { getFavoriteSongIdsFlow() } returns flowOf(emptySet())
    }
    private val snapshot = MutableStateFlow(ConnectedSnapshot(spotifyLikes = listOf(song("liked"))))
    private val connected = mockk<ConnectedLibraryRepository> { every { snapshot } returns this@AdaptiveMixTest.snapshot }
    private val youtube = mockk<YouTubeRepository>()
    private val playlists = mockk<com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository> {
        every { userPlaylistsFlow } returns flowOf(emptyList())
    }
    private val cloud = mockk<com.theveloper.pixelplay.data.database.CloudSongDao> {
        every { getDownloadedSongIds() } returns flowOf(emptyList())
    }
    private val feedback = mockk<MixFeedback>(relaxed = true)
    private val learning = mockk<MixLearning> { coEvery { recent() } returns emptyList() }
    private val mix = AdaptiveMix(music, youtube, connected, playlists, cloud, feedback, learning, gatherer)

    @Test fun `disliked tracks are excluded even without recent queue history`() = runTest {
        every { feedback.isDisliked(match { it.id == "liked" }, any()) } returns true
        assertEquals(listOf("local"), mix.next(MixFlavor.NORMAL, emptyList(), emptySet()).map { it.id })
    }

    @Test fun `explicit track fatigue lowers recommendation rank`() = runTest {
        every { feedback.penalty(match { it.id == "local" }) } returns 8
        assertEquals(listOf("liked", "local"), mix.next(MixFlavor.NORMAL, emptyList(), emptySet()).map { it.id })
    }

    @Test fun `related endpoint failure still uses search fallback`() = runTest {
        coEvery { youtube.relatedSongs(any()) } throws java.io.IOException("offline")
        coEvery { youtube.searchSongs("Artist") } returns listOf(song("new"))
        assertEquals(setOf("local", "liked", "new"), mix.next(MixFlavor.SMART, listOf(song("seed").copy(youtubeId = "abcdefghijk")), emptySet()).map { it.id }.toSet())
    }

    @Test fun `cancelled discovery does not fall through to other endpoints`() = runTest {
        coEvery { youtube.relatedSongs(any()) } throws kotlinx.coroutines.CancellationException()
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { mix.next(MixFlavor.SMART, listOf(song("seed").copy(youtubeId = "abcdefghijk")), emptySet()) }
        }
    }

    @Test fun `normal includes connected likes and never searches online`() = runTest {
        assertEquals(setOf("local", "liked"), mix.next(MixFlavor.NORMAL, emptyList(), emptySet()).map { it.id }.toSet())
        coVerify(exactly = 0) { youtube.searchSongs(any()) }
    }
    @Test fun `smart combines library and discovery while excluding seen and duplicates`() = runTest {
        coEvery { youtube.searchSongs("Artist") } returns listOf(song("local"), song("seen"), song("new"), song("duplicate", "new"))
        assertEquals(setOf("local", "liked", "new"), mix.next(MixFlavor.SMART, listOf(song("seed")), setOf(mixIdentity(song("seen")))).map { it.id }.toSet())
    }
    @Test fun `next refill sees library updates and excludes unavailable tracks`() = runTest {
        snapshot.value = ConnectedSnapshot(youtubeLikes = listOf(song("added"), song("gone").copy(contentUriString = "")))
        assertEquals(listOf("added"), mix.next(MixFlavor.NORMAL, emptyList(), setOf(mixIdentity(song("local")))).map { it.id })
    }
}

