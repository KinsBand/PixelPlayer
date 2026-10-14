package com.theveloper.pixelplay.data.ai.nano

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates synced lyrics on the phone with Gemini Nano.
 *
 * Only the words go to the model, a few lines at a time (so every request fits); the
 * timestamps never do. They're put back in code afterwards, so timing can't drift and no line
 * can be lost. A chunk whose answer doesn't line up is asked once more, then shown untranslated.
 *
 * Output keeps the format the lyrics screen already reads:
 * `[timestamp] original` then `[timestamp] translation` for every line, or
 * [ALREADY_IN_TARGET] when the lyrics are already in the target language.
 */
@Singleton
class NanoLyricsTranslator @Inject constructor(
    private val nano: NanoEngine,
) {
    val isReady: Boolean get() = nano.status.value == NanoStatus.Ready

    suspend fun translate(lyricsText: String, targetLanguage: String): Result<String> {
        if (!isReady) return Result.failure(IllegalStateException(NOT_READY))
        val parsed = lyricsText.lines().map(::splitLine)
        val textIndexes = parsed.indices.filter { parsed[it].text.isNotBlank() }
        if (textIndexes.isEmpty()) return Result.success(lyricsText)

        val prefix = prefixFor(targetLanguage)
        val translations = HashMap<Int, String>()
        nano.warmUp()
        for ((chunkNo, chunk) in textIndexes.chunked(CHUNK_LINES).withIndex()) {
            val input = chunk.mapIndexed { i, idx -> "${i + 1}. ${parsed[idx].text.take(200)}" }.joinToString("\n")
            var result: ChunkAnswer? = null
            for (attempt in 0 until 2) {
                val answer = nano.generate(
                    prefix = prefix,
                    input = "Lines:\n$input\n",
                    maxOutputTokens = 60 + chunk.size * 40,
                    temperature = 0.1f,
                    timeoutMs = 10_000L,
                ) ?: continue
                result = parseChunk(answer, chunk.size)
                if (result != null) break
            }
            if (chunkNo == 0 && result?.alreadyInTarget == true) return Result.success(ALREADY_IN_TARGET)
            result?.lines?.forEachIndexed { i, line -> translations[chunk[i]] = line }
            if (result == null && chunkNo == 0) {
                return Result.failure(IllegalStateException("Gemini Nano couldn't translate these lyrics right now. Try again in a moment."))
            }
        }

        val out = buildString {
            parsed.forEachIndexed { i, line ->
                if (line.text.isBlank()) {
                    appendLine(line.original)
                    return@forEachIndexed
                }
                appendLine(line.original)
                val translated = translations[i]
                if (!translated.isNullOrBlank() && !translated.equals(line.text.trim(), ignoreCase = true)) {
                    appendLine(if (line.timestamp != null) "${line.timestamp} $translated" else translated)
                }
            }
        }.trimEnd()
        return Result.success(out)
    }

    private data class Line(val original: String, val timestamp: String?, val text: String)

    private fun splitLine(raw: String): Line {
        val m = TIMESTAMP.find(raw)
        return if (m != null) Line(raw, m.groupValues[1], m.groupValues[2]) else Line(raw, null, raw.trim())
    }

    internal data class ChunkAnswer(val alreadyInTarget: Boolean, val lines: List<String>?)

    companion object {
        const val ALREADY_IN_TARGET = "ALREADY_IN_TARGET_LANGUAGE"
        const val NOT_READY = "Gemini Nano isn't ready yet. Download it in Settings › AI to translate lyrics on your phone."
        private const val CHUNK_LINES = 18
        private val TIMESTAMP = Regex("^\\s*(\\[[0-9:.]+\\])\\s?(.*)$")
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Fixed per language, so AICore can cache it. Under 150 words. */
        internal fun prefixFor(language: String): String = """
            You translate song lyrics into $language. Keep the meaning, tone and line breaks; don't explain.
            You get numbered lines. Reply with JSON only:
            {"same":true} if the lines are already in $language, otherwise
            {"lines":["…","…"]} with exactly one translated line per numbered line, in order.
        """.trimIndent()

        /** Pure: Nano's answer for a chunk of [expected] lines, or null when it doesn't line up. */
        internal fun parseChunk(answer: String, expected: Int): ChunkAnswer? {
            val start = answer.indexOf('{')
            val end = answer.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val obj = runCatching { json.parseToJsonElement(answer.substring(start, end + 1)) as? JsonObject }
                .getOrNull() ?: return null
            if ((obj["same"] as? JsonPrimitive)?.booleanOrNull == true) return ChunkAnswer(true, null)
            val lines = (obj["lines"] as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull?.trim().orEmpty() }
                ?: return null
            // Some answers number the lines again ("1. …"): strip that.
            val cleaned = lines.map { it.replace(Regex("^\\d+[.)]\\s*"), "") }
            return if (cleaned.size == expected) ChunkAnswer(false, cleaned) else null
        }
    }
}
