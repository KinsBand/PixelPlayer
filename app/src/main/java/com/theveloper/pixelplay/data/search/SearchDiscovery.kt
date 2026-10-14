package com.theveloper.pixelplay.data.search

import com.theveloper.pixelplay.data.library.LibraryInsightsRepository
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.ArtistCatalog
import kotlin.random.Random

/** One tile under an artist on Search's "Because you listened" card. Tapping it searches [query]. */
data class HubSuggestion(
    val query: String,
    val kind: HubSuggestionKind,
    /** Artist picture for [HubSuggestionKind.ARTIST] tiles, when known. */
    val imageUrl: String? = null
)

enum class HubSuggestionKind {
    /** An artist that sounds like the one you listen to (a search for their name). */
    ARTIST,
    /** The mood / genre / era of that artist's sound ("melancholic post-punk", "80s new wave"). */
    MOOD
}

/** "Because you listened" card: one artist you play, and searches for music like theirs. */
data class ArtistSearchSuggestionHub(
    val artist: LibraryInsightsRepository.ArtistCard,
    val suggestions: List<HubSuggestion>
)

/** A similar artist from Deezer ("related") or Last.fm ("similar"). */
data class SimilarArtist(val name: String, val imageUrl: String? = null)

/**
 * Pure helpers for Search's discovery hub: what an artist *sounds like* (genres, moods, era),
 * the searches that describe it without naming the artist, and the rotation that keeps the
 * cards changing.
 */
object SearchDiscovery {

    /** How long one set of cards stays before the next rotation. */
    const val ROTATION_PERIOD_MS = 3L * 60 * 60 * 1000
    /** Your top artists the cards rotate through (by recent listening). */
    const val ANCHOR_POOL = 6

    /** What an artist's music sounds like, from your copies of it (and Last.fm tags if set up). */
    data class SoundProfile(
        /** Most common first: "post-punk", "new wave". */
        val genres: List<String>,
        /** Most common first: "melancholic", "dreamy". */
        val moods: List<String>,
        /** 1980 for the 80s; null when unknown. */
        val decade: Int?
    ) {
        val isEmpty: Boolean get() = genres.isEmpty() && moods.isEmpty() && decade == null
    }

    private val SPLIT = Regex("""\s*[/;,|&]\s*|\s+-\s+""")
    private val SPACES = Regex("""\s+""")
    private val DECADE_TAG = Regex("""^(?:19|20)?\d0s$""")

    /** Tags that say nothing about the sound. */
    private val JUNK = setOf(
        "other", "others", "unknown", "misc", "miscellaneous", "music", "genre", "none", "various",
        "seen live", "favorites", "favourites", "favorite", "favourite", "albums i own", "love",
        "beautiful", "awesome", "cool", "good", "best", "spotify", "youtube", "soundtrack",
        "male vocalists", "female vocalists", "male vocalist", "female vocalist", "singer",
        "american", "british", "english", "usa", "uk", "canadian", "australian", "german", "french",
        "swedish", "japanese", "korean", "irish", "scottish", "under 2000 listeners"
    )

    /** Tags that describe a mood rather than a genre. */
    private val MOOD_WORDS = setOf(
        "melancholic", "melancholy", "sad", "chill", "chillout", "mellow", "dreamy", "dark", "energetic",
        "upbeat", "happy", "romantic", "atmospheric", "moody", "aggressive", "calm", "relaxing",
        "uplifting", "nostalgic", "haunting", "ethereal", "groovy", "angry", "heavy", "soft",
        "emotional", "intense", "euphoric", "sensual", "brooding", "bittersweet", "hopeful",
        "introspective", "peaceful", "playful", "quirky", "sexy", "warm", "gloomy", "epic"
    )

