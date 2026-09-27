package com.theveloper.pixelplay.presentation.components

import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.data.media.ReleaseCountry
import com.theveloper.pixelplay.data.model.Song
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The four filter fields shown in the Daily Mix sheet header.
 *
 * Country / Era / Genre / Mood are derived from whatever metadata the user's
 * library actually carries, so a field with no usable metadata simply offers
 * no options and renders as disabled rather than showing dead chips.
 */
enum class MixFilterField(val label: String) {
    Country("Country"),
    Era("Era"),
    Genre("Genre"),
    Mood("Mood")
}

/** Currently selected value for each filter field. `null` means "not filtered". */
@Immutable
data class MixFilterSelection(
    val country: String? = null,
    val era: String? = null,
    val genre: String? = null,
    val mood: String? = null
) {
    fun valueOf(field: MixFilterField): String? = when (field) {
        MixFilterField.Country -> country
        MixFilterField.Era -> era
        MixFilterField.Genre -> genre
        MixFilterField.Mood -> mood
    }

    fun with(field: MixFilterField, value: String?): MixFilterSelection = when (field) {
        MixFilterField.Country -> copy(country = value)
        MixFilterField.Era -> copy(era = value)
        MixFilterField.Genre -> copy(genre = value)
        MixFilterField.Mood -> copy(mood = value)
    }

    val activeCount: Int
        get() = listOfNotNull(country, era, genre, mood).size

    val isEmpty: Boolean
        get() = activeCount == 0
}

/** Option lists for each field, derived from the library. */
@Immutable
data class MixFilterOptions(
    val countries: ImmutableList<String> = persistentListOf(),
    val eras: ImmutableList<String> = persistentListOf(),
    val genres: ImmutableList<String> = persistentListOf(),
    val moods: ImmutableList<String> = persistentListOf()
) {
    fun optionsFor(field: MixFilterField): ImmutableList<String> = when (field) {
        MixFilterField.Country -> countries
        MixFilterField.Era -> eras
        MixFilterField.Genre -> genres
        MixFilterField.Mood -> moods
    }
}

/** A playlist card generated from the filtered pool. */
@Immutable
data class GeneratedMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val songs: ImmutableList<Song>
)

private const val MaxOptionsPerField = 24
private const val MinSongsPerGeneratedMix = 4
private const val MaxGeneratedMixes = 8
private const val MaxSongsPerGeneratedMix = 40

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

/** Mood falls back to the tempo category when no explicit mood was analysed. */
fun songMoodLabel(song: Song): String? {
    val mood = song.mixIntelligence.mood?.trim()?.takeIf { it.isNotEmpty() }
    if (mood != null) return mood.replaceFirstChar { it.uppercase() }
    val tempo = song.mixIntelligence.tempoCategory?.trim()?.takeIf { it.isNotEmpty() }
    return tempo?.replaceFirstChar { it.uppercase() }
}

private fun List<Song>.facet(selector: (Song) -> String?): ImmutableList<String> =
    asSequence()
        .mapNotNull(selector)
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(MaxOptionsPerField)
        .map { it.key }
        .toImmutableList()

/** Builds the option lists shown by each of the four filter buttons. */
fun buildMixFilterOptions(songs: List<Song>): MixFilterOptions {
    if (songs.isEmpty()) return MixFilterOptions()
    return MixFilterOptions(
        countries = songs.facet { songCountryLabel(it) },
        eras = songs.asSequence()
            .mapNotNull { songEraLabel(it.year) }
            .distinct()
            .sortedBy { label ->
                when {
                    label == "Pre-60s" -> -1
                    else -> label.dropLast(1).toIntOrNull()
                        ?.let { if (it < 60) it + 100 else it } ?: 999
                }
            }
            .toList()
            .toImmutableList(),
        genres = songs.facet { songGenreLabel(it) },
        moods = songs.facet { songMoodLabel(it) }
    )
}

/** Narrows the pool to songs matching every active filter. */
fun applyMixFilters(songs: List<Song>, selection: MixFilterSelection): List<Song> {
    if (selection.isEmpty) return songs
    return songs.filter { song ->
        (selection.country == null || songCountryLabel(song) == selection.country) &&
            (selection.era == null || songEraLabel(song.year) == selection.era) &&
            (selection.genre == null || songGenreLabel(song) == selection.genre) &&
            (selection.mood == null || songMoodLabel(song) == selection.mood)
    }
}

/**
 * Turns the filtered pool into playlist cards. Grouping key is chosen so the
 * cards stay informative: whichever facet the user has *not* pinned varies most,
 * so we group by the first unpinned facet that yields more than one bucket.
 */
fun buildGeneratedMixes(
    songs: List<Song>,
    selection: MixFilterSelection
): ImmutableList<GeneratedMix> {
    val pool = applyMixFilters(songs, selection)
    if (pool.isEmpty()) return persistentListOf()

    val groupers: List<Triple<MixFilterField, (Song) -> String?, String>> = listOf(
        Triple(MixFilterField.Genre, { s: Song -> songGenreLabel(s) }, "mix"),
        Triple(MixFilterField.Era, { s: Song -> songEraLabel(s.year) }, "mix"),
        Triple(MixFilterField.Mood, { s: Song -> songMoodLabel(s) }, "mix"),
        Triple(MixFilterField.Country, { s: Song -> songCountryLabel(s) }, "mix")
    )

    val chosen = groupers.firstOrNull { (field, selector, _) ->
        selection.valueOf(field) == null &&
            pool.mapNotNull(selector).distinct().size > 1
    }

    val buckets: List<Pair<String, List<Song>>> = if (chosen != null) {
        pool.groupBy { chosen.second(it) }
            .mapNotNull { (key, value) -> key?.let { it to value } }
            .filter { it.second.size >= MinSongsPerGeneratedMix }
            .sortedByDescending { it.second.size }
    } else {
        // Everything is pinned (or there is nothing to vary by) — fall back to artists.
        pool.groupBy { it.displayArtist }
            .filter { it.value.size >= MinSongsPerGeneratedMix }
            .map { it.key to it.value }
            .sortedByDescending { it.second.size }
    }

    val cards = buckets.take(MaxGeneratedMixes).map { (label, bucketSongs) ->
        val ordered = bucketSongs.sortedByDescending { it.userActivityStats.playCount }
            .take(MaxSongsPerGeneratedMix)
        GeneratedMix(
            id = "mix_${label.lowercase().replace(' ', '_')}",
            title = "$label mix",
            subtitle = "${ordered.size} tracks · ${ordered.firstOrNull()?.displayArtist.orEmpty()}",
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
            subtitle = "${single.size} tracks",
            songs = single.toImmutableList()
        )
    )
}

/** Human-readable summary of the active filters, used as a fallback card title. */
fun describeSelection(selection: MixFilterSelection): String {
    val parts = listOfNotNull(
        selection.mood,
        selection.era,
        selection.genre,
        selection.country
    )
    return if (parts.isEmpty()) "Your mix" else parts.joinToString(" · ") + " mix"
}
