package com.theveloper.pixelplay.presentation.components

import android.content.Context
import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository.PlaybackHistoryEntry
import com.theveloper.pixelplay.presentation.library.MusicVibeFilters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

// Home discovery: the "Right now" daypart mix, the Back again shelf (Forgotten / On this day)
// and the weekly Fresh Finds. Everything here is plain functions over play history and songs,
// so it can run on Dispatchers.Default and be reasoned about without the UI.

private val RecentWindowMs = TimeUnit.DAYS.toMillis(60)
private const val DaypartMixSize = 40
private const val DaypartMinHistorySongs = 15
private const val ForgottenMinPlays = 5
private const val ShelfSize = 20

/** Song ids picked from play history, before they're resolved to songs. */
@Immutable
data class HomeHistoryPicks(
    val daypart: Daypart,
    /** Played in this daypart in the last 60 days, most-played first. */
    val daypartIds: List<String> = emptyList(),
    /** At least [ForgottenMinPlays] plays ever, none in the last 60 days, most-played first. */
    val forgottenIds: List<String> = emptyList(),
    /** Played on this date (or within 3 days of it) in an earlier year: song id → year. */
    val onThisDay: List<Pair<String, Int>> = emptyList(),
    /** Every song id with a play in the last 60 days. */
    val recentlyPlayedIds: Set<String> = emptySet(),
    /** Every song id with any play in the loaded history. */
    val everPlayedIds: Set<String> = emptySet()
) {
    val allIds: List<String>
        get() = (daypartIds + forgottenIds + onThisDay.map { it.first }).distinct()
}

/** Picks song ids for the history-driven Home shelves. Heavy: call off the main thread. */
fun computeHomeHistoryPicks(
    history: List<PlaybackHistoryEntry>,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): HomeHistoryPicks {
    val daypart = Daypart.of(Instant.ofEpochMilli(nowMillis).atZone(zone).hour)
    if (history.isEmpty()) return HomeHistoryPicks(daypart)

    val recentCutoff = nowMillis - RecentWindowMs
    val playCounts = HashMap<String, Int>()
    val lastPlayed = HashMap<String, Long>()
    val daypartCounts = HashMap<String, Int>()
    for (entry in history) {
        playCounts[entry.songId] = (playCounts[entry.songId] ?: 0) + 1
        if ((lastPlayed[entry.songId] ?: Long.MIN_VALUE) < entry.timestamp) lastPlayed[entry.songId] = entry.timestamp
        if (entry.timestamp >= recentCutoff &&
            Daypart.of(Instant.ofEpochMilli(entry.timestamp).atZone(zone).hour) == daypart
        ) {
            daypartCounts[entry.songId] = (daypartCounts[entry.songId] ?: 0) + 1
        }
    }

    val recentIds = lastPlayed.filterValues { it >= recentCutoff }.keys
    val daypartIds = daypartCounts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { lastPlayed[it.key] ?: 0L })
        .map { it.key }
        .take(DaypartMixSize)

    val forgottenIds = playCounts.entries
        .filter { it.value >= ForgottenMinPlays && (lastPlayed[it.key] ?: Long.MAX_VALUE) < recentCutoff }
        .sortedByDescending { it.value }
        .map { it.key }
        .take(ShelfSize)

    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    fun onDates(tolerance: Long): List<Pair<String, Int>> {
        val seen = LinkedHashMap<String, Int>()
        history.sortedByDescending { it.timestamp }.forEach { entry ->
            val date = Instant.ofEpochMilli(entry.timestamp).atZone(zone).toLocalDate()
            if (date.year >= today.year) return@forEach
            val sameDayThatYear = runCatching { today.withYear(date.year) }.getOrNull() ?: return@forEach
            if (kotlin.math.abs(ChronoUnit.DAYS.between(sameDayThatYear, date)) <= tolerance) {
                seen.putIfAbsent(entry.songId, date.year)
            }
        }
        return seen.entries.map { it.key to it.value }.take(ShelfSize)
    }
    val onThisDay = onDates(0).ifEmpty { onDates(3) }

    return HomeHistoryPicks(
        daypart = daypart,
        daypartIds = daypartIds,
        forgottenIds = forgottenIds,
        onThisDay = onThisDay,
        recentlyPlayedIds = recentIds,
        everPlayedIds = playCounts.keys
    )
}

// ---- "Right now" daypart mix -------------------------------------------------------------------