    /**
     * Genres and moods of [songs] (genre, subgenre, related genres/moods, the analysed mood) plus
     * [extraTags] (Last.fm). When no song carries a mood tag, one is read from the analysed
     * energy / valence / acousticness / danceability averages.
     */
    fun soundProfile(songs: List<Song>, extraTags: List<String> = emptyList(), artistName: String = ""): SoundProfile {
        val artistKey = ArtistCatalog.fold(artistName)
        val genreCounts = HashMap<String, Int>()
        val moodCounts = HashMap<String, Int>()
        fun addTag(raw: String?, weight: Int, moodHint: Boolean = false) {
            if (raw.isNullOrBlank()) return
            raw.split(SPLIT).forEach { part ->
                val tag = part.trim().lowercase().replace(SPACES, " ")
                if (tag.length < 2 || tag.length > 30 || tag in JUNK) return@forEach
                if (DECADE_TAG.matches(tag)) return@forEach
                if (artistKey.isNotBlank() && ArtistCatalog.fold(tag) == artistKey) return@forEach
                if (moodHint || tag in MOOD_WORDS) moodCounts[tag] = (moodCounts[tag] ?: 0) + weight
                else genreCounts[tag] = (genreCounts[tag] ?: 0) + weight
            }
        }
        songs.forEach { song ->
            addTag(song.genre, 2)
            addTag(song.songInformation.subgenre, 2)
            song.songRelationships.relatedGenres.forEach { addTag(it, 1) }
            song.songRelationships.relatedMoods.forEach { addTag(it, 1, moodHint = true) }
            addTag(song.mixIntelligence.mood, 1, moodHint = true)
        }
        // Last.fm's top tags are ordered by weight: earlier tags count more.
        extraTags.take(10).forEachIndexed { index, tag -> addTag(tag, (10 - index).coerceAtLeast(3)) }

        val moods = moodCounts.entries.sortedByDescending { it.value }.map { it.key }.toMutableList()
        if (moods.isEmpty()) featureMood(songs)?.let { moods += it }

        val years = songs.mapNotNull { song -> song.year.takeIf { it in 1950..2100 } }.sorted()
        val decade = years.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] / 10 * 10 }

        return SoundProfile(
            genres = genreCounts.entries.sortedByDescending { it.value }.map { it.key }.take(3),
            moods = moods.take(3),
            decade = decade
        )
    }

    /** A mood word from the analysed audio features, or null with too few analysed songs. */
    fun featureMood(songs: List<Song>): String? {
        val analysed = songs.map { it.mixIntelligence }.filter { it.energy != null && it.valence != null }
        if (analysed.size < 3) return null
        val energy = analysed.mapNotNull { it.energy }.average()
        val valence = analysed.mapNotNull { it.valence }.average()
        val acoustic = analysed.mapNotNull { it.acousticness }.takeIf { it.isNotEmpty() }?.average()
        val dance = analysed.mapNotNull { it.danceability }.takeIf { it.isNotEmpty() }?.average()
        return when {
            acoustic != null && acoustic >= 0.6 && energy < 0.5 -> "acoustic"
            dance != null && dance >= 0.72 && energy >= 0.6 -> "groovy"
            energy >= 0.65 && valence >= 0.55 -> "upbeat"
            energy >= 0.65 && valence < 0.4 -> "intense"
            energy < 0.45 && valence < 0.4 -> "melancholic"
            energy < 0.45 && valence >= 0.55 -> "mellow"
            valence < 0.4 -> "moody"
            else -> null
        }
    }

    /** "80s" for 1980, "2010s" for 2010. */
    fun decadeLabel(decade: Int): String = if (decade < 2000) "${decade % 100}s" else "${decade}s"

    /**
     * Searches for music *like* the artist's, never naming them: "melancholic post-punk",
     * "80s new wave", "dreamy shoegaze". Without genres: "melancholic songs".
     */
    fun moodQueries(profile: SoundProfile, artistName: String): List<String> {
        val genres = profile.genres
        val moods = profile.moods
        val era = profile.decade?.let { decadeLabel(it) }
        val out = ArrayList<String>()
        if (genres.isNotEmpty()) {
            moods.getOrNull(0)?.let { out += "$it ${genres[0]}" }
            era?.let { out += "$it ${genres[0]}" }
            genres.getOrNull(1)?.let { second -> out += (moods.getOrNull(1) ?: moods.getOrNull(0))?.let { "$it $second" } ?: second }
            genres.getOrNull(2)?.let { out += it }
            if (out.isEmpty()) out += genres[0]
        } else {
            moods.forEach { out += "$it songs" }
            if (era != null && moods.isNotEmpty()) out += "$era ${moods[0]} songs"
        }
        val artistKey = ArtistCatalog.fold(artistName)
        return out.map { it.trim() }
            .filter { it.isNotBlank() && (artistKey.isBlank() || !ArtistCatalog.fold(it).contains(artistKey)) }
            .distinctBy { ArtistCatalog.fold(it) }
    }

    /** Which rotation we're in at [nowMs]. Changes every [ROTATION_PERIOD_MS]. */
    fun rotationSlot(nowMs: Long, periodMs: Long = ROTATION_PERIOD_MS): Long = nowMs / periodMs

    /** Milliseconds until the next rotation after [nowMs]. */
    fun msUntilNextSlot(nowMs: Long, periodMs: Long = ROTATION_PERIOD_MS): Long = periodMs - (nowMs % periodMs)

    /**
     * The [count] artists shown this rotation, walking through [pool] (your top artists) so each
     * gets a turn. A pool no bigger than [count] is shown as is.
     */
    fun <T> anchorsFor(pool: List<T>, slot: Long, count: Int = 2): List<T> {
        if (pool.size <= count) return pool
        val start = Math.floorMod(slot, pool.size.toLong()).toInt()
        return List(count) { pool[(start + it) % pool.size] }
    }

    /**
     * The tiles for one card: similar artists you don't already play a lot ([exclude]), picked
     * afresh each rotation from the closest matches, plus mood searches (rotated too).
     * [total] is 3 for side-by-side cards, 4 for a single full-width card.
     */
    fun buildSuggestions(
        artistName: String,
        artistId: Long,
        similar: List<SimilarArtist>,
        moodQueries: List<String>,
        slot: Long,
        total: Int,
        exclude: Set<String> = emptySet()
    ): List<HubSuggestion> {
        val excluded = (exclude + artistName).mapTo(HashSet()) { ArtistCatalog.fold(it) }
        val random = Random(slot * 1_000_003L + artistId)
        val artists = similar
            .filter { it.name.isNotBlank() && ArtistCatalog.fold(it.name) !in excluded }
            .distinctBy { ArtistCatalog.fold(it.name) }
            .take(CLOSEST_SIMILAR)
            .shuffled(random)
            .map { HubSuggestion(it.name, HubSuggestionKind.ARTIST, it.imageUrl) }
        val moods = moodQueries.takeIf { it.isNotEmpty() }?.let { list ->
            val shift = Math.floorMod(slot, list.size.toLong()).toInt()
            (list.drop(shift) + list.take(shift)).map { HubSuggestion(it, HubSuggestionKind.MOOD) }
        }.orEmpty()
        val artistTarget = minOf(2, artists.size)
        val picked = ArrayList<HubSuggestion>(total)
        picked += artists.take(artistTarget)
        picked += moods.take(total - picked.size)
        // Not enough moods: more artists fill in (and the other way round).
        if (picked.size < total) picked += artists.drop(artistTarget).take(total - picked.size)
        return picked
    }

    /** Only the closest matches are shuffled, so a rotation never shows a far-off artist. */
    private const val CLOSEST_SIMILAR = 8
}
