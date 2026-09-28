package com.theveloper.pixelplay.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MicroSkipPolicyTest {
    private fun attempt(id: String, artist: String = "Artist", genre: String? = "indie rock") =
        MixAttempt(id, id, "normal", 100_000, 10_000, 10_000, 0, 180_000, false, "SKIP", 0,
            sessionId = "session", artist = artist, genre = genre, startPositionMs = 0, endPositionMs = 10_000)

    @Test fun `two consecutive micro skips trigger artist and subgenre cooldowns`() {
        val cooldowns = MicroSkipPolicy.triggered(attempt("a"), attempt("b"))
        assertEquals(setOf("artist:artist", "genre:indie_rock"), cooldowns.map { it.vector }.toSet())
        assertTrue(cooldowns.all { it.expiresAt == 110_000 + MicroSkipPolicy.COOLDOWN_MS })
    }
    @Test fun `artist and genre matches work independently`() {
        assertEquals(listOf("genre:indie_rock"), MicroSkipPolicy.triggered(attempt("a"), attempt("b", "Other")).map { it.vector })
        assertEquals(listOf("artist:artist"), MicroSkipPolicy.triggered(attempt("a"), attempt("b", genre = "Rock")).map { it.vector })
    }
    @Test fun `unknown broad genres do not create shared cooldown`() {
        assertTrue(MicroSkipPolicy.vectors("<unknown>", "Rock").isEmpty())
    }
    @Test fun `fifteen seconds is excluded and errors interruptions seeks are not skips`() {
        assertFalse(MicroSkipPolicy.qualifies(attempt("a").copy(activeMs = 15_000)))
        assertFalse(MicroSkipPolicy.qualifies(attempt("a").copy(endPositionMs = 15_000)))
        assertFalse(MicroSkipPolicy.qualifies(attempt("a").copy(startPositionMs = 30_000)))
        assertFalse(MicroSkipPolicy.qualifies(attempt("a").copy(seeks = 1)))
        for (reason in listOf("ERROR", "INTERRUPTION", "NATURAL", "UNKNOWN")) {
            assertFalse(MicroSkipPolicy.qualifies(attempt("a").copy(endReason = reason)))
        }
    }
    @Test fun `different sessions duplicate outcomes and intervening listen break the pair`() {
        assertTrue(MicroSkipPolicy.triggered(attempt("a"), attempt("a")).isEmpty())
        assertTrue(MicroSkipPolicy.triggered(attempt("a"), attempt("b").copy(sessionId = "new")).isEmpty())
        assertTrue(MicroSkipPolicy.triggered(attempt("a").copy(endReason = "NATURAL"), attempt("b")).isEmpty())
    }
    @Test fun `penalty persists across sessions and expires exactly after seven days`() {
        val cooldowns = MicroSkipPolicy.triggered(attempt("a"), attempt("b"))
        assertEquals(8.0, MicroSkipPolicy.penalty("Artist", null, cooldowns, "session", 120_000))
        assertEquals(3.0, MicroSkipPolicy.penalty("Artist", null, cooldowns, "new", 120_000))
        assertEquals(0.0, MicroSkipPolicy.penalty("Artist", null, cooldowns, "session", cooldowns.first().expiresAt))
    }
    @Test fun `planner includes cooldown in ranking without excluding the artist`() {
        val songs = listOf("a", "b").map { com.theveloper.pixelplay.data.model.Song.emptySong().copy(id = it, artist = it) }
        val decisions = MixSequencePlanner.plan(songs, emptyList(), emptySet(), emptySet(), emptyList(), "normal", 0.0, 120_000,
            sessionId = "session", microSkipCooldowns = listOf(MicroSkipCooldown("artist:a", "session", 999_999)))
        assertEquals("b", decisions.first().song.id)
        assertEquals(-8.0, decisions.first { it.song.id == "a" }.components["microSkipCooldown"])
    }
}
