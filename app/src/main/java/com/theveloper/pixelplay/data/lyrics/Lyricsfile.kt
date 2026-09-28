package com.theveloper.pixelplay.data.lyrics

import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsTimingEvidence
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.schema.CoreSchema
import kotlin.math.roundToLong

/**
 * Lyricsfile 1.0 (https://lrclib.net/lyricsfile), the YAML lyrics format of LRCGET and LRCLIB:
 * plain, line-synced and word-synced lyrics in one document. Every LRCLIB record carries one,
 * and it is the only LRCLIB field that can hold word timing.
 *
 * Reading follows the format's rules: times are milliseconds; when a line has `words` they are
 * what is shown, each word carrying its own trailing space; `offset_ms` moves every time by
 * that much; an instrumental document has no lyrics. Unknown keys are ignored, so documents
 * with newer or tool-specific fields still load.
 */
object Lyricsfile {
    const val VERSION = "1.0"

    /** Sidecar extension used next to audio files (`song.lyrics`). */
    const val FILE_EXTENSION = "lyrics"

    /** Larger than any real song; the YAML parser also stops here. */
    const val MAX_CHARS = 1_000_000
    private const val MAX_LINES = 5_000
    private const val MAX_WORDS_PER_LINE = 1_000

    data class Metadata(
        val title: String = "",
        val artist: String = "",
        val album: String? = null,
        val durationMs: Long? = null,
        /** Already applied to every time in [Document.lyrics]. */
        val offsetMs: Long = 0,
        val language: String? = null,
        val instrumental: Boolean = false
    )

    class Document(val metadata: Metadata, val lyrics: Lyrics) {
        val hasWordTiming: Boolean
            get() = lyrics.synced.orEmpty().any { !it.words.isNullOrEmpty() }
    }

    private val VERSION_KEY = Regex("""(?m)^["']?version["']?[ \t]*:[ \t]*["']?\d""")
    private val BODY_KEY = Regex("""(?m)^["']?(metadata|lines|plain)["']?[ \t]*:""")
    private val METADATA_KEY = Regex("""(?m)^["']?metadata["']?[ \t]*:""")

    private val loadSettings: LoadSettings = LoadSettings.builder()
        .setSchema(CoreSchema())
        .setCodePointLimit(MAX_CHARS + 1)
        // Lyrics never need aliases; this also rules out "billion laughs" documents.
        .setMaxAliasesForCollections(10)
        .setAllowRecursiveKeys(false)
        .setUseMarks(false)
        .build()

    private val dumpSettings: DumpSettings = DumpSettings.builder()
        .setDefaultFlowStyle(FlowStyle.BLOCK)
        .setIndent(2)
        .setIndicatorIndent(2)
        .setIndentWithIndicator(true)
        // Keep every lyric line on one line so the file stays easy to edit by hand.
        .setSplitLines(false)
        .build()

    /**
     * Cheap check before parsing: a top-level `version` plus one of the document keys. LRC,
     * TTML, timing JSON and plain lyrics don't start lines with those. The JSON form (YAML is a
     * superset of JSON) is recognised by its keys.
     */
    fun looksLikeLyricsfile(text: String): Boolean {
        val head = text.trimStart { it.isWhitespace() || it == '﻿' }
        if (head.startsWith("{")) return head.contains("\"version\"") && head.contains("\"metadata\"")
        return VERSION_KEY.containsMatchIn(head) && BODY_KEY.containsMatchIn(head)
    }

    /**
     * True when [text] is certainly meant as a Lyricsfile (YAML `version` and `metadata` keys),
     * so a parse failure means a broken file rather than some other format.
     */
    fun isDefinitelyLyricsfile(text: String): Boolean {
        val head = text.trimStart { it.isWhitespace() || it == '﻿' }
        return !head.startsWith("{") && VERSION_KEY.containsMatchIn(head) && METADATA_KEY.containsMatchIn(head)
    }

