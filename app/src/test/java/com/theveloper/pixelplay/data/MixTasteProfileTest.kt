package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.model.Song
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MixTasteProfileTest {
    private val now = 100L * 86_400_000
    private fun song(id: String, genre: String, artist: String = id) = Song.emptySong().copy(id = id, title = id, artist = artist, genre = genre)
    private fun attempt(id: String, reason: String = "NATURAL", mix: String = "focus", time: Long = now) =
        MixAttempt("$id-$time", id, mix, time, 180000, 180000, 0, 200000, true, reason, 0)

    @Test fun `distinct taste clusters both support unfamiliar neighbours`() {
        val folk = song("folk", "folk")
        val metal = song("metal", "metal")
        val profile = MixTasteProfile(listOf(folk, metal), List(6) { attempt("folk") } + List(6) { attempt("metal") }, emptySet(), "focus", now)
        assertTrue(profile.components(song("new-folk", "folk")).getValue("longTermTaste") > 0)
        assertTrue(profile.components(song("new-metal", "metal")).getValue("longTermTaste") > 0)
        assertEquals(0.0, profile.components(song("new-jazz", "jazz")).getValue("longTermTaste"))
    }
    @Test fun `confidence grows with independent exposure evidence`() {
        val seed = song("seed", "folk")
        val once = MixTasteProfile(listOf(seed), listOf(attempt("seed")), emptySet(), "focus", now)
        val repeated = MixTasteProfile(listOf(seed), List(10) { attempt("seed") }, emptySet(), "focus", now)
        assertTrue(once.confidence(seed) < repeated.confidence(seed))
        assertTrue(once.confidence(seed) < 0.5)
    }
    @Test fun `skip in another mix cannot teach this mix a rejection`() {
        val seed = song("seed", "folk")
        val skip = attempt("seed", "SKIP", "workout").copy(uniqueMs = 1000)
        val profile = MixTasteProfile(listOf(seed), listOf(skip), emptySet(), "focus", now)
        assertEquals(0.0, profile.components(seed).getValue("sessionTaste"))
        assertEquals(0.0, profile.components(seed).getValue("longTermTaste"))
    }
    @Test fun `future observations cannot leak into earlier recommendations`() {
        val seed = song("seed", "folk")
        val profile = MixTasteProfile(listOf(seed), listOf(attempt("seed", time = now + 1000)), emptySet(), "focus", now)
        assertEquals(0.0, profile.confidence(seed))
        assertEquals(0.0, profile.components(seed).values.sum())
    }
    @Test fun `errors and explicit scoped dislikes never become taste labels`() {
        val seed = song("seed", "folk")
        for (reason in listOf("ERROR", "UNKNOWN", "DISLIKE", "INTERRUPTION")) {
            val profile = MixTasteProfile(listOf(seed), listOf(attempt("seed", reason)), emptySet(), "focus", now)
            assertEquals(0.0, profile.components(seed).getValue("longTermTaste"))
        }
    }
    @Test fun `explicit favorites seed cold start taste without fabricated history`() {
        val seed = song("seed", "folk")
        val profile = MixTasteProfile(listOf(seed), emptyList(), setOf("seed"), "focus", now)
        assertTrue(profile.components(song("unheard", "folk")).getValue("longTermTaste") > 0)
        assertEquals(0.0, profile.components(seed).getValue("recentTaste"))
    }
}
