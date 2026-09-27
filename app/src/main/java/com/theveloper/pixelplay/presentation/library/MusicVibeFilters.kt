package com.theveloper.pixelplay.presentation.library

import com.theveloper.pixelplay.data.model.Song
import java.util.Locale

/**
 * Offline "vibe" filters for the Your Music screen.
 *
 * Every song is scored against a profile using whatever metadata it has: genre / subgenre /
 * tags, the analysed mix features (energy, valence, danceability, acousticness ...), BPM, key
 * mode, year and keywords in the title or album. Songs without analysed features still match
 * through genre and keywords, so streaming tracks with thin metadata show up less often.
 */
enum class VibeCategory(val label: String) {
    CONTEXT("Context & Activity"),
    MOOD("Mood & Emotion"),
    GENRE("Genre & Sonic Texture"),
    CUSTOM("Custom")
}

data class VibeFilter(
    val id: String,
    val label: String,
    val category: VibeCategory,
    val description: String,
    val genres: List<String> = emptyList(),
    val words: List<String> = emptyList(),
    val bpm: ClosedFloatingPointRange<Float>? = null,
    val energy: ClosedFloatingPointRange<Float>? = null,
    val valence: ClosedFloatingPointRange<Float>? = null,
    val minDanceability: Float? = null,
    val minAcousticness: Float? = null,
    val minInstrumentalness: Float? = null,
    val prefersMinor: Boolean? = null,
    val years: IntRange? = null,
    /** Free text typed by the user (custom filters only). */
    val query: String? = null
)

/** Playlists that are really "my favourites" get folded into Your Music's liked songs. */
private val FAVORITES_PLAYLIST_NAME = Regex(
    "^(my |your )?(favou?rites?|favs?|faves?|liked( songs| music)?|likes|loved( songs| tracks)?)( playlist)?$",
    RegexOption.IGNORE_CASE
)

fun isFavoritesPlaylistName(name: String): Boolean = FAVORITES_PLAYLIST_NAME.matches(name.trim())

object MusicVibeFilters {

