package com.theveloper.pixelplay.presentation.components

import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.data.media.ReleaseCountry
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.library.MusicVibeFilters
import com.theveloper.pixelplay.presentation.library.VibeCategory
import com.theveloper.pixelplay.presentation.library.VibeFilter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

/**
 * The filter chips of the Daily Mix sheet, in display order.
 *
 * Library facets (Artist, Genre, Album, Language, Country) list what your songs actually carry;
 * the others are fixed buckets (Tempo, Energy, Plays, Added, Length, Source) or vibe profiles
 * (Mood, Activity). Fields marked [allowsCustom] also take free text from the chip's search
 * box ("2am drive", "1996", "100-120"), matched loosely.
 */
enum class MixFilterField(
    val label: String,
    val allowsCustom: Boolean,
    /** Placeholder for the chip's search box. */
    val searchHint: String,
) {
    Artist("Artist", true, "Search or type an artist"),
    Genre("Genre", true, "Search or type a genre"),
    Era("Era", true, "A decade, year or range — 90s, 2007, 1980-1995"),
    Mood("Mood", true, "Any mood — rainy day, euphoric, late night"),
    Activity("Activity", true, "Any activity — studying, cooking, gym"),
    Tempo("Tempo", true, "Pick one, or type BPM — 128, 90-110"),
    Energy("Energy", false, "Search energy levels"),
    Album("Album", true, "Search or type an album"),
    Language("Language", true, "Search or type a language"),
    Country("Country", true, "Search or type a country"),
    Plays("Plays", false, "Search"),
    Added("Added", false, "Search"),
    Length("Length", false, "Search"),
    Source("Source", false, "Search");
}

/** Currently selected value for each filter field. A missing field means "not filtered". */
@Immutable
data class MixFilterSelection(
    val values: Map<MixFilterField, String> = emptyMap()
) {
    fun valueOf(field: MixFilterField): String? = values[field]

    fun with(field: MixFilterField, value: String?): MixFilterSelection {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() }
        return copy(values = if (clean == null) values - field else values + (field to clean))
    }

    val activeCount: Int get() = values.size

    val isEmpty: Boolean get() = values.isEmpty()

    /** "Field=value" pairs, for rememberSaveable. */
    fun toSaved(): List<String> = values.map { (field, value) -> field.name + "=" + value }

    companion object {
        fun fromSaved(saved: List<String>): MixFilterSelection = MixFilterSelection(
            saved.mapNotNull { entry ->
                val field = MixFilterField.entries.firstOrNull { it.name == entry.substringBefore('=') }
                val value = entry.substringAfter('=', "").takeIf { it.isNotEmpty() }
                if (field != null && value != null) field to value else null
            }.toMap()
        )
    }
}

/** One choice in a chip's list, with how many library songs it covers (0 = not counted). */
@Immutable
data class MixFilterOption(val value: String, val count: Int = 0)

/** Option lists for each field, derived from the library. */
@Immutable
data class MixFilterOptions(
    val byField: Map<MixFilterField, ImmutableList<MixFilterOption>> = emptyMap()
) {
    fun optionsFor(field: MixFilterField): ImmutableList<MixFilterOption> = byField[field] ?: persistentListOf()
}

/** A playlist card generated from the filtered pool. */
@Immutable
data class GeneratedMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val songs: ImmutableList<Song>
)

/** Everything the Daily Mix sheet shows for one selection; built off the main thread. */
@Immutable
data class DailyMixSheetModel(
    val selection: MixFilterSelection = MixFilterSelection(),
    val options: MixFilterOptions = MixFilterOptions(),
    val artistMixes: ImmutableList<GeneratedMix> = persistentListOf(),
    val generatedMixes: ImmutableList<GeneratedMix> = persistentListOf(),
    /** The pool narrowed by every active filter, best first. */
    val filtered: ImmutableList<Song> = persistentListOf(),
    /** How many of [filtered] were found online for a value that isn't in the library. */
    val foundOnline: Int = 0,
) {
    companion object {
        val Empty = DailyMixSheetModel()
    }
}

private const val MaxOptionsPerField = 300
private const val MinSongsPerGeneratedMix = 4
private const val MaxGeneratedMixes = 10
private const val MaxSongsPerGeneratedMix = 40

// ---- Labels ---------------------------------------------------------------------------------

