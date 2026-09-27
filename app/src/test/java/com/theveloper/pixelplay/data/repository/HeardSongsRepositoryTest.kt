package com.theveloper.pixelplay.data.repository

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeardSongsRepositoryTest {

    private lateinit var repository: HeardSongsRepository

    private val testSong1 = Song.emptySong().copy(
        id = "song-1",
        title = "Blinding Lights",
        artist = "The Weeknd"
    )

    private val testSong2 = Song.emptySong().copy(
        id = "song-2",
        title = "Bohemian Rhapsody",
        artist = "Queen"
    )

    @Before
    fun setUp() {
        repository = HeardSongsRepository()
    }

    @Test
    fun `dismiss suppresses repeated mentions across providers`() {
        repository.addOrUpvote(testSong1)
        repository.dismiss(repository.heardSongs.value.single().id)
        assertThat(repository.addOrUpvote(testSong1.copy(id = "other-provider"))).isFalse()
        assertThat(repository.heardSongs.value).isEmpty()
    }

    @Test
    fun `clear all suppresses immediate reappearance`() {
        repository.addOrUpvote(testSong1)
        repository.clearAll()
        assertThat(repository.addOrUpvote(testSong1)).isFalse()
    }

    @Test
    fun `suggestions remain bounded during long listening sessions`() {
        repeat(70) { repository.addOrUpvote(testSong1.copy(id = "song-$it", title = "Title $it")) }
        assertThat(repository.heardSongs.value).hasSize(50)
    }

    @Test
    fun `addOrUpvote adds new song as first item`() = runTest {
        val added = repository.addOrUpvote(testSong1, isOnlineMatch = false, rawPhrase = "play blinding lights")

        assertThat(added).isTrue()
        val list = repository.heardSongs.value
        assertThat(list).hasSize(1)
        assertThat(list[0].song.title).isEqualTo("Blinding Lights")
        assertThat(list[0].mentionCount).isEqualTo(1)
    }

    @Test
    fun `addOrUpvote bumps and increments mention counter when repeated`() = runTest {
        repository.addOrUpvote(testSong1)
        repository.addOrUpvote(testSong2)

        // Song 2 is at index 0, Song 1 is at index 1
        assertThat(repository.heardSongs.value[0].song.id).isEqualTo("song-2")

        // Mention song 1 again
        val added = repository.addOrUpvote(testSong1)
        assertThat(added).isTrue()

        val list = repository.heardSongs.value
        assertThat(list).hasSize(2)
        // Song 1 should now be at index 0 and have mentionCount 2
        assertThat(list[0].song.id).isEqualTo("song-1")
        assertThat(list[0].mentionCount).isEqualTo(2)
    }

    @Test
    fun `addOrUpvote skips song if it is actively playing`() = runTest {
        val added = repository.addOrUpvote(testSong1, currentPlayingSongId = "song-1")

        assertThat(added).isFalse()
        assertThat(repository.heardSongs.value).isEmpty()
    }

    @Test
    fun `dismiss removes song by id`() = runTest {
        repository.addOrUpvote(testSong1)
        val id = repository.heardSongs.value[0].id

        repository.dismiss(id)
        assertThat(repository.heardSongs.value).isEmpty()
    }

    @Test
    fun `clearAll removes all items`() = runTest {
        repository.addOrUpvote(testSong1)
        repository.addOrUpvote(testSong2)
        assertThat(repository.heardSongs.value).hasSize(2)

        repository.clearAll()
        assertThat(repository.heardSongs.value).isEmpty()
    }
}
