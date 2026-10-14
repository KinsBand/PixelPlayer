package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.presentation.library.VibeCategory
import com.theveloper.pixelplay.presentation.library.VibeFilter
import java.util.Locale

/**
 * What the queue prompt box understood from a plain-English request such as
 * "upbeat 80s rock like Queen", "slow sad acoustic", "fast aussie hip hop" or
 * "songs like Mr Brightside but chiller". Every part is optional and they all combine.
 *
 * [artists] and [songs] are phrases to look up in the library ("like Queen", "songs like X").
 * [freeWords] is whatever was left over: it can still match a song's genre, tags, title or artist.
 */
data class PromptIntent(
    val raw: String,
    val bpm: ClosedFloatingPointRange<Float>? = null,
    val energy: ClosedFloatingPointRange<Float>? = null,
    val valence: ClosedFloatingPointRange<Float>? = null,
    val prefersMinor: Boolean? = null,
    val minAcousticness: Float? = null,
    val minDanceability: Float? = null,
    val minInstrumentalness: Float? = null,
    val years: IntRange? = null,
    val genres: List<String> = emptyList(),
    val regions: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val songs: List<String> = emptyList(),
    val excludeGenres: List<String> = emptyList(),
    val freeWords: List<String> = emptyList(),
    /** Short human labels for what was understood, in the order typed ("upbeat", "80s", "rock"…). */
    val labels: List<String> = emptyList(),
    // ── Direction (where the mix should move, not what it should match) ─────────────────
    /** "no Drake": artists to leave out for the rest of the session. */
    val awayArtists: List<String> = emptyList(),
    /** "less Drake": artists to play less often. */
    val lessArtists: List<String> = emptyList(),
    /** "less rap": genres to play less of (not excluded). */
    val lessGenres: List<String> = emptyList(),
    /** "between Adele and Daft Punk": two names the mix should sit between. */
    val blend: List<String> = emptyList(),
    /** "start with Lorde, end with Fred again": the mix moves from one to the other. */
    val pathFrom: String? = null,
    val pathTo: String? = null,
    /** "a little" 0.15 · default null (0.35) · "a lot" / "only" 0.6. */
    val strength: Double? = null,
    /** "for a few songs": the steer lasts this many songs, then fades back. */
    val temporarySongs: Int? = null,
    /** Relative moves from the current vibe ("calmer" −0.15 energy, "faster" +10 % tempo…). */
    val energyShift: Double? = null,
    /** In log2 tempo units (+10 % ≈ 0.1375). */
    val tempoShift: Double? = null,
    val valenceShift: Double? = null,
) {
    /** True when the prompt says where to move (away, less, blend, path, calmer…), not only what to match. */
    val hasDirection: Boolean
        get() = awayArtists.isNotEmpty() || lessArtists.isNotEmpty() || lessGenres.isNotEmpty() ||
            blend.size >= 2 || (pathFrom != null && pathTo != null) ||
            energyShift != null || tempoShift != null || valenceShift != null

    /** True when there's something to look up or score (a name, a sound, or leftover words). */
    val hasTarget: Boolean
        get() = hasVibe || artists.isNotEmpty() || songs.isNotEmpty() || freeWords.isNotEmpty()

    /** True when the prompt asks for a sound / era / mood, not only "more of this song or artist". */
    val hasVibe: Boolean
        get() = bpm != null || energy != null || valence != null || prefersMinor != null ||
            minAcousticness != null || minDanceability != null || minInstrumentalness != null ||
            years != null || genres.isNotEmpty() || regions.isNotEmpty() || excludeGenres.isNotEmpty()

    /**
     * The intent as a vibe filter for the existing scorer (MusicVibeFilters.score). [query] stays
     * null on purpose so every measured feature (BPM, energy, valence, year…) is scored.
     */
    fun toFilter(): VibeFilter = VibeFilter(
        id = "prompt",
        label = raw.trim(),
        category = VibeCategory.CUSTOM,
        description = "Typed in the queue: “${raw.trim()}”",
        genres = (genres + regions + freeWords).distinct(),
        words = freeWords,
        bpm = bpm,
        energy = energy,
        valence = valence,
        minDanceability = minDanceability,
        minAcousticness = minAcousticness,
        minInstrumentalness = minInstrumentalness,
        prefersMinor = prefersMinor,
        years = years,
        query = null,
    )

    /** Fills empty parts of this intent from [other] (used to merge an on-device AI reading). */
    fun mergedWith(other: PromptIntent): PromptIntent = copy(
        bpm = bpm ?: other.bpm,
        energy = energy ?: other.energy,
        valence = valence ?: other.valence,
        prefersMinor = prefersMinor ?: other.prefersMinor,
        minAcousticness = minAcousticness ?: other.minAcousticness,
        minDanceability = minDanceability ?: other.minDanceability,
        minInstrumentalness = minInstrumentalness ?: other.minInstrumentalness,
        years = years ?: other.years,
        genres = (genres + other.genres).distinct(),
        regions = (regions + other.regions).distinct(),
        artists = (artists + other.artists).distinctBy { it.lowercase(Locale.ROOT) },
        songs = (songs + other.songs).distinctBy { it.lowercase(Locale.ROOT) },
        excludeGenres = (excludeGenres + other.excludeGenres).distinct(),
        // Words the AI turned into something structured are no longer free words.
        freeWords = freeWords.filterNot { w ->
            w in other.genres || other.artists.any { it.lowercase(Locale.ROOT).contains(w) }
        },
        labels = (labels + other.labels).distinct(),
        awayArtists = (awayArtists + other.awayArtists).distinctBy { it.lowercase(Locale.ROOT) },
        lessArtists = (lessArtists + other.lessArtists).distinctBy { it.lowercase(Locale.ROOT) },
        lessGenres = (lessGenres + other.lessGenres).distinct(),
        blend = blend.ifEmpty { other.blend },
        pathFrom = pathFrom ?: other.pathFrom,
        pathTo = pathTo ?: other.pathTo,
        strength = strength ?: other.strength,
        temporarySongs = temporarySongs ?: other.temporarySongs,
        energyShift = energyShift ?: other.energyShift,
        tempoShift = tempoShift ?: other.tempoShift,
        valenceShift = valenceShift ?: other.valenceShift,
    )
}