    val presets: List<VibeFilter> = listOf(
        // Context & Activity
        VibeFilter("focus", "Focus", VibeCategory.CONTEXT,
            "Steady, non-intrusive soundscapes and repetitive rhythms engineered for deep work, coding, and cognitive endurance without vocal distraction.",
            genres = listOf("ambient", "lo-fi", "lofi", "minimal", "classical", "downtempo", "post-rock", "instrumental", "chillhop", "piano", "study"),
            words = listOf("focus", "study", "concentration", "deep work", "instrumental", "lofi", "lo-fi"),
            bpm = 60f..110f, energy = 0.1f..0.55f, minInstrumentalness = 0.5f),
        VibeFilter("workout", "Workout", VibeCategory.CONTEXT,
            "High-tempo, percussion-driven tracks designed to elevate heart rate, pace movement, and sustain physical exertion.",
            genres = listOf("edm", "electro", "drum and bass", "dnb", "trap", "hip hop", "hip-hop", "rap", "metal", "hardstyle", "phonk", "house", "dubstep"),
            words = listOf("workout", "gym", "run", "power", "pump", "beast", "grind"),
            bpm = 120f..180f, energy = 0.7f..1f),
        VibeFilter("party", "Party", VibeCategory.CONTEXT,
            "Upbeat, crowd-pleasing anthems with pronounced low-end and high danceability built for social gatherings.",
            genres = listOf("dance", "pop", "house", "edm", "disco", "reggaeton", "hip hop", "hip-hop", "funk", "electro", "dancehall", "afrobeats"),
            words = listOf("party", "dance", "club", "tonight", "remix", "anthem"),
            bpm = 110f..135f, energy = 0.65f..1f, valence = 0.5f..1f, minDanceability = 0.65f),
        VibeFilter("drive", "Drive", VibeCategory.CONTEXT,
            "Propulsive, rhythmic tracks with engaging mid-tempo momentum calibrated for open highways and daily commuting.",
            genres = listOf("synthwave", "rock", "indie", "alternative", "electronic", "outrun", "classic rock", "pop rock", "retrowave"),
            words = listOf("drive", "driving", "highway", "road", "ride", "cruise"),
            bpm = 95f..130f, energy = 0.5f..0.85f),
        VibeFilter("sleep", "Sleep", VibeCategory.CONTEXT,
            "Low-frequency, drone-heavy ambient audio stripped of sharp transients and sudden dynamic shifts to facilitate rest.",
            genres = listOf("ambient", "drone", "new age", "sleep", "meditation", "dark ambient", "classical", "piano"),
            words = listOf("sleep", "dream", "lullaby", "rest", "calm", "rain"),
            bpm = 40f..80f, energy = 0f..0.3f, minInstrumentalness = 0.5f),
        VibeFilter("unwind", "Unwind", VibeCategory.CONTEXT,
            "Soft, down-tempo transitional music intended to decompress stress and smooth the shift from work to leisure.",
            genres = listOf("downtempo", "chill", "chillout", "lounge", "trip hop", "trip-hop", "neo soul", "r&b", "soft rock", "acoustic", "bossa nova"),
            words = listOf("unwind", "relax", "slow", "easy", "sunset", "breathe"),
            bpm = 70f..105f, energy = 0.15f..0.55f),
        VibeFilter("gaming", "Gaming", VibeCategory.CONTEXT,
            "Immersive, synth-forward, or soundtrack-level instrumentals that heighten focus and atmosphere without masking dialogue.",
            genres = listOf("soundtrack", "score", "video game", "game", "chiptune", "synthwave", "electronic", "orchestral", "cinematic", "dubstep", "ost"),
            words = listOf("ost", "theme", "soundtrack", "game", "boss", "level", "battle"),
            energy = 0.4f..0.9f, minInstrumentalness = 0.5f),
        VibeFilter("dining", "Dining", VibeCategory.CONTEXT,
            "Warm, low-gain acoustic and jazz-tinged arrangements dialed back to sit cleanly beneath conversational chatter.",
            genres = listOf("jazz", "bossa nova", "lounge", "acoustic", "swing", "soul", "vocal jazz", "easy listening", "latin jazz"),
            words = listOf("dinner", "cafe", "wine", "evening", "bossa"),
            bpm = 60f..115f, energy = 0.1f..0.5f, minAcousticness = 0.5f),
        VibeFilter("reset", "Reset", VibeCategory.CONTEXT,
            "Gentle, organic acoustic arrangements meant for morning routines, coffee, and mindful mental recalibration.",
            genres = listOf("acoustic", "folk", "singer-songwriter", "indie folk", "new age", "piano", "ambient"),
            words = listOf("morning", "coffee", "sunrise", "breathe", "reset", "new day"),
            energy = 0.1f..0.5f, valence = 0.4f..1f, minAcousticness = 0.55f),

        // Mood & Emotion
        VibeFilter("chill", "Chill", VibeCategory.MOOD,
            "Mellow, laid-back grooves with warm low ends and soft attacks, providing effortless passive listening.",
            genres = listOf("chill", "chillout", "lo-fi", "lofi", "chillhop", "downtempo", "r&b", "neo soul", "trip hop", "lounge"),
            words = listOf("chill", "vibe", "mellow", "smooth", "slow"),
            bpm = 65f..105f, energy = 0.15f..0.55f),
        VibeFilter("hype", "Hype", VibeCategory.MOOD,
            "Maximum dynamic energy, heavy 808s, and aggressive hooks crafted to pump adrenaline and build instant motivation.",
            genres = listOf("trap", "drill", "phonk", "hip hop", "hip-hop", "rap", "hardstyle", "dubstep", "edm", "grime"),
            words = listOf("hype", "lit", "turn up", "go", "savage", "fire"),
            bpm = 125f..180f, energy = 0.8f..1f),
        VibeFilter("melancholy", "Melancholy", VibeCategory.MOOD,
            "Minor keys, introspective lyrical themes, and stark piano or string instrumentation for emotional catharsis.",
            genres = listOf("sad", "emo", "slowcore", "piano", "singer-songwriter", "indie folk", "classical", "ballad"),
            words = listOf("sad", "tears", "cry", "alone", "lonely", "goodbye", "broken", "miss you", "rain", "sorry"),
            energy = 0f..0.5f, valence = 0f..0.35f, prefersMinor = true),
        VibeFilter("euphoric", "Euphoric", VibeCategory.MOOD,
            "Uplifting progressions, soaring choruses, and bright synths engineered to spark pure positive release and joy.",
            genres = listOf("trance", "progressive house", "euphoric", "edm", "synthpop", "dance pop", "future bass", "eurodance"),
            words = listOf("happy", "alive", "sun", "light", "fly", "high", "euphoria", "love"),
            energy = 0.65f..1f, valence = 0.6f..1f, prefersMinor = false),
        VibeFilter("dreamy", "Dreamy", VibeCategory.MOOD,
            "Reverb-drenched guitars, ethereal vocal washes, and tape-saturated textures that evoke a floating, surreal state.",
            genres = listOf("dream pop", "shoegaze", "ethereal", "chillwave", "bedroom pop", "ambient pop", "psychedelic"),
            words = listOf("dream", "dreaming", "float", "cloud", "haze", "moon", "stars"),
            energy = 0.2f..0.6f),
        VibeFilter("dark", "Dark", VibeCategory.MOOD,
            "Low-register bass, industrial tension, and minor-scale textures that create an ominous, intense atmosphere.",
            genres = listOf("industrial", "darkwave", "dark ambient", "gothic", "witch house", "doom", "black metal", "dark techno", "horrorcore"),
            words = listOf("dark", "shadow", "black", "death", "demon", "night", "blood", "evil"),
            valence = 0f..0.35f, prefersMinor = true),
        VibeFilter("gritty", "Gritty", VibeCategory.MOOD,
            "Distorted, raw, unpolished audio with live tracking friction and vintage analog saturation.",
            genres = listOf("garage rock", "grunge", "blues rock", "punk", "noise rock", "stoner rock", "lo-fi", "sludge"),
            words = listOf("live", "raw", "demo", "dirty", "rough"),
            energy = 0.55f..1f),
        VibeFilter("nostalgia", "Nostalgia", VibeCategory.MOOD,
            "Sentimental hooks, familiar melodic tropes, and retro-specific production that trigger personal memory recall.",
            genres = listOf("oldies", "classic", "retro", "80s", "90s", "70s", "60s", "motown", "new wave", "synthwave", "city pop"),
            words = listOf("remember", "memories", "yesterday", "summer", "old", "back", "remaster"),
            years = 1950..2009),
        VibeFilter("romantic", "Romantic", VibeCategory.MOOD,
            "Intimate, vocal-forward arrangements driven by sensual rhythms, soft dynamics, and close-mic warmth.",
            genres = listOf("r&b", "rnb", "soul", "neo soul", "love songs", "ballad", "bossa nova", "slow jam"),
            words = listOf("love", "baby", "heart", "kiss", "darling", "forever"),
            energy = 0.15f..0.6f, valence = 0.3f..0.85f),

        // Genre & Sonic Texture
        VibeFilter("rock", "Rock", VibeCategory.GENRE,
            "Raw overdrive, dynamic verse-chorus shifts, and organic drum kits centered on classic instrumental energy.",
            genres = listOf("rock", "hard rock", "classic rock", "alternative rock", "alt rock", "grunge", "garage rock")),
        VibeFilter("metal", "Metal", VibeCategory.GENRE,
            "Heavy down-tuned guitars, blistering tempos, double-kick pedals, and intense vocal aggression.",
            genres = listOf("metal", "metalcore", "deathcore", "thrash", "death metal", "black metal", "doom", "nu metal", "djent")),
        VibeFilter("rap", "Rap", VibeCategory.GENRE,
            "Cadenced lyricism, syncopated 808 percussion, modern trap hi-hats, and rhythmic storytelling.",
            genres = listOf("rap", "hip hop", "hip-hop", "trap", "drill", "grime", "boom bap", "hiphop")),
        VibeFilter("pop", "Pop", VibeCategory.GENRE,
            "High-polish production, hook-dense songcraft, and pristine melodic clarity engineered for broad accessibility.",
            genres = listOf("pop", "dance pop", "synthpop", "electropop", "k-pop", "kpop", "j-pop", "teen pop")),
        VibeFilter("indie", "Indie", VibeCategory.GENRE,
            "Off-kilter arrangements, lo-fi aesthetics, DIY ethos, and alternative song structures outside mainstream conventions.",
            genres = listOf("indie", "alternative", "bedroom pop", "indie rock", "indie pop", "lo-fi", "art rock")),
        VibeFilter("acoustic", "Acoustic", VibeCategory.GENRE,
            "Stripped-back, unplugged string instrumentation showcasing natural room acoustics, fingerpicking, and vocal intimacy.",
            genres = listOf("acoustic", "unplugged", "singer-songwriter", "folk"),
            words = listOf("acoustic", "unplugged", "stripped"),
            minAcousticness = 0.7f),
        VibeFilter("electronic", "Electronic", VibeCategory.GENRE,
            "Synthesizer architecture, sequenced beats, dynamic filter sweeps, and digitally manipulated production.",
            genres = listOf("electronic", "electronica", "edm", "house", "techno", "trance", "dubstep", "drum and bass", "idm", "synth", "electro")),
        VibeFilter("ambient", "Ambient", VibeCategory.GENRE,
            "Minimalist harmonic washes and spatial resonance prioritizing timbre and mood over rhythm or melody.",
            genres = listOf("ambient", "drone", "new age", "dark ambient", "space music"),
            minInstrumentalness = 0.6f, energy = 0f..0.35f),
        VibeFilter("punk", "Punk", VibeCategory.GENRE,
            "Fast, stripped-down chord progressions, high-velocity tempo, and defiant, rebellious vocal delivery.",
            genres = listOf("punk", "pop punk", "hardcore", "post-punk", "skate punk", "emo")),
        VibeFilter("folk", "Folk", VibeCategory.GENRE,
            "Traditional storytelling carried by acoustic guitars, banjos, violins, and rich vocal harmonies.",
            genres = listOf("folk", "americana", "bluegrass", "country", "celtic", "indie folk", "traditional")),
        VibeFilter("soul", "Soul", VibeCategory.GENRE,
            "Expressive vocal performance, emotional depth, warm brass sections, and syncopated rhythmic grooves.",
            genres = listOf("soul", "neo soul", "motown", "funk", "r&b", "rnb", "gospel")),
        VibeFilter("jazz", "Jazz", VibeCategory.GENRE,
            "Complex modal improvisation, syncopated swing rhythms, dynamic counterpoint, and rich extended chord voicings.",
            genres = listOf("jazz", "swing", "bebop", "bossa nova", "fusion", "smooth jazz", "big band", "vocal jazz")),
    )