/** The vibe a daypart leans toward when there isn't enough history for that time of day. */
private fun Daypart.defaultVibeId(): String = when (this) {
    Daypart.Morning -> "focus"
    Daypart.Afternoon -> "drive"
    Daypart.Evening -> "chill"
    Daypart.Night -> "dreamy"
}

private fun Daypart.words(): List<String> = when (this) {
    Daypart.Morning -> listOf("wake-up", "slow start", "sunrise")
    Daypart.Afternoon -> listOf("afternoon drift", "midday", "golden hour")
    Daypart.Evening -> listOf("wind-down", "dusk", "after hours")
    Daypart.Night -> listOf("late-night", "slowdown", "moonlit")
}

@Immutable
data class DaypartMix(
    val daypart: Daypart,
    /** Playful generated name, e.g. "dreamy indie wind-down". */
    val name: String,
    /** "Thursday evening". */
    val whenLabel: String,
    val songs: List<Song>
)

/**
 * Builds the "Right now" mix: what you play at this time of day (last 60 days), plus about 10%
 * songs you haven't played in 60+ days that fit the daypart's vibe. With little history it falls
 * back to [fallbackPool] ranked by the daypart's default vibe. Heavy: call off the main thread.
 */
fun buildDaypartMix(
    picks: HomeHistoryPicks,
    daypartSongs: List<Song>,
    fallbackPool: List<Song>,
    libraryPool: List<Song>,
    mood: HomeMoodScores?,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): DaypartMix? {
    val vibe = mood?.mood ?: MusicVibeFilters.presets.firstOrNull { it.id == picks.daypart.defaultVibeId() }
    val base: List<Song> = if (daypartSongs.size >= DaypartMinHistorySongs) {
        daypartSongs
    } else {
        val ranked = if (vibe == null) fallbackPool else fallbackPool.sortedByDescending { MusicVibeFilters.score(it, vibe) }
        (daypartSongs + ranked).distinctBy { it.id }
    }
    if (base.isEmpty()) return null

    // ~10% rediscovery: unplayed-lately songs that fit the vibe, slotted in every 9 tracks.
    val taken = base.mapTo(HashSet()) { it.id }
    val rediscover = if (vibe == null) emptyList() else libraryPool.asSequence()
        .filter { it.id !in taken && it.id !in picks.recentlyPlayedIds }
        .filter { MusicVibeFilters.score(it, vibe) >= MusicVibeFilters.MATCH_THRESHOLD }
        .take(DaypartMixSize / 10)
        .toList()
    val mixed = ArrayList<Song>(DaypartMixSize)
    val rediscoverQueue = ArrayDeque(rediscover)
    base.forEachIndexed { index, song ->
        mixed += song
        if ((index + 1) % 9 == 0) rediscoverQueue.removeFirstOrNull()?.let { mixed += it }
    }
    mixed.addAll(rediscoverQueue)
    val ranked = rankSongsByMood(mixed, mood).items.take(DaypartMixSize)

    val date = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return DaypartMix(
        daypart = picks.daypart,
        name = daypartMixName(ranked, picks.daypart, date, mood),
        whenLabel = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + picks.daypart.label,
        songs = ranked
    )
}

/**
 * "{mood} {genre} {daypart word}", lower case. The word is picked by date so it changes day to
 * day but stays put within a daypart.
 */
private fun daypartMixName(songs: List<Song>, daypart: Daypart, date: LocalDate, mood: HomeMoodScores?): String {
    fun <T> List<T>.topLabel(selector: (T) -> String?): String? =
        mapNotNull(selector).groupingBy { it.lowercase(Locale.ROOT) }.eachCount().maxByOrNull { it.value }?.key
    val moodWord = mood?.mood?.label?.lowercase(Locale.ROOT) ?: songs.topLabel { songMoodLabel(it) }
    val genreWord = songs.topLabel { songGenreLabel(it) }
    val words = daypart.words()
    val word = words[(date.dayOfYear + daypart.ordinal) % words.size]
    return listOfNotNull(moodWord, genreWord, word)
        .distinct()
        .joinToString(" ")
}

/** Freezes the hero name per date + daypart so it doesn't change every time you play a song. */
object DaypartMixNameCache {
    private const val PREFS = "home_discovery"
    private const val KEY = "daypart_name"

    fun nameFor(context: Context, date: LocalDate, daypart: Daypart, moodId: String?, generated: String): String {
        if (moodId != null) return generated // Mood names follow the chip; not cached.
        val stamp = "$date|${daypart.name}"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY, null)
        if (stored != null && stored.substringBefore('\n') == stamp) return stored.substringAfter('\n')
        prefs.edit().putString(KEY, "$stamp\n$generated").apply()
        return generated
    }
}

