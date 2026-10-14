package com.theveloper.pixelplay.data.social

import kotlin.math.abs

/**
 * Turns "what a friend is playing right now" sightings into one history row per real play.
 *
 * Platforms only report the current song, and Spotify's live feed moves the song's timestamp
 * when the friend pauses, resumes or seeks. Matching only on "same start time ±30 s" therefore
 * turned one play into several back-to-back rows. A sighting of the song that's already the
 * newest row counts as the same play when:
 *  - its start is within [SAME_START_MS] of that row (seen again on a later poll), or
 *  - it "started" before that play could have finished (a jump inside the song: a resume or a
 *    seek; a real replay can only begin once the song has about run its length), or
 *  - the friend was seen paused on it and came back within [RESUME_WINDOW_MS] (a resume).
 * Anything else, including the same song again after it had time to finish, is a real replay.
 */
object FriendPlayMerge {
    const val SAME_START_MS = 30_000L
    /** A replay can begin this close to the end of the previous play (crossfade, skipped outro). */
    const val REPLAY_MARGIN_MS = 15_000L
    /** Song length to assume when the platform doesn't give one. */
    const val FALLBACK_DURATION_MS = 3L * 60 * 1000
    /** A pause longer than this ends the play; coming back later starts a new one. */
    const val RESUME_WINDOW_MS = 30L * 60 * 1000

    /**
     * Adds one sighting to a friend's history (newest first) and returns the new history.
     * [isPlaying] = the platform said it's playing (false = paused); [now] = when it was seen.
     */
    fun merge(history: List<FriendTrack>, observed: FriendTrack, isPlaying: Boolean, now: Long): List<FriendTrack> {
        // The exact same play seen again on a later poll (anywhere in the list).
        val exact = history.indexOfFirst { it.key == observed.key && abs(it.playedAt - observed.playedAt) < SAME_START_MS }
        if (exact >= 0) return history.replaceAt(exact) { it.seenAgain(observed, isPlaying, now) }

        val newestIndex = history.indices.maxByOrNull { history[it].playedAt }
        val newest = newestIndex?.let { history[it] }
        if (newest != null && newest.key == observed.key && observed.playedAt >= newest.playedAt - SAME_START_MS) {
            val sinceStart = observed.playedAt - newest.playedAt
            val length = (newest.durationMs ?: observed.durationMs)?.takeIf { it > 0 } ?: FALLBACK_DURATION_MS
            val couldStillBePlaying = sinceStart < length - minOf(REPLAY_MARGIN_MS, length / 10)
            val lastSeen = maxOf(newest.lastSeenAt, newest.playedAt)
            val resumed = newest.pausedSeen && observed.playedAt - lastSeen < RESUME_WINDOW_MS
            if (couldStillBePlaying || resumed) {
                return history.replaceAt(newestIndex) { it.seenAgain(observed, isPlaying, now) }
            }
        }
        val row = observed.copy(lastSeenAt = now, pausedSeen = !isPlaying)
        return (listOf(row) + history).sortedByDescending { it.playedAt }
    }

    /**
     * Rebuilds a stored history with [merge] (oldest first), collapsing duplicates written by the
     * old ±30 s rule. Every stored row counts as a playing sighting seen at its own start.
     */
    fun collapse(history: List<FriendTrack>): List<FriendTrack> =
        history.sortedBy { it.playedAt }.fold(emptyList()) { acc, track ->
            merge(acc, track, isPlaying = !track.pausedSeen, now = maxOf(track.lastSeenAt, track.playedAt))
        }

    /** How many times each song (by [FriendTrack.key]) was played in [history]. */
    fun playCounts(history: List<FriendTrack>): Map<String, Int> =
        history.groupingBy { it.key }.eachCount()

    private fun FriendTrack.seenAgain(observed: FriendTrack, isPlaying: Boolean, now: Long) = copy(
        lastSeenAt = maxOf(lastSeenAt, now),
        // Seen paused at any point: a later timestamp jump is a resume. Playing again clears it
        // only once the play is clearly continuing (the next sighting still merges either way).
        pausedSeen = !isPlaying,
        durationMs = durationMs ?: observed.durationMs,
        coverUrl = coverUrl ?: observed.coverUrl,
    )

    private inline fun List<FriendTrack>.replaceAt(index: Int, change: (FriendTrack) -> FriendTrack): List<FriendTrack> =
        toMutableList().also { it[index] = change(it[index]) }
}