/** Decade bucket for a song, e.g. 1994 -> "90s". Returns null when the year is unknown. */
fun songEraLabel(year: Int): String? {
    if (year < 1900 || year > 2999) return null
    val decade = (year / 10) * 10
    return when {
        decade < 1960 -> "Pre-60s"
        decade >= 2000 -> "%02ds".format(decade % 100)
        else -> "${decade % 100}s"
    }
}

/**
 * Country comes from the release-territory tag captured during library scan and
 * stored as an ISO-3166 alpha-2 code; it is rendered here as a readable name.
 * Songs whose files carry no release-country tag are absent from this facet.
 */
fun songCountryLabel(song: Song): String? =
    ReleaseCountry.displayName(song.songInformation.releaseCountry)

fun songGenreLabel(song: Song): String? =
    song.genre?.trim()?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }

/** Primary artist only, so "A, B" collaborations count towards A's mix. */
fun songArtistLabel(song: Song): String? =
    song.primaryArtist.name.trim()
        .takeIf { it.isNotEmpty() && !it.equals("unknown artist", ignoreCase = true) && !it.equals("<unknown>", ignoreCase = true) }

/** Mood falls back to the tempo category when no explicit mood was analysed. */
fun songMoodLabel(song: Song): String? {
    val mood = song.mixIntelligence.mood?.trim()?.takeIf { it.isNotEmpty() }
    if (mood != null) return mood.replaceFirstChar { it.uppercase() }
    val tempo = song.mixIntelligence.tempoCategory?.trim()?.takeIf { it.isNotEmpty() }
    return tempo?.replaceFirstChar { it.uppercase() }
}

fun songAlbumLabel(song: Song): String? =
    song.album.trim().takeIf { it.isNotEmpty() && !it.equals("unknown album", ignoreCase = true) && !it.equals("<unknown>", ignoreCase = true) }

/** "eng" / "en" / "English" → "English". */
fun songLanguageLabel(song: Song): String? {
    val raw = song.songInformation.language?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (raw.length in 2..3 && raw.all { it.isLetter() }) {
        val name = runCatching { Locale(raw.lowercase(Locale.ROOT)).getDisplayLanguage(Locale.ENGLISH) }.getOrNull()
        if (!name.isNullOrBlank() && !name.equals(raw, ignoreCase = true)) return name
    }
    return raw.replaceFirstChar { it.uppercase() }
}

private object Buckets {
    const val TempoSlow = "Slow"
    const val TempoMid = "Mid-tempo"
    const val TempoUpbeat = "Upbeat"
    const val TempoFast = "Fast"
    val tempo = listOf(TempoSlow, TempoMid, TempoUpbeat, TempoFast)

    const val EnergyCalm = "Calm"
    const val EnergyBalanced = "Balanced"
    const val EnergyHigh = "Energetic"
    val energy = listOf(EnergyCalm, EnergyBalanced, EnergyHigh)

    const val PlaysMost = "Most played"
    const val PlaysFew = "Played a few times"
    const val PlaysNever = "Never played"
    val plays = listOf(PlaysMost, PlaysFew, PlaysNever)

    const val AddedWeek = "Last 7 days"
    const val AddedMonth = "Last 30 days"
    const val AddedYear = "Last 12 months"
    const val AddedOlder = "Over a year ago"
    val added = listOf(AddedWeek, AddedMonth, AddedYear, AddedOlder)

    const val LengthShort = "Under 3 min"
    const val LengthMid = "3–5 min"
    const val LengthLong = "Over 5 min"
    val length = listOf(LengthShort, LengthMid, LengthLong)

    const val SourceDevice = "On this device"
    const val SourceDownloaded = "Downloaded"
    const val SourceStreaming = "Streaming"
    val source = listOf(SourceDevice, SourceDownloaded, SourceStreaming)
}

private fun songBpm(song: Song): Float? = song.musicalFeatures.bpm?.takeIf { it.isFinite() && it > 0f }

private fun tempoBucket(song: Song): String? {
    val bpm = songBpm(song) ?: return null
    return when {
        bpm < 90f -> Buckets.TempoSlow
        bpm < 120f -> Buckets.TempoMid
        bpm < 140f -> Buckets.TempoUpbeat
        else -> Buckets.TempoFast
    }
}

