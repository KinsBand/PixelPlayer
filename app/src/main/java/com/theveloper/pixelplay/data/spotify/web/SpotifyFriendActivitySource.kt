package com.theveloper.pixelplay.data.spotify.web

import com.theveloper.pixelplay.data.accounts.MusicSources
import com.theveloper.pixelplay.data.social.FriendActivityReport
import com.theveloper.pixelplay.data.social.FriendActivitySource
import com.theveloper.pixelplay.data.social.FriendPublicPlaylist
import com.theveloper.pixelplay.data.social.FriendTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feeds the library's Friends section from Spotify: the live listening feed (with the older
 * buddy list as backup), people you follow who hide their activity, and each friend's public
 * playlists (cached for hours, so polling stays light). Reports nothing until Spotify is
 * connected with "Sign in with Spotify".
 */
@Singleton
class SpotifyFriendActivitySource @Inject constructor(
    private val session: SpotifyWebSession,
    private val social: SpotifySocialRepository,
    private val library: SpotifyWebLibrary
) : FriendActivitySource {

    /** Song count and total length per public playlist, so cards show real numbers before opening. */
    private data class PlaylistStats(val count: Int, val durationMs: Long, val at: Long)
    private val stats = java.util.concurrent.ConcurrentHashMap<String, PlaylistStats>()
    private val statLookups = Semaphore(2)
    private companion object {
        const val STATS_TTL_MS = 12 * 60 * 60_000L
        /** New playlists counted per poll; the rest fill in over the next polls (every 10–30 s). */
        const val STATS_PER_POLL = 6
    }

    override val source: String = MusicSources.SPOTIFY

    private val profileLookups = Semaphore(3)

    /**
     * Song lengths by track id, so the history can tell a resume from a replay. The feed doesn't
     * carry them; one search per new song (cached, -1 = not found, retried after an hour).
     */
    private data class KnownLength(val ms: Long, val at: Long)
    private val lengths = java.util.concurrent.ConcurrentHashMap<String, KnownLength>()
    private val lengthLookups = Semaphore(1)

    private suspend fun lengthOf(trackId: String, title: String, artist: String, now: Long): Long? {
        lengths[trackId]?.let { known ->
            if (known.ms > 0) return known.ms
            if (now - known.at < 60 * 60_000L) return null
        }
        return lengthLookups.withPermit {
            try {
                val found = library.searchTracks("$title $artist".trim(), limit = 5)
                    .firstOrNull { it.id == trackId }?.durationMs?.toLong()?.takeIf { it > 0 }
                lengths[trackId] = KnownLength(found ?: -1L, now)
                if (lengths.size > 500) lengths.keys.take(100).forEach(lengths::remove)
                found
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { lengths[trackId] = KnownLength(-1L, now); null }
        }
    }

    override suspend fun poll(): List<FriendActivityReport> {
        if (!session.hasSession) return emptyList()
        val now = System.currentTimeMillis()
        val friends = social.friends(includeHidden = true)
        return coroutineScope {
            friends.map { friend ->
                async {
                    val playlists = profileLookups.withPermit {
                        try { social.profile(friend.userId).playlists }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { null }
                    }
                    val a = friend.activity
                    FriendActivityReport(
                        friendId = "${MusicSources.SPOTIFY}:${friend.userId}",
                        displayName = friend.name,
                        avatarUrl = friend.avatarUrl,
                        track = a?.let {
                            FriendTrack(title = it.title, artist = it.artist, coverUrl = it.coverUrl, trackUri = it.trackUri,
                                durationMs = lengthOf(it.trackId, it.title, it.artist, now), playedAt = it.timestamp, album = it.album)
                        },
                        // The live feed says so directly; the old buddy list only has a start time,
                        // so a song that started in the last few minutes counts as playing.
                        isPlaying = a != null && (a.isPlaying || (!a.fromLiveFeed && now - a.timestamp < 4 * 60_000L)),
                        activityHidden = friend.presence == FriendPresence.HIDDEN,
                        publicPlaylists = playlists?.map { p ->
                            val known = stats[p.id]?.takeIf { it.count >= 0 && now - it.at < STATS_TTL_MS }
                            FriendPublicPlaylist(remoteId = p.id, source = MusicSources.SPOTIFY, title = p.title,
                                coverUrl = p.coverUrl, trackCount = known?.count ?: p.totalTracks,
                                durationMs = known?.durationMs?.takeIf { it > 0 },
                                collaborative = p.collaborative,
                                ownerName = p.ownerName.ifBlank { null },
                                saves = p.saves,
                                description = p.description)
                        }
                    )
                }
            }.awaitAll().also { reports -> countMissing(reports, now) }
        }
    }

    /**
     * Counts a few uncounted playlists per poll. The count comes from the first page; the length is
     * only worked out for playlists small enough to read whole (it used to download every song of
     * every friend's playlist, which on big ones wasted a lot of memory every poll).
     */
    private suspend fun countMissing(reports: List<FriendActivityReport>, now: Long) = coroutineScope {
        val missing = reports.flatMap { it.publicPlaylists.orEmpty() }
            .map { it.remoteId }.distinct()
            .filter { id -> stats[id]?.let { now - it.at >= STATS_TTL_MS } ?: true }
            .take(STATS_PER_POLL)
        missing.map { id ->
            async {
                statLookups.withPermit {
                    try {
                        val (count, durationMs) = library.playlistSummary(id)
                        stats[id] = PlaylistStats(count, durationMs ?: 0L, System.currentTimeMillis())
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { stats[id] = PlaylistStats(-1, 0, System.currentTimeMillis() - STATS_TTL_MS + 30 * 60_000L) } // unknown; retry in 30 min
                }
            }
        }.awaitAll()
    }
}
