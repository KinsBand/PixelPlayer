package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.model.Song
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MixSequencePlannerTest {
    private fun song(id: String, artist: String = id) = Song.emptySong().copy(id = id, title = id, artist = artist)
    private val now = 100L * 86400000
    private fun attempt(songId: String, end: String = "NATURAL", age: Long = 0, voluntary: Boolean = false) =
        MixAttempt(songId, songId, "normal", now - age, 180000, 180000, 0, 200000, voluntary, end, 0)

    @Test fun `recent repeated exposure reduces rank`() {
        val songs = listOf(song("a"), song("b"))
        val result = MixSequencePlanner.plan(songs, emptyList(), emptySet(), songs.map(::mixIdentity).toSet(),
            listOf(attempt("a")), "normal", 0.0, now)
        assertEquals("b", result.first().song.id)
    }
    @Test fun `errors and incomplete checkpoints do not become positive preference`() {
        val song = song("a")
        for (end in listOf("ERROR", "UNKNOWN", "INTERRUPTION", "DISLIKE")) {
            val result = MixSequencePlanner.plan(listOf(song), emptyList(), emptySet(), emptySet(),
                listOf(attempt("a", end)), "normal", 0.0, now)
            assertEquals(0.0, result.single().components["enjoyment"])
        }
    }
    @Test fun `planner spaces artists and is deterministic`() {
        val songs = listOf(song("a", "same"), song("b", "same"), song("c", "other"))
        fun plan() = MixSequencePlanner.plan(songs, emptyList(), emptySet(), songs.map(::mixIdentity).toSet(), emptyList(), "normal", 0.0, now)
        assertEquals(listOf("a", "c", "b"), plan().map { it.song.id })
        assertEquals(plan(), plan())
    }
    @Test fun `balanced pool produces both familiar and discovery choices`() {
        val songs = (1..12).map { song(it.toString()) }
        val result = MixSequencePlanner.plan(songs, emptyList(), emptySet(), songs.take(8).map(::mixIdentity).toSet(), emptyList(), "smart", 0.35, now)
        assertEquals(4, result.count { it.source == "discovery" })
        assertEquals(8, result.count { it.source == "familiar" })
    }
    @Test fun `long duration without coverage does not earn enjoyment`() {
        val song = song("a")
        val result = MixSequencePlanner.plan(listOf(song), emptyList(), emptySet(), emptySet(),
            listOf(attempt("a").copy(activeMs = 900000, uniqueMs = 1000)), "normal", 0.0, now)
        assertEquals(0.0, result.single().components["enjoyment"])
    }
}