    private val presetById = presets.associateBy { it.id }

    /** Loose words people type that mean one of the presets. */
    private val aliases = mapOf(
        "study" to "focus", "coding" to "focus", "work" to "focus", "concentrate" to "focus",
        "gym" to "workout", "run" to "workout", "running" to "workout", "lifting" to "workout", "cardio" to "workout",
        "club" to "party", "dance" to "party", "road trip" to "drive", "commute" to "drive", "car" to "drive",
        "bed" to "sleep", "night" to "sleep", "relax" to "unwind", "calm" to "unwind", "game" to "gaming",
        "dinner" to "dining", "morning" to "reset", "coffee" to "reset", "sad" to "melancholy", "happy" to "euphoric",
        "energy" to "hype", "hiphop" to "rap", "hip hop" to "rap", "hip-hop" to "rap", "edm" to "electronic",
        "love" to "romantic", "retro" to "nostalgia", "oldies" to "nostalgia", "mellow" to "chill", "lofi" to "chill",
        "lo-fi" to "chill", "angry" to "metal", "country" to "folk", "r&b" to "soul", "rnb" to "soul"
    )

    fun custom(id: String, text: String): VibeFilter = VibeFilter(
        id = id,
        label = text.trim(),
        category = VibeCategory.CUSTOM,
        description = "Custom filter: “${text.trim()}”",
        query = text.trim()
    )

