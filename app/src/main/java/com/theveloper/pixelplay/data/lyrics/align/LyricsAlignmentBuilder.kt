package com.theveloper.pixelplay.data.lyrics.align

import com.theveloper.pixelplay.data.lyrics.LyricsTiming
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsTimingEvidence
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedPhoneme
import com.theveloper.pixelplay.data.model.SyncedWord
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns line-synced lyrics plus the song's CTC emissions into word- and letter-synced lyrics.
 *
 * Each line is aligned inside a window around its source time (shifted by the song's known
 * offset), never earlier than where the previous aligned line ended. Every line is then checked;
 * a line that fails keeps its source timing (shifted by the offset), so the result is never worse
 * than the input:
 *
 *  - **Letter** level (measured letter spans, `alphabet = "grapheme"`): the line's letters scored
 *    well enough to trust their individual timing.
 *  - **Word** level (word start/end only): the words land plausibly but letters are shaky.
 *  - **Line** level (source timing): not English, too little alignable text, too far from the
 *    source time, implausible word lengths, or low confidence.
 *
 * All times in the result are in **audio time** (the player position), so the song's automatic
 * offset no longer applies once these lyrics are shown. Pure Kotlin.
 */
object LyricsAlignmentBuilder {

    const val SOURCE = "on-device-align"

    data class Config(
        /** Audio before a line's source start that may hold its first word. */
        val leadPadMs: Int = 2_500,
        /** Audio after the next line's source start that may still hold this line's last word. */
        val tailPadMs: Int = 700,
        /** Longest window for a single line. */
        val maxWindowMs: Int = 30_000,
        /** A line further than this from its (offset-corrected) source start is rejected. */
        val maxLineShiftMs: Int = 3_000,
        val minEnglishShare: Float = 0.9f,
        /** Lines with more unalignable words than this share keep line timing. */
        val maxUnalignableShare: Float = 0.4f,
        val minWordConfidence: Float = 0.30f,
        val minLetterConfidence: Float = 0.50f,
        /** Any alignable word sung longer than this means the alignment wandered. */
        val maxWordMs: Int = 6_000,
        /** Last word of a line may hold this long past its last letter at most. */
        val maxTailHoldMs: Int = 1_500,
        /** Share of eligible lines that must align for the song to be published at all. */
        val minAlignedShare: Float = 0.4f,
        val starPenalty: Float = -0.6f,
        /** How far either side of the source the shift probe looks. */
        val probeRangeMs: Int = 10_000
    )

    enum class Level { LETTER, WORD, LINE }

    class LineOutcome(val index: Int, val level: Level, val reason: String, val confidence: Float)

    class Result(
        /** Null when too few lines aligned to be worth showing (the song keeps its source lyrics). */
        val lyrics: Lyrics?,
        val outcomes: List<LineOutcome>,
        val reason: String
    ) {
        val letterLines: Int get() = outcomes.count { it.level == Level.LETTER }
        val wordLines: Int get() = outcomes.count { it.level == Level.WORD }
        val lineLines: Int get() = outcomes.count { it.level == Level.LINE }
    }