/**
 * Pure, on-device reading of the queue prompt. No network, no Android: instant and testable.
 * Order of work: pull out quoted / "like …" / "by …" / "no …" phrases, then read the remaining
 * words for tempo, energy, mood, era, region and genre. Anything left becomes [PromptIntent.freeWords].
 */
object MixPromptParser {

    private val FAST = 118f..200f
    private val MID = 90f..125f
    private val SLOW = 50f..95f

    private val tempoWords: Map<String, Pair<String, ClosedFloatingPointRange<Float>>> = mapOf(
        "fast" to ("fast" to FAST), "faster" to ("faster" to FAST), "upbeat" to ("upbeat" to FAST),
        "uptempo" to ("upbeat" to FAST), "up-tempo" to ("upbeat" to FAST), "quick" to ("fast" to FAST),
        "slow" to ("slow" to SLOW), "slower" to ("slower" to SLOW), "downtempo" to ("slow" to SLOW),
        "midtempo" to ("mid-tempo" to MID), "mid-tempo" to ("mid-tempo" to MID),
    )

    private val HIGH = 0.65f..1f
    private val MID_E = 0.35f..0.7f
    private val LOW = 0f..0.45f

    private val energyWords: Map<String, Pair<String, ClosedFloatingPointRange<Float>>> = mapOf(
        "energetic" to ("high energy" to HIGH), "hype" to ("hype" to HIGH), "hyped" to ("hype" to HIGH),
        "pumped" to ("hype" to HIGH), "intense" to ("intense" to HIGH), "heavy" to ("heavy" to HIGH),
        "heavier" to ("heavier" to HIGH), "loud" to ("loud" to HIGH), "banger" to ("bangers" to HIGH),
        "bangers" to ("bangers" to HIGH), "aggressive" to ("aggressive" to HIGH), "hard" to ("hard" to HIGH),
        "chill" to ("chill" to LOW), "chiller" to ("chiller" to LOW), "calm" to ("calm" to LOW),
        "calmer" to ("calmer" to LOW), "mellow" to ("mellow" to LOW), "soft" to ("soft" to LOW),
        "softer" to ("softer" to LOW), "relaxing" to ("relaxing" to LOW), "relaxed" to ("relaxed" to LOW),
        "gentle" to ("gentle" to LOW), "quiet" to ("quiet" to LOW), "laid-back" to ("laid-back" to LOW),
        "laidback" to ("laid-back" to LOW), "sleepy" to ("sleepy" to LOW), "moderate" to ("medium energy" to MID_E),
    )

    /** label, valence range, prefers minor. */
    private data class Mood(val label: String, val valence: ClosedFloatingPointRange<Float>, val minor: Boolean?)

