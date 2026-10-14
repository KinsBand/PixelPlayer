package com.theveloper.pixelplay.data.social

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.spotify.TrackMatching
import java.util.Calendar
import java.util.Locale
import java.util.Random

/**
 * A daily "Blend": your listening and one friend's, mixed into one playlist.
 *
 * Built only from data already on the device: your play counts, the friend's recent listening
 * (friend activity keeps a rolling window) and the friend's saved playlists. Same inputs on the
 * same day give the same mix; a new day reshuffles it.
 */
data class BlendMix(
    val friendId: String,
    val friendName: String,
    val avatarUrl: String?,
    val songs: List<Song>,
    /** Songs you both play (or by artists you both play). */
    val sharedCount: Int,
    /** Local day (yyyyDDD) the mix was built for. */
    val day: Int,
    /** Distinct songs known from the friend's side (recent plays + saved playlists). */
    val friendInputCount: Int = 0,
    /**
     * Whose side each song came from (song id -> [BlendMixGenerator.ME], the friend id, or
     * [BlendMixGenerator.BOTH] for songs you both play). Drives the blend page's contributor filter.
     */
    val contributors: Map<String, String> = emptyMap(),
) {
    /** Nothing known about the friend's listening yet: no card at all. */
    val hasFriendData: Boolean get() = friendInputCount > 0
    /** Too little of theirs to mix well yet: the card says the blend is still building. */
    val isBuilding: Boolean get() = friendInputCount < BlendMixGenerator.MIN_FRIEND_SONGS || songs.size < BlendMixGenerator.MIN_SONGS
}

/** One person in a custom blend: you ([BlendMixGenerator.ME]) or a friend, with their songs, most played first. */
data class BlendMember(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val songs: List<Song>,
)

/**
 * A custom blend: you and every friend you ticked, mixed into one playlist of up to
 * [BlendMixGenerator.GROUP_TARGET_SONGS] songs. Everyone gets a fair share, songs several of you
 * play come first (spread through the mix), and a new day reshuffles it.
 */
data class CustomBlend(
    /** Friend ids in the blend (you are always in it). */
    val friendIds: List<String>,
    val songs: List<Song>,
    /** Who each song came from: song id → member id ([BlendMixGenerator.ME] for you). */
    val contributors: Map<String, String>,
    /** Songs at least two of you play. */
    val sharedCount: Int,
    val day: Int,
    /** Distinct songs known from the friends' side. */
    val friendInputCount: Int,
    /** Songs two or more of you play (the blend page marks them with a sparkle). */
    val sharedSongIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = friendIds.isEmpty()
    val isBuilding: Boolean get() = friendInputCount < BlendMixGenerator.MIN_FRIEND_SONGS || songs.size < BlendMixGenerator.MIN_SONGS
}

object BlendMixGenerator {
    const val MIN_SONGS = 8
    const val MIN_FRIEND_SONGS = 5
    const val TARGET_SONGS = 40
    const val GROUP_TARGET_SONGS = 100

    /** Member id standing for you in a [CustomBlend]. */
    const val ME = "me"

    /** A 1-on-1 blend song you both play (shows under You and under the friend). */
    const val BOTH = "both"

    /** No more than this many songs by one artist in a custom blend (relaxed only if needed to fill it). */
    private const val GROUP_ARTIST_CAP = 5

    /** A friend's song counts for a mood when at least this share of your rated songs by that artist fit it. */
    internal const val ARTIST_MOOD_SHARE = 0.5f

    /** Your side of a mood blend is topped up from your library to about this many songs. */
    const val MOOD_MY_SIDE = 80