    /**
     * @param sourceOffsetMs the offset the player would add to this song's lyrics (positive =
     *   lyrics shown earlier): audio time = lyric time − offset.
     * @param durationMs song length, used to bound the last line's window.
     */
    fun build(
        lyrics: Lyrics,
        emissions: CtcEmissions,
        sourceOffsetMs: Int,
        durationMs: Long,
        modelVersion: String,
        vocab: CtcVocabulary = CtcVocabulary.WAV2VEC2_960H,
        config: Config = Config()
    ): Result {
        val lines = lyrics.synced.orEmpty()
        if (lines.isEmpty()) return Result(null, emptyList(), "no synced lines")
        val audioEndMs = min(durationMs.toDouble().takeIf { it > 0 } ?: Double.MAX_VALUE, emissions.msAt(emissions.frames))

        // The source lyrics may be further off than the known offset says (no offset measured
        // yet, or measured wrong): probe a few lines in wide windows first and correct by the
        // shift they agree on, so the per-line windows land on the singing.
        val probe = probeShift(lines, sourceOffsetMs, emissions, audioEndMs, vocab, config)
        val offsetMs = sourceOffsetMs - (probe ?: 0)
        // Source starts in audio time.
        val src = IntArray(lines.size) { lines[it].time - offsetMs }
        val out = ArrayList<SyncedLine>(lines.size)
        val outcomes = ArrayList<LineOutcome>(lines.size)
        var floorMs = 0.0            // where the previous aligned line ended
        var lastLineStart = Int.MIN_VALUE
        var eligible = 0

        for ((i, line) in lines.withIndex()) {
            val shifted = shiftLine(line, offsetMs, lastLineStart)
            if (line.line.isBlank()) {
                out += shifted; lastLineStart = shifted.time
                outcomes += LineOutcome(i, Level.LINE, "blank", 0f)
                continue
            }
            val norm = AlignmentTextNormalizer.normalize(line.line, vocab)
            if (norm.englishLetterShare < config.minEnglishShare || norm.alignableWords == 0) {
                out += shifted; lastLineStart = shifted.time
                outcomes += LineOutcome(i, Level.LINE, "not English", 0f)
                continue
            }
            eligible++
            val unalignable = norm.words.size - norm.alignableWords
            if (unalignable.toFloat() / norm.words.size > config.maxUnalignableShare) {
                out += shifted; lastLineStart = shifted.time
                outcomes += LineOutcome(i, Level.LINE, "too many unalignable words", 0f)
                continue
            }

            val nextSrc = (i + 1 until lines.size).firstOrNull { lines[it].line.isNotBlank() }?.let { src[it] }
            val srcEnd = nextSrc ?: line.endTime?.let { it - offsetMs } ?: (src[i] + 8_000)
            // A little overlap with the previous line: its last word's hold is an upper bound.
            val fromMs = max(src[i] - config.leadPadMs.toDouble(), floorMs - FLOOR_OVERLAP_MS).coerceAtLeast(0.0)
            var toMs = min(max(srcEnd, src[i] + 1_000) + config.tailPadMs.toDouble(), audioEndMs)
            toMs = min(toMs, fromMs + config.maxWindowMs)

            val attempt = alignLine(line, norm, emissions, fromMs, toMs, src[i], vocab, config)
            if (attempt.line == null || attempt.line.time < lastLineStart) {
                out += shifted; lastLineStart = shifted.time
                outcomes += LineOutcome(i, Level.LINE, attempt.reason.ifEmpty { "out of order" }, attempt.confidence)
                continue
            }
            out += attempt.line
            lastLineStart = attempt.line.time
            floorMs = (attempt.line.endTime ?: attempt.line.time).toDouble()
            outcomes += LineOutcome(i, attempt.level, attempt.reason, attempt.confidence)
        }

        val aligned = outcomes.count { it.level != Level.LINE }
        if (eligible == 0) return Result(null, outcomes, "no English lines")
        if (aligned < 2 || aligned < eligible * config.minAlignedShare) {
            return Result(null, outcomes, "only $aligned of $eligible lines aligned")
        }

        val confidences = outcomes.filter { it.level != Level.LINE }.map { it.confidence }
        val result = lyrics.copy(
            synced = out,
            timing = LyricsTimingEvidence(
                lyricsHash = LyricsTiming.textHash(lyrics),
                source = SOURCE,
                modelVersion = modelVersion,
                language = "en",
                confidence = confidences.average().toFloat(),
                verified = false,
                offsetMs = 0
            )
        )
        val problems = LyricsTiming.validate(result)
        if (problems.isNotEmpty()) return Result(null, outcomes, "invalid timing: ${problems.first()}")
        val probeNote = probe?.let { "; source shifted ${it} ms" } ?: ""
        return Result(result, outcomes, "$aligned of $eligible lines aligned$probeNote")
    }

