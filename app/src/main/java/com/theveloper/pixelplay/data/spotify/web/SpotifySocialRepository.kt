package com.theveloper.pixelplay.data.spotify.web

import com.theveloper.pixelplay.data.spotify.SpotifyPlaylist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class FriendPresence(val label: String) {
    PLAYING("Listening now"),
    PAUSED("Paused"),
    RECENT("Recently active"),
    OFFLINE("Offline"),
    HIDDEN("Activity hidden")
}

data class SpotifyFriendActivity(
    val trackUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String?,
    val contextUri: String?,
    val contextName: String?,
    /** When Spotify last saw this track change (ms). */
    val timestamp: Long,
    val isPlaying: Boolean,
    /** True when this came from the live feed, which reports pauses; the old buddy list doesn't. */
    val fromLiveFeed: Boolean = false
) {
    val trackId: String get() = trackUri.substringAfterLast(':')
}

data class SpotifyFriend(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val activity: SpotifyFriendActivity?,
    val presence: FriendPresence
)

data class SpotifyPublicProfile(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val followers: Int,
    val playlists: List<SpotifyPlaylist>
)

/**
 * Friends on Spotify, from three layers so a change on Spotify's side doesn't take it all down:
 *  1. `listening-activity/v1/feed`: what the desktop app uses now, with a real "is playing" flag.
 *  2. `presence-view/v1/buddylist`: the older feed; has names but can miss people.
 *  3. The people you follow: shown as "Activity hidden" when they don't share listening.
 * Nobody can see a friend who turned off "Share my listening activity" or is in a private session.
 */
