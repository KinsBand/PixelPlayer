package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import java.text.Normalizer
import java.util.Locale

/**
 * Removes the header and footer rows that lyrics providers put around the actual lyrics:
 * "After Hours - The Velvet Underground", "Lyrics by：Lou Reed", "Composed by：Lou Reed",
 * "作词 : …", "Produced by: …", copyright notices and the like.
 *
 * Only the leading and trailing runs are cleaned (never lines in the middle of the song), so a
 * lyric that happens to contain "by:" is safe. Blank lines stay, since a blank synced line marks
 * an instrumental section.
 */
object LyricsCleanup {
    private val CREDIT = Regex(
        "^\\s*[(\\[（【]?\\s*(?:" +
            // Chinese credit keys (NetEase / QQ / Kugou)
            "作词|作詞|作曲|编曲|編曲|词|詞|曲|制作人|製作人|制作|製作|监制|監製|混音|母带|母帶|和声|和聲|和音|" +
            "吉他|贝斯|貝斯|鼓|键盘|鍵盤|弦乐|弦樂|录音|錄音|混音师|出品|发行|發行|企划|統籌|统筹|演唱|原唱|翻唱|歌手|" +
            "OP|SP|ISRC|" +
            // English credit keys
            "lyrics?|lyricists?|lyrics?\\s+by|words?(?:\\s+and\\s+music)?\\s+by|written\\s+by|writers?|songwriters?|" +
            "composed\\s+by|composers?|composition|music(?:\\s+by)?|arranged\\s+by|arrangers?|arrangement|" +
            "produced\\s+by|producers?|production|executive\\s+producers?|" +
            "mix(?:ed|ing)?(?:\\s+(?:by|engineer))?|master(?:ed|ing)?(?:\\s+(?:by|engineer))?|" +
            "recorded\\s+by|recording(?:\\s+engineer)?|engineer(?:ed)?(?:\\s+by)?|vocals?(?:\\s+by)?|" +
            "backing\\s+vocals?|guitars?|bass|drums|keyboards?|strings|programming|" +
            "original\\s+(?:artist|song|singer)|performed\\s+by|publisher|published\\s+by|label|" +
            "(?:lrc|lyrics?)\\s+(?:made|edited|synced|timed)\\s+by|transcribed\\s+by|" +
            "sync(?:ed)?\\s+by|timing\\s+by" +
            ")\\s*[:：]",
        RegexOption.IGNORE_CASE
    )

    /** Copyright / "don't cover without permission" notices (QQ, NetEase, Kugou). */
    private val NOTICE = Regex(
        "未经.{0,20}许可|不得翻唱|翻录|版权所有|著作权|All rights reserved|Lyrics (?:provided|licensed) by|" +
            "^\\s*(?:QQ音乐|酷狗音乐|网易云音乐|TME)\\b",
        RegexOption.IGNORE_CASE
    )

    /** "Title - Artist" (also with an en or em dash). */
    private val TITLE_ARTIST = Regex("^.{1,80}\\s[-–—]\\s.{1,80}$")

    /** Only the first / last few non-blank lines are looked at. */
    private const val MAX_HEADER_LINES = 10
    private const val MAX_FOOTER_LINES = 6

    fun isCreditLine(text: String): Boolean {
        val t = text.trim()
        return t.isNotEmpty() && t.length <= 160 && (CREDIT.containsMatchIn(t) || NOTICE.containsMatchIn(t))
    }

    /** Cleans both the synced and the plain lyrics. [title] / [artist] help spot the title row. */
    fun clean(lyrics: Lyrics, title: String? = null, artist: String? = null): Lyrics {
        val synced = lyrics.synced?.let { cleanSynced(it, title, artist) }
        val plain = lyrics.plain?.let { cleanPlain(it, title, artist) }
        if (synced === lyrics.synced && plain === lyrics.plain) return lyrics
        return lyrics.copy(synced = synced, plain = plain)
    }

    fun cleanSynced(lines: List<SyncedLine>, title: String? = null, artist: String? = null): List<SyncedLine> {
        if (lines.isEmpty()) return lines
        val drop = droppedIndices(lines.map { it.line }, title, artist)
        // A translation that is only a credit ("by: …") isn't worth showing either.
        val cleanedTranslations = lines.any { it.translation?.let(::isTranslationCredit) == true }
        if (drop.isEmpty() && !cleanedTranslations) return lines
        return lines.filterIndexed { index, _ -> index !in drop }.map { line ->
            if (line.translation?.let(::isTranslationCredit) == true) line.copy(translation = null) else line
        }
    }

    fun cleanPlain(lines: List<String>, title: String? = null, artist: String? = null): List<String> {
        if (lines.isEmpty()) return lines
        // Plain entries built from synced lines carry romanization / translation after a
        // newline; the lyric itself is the first line.
        val drop = droppedIndices(lines.map { it.substringBefore('\n') }, title, artist)
        if (drop.isEmpty()) return lines
        return lines.filterIndexed { index, _ -> index !in drop }
    }

    private fun isTranslationCredit(text: String): Boolean =
        text.lines().all { it.isBlank() || isCreditLine(it) || it.trim().matches(Regex("^by\\s*[:：].*", RegexOption.IGNORE_CASE)) }

    private fun droppedIndices(texts: List<String>, title: String?, artist: String?): Set<Int> {
        val drop = HashSet<Int>()
        val normTitle = title?.let(::normalize)?.takeIf { it.length >= 2 }
        val normArtist = artist?.let(::normalize)?.takeIf { it.length >= 2 && it != "unknownartist" }

        fun isTitleRow(text: String): Boolean {
            val n = normalize(text)
            if (n.isEmpty() || normTitle == null) return false
            if (n == normTitle) return true
            if (!n.contains(normTitle)) return false
            // "Title - Artist", "Artist - Title", "Title (by Artist)"…
            return (normArtist != null && n.contains(normArtist)) || TITLE_ARTIST.matches(text.trim())
        }

        // Header: credits and a title row, before the first real lyric.
        val headerTitleCandidates = mutableListOf<Int>()
        var sawCredit = false
        var seen = 0
        var i = 0
        while (i < texts.size && seen < MAX_HEADER_LINES) {
            val text = texts[i].trim()
            if (text.isEmpty()) { i++; continue }
            seen++
            when {
                isCreditLine(text) -> { drop += i; sawCredit = true }
                isTitleRow(text) -> drop += i
                // "Something - Someone" with no song info: dropped only if credits surround it.
                TITLE_ARTIST.matches(text) && headerTitleCandidates.isEmpty() -> headerTitleCandidates += i
                else -> break
            }
            i++
        }
        if (sawCredit) drop += headerTitleCandidates

        // Footer: credits and notices after the last real lyric.
        seen = 0
        var j = texts.lastIndex
        while (j >= 0 && seen < MAX_FOOTER_LINES && j !in drop) {
            val text = texts[j].trim()
            if (text.isEmpty()) { j--; continue }
            seen++
            if (isCreditLine(text)) drop += j else break
            j--
        }

        // Never remove everything: if all that's left would be blank, keep the original.
        val remaining = texts.indices.filter { it !in drop }
        if (remaining.none { texts[it].isNotBlank() }) return emptySet()
        return drop
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }
}
