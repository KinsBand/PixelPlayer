package com.theveloper.pixelplay.data.social

/** One of a friend's most-played songs this week; [track] is that song's newest play. */
data class FriendTopSong(val track: FriendTrack, val plays: Int)

/**
 * Pure: a friend's top songs over their kept history (the rolling 7-day window), most plays
 * first, newest play breaking ties. Plays are grouped by [FriendTrack.key], like the 🔥 counts.
 * Unit-tested in FriendWeeklyTopTest.
 */
object FriendWeeklyTop {
    fun topSongs(history: List<FriendTrack>, limit: Int = 10): List<FriendTopSong> =
        history.groupBy { it.key }
            .values
            .map { plays -> FriendTopSong(plays.maxBy { it.playedAt }, plays.size) }
            .sortedWith(compareByDescending<FriendTopSong> { it.plays }.thenByDescending { it.track.playedAt })
            .take(limit)
}