    private fun norm(value: String?): String = value.orEmpty().lowercase(Locale.ROOT)

    private fun Song.tagText(): String = buildString {
        append(norm(genre)); append(' ')
        append(norm(songInformation.subgenre)); append(' ')
        songRelationships.relatedGenres.forEach { append(norm(it)); append(' ') }
        songRelationships.relatedMoods.forEach { append(norm(it)); append(' ') }
        userActivityStats.tags.forEach { append(norm(it)); append(' ') }
        append(norm(mixIntelligence.mood)); append(' ')
        append(norm(mixIntelligence.tempoCategory)); append(' ')
        append(norm(userPreferences.preferredActivity)); append(' ')
        append(norm(userPreferences.listeningContext))
    }

    private fun Song.wordText(): String = norm(title) + " " + norm(album)

    private fun containsWord(haystack: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        var start = haystack.indexOf(needle)
        while (start >= 0) {
            val before = if (start == 0) ' ' else haystack[start - 1]
            val afterIndex = start + needle.length
            val after = if (afterIndex >= haystack.length) ' ' else haystack[afterIndex]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            start = haystack.indexOf(needle, start + 1)
        }
        return false
    }

    private fun rangeScore(value: Float?, range: ClosedFloatingPointRange<Float>?, hit: Double, miss: Double): Double {
        if (value == null || range == null) return 0.0
        return if (value in range) hit else -miss
    }