    /**
     * Extra shift (ms, audio minus offset-corrected source) agreed on by the first few English
     * lines when each is aligned in a ±[Config.probeRangeMs] window, or null when they don't agree
     * or the shift is small enough for the normal windows to absorb.
     */
    private fun probeShift(
        lines: List<SyncedLine>,
        offsetMs: Int,
        em: CtcEmissions,
        audioEndMs: Double,
        vocab: CtcVocabulary,
        config: Config
    ): Int? {
        val wide = config.copy(maxLineShiftMs = config.probeRangeMs)
        val shifts = ArrayList<Int>()
        var tried = 0
        for ((i, line) in lines.withIndex()) {
            if (tried >= PROBE_LINES || line.line.isBlank()) continue
            val norm = AlignmentTextNormalizer.normalize(line.line, vocab)
            if (norm.englishLetterShare < config.minEnglishShare || norm.alignableWords < 3) continue
            tried++
            val srcMs = line.time - offsetMs
            val nextMs = (i + 1 until lines.size).firstOrNull { lines[it].line.isNotBlank() }?.let { lines[it].time - offsetMs }
            val lengthMs = ((nextMs ?: (srcMs + 6_000)) - srcMs).coerceIn(1_000, 12_000)
            val from = max(srcMs - config.probeRangeMs.toDouble(), 0.0)
            val to = min(srcMs + lengthMs + config.probeRangeMs.toDouble(), audioEndMs)
            val attempt = alignLine(line, norm, em, from, to, srcMs, vocab, wide)
            attempt.line?.let { shifts += it.time - srcMs }
        }
        if (shifts.size < 2) return null
        shifts.sort()
        val median = shifts[shifts.size / 2]
        val agreeing = shifts.count { abs(it - median) <= PROBE_AGREE_MS }
        if (agreeing < 2 || agreeing * 2 < shifts.size) return null
        return median.takeIf { abs(it) >= PROBE_MIN_MS }
    }

    /**
     * [lyrics] with every time moved by [deltaMs] (letters, words, lines). Used to pre-apply a
     * manual offset to aligned lyrics. A line pushed before 0 keeps only its line timing at 0.
     * Returns [lyrics] unchanged when [deltaMs] is 0 or the result would be invalid.
     */
    fun shift(lyrics: Lyrics, deltaMs: Int): Lyrics {
        if (deltaMs == 0) return lyrics
        var previous = 0
        val lines = lyrics.synced?.map { line ->
            val time = line.time + deltaMs
            if (time < 0 || (line.words?.firstOrNull()?.time ?: line.time) + deltaMs < 0) {
                return@map line.copy(time = previous, endTime = null, words = null).also { previous = it.time }
            }
            line.copy(
                time = time,
                endTime = line.endTime?.plus(deltaMs),
                words = line.words?.map { w ->
                    w.copy(
                        time = w.time + deltaMs,
                        endTime = w.endTime?.plus(deltaMs),
                        phonemes = w.phonemes?.map { p -> p.copy(time = p.time + deltaMs, endTime = p.endTime + deltaMs) }
                    )
                }
            ).also { previous = it.time }
        }
        val shifted = lyrics.copy(synced = lines)
        return if (LyricsTiming.validate(shifted).isEmpty()) shifted else lyrics
    }

    private class Attempt(val line: SyncedLine?, val level: Level, val reason: String, val confidence: Float)