private fun energyBucket(song: Song): String? {
    val e = song.mixIntelligence.energy?.takeIf { it.isFinite() } ?: return null
    return when {
        e < 0.4f -> Buckets.EnergyCalm
        e < 0.7f -> Buckets.EnergyBalanced
        else -> Buckets.EnergyHigh
    }
}

private fun playsBucket(song: Song): String = when (song.userActivityStats.playCount) {
    0 -> Buckets.PlaysNever
    in 1..9 -> Buckets.PlaysFew
    else -> Buckets.PlaysMost
}

/** Library timestamps are seconds on some sources and milliseconds on others. */
private fun addedMillis(song: Song): Long? {
    val raw = song.dateAdded.takeIf { it > 0 } ?: return null
    return if (raw < 100_000_000_000L) raw * 1000 else raw
}

private fun addedBucket(song: Song, now: Long): String? {
    val added = addedMillis(song) ?: return null
    val days = (now - added) / 86_400_000L
    return when {
        days < 7 -> Buckets.AddedWeek
        days < 30 -> Buckets.AddedMonth
        days < 365 -> Buckets.AddedYear
        else -> Buckets.AddedOlder
    }
}

private fun lengthBucket(song: Song): String? {
    val seconds = song.duration / 1000
    if (seconds <= 0) return null
    return when {
        seconds < 180 -> Buckets.LengthShort
        seconds <= 300 -> Buckets.LengthMid
        else -> Buckets.LengthLong
    }
}

private fun sourceBucket(song: Song): String = when {
    song.isLocal -> Buckets.SourceDevice
    song.isDownloaded -> Buckets.SourceDownloaded
    else -> Buckets.SourceStreaming
}

// ---- Options --------------------------------------------------------------------------------

private fun List<Song>.facet(selector: (Song) -> String?): ImmutableList<MixFilterOption> =
    asSequence()
        .mapNotNull(selector)
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase(Locale.ROOT) })
        .take(MaxOptionsPerField)
        .map { MixFilterOption(it.key, it.value) }
        .toImmutableList()

/** Fixed buckets in their natural order, keeping only those with songs. */
private fun List<Song>.buckets(order: List<String>, selector: (Song) -> String?): ImmutableList<MixFilterOption> {
    val counts = asSequence().mapNotNull(selector).groupingBy { it }.eachCount()
    return order.mapNotNull { label -> counts[label]?.let { MixFilterOption(label, it) } }.toImmutableList()
}

/** Vibe profiles of one category that at least a few songs fit, best covered first. */
private fun List<Song>.vibes(category: VibeCategory, extra: List<MixFilterOption> = emptyList()): ImmutableList<MixFilterOption> {
    val counted = MusicVibeFilters.presets.filter { it.category == category }.mapNotNull { vibe ->
        var count = 0
        for (song in this) if (MusicVibeFilters.score(song, vibe) >= MusicVibeFilters.MATCH_THRESHOLD) count++
        if (count >= MinSongsPerGeneratedMix) MixFilterOption(vibe.label, count) else null
    }.sortedByDescending { it.count }
    val labels = counted.mapTo(HashSet()) { it.value.lowercase(Locale.ROOT) }
    return (counted + extra.filter { labels.add(it.value.lowercase(Locale.ROOT)) }).toImmutableList()
}

/** Builds the option lists shown by each chip. Heavy: call off the main thread. */
fun buildMixFilterOptions(songs: List<Song>): MixFilterOptions {
    if (songs.isEmpty()) return MixFilterOptions()
    val now = System.currentTimeMillis()
    val eras = songs.asSequence()
        .mapNotNull { songEraLabel(it.year) }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedBy { (label, _) ->
            when (label) {
                "Pre-60s" -> -1
                else -> label.dropLast(1).toIntOrNull()?.let { if (it < 60) it + 100 else it } ?: 999
            }
        }
        .map { MixFilterOption(it.key, it.value) }
        .toImmutableList()
    return MixFilterOptions(
        mapOf(
            MixFilterField.Artist to songs.facet { songArtistLabel(it) },
            MixFilterField.Genre to songs.facet { songGenreLabel(it) },
            MixFilterField.Era to eras,
            MixFilterField.Mood to songs.vibes(VibeCategory.MOOD, songs.facet { songMoodLabel(it) }),
            MixFilterField.Activity to songs.vibes(VibeCategory.CONTEXT),
            MixFilterField.Tempo to songs.buckets(Buckets.tempo) { tempoBucket(it) },
            MixFilterField.Energy to songs.buckets(Buckets.energy) { energyBucket(it) },
            MixFilterField.Album to songs.facet { songAlbumLabel(it) },
            MixFilterField.Language to songs.facet { songLanguageLabel(it) },
            MixFilterField.Country to songs.facet { songCountryLabel(it) },
            MixFilterField.Plays to songs.buckets(Buckets.plays) { playsBucket(it) },
            MixFilterField.Added to songs.buckets(Buckets.added) { addedBucket(it, now) },
            MixFilterField.Length to songs.buckets(Buckets.length) { lengthBucket(it) },
            MixFilterField.Source to songs.buckets(Buckets.source) { sourceBucket(it) },
        )
    )
}