@Singleton
class SpotifySocialRepository @Inject constructor(
    private val pathfinder: SpotifyPathfinder,
    private val session: SpotifyWebSession
) {
    companion object {
        private const val FEED_URL = "https://spclient.wg.spotify.com/listening-activity/v1/feed"
        private const val BUDDY_URL = "https://guc-spclient.spotify.com/presence-view/v1/buddylist"
        private const val PROFILE_URL = "https://spclient.wg.spotify.com/user-profile-view/v3/profile"
        private const val RECENT_MS = 30 * 60_000L
        private const val PROFILE_TTL_MS = 12 * 60 * 60_000L
    }

    private data class Cached<T>(val value: T, val at: Long)
    private val trackCache = ConcurrentHashMap<String, SpotifyFriendActivity>()
    private val profileCache = ConcurrentHashMap<String, Cached<SpotifyPublicProfile>>()
    @Volatile private var followingCache: Cached<List<SpotifyPublicProfile>>? = null
    private val lookups = Semaphore(4)

    /** Everyone to show in the Friends list, listening-now first. */
    suspend fun friends(includeHidden: Boolean = true): List<SpotifyFriend> = coroutineScope {
        val feedJob = async { runCatching { feed() }.onFailure { if (it is CancellationException) throw it }.getOrNull() }
        val buddyJob = async { runCatching { buddyList() }.onFailure { if (it is CancellationException) throw it }.getOrNull() }
        val followingJob = async { if (includeHidden) runCatching { following() }.getOrNull().orEmpty() else emptyList() }
        val live = feedJob.await()
        val legacy = buddyJob.await()
        if (live == null && legacy == null) throw SpotifyWebException("Couldn't load friend activity from Spotify.")
        val now = System.currentTimeMillis()
        val merged = linkedMapOf<String, SpotifyFriend>()
        legacy.orEmpty().forEach { merged[it.userId] = it }
        live.orEmpty().forEach { f ->
            val older = merged[f.userId]
            // The live feed wins for status; keep the richer names from the buddy list.
            merged[f.userId] = f.copy(
                name = f.name.takeUnless { it == f.userId } ?: older?.name ?: f.name,
                avatarUrl = f.avatarUrl ?: older?.avatarUrl,
                activity = f.activity?.let { a ->
                    val o = older?.activity?.takeIf { it.trackUri == a.trackUri }
                    if (o != null && a.title.isBlank()) a.copy(title = o.title, artist = o.artist, album = o.album, coverUrl = a.coverUrl ?: o.coverUrl, contextName = a.contextName ?: o.contextName) else a
                } ?: older?.activity
            )
        }
        val withNames = merged.values.map { f ->
            async { lookups.withPermit { fillIn(f) } }
        }.awaitAll().map { it.copy(presence = presenceOf(it.activity, now)) }
        val hidden = followingJob.await().filter { it.userId !in merged }.map {
            SpotifyFriend(it.userId, it.name, it.avatarUrl, null, FriendPresence.HIDDEN)
        }
        (withNames + hidden).sortedWith(
            compareBy<SpotifyFriend> { it.presence.ordinal }
                .thenByDescending { it.activity?.timestamp ?: 0L }
                .thenBy { it.name.lowercase() }
        )
    }

    /** A person's public profile and playlists. */
    suspend fun profile(userId: String, force: Boolean = false): SpotifyPublicProfile {
        require(userId.isNotBlank())
        val cached = profileCache[userId]
        if (!force && cached != null && System.currentTimeMillis() - cached.at < PROFILE_TTL_MS) return cached.value
        val url = "$PROFILE_URL/${java.net.URLEncoder.encode(userId, "UTF-8")}?playlist_limit=50&artist_limit=0&episode_limit=0&market=from_token"
        val json = JSONObject(pathfinder.rest("GET", url))
        val profile = SpotifySocialParsers.profile(userId, json)
        profileCache[userId] = Cached(profile, System.currentTimeMillis())
        return profile
    }

    /** People you follow (users only, not artists). */
    suspend fun following(): List<SpotifyPublicProfile> {
        followingCache?.takeIf { System.currentTimeMillis() - it.at < PROFILE_TTL_MS }?.let { return it.value }
        val me = session.username.ifBlank { return emptyList() }
        val json = JSONObject(pathfinder.rest("GET", "$PROFILE_URL/${java.net.URLEncoder.encode(me, "UTF-8")}/following?market=from_token"))
        val list = SpotifySocialParsers.following(json)
        followingCache = Cached(list, System.currentTimeMillis())
        return list
    }

    private suspend fun feed(): List<SpotifyFriend> {
        val body = pathfinder.rest("POST", FEED_URL, JSONObject().put("unused", true).put("resultLimit", 100).toString())
        return SpotifySocialParsers.feed(JSONObject(body))
    }

    private suspend fun buddyList(): List<SpotifyFriend> =
        SpotifySocialParsers.buddyList(JSONObject(pathfinder.rest("GET", BUDDY_URL)))

    /** The live feed only has ids: look up names, avatars and track titles (cached). */
    private suspend fun fillIn(friend: SpotifyFriend): SpotifyFriend {
        var result = friend
        if (friend.name == friend.userId || friend.avatarUrl == null) {
            runCatching { profile(friend.userId) }.getOrNull()?.let { p ->
                result = result.copy(name = p.name.ifBlank { result.name }, avatarUrl = result.avatarUrl ?: p.avatarUrl)
            }
        }
        val activity = result.activity
        if (activity != null && activity.title.isBlank()) {
            val known = trackCache[activity.trackUri] ?: runCatching { lookupTrack(activity.trackUri) }.getOrNull()?.also { trackCache[activity.trackUri] = it }
            if (known != null) result = result.copy(activity = activity.copy(title = known.title, artist = known.artist,
                album = known.album, coverUrl = activity.coverUrl ?: known.coverUrl))
        }
        return result
    }

    private suspend fun lookupTrack(uri: String): SpotifyFriendActivity? {
        val data = pathfinder.query(SpotifyQueries.TRACK, JSONObject().put("uri", uri)).optJSONObject("trackUnion") ?: return null
        val track = SpotifyWebParsers.track(data, uri, uri)
        if (track.id.startsWith("unavailable:")) return null
        return SpotifyFriendActivity(uri, track.title, track.artistName, track.albumName, track.coverUrl, null, null, 0, false)
    }

    private fun presenceOf(activity: SpotifyFriendActivity?, now: Long): FriendPresence = when {
        activity == null -> FriendPresence.OFFLINE
        activity.isPlaying -> FriendPresence.PLAYING
        activity.fromLiveFeed && now - activity.timestamp < 10 * 60_000L -> FriendPresence.PAUSED
        now - activity.timestamp < RECENT_MS -> FriendPresence.RECENT
        else -> FriendPresence.OFFLINE
    }
}

/** Pure JSON mapping for the social endpoints; unit-tested with sample responses. */
internal object SpotifySocialParsers {
    private fun userId(uri: String?): String? = uri?.takeIf { it.startsWith("spotify:user:") }?.substringAfterLast(':')?.let {
        java.net.URLDecoder.decode(it, "UTF-8")
    }

