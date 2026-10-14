package com.theveloper.pixelplay.data.ai.nano

import com.theveloper.pixelplay.data.MixPromptParser
import com.theveloper.pixelplay.data.PromptIntent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a plain-English music request with on-device Gemini Nano and returns it as a
 * [PromptIntent]: the same shape the local [MixPromptParser] produces, so the two merge.
 *
 * Nano only describes the request (tempo, energy, mood, genres, era, countries, names). It never
 * picks songs: the app's own scorers do that over the library, so results are always real songs.
 * Artist and song names are only kept when they were actually typed, so the model can't invent
 * an artist the user never asked for.
 */
@Singleton
class NanoMixIntentReader @Inject constructor(
    private val nano: NanoEngine,
) {
    val isReady: Boolean get() = nano.status.value == NanoStatus.Ready

    fun warmUp() = nano.warmUp()

    /** Null when Nano isn't ready, is busy, too slow, or answered something unusable. */
    suspend fun read(query: String, timeoutMs: Long = 2_500L): PromptIntent? {
        val q = query.trim()
        if (q.isEmpty() || !isReady) return null
        val answer = nano.generate(
            prefix = PREFIX,
            input = "Request: ${q.take(300)}\n",
            maxOutputTokens = 160,
            temperature = 0.1f,
            timeoutMs = timeoutMs,
        ) ?: return null
        return parseAnswer(q, answer)
    }

    companion object {
        /** Fixed instructions + examples (cached by AICore where supported). Under 150 words. */
        internal val PREFIX = """
            Turn a music request into JSON. Keys, all optional: tempo (slow|mid|fast), bpm (number), energy (low|mid|high), mood (happy|sad|angry|romantic|dark|dreamy|nostalgic), genres (list), decades (list like 1980), countries (list), artists (list), songs (list), exclude (genres to avoid), acoustic, instrumental, danceable (true).
            Use only what the request says or clearly implies. Lowercase genres. Reply with JSON only.
            Request: upbeat 80s rock like queen
            {"tempo":"fast","energy":"high","decades":[1980],"genres":["rock"],"artists":["Queen"]}
            Request: something to cry to, no rap
            {"mood":"sad","energy":"low","exclude":["rap"]}
            Request: songs like blinding lights for a night drive
            {"songs":["Blinding Lights"],"genres":["synthwave","pop"],"energy":"mid"}
        """.trimIndent()

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Pure: turns Nano's JSON answer into an intent (or null). Visible for tests. */
        fun parseAnswer(query: String, answer: String): PromptIntent? {
            val start = answer.indexOf('{')
            val end = answer.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val obj = runCatching { json.parseToJsonElement(answer.substring(start, end + 1)) as? JsonObject }
                .getOrNull() ?: return null
            val lowerQuery = query.lowercase(Locale.ROOT)

            fun str(key: String): String? = (obj[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            fun list(key: String): List<String> = when (val v = obj[key]) {
                is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
                is JsonPrimitive -> listOfNotNull(v.contentOrNull?.trim()?.takeIf(String::isNotEmpty))
                else -> emptyList()
            }.take(5)
            fun flag(key: String): Boolean = (obj[key] as? JsonPrimitive)?.booleanOrNull == true

            val labels = mutableListOf<String>()
            val tempoWord = str("tempo")
            var bpm = tempoWord?.let { MixPromptParser.tempoRange(it) }
            (obj["bpm"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it in 40.0..220.0 }?.let { n ->
                bpm = (n.toFloat() - 8f)..(n.toFloat() + 8f)
                labels += "${n.toInt()} bpm"
            }
            if (tempoWord != null && bpm != null && labels.none { it.endsWith("bpm") }) labels += tempoWord
            val energyWord = str("energy")
            val energy = energyWord?.let { MixPromptParser.energyRange(it) }
            if (energy != null) labels += "$energyWord energy"
            val moodWord = str("mood")
            val mood = moodWord?.let { MixPromptParser.moodFor(it) }
            if (mood != null && moodWord != null) labels += moodWord

            val genres = list("genres").map { it.lowercase(Locale.ROOT) }
            val genreTags = genres.flatMap { g -> MixPromptParser.genreTagsFor(g).ifEmpty { listOf(g) } }
            labels += genres

            val decades = list("decades").mapNotNull { d ->
                d.filter(Char::isDigit).toIntOrNull()?.let { if (it < 100) (if (it <= 30) 2000 + it else 1900 + it) else it }
                    ?.takeIf { it in 1900..2090 }?.let { it - it % 10 }
            }
            val years = if (decades.isEmpty()) null else decades.min()..(decades.max() + 9)
            decades.distinct().sorted().forEach { labels += "${it % 100}s".padStart(3, '0') }

            val countries = list("countries")
            val regionTags = countries.flatMap { c ->
                MixPromptParser.regionTagsFor(c).ifEmpty { listOf(c.lowercase(Locale.ROOT)) }
            }
            countries.forEach { labels += MixPromptParser.regionLabelFor(it) ?: it }

            // Names only count when they were typed (no invented artists).
            fun typed(name: String) = name.length >= 2 && lowerQuery.contains(name.lowercase(Locale.ROOT))
            val artists = list("artists").filter(::typed)
            val songs = list("songs").filter(::typed)
            artists.forEach { labels += "like $it" }
            songs.forEach { labels += "like $it" }

            val exclude = list("exclude").flatMap { MixPromptParser.genreTagsFor(it).ifEmpty { listOf(it.lowercase(Locale.ROOT)) } }
            list("exclude").forEach { labels += "no ${it.lowercase(Locale.ROOT)}" }

            val acoustic = flag("acoustic")
            val instrumental = flag("instrumental")
            val danceable = flag("danceable")
            if (acoustic) labels += "acoustic"
            if (instrumental) labels += "instrumental"
            if (danceable) labels += "danceable"

            val intent = PromptIntent(
                raw = query,
                bpm = bpm,
                energy = energy,
                valence = mood?.first,
                prefersMinor = mood?.second,
                minAcousticness = if (acoustic) 0.6f else null,
                minDanceability = if (danceable) 0.6f else null,
                minInstrumentalness = if (instrumental) 0.6f else null,
                years = years,
                genres = genreTags.distinct(),
                regions = regionTags.distinct(),
                artists = artists,
                songs = songs,
                excludeGenres = exclude.distinct(),
                labels = labels.distinct(),
            )
            return intent.takeIf { it.hasVibe || artists.isNotEmpty() || songs.isNotEmpty() }
        }
    }
}