    private val moodWords: Map<String, Mood> = mapOf(
        "happy" to Mood("happy", 0.6f..1f, false), "happier" to Mood("happier", 0.6f..1f, false),
        "joyful" to Mood("joyful", 0.6f..1f, false), "cheerful" to Mood("cheerful", 0.6f..1f, false),
        "feel-good" to Mood("feel-good", 0.6f..1f, false), "feelgood" to Mood("feel-good", 0.6f..1f, false),
        "sunny" to Mood("sunny", 0.6f..1f, false), "uplifting" to Mood("uplifting", 0.55f..1f, false),
        "euphoric" to Mood("euphoric", 0.6f..1f, false), "positive" to Mood("positive", 0.55f..1f, false),
        "sad" to Mood("sad", 0f..0.35f, true), "sadder" to Mood("sadder", 0f..0.35f, true),
        "melancholy" to Mood("melancholy", 0f..0.4f, true), "melancholic" to Mood("melancholy", 0f..0.4f, true),
        "depressing" to Mood("sad", 0f..0.3f, true), "heartbreak" to Mood("heartbreak", 0f..0.4f, true),
        "heartbroken" to Mood("heartbreak", 0f..0.4f, true), "moody" to Mood("moody", 0f..0.45f, true),
        "dark" to Mood("dark", 0f..0.35f, true), "emotional" to Mood("emotional", 0f..0.5f, null),
        "angry" to Mood("angry", 0f..0.4f, true), "romantic" to Mood("romantic", 0.3f..0.85f, null),
        "dreamy" to Mood("dreamy", 0.2f..0.7f, null), "nostalgic" to Mood("nostalgic", 0.3f..0.8f, null),
    )

    /** Words that set a texture rather than a genre. */
    private val textureWords: Map<String, String> = mapOf(
        "acoustic" to "acoustic", "unplugged" to "acoustic", "instrumental" to "instrumental",
        "danceable" to "danceable", "dancey" to "danceable", "dance" to "danceable", "party" to "danceable",
    )

    /** Multi-word genres first so "hip hop" wins over "hop". Values are the tag words to match. */
    private val genreWords: List<Pair<String, List<String>>> = listOf(
        "drum and bass" to listOf("drum and bass", "dnb"), "hip hop" to listOf("hip hop", "hip-hop", "hiphop", "rap"),
        "hip-hop" to listOf("hip hop", "hip-hop", "hiphop", "rap"), "r&b" to listOf("r&b", "rnb", "soul"),
        "lo-fi" to listOf("lo-fi", "lofi", "chillhop"), "pop punk" to listOf("pop punk", "punk"),
        "classic rock" to listOf("classic rock", "rock"), "hard rock" to listOf("hard rock", "rock"),
        "indie rock" to listOf("indie rock", "indie", "rock"), "indie pop" to listOf("indie pop", "indie", "pop"),
        "k-pop" to listOf("k-pop", "kpop"), "j-pop" to listOf("j-pop", "jpop"), "city pop" to listOf("city pop"),
        "new wave" to listOf("new wave"), "drill" to listOf("drill"), "synthwave" to listOf("synthwave", "retrowave"),
        "rock" to listOf("rock"), "metal" to listOf("metal"), "punk" to listOf("punk"), "pop" to listOf("pop"),
        "rap" to listOf("rap", "hip hop", "hip-hop"), "hiphop" to listOf("hip hop", "hip-hop", "rap"),
        "trap" to listOf("trap"), "edm" to listOf("edm", "electronic", "house", "dance"),
        "electronic" to listOf("electronic", "electronica", "edm"), "house" to listOf("house"),
        "techno" to listOf("techno"), "trance" to listOf("trance"), "dubstep" to listOf("dubstep"),
        "dnb" to listOf("drum and bass", "dnb"), "jazz" to listOf("jazz"), "blues" to listOf("blues"),
        "soul" to listOf("soul", "r&b"), "funk" to listOf("funk"), "disco" to listOf("disco"),
        "rnb" to listOf("r&b", "rnb", "soul"), "country" to listOf("country"), "folk" to listOf("folk"),
        "indie" to listOf("indie", "alternative"), "alternative" to listOf("alternative", "indie"),
        "alt" to listOf("alternative"), "grunge" to listOf("grunge"), "emo" to listOf("emo"),
        "reggae" to listOf("reggae"), "reggaeton" to listOf("reggaeton"), "latin" to listOf("latin", "reggaeton"),
        "classical" to listOf("classical", "orchestral"), "orchestral" to listOf("orchestral", "classical"),
        "ambient" to listOf("ambient"), "lofi" to listOf("lo-fi", "lofi", "chillhop"), "gospel" to listOf("gospel"),
        "kpop" to listOf("k-pop", "kpop"), "phonk" to listOf("phonk"), "grime" to listOf("grime"),
        "afrobeats" to listOf("afrobeats", "afrobeat"), "afrobeat" to listOf("afrobeats", "afrobeat"),
        "shoegaze" to listOf("shoegaze"), "synthpop" to listOf("synthpop", "synth-pop"),
        "soundtrack" to listOf("soundtrack", "score", "ost"), "ost" to listOf("soundtrack", "ost"),
        "musical" to listOf("musical", "show tunes"), "ska" to listOf("ska"), "bluegrass" to listOf("bluegrass"),
        "hardstyle" to listOf("hardstyle"), "garage" to listOf("garage"), "bossa" to listOf("bossa nova"),
        "chiptune" to listOf("chiptune", "8-bit"), "8-bit" to listOf("chiptune", "8-bit"), "8bit" to listOf("chiptune", "8-bit"),
    )