    /** The document in [text], or null when it isn't a readable Lyricsfile 1.x. */
    fun parse(text: String): Document? {
        if (text.length > MAX_CHARS) return null
        val root = try {
            Load(loadSettings).loadFromString(text.trimStart('﻿'))
        } catch (e: Exception) {
            return null
        } catch (e: StackOverflowError) {
            return null
        } as? Map<*, *> ?: return null
        if (!isVersionOne(root["version"])) return null
        val meta = root["metadata"] as? Map<*, *>
        if (meta == null && root["lines"] == null && root["plain"] == null) return null
        val metadata = readMetadata(meta ?: emptyMap<Any?, Any?>())
        if (metadata.instrumental) return Document(metadata, Lyrics(plain = emptyList(), synced = emptyList()))

        val synced = ArrayList<SyncedLine>()
        val allTexts = ArrayList<String>()
        (root["lines"] as? List<*>).orEmpty().asSequence().take(MAX_LINES).forEach { item ->
            val line = item as? Map<*, *> ?: return@forEach
            val words = readWords(line["words"], metadata.offsetMs)
            val text = words?.text ?: line.string("text").orEmpty()
            allTexts += text
            val start = line.long("start_ms")?.let { shift(it, metadata.offsetMs) } ?: return@forEach
            val end = line.long("end_ms")?.let { shift(it, metadata.offsetMs) }?.takeIf { it > start }
            synced += buildLine(start, end, text, words?.words)
        }

        val timing = LyricsTimingEvidence(source = SOURCE, language = metadata.language)
        if (synced.isNotEmpty()) {
            // Stable: lines that share a start keep their order (a translation follows its line).
            val lines = synced.sortedBy { it.time }
            return Document(metadata, Lyrics(plain = lines.map { it.line }, synced = lines, timing = timing))
        }
        val plain = root.string("plain")?.takeIf { it.isNotBlank() }
            ?.replace("\r\n", "\n")?.trimEnd()?.lines()
            ?: allTexts.takeIf { texts -> texts.any { it.isNotBlank() } }
            ?: emptyList()
        return Document(metadata, Lyrics(plain = plain, synced = null, timing = timing))
    }

    /**
     * Writes [lyrics] as a Lyricsfile. Word texts get the trailing space the format expects
     * whenever the next word starts a new word, so joined words give back the line. A line's
     * translation is written as a second line with the same times, as LRCLIB does for LRC with
     * translations, and read back as that line's translation. Romanization is left out: it is
     * generated when lyrics are read.
     */
    fun serialize(lyrics: Lyrics, metadata: Metadata): String {
        val synced = lyrics.synced.orEmpty().sortedBy { it.time }
        val document = LinkedHashMap<String, Any?>()
        document["version"] = VERSION
        document["metadata"] = LinkedHashMap<String, Any?>().apply {
            put("title", metadata.title)
            put("artist", metadata.artist)
            metadata.album?.takeIf { it.isNotBlank() }?.let { put("album", it) }
            metadata.durationMs?.takeIf { it > 0 }?.let { put("duration_ms", it) }
            (metadata.language ?: lyrics.timing?.language)?.takeIf { it.isNotBlank() }?.let { put("language", it) }
            put("instrumental", metadata.instrumental)
        }
        if (metadata.instrumental) {
            document["lines"] = emptyList<Any>()
            return Dump(dumpSettings).dumpToString(document)
        }
        document["lines"] = synced.flatMap { line ->
            buildList {
                add(lineEntry(line.line, line.time, line.endTime, line.words))
                line.translation?.lines()?.filter { it.isNotBlank() }?.forEach { translation ->
                    add(lineEntry(translation, line.time, line.endTime, words = null))
                }
            }
        }
        val plainLines = if (synced.isNotEmpty()) {
            synced.map { it.line }
        } else {
            // Parsed lyrics may carry a generated romanization after the first line break.
            lyrics.plain.orEmpty().map { it.substringBefore('\n') }
        }
        plainLines.joinToString("\n").takeIf { it.isNotBlank() }?.let { document["plain"] = it }
        return Dump(dumpSettings).dumpToString(document)
    }

    const val SOURCE = "lyricsfile"

    private fun lineEntry(text: String, start: Int, end: Int?, words: List<SyncedWord>?): Map<String, Any?> =
        LinkedHashMap<String, Any?>().apply {
            put("text", text)
            put("start_ms", start.toLong())
            end?.takeIf { it > start }?.let { put("end_ms", it.toLong()) }
            if (!words.isNullOrEmpty()) {
                put("words", words.mapIndexed { index, word ->
                    val next = words.getOrNull(index + 1)
                    LinkedHashMap<String, Any?>().apply {
                        put("text", if (next != null && next.startsNewWord) word.word + " " else word.word)
                        put("start_ms", word.time.toLong())
                        word.endTime?.takeIf { it > word.time }?.let { put("end_ms", it.toLong()) }
                    }
                })
            }
        }