// ---- Matching -------------------------------------------------------------------------------

/**
 * Does [song] pass one filter? Exact option values match exactly; anything typed matches loosely
 * (contained in the field, a decade/year/range for Era, BPM for Tempo, a vibe for Mood/Activity).
 */
private class MixMatcher(selection: MixFilterSelection) {
    private val now = System.currentTimeMillis()
    private val checks: List<(Song) -> Boolean> = selection.values.map { (field, value) -> matcherFor(field, value) }

    fun matches(song: Song): Boolean = checks.all { it(song) }

    private fun matcherFor(field: MixFilterField, raw: String): (Song) -> Boolean {
        val value = raw.trim()
        val lower = value.lowercase(Locale.ROOT)
        fun text(selector: (Song) -> String?): (Song) -> Boolean = { song ->
            val label = selector(song)
            label != null && (label.equals(value, ignoreCase = true) || label.lowercase(Locale.ROOT).contains(lower))
        }
        return when (field) {
            MixFilterField.Artist -> { song ->
                songArtistLabel(song)?.equals(value, ignoreCase = true) == true ||
                    song.displayArtist.lowercase(Locale.ROOT).contains(lower)
            }
            MixFilterField.Genre -> { song ->
                text { songGenreLabel(it) }(song) ||
                    song.songInformation.subgenre?.lowercase(Locale.ROOT)?.contains(lower) == true
            }
            MixFilterField.Album -> text { songAlbumLabel(it) }
            MixFilterField.Language -> text { songLanguageLabel(it) }
            MixFilterField.Country -> text { songCountryLabel(it) }
            MixFilterField.Era -> eraMatcher(value)
            MixFilterField.Mood, MixFilterField.Activity -> vibeMatcher(field, value)
            MixFilterField.Tempo -> tempoMatcher(value)
            MixFilterField.Energy -> { song -> energyBucket(song) == value }
            MixFilterField.Plays -> { song -> playsBucket(song) == value }
            MixFilterField.Added -> { song -> addedBucket(song, now) == value }
            MixFilterField.Length -> { song -> lengthBucket(song) == value }
            MixFilterField.Source -> { song -> sourceBucket(song) == value }
        }
    }

    private fun eraMatcher(value: String): (Song) -> Boolean {
        val range = parseYearRange(value)
        return { song ->
            if (range != null) song.year in range else songEraLabel(song.year)?.equals(value, ignoreCase = true) == true
        }
    }

    private fun tempoMatcher(value: String): (Song) -> Boolean {
        if (value in Buckets.tempo) return { song -> tempoBucket(song) == value }
        val numbers = Regex("\\d{2,3}").findAll(value).map { it.value.toFloat() }.toList()
        val range: ClosedFloatingPointRange<Float> = when (numbers.size) {
            0 -> return { false }
            1 -> (numbers[0] - 6f)..(numbers[0] + 6f)
            else -> minOf(numbers[0], numbers[1])..maxOf(numbers[0], numbers[1])
        }
        // Half/double time counts too: catalogue BPMs are often an octave off.
        return { song -> songBpm(song)?.let { it in range || it * 2f in range || it / 2f in range } == true }
    }

