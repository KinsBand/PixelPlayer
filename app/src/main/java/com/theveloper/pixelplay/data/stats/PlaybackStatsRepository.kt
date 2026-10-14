package com.theveloper.pixelplay.data.stats

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import timber.log.Timber

@Singleton
class PlaybackStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val gson = Gson()
    private val historyFile = File(context.filesDir, "playback_history.json")
    private val atomicHistoryFile = AtomicFile(historyFile)
    private val fileLock = Any()
    private var cachedEvents: List<PlaybackEvent>? = null  // guarded by fileLock
    private val eventsType = object : TypeToken<MutableList<PlaybackEvent>>() {}.type
    private val _refreshVersion = MutableStateFlow(0L)
    val refreshFlow: StateFlow<Long> = _refreshVersion.asStateFlow()

    private val sessionGapThresholdMs = TimeUnit.MINUTES.toMillis(30)

    data class PlaybackEvent(
        val songId: String,
        val timestamp: Long,
        val durationMs: Long,
        val startTimestamp: Long? = null,
        val endTimestamp: Long? = null
    )

    data class PlaybackHistoryEntry(
        val songId: String,
        val timestamp: Long
    )

    data class SongPlaybackSummary(
        val songId: String,
        val title: String,
        val artist: String,
        val albumArtUri: String?,
        val totalDurationMs: Long,
        val playCount: Int
    )

    data class ArtistPlaybackSummary(
        val artist: String,
        val totalDurationMs: Long,
        val playCount: Int,
        val uniqueSongs: Int,
        /** Art of the artist's most-played song in the range, used for avatars and share cards. */
        val albumArtUri: String? = null
    )

    data class GenrePlaybackSummary(
        val genre: String,
        val totalDurationMs: Long,
        val playCount: Int,
        val uniqueArtists: Int
    )

    data class AlbumPlaybackSummary(
        val album: String,
        val albumArtUri: String?,
        val totalDurationMs: Long,
        val playCount: Int,
        val uniqueSongs: Int
    )

    data class TimelineEntry(
        val label: String,
        val totalDurationMs: Long,
        val playCount: Int
    )

    data class PlaybackSegment(
        val songId: String,
        val startMillis: Long,
        val endMillis: Long
    ) {
        val durationMs: Long
            get() = (endMillis - startMillis).coerceAtLeast(0L)
    }

    data class PlaybackSpan(
        val startMillis: Long,
        val endMillis: Long
    ) {
        val durationMs: Long
            get() = (endMillis - startMillis).coerceAtLeast(0L)
    }

    data class DayListeningDistribution(
        val bucketSizeMinutes: Int,
        val buckets: List<DailyListeningBucket>,
        val maxBucketDurationMs: Long,
        val days: List<DailyListeningDay>
    )

    data class DailyListeningBucket(
        val startMinute: Int,
        val endMinuteExclusive: Int,
        val totalDurationMs: Long
    )

    data class DailyListeningDay(
        val date: LocalDate,
        val buckets: List<DailyListeningBucket>,
        val totalDurationMs: Long
    )

    data class PlaybackStatsSummary(
        val range: StatsTimeRange,
        val startTimestamp: Long?,
        val endTimestamp: Long,
        val totalDurationMs: Long,
        val totalPlayCount: Int,
        val uniqueSongs: Int,
        val averageDailyDurationMs: Long,
        val songs: List<SongPlaybackSummary> = emptyList(),
        val topSongs: List<SongPlaybackSummary> = emptyList(),
        val topGenres: List<GenrePlaybackSummary> = emptyList(),
        val timeline: List<TimelineEntry>,
        val topArtists: List<ArtistPlaybackSummary>,
        val topAlbums: List<AlbumPlaybackSummary>,
        val activeDays: Int,
        val longestStreakDays: Int,
        val totalSessions: Int,
        val averageSessionDurationMs: Long,
        val longestSessionDurationMs: Long,
        val averageSessionsPerDay: Double,
        val dayListeningDistribution: DayListeningDistribution?,
        val peakTimeline: TimelineEntry?,
        val peakDayLabel: String?,
        val peakDayDurationMs: Long,
        /** Distinct artists played in the range. */
        val uniqueArtists: Int = 0,
        /** Consecutive days up to today (or yesterday) with any listening, over all history. */
        val currentStreakDays: Int = 0,
        /** Same-length previous period (last month's same days, etc.). Null for All time. */
        val previous: PeriodComparison? = null,
        /** Listening per hour of day (index 0 = midnight), in ms. Empty when nothing was played. */
        val hourlyDurationsMs: List<Long> = emptyList(),
        val weekdayHourlyDurationsMs: List<Long> = emptyList(),
        val weekendHourlyDurationsMs: List<Long> = emptyList(),
        /** One entry per calendar day of the range (capped to the most recent 400 days). */
        val dailyTotals: List<DailyTotal> = emptyList(),
        /** Songs and artists first played in this range. Null for All time or without older history. */
        val discoveries: Discoveries? = null,
        /** The song played most in the last few days, if it was played enough to count as "on repeat". */
        val onRepeat: OnRepeatSong? = null,
        /** #1 artist of each month (Year and All time only). */
        val monthlyTopArtists: List<MonthTopArtist> = emptyList(),
        /** Top albums for the 3x3 share grid (up to 9). */
        val albumGrid: List<AlbumPlaybackSummary> = emptyList(),
        /** All-time counted plays of this range's #1 artist (for milestones). */
        val topArtistAllTimePlays: Int = 0,
        /** Days with any listening so far this calendar year. */
        val activeDaysThisYear: Int = 0,
        /** True when the range was picked with the custom date picker ([range] is then ALL). */
        val isCustomRange: Boolean = false
    )

    data class PeriodComparison(
        val startMillis: Long,
        val endMillis: Long,
        val totalDurationMs: Long,
        val totalPlayCount: Int,
        val uniqueSongs: Int,
        val uniqueArtists: Int,
        /** Same buckets as the current timeline, shifted back one period. */
        val timeline: List<TimelineEntry>,
        /** songId -> 1-based rank by listening time. */
        val songRanks: Map<String, Int>,
        /** [artistRankKey] -> 1-based rank by listening time. */
        val artistRanks: Map<String, Int>
    )

    data class DailyTotal(
        val date: LocalDate,
        val durationMs: Long
    )

    data class NewArtistFind(
        val artist: String,
        val firstPlayedAt: Long,
        val playCount: Int,
        val albumArtUri: String?
    )

    data class Discoveries(
        val newSongs: Int,
        val newArtists: Int,
        val newDurationMs: Long,
        val artists: List<NewArtistFind>,
        /** New song ids, most listened first (capped), for "Play your new finds". */
        val newSongIds: List<String>
    )

    data class OnRepeatSong(
        val songId: String,
        val title: String,
        val artist: String,
        val albumArtUri: String?,
        val recentPlays: Int,
        /** Counted plays per day for the last 11 days, oldest first. */
        val dailyPlays: List<Int>
    )

    data class MonthTopArtist(
        val label: String,
        val artist: String,
        val durationMs: Long,
        val albumArtUri: String?
    )

    suspend fun recordPlayback(
        songId: String,
        durationMs: Long,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        if (songId.isBlank()) return@withContext
        val coercedTimestamp = timestamp.coerceAtLeast(0L)
        val coercedDuration = durationMs.coerceAtLeast(0L)
        val start = (coercedTimestamp - coercedDuration).coerceAtLeast(0L)
        val sanitizedEvent = PlaybackEvent(
            songId = songId,
            timestamp = coercedTimestamp,
            durationMs = coercedDuration,
            startTimestamp = start,
            endTimestamp = coercedTimestamp
        )
        val writeSucceeded = updateEventsAtomically { events ->
            val cutoff = sanitizedEvent.endMillis() - MAX_HISTORY_AGE_MS
            if (cutoff > 0) {
                events.removeAll { it.endMillis() < cutoff }
            }
            events += sanitizedEvent
            events
        }
        if (writeSucceeded) {
            notifyStatsChanged()
        }
    }

    suspend fun loadSummary(
        range: StatsTimeRange,
        songs: List<Song>,
        nowMillis: Long = System.currentTimeMillis(),
        /** Inclusive start/end millis of a custom range; [range] should be ALL when set. */
        customBounds: Pair<Long, Long>? = null
    ): PlaybackStatsSummary = withContext(Dispatchers.IO) {
        val zoneId = ZoneId.systemDefault()
        val allEvents = readEvents()
        buildSummaryFromEvents(
            range = range,
            songs = songs,
            nowMillis = nowMillis,
            allEvents = allEvents,
            zoneId = zoneId,
            customBounds = customBounds
        )
    }

    internal fun buildSummaryFromEvents(
        range: StatsTimeRange,
        songs: List<Song>,
        nowMillis: Long,
        allEvents: List<PlaybackEvent>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        customBounds: Pair<Long, Long>? = null
    ): PlaybackStatsSummary {
        val (startBound, endBound) = if (customBounds != null) {
            Pair<Long?, Long>(customBounds.first, customBounds.second)
        } else {
            range.resolveBounds(allEvents, nowMillis, zoneId)
        }
        val filteredEvents = allEvents.mapNotNull { event ->
            val start = event.startMillis()
            val end = event.endMillis()
            val lowerBound = startBound ?: Long.MIN_VALUE
            if (end < lowerBound || start > endBound) {
                return@mapNotNull null
            }

            val clippedStart = max(start, lowerBound)
            val clippedEnd = min(end, endBound)
            val clippedDuration = (clippedEnd - clippedStart).coerceAtLeast(0L)
            if (clippedDuration <= 0L) {
                return@mapNotNull null
            }

            event.copy(
                timestamp = clippedEnd,
                durationMs = clippedDuration,
                startTimestamp = clippedStart,
                endTimestamp = clippedEnd
            )
        }

        val songMap = songs.associateBy { it.id }
        val normalizedEvents = filteredEvents

        val segmentsBySong = normalizedEvents
            .groupBy { it.songId }
            .mapValues { (_, eventsForSong) -> mergeSongEvents(eventsForSong) }

        val overallSpans = mergeSpans(segmentsBySong.values.flatten().map { PlaybackSpan(it.startMillis, it.endMillis) })

        val effectiveStart = startBound
            ?: overallSpans.minOfOrNull { it.startMillis }
            ?: normalizedEvents.minOfOrNull { it.startMillis() }
            ?: allEvents.minOfOrNull { it.startMillis() }
        val effectiveEnd = overallSpans.maxOfOrNull { it.endMillis } ?: endBound

        val totalDuration = overallSpans.sumOf { it.durationMs }
        val totalPlays = segmentsBySong.values.sumOf { it.size }
        val uniqueSongs = segmentsBySong.keys.size

        val allSongs = segmentsBySong
            .mapNotNull { (songId, segmentsForSong) ->
                val song = songMap[songId] ?: return@mapNotNull null
                val title = song.title.takeIf { it.isNotBlank() }
                    ?: song.path.substringAfterLast('/').ifBlank { return@mapNotNull null }
                val artist = song.displayArtist.takeIf { it.isNotBlank() } ?: "Unknown Artist"
                SongPlaybackSummary(
                    songId = songId,
                    title = title,
                    artist = artist,
                    albumArtUri = song.albumArtUriString,
                    totalDurationMs = segmentsForSong.sumOf { it.durationMs },
                    playCount = segmentsForSong.size
                )
            }
            .sortedWith(
                compareByDescending<SongPlaybackSummary> { it.totalDurationMs }
                    .thenByDescending { it.playCount }
            )
            .take(MAX_SONG_STATS_COUNT)
        val topSongs = allSongs.take(5)

        val topGenres = segmentsBySong.entries
            .groupBy { (songId, _) ->
                val genre = songMap[songId]?.genre
                if (genre.isNullOrBlank()) "Unknown Genre" else genre
            }
            .map { (genre, groupedSongs) ->
                val flattened = groupedSongs.flatMap { it.value }
                val uniqueArtists = groupedSongs
                    .flatMap { (songId, _) ->
                        statsArtistNames(songMap[songId])
                    }
                    .distinctBy { it.normalizedArtistKey() }
                    .size
                GenrePlaybackSummary(
                    genre = genre,
                    totalDurationMs = flattened.sumOf { it.durationMs },
                    playCount = flattened.size,
                    uniqueArtists = uniqueArtists
                )
            }
            .sortedWith(
                compareByDescending<GenrePlaybackSummary> { it.totalDurationMs }
                    .thenByDescending { it.playCount }
            )
            .take(5)

        var daySpan = 1L
        val averageDailyDuration = if (effectiveStart != null) {
            val startInstant = Instant.ofEpochMilli(effectiveStart)
            val endInstant = Instant.ofEpochMilli(effectiveEnd)
            val startDate = startInstant.atZone(zoneId).toLocalDate()
            val endDate = endInstant.atZone(zoneId).toLocalDate()
            daySpan = max(1L, java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1)
            if (daySpan > 0) totalDuration / daySpan else totalDuration
        } else {
            totalDuration
        }

        val daySlices = overallSpans.flatMap { sliceSpanByDay(it, zoneId) }
        val eventsByDay = daySlices.groupBy { it.date }
        val activeDays = eventsByDay.size
        val sortedDays = eventsByDay.keys.sorted()
        var longestStreak = 0
        var currentStreak = 0
        var lastDay: java.time.LocalDate? = null
        sortedDays.forEach { day ->
            if (lastDay == null || day == lastDay.plusDays(1)) {
                currentStreak += 1
            } else {
                currentStreak = 1
            }
            if (currentStreak > longestStreak) {
                longestStreak = currentStreak
            }
            lastDay = day
        }

        val sessions = computeListeningSessions(overallSpans)
        val totalSessions = sessions.size
        val totalSessionDuration = sessions.sumOf { it.totalDuration }
        val averageSessionDuration = if (totalSessions > 0) totalSessionDuration / totalSessions else 0L
        val longestSessionDuration = sessions.maxOfOrNull { it.totalDuration } ?: 0L
        val averageSessionsPerDay = if (daySpan > 0) totalSessions.toDouble() / daySpan else 0.0

        val timelineBuckets = if (customBounds != null) {
            createCustomBuckets(zoneId, customBounds.first, customBounds.second)
        } else createTimelineBuckets(
            range = range,
            zoneId = zoneId,
            now = Instant.ofEpochMilli(endBound),
            spans = overallSpans,
            fallbackStart = effectiveStart ?: endBound
        )
        val timelineEntries = accumulateTimelineEntries(timelineBuckets, overallSpans)

        val allArtists = segmentsBySong.entries
            .flatMap { (songId, segmentsForSong) ->
                statsArtistNames(songMap[songId]).map { artist ->
                    ArtistSongPlayback(
                        artist = artist,
                        songId = songId,
                        segments = segmentsForSong
                    )
                }
            }
            .groupBy { it.artist }
            .map { (artist, artistSongs) ->
                val flattened = artistSongs.flatMap { it.segments }
                val uniqueSongCount = artistSongs
                    .map { it.songId }
                    .toSet()
                    .size
                val topSongArt = artistSongs
                    .maxByOrNull { songPlayback -> songPlayback.segments.sumOf { it.durationMs } }
                    ?.let { songMap[it.songId]?.albumArtUriString }
                ArtistPlaybackSummary(
                    artist = artist,
                    totalDurationMs = flattened.sumOf { it.durationMs },
                    playCount = flattened.size,
                    uniqueSongs = uniqueSongCount,
                    albumArtUri = topSongArt
                )
            }
            .sortedWith(
                compareByDescending<ArtistPlaybackSummary> { it.totalDurationMs }
                    .thenByDescending { it.playCount }
            )
        val topArtists = allArtists.take(5)

        val allAlbums = segmentsBySong.entries
            .groupBy { (songId, _) ->
                val song = songMap[songId]
                song?.album?.takeIf { it.isNotBlank() } ?: "Unknown Album"
            }
            .map { (album, groupedSongs) ->
                val flattened = groupedSongs.flatMap { it.value }
                val uniqueSongCount = groupedSongs.size
                val firstSong = groupedSongs
                    .asSequence()
                    .mapNotNull { songMap[it.key] }
                    .firstOrNull()
                AlbumPlaybackSummary(
                    album = album,
                    albumArtUri = firstSong?.albumArtUriString,
                    totalDurationMs = flattened.sumOf { it.durationMs },
                    playCount = flattened.size,
                    uniqueSongs = uniqueSongCount
                )
            }
            .sortedWith(
                compareByDescending<AlbumPlaybackSummary> { it.totalDurationMs }
                    .thenByDescending { it.playCount }
            )
        val topAlbums = allAlbums.take(5)

        val peakTimeline = timelineEntries
            .filter { it.totalDurationMs > 0L }
            .maxByOrNull { it.totalDurationMs }

        val durationsByDayOfWeek = daySlices.groupBy { slice ->
            slice.date.dayOfWeek
        }
        val peakDay = durationsByDayOfWeek.maxByOrNull { entry ->
            entry.value.sumOf { it.durationMs }
        }
        val peakDayLabel = peakDay?.key?.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val peakDayDuration = peakDay?.value?.sumOf { it.durationMs } ?: 0L
        val dayListeningDistribution = if (range == StatsTimeRange.DAY || range == StatsTimeRange.WEEK) {
            computeDayListeningDistribution(
                spans = overallSpans,
                zoneId = zoneId,
                range = range,
                startBound = startBound,
                endBound = endBound
            )
        } else null

        val extras = computeExtras(
            range = range,
            isCustom = customBounds != null,
            nowMillis = nowMillis,
            allEvents = allEvents,
            songMap = songMap,
            segmentsBySong = segmentsBySong,
            overallSpans = overallSpans,
            daySlices = daySlices,
            startBound = startBound,
            endBound = endBound,
            effectiveStart = effectiveStart,
            timelineBuckets = timelineBuckets,
            allArtists = allArtists,
            zoneId = zoneId
        )

        return PlaybackStatsSummary(
            range = range,
            startTimestamp = startBound,
            endTimestamp = endBound,
            totalDurationMs = totalDuration,
            totalPlayCount = totalPlays,
            uniqueSongs = uniqueSongs,
            averageDailyDurationMs = averageDailyDuration,
            songs = allSongs,
            topSongs = topSongs,
            timeline = timelineEntries,
            topGenres = topGenres,
            topArtists = topArtists,
            topAlbums = topAlbums,
            activeDays = activeDays,
            longestStreakDays = longestStreak,
            totalSessions = totalSessions,
            averageSessionDurationMs = averageSessionDuration,
            longestSessionDurationMs = longestSessionDuration,
            averageSessionsPerDay = averageSessionsPerDay,
            dayListeningDistribution = dayListeningDistribution,
            peakTimeline = peakTimeline,
            peakDayLabel = peakDayLabel,
            peakDayDurationMs = peakDayDuration,
            uniqueArtists = extras.uniqueArtists,
            currentStreakDays = extras.currentStreakDays,
            previous = extras.previous,
            hourlyDurationsMs = extras.hourly,
            weekdayHourlyDurationsMs = extras.weekdayHourly,
            weekendHourlyDurationsMs = extras.weekendHourly,
            dailyTotals = extras.dailyTotals,
            discoveries = extras.discoveries,
            onRepeat = extras.onRepeat,
            monthlyTopArtists = extras.monthlyTopArtists,
            albumGrid = allAlbums.take(9),
            topArtistAllTimePlays = extras.topArtistAllTimePlays,
            activeDaysThisYear = extras.activeDaysThisYear,
            isCustomRange = customBounds != null
        )
    }

    private data class SummaryExtras(
        val uniqueArtists: Int,
        val currentStreakDays: Int,
        val previous: PeriodComparison?,
        val hourly: List<Long>,
        val weekdayHourly: List<Long>,
        val weekendHourly: List<Long>,
        val dailyTotals: List<DailyTotal>,
        val discoveries: Discoveries?,
        val onRepeat: OnRepeatSong?,
        val monthlyTopArtists: List<MonthTopArtist>,
        val topArtistAllTimePlays: Int,
        val activeDaysThisYear: Int
    )

    /**
     * Everything the redesigned Stats screen adds on top of the original summary. Kept apart from
     * [buildSummaryFromEvents] so the original numbers are computed exactly as before.
     */
    private fun computeExtras(
        range: StatsTimeRange,
        isCustom: Boolean,
        nowMillis: Long,
        allEvents: List<PlaybackEvent>,
        songMap: Map<String, Song>,
        segmentsBySong: Map<String, List<PlaybackSegment>>,
        overallSpans: List<PlaybackSpan>,
        daySlices: List<DaySlice>,
        startBound: Long?,
        endBound: Long,
        effectiveStart: Long?,
        timelineBuckets: List<TimelineBucket>,
        allArtists: List<ArtistPlaybackSummary>,
        zoneId: ZoneId
    ): SummaryExtras {
        // Artist keys per song, computed once (statsArtistNames allocates).
        val artistKeysBySong = HashMap<String, List<Pair<String, String>>>()
        fun artistsOf(songId: String): List<Pair<String, String>> = artistKeysBySong.getOrPut(songId) {
            statsArtistNames(songMap[songId])
                .map { it.normalizedArtistKey() to it }
                .distinctBy { it.first }
        }
        val unknownKey = UNKNOWN_ARTIST.normalizedArtistKey()

        val uniqueArtists = segmentsBySong.keys
            .flatMap { songId -> artistsOf(songId).map { it.first } }
            .toSet()
            .size

        // Streak and yearly active days use all history, relative to now (not to the range end).
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val playedDays = HashSet<LocalDate>()
        for (event in allEvents) {
            if (event.durationMs <= 0L) continue
            playedDays += Instant.ofEpochMilli(event.startMillis()).atZone(zoneId).toLocalDate()
            playedDays += Instant.ofEpochMilli(event.endMillis()).atZone(zoneId).toLocalDate()
        }
        var streakDay = if (today in playedDays) today else today.minusDays(1)
        var currentStreak = 0
        while (streakDay in playedDays) {
            currentStreak++
            streakDay = streakDay.minusDays(1)
        }
        val activeDaysThisYear = playedDays.count { it.year == today.year && !it.isAfter(today) }

        // Listening clock.
        val hourly = LongArray(24)
        val weekdayHourly = LongArray(24)
        val weekendHourly = LongArray(24)
        overallSpans.forEach { span ->
            var cursor = span.startMillis
            while (cursor < span.endMillis) {
                val zoned = Instant.ofEpochMilli(cursor).atZone(zoneId)
                val nextHour = zoned.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
                val sliceEnd = min(span.endMillis, max(nextHour, cursor + 1L))
                val sliceMs = sliceEnd - cursor
                val hour = zoned.hour
                hourly[hour] += sliceMs
                val isWeekend = zoned.dayOfWeek == DayOfWeek.SATURDAY || zoned.dayOfWeek == DayOfWeek.SUNDAY
                if (isWeekend) weekendHourly[hour] += sliceMs else weekdayHourly[hour] += sliceMs
                cursor = sliceEnd
            }
        }
        val hasClock = hourly.any { it > 0L }

        // Calendar.
        val byDate = daySlices.groupBy { it.date }.mapValues { (_, slices) -> slices.sumOf { it.durationMs } }
        val firstDate = (startBound ?: effectiveStart)?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
        val lastDate = Instant.ofEpochMilli(endBound).atZone(zoneId).toLocalDate()
        val dailyTotals = if (firstDate == null || firstDate.isAfter(lastDate)) {
            emptyList()
        } else {
            val boundedFirst = maxOf(firstDate, lastDate.minusDays(MAX_DAILY_TOTAL_DAYS - 1))
            generateSequence(boundedFirst) { it.plusDays(1) }
                .takeWhile { !it.isAfter(lastDate) }
                .map { DailyTotal(it, byDate[it] ?: 0L) }
                .toList()
        }

        // Previous period.
        val shift = previousPeriodShift(range, isCustom, startBound, endBound, zoneId)
        val previous = if (shift != null && startBound != null) {
            val prevStart = shift(startBound)
            val prevEnd = shift(endBound)
            if (prevEnd > prevStart) {
                val shiftedBuckets = timelineBuckets.map { bucket ->
                    bucket.copy(startMillis = shift(bucket.startMillis), endMillis = shift(bucket.endMillis))
                }
                buildPeriodComparison(allEvents, songMap, prevStart, prevEnd, shiftedBuckets, ::artistsOf)
            } else null
        } else null

        // Discoveries: first play ever (within kept history) falls inside this range.
        val historyStart = allEvents.minOfOrNull { it.startMillis() }
        val discoveries = if (
            startBound != null &&
            (range != StatsTimeRange.ALL || isCustom) &&
            historyStart != null && historyStart < startBound
        ) {
            val firstPlayBySong = HashMap<String, Long>()
            for (event in allEvents) {
                if (event.durationMs <= 0L) continue
                val start = event.startMillis()
                val current = firstPlayBySong[event.songId]
                if (current == null || start < current) firstPlayBySong[event.songId] = start
            }
            val firstPlayByArtist = HashMap<String, Long>()
            val artistName = HashMap<String, String>()
            firstPlayBySong.forEach { (songId, first) ->
                artistsOf(songId).forEach { (key, name) ->
                    if (key == unknownKey) return@forEach
                    val current = firstPlayByArtist[key]
                    if (current == null || first < current) firstPlayByArtist[key] = first
                    artistName.putIfAbsent(key, name)
                }
            }
            val songDuration = segmentsBySong.mapValues { (_, segments) -> segments.sumOf { it.durationMs } }
            val newSongIds = segmentsBySong.keys
                .filter { (firstPlayBySong[it] ?: Long.MIN_VALUE) >= startBound }
                .sortedByDescending { songDuration[it] ?: 0L }
            val playsByArtist = HashMap<String, Int>()
            val artByArtist = HashMap<String, String?>()
            segmentsBySong.forEach { (songId, segments) ->
                artistsOf(songId).forEach { (key, _) ->
                    playsByArtist[key] = (playsByArtist[key] ?: 0) + segments.size
                    if (!artByArtist.containsKey(key)) artByArtist[key] = songMap[songId]?.albumArtUriString
                }
            }
            val newArtistKeys = firstPlayByArtist
                .filter { (key, first) -> first >= startBound && first <= endBound && (playsByArtist[key] ?: 0) > 0 }
                .keys
            Discoveries(
                newSongs = newSongIds.size,
                newArtists = newArtistKeys.size,
                newDurationMs = newSongIds.sumOf { songDuration[it] ?: 0L },
                artists = newArtistKeys
                    .map { key ->
                        NewArtistFind(
                            artist = artistName[key] ?: key,
                            firstPlayedAt = firstPlayByArtist[key] ?: startBound,
                            playCount = playsByArtist[key] ?: 0,
                            albumArtUri = artByArtist[key]
                        )
                    }
                    .sortedByDescending { it.playCount }
                    .take(MAX_DISCOVERY_ARTISTS),
                newSongIds = newSongIds.take(MAX_NEW_SONG_IDS)
            )
        } else null

        // On repeat: most counted plays in the last few days, relative to now.
        val repeatWindowStart = nowMillis - TimeUnit.DAYS.toMillis(ON_REPEAT_WINDOW_DAYS)
        val recentPlays = HashMap<String, Int>()
        for (event in allEvents) {
            if (event.durationMs < MIN_COUNTED_PLAY_MS) continue
            val end = event.endMillis()
            if (end in repeatWindowStart..nowMillis) {
                recentPlays[event.songId] = (recentPlays[event.songId] ?: 0) + 1
            }
        }
        val onRepeat = recentPlays.entries
            .filter { it.value >= ON_REPEAT_MIN_PLAYS && songMap.containsKey(it.key) }
            .maxByOrNull { it.value }
            ?.let { (songId, plays) ->
                val song = songMap.getValue(songId)
                val daily = IntArray(ON_REPEAT_DAILY_DAYS)
                for (event in allEvents) {
                    if (event.songId != songId || event.durationMs < MIN_COUNTED_PLAY_MS) continue
                    val date = Instant.ofEpochMilli(event.endMillis()).atZone(zoneId).toLocalDate()
                    val daysAgo = ChronoUnit.DAYS.between(date, today)
                    if (daysAgo >= 0L && daysAgo < ON_REPEAT_DAILY_DAYS) {
                        daily[(ON_REPEAT_DAILY_DAYS - 1 - daysAgo).toInt()]++
                    }
                }
                OnRepeatSong(
                    songId = songId,
                    title = song.title.ifBlank { song.path.substringAfterLast('/') },
                    artist = song.displayArtist.ifBlank { UNKNOWN_ARTIST },
                    albumArtUri = song.albumArtUriString,
                    recentPlays = plays,
                    dailyPlays = daily.toList()
                )
            }

        // #1 artist per month.
        val currentMonth = YearMonth.from(Instant.ofEpochMilli(endBound).atZone(zoneId))
        val months: List<YearMonth> = when {
            isCustom -> emptyList()
            range == StatsTimeRange.YEAR -> (1..currentMonth.monthValue).map { YearMonth.of(currentMonth.year, it) }
            range == StatsTimeRange.ALL -> (11 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
            else -> emptyList()
        }
        val monthlyTopArtists = if (months.isEmpty()) emptyList() else {
            val monthSet = months.toSet()
            val perMonth = HashMap<YearMonth, HashMap<String, Long>>()
            val names = HashMap<String, String>()
            segmentsBySong.forEach { (songId, segments) ->
                val artists = artistsOf(songId).filter { it.first != unknownKey }
                if (artists.isEmpty()) return@forEach
                segments.forEach { segment ->
                    val month = YearMonth.from(Instant.ofEpochMilli(segment.startMillis).atZone(zoneId))
                    if (month !in monthSet) return@forEach
                    val bucket = perMonth.getOrPut(month) { HashMap() }
                    artists.forEach { (key, name) ->
                        bucket[key] = (bucket[key] ?: 0L) + segment.durationMs
                        names.putIfAbsent(key, name)
                    }
                }
            }
            val artByKey = allArtists.associate { it.artist.normalizedArtistKey() to it.albumArtUri }
            months.mapNotNull { month ->
                val best = perMonth[month]?.maxByOrNull { it.value } ?: return@mapNotNull null
                MonthTopArtist(
                    label = month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    artist = names[best.key] ?: best.key,
                    durationMs = best.value,
                    albumArtUri = artByKey[best.key]
                )
            }
        }

        // All-time plays of this range's #1 artist.
        val topKey = allArtists.firstOrNull()?.artist?.normalizedArtistKey()
        val topArtistAllTimePlays = if (topKey == null || topKey == unknownKey) 0 else {
            allEvents.count { event ->
                event.durationMs >= MIN_COUNTED_PLAY_MS && artistsOf(event.songId).any { it.first == topKey }
            }
        }

        return SummaryExtras(
            uniqueArtists = uniqueArtists,
            currentStreakDays = currentStreak,
            previous = previous,
            hourly = if (hasClock) hourly.toList() else emptyList(),
            weekdayHourly = if (hasClock) weekdayHourly.toList() else emptyList(),
            weekendHourly = if (hasClock) weekendHourly.toList() else emptyList(),
            dailyTotals = dailyTotals,
            discoveries = discoveries,
            onRepeat = onRepeat,
            monthlyTopArtists = monthlyTopArtists,
            topArtistAllTimePlays = topArtistAllTimePlays,
            activeDaysThisYear = activeDaysThisYear
        )
    }

    private fun buildPeriodComparison(
        allEvents: List<PlaybackEvent>,
        songMap: Map<String, Song>,
        start: Long,
        end: Long,
        buckets: List<TimelineBucket>,
        artistsOf: (String) -> List<Pair<String, String>>
    ): PeriodComparison {
        val clipped = clipEvents(allEvents, start, end)
        val segments = clipped.groupBy { it.songId }.mapValues { (_, events) -> mergeSongEvents(events) }
        val spans = mergeSpans(segments.values.flatten().map { PlaybackSpan(it.startMillis, it.endMillis) })
        val artistDurations = HashMap<String, Long>()
        segments.forEach { (songId, songSegments) ->
            val duration = songSegments.sumOf { it.durationMs }
            artistsOf(songId).forEach { (key, _) ->
                artistDurations[key] = (artistDurations[key] ?: 0L) + duration
            }
        }
        val songRanks = segments.entries
            .sortedByDescending { entry -> entry.value.sumOf { it.durationMs } }
            .take(MAX_SONG_STATS_COUNT)
            .mapIndexed { index, entry -> entry.key to index + 1 }
            .toMap()
        val artistRanks = artistDurations.entries
            .sortedByDescending { it.value }
            .take(MAX_SONG_STATS_COUNT)
            .mapIndexed { index, entry -> entry.key to index + 1 }
            .toMap()
        return PeriodComparison(
            startMillis = start,
            endMillis = end,
            totalDurationMs = spans.sumOf { it.durationMs },
            totalPlayCount = segments.values.sumOf { it.size },
            uniqueSongs = segments.size,
            uniqueArtists = artistDurations.size,
            timeline = accumulateTimelineEntries(buckets, spans),
            songRanks = songRanks,
            artistRanks = artistRanks
        )
    }

    /** Events overlapping [lower, upper], clipped to it (same rules as [buildSummaryFromEvents]). */
    private fun clipEvents(allEvents: List<PlaybackEvent>, lower: Long, upper: Long): List<PlaybackEvent> {
        return allEvents.mapNotNull { event ->
            val start = event.startMillis()
            val end = event.endMillis()
            if (end < lower || start > upper) return@mapNotNull null
            val clippedStart = max(start, lower)
            val clippedEnd = min(end, upper)
            val clippedDuration = (clippedEnd - clippedStart).coerceAtLeast(0L)
            if (clippedDuration <= 0L) return@mapNotNull null
            event.copy(
                timestamp = clippedEnd,
                durationMs = clippedDuration,
                startTimestamp = clippedStart,
                endTimestamp = clippedEnd
            )
        }
    }

    /** Maps a timestamp to the same moment one period earlier, or null when there is no previous period. */
    private fun previousPeriodShift(
        range: StatsTimeRange,
        isCustom: Boolean,
        startBound: Long?,
        endBound: Long,
        zoneId: ZoneId
    ): ((Long) -> Long)? {
        if (startBound == null) return null
        if (isCustom) {
            val length = (endBound - startBound + 1L).coerceAtLeast(1L)
            return { it - length }
        }
        val step: (java.time.ZonedDateTime) -> java.time.ZonedDateTime = when (range) {
            StatsTimeRange.DAY -> { zoned -> zoned.minusDays(1) }
            StatsTimeRange.WEEK -> { zoned -> zoned.minusWeeks(1) }
            StatsTimeRange.MONTH -> { zoned -> zoned.minusMonths(1) }
            StatsTimeRange.YEAR -> { zoned -> zoned.minusYears(1) }
            StatsTimeRange.ALL -> return null
        }
        return { millis -> step(Instant.ofEpochMilli(millis).atZone(zoneId)).toInstant().toEpochMilli() }
    }

    /** Timeline buckets for a custom range: days (up to 2 weeks), weeks (up to ~4 months), else months. */
    private fun createCustomBuckets(zoneId: ZoneId, start: Long, end: Long): List<TimelineBucket> {
        val startDate = Instant.ofEpochMilli(start).atZone(zoneId).toLocalDate()
        val endDate = Instant.ofEpochMilli(end).atZone(zoneId).toLocalDate()
        if (endDate.isBefore(startDate)) return emptyList()
        val dayCount = ChronoUnit.DAYS.between(startDate, endDate) + 1
        val dayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val monthFormatter = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())
        fun bucket(label: String, from: LocalDate, toExclusive: LocalDate) = TimelineBucket(
            label = label,
            startMillis = max(from.atStartOfDay(zoneId).toInstant().toEpochMilli(), start),
            endMillis = min(toExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli(), end + 1L),
            inclusiveEnd = false
        )
        return when {
            dayCount <= 14 -> (0 until dayCount).map { offset ->
                val day = startDate.plusDays(offset)
                bucket(dayFormatter.format(day), day, day.plusDays(1))
            }
            dayCount <= 120 -> generateSequence(startDate) { it.plusDays(7) }
                .takeWhile { !it.isAfter(endDate) }
                .map { weekStart -> bucket(dayFormatter.format(weekStart), weekStart, minOf(weekStart.plusDays(7), endDate.plusDays(1))) }
                .toList()
            else -> generateSequence(YearMonth.from(startDate)) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(YearMonth.from(endDate)) }
                .map { month ->
                    val from = maxOf(month.atDay(1), startDate)
                    val to = minOf(month.atEndOfMonth().plusDays(1), endDate.plusDays(1))
                    bucket(monthFormatter.format(month), from, to)
                }
                .toList()
        }
    }

    suspend fun exportEventsForBackup(): List<PlaybackEvent> = withContext(Dispatchers.IO) {
        readEvents().map { event -> sanitizeEvent(event) }
    }

    suspend fun loadPlaybackHistory(limit: Int = DEFAULT_PLAYBACK_HISTORY_LIMIT): List<PlaybackHistoryEntry> = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext emptyList()
        val safeLimit = limit.coerceAtMost(MAX_PLAYBACK_HISTORY_LIMIT)
        readEvents()
            .asSequence()
            .sortedByDescending { event -> event.timestamp }
            .take(safeLimit)
            .map { event ->
                PlaybackHistoryEntry(
                    songId = event.songId,
                    timestamp = event.timestamp.coerceAtLeast(0L)
                )
            }
            .toList()
    }

    /**
     * Every recorded play (about two years), newest first, for Home's discovery shelves
     * (Forgotten favourites, On this day, the time-of-day mix). Unlike [loadPlaybackHistory] it
     * isn't capped by count, so heavy listeners still get plays from months and years ago.
     */
    suspend fun loadFullPlaybackHistory(): List<PlaybackHistoryEntry> = withContext(Dispatchers.IO) {
        readEvents()
            .asSequence()
            .sortedByDescending { event -> event.timestamp }
            .map { event ->
                PlaybackHistoryEntry(
                    songId = event.songId,
                    timestamp = event.timestamp.coerceAtLeast(0L)
                )
            }
            .toList()
    }

    suspend fun importEventsFromBackup(
        events: List<PlaybackEvent>,
        clearExisting: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val writeSucceeded = updateEventsAtomically { existingEvents ->
            val base = if (clearExisting) {
                emptyList()
            } else {
                existingEvents
            }
            val merged = (base + events)
                .map { event -> sanitizeEvent(event) }
                .distinctBy { event ->
                    "${event.songId}:${event.startMillis()}:${event.endMillis()}:${event.durationMs}"
                }
                .sortedBy { event -> event.timestamp }
                .toMutableList()
            merged
        }
        if (writeSucceeded) {
            notifyStatsChanged()
        }
    }

    /** Your listening to one artist, from the play history of their song ids. */
    data class ArtistListening(
        /** Plays (≥ [MIN_COUNTED_PLAY_MS]) per song id. */
        val playsBySongId: Map<String, Int>,
        val totalDurationMs: Long,
        val totalPlays: Int,
        val firstPlayedAt: Long?,
        val lastPlayedAt: Long?,
        /** Consecutive days, ending today or yesterday, with at least one play. */
        val currentStreakDays: Int,
        /** This artist's share of everything you listened to (0..1). */
        val shareOfListening: Float
    )

    /**
     * Scans the (already cached) play history once for [songIds]. Cheap enough for a screen:
     * no summaries, timelines or song lookups are built.
     */
    suspend fun loadArtistListening(
        songIds: Set<String>,
        nowMillis: Long = System.currentTimeMillis()
    ): ArtistListening = withContext(Dispatchers.IO) {
        val plays = HashMap<String, Int>()
        var total = 0L
        var allTotal = 0L
        var count = 0
        var first: Long? = null
        var last: Long? = null
        val zoneId = ZoneId.systemDefault()
        val days = HashSet<Long>()
        for (event in readEvents()) {
            val duration = event.durationMs.coerceAtLeast(0L)
            allTotal += duration
            if (event.songId !in songIds) continue
            total += duration
            if (duration >= MIN_COUNTED_PLAY_MS) {
                plays[event.songId] = (plays[event.songId] ?: 0) + 1
                count++
            }
            val at = event.timestamp.coerceAtLeast(0L)
            if (first == null || at < first) first = at
            if (last == null || at > last) last = at
            days += Instant.ofEpochMilli(at).atZone(zoneId).toLocalDate().toEpochDay()
        }
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().toEpochDay()
        var day = if (today in days) today else today - 1
        var streak = 0
        while (day in days) {
            streak++
            day--
        }
        ArtistListening(
            playsBySongId = plays,
            totalDurationMs = total,
            totalPlays = count,
            firstPlayedAt = first,
            lastPlayedAt = last,
            currentStreakDays = streak,
            shareOfListening = if (allTotal > 0) (total.toDouble() / allTotal).toFloat() else 0f
        )
    }

    fun requestRefresh() {
        notifyStatsChanged()
    }

    private fun readEvents(): List<PlaybackEvent> {
        synchronized(fileLock) { cachedEvents }?.let { return it }
        val raw = synchronized(fileLock) { readRawHistoryLocked() }
        return parseEvents(raw).also { parsed ->
            synchronized(fileLock) { if (cachedEvents == null) cachedEvents = parsed }
        }
    }

    private fun readRawHistoryLocked(): String? {
        return runCatching {
            atomicHistoryFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
            .onFailure { throwable ->
                if (throwable !is FileNotFoundException) {
                    Timber.e(throwable, "Failed reading playback history")
                }
            }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseEvents(raw: String?): MutableList<PlaybackEvent> {
        if (raw.isNullOrBlank()) {
            return mutableListOf()
        }

        return runCatching {
            val element = gson.fromJson(raw, JsonElement::class.java)
            parseElement(element)
        }.getOrElse { throwable ->
            Timber.e(throwable, "Failed parsing playback history")
            mutableListOf()
        }
    }

    private fun parseElement(element: JsonElement?): MutableList<PlaybackEvent> {
        if (element == null || element.isJsonNull) return mutableListOf()
        if (element.isJsonArray) {
            val parsed: MutableList<PlaybackEvent> = gson.fromJson(element, eventsType)
            return parsed.mapTo(mutableListOf()) { sanitizeEvent(it) }
        }
        return mutableListOf()
    }

    private fun sanitizeEvent(event: PlaybackEvent): PlaybackEvent {
        val safeDuration = event.durationMs.coerceAtLeast(0L)
        val safeEnd = (event.endTimestamp ?: event.timestamp).coerceAtLeast(0L)
        val safeStart = when {
            event.startTimestamp != null -> event.startTimestamp.coerceIn(0L, safeEnd)
            safeDuration > 0L -> (safeEnd - safeDuration).coerceAtLeast(0L)
            else -> safeEnd
        }
        val normalizedDuration = (safeEnd - safeStart).coerceAtLeast(0L)
        val finalDuration = when {
            event.startTimestamp == null && safeDuration in 1 until normalizedDuration -> safeDuration
            else -> normalizedDuration
        }
        val finalStart = (safeEnd - finalDuration).coerceAtLeast(0L)
        return event.copy(
            songId = event.songId,
            timestamp = safeEnd,
            durationMs = finalDuration,
            startTimestamp = finalStart,
            endTimestamp = safeEnd
        )
    }

    private fun mergeSongEvents(events: List<PlaybackEvent>): List<PlaybackSegment> {
        if (events.isEmpty()) return emptyList()
        val sorted = events.sortedBy { it.startMillis() }
        val songId = sorted.first().songId
        val segments = mutableListOf<PlaybackSegment>()
        var currentStart = sorted.first().startMillis()
        var currentEnd = sorted.first().endMillis()
        for (index in 1 until sorted.size) {
            val event = sorted[index]
            val start = event.startMillis()
            val end = event.endMillis()
            if (start <= currentEnd + SEGMENT_JOIN_TOLERANCE_MS) {
                currentEnd = max(currentEnd, end)
            } else {
                segments += PlaybackSegment(songId, currentStart, currentEnd)
                currentStart = start
                currentEnd = end
            }
        }
        segments += PlaybackSegment(songId, currentStart, currentEnd)
        return segments
    }

    private fun mergeSpans(spans: List<PlaybackSpan>): List<PlaybackSpan> {
        if (spans.isEmpty()) return emptyList()
        val sorted = spans.sortedBy { it.startMillis }
        val merged = mutableListOf<PlaybackSpan>()
        var currentStart = sorted.first().startMillis
        var currentEnd = sorted.first().endMillis
        for (index in 1 until sorted.size) {
            val span = sorted[index]
            val start = span.startMillis
            val end = span.endMillis
            if (start <= currentEnd + SEGMENT_JOIN_TOLERANCE_MS) {
                currentEnd = max(currentEnd, end)
            } else {
                merged += PlaybackSpan(currentStart, currentEnd)
                currentStart = start
                currentEnd = end
            }
        }
        merged += PlaybackSpan(currentStart, currentEnd)
        return merged
    }

    private data class DaySlice(
        val date: LocalDate,
        val durationMs: Long
    )

    private data class ArtistSongPlayback(
        val artist: String,
        val songId: String,
        val segments: List<PlaybackSegment>
    )

    private fun statsArtistNames(song: Song?): List<String> {
        if (song == null) return listOf(UNKNOWN_ARTIST)

        val separatedArtists = song.artists
            .sortedByDescending { it.isPrimary }
            .map { it.name.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.normalizedArtistKey() }
        if (separatedArtists.isNotEmpty()) {
            return separatedArtists
        }

        val fallbackArtist = song.displayArtist.trim()
        return listOf(fallbackArtist.ifBlank { UNKNOWN_ARTIST })
    }

    private fun String.normalizedArtistKey(): String = trim().lowercase(Locale.ROOT)

    private fun sliceSpanByDay(span: PlaybackSpan, zoneId: ZoneId): List<DaySlice> {
        if (span.durationMs <= 0L) return emptyList()
        val slices = mutableListOf<DaySlice>()
        var cursor = span.startMillis
        val end = span.endMillis
        while (cursor < end) {
            val zoned = Instant.ofEpochMilli(cursor).atZone(zoneId)
            val dayStart = zoned.toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
            val nextDayStart = zoned.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val sliceEnd = min(end, nextDayStart)
            val sliceDuration = (sliceEnd - cursor).coerceAtLeast(0L)
            if (sliceDuration > 0L) {
                slices += DaySlice(zoned.toLocalDate(), sliceDuration)
            }
            cursor = sliceEnd
        }
        return slices
    }

    private fun computeDayListeningDistribution(
        spans: List<PlaybackSpan>,
        zoneId: ZoneId,
        range: StatsTimeRange,
        startBound: Long?,
        endBound: Long,
        bucketSizeMinutes: Int = 5
    ): DayListeningDistribution? {
        if (spans.isEmpty()) return null
        val bucketDurationMs = TimeUnit.MINUTES.toMillis(bucketSizeMinutes.toLong())
        val minutesPerDay = TimeUnit.DAYS.toMinutes(1)
        val bucketCount = (minutesPerDay / bucketSizeMinutes).toInt().coerceAtLeast(1)
        val totals = LongArray(bucketCount)
        val totalsByDay = mutableMapOf<LocalDate, LongArray>()
        spans.forEach { span ->
            var cursor = span.startMillis
            val end = span.endMillis
            while (cursor < end) {
                val zoned = Instant.ofEpochMilli(cursor).atZone(zoneId)
                val day = zoned.toLocalDate()
                val dayStart = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
                var bucketIndex = ((cursor - dayStart) / bucketDurationMs).toInt()
                if (bucketIndex >= bucketCount) {
                    bucketIndex = bucketCount - 1
                } else if (bucketIndex < 0) {
                    bucketIndex = 0
                }
                val bucketStart = dayStart + bucketIndex * bucketDurationMs
                val bucketEnd = min(end, bucketStart + bucketDurationMs)
                val contribution = (bucketEnd - cursor).coerceAtLeast(0L)
                if (contribution > 0L) {
                    totals[bucketIndex] += contribution
                    val dayTotals = totalsByDay.getOrPut(day) { LongArray(bucketCount) }
                    dayTotals[bucketIndex] += contribution
                }
                cursor = if (bucketEnd > cursor) bucketEnd else end
            }
        }
        val buckets = buildList {
            for (index in 0 until bucketCount) {
                val durationMs = totals[index]
                if (durationMs > 0L) {
                    add(
                        DailyListeningBucket(
                            startMinute = index * bucketSizeMinutes,
                            endMinuteExclusive = (index + 1) * bucketSizeMinutes,
                            totalDurationMs = durationMs
                        )
                    )
                }
            }
        }
        if (buckets.isEmpty()) return null
        val maxBucketDuration = buckets.maxOf { it.totalDurationMs }.coerceAtLeast(0L)

        val daySequence: List<LocalDate> = when (range) {
            StatsTimeRange.DAY -> {
                val anchor = startBound?.let { Instant.ofEpochMilli(it) }
                    ?: spans.minOfOrNull { Instant.ofEpochMilli(it.startMillis) }
                    ?: Instant.ofEpochMilli(endBound)
                listOf(anchor.atZone(zoneId).toLocalDate())
            }
            StatsTimeRange.WEEK -> {
                val anchor = startBound?.let { Instant.ofEpochMilli(it) }
                    ?: spans.minOfOrNull { Instant.ofEpochMilli(it.startMillis) }
                    ?: Instant.ofEpochMilli(endBound)
                val startDate = anchor.atZone(zoneId).toLocalDate()
                (0 until 7).map { offset -> startDate.plusDays(offset.toLong()) }
            }
            else -> totalsByDay.keys.sorted()
        }

        val days = daySequence.map { date ->
            val bucketTotals = totalsByDay[date]
            val dayBuckets = buildList {
                if (bucketTotals != null) {
                    for (index in 0 until bucketCount) {
                        val duration = bucketTotals[index]
                        if (duration > 0L) {
                            add(
                                DailyListeningBucket(
                                    startMinute = index * bucketSizeMinutes,
                                    endMinuteExclusive = (index + 1) * bucketSizeMinutes,
                                    totalDurationMs = duration
                                )
                            )
                        }
                    }
                }
            }
            val totalDuration = bucketTotals?.sumOf { it } ?: 0L
            DailyListeningDay(
                date = date,
                buckets = dayBuckets,
                totalDurationMs = totalDuration
            )
        }

        return DayListeningDistribution(
            bucketSizeMinutes = bucketSizeMinutes,
            buckets = buckets,
            maxBucketDurationMs = maxBucketDuration,
            days = days
        )
    }

    private data class ListeningSessionAggregate(
        var start: Long,
        var end: Long,
        var totalDuration: Long,
        var playCount: Int
    )

    private fun computeListeningSessions(spans: List<PlaybackSpan>): List<ListeningSessionAggregate> {
        if (spans.isEmpty()) return emptyList()
        val sorted = spans.sortedBy { it.startMillis }
        val sessions = mutableListOf<ListeningSessionAggregate>()

        var current = ListeningSessionAggregate(
            start = sorted.first().startMillis,
            end = sorted.first().endMillis,
            totalDuration = sorted.first().durationMs,
            playCount = 1
        )

        for (index in 1 until sorted.size) {
            val span = sorted[index]
            val spanStart = span.startMillis
            val spanEnd = span.endMillis
            val gap = spanStart - current.end
            if (gap <= sessionGapThresholdMs) {
                current.end = max(current.end, spanEnd)
                current.totalDuration += span.durationMs
                current.playCount += 1
            } else {
                sessions += current
                current = ListeningSessionAggregate(
                    start = spanStart,
                    end = spanEnd,
                    totalDuration = span.durationMs,
                    playCount = 1
                )
            }
        }

        sessions += current
        return sessions
    }

    private fun updateEventsAtomically(
        transform: (MutableList<PlaybackEvent>) -> MutableList<PlaybackEvent>
    ): Boolean {
        repeat(MAX_FILE_UPDATE_RETRIES) {
            val rawSnapshot = synchronized(fileLock) { readRawHistoryLocked() }
            val updatedEvents = transform(parseEvents(rawSnapshot))
            val payload = serializeEvents(updatedEvents)

            val writeSucceeded = synchronized(fileLock) {
                val latestRaw = readRawHistoryLocked()
                if (latestRaw != rawSnapshot) {
                    return@synchronized false
                }
                val result = writePayloadLocked(payload)
                if (result) cachedEvents = null
                result
            }
            if (writeSucceeded) {
                return true
            }
        }

        val fallbackRawSnapshot = synchronized(fileLock) { readRawHistoryLocked() }
        val payload = serializeEvents(transform(parseEvents(fallbackRawSnapshot)))
        return synchronized(fileLock) {
            val result = writePayloadLocked(payload)
            if (result) cachedEvents = null
            result
        }
    }

    private fun serializeEvents(events: List<PlaybackEvent>): ByteArray {
        val sanitized = events.map { sanitizeEvent(it) }
        return gson.toJson(sanitized).toByteArray(Charsets.UTF_8)
    }

    private fun writePayloadLocked(payload: ByteArray): Boolean {
        var outputStream: FileOutputStream? = null
        return runCatching {
            val parent = historyFile.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Timber.w("Unable to ensure playback history directory: ${parent.absolutePath}")
            }
            outputStream = atomicHistoryFile.startWrite()
            outputStream?.write(payload)
            outputStream?.fd?.sync()
            outputStream?.let { atomicHistoryFile.finishWrite(it) }
            outputStream = null
            true
        }.onFailure { throwable ->
            outputStream?.let { stream -> atomicHistoryFile.failWrite(stream) }
            Timber.e(throwable, "Failed to persist playback history")
        }.getOrDefault(false)
    }

    private fun accumulateTimelineEntries(
        buckets: List<TimelineBucket>,
        spans: List<PlaybackSpan>
    ): List<TimelineEntry> {
        if (buckets.isEmpty()) return emptyList()
        val durationByBucket = LongArray(buckets.size)
        val playCountByBucket = DoubleArray(buckets.size)
        spans.forEach { span ->
            val spanStart = span.startMillis
            val spanEnd = span.endMillis
            val spanDuration = span.durationMs
            if (spanDuration <= 0L) return@forEach
            buckets.forEachIndexed { index, bucket ->
                val bucketEndExclusive = if (bucket.inclusiveEnd) bucket.endMillis + 1 else bucket.endMillis
                val overlapStart = max(spanStart, bucket.startMillis)
                val overlapEnd = min(spanEnd, bucketEndExclusive)
                val overlap = (overlapEnd - overlapStart).coerceAtLeast(0L)
                if (overlap > 0) {
                    durationByBucket[index] += overlap
                    playCountByBucket[index] += overlap.toDouble() / spanDuration.toDouble()
                }
            }
        }
        return buckets.mapIndexed { index, bucket ->
            TimelineEntry(
                label = bucket.label,
                totalDurationMs = durationByBucket[index],
                playCount = playCountByBucket[index].roundToInt()
            )
        }
    }

    private fun createTimelineBuckets(
        range: StatsTimeRange,
        zoneId: ZoneId,
        now: Instant,
        spans: List<PlaybackSpan>,
        fallbackStart: Long
    ): List<TimelineBucket> {
        return when (range) {
            StatsTimeRange.DAY -> createDayBuckets(zoneId, now)
            StatsTimeRange.WEEK -> createWeekBuckets(zoneId, now)
            StatsTimeRange.MONTH -> createMonthBuckets(zoneId, now)
            StatsTimeRange.YEAR -> createYearBuckets(zoneId, now)
            StatsTimeRange.ALL -> createAllTimeBuckets(zoneId, spans, fallbackStart, now)
        }
    }

    private fun createDayBuckets(zoneId: ZoneId, now: Instant): List<TimelineBucket> {
        val dayStart = now.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()
        val formatter = DateTimeFormatter.ofPattern("ha", Locale.US)
        return (0 until 6).map { index ->
            val bucketStart = dayStart.plus(Duration.ofHours((index * 4).toLong()))
            val bucketEnd = bucketStart.plus(Duration.ofHours(4))
            val label = formatter.format(bucketStart.atZone(zoneId)).lowercase(Locale.US)
            TimelineBucket(
                label = label,
                startMillis = bucketStart.toEpochMilli(),
                endMillis = bucketEnd.toEpochMilli(),
                inclusiveEnd = false
            )
        }
    }

    private fun createWeekBuckets(zoneId: ZoneId, now: Instant): List<TimelineBucket> {
        val startOfWeek = now.atZone(zoneId)
            .toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0 until 7).map { index ->
            val day = startOfWeek.plusDays(index.toLong())
            val start = day.atStartOfDay(zoneId).toInstant()
            val end = day.plusDays(1).atStartOfDay(zoneId).toInstant()
            TimelineBucket(
                label = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                startMillis = start.toEpochMilli(),
                endMillis = end.toEpochMilli(),
                inclusiveEnd = false
            )
        }
    }

    private fun createMonthBuckets(zoneId: ZoneId, now: Instant): List<TimelineBucket> {
        val yearMonth = YearMonth.from(now.atZone(zoneId))
        val daysInMonth = yearMonth.lengthOfMonth()
        val bucketCount = 4
        return buildList {
            repeat(bucketCount) { index ->
                val startDay = index * 7 + 1
                if (startDay > daysInMonth) {
                    return@repeat
                }
                val endDay = if (index == bucketCount - 1) {
                    daysInMonth
                } else {
                    minOf(startDay + 6, daysInMonth)
                }
                val start = yearMonth.atDay(startDay).atStartOfDay(zoneId).toInstant()
                val end = yearMonth.atDay(endDay).plusDays(1).atStartOfDay(zoneId).toInstant()
                add(
                    TimelineBucket(
                        label = context.getString(com.theveloper.pixelplay.R.string.stats_week_label, index + 1),
                        startMillis = start.toEpochMilli(),
                        endMillis = end.toEpochMilli(),
                        inclusiveEnd = false
                    )
                )
            }
        }
    }

    private fun createYearBuckets(zoneId: ZoneId, now: Instant): List<TimelineBucket> {
        val year = Year.from(now.atZone(zoneId))
        return (1..12).map { monthIndex ->
            val start = year.atMonth(monthIndex).atDay(1).atStartOfDay(zoneId).toInstant()
            val end = year.atMonth(monthIndex).atEndOfMonth().plusDays(1).atStartOfDay(zoneId).toInstant()
            TimelineBucket(
                label = year.atMonth(monthIndex).month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                startMillis = start.toEpochMilli(),
                endMillis = end.toEpochMilli(),
                inclusiveEnd = false
            )
        }
    }

    private fun createAllTimeBuckets(
        zoneId: ZoneId,
        spans: List<PlaybackSpan>,
        fallbackStart: Long,
        now: Instant
    ): List<TimelineBucket> {
        val allSpans = if (spans.isEmpty()) listOf(PlaybackSpan(fallbackStart, fallbackStart)) else spans
        val minTimestamp = allSpans.minOfOrNull { it.startMillis } ?: fallbackStart
        val maxTimestamp = allSpans.maxOfOrNull { it.endMillis } ?: now.toEpochMilli()
        val startYear = Instant.ofEpochMilli(minTimestamp).atZone(zoneId).year
        val endYear = Instant.ofEpochMilli(maxTimestamp).atZone(zoneId).year
        if (startYear > endYear) return emptyList()
        return (startYear..endYear).map { yearValue ->
            val year = Year.of(yearValue)
            val start = year.atDay(1).atStartOfDay(zoneId).toInstant()
            val end = year.plusYears(1).atDay(1).atStartOfDay(zoneId).toInstant()
            TimelineBucket(
                label = yearValue.toString(),
                startMillis = start.toEpochMilli(),
                endMillis = end.toEpochMilli(),
                inclusiveEnd = false
            )
        }
    }

    private fun StatsTimeRange.resolveBounds(
        events: List<PlaybackEvent>,
        nowMillis: Long,
        zoneId: ZoneId
    ): Pair<Long?, Long> {
        val nowInstant = Instant.ofEpochMilli(nowMillis)
        return when (this) {
            StatsTimeRange.DAY -> {
                val start = nowInstant.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
                start to nowMillis
            }
            StatsTimeRange.WEEK -> {
                val start = nowInstant.atZone(zoneId)
                    .toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
                start to nowMillis
            }
            StatsTimeRange.MONTH -> {
                val start = YearMonth.from(nowInstant.atZone(zoneId))
                    .atDay(1)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
                start to nowMillis
            }
            StatsTimeRange.YEAR -> {
                val start = nowInstant.atZone(zoneId)
                    .toLocalDate()
                    .withDayOfYear(1)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
                start to nowMillis
            }
            StatsTimeRange.ALL -> {
                val start = events.minOfOrNull { it.startMillis() }
                start to nowMillis
            }
        }
    }

    private data class TimelineBucket(
        val label: String,
        val startMillis: Long,
        val endMillis: Long,
        val inclusiveEnd: Boolean
    )

    private fun PlaybackEvent.startMillis(): Long {
        val end = (endTimestamp ?: timestamp).coerceAtLeast(0L)
        val inferredStart = (startTimestamp ?: (end - durationMs)).coerceAtLeast(0L)
        return min(inferredStart, end)
    }

    private fun PlaybackEvent.endMillis(): Long {
        val end = (endTimestamp ?: timestamp).coerceAtLeast(0L)
        val start = startMillis()
        return max(end, start)
    }

    private fun notifyStatsChanged() {
        _refreshVersion.update { current ->
            if (current == Long.MAX_VALUE) {
                1L
            } else {
                current + 1L
            }
        }
    }

    companion object {
        private const val DEFAULT_PLAYBACK_HISTORY_LIMIT = 500
        private const val MAX_PLAYBACK_HISTORY_LIMIT = 5_000
        private const val MAX_FILE_UPDATE_RETRIES = 3
        private const val UNKNOWN_ARTIST = "Unknown Artist"
        private val MAX_HISTORY_AGE_MS = TimeUnit.DAYS.toMillis(730) // Keep roughly two years of history
        private const val SEGMENT_JOIN_TOLERANCE_MS = 0L
        private const val MAX_SONG_STATS_COUNT = 100
        /** A play shorter than this (a skip) doesn't count towards an artist's play counts. */
        const val MIN_COUNTED_PLAY_MS = 30_000L
        private const val MAX_DAILY_TOTAL_DAYS = 400L
        private const val MAX_DISCOVERY_ARTISTS = 5
        private const val MAX_NEW_SONG_IDS = 100
        private const val ON_REPEAT_WINDOW_DAYS = 3L
        private const val ON_REPEAT_MIN_PLAYS = 5
        private const val ON_REPEAT_DAILY_DAYS = 11

        /** Key used by [PeriodComparison.artistRanks]; matches how artists are grouped. */
        fun artistRankKey(name: String): String = name.trim().lowercase(Locale.ROOT)
    }
}

enum class StatsTimeRange(val displayName: String) {
    DAY("Today"),
    WEEK("Week to Date"),
    MONTH("Month to Date"),
    YEAR("Year to Date"),
    ALL("All Time")
}