    /** Country / region words → tag words a song might carry ("australian", "aussie hip hop"…). */
    private val regionWords: Map<String, Pair<String, List<String>>> = mapOf(
        "aussie" to ("Australian" to listOf("australian", "aussie", "australia")),
        "australian" to ("Australian" to listOf("australian", "aussie", "australia")),
        "british" to ("British" to listOf("british", "uk", "britpop")), "uk" to ("UK" to listOf("uk", "british", "britpop")),
        "english" to ("British" to listOf("british", "uk")), "american" to ("American" to listOf("american", "us")),
        "canadian" to ("Canadian" to listOf("canadian")), "irish" to ("Irish" to listOf("irish", "celtic")),
        "korean" to ("Korean" to listOf("korean", "k-pop", "kpop")), "japanese" to ("Japanese" to listOf("japanese", "j-pop", "jpop", "city pop")),
        "french" to ("French" to listOf("french", "chanson")), "spanish" to ("Spanish" to listOf("spanish", "latin")),
        "mexican" to ("Mexican" to listOf("mexican", "latin", "regional mexican")), "brazilian" to ("Brazilian" to listOf("brazilian", "mpb", "bossa nova")),
        "german" to ("German" to listOf("german", "deutsch")), "italian" to ("Italian" to listOf("italian")),
        "african" to ("African" to listOf("african", "afrobeats")), "nigerian" to ("Nigerian" to listOf("nigerian", "afrobeats")),
        "indian" to ("Indian" to listOf("indian", "bollywood", "desi")), "bollywood" to ("Bollywood" to listOf("bollywood", "indian")),
        "kiwi" to ("NZ" to listOf("new zealand", "kiwi")), "swedish" to ("Swedish" to listOf("swedish")),
        "latino" to ("Latin" to listOf("latin", "reggaeton")),
    )

    private val stopWords = setOf(
        "a", "an", "the", "some", "songs", "song", "music", "tracks", "track", "tunes", "stuff", "vibes", "vibe",
        "with", "and", "or", "of", "for", "to", "me", "play", "give", "i", "want", "something", "anything",
        "more", "less", "but", "bit", "little", "kind", "kinda", "type", "sort", "style", "please", "mix",
        "that", "this", "is", "are", "in", "on", "it", "like", "from", "by", "era", "plus", "also", "just",
        "really", "very", "super", "lots", "lot", "mostly", "only", "all", "everything", "anything", "new",
        "old", "recent", "latest", "classic", "classics", "throwback", "throwbacks", "oldies", "tempo", "energy",
        "bpm", "vibey", "mood", "feel", "feeling", "sound", "sounding", "sounds",
    )

    /** "like X", "by X", "similar to X", "songs like X", "sounds like X" … up to a separator or keyword. */
    private val likePhrase = Regex(
        "\\b(?:songs? like|tracks? like|stuff like|music like|sounds? like|similar to|in the style of|like|by)\\s+(.+?)(?=\\s*(?:,|;|\\bbut\\b|\\band\\b|\\bwith\\b|\\bno\\b|\\bwithout\\b|\\bfrom\\b|\\bplus\\b|$))",
        RegexOption.IGNORE_CASE
    )
    private val songLikePhrase = Regex("^(?:songs?|tracks?) like\\s", RegexOption.IGNORE_CASE)
    private val excludePhrase = Regex("\\b(?:no|not|without|except|minus|skip)\\s+([a-z&\\- ]+?)(?=\\s*(?:,|;|\\bbut\\b|\\band\\b|$))", RegexOption.IGNORE_CASE)
    private val quoted = Regex("[\"“”']([^\"“”']{2,60})[\"“”']")