    /**
     * BPM check that tolerates half / double tempo. Catalogue BPMs (Deezer) are often an
     * octave off the felt tempo (a 70 BPM ballad listed as 140), which used to fail the range.
     */
    private fun bpmScore(bpm: Float?, range: ClosedFloatingPointRange<Float>?): Double {
        if (bpm == null || range == null || !bpm.isFinite() || bpm <= 0f) return 0.0
        return when {
            bpm in range -> 1.0
            bpm * 2f in range || bpm / 2f in range -> 0.5
            else -> -0.75
        }
    }

    /** Relevance of [song] for [filter]; a song matches when the score is at least [MATCH_THRESHOLD]. */
    fun score(song: Song, filter: VibeFilter): Double {
        filter.query?.let { return customScore(song, it) }
        val tags = song.tagText()
        val words = song.wordText()
        val features = song.mixIntelligence
        var score = 0.0
        if (filter.genres.any { containsWord(tags, it) }) score += 3.0
        if (containsWord(tags, norm(filter.label))) score += 2.0
        if (filter.words.any { containsWord(words, it) }) score += 1.5
        score += bpmScore(song.musicalFeatures.bpm, filter.bpm)
        score += rangeScore(features.energy, filter.energy, 1.5, 1.0)
        score += rangeScore(features.valence, filter.valence, 1.0, 0.5)
        filter.minDanceability?.let { min -> features.danceability?.let { score += if (it >= min) 1.0 else -0.5 } }
        filter.minAcousticness?.let { min -> features.acousticness?.let { score += if (it >= min) 1.0 else -0.5 } }
        filter.minInstrumentalness?.let { min -> features.instrumentalness?.let { score += if (it >= min) 1.0 else -0.75 } }
        filter.prefersMinor?.let { minor ->
            val mode = norm(song.musicalFeatures.mode)
            if (mode.isNotBlank()) score += if (mode.startsWith("min") == minor) 0.75 else -0.5
        }
        filter.years?.let { if (song.year > 0) score += if (song.year in it) 2.0 else -0.5 }
        // Two kinds of evidence when the song has been measured: a genre or word match alone
        // isn't enough if every measured feature the filter cares about disagrees (a slow
        // "pop" ballad for Party). Songs without measurements are judged on tags as before.
        val checks = featureChecks(song, filter)
        if (checks.first > 0 && checks.second == 0) score -= 1.0
        return score
    }