    private fun parseTime(value: Any?): Long = when (value) {
        is Number -> value.toLong().let { if (it < 10_000_000_000L) it * 1000 else it }
        is String -> value.toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1000 else it }
            ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
        else -> 0L
    }

    fun feed(json: JSONObject): List<SpotifyFriend> {
        val entities = json.optJSONArray("entities") ?: return emptyList()
        val byUser = linkedMapOf<String, SpotifyFriend>()
        for (i in 0 until entities.length()) {
            val item = entities.optJSONObject(i) ?: continue
            val entity = item.optJSONObject("userEntity") ?: item.optJSONObject("followEntity") ?: continue
            val id = userId(entity.optString("uri")) ?: continue
            val a = entity.optJSONObject("activity") ?: continue
            val trackUri = a.optString("entityUri").takeIf { it.startsWith("spotify:track:") } ?: continue
            val ts = parseTime(a.opt("timestamp"))
            val activity = SpotifyFriendActivity(
                trackUri = trackUri,
                title = a.optString("name").ifBlank { a.optString("entityName") },
                artist = a.optString("artistName"),
                album = "",
                coverUrl = a.optString("imageUrl").ifBlank { null },
                contextUri = a.optString("contextUri").ifBlank { null },
                contextName = a.optString("contextName").ifBlank { null },
                timestamp = ts,
                isPlaying = a.optBoolean("isPlaying", false),
                fromLiveFeed = true
            )
            val existing = byUser[id]
            if (existing == null || ts > (existing.activity?.timestamp ?: 0L)) {
                byUser[id] = SpotifyFriend(id, entity.optString("name").ifBlank { entity.optString("displayName").ifBlank { id } },
                    entity.optString("imageUrl").ifBlank { null }, activity, FriendPresence.OFFLINE)
            }
        }
        return byUser.values.toList()
    }

    fun buddyList(json: JSONObject): List<SpotifyFriend> {
        val friends = json.optJSONArray("friends") ?: return emptyList()
        return (0 until friends.length()).mapNotNull { i ->
            val f = friends.optJSONObject(i) ?: return@mapNotNull null
            val user = f.optJSONObject("user") ?: return@mapNotNull null
            val id = userId(user.optString("uri")) ?: return@mapNotNull null
            val t = f.optJSONObject("track")
            val activity = t?.optString("uri")?.takeIf { it.startsWith("spotify:track:") }?.let { uri ->
                SpotifyFriendActivity(
                    trackUri = uri,
                    title = t.optString("name"),
                    artist = t.optJSONObject("artist")?.optString("name").orEmpty(),
                    album = t.optJSONObject("album")?.optString("name").orEmpty(),
                    coverUrl = t.optString("imageUrl").ifBlank { null },
                    contextUri = t.optJSONObject("context")?.optString("uri")?.ifBlank { null },
                    contextName = t.optJSONObject("context")?.optString("name")?.ifBlank { null },
                    timestamp = parseTime(f.opt("timestamp")),
                    isPlaying = false
                )
            }
            SpotifyFriend(id, user.optString("name").ifBlank { id }, user.optString("imageUrl").ifBlank { null }, activity, FriendPresence.OFFLINE)
        }
    }

    fun profile(userId: String, json: JSONObject): SpotifyPublicProfile {
        val lists = json.optJSONArray("public_playlists") ?: JSONArray()
        val playlists = (0 until lists.length()).mapNotNull { i ->
            val p = lists.optJSONObject(i) ?: return@mapNotNull null
            val id = p.optString("uri").takeIf { it.startsWith("spotify:playlist:") || it.contains(":playlist:") }?.substringAfterLast(':') ?: return@mapNotNull null
            SpotifyPlaylist(id, p.optString("name").ifBlank { "Untitled playlist" }, p.optString("image_url").ifBlank { null }, 0,
                userId(p.optString("owner_uri")) ?: userId, p.optString("owner_name"), false)
        }
        return SpotifyPublicProfile(userId, json.optString("name").ifBlank { userId }, json.optString("image_url").ifBlank { null },
            json.optInt("followers_count", 0), playlists)
    }

    fun following(json: JSONObject): List<SpotifyPublicProfile> {
        val profiles = json.optJSONArray("profiles") ?: return emptyList()
        return (0 until profiles.length()).mapNotNull { i ->
            val p = profiles.optJSONObject(i) ?: return@mapNotNull null
            val id = userId(p.optString("uri")) ?: return@mapNotNull null // skips artists
            SpotifyPublicProfile(id, p.optString("name").ifBlank { id }, p.optString("image_url").ifBlank { null }, p.optInt("followers_count", 0), emptyList())
        }
    }
}