    // ── Direction phrases ───────────────────────────────────────────────────────────
    private val temporaryPhrase = Regex(
        "\\b(?:just\\s+)?for\\s+(?:(?:a\\s+)?(few|couple(?:\\s+of)?|bit|while)|(\\d{1,2}))(?:\\s+(?:more\\s+)?(?:songs?|tracks?))?\\b|\\bfor now\\b",
        RegexOption.IGNORE_CASE
    )
    private val littlePhrase = Regex("\\b(?:a little|a little bit|a bit|slightly|a touch|somewhat|a tad|a smidge)\\b(?:\\s+more\\b)?", RegexOption.IGNORE_CASE)
    private val strongPhrase = Regex(
        "\\b(?:a lot|way|much|heaps|loads|lots)\\s+more\\b|\\b(?:a lot|way|much|heaps|loads)\\b(?=\\s+[a-z]+er\\b)|\\bnothing but\\b|\\bonly\\b",
        RegexOption.IGNORE_CASE
    )
    private val pathPhrase = Regex(
        "\\bstart(?:ing)?\\s+(?:with|on|at)\\s+(.+?)\\s*,?\\s*(?:and\\s+|then\\s+)?(?:end|finish)(?:ing)?\\s+(?:with|on|at)\\s+(.+?)(?=\\s*(?:,|;|$))" +
            "|\\bfrom\\s+(.+?)\\s+(?:to|into|towards?)\\s+(.+?)(?=\\s*(?:,|;|$))",
        RegexOption.IGNORE_CASE
    )
    private val blendPhrase = Regex(
        "\\b(?:between|halfway between|mix of|blend of|cross between|mashup of)\\s+(.+?)\\s+(?:and|&|with)\\s+(.+?)(?=\\s*(?:,|;|\\bbut\\b|$))" +
            "|^\\s*(.+?)\\s+meets\\s+(.+?)\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val lessPhrase = Regex("\\b(?:less|fewer)\\s+(?:of\\s+)?([a-z0-9&'\\-. ]+?)(?=\\s*(?:,|;|\\bbut\\b|\\band\\b|$))", RegexOption.IGNORE_CASE)
    private val morePhrase = Regex("\\bmore\\s+(energy|energetic|hype|hyped|intense|chill|relaxed|mellow|upbeat|happy|cheerful|sad|dark|fast|slow)\\b", RegexOption.IGNORE_CASE)

    /** Comparative words read as a move from where the mix is now. Axis: e = energy, t = tempo, v = valence. */
    private val shiftWords: Map<String, Pair<Char, Int>> = mapOf(
        "calmer" to ('e' to -1), "chiller" to ('e' to -1), "softer" to ('e' to -1), "mellower" to ('e' to -1),
        "quieter" to ('e' to -1), "gentler" to ('e' to -1), "lighter" to ('e' to -1), "relaxed" to ('e' to -1),
        "heavier" to ('e' to 1), "louder" to ('e' to 1), "harder" to ('e' to 1), "hyper" to ('e' to 1),
        "wilder" to ('e' to 1), "livelier" to ('e' to 1), "energetic" to ('e' to 1), "energy" to ('e' to 1),
        "faster" to ('t' to 1), "quicker" to ('t' to 1), "slower" to ('t' to -1),
        "happier" to ('v' to 1), "brighter" to ('v' to 1), "cheerier" to ('v' to 1),
        "sadder" to ('v' to -1), "darker" to ('v' to -1), "moodier" to ('v' to -1),
        "intense" to ('e' to 1), "hype" to ('e' to 1), "hyped" to ('e' to 1), "chill" to ('e' to -1),
        "mellow" to ('e' to -1), "upbeat" to ('e' to 1), "happy" to ('v' to 1), "cheerful" to ('v' to 1),
        "sad" to ('v' to -1), "dark" to ('v' to -1), "fast" to ('t' to 1), "slow" to ('t' to -1),
        "loud" to ('e' to 1),
    )
    /** Comparatives that only ever mean "from now" (the plain forms stay absolute: "chill songs"). */
    private val comparativeOnly = setOf(
        "calmer", "chiller", "softer", "mellower", "quieter", "gentler", "lighter", "heavier", "louder",
        "harder", "hyper", "wilder", "livelier", "faster", "quicker", "slower", "happier", "brighter",
        "cheerier", "sadder", "darker", "moodier",
    )
    private val bpmNumber = Regex("\\b(\\d{2,3})\\s*bpm\\b|\\bbpm\\s*(\\d{2,3})\\b", RegexOption.IGNORE_CASE)
    private val decade = Regex("\\b(?:(19|20)?(\\d)0'?s)\\b", RegexOption.IGNORE_CASE)
    private val year = Regex("\\b(19[5-9]\\d|20[0-3]\\d)\\b")

    fun parse(text: String, currentYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)): PromptIntent {
        val raw = text.trim()
        if (raw.isEmpty()) return PromptIntent(raw = raw)
        var rest = " " + raw.lowercase(Locale.ROOT) + " "
        val labels = mutableListOf<String>()
        val artists = mutableListOf<String>()
        val songs = mutableListOf<String>()
        val excludes = mutableListOf<String>()
        val awayArtists = mutableListOf<String>()
        val lessArtists = mutableListOf<String>()
        val lessGenres = mutableListOf<String>()
        var blend: List<String> = emptyList()
        var pathFrom: String? = null
        var pathTo: String? = null
        var strength: Double? = null
        var temporary: Int? = null
        var energyShift: Double? = null
        var tempoShift: Double? = null
        var valenceShift: Double? = null
        /** The phrase as typed (original case), for names. */
        fun original(lowerPhrase: String): String {
            val at = raw.lowercase(Locale.ROOT).indexOf(lowerPhrase)
            return if (at < 0) lowerPhrase else raw.substring(at, at + lowerPhrase.length)
        }
        fun isName(phrase: String): Boolean {
            val words = phrase.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            return words.isNotEmpty() && words.size <= 6 && phrase.any(Char::isLetter) &&
                !words.all { isVibeWord(it) || it in stopWords || it in shiftWords }
        }
        fun shift(axis: Char, sign: Int) {
            when (axis) {
                'e' -> energyShift = sign * ENERGY_STEP
                't' -> tempoShift = sign * TEMPO_STEP
                'v' -> valenceShift = sign * VALENCE_STEP
            }
        }

        // How long and how hard: "for a few songs", "a little", "a lot more", "only".
        temporaryPhrase.find(rest)?.let { m ->
            temporary = when {
                m.groupValues[2].isNotEmpty() -> m.groupValues[2].toInt().coerceIn(1, 30)
                m.groupValues[1].startsWith("couple") -> 3
                else -> 6
            }
            labels += "for ${temporary} songs"
            rest = rest.replace(m.value, " ")
        }
        littlePhrase.find(rest)?.let { m ->
            strength = 0.15
            labels += "a little"
            rest = rest.replace(m.value, " ")
        }
        strongPhrase.find(rest)?.let { m ->
            if (strength == null) {
                strength = 0.6
                labels += if (m.value.trim() == "only" || m.value.trim() == "nothing but") "only" else "a lot"
            }
            rest = rest.replace(m.value, " ")
        }

        // "start with X, end with Y" / "from X to Y": a path from one to the other.
        pathPhrase.find(rest)?.let { m ->
            val a = (m.groupValues[1].ifEmpty { m.groupValues[3] }).trim()
            val b = (m.groupValues[2].ifEmpty { m.groupValues[4] }).trim()
            if (isName(a) && isName(b)) {
                pathFrom = original(a)
                pathTo = original(b)
                labels += "${pathFrom} → ${pathTo}"
                rest = rest.replace(m.value, " ")
            }
        }
        // "between X and Y" / "X meets Y": somewhere between the two.
        blendPhrase.find(rest)?.let { m ->
            val a = (m.groupValues[1].ifEmpty { m.groupValues[3] }).trim()
            val b = (m.groupValues[2].ifEmpty { m.groupValues[4] }).trim()
            if (isName(a) && isName(b)) {
                blend = listOf(original(a), original(b))
                labels += "between ${blend[0]} & ${blend[1]}"
                rest = rest.replace(m.value, " ")
            }
        }
        // "more energy", "more chill": moves from where the mix is now.
        morePhrase.findAll(rest).toList().forEach { m ->
            val word = m.groupValues[1].lowercase(Locale.ROOT)
            shiftWords[word]?.let { (axis, sign) ->
                shift(axis, sign)
                labels += "more $word"
                rest = rest.replace(m.value, " ")
            }
        }
        // "less energy" (a move), "less rap" (fewer of a genre), "less Drake" (fewer of an artist).
        lessPhrase.findAll(rest).toList().forEach { m ->
            val phrase = m.groupValues[1].trim().trim('.')
            val shiftWord = shiftWords[phrase]
            val tags = genreTagsFor(phrase)
            when {
                shiftWord != null -> { shift(shiftWord.first, -shiftWord.second); labels += "less $phrase" }
                tags.isNotEmpty() -> { lessGenres += tags; labels += "less $phrase" }
                isName(phrase) -> { lessArtists += original(phrase); labels += "less ${original(phrase)}" }
                else -> return@forEach
            }
            rest = rest.replace(m.value, " ")
        }

        // Quoted text is a song title (or artist) to look up as typed.
        quoted.findAll(raw).forEach { m ->
            songs += m.groupValues[1].trim()
            labels += "like ${m.groupValues[1].trim()}"
            rest = rest.replace(m.value.lowercase(Locale.ROOT), " ")
        }

        // Exclusions: "no rap", "without metal" (genres) and "no Drake" (an artist, this session).
        excludePhrase.findAll(rest).toList().forEach { m ->
            val phrase = m.groupValues[1].trim()
            val tags = genreTagsFor(phrase)
            if (tags.isNotEmpty()) {
                excludes += tags
                val named = phrase.split(Regex("\\s+")).filter { w -> genreWords.any { it.first == w } }
                labels += "no " + named.ifEmpty { listOf(phrase) }.joinToString(" ")
                rest = rest.replace(m.value, " ")
            } else if (isName(phrase) && phrase !in setOf("more", "repeats", "skips", "ads") &&
                phrase.split(Regex("\\s+")).none { isVibeWord(it) || it in shiftWords || it in setOf("too", "so", "very") }
            ) {
                awayArtists += original(phrase)
                labels += "no ${original(phrase)}"
                rest = rest.replace(m.value, " ")
            }
        }

        // Explicit BPM.
        var bpm: ClosedFloatingPointRange<Float>? = null
        bpmNumber.find(rest)?.let { m ->
            val n = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toFloat()
            bpm = (n - 8f)..(n + 8f)
            labels += "${n.toInt()} bpm"
            rest = rest.replace(m.value.lowercase(Locale.ROOT), " ")
        }

        // Era: decades, years, "old" / "new".
        var years: IntRange? = null
        decade.findAll(rest).toList().forEach { m ->
            val digit = m.groupValues[2].toInt()
            val century = when (m.groupValues[1]) {
                "19" -> 1900
                "20" -> 2000
                else -> if (digit <= 2) 2000 else 1900
            }
            val start = century + digit * 10
            val range = start..(start + 9)
            years = years?.let { minOf(it.first, range.first)..maxOf(it.last, range.last) } ?: range
            labels += if (m.groupValues[1].isEmpty()) "${digit}0s" else "${start}s"
            rest = rest.replace(m.value, " ")
        }
        if (years == null) {
            year.findAll(rest).toList().takeIf { it.isNotEmpty() }?.let { found ->
                val ys = found.map { it.value.toInt() }
                years = if (ys.size >= 2) ys.min()..ys.max() else (ys[0] - 1)..(ys[0] + 1)
                labels += if (ys.size >= 2) "${ys.min()}–${ys.max()}" else "${ys[0]}"
                found.forEach { rest = rest.replace(it.value, " ") }
            }
        }

        // "like X" / "by X" phrases → artists or songs (looked up in the library later).
        likePhrase.findAll(rest).toList().forEach { m ->
            val full = m.groupValues[1].trim().trim('.', '!', '?')
            if (full.isBlank() || full in stopWords) return@forEach
            // "like 80s rock" already lost its decade; a phrase that is only vibe words is not a name.
            val all = full.split(Regex("\\s+"))
            if (all.all { isVibeWord(it) || it in stopWords }) return@forEach
            // "by Taylor Swift happy": trailing vibe words belong to the rest of the prompt.
            var keep = all.size
            while (keep > 1 && (isVibeWord(all[keep - 1]) || all[keep - 1] in stopWords)) keep--
            val phrase = all.take(keep).joinToString(" ")
            val trailing = all.drop(keep).joinToString(" ")
            val original = raw.substring(
                raw.lowercase(Locale.ROOT).indexOf(phrase).coerceAtLeast(0)
            ).take(phrase.length).ifBlank { phrase }
            if (songLikePhrase.containsMatchIn(m.value)) {
                songs += original
                labels += "like $original"
            } else {
                artists += original
                labels += (if (m.value.trimStart().startsWith("by")) "by " else "like ") + original
            }
            rest = rest.replace(m.value, " $trailing ")
        }

        // Multi-word genres before single words.
        val genres = mutableListOf<String>()
        genreWords.filter { it.first.contains(' ') || it.first.contains('&') || it.first.contains('-') }.forEach { (key, tags) ->
            if (containsWord(rest, key)) {
                genres += tags
                labels += key
                rest = removeWord(rest, key)
            }
        }

        var energy: ClosedFloatingPointRange<Float>? = null
        var valence: ClosedFloatingPointRange<Float>? = null
        var minor: Boolean? = null
        var acoustic: Float? = null
        var dance: Float? = null
        var instrumental: Float? = null
        val regions = mutableListOf<String>()
        val free = mutableListOf<String>()

        for (word in tokens(rest)) {
            when {
                // "calmer", "faster", "happier": a move from the current vibe, not a fixed range.
                word in comparativeOnly -> {
                    val (axis, sign) = shiftWords.getValue(word)
                    shift(axis, sign)
                    labels += word
                }
                word in tempoWords -> {
                    val (label, range) = tempoWords.getValue(word)
                    bpm = bpm ?: range
                    if (range == FAST && energy == null) energy = 0.55f..1f
                    if (range == SLOW && energy == null) energy = 0f..0.55f
                    labels += label
                }
                word in energyWords -> {
                    val (label, range) = energyWords.getValue(word)
                    energy = range
                    labels += label
                }
                word in moodWords -> {
                    val mood = moodWords.getValue(word)
                    valence = mood.valence
                    if (mood.minor != null) minor = mood.minor
                    labels += mood.label
                }
                word in textureWords -> when (textureWords.getValue(word)) {
                    "acoustic" -> { acoustic = 0.6f; genres += "acoustic"; labels += "acoustic" }
                    "instrumental" -> { instrumental = 0.6f; labels += "instrumental" }
                    "danceable" -> { dance = 0.6f; labels += word }
                }
                word in regionWords -> {
                    val (label, tags) = regionWords.getValue(word)
                    regions += tags
                    labels += label
                }
                genreWords.any { it.first == word } -> {
                    genres += genreWords.first { it.first == word }.second
                    labels += word
                }
                word == "old" || word == "oldies" || word == "classic" || word == "classics" ||
                    word == "throwback" || word == "throwbacks" || word == "retro" -> if (years == null) {
                    years = 1950..2005
                    labels += "classics"
                }
                word == "new" || word == "recent" || word == "latest" || word == "modern" -> if (years == null) {
                    years = (currentYear - 3)..currentYear
                    labels += "new"
                }
                word in stopWords -> Unit
                word.length >= 3 && word.any(Char::isLetter) -> free += word
            }
        }

        return PromptIntent(
            raw = raw,
            bpm = bpm,
            energy = energy,
            valence = valence,
            prefersMinor = minor,
            minAcousticness = acoustic,
            minDanceability = dance,
            minInstrumentalness = instrumental,
            years = years,
            genres = genres.distinct(),
            regions = regions.distinct(),
            artists = artists.distinct(),
            songs = songs.distinct(),
            excludeGenres = excludes.distinct(),
            freeWords = free.distinct(),
            labels = inTypedOrder(raw, labels.distinct()),
            awayArtists = awayArtists.distinctBy { it.lowercase(Locale.ROOT) },
            lessArtists = lessArtists.distinctBy { it.lowercase(Locale.ROOT) },
            lessGenres = lessGenres.distinct(),
            blend = blend,
            pathFrom = pathFrom,
            pathTo = pathTo,
            strength = strength,
            temporarySongs = temporary,
            energyShift = energyShift,
            tempoShift = tempoShift,
            valenceShift = valenceShift,
        )
    }

    /** Size of one relative move ("calmer", "faster", "happier") at the default strength. */
    const val ENERGY_STEP = 0.15
    const val VALENCE_STEP = 0.15
    /** +10 % tempo in log2 units. */
    val TEMPO_STEP = kotlin.math.ln(1.1) / kotlin.math.ln(2.0)

    /** Labels in the order their words appear in what was typed (unknown positions last). */
    private fun inTypedOrder(raw: String, labels: List<String>): List<String> {
        val lower = raw.lowercase(Locale.ROOT)
        fun position(label: String): Int {
            val key = label.lowercase(Locale.ROOT).removePrefix("like ").removePrefix("by ").removePrefix("no ")
                .removeSuffix(" bpm").substringBefore('–')
            val direct = lower.indexOf(key)
            if (direct >= 0) return direct
            val first = key.split(' ').firstOrNull().orEmpty()
            return lower.indexOf(first).takeIf { first.isNotEmpty() && it >= 0 } ?: Int.MAX_VALUE
        }
        return labels.sortedBy(::position)
    }

    /** Tag words for a genre phrase ("rap" → rap, hip hop…), or empty when it isn't a genre. */
    fun genreTagsFor(phrase: String): List<String> {
        val p = phrase.lowercase(Locale.ROOT).trim()
        genreWords.firstOrNull { it.first == p }?.let { return it.second }
        return p.split(Regex("\\s+")).flatMap { w -> genreWords.firstOrNull { it.first == w }?.second.orEmpty() }.distinct()
    }

    /** Tag words for a country / region word ("aussie" → australian…), or empty. */
    fun regionTagsFor(word: String): List<String> =
        regionWords[word.lowercase(Locale.ROOT).trim()]?.second.orEmpty()

    /** Display label for a country / region word ("aussie" → Australian), or null. */
    fun regionLabelFor(word: String): String? = regionWords[word.lowercase(Locale.ROOT).trim()]?.first

    /** BPM range for a tempo word (slow / mid / fast). */
    fun tempoRange(word: String): ClosedFloatingPointRange<Float>? = when (word.lowercase(Locale.ROOT).trim()) {
        "slow" -> SLOW
        "mid", "medium", "mid-tempo", "midtempo" -> MID
        "fast", "upbeat" -> FAST
        else -> null
    }

    /** Energy range for low / mid / high. */
    fun energyRange(word: String): ClosedFloatingPointRange<Float>? = when (word.lowercase(Locale.ROOT).trim()) {
        "low", "calm", "chill" -> LOW
        "mid", "medium" -> MID_E
        "high", "hype" -> HIGH
        else -> null
    }

    /** (valence range, prefers minor) for a mood word, or null. */
    fun moodFor(word: String): Pair<ClosedFloatingPointRange<Float>, Boolean?>? =
        moodWords[word.lowercase(Locale.ROOT).trim()]?.let { it.valence to it.minor }

    private fun isVibeWord(w: String): Boolean =
        w in tempoWords || w in energyWords || w in moodWords || w in textureWords || w in regionWords ||
            genreWords.any { it.first == w }

    private fun tokens(text: String): List<String> =
        text.split(Regex("[\\s,;/!?.()]+")).map { it.trim('\'', '"') }.filter { it.isNotBlank() }

    private fun containsWord(haystack: String, needle: String): Boolean {
        var start = haystack.indexOf(needle)
        while (start >= 0) {
            val before = if (start == 0) ' ' else haystack[start - 1]
            val end = start + needle.length
            val after = if (end >= haystack.length) ' ' else haystack[end]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            start = haystack.indexOf(needle, start + 1)
        }
        return false
    }

    private fun removeWord(haystack: String, needle: String): String =
        haystack.replace(Regex("(?<![a-z0-9])" + Regex.escape(needle) + "(?![a-z0-9])"), " ")
}