    /** (measured features the filter checks, how many of them agree). */
    private fun featureChecks(song: Song, filter: VibeFilter): Pair<Int, Int> {
        val f = song.mixIntelligence
        var measured = 0
        var agree = 0
        fun check(value: Float?, ok: (Float) -> Boolean) {
            if (value == null || !value.isFinite()) return
            measured++
            if (ok(value)) agree++
        }
        filter.energy?.let { range -> check(f.energy) { it in range } }
        filter.valence?.let { range -> check(f.valence) { it in range } }
        filter.minDanceability?.let { min -> check(f.danceability) { it >= min } }
        filter.minAcousticness?.let { min -> check(f.acousticness) { it >= min } }
        filter.minInstrumentalness?.let { min -> check(f.instrumentalness) { it >= min } }
        filter.bpm?.let { range -> check(song.musicalFeatures.bpm) { it in range || it * 2f in range || it / 2f in range } }
        return measured to agree
    }

    private fun customScore(song: Song, query: String): Double {
        val q = norm(query).trim()
        if (q.isEmpty()) return 0.0
        // A custom filter that names (or means) a preset behaves like that preset, plus text matching.
        val preset = presets.firstOrNull { norm(it.label) == q }
            ?: aliases[q]?.let { presetById[it] }
        val tokens = q.split(Regex("[\\s,;/]+")).filter { it.length >= 2 }
        val tokenPresets = tokens.mapNotNull { t -> presets.firstOrNull { norm(it.label) == t } ?: aliases[t]?.let { presetById[it] } }
        val tags = song.tagText()
        val words = song.wordText()
        val artist = norm(song.displayArtist)
        var score = preset?.let { score(song, it) } ?: 0.0
        if (containsWord(tags, q) || containsWord(words, q) || containsWord(artist, q)) score += 3.0
        tokens.forEach { t ->
            if (containsWord(tags, t)) score += 2.0
            if (containsWord(words, t)) score += 1.0
            if (containsWord(artist, t)) score += 2.0
        }
        if (preset == null && tokenPresets.isNotEmpty()) {
            score += tokenPresets.maxOf { score(song, it) } * 0.8
        }
        // Decades, e.g. "80s" or "1990s".
        Regex("(?:19|20)?(\\d)0s").find(q)?.let { m ->
            val digit = m.groupValues[1].toInt()
            val century = if (m.value.startsWith("20") || digit <= 2) 2000 else 1900
            val decade = century + digit * 10
            if (song.year in decade until decade + 10) score += 3.0
        }
        return score
    }

    const val MATCH_THRESHOLD = 2.0

    /** Listening history used to personalise a mix. */
    data class Listening(val playCount: Int = 0, val lastPlayedMs: Long = 0L)