    private class Words(val text: String, val words: List<SyncedWord>?)

    /**
     * Word list of a line. The text is always the joined word texts (the format says words
     * replace `text` for display); timing is kept only when every word has a start.
     */
    private fun readWords(raw: Any?, offsetMs: Long): Words? {
        val items = (raw as? List<*>)?.takeIf { it.isNotEmpty() } ?: return null
        val tokens = items.asSequence().take(MAX_WORDS_PER_LINE).mapNotNull { it as? Map<*, *> }.toList()
        if (tokens.isEmpty()) return null
        val text = tokens.joinToString("") { it.string("text").orEmpty() }.trim()
        if (text.isEmpty()) return null
        val starts = tokens.map { token -> token.long("start_ms")?.let { shift(it, offsetMs) } }
        if (starts.any { it == null }) return Words(text, null)

        val words = ArrayList<SyncedWord>(tokens.size)
        var pendingBoundary = true
        tokens.forEachIndexed { index, token ->
            val raw = token.string("text").orEmpty()
            val word = raw.trim()
            if (word.isEmpty()) {
                if (raw.isNotEmpty()) pendingBoundary = true
                return@forEachIndexed
            }
            val start = starts[index]!!
            // A word without an end runs until the next word starts.
            val end = token.long("end_ms")?.let { shift(it, offsetMs) }?.takeIf { it > start }
                ?: starts.drop(index + 1).firstOrNull { it!! > start }
            words += SyncedWord(
                time = start,
                word = word,
                startsNewWord = words.isEmpty() || pendingBoundary || raw.first().isWhitespace(),
                endTime = end
            )
            pendingBoundary = raw.last().isWhitespace()
        }
        return Words(text, words.sortedBy { it.time }.takeIf { it.isNotEmpty() })
    }

    /** Keeps word timing inside the line: the line starts with its first word and outlasts its last. */
    private fun buildLine(start: Int, end: Int?, text: String, words: List<SyncedWord>?): SyncedLine {
        if (words.isNullOrEmpty()) return SyncedLine(time = start, line = text, endTime = end)
        val lineStart = minOf(start, words.first().time)
        val lineEnd = end?.let { maxOf(it, words.maxOf { word -> word.endTime ?: word.time }) }
        return SyncedLine(time = lineStart, line = text, words = words, endTime = lineEnd?.takeIf { it > lineStart })
    }

    private fun readMetadata(meta: Map<*, *>): Metadata = Metadata(
        title = meta.string("title").orEmpty(),
        artist = meta.string("artist").orEmpty(),
        album = meta.string("album")?.takeIf { it.isNotBlank() },
        durationMs = meta.long("duration_ms")?.takeIf { it > 0 },
        offsetMs = meta.long("offset_ms")?.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS) ?: 0,
        language = meta.string("language")?.trim()?.takeIf { it.isNotEmpty() },
        instrumental = when (val value = meta["instrumental"]) {
            is Boolean -> value
            is String -> value.trim().equals("true", ignoreCase = true)
            else -> false
        }
    )

    private const val MAX_OFFSET_MS = 10L * 60 * 1000

    private fun isVersionOne(value: Any?): Boolean = when (value) {
        is Number -> value.toDouble().let { it >= 1.0 && it < 2.0 }
        is String -> value.trim().substringBefore('.') == "1"
        else -> false
    }

    /** Applies the document offset and keeps the time a valid, non-negative Int. */
    private fun shift(ms: Long, offsetMs: Long): Int? {
        val shifted = ms + offsetMs
        if (ms < 0 || shifted > Int.MAX_VALUE) return null
        return shifted.coerceAtLeast(0).toInt()
    }

    private fun Map<*, *>.string(key: String): String? = when (val value = this[key]) {
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> null
    }

    private fun Map<*, *>.long(key: String): Long? = when (val value = this[key]) {
        is Double -> value.takeIf { it.isFinite() }?.roundToLong()
        is Float -> value.takeIf { it.isFinite() }?.roundToLong()
        is java.math.BigInteger -> value.takeIf { it.bitLength() < 63 }?.toLong()
        is Number -> value.toLong()
        is String -> value.trim().let { it.toLongOrNull() ?: it.toDoubleOrNull()?.takeIf(Double::isFinite)?.roundToLong() }
        else -> null
    }
}