    fun today(now: Long = System.currentTimeMillis()): Int {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    internal fun songKey(song: Song): String =
        TrackMatching.normalize(song.title) + "|" + artistKey(song.displayArtist)

    internal fun artistKey(artist: String): String =
        TrackMatching.stripAccents(artist).lowercase(Locale.ROOT)
            .split(',', '&', '/').firstOrNull()?.trim().orEmpty()

    /**
     * @param mine your songs, most played first.
     * @param friend the friend's songs, most played first (history then playlists).
     */
    fun build(
        friendId: String,
        friendName: String,
        avatarUrl: String?,
        mine: List<Song>,
        friend: List<Song>,
        day: Int = today(),
        limit: Int = TARGET_SONGS,
    ): BlendMix {
        val random = Random(day.toLong() * 31 + friendId.hashCode())
        val mineByKey = LinkedHashMap<String, Song>().apply { mine.forEach { putIfAbsent(songKey(it), it) } }
        val friendByKey = LinkedHashMap<String, Song>().apply { friend.forEach { putIfAbsent(songKey(it), it) } }
        val myArtists = mineByKey.values.mapTo(HashSet()) { artistKey(it.displayArtist) }.apply { remove("") }
        val friendArtists = friendByKey.values.mapTo(HashSet()) { artistKey(it.displayArtist) }.apply { remove("") }

        // 1. Songs you both play: prefer your copy (it's already in your library / playable).
        val shared = mineByKey.keys.filter { it in friendByKey }.map { mineByKey.getValue(it) }
        // 2. Songs by artists you both like, from each side.
        val sharedArtists = myArtists intersect friendArtists
        val mineOnArtists = mineByKey.filterKeys { it !in friendByKey }.values
            .filter { artistKey(it.displayArtist) in sharedArtists }
        val friendOnArtists = friendByKey.filterKeys { it !in mineByKey }.values
            .filter { artistKey(it.displayArtist) in sharedArtists }
        // 3. Each side's own favourites (top half, shuffled for the day so it stays fresh).
        val mineRest = mineByKey.filterKeys { it !in friendByKey }.values
            .filterNot { artistKey(it.displayArtist) in sharedArtists }
            .take(limit * 2).shuffled(random)
        val friendRest = friendByKey.filterKeys { it !in mineByKey }.values
            .filterNot { artistKey(it.displayArtist) in sharedArtists }
            .take(limit * 2).shuffled(random)

        val result = LinkedHashMap<String, Song>()
        val sideOf = HashMap<String, String>()
        fun add(song: Song, side: String) {
            if (result.size < limit && result.putIfAbsent(songKey(song), song) == null) sideOf[song.id] = side
        }

        shared.shuffled(random).take(limit / 3).forEach { add(it, BOTH) }
        // Alternate sides so neither person dominates, overlap-adjacent picks first.
        val mineQueue = ArrayDeque(mineOnArtists.shuffled(random) + mineRest)
        val friendQueue = ArrayDeque(friendOnArtists.shuffled(random) + friendRest)
        while (result.size < limit && (mineQueue.isNotEmpty() || friendQueue.isNotEmpty())) {
            friendQueue.removeFirstOrNull()?.let { add(it, friendId) }
            mineQueue.removeFirstOrNull()?.let { add(it, ME) }
        }
        // Spread shared songs through the mix rather than front-loading them.
        val ordered = result.values.toMutableList()
        val sharedKeys = shared.mapTo(HashSet()) { songKey(it) }
        val (sharedPicked, others) = ordered.partition { songKey(it) in sharedKeys }
        val mixed = ArrayList<Song>(ordered.size)
        val step = if (sharedPicked.isEmpty()) Int.MAX_VALUE else (others.size / (sharedPicked.size + 1)).coerceAtLeast(1)
        val sharedIt = sharedPicked.iterator()
        others.forEachIndexed { i, song ->
            if (i > 0 && i % step == 0 && sharedIt.hasNext()) mixed += sharedIt.next()
            mixed += song
        }
        sharedIt.forEachRemaining { mixed += it }

        return BlendMix(
            friendId = friendId,
            friendName = friendName,
            avatarUrl = avatarUrl,
            songs = mixed,
            sharedCount = sharedPicked.size,
            day = day,
            friendInputCount = friendByKey.size,
            contributors = sideOf,
        )
    }

    /**
     * Mixes you and several friends into one playlist of up to [limit] songs.
     *
     * 1. Songs two or more of you play come first (most shared first), a quarter of the mix at most,
     *    and are spread through it rather than bunched at the start.
     * 2. Then everyone takes turns, one song each, so nobody dominates: first songs by artists
     *    someone else in the group also plays, then their own favourites (top of their list,
     *    shuffled for the day).
     * 3. At most [GROUP_ARTIST_CAP] songs per artist while there is enough to choose from, and the
     *    same artist never plays twice in a row where it can be avoided.
     *
     * Same members on the same day give the same mix.
     */
    fun buildGroup(
        members: List<BlendMember>,
        day: Int = today(),
        limit: Int = GROUP_TARGET_SONGS,
    ): CustomBlend {
        val friendIds = members.map { it.id }.filter { it != ME }
        val random = Random(day.toLong() * 31 + members.map { it.id }.sorted().joinToString(",").hashCode())
        // Each member's songs by key, first (most played) copy kept.
        val byMember = members.map { m -> m to LinkedHashMap<String, Song>().apply { m.songs.forEach { putIfAbsent(songKey(it), it) } } }
        val keyOwners = HashMap<String, MutableList<String>>()
        byMember.forEach { (m, songs) -> songs.keys.forEach { keyOwners.getOrPut(it) { mutableListOf() } += m.id } }
        val artistOwners = HashMap<String, MutableSet<String>>()
        byMember.forEach { (m, songs) ->
            songs.values.forEach { s -> artistKey(s.displayArtist).takeIf { it.isNotEmpty() }?.let { artistOwners.getOrPut(it) { HashSet() } += m.id } }
        }
        val friendInputCount = byMember.filter { it.first.id != ME }.flatMapTo(HashSet()) { it.second.keys }.size

        val picked = LinkedHashMap<String, Song>()
        val contributors = HashMap<String, String>()
        val artistCounts = HashMap<String, Int>()
        fun canAdd(song: Song, capped: Boolean): Boolean {
            if (picked.size >= limit || songKey(song) in picked) return false
            if (!capped) return true
            val artist = artistKey(song.displayArtist)
            return artist.isEmpty() || (artistCounts[artist] ?: 0) < GROUP_ARTIST_CAP
        }
        fun add(song: Song, from: String) {
            picked[songKey(song)] = song
            contributors[song.id] = from
            artistKey(song.displayArtist).takeIf { it.isNotEmpty() }?.let { artistCounts[it] = (artistCounts[it] ?: 0) + 1 }
        }

        // 1. Shared songs: prefer your copy (already in your library), otherwise the first owner's.
        val sharedKeys = keyOwners.filter { it.value.size >= 2 }.keys
            .shuffled(random).sortedByDescending { keyOwners.getValue(it).size }
        for (key in sharedKeys) {
            if (picked.size >= limit / 4) break
            val owners = keyOwners.getValue(key)
            val ownerId = if (ME in owners) ME else owners.first()
            val song = byMember.first { it.first.id == ownerId }.second.getValue(key)
            if (canAdd(song, capped = true)) add(song, ownerId)
        }

        // 2. Turns: everyone's queue = songs by artists others play too, then their own favourites.
        val queues = byMember.map { (m, songs) ->
            val own = songs.filterKeys { keyOwners.getValue(it).size < 2 }.values
            val overlap = own.filter { (artistOwners[artistKey(it.displayArtist)]?.size ?: 0) >= 2 }.shuffled(random)
            // Sample across all of their history + playlists (already interleaved), not just the top.
            val rest = own.filterNot { (artistOwners[artistKey(it.displayArtist)]?.size ?: 0) >= 2 }
                .let { list -> if (list.size <= limit * 2) list else list.filterIndexed { i, _ -> i % ((list.size + limit * 2 - 1) / (limit * 2)) == 0 } }
                .shuffled(random)
            m.id to ArrayDeque(overlap + rest)
        }.filter { it.second.isNotEmpty() }
        for (capped in listOf(true, false)) {
            val deferred = queues.map { it.first to ArrayDeque<Song>() }.toMap()
            var progress = true
            while (picked.size < limit && progress) {
                progress = false
                for ((memberId, queue) in queues) {
                    if (picked.size >= limit) break
                    while (queue.isNotEmpty()) {
                        val song = queue.removeFirst()
                        if (canAdd(song, capped)) { add(song, memberId); progress = true; break }
                        if (capped && songKey(song) !in picked) deferred.getValue(memberId).addLast(song)
                    }
                }
            }
            // Songs held back by the artist cap get another chance, without the cap.
            if (capped) queues.forEach { (memberId, queue) -> queue.addAll(deferred.getValue(memberId)) }
        }

        // 3. Order: shared songs spread through the others, then no artist twice in a row.
        val shared = sharedKeys.toHashSet()
        val (sharedPicked, others) = picked.entries.partition { it.key in shared }
        val ordered = ArrayList<Song>(picked.size)
        val step = if (sharedPicked.isEmpty()) Int.MAX_VALUE else (others.size / (sharedPicked.size + 1)).coerceAtLeast(1)
        val sharedIt = sharedPicked.iterator()
        others.forEachIndexed { i, entry ->
            if (i > 0 && i % step == 0 && sharedIt.hasNext()) ordered += sharedIt.next().value
            ordered += entry.value
        }
        sharedIt.forEachRemaining { ordered += it.value }
        for (i in 1 until ordered.size) {
            if (artistKey(ordered[i].displayArtist) != artistKey(ordered[i - 1].displayArtist)) continue
            val swap = (i + 1 until ordered.size).firstOrNull {
                artistKey(ordered[it].displayArtist) != artistKey(ordered[i - 1].displayArtist)
            } ?: continue
            val tmp = ordered[i]; ordered[i] = ordered[swap]; ordered[swap] = tmp
        }

        return CustomBlend(
            friendIds = friendIds,
            songs = ordered,
            contributors = contributors.filterKeys { id -> ordered.any { it.id == id } },
            sharedCount = sharedPicked.size,
            day = day,
            friendInputCount = friendInputCount,
            sharedSongIds = sharedPicked.mapTo(HashSet()) { it.value.id },
        )
    }
}

/**
 * What one mood looks like in your library, used to build a mood blend from everyone's songs.
 *
 * Friends' songs arrive with only a title and artist (no genre, no audio features), so they
 * can't be scored directly. A friend's song fits the mood when:
 * 1. your copy of the same song fits it (or, if your copy was rated and doesn't fit, it doesn't), or
 * 2. it fits on its own (title words, any tags it has), or
 * 3. at least [BlendMixGenerator.ARTIST_MOOD_SHARE] of your rated songs by that artist fit it.
 *
 * Build one with [BlendMixGenerator.moodLens]; it holds only keys and a capped pick list, not the library.
 */
class BlendMoodLens internal constructor(
    private val threshold: Double,
    private val fit: (Song) -> Double,
    /** Song key -> does your (rated) copy fit. */
    private val verdicts: Map<String, Boolean>,
    /** Artist key -> share of your rated songs by them that fit (only artists with at least one fit). */
    private val artistShare: Map<String, Float>,
    /** Your library songs that fit, best fit first (tops up your side of the blend). */
    val libraryPicks: List<Song>,
) {
    fun fits(song: Song): Boolean {
        verdicts[BlendMixGenerator.songKey(song)]?.let { return it }
        if (fit(song) >= threshold) return true
        val artist = BlendMixGenerator.artistKey(song.displayArtist)
        return artist.isNotEmpty() && (artistShare[artist] ?: 0f) >= BlendMixGenerator.ARTIST_MOOD_SHARE
    }
}

/**
 * Builds the [BlendMoodLens] for one mood from your whole library.
 * @param fit the mood score of a song (0.0 exactly = nothing known about it, so it isn't rated).
 * @param threshold the score a song needs to fit.
 */
fun BlendMixGenerator.moodLens(library: List<Song>, threshold: Double, fit: (Song) -> Double): BlendMoodLens {
    val verdicts = HashMap<String, Boolean>()
    val rated = HashMap<String, Int>()
    val fitting = HashMap<String, Int>()
    val picks = ArrayList<Pair<Song, Double>>()
    for (song in library) {
        val score = fit(song)
        // No tags and no measurements: nothing to judge it by, so it neither counts for nor against.
        if (score == 0.0) continue
        val ok = score >= threshold
        val key = songKey(song)
        verdicts[key] = (verdicts[key] ?: false) || ok
        val artist = artistKey(song.displayArtist)
        if (artist.isNotEmpty()) {
            rated[artist] = (rated[artist] ?: 0) + 1
            if (ok) fitting[artist] = (fitting[artist] ?: 0) + 1
        }
        if (ok) picks += song to score
    }
    val share = fitting.mapValues { (artist, n) -> n.toFloat() / (rated[artist] ?: n).coerceAtLeast(1) }
    val bestPicks = picks.sortedByDescending { it.second }.take(MOOD_LIBRARY_PICKS_FOR_LENS).map { it.first }
    return BlendMoodLens(threshold, fit, verdicts, share, bestPicks)
}

private const val MOOD_LIBRARY_PICKS_FOR_LENS = 300

/**
 * Your side of a mood blend: the songs you play most that fit the mood, topped up with your
 * library's best fits until there are about [size].
 */
fun BlendMixGenerator.mineForMood(mine: List<Song>, lens: BlendMoodLens, size: Int = BlendMixGenerator.MOOD_MY_SIDE): List<Song> {
    val played = mine.filter(lens::fits)
    if (played.size >= size) return played
    val seen = played.mapTo(HashSet()) { songKey(it) }
    return played + lens.libraryPicks.filter { seen.add(songKey(it)) }.take(size - played.size)
}

/** Takes one song from each list in turn (a, b, c, a, b, c, …) until all are used. */
fun interleaveSongLists(lists: List<List<Song>>): List<Song> {
    val nonEmpty = lists.filter { it.isNotEmpty() }
    if (nonEmpty.size <= 1) return nonEmpty.firstOrNull().orEmpty()
    val out = ArrayList<Song>(nonEmpty.sumOf { it.size })
    val max = nonEmpty.maxOf { it.size }
    for (i in 0 until max) for (list in nonEmpty) if (i < list.size) out += list[i]
    return out
}