// ---- Fresh Finds (weekly) ----------------------------------------------------------------------

/** A Fresh Finds row: a song you've never played and, for friend finds, who played it. */
@Immutable
data class FreshFind(
    val song: Song,
    /** "{friend} played this"; null for library finds (no filler line). */
    val why: String? = null,
    /** The friend who played it, when that's the reason. */
    val friendAvatarUrl: String? = null
)

/** Monday-based week stamp, e.g. "2026-W40", so the list is stable all week. */
fun freshFindsWeekStamp(nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val date = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val week = date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val year = date.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR)
    return "%d-W%02d".format(year, week)
}

/**
 * The week's Fresh Finds, about 30 songs you've never played, at most 2 per artist:
 *  1. friends' plays you haven't heard ("{friend} played this"),
 *  2. songs in your library by artists you've never played ("In your library, never played").
 * The library part is shuffled with the week as the seed, so it's stable all week and new on
 * Monday. Heavy: call off the main thread.
 */
fun buildFreshFinds(
    friendFinds: List<FreshFind>,
    library: List<Song>,
    everPlayedIds: Set<String>,
    hiddenIds: Set<String>,
    weekStamp: String,
    /** This week's saved library picks (song ids, in order), so that part is stable all week. */
    cachedIds: List<String> = emptyList(),
    maxItems: Int = 30
): List<FreshFind> {
    val playedArtists = library.asSequence()
        .filter { it.id in everPlayedIds || it.userActivityStats.playCount > 0 }
        .map { it.displayArtist.trim().lowercase(Locale.ROOT) }
        .toHashSet()
    val neverPlayedLibrary = library
        .filter { song ->
            song.id !in everPlayedIds && song.userActivityStats.playCount == 0 &&
                song.displayArtist.trim().lowercase(Locale.ROOT) !in playedArtists
        }
        .shuffled(java.util.Random(weekStamp.hashCode().toLong()))
        .map { FreshFind(it) }

    val perArtist = HashMap<String, Int>()
    val seen = HashSet<String>()
    val out = ArrayList<FreshFind>(maxItems)
    // Friends first (they change through the week as friends listen), then this week's saved
    // library picks in their saved order, then any new library picks to top up.
    val libraryById = neverPlayedLibrary.associateBy { it.song.id }
    val ordered = friendFinds + cachedIds.mapNotNull { libraryById[it] } + neverPlayedLibrary
    for (find in ordered) {
        if (out.size >= maxItems) break
        val song = find.song
        if (song.id in hiddenIds || song.id in everPlayedIds || !seen.add(song.id)) continue
        val artistKey = song.displayArtist.trim().lowercase(Locale.ROOT)
        val n = perArtist[artistKey] ?: 0
        if (n >= 2) continue
        perArtist[artistKey] = n + 1
        out += find
    }
    return out
}

/** "Not for me" picks, per week, so a dismissed song stays gone until Monday. */
object FreshFindsStore {
    private const val PREFS = "home_discovery"
    private const val KEY_HIDDEN = "fresh_hidden"
    private const val KEY_SEEN_WEEK = "fresh_seen_week"

    fun hidden(context: Context, week: String): Set<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HIDDEN, null) ?: return emptySet()
        if (raw.substringBefore('\n') != week) return emptySet()
        return raw.substringAfter('\n', "").split('\n').filter { it.isNotBlank() }.toSet()
    }

    fun hide(context: Context, week: String, songId: String): Set<String> {
        val next = hidden(context, week) + songId
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_HIDDEN, week + "\n" + next.joinToString("\n")).apply()
        return next
    }

    private const val KEY_CACHE = "fresh_cache"

    /** Song ids of this week's list, in order (empty when the week changed). */
    fun cachedIds(context: Context, week: String): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CACHE, null) ?: return emptyList()
        if (raw.substringBefore('\n') != week) return emptyList()
        return raw.substringAfter('\n', "").split('\n').filter { it.isNotBlank() }
    }

    fun saveIds(context: Context, week: String, ids: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CACHE, week + "\n" + ids.joinToString("\n")).apply()
    }

    /** True until the first Fresh Finds play of [week]. */
    fun isNew(context: Context, week: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SEEN_WEEK, null) != week

    fun markSeen(context: Context, week: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SEEN_WEEK, week).apply()
    }
}
