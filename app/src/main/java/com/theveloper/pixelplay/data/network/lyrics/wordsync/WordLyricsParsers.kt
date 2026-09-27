package com.theveloper.pixelplay.data.network.lyrics.wordsync

import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import org.json.JSONArray
import kotlin.math.roundToInt

/**
 * Parsers for the word-timed formats returned by the free lyric sources. Every parser returns
 * [Lyrics] whose words carry both start and end times, already passed through [normalize] so the
 * result satisfies `LyricsTiming.validate()` and can be stored as native timing JSON.
 */
internal object WordLyricsParsers {

    /** A token of timed text before it is attached to a line. */
    private data class Token(val start: Int, val end: Int, val text: String)

    // ── NetEase YRC ────────────────────────────────────────────────────────
    // [lineStart,lineDur](wordStart,wordDur,0)Word (wordStart,wordDur,0)next
    private val YRC_LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val YRC_WORD = Regex("""\((\d+),(\d+),-?\d+\)(.*?)(?=\(\d+,\d+,-?\d+\)|$)""")

    fun parseYrc(raw: String): Lyrics? {
        val lines = raw.lineSequence().mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("{")) return@mapNotNull null
            val m = YRC_LINE.matchEntire(line) ?: return@mapNotNull null
            val lineStart = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val lineDur = m.groupValues[2].toIntOrNull() ?: 0
            val tokens = YRC_WORD.findAll(m.groupValues[3]).mapNotNull { w ->
                val start = w.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val dur = w.groupValues[2].toIntOrNull() ?: 0
                Token(start, start + dur, w.groupValues[3])
            }.toList()
            buildLine(lineStart, lineStart + lineDur, tokens)
        }.toList()
        return finish(lines)
    }

    // ── QQ Music QRC ───────────────────────────────────────────────────────
    // [lineStart,lineDur]Word (wordStart,wordDur)next(wordStart,wordDur)
    private val QRC_LYRIC_CONTENT = Regex("""LyricContent\s*=\s*"(.*?)"\s*/?>""", RegexOption.DOT_MATCHES_ALL)
    private val QRC_WORD = Regex("""(.*?)\((\d+),(\d+)\)""", RegexOption.DOT_MATCHES_ALL)

    /** Accepts either the decrypted XML wrapper or bare QRC text. */
    fun parseQrc(decrypted: String): Lyrics? {
        val body = QRC_LYRIC_CONTENT.find(decrypted)?.groupValues?.get(1)?.let(::unescapeXml) ?: decrypted
        val lines = body.lineSequence().mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@mapNotNull null
            val m = YRC_LINE.matchEntire(line) ?: return@mapNotNull null
            val lineStart = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val lineDur = m.groupValues[2].toIntOrNull() ?: 0
            val tokens = QRC_WORD.findAll(m.groupValues[3]).mapNotNull { w ->
                val start = w.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val dur = w.groupValues[3].toIntOrNull() ?: 0
                Token(start, start + dur, w.groupValues[1])
            }.toList()
            buildLine(lineStart, lineStart + lineDur, tokens)
        }.toList()
        return finish(lines)
    }

    // ── Kugou KRC (decrypted) ──────────────────────────────────────────────
    // [lineStart,lineDur]<offsetFromLine,wordDur,0>Word <offset,dur,0>next
    private val KRC_WORD = Regex("""<(\d+),(\d+),-?\d+>(.*?)(?=<\d+,\d+,-?\d+>|$)""")
    private val KRC_OFFSET = Regex("""^\[offset:\s*(-?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parseKrc(decrypted: String): Lyrics? {
        val offset = decrypted.lineSequence().firstNotNullOfOrNull { KRC_OFFSET.find(it.trim())?.groupValues?.get(1)?.toIntOrNull() } ?: 0
        val lines = decrypted.lineSequence().mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@mapNotNull null
            val m = YRC_LINE.matchEntire(line) ?: return@mapNotNull null
            val lineStart = (m.groupValues[1].toIntOrNull() ?: return@mapNotNull null) + offset
            val lineDur = m.groupValues[2].toIntOrNull() ?: 0
            val tokens = KRC_WORD.findAll(m.groupValues[3]).mapNotNull { w ->
                val start = lineStart + (w.groupValues[1].toIntOrNull() ?: return@mapNotNull null)
                val dur = w.groupValues[2].toIntOrNull() ?: 0
                Token(start, start + dur, w.groupValues[3])
            }.toList()
            buildLine(lineStart, lineStart + lineDur, tokens)
        }.toList()
        return finish(lines)
    }

    // ── Musixmatch RichSync ────────────────────────────────────────────────
    // [{"ts":12.3,"te":15.1,"l":[{"c":"Hello","o":0.0},{"c":" ","o":0.42},…],"x":"Hello …"}, …]
    fun parseMusixmatchRichSync(richsyncBody: String): Lyrics? = runCatching {
        val array = JSONArray(richsyncBody)
        val lines = buildList {
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val ts = (entry.optDouble("ts", Double.NaN) * 1000).takeIf { !it.isNaN() }?.roundToInt() ?: continue
                val te = (entry.optDouble("te", Double.NaN) * 1000).takeIf { !it.isNaN() }?.roundToInt() ?: ts
                val chunks = entry.optJSONArray("l") ?: continue
                val starts = ArrayList<Pair<Int, String>>(chunks.length())
                for (c in 0 until chunks.length()) {
                    val chunk = chunks.optJSONObject(c) ?: continue
                    val text = chunk.optString("c")
                    val offset = (chunk.optDouble("o", 0.0) * 1000).roundToInt()
                    starts.add(ts + offset to text)
                }
                // A chunk ends where the next one starts (spaces are their own chunks).
                val tokens = starts.mapIndexed { index, (start, text) ->
                    val end = starts.getOrNull(index + 1)?.first ?: te
                    Token(start, maxOf(end, start + 1), text)
                }
                buildLine(ts, te, mergeSpaceChunks(tokens))?.let(::add)
            }
        }
        finish(lines)
    }.getOrNull()

    /** Folds whitespace-only chunks into the preceding word as a trailing space (word boundary). */
    private fun mergeSpaceChunks(tokens: List<Token>): List<Token> {
        val out = ArrayList<Token>(tokens.size)
        for (token in tokens) {
            if (token.text.isBlank()) {
                val last = out.removeLastOrNull()
                if (last != null) out.add(last.copy(text = last.text + " ")) else out.add(token.copy(text = " "))
            } else {
                out.add(token)
            }
        }
        return out
    }

    // ── Shared helpers ─────────────────────────────────────────────────────

    private fun buildLine(lineStart: Int, lineEnd: Int, tokens: List<Token>): SyncedLine? {
        val words = ArrayList<SyncedWord>(tokens.size)
        var pendingBoundary = true
        val display = StringBuilder()
        for (token in tokens) {
            val raw = token.text.replace(' ', ' ')
            val text = raw.trim()
            val leadingSpace = raw.firstOrNull()?.isWhitespace() == true
            if (text.isEmpty()) {
                if (raw.isNotEmpty()) pendingBoundary = true
                continue
            }
            val startsNew = words.isEmpty() || pendingBoundary || leadingSpace
            if (display.isNotEmpty() && startsNew) display.append(' ')
            display.append(text)
            words.add(
                SyncedWord(
                    time = token.start,
                    word = text,
                    startsNewWord = startsNew,
                    endTime = maxOf(token.end, token.start + 1)
                )
            )
            pendingBoundary = raw.lastOrNull()?.isWhitespace() == true
        }
        val lineText = display.toString().trim()
        if (lineText.isEmpty()) return null
        return SyncedLine(time = lineStart, line = lineText, words = words.takeIf { it.isNotEmpty() }, endTime = lineEnd)
    }

    private fun finish(lines: List<SyncedLine>): Lyrics? {
        val normalized = normalize(lines)
        if (normalized.isEmpty()) return null
        return Lyrics(plain = normalized.map { it.line }, synced = normalized, areFromRemote = true)
    }

    /**
     * Makes provider timing internally consistent:
     *  - lines sorted, words sorted inside a line;
     *  - every word has `endTime > time` and never overlaps the next word's start by more than it lasts;
     *  - the line starts no later than its first word and ends no earlier than its last word.
     */
    fun normalize(lines: List<SyncedLine>): List<SyncedLine> =
        lines.asSequence()
            .filter { it.line.isNotBlank() }
            .map { line ->
                val words = line.words.orEmpty()
                    .filter { it.word.isNotBlank() && it.time >= 0 }
                    .sortedBy { it.time }
                    .map { w -> w.copy(endTime = maxOf(w.endTime ?: (w.time + 1), w.time + 1)) }
                val first = words.firstOrNull()?.time
                val lineStart = if (first != null) minOf(line.time, first) else line.time
                val lastEnd = words.maxOfOrNull { it.endTime ?: it.time } ?: 0
                val lineEnd = listOfNotNull(line.endTime, lastEnd.takeIf { it > 0 }).maxOrNull()
                    ?.takeIf { it > lineStart }
                line.copy(
                    time = lineStart.coerceAtLeast(0),
                    words = words.takeIf { it.isNotEmpty() },
                    endTime = lineEnd
                )
            }
            .sortedBy { it.time }
            .toList()

    /** True when enough of the song has real word timing to be worth preferring over line LRC. */
    fun isWordTimed(lyrics: Lyrics?): Boolean {
        val lines = lyrics?.synced?.filter { it.line.isNotBlank() } ?: return false
        if (lines.size < MIN_LINES) return false
        val timed = lines.count { line -> line.words.orEmpty().any { it.endTime != null } }
        if (timed < MIN_LINES) return false
        // Songs where every line is a single "word" are really line timing in disguise.
        val multiWordLines = lines.count { (it.words?.size ?: 0) >= 2 }
        if (multiWordLines < MIN_LINES / 2) return false
        return timed.toFloat() / lines.size >= MIN_TIMED_FRACTION
    }

    private const val MIN_LINES = 4
    private const val MIN_TIMED_FRACTION = 0.6f

    private fun unescapeXml(value: String): String =
        value.replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#10;", "\n")
            .replace("&#13;", "")
            .replace("&amp;", "&")
}
