package com.theveloper.pixelplay.data.social

import java.util.Locale

/** An artist a friend played this week, for the history sheet's picture row. */
data class FriendWeeklyArtist(val name: String, val coverUrl: String?, val plays: Int)

/** Pure: the distinct artists in a friend's history (unit-tested in FriendWeeklyArtistsTest). */
object FriendWeeklyArtists {
    private val SPLIT = Regex("""\s*(?:,|&|\bfeat\.?|\bft\.?|\bfeaturing\b|\bx\b|/|;)\s*""", RegexOption.IGNORE_CASE)

    /** Main artist of each play, most played first (newest play breaks ties), with the newest cover. */
    fun from(history: List<FriendTrack>, limit: Int = 12): List<FriendWeeklyArtist> =
        history.mapNotNull { track ->
            val name = track.artist.split(SPLIT).firstOrNull { it.isNotBlank() }?.trim() ?: return@mapNotNull null
            name to track
        }
            .groupBy { (name, _) -> name.lowercase(Locale.ROOT) }
            .map { (_, plays) ->
                val newest = plays.maxBy { it.second.playedAt }
                Triple(FriendWeeklyArtist(newest.first, newest.second.coverUrl, plays.size), newest.second.playedAt, plays.size)
            }
            .sortedWith(compareByDescending<Triple<FriendWeeklyArtist, Long, Int>> { it.third }.thenByDescending { it.second })
            .take(limit)
            .map { it.first }
}