    private fun alignLine(
        line: SyncedLine,
        norm: AlignLine,
        em: CtcEmissions,
        fromMs: Double,
        toMs: Double,
        srcStartMs: Int,
        vocab: CtcVocabulary,
        config: Config
    ): Attempt {
        // Transcript: alignable words separated by the word-separator token.
        val labels = ArrayList<Int>(norm.tokenCount + norm.words.size)
        val owner = ArrayList<Int>()   // for each label: word index, or -1 for a separator
        val tokenInWord = ArrayList<Int>()
        for ((w, word) in norm.words.withIndex()) {
            if (!word.alignable) continue
            if (labels.isNotEmpty()) { labels += vocab.wordSeparator; owner += -1; tokenInWord += -1 }
            for ((k, id) in word.tokens.withIndex()) { labels += id; owner += w; tokenInWord += k }
        }
        val from = em.frameAt(fromMs)
        val to = em.frameAt(toMs)
        val res = CtcForcedAligner.align(em, from, to, labels.toIntArray(), vocab.blank, config.starPenalty)
            ?: return Attempt(null, Level.LINE, "window too short", 0f)

        // Per word: letter spans in ms.
        class Letter(val char: Int, val startMs: Int, var endMs: Int, val confidence: Float)
        val letters = arrayOfNulls<MutableList<Letter>>(norm.words.size)
        for ((j, span) in res.spans.withIndex()) {
            val w = owner[j]
            if (w < 0) continue
            val word = norm.words[w]
            val startMs = em.msAt(span.firstFrame).roundToInt()
            val holdMs = em.msAt(res.holdUntilFrame[j]).roundToInt()
            val list = letters[w] ?: ArrayList<Letter>().also { letters[w] = it }
            list += Letter(word.charIndex[tokenInWord[j]], startMs, max(holdMs, startMs + 1), span.confidence)
        }

        // Letters of a word run back to back; the last one holds until the separator / next word.
        val lastAlignable = norm.words.indexOfLast { it.alignable }
        val wordStart = IntArray(norm.words.size) { -1 }
        val wordEnd = IntArray(norm.words.size) { -1 }
        val wordConf = FloatArray(norm.words.size)
        var letterSum = 0.0
        var letterCount = 0
        var minLetter = 1f
        for ((w, list) in letters.withIndex()) {
            if (list == null) continue
            for (k in 0 until list.size - 1) list[k].endMs = max(list[k + 1].startMs, list[k].startMs + 1)
            val lastLetter = list.last()
            val lastSungMs = em.msAt(res.spans.indices.last { owner[it] == w }.let { res.spans[it].lastFrame } + 1).roundToInt()
            val cap = if (w == lastAlignable) lastSungMs + config.maxTailHoldMs else Int.MAX_VALUE
            lastLetter.endMs = max(min(lastLetter.endMs, cap), lastLetter.startMs + 1)
            wordStart[w] = list.first().startMs
            wordEnd[w] = lastLetter.endMs
            wordConf[w] = list.map { it.confidence }.average().toFloat()
            list.forEach { letterSum += it.confidence; letterCount++; if (it.confidence < minLetter) minLetter = it.confidence }
            if (wordEnd[w] - wordStart[w] > config.maxWordMs) {
                return Attempt(null, Level.LINE, "word \"${norm.words[w].text}\" held ${wordEnd[w] - wordStart[w]} ms", 0f)
            }
        }

        val lineConf = (letterSum / letterCount.coerceAtLeast(1)).toFloat()
        val firstStart = wordStart.first { it >= 0 }
        if (abs(firstStart - srcStartMs) > config.maxLineShiftMs) {
            return Attempt(null, Level.LINE, "moved ${firstStart - srcStartMs} ms from source", lineConf)
        }
        val alignedWordConf = wordConf.filterIndexed { w, _ -> norm.words[w].alignable }
        val meanWordConf = alignedWordConf.average().toFloat()
        if (meanWordConf < config.minWordConfidence) {
            return Attempt(null, Level.LINE, "low confidence %.2f".format(meanWordConf), meanWordConf)
        }
        val letterLevel = lineConf >= config.minLetterConfidence &&
            alignedWordConf.all { it >= config.minWordConfidence }

        // Unalignable words borrow time between their aligned neighbours.
        fillGaps(wordStart, wordEnd, firstStart)

        val words = norm.words.mapIndexed { w, word ->
            val start = wordStart[w]
            val end = max(wordEnd[w], start + 1)
            val phonemes = if (letterLevel && word.alignable) {
                val list = letters[w]!!
                list.mapIndexed { k, letter ->
                    val charStart = if (k == 0) 0 else letter.char
                    val charEnd = if (k + 1 < list.size) list[k + 1].char else word.text.length
                    SyncedPhoneme(
                        phoneme = word.text.substring(letter.char, letter.char + 1),
                        alphabet = "grapheme",
                        time = letter.startMs.coerceIn(start, end - 1),
                        endTime = letter.endMs.coerceIn(start + 1, end),
                        characterStart = charStart,
                        characterEnd = max(charEnd, charStart + 1).coerceAtMost(word.text.length),
                        soundType = if (word.text[letter.char].uppercaseChar() in VOWELS) "vowel" else "consonant",
                        confidence = letter.confidence
                    )
                }.let(::sanitizePhonemes)
            } else null
            SyncedWord(
                time = start,
                word = word.text,
                startsNewWord = true,
                endTime = end,
                phonemes = phonemes,
                confidence = if (word.alignable) wordConf[w] else null
            )
        }
        val lineEnd = words.maxOf { it.endTime ?: it.time }
        val aligned = line.copy(time = words.first().time, words = words, endTime = max(lineEnd, words.first().time + 1))
        return Attempt(
            aligned,
            if (letterLevel) Level.LETTER else Level.WORD,
            if (letterLevel) "letter" else "word (letters %.2f)".format(lineConf),
            lineConf
        )
    }