    private fun vibeMatcher(field: MixFilterField, value: String): (Song) -> Boolean {
        val lower = value.lowercase(Locale.ROOT)
        val preset = MusicVibeFilters.presets.firstOrNull { it.label.equals(value, ignoreCase = true) }
        val vibe: VibeFilter = preset ?: MusicVibeFilters.custom("mix_${field.name}_$lower", value)
        return { song ->
            (field == MixFilterField.Mood && songMoodLabel(song)?.equals(value, ignoreCase = true) == true) ||
                MusicVibeFilters.score(song, vibe) >= MusicVibeFilters.MATCH_THRESHOLD
        }
    }
}

/** "90s", "1990s", "2007", "1980-1995", "80s-90s" → a year range; null for anything else. */
fun parseYearRange(text: String): IntRange? {
    val t = text.lowercase(Locale.ROOT).replace('–', '-').replace('—', '-').trim()
    fun decade(token: String): IntRange? {
        val m = Regex("^'?(\\d{2}|\\d{4})s$").find(token.trim()) ?: return null
        val digits = m.groupValues[1]
        val start = if (digits.length == 4) digits.toInt() / 10 * 10 else {
            val d = digits.toInt()
            if (d <= 29) 2000 + d / 10 * 10 else 1900 + d / 10 * 10
        }
        return start..(start + 9)
    }
    fun year(token: String): Int? = token.trim().takeIf { Regex("^\\d{4}$").matches(it) }?.toInt()?.takeIf { it in 1900..2999 }
    val parts = t.split('-').map { it.trim() }.filter { it.isNotEmpty() }
    return when (parts.size) {
        1 -> decade(parts[0]) ?: year(parts[0])?.let { it..it }
        2 -> {
            val a = decade(parts[0])?.first ?: year(parts[0])
            val b = decade(parts[1])?.last ?: year(parts[1])
            if (a != null && b != null) minOf(a, b)..maxOf(a, b) else null
        }
        else -> null
    }
}

/** Narrows the pool to songs matching every active filter. */
fun applyMixFilters(songs: List<Song>, selection: MixFilterSelection): List<Song> {
    if (selection.isEmpty) return songs
    val matcher = MixMatcher(selection)
    return songs.filter { matcher.matches(it) }
}

// ---- Mixes ----------------------------------------------------------------------------------

/**
 * Turns the filtered pool into playlist cards. The grouping key is chosen so the cards stay
 * informative: we group by the first facet the user has *not* pinned that yields more than one
 * bucket.
 */
fun buildGeneratedMixes(
    songs: List<Song>,
    selection: MixFilterSelection,
    prefiltered: List<Song>? = null,
): ImmutableList<GeneratedMix> {
    val pool = prefiltered ?: applyMixFilters(songs, selection)
    if (pool.isEmpty()) return persistentListOf()

    val now = System.currentTimeMillis()
    val groupers: List<Pair<MixFilterField, (Song) -> String?>> = listOf(
        MixFilterField.Genre to { s: Song -> songGenreLabel(s) },
        MixFilterField.Era to { s: Song -> songEraLabel(s.year) },
        MixFilterField.Mood to { s: Song -> songMoodLabel(s) },
        MixFilterField.Tempo to { s: Song -> tempoBucket(s) },
        MixFilterField.Language to { s: Song -> songLanguageLabel(s) },
        MixFilterField.Country to { s: Song -> songCountryLabel(s) },
        MixFilterField.Added to { s: Song -> addedBucket(s, now) },
        MixFilterField.Artist to { s: Song -> songArtistLabel(s) },
    )

    val chosen = groupers.firstOrNull { (field, selector) ->
        selection.valueOf(field) == null &&
            pool.asSequence().mapNotNull(selector).distinct().take(2).count() > 1
    }

    val buckets: List<Pair<String, List<Song>>> = if (chosen != null) {
        pool.groupBy { chosen.second(it) }
            .mapNotNull { (key, value) -> key?.let { it to value } }
            .filter { it.second.size >= MinSongsPerGeneratedMix }
            .sortedByDescending { it.second.size }
    } else {
        // Everything is pinned (or there is nothing to vary by): fall back to artists.
        pool.groupBy { it.displayArtist }
            .filter { it.value.size >= MinSongsPerGeneratedMix }
            .map { it.key to it.value }
            .sortedByDescending { it.second.size }
    }

    val cards = buckets.take(MaxGeneratedMixes).map { (label, bucketSongs) ->
        val ordered = bucketSongs.sortedByDescending { it.userActivityStats.playCount }
            .take(MaxSongsPerGeneratedMix)
        GeneratedMix(
            id = "mix_" + mixKey(label),
            title = "$label mix",
            subtitle = ordered.firstOrNull()?.displayArtist.orEmpty(),
            songs = ordered.toImmutableList()
        )
    }

    if (cards.isNotEmpty()) return cards.toImmutableList()

    // Pool too small or too uniform to split — offer it as one card.
    val single = pool.take(MaxSongsPerGeneratedMix)
    return persistentListOf(
        GeneratedMix(
            id = "mix_filtered",
            title = describeSelection(selection),
            subtitle = "",
            songs = single.toImmutableList()
        )
    )
}

