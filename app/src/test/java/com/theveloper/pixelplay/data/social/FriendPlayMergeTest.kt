package com.theveloper.pixelplay.data.social

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Guards against one friend play showing up as several back-to-back history cards. */
class FriendPlayMergeTest {
    private val min = 60_000L
    private fun track(uri: String, playedAt: Long, durationMs: Long? = 210_000L) =
        FriendTrack(title = uri, artist = "Artist", trackUri = uri, durationMs = durationMs, playedAt = playedAt)

    private fun sight(history: List<FriendTrack>, uri: String, startedAt: Long, seenAt: Long, playing: Boolean = true,
                      durationMs: Long? = 210_000L) =
        FriendPlayMerge.merge(history, track(uri, startedAt, durationMs), playing, seenAt)

    @Test fun `polls with the same start are one play`() {
        var h = emptyList<FriendTrack>()
        for (i in 0 until 20) h = sight(h, "a", startedAt = 0, seenAt = i * 10_000L)
        assertEquals(1, h.size)
    }

    @Test fun `pause and resume of one song is one play`() {
        var h = sight(emptyList(), "a", startedAt = 0, seenAt = 0)
        h = sight(h, "a", startedAt = 0, seenAt = 60_000, playing = false)          // seen paused
        h = sight(h, "a", startedAt = 9 * min, seenAt = 9 * min)                     // resumed much later: timestamp moved
        assertEquals(1, h.size)
    }

    @Test fun `drifting timestamps inside the song are one play`() {
        var h = emptyList<FriendTrack>()
        listOf(0L, 40_000L, 95_000L, 150_000L).forEach { h = sight(h, "a", startedAt = it, seenAt = it) }
        assertEquals(1, h.size)
    }

    @Test fun `a different song in between keeps both plays`() {
        var h = sight(emptyList(), "a", startedAt = 0, seenAt = 0)
        h = sight(h, "b", startedAt = 4 * min, seenAt = 4 * min)
        h = sight(h, "a", startedAt = 8 * min, seenAt = 8 * min)
        assertEquals(listOf("a", "b", "a"), h.map { it.key })
    }

    @Test fun `looping a song after it ends is a replay`() {
        var h = sight(emptyList(), "a", startedAt = 0, seenAt = 0)
        h = sight(h, "a", startedAt = 210_000, seenAt = 215_000)
        h = sight(h, "a", startedAt = 420_000, seenAt = 425_000)
        assertEquals(3, h.size)
        assertEquals(3, FriendPlayMerge.playCounts(h)["a"])
    }

    @Test fun `plays across days are counted`() {
        val day = 24 * 60 * min
        var h = emptyList<FriendTrack>()
        listOf(0L, day, 2 * day).forEach { h = sight(h, "a", startedAt = it, seenAt = it) }
        assertEquals(3, FriendPlayMerge.playCounts(h)["a"])
    }

    @Test fun `stored duplicates collapse on load`() {
        // What the old ±30 s rule wrote for one play with a pause: two rows 2 min apart.
        val stored = listOf(track("a", 120_000), track("a", 0), track("b", -5 * min))
        val collapsed = FriendPlayMerge.collapse(stored)
        assertEquals(listOf("a", "b"), collapsed.map { it.key })
    }

    @Test fun `long pause without a seen pause and a new start is a replay`() {
        var h = sight(emptyList(), "a", startedAt = 0, seenAt = 0)
        h = sight(h, "a", startedAt = 2 * 60 * min, seenAt = 2 * 60 * min)
        assertEquals(2, h.size)
    }
}