    /** Gives each word without its own timing a slice between its neighbours' times. */
    private fun fillGaps(start: IntArray, end: IntArray, lineStart: Int) {
        var w = 0
        while (w < start.size) {
            if (start[w] >= 0) { w++; continue }
            val runStart = w
            while (w < start.size && start[w] < 0) w++
            val runLength = w - runStart
            // A run before the first aligned word goes just in front of it (a short pickup).
            val from = if (runStart > 0) end[runStart - 1] else (lineStart - LEAD_IN_WORD_MS * runLength).coerceAtLeast(0)
            val nextStart = if (w < start.size) start[w] else from + TRAILING_WORD_MS * runLength
            val span = max(nextStart - from, runLength)
            for (k in 0 until runLength) {
                start[runStart + k] = from + span * k / runLength
                end[runStart + k] = max(from + span * (k + 1) / runLength, start[runStart + k] + 1)
            }
        }
        // Keep starts non-decreasing (validation requires word order).
        for (i in 1 until start.size) if (start[i] < start[i - 1]) start[i] = start[i - 1]
        for (i in start.indices) if (end[i] <= start[i]) end[i] = start[i] + 1
    }

    /** Letter spans must be ordered and non-overlapping inside their word. */
    private fun sanitizePhonemes(list: List<SyncedPhoneme>): List<SyncedPhoneme>? {
        val out = ArrayList<SyncedPhoneme>(list.size)
        var cursor = Int.MIN_VALUE
        for (p in list) {
            val t = max(p.time, cursor)
            if (p.endTime <= t) return null
            out += p.copy(time = t)
            cursor = p.endTime
        }
        return out
    }

    /** Source timing moved into audio time; kept in order behind the previous line. */
    private fun shiftLine(line: SyncedLine, offsetMs: Int, previousStart: Int): SyncedLine {
        val time = max(line.time - offsetMs, max(previousStart, 0))
        val end = line.endTime?.let { it - offsetMs }?.takeIf { it > time }
        val words = line.words?.let { ws ->
            // Only reached for word-less lines in practice; shift defensively anyway.
            ws.map { w -> w.copy(time = max(w.time - offsetMs, time), endTime = w.endTime?.let { it - offsetMs }) }
        }
        return line.copy(time = time, endTime = end, words = words)
    }

    private val VOWELS = setOf('A', 'E', 'I', 'O', 'U', 'Y')
    private const val LEAD_IN_WORD_MS = 250
    private const val TRAILING_WORD_MS = 400
    private const val FLOOR_OVERLAP_MS = 300.0
    private const val PROBE_LINES = 6
    private const val PROBE_AGREE_MS = 500
    /** Smaller shifts are left to the per-line windows. */
    private const val PROBE_MIN_MS = 1_000
}