private fun mixKey(label: String): String = label.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_")

/** Human-readable summary of the active filters, e.g. "Chill · 90s · Rock mix". */
fun describeSelection(selection: MixFilterSelection): String {
    val parts = MixFilterField.entries.mapNotNull { selection.valueOf(it) }
    return if (parts.isEmpty()) "Your mix" else parts.joinToString(" · ") + " mix"
}

/** How many artist mixes the results row offers at most. */
private const val MaxArtistMixes = 10

/**
 * One mix per primary artist in the filtered pool, biggest first. Empty when the
 * Artist filter is pinned, since the row would then repeat the chip.
 * Ids are prefixed `artist_` so they never collide with the facet mixes' `mix_` ids.
 */
fun buildArtistMixes(
    songs: List<Song>,
    selection: MixFilterSelection,
    prefiltered: List<Song>? = null,
): ImmutableList<GeneratedMix> {
    if (selection.valueOf(MixFilterField.Artist) != null) return persistentListOf()
    val pool = prefiltered ?: applyMixFilters(songs, selection)
    if (pool.isEmpty()) return persistentListOf()
    return pool.groupBy { songArtistLabel(it) }
        .mapNotNull { (name, bucket) -> name?.let { it to bucket } }
        .filter { it.second.size >= MinSongsPerGeneratedMix }
        .sortedByDescending { it.second.size }
        .take(MaxArtistMixes)
        .map { (name, bucket) ->
            val ordered = bucket.sortedByDescending { it.userActivityStats.playCount }
                .take(MaxSongsPerGeneratedMix)
            GeneratedMix(
                id = "artist_" + mixKey(name),
                title = name,
                subtitle = "",
                songs = ordered.toImmutableList()
            )
        }
        .toImmutableList()
}

/**
 * Everything the Daily Mix sheet needs for [selection]: the options (from the whole pool, so
 * picking one filter never hides the others' choices), the filtered songs and the mixes.
 * Heavy: call off the main thread. Pass [options] to reuse ones already built for this pool.
 */
fun buildDailyMixSheetModel(
    pool: List<Song>,
    selection: MixFilterSelection,
    options: MixFilterOptions? = null,
    /**
     * Songs found online *for* the filter value (e.g. a typed artist that isn't in the library).
     * They already match by how they were found, so they skip the metadata filters.
     */
    extraMatches: List<Song> = emptyList(),
): DailyMixSheetModel {
    val fromPool = applyMixFilters(pool, selection)
    val poolIds = fromPool.mapTo(HashSet()) { it.id }
    val extra = extraMatches.filter { poolIds.add(it.id) }
    val filtered = fromPool + extra
    val artistMixes = buildArtistMixes(pool + extra, selection, prefiltered = filtered)
    val taken = artistMixes.mapTo(HashSet()) { it.id }
    // Facet mixes are de-duplicated against the artist row so the keyed row never repeats a key.
    val generated = buildGeneratedMixes(pool + extra, selection, prefiltered = filtered)
        .filter { taken.add(it.id) }
        .toImmutableList()
    return DailyMixSheetModel(
        selection = selection,
        options = options ?: buildMixFilterOptions(pool),
        artistMixes = artistMixes,
        generatedMixes = generated,
        filtered = filtered.toImmutableList(),
        foundOnline = extra.size,
    )
}

/** Top distinct primary artists of a song list, for the bar subtitle. */
fun topArtistNames(songs: List<Song>, limit: Int = 2): List<String> =
    songs.asSequence()
        .mapNotNull { songArtistLabel(it) }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(limit)
        .map { it.key }