    /**
     * Builds an on-the-spot mix for one filter instead of a plain metadata match.
     *
     * 1. Songs that fit the vibe become seeds (best matches first).
     * 2. The mix grows from the seeds: other songs by the same artists, on the same albums and in
     *    the same genres are pulled in, so tracks with thin metadata (YouTube Music) still join.
     * 3. Each candidate is ranked by vibe fit + closeness to the seeds + how much you play / like
     *    it + a little randomness from [seed], so every "new mix" is different.
     * 4. Artists are capped and spread out so the same artist never plays back to back.
     */
    fun buildMix(
        pool: List<Song>,
        filter: VibeFilter,
        likedIds: Set<String>,
        listening: Map<String, Listening>,
        seed: Long,
        size: Int = 60,
        /**
         * What the mixes have learned per song id (taste, snoozes, removals, skips), from
         * [com.theveloper.pixelplay.data.AdaptiveMix.personalAdjustments]. Empty = none.
         */
        adjust: Map<String, Double> = emptyMap()
    ): List<Song> {
        if (pool.isEmpty()) return emptyList()
        val random = java.util.Random(seed xor filter.id.hashCode().toLong())
        val vibe = HashMap<String, Double>(pool.size)
        pool.forEach { vibe[it.id] = score(it, filter) }

        val seeds = pool.filter { (vibe[it.id] ?: 0.0) >= MATCH_THRESHOLD }
            .sortedByDescending { vibe[it.id] }
            .take(40)
        if (seeds.isEmpty()) return emptyList()

        fun artistKey(song: Song) = norm(song.displayArtist).substringBefore(",").substringBefore(" feat").trim()
        fun genreKey(song: Song) = norm(song.genre).trim().takeUnless { it.isBlank() || it == "youtube music" }

        val artistWeight = HashMap<String, Double>()
        val albumWeight = HashMap<String, Double>()
        val genreWeight = HashMap<String, Double>()
        seeds.forEach { s ->
            val w = vibe[s.id] ?: 0.0
            artistWeight.merge(artistKey(s), w) { a, b -> a + b }
            if (s.album.isNotBlank()) albumWeight.merge(norm(s.album), w) { a, b -> a + b }
            genreKey(s)?.let { genreWeight.merge(it, w) { a, b -> a + b } }
        }
        val maxArtist = artistWeight.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val maxAlbum = albumWeight.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val maxGenre = genreWeight.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val maxPlays = listening.values.maxOfOrNull { it.playCount }?.takeIf { it > 0 } ?: 1
        val now = System.currentTimeMillis()

        val ranked = pool.mapNotNull { song ->
            val fit = vibe[song.id] ?: 0.0
            val artist = (artistWeight[artistKey(song)] ?: 0.0) / maxArtist
            val album = (albumWeight[norm(song.album)] ?: 0.0) / maxAlbum
            val genre = genreKey(song)?.let { (genreWeight[it] ?: 0.0) / maxGenre } ?: 0.0
            // Must either fit the vibe or be clearly related to what does.
            if (fit < MATCH_THRESHOLD && artist < 0.25 && album < 0.5 && genre < 0.5) return@mapNotNull null
            if (fit < 0) return@mapNotNull null
            val stats = listening[song.id]
            val plays = stats?.playCount ?: 0
            val familiarity = kotlin.math.ln(1.0 + plays) / kotlin.math.ln(1.0 + maxPlays)
            val daysSince = stats?.lastPlayedMs?.takeIf { it > 0 }?.let { (now - it) / 86_400_000.0 }
            // Slightly favour songs you haven't heard in the last day so mixes feel fresh.
            val freshness = when {
                daysSince == null -> 0.4
                daysSince < 1 -> -0.6
                daysSince < 7 -> 0.2
                else -> 0.5
            }
            val score = fit.coerceAtMost(8.0) * 1.0 +
                artist * 2.5 + album * 1.0 + genre * 1.5 +
                familiarity * 1.2 + freshness +
                (if (song.id in likedIds) 1.0 else 0.0) +
                (adjust[song.id] ?: 0.0) +
                random.nextDouble() * 2.5
            song to score
        }.sortedByDescending { it.second }

        // Cap each artist and avoid back-to-back repeats.
        val perArtistCap = if (artistWeight.size <= 2) size else 4
        val used = HashMap<String, Int>()
        val chosen = ArrayList<Song>(size)
        val remaining = ranked.map { it.first }.distinctBy { it.id }.toMutableList()
        while (chosen.size < size && remaining.isNotEmpty()) {
            val previous = chosen.lastOrNull()?.let { artistKey(it) }
            val index = remaining.indexOfFirst { song ->
                val key = artistKey(song)
                (used[key] ?: 0) < perArtistCap && key != previous
            }.takeIf { it >= 0 } ?: remaining.indexOfFirst { (used[artistKey(it)] ?: 0) < perArtistCap }
            if (index < 0) break
            val song = remaining.removeAt(index)
            used.merge(artistKey(song), 1) { a, b -> a + b }
            chosen += song
        }
        return chosen
    }
}
