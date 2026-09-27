package com.theveloper.pixelplay.data.lyrics

import com.theveloper.pixelplay.data.model.SyncedLine
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** One part of a song (Intro, Verse 1, Chorus…) with where it starts and ends. */
data class SongSection(
    val kind: SongSectionKind,
    /** What is shown, e.g. "Verse 2", "Chorus". */
    val label: String,
    val startMs: Long,
    val endMs: Long,
)

enum class SongSectionKind(val key: String, val title: String) {
    INTRO("intro", "Intro"),
    VERSE("verse", "Verse"),
    PRE_CHORUS("pre_chorus", "Pre-Chorus"),
    CHORUS("chorus", "Chorus"),
    POST_CHORUS("post_chorus", "Post-Chorus"),
    HOOK("hook", "Hook"),
    BRIDGE("bridge", "Bridge"),
    BREAKDOWN("breakdown", "Breakdown"),
    INSTRUMENTAL("instrumental", "Instrumental"),
    SOLO("solo", "Solo"),
    DROP("drop", "Drop"),
    OUTRO("outro", "Outro"),
    OTHER("other", "Part");

    companion object {
        fun fromKey(key: String?): SongSectionKind = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/**
 * A song's full structure.
 * @param source where it came from: "tags" (section tags in the synced lyrics or TTML),
 *   "lyrics_text" (headers in the plain lyrics we already had), "lrclib" (headers in LRCLIB's
 *   plain lyrics, fetched once), or "analysis" (worked out on the device from the synced
 *   lyrics' timing and repetition).
 * @param lyricsFingerprint the synced lyrics it was built from; a different fingerprint means
 *   the lyrics changed and the structure is rebuilt (without searching online again).
 */
data class SongStructure(
    val sections: List<SongSection>,
    val source: String,
    val lyricsFingerprint: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Index of the section playing at [positionMs], or -1. */
    fun indexAt(positionMs: Long): Int {
        if (sections.isEmpty()) return -1
        if (positionMs < sections.first().startMs) return 0
        var lo = 0
        var hi = sections.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (sections[mid].startMs <= positionMs) lo = mid else hi = mid - 1
        }
        return lo
    }
}

/**
 * Builds a [SongStructure] from lyrics. Pure Kotlin (no Android) so it can be unit tested.
 *
 * Order of preference:
 *  1. Explicit section tags on the synced lines (TTML `song-part`, or header lines such as
 *     "[Chorus]" / "(Verse 2)" / "Bridge:" inside the synced lyrics).
 *  2. Section headers in a plain-lyrics text, aligned onto the synced lines by matching text.
 *  3. Analysis of the synced lyrics: stanzas from timing gaps, the chorus from the most
 *     repeated block, pre/post-chorus from what surrounds it, a bridge from the late one-off
 *     block between choruses, and intro / instrumental / outro from long gaps with no words.
 */
object SongStructureAnalyzer {

    private const val MIN_INTRO_MS = 4_000L
    private const val MIN_OUTRO_MS = 6_000L
    private const val MIN_INSTRUMENTAL_MS = 9_000L

    // ─── Headers ────────────────────────────────────────────────────────────────────────

    private const val SECTION_WORDS =
        """(intro|verse|pre[\s-]?chorus|post[\s-]?chorus|chorus|refrain|hook|bridge|""" +
            """breakdown|interlude|instrumental(?:\s+break)?|break|(?:guitar\s+|piano\s+|sax\s+)?solo|""" +
            """drop|build[\s-]?up|outro|coda|ending)"""

    /** "[Verse 2: Someone]", "(Chorus x2)", "{Bridge}": anything may follow inside brackets. */
    private val bracketedHeader = Regex(
        """^\s*[\[(<{]\s*$SECTION_WORDS\b\s*(\d+)?[^\])>}]*[\])>}]\s*:?\s*$""",
        RegexOption.IGNORE_CASE
    )

    /** "Chorus", "Verse 2:", "Bridge" on their own: nothing else allowed, so a lyric like
     *  "Chorus is where the heart is" isn't taken for a header. */
    private val bareHeader = Regex(
        """^\s*$SECTION_WORDS\s*(\d+)?\s*:?\s*$""",
        RegexOption.IGNORE_CASE
    )

    /** "[Verse 2: Someone]" → VERSE, "Verse 2". Null when [text] isn't a section header. */
    fun parseHeader(text: String): Pair<SongSectionKind, String>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 48) return null
        val m = bracketedHeader.matchEntire(trimmed) ?: bareHeader.matchEntire(trimmed) ?: return null
        val word = m.groupValues[1].lowercase(Locale.ROOT).replace(Regex("""[\s-]+"""), " ")
        val number = m.groupValues[2].toIntOrNull()
        val kind = kindForWord(word)
        val base = if (kind == SongSectionKind.OTHER) word.replaceFirstChar { it.titlecase(Locale.ROOT) } else kind.title
        return kind to if (number != null) "$base $number" else base
    }

    fun kindForWord(word: String): SongSectionKind {
        val w = word.lowercase(Locale.ROOT).replace(Regex("""[\s_-]+"""), " ").trim()
        return when {
            w.startsWith("pre chorus") || w == "prechorus" -> SongSectionKind.PRE_CHORUS
            w.startsWith("post chorus") || w == "postchorus" -> SongSectionKind.POST_CHORUS
            w.startsWith("chorus") || w.startsWith("refrain") -> SongSectionKind.CHORUS
            w.startsWith("verse") -> SongSectionKind.VERSE
            w.startsWith("intro") -> SongSectionKind.INTRO
            w.startsWith("hook") -> SongSectionKind.HOOK
            w.startsWith("bridge") -> SongSectionKind.BRIDGE
            w.startsWith("breakdown") -> SongSectionKind.BREAKDOWN
            w.startsWith("instrumental") || w.startsWith("interlude") || w == "break" -> SongSectionKind.INSTRUMENTAL
            w.endsWith("solo") -> SongSectionKind.SOLO
            w.startsWith("drop") || w.startsWith("build") -> SongSectionKind.DROP
            w.startsWith("outro") || w.startsWith("coda") || w.startsWith("ending") -> SongSectionKind.OUTRO
            else -> SongSectionKind.OTHER
        }
    }

    fun containsHeaders(lines: List<String>): Boolean = lines.count { parseHeader(it) != null } >= 2

    // ─── Fingerprint ────────────────────────────────────────────────────────────────────

    fun fingerprint(synced: List<SyncedLine>): String {
        var h = 1125899906842597L
        synced.forEach { line ->
            h = 31 * h + line.time
            h = 31 * h + line.line.hashCode()
            h = 31 * h + (line.songPart?.hashCode() ?: 0)
        }
        return "${synced.size}-${java.lang.Long.toHexString(h)}"
    }

    // ─── 1. Tags on synced lines ────────────────────────────────────────────────────────

    fun fromSyncedTags(synced: List<SyncedLine>, durationMs: Long): List<SongSection>? {
        val starts = mutableListOf<Triple<Long, SongSectionKind, String>>()
        var lastPart: String? = null
        synced.forEach { line ->
            val part = line.songPart?.takeIf { it.isNotBlank() }
            if (part != null && part != lastPart) {
                val kind = kindForWord(part.replace(Regex("""(?<=[a-z])(?=[A-Z])"""), " "))
                starts += Triple(line.time.toLong(), kind, kind.title.takeIf { kind != SongSectionKind.OTHER } ?: part)
                lastPart = part
            } else if (part == null) {
                parseHeader(line.line)?.let { (kind, label) ->
                    starts += Triple(line.time.toLong(), kind, label)
                    lastPart = null
                }
            }
        }
        if (starts.size < 2) return null
        return finish(starts, synced, durationMs, numberUnnumbered = true)
    }

    // ─── 2. Headers in plain text, aligned onto synced lines ───────────────────────────

    fun fromPlainHeaders(plain: List<String>, synced: List<SyncedLine>, durationMs: Long): List<SongSection>? {
        if (!containsHeaders(plain)) return null
        val lyricLines = synced.withIndex().filter { it.value.line.isNotBlank() && parseHeader(it.value.line) == null }
        if (lyricLines.isEmpty()) return null

        val starts = mutableListOf<Triple<Long, SongSectionKind, String>>()
        var pendingHeader: Pair<SongSectionKind, String>? = null
        var cursor = 0 // index into lyricLines
        for (raw in plain) {
            val text = raw.trim()
            if (text.isEmpty()) continue
            val header = parseHeader(text)
            if (header != null) {
                pendingHeader = header
                continue
            }
            // Find this plain line among the next few synced lines.
            val target = normalise(text)
            if (target.isEmpty()) continue
            var found = -1
            val end = min(lyricLines.size, cursor + 8)
            for (k in cursor until end) {
                if (similarity(target, normalise(lyricLines[k].value.line)) >= 0.6f) {
                    found = k; break
                }
            }
            if (found < 0) continue
            pendingHeader?.let { (kind, label) ->
                starts += Triple(lyricLines[found].value.time.toLong(), kind, label)
                pendingHeader = null
            }
            cursor = found + 1
        }
        if (starts.size < 2) return null
        // A header that matched nothing leaves no trace; drop starts that went backwards.
        val ordered = starts.fold(mutableListOf<Triple<Long, SongSectionKind, String>>()) { acc, s ->
            if (acc.isEmpty() || s.first > acc.last().first) acc += s
            acc
        }
        return finish(ordered, synced, durationMs, numberUnnumbered = true)
    }

    // ─── 3. On-device analysis ─────────────────────────────────────────────────────────

    private data class Segment(val lines: List<SyncedLine>, val repeated: Boolean) {
        val start get() = lines.first().time.toLong()
        val signature: Set<String> by lazy { lines.map { normalise(it.line) }.filter { it.isNotEmpty() }.toSet() }
    }

    fun analyse(synced: List<SyncedLine>, durationMs: Long): List<SongSection>? {
        val lines = synced.filter { it.line.isNotBlank() && parseHeader(it.line) == null }.sortedBy { it.time }
        if (lines.size < 6) return null

        val norms = lines.map { normalise(it.line) }

        // How often each line (or a near copy) appears in the song.
        val counts = IntArray(lines.size) { i ->
            if (norms[i].length < 3) 1
            else norms.count { other -> other.length >= 3 && (other == norms[i] || similarity(other, norms[i]) >= 0.85f) }
        }
        val rawRepeated = BooleanArray(lines.size) { counts[it] >= 2 }
        // Smooth single-line flips so one repeated "yeah" inside a verse doesn't split it.
        val repeated = BooleanArray(lines.size) { i ->
            val prev = if (i > 0) rawRepeated[i - 1] else rawRepeated[i]
            val next = if (i < lines.lastIndex) rawRepeated[i + 1] else rawRepeated[i]
            if (prev == next && prev != rawRepeated[i]) prev else rawRepeated[i]
        }

        // Stanza breaks from timing: a gap much longer than the song's usual line gap, or a
        // blank line in the original synced lyrics.
        val gaps = (1 until lines.size).map { (lines[it].time - lines[it - 1].time).toLong() }
        val medianGap = gaps.sorted().let { it[it.size / 2] }.coerceAtLeast(800L)
        val stanzaGap = max((medianGap * 2.1f).toLong(), 4_500L)
        val blankBreakTimes = synced.filter { it.line.isBlank() }.map { it.time.toLong() }.toSet()
        fun isBreak(i: Int): Boolean {
            if (i == 0) return false
            val a = lines[i - 1].time.toLong()
            val b = lines[i].time.toLong()
            return b - a >= stanzaGap || blankBreakTimes.any { it in (a + 1) until b }
        }

        // Segments: split on stanza breaks and on repeated ↔ one-off changes.
        val segments = mutableListOf<Segment>()
        var current = mutableListOf(lines[0])
        var currentRepeated = repeated[0]
        for (i in 1 until lines.size) {
            if (isBreak(i) || repeated[i] != currentRepeated) {
                segments += Segment(current, currentRepeated)
                current = mutableListOf()
                currentRepeated = repeated[i]
            }
            current += lines[i]
        }
        segments += Segment(current, currentRepeated)

        // Group repeated segments that share lines; the biggest group is the chorus.
        val repeatedIdx = segments.indices.filter { segments[it].repeated }
        val groupOf = IntArray(segments.size) { -1 }
        var groups = 0
        for (i in repeatedIdx) {
            if (groupOf[i] >= 0) continue
            groupOf[i] = groups
            for (j in repeatedIdx) {
                if (j != i && groupOf[j] < 0 && overlap(segments[i].signature, segments[j].signature) >= 0.5f) {
                    groupOf[j] = groups
                }
            }
            groups++
        }
        val groupWeight = IntArray(groups)
        val groupCount = IntArray(groups)
        segments.indices.forEach { i ->
            val g = groupOf[i]
            if (g >= 0) { groupWeight[g] += segments[i].lines.size; groupCount[g]++ }
        }
        val chorusGroup = (0 until groups).filter { groupCount[it] >= 2 }.maxByOrNull { groupWeight[it] } ?: -1

        val kinds = Array(segments.size) { SongSectionKind.VERSE }
        segments.indices.forEach { i ->
            val g = groupOf[i]
            kinds[i] = when {
                g < 0 -> SongSectionKind.VERSE
                g == chorusGroup -> SongSectionKind.CHORUS
                else -> SongSectionKind.HOOK
            }
        }
        // Other repeated groups: always right before a chorus → pre-chorus; always right after
        // → post-chorus.
        if (chorusGroup >= 0) {
            (0 until groups).filter { it != chorusGroup }.forEach { g ->
                val members = segments.indices.filter { groupOf[it] == g }
                val allBefore = members.all { groupOf.getOrNull(it + 1) == chorusGroup }
                val allAfter = members.all { groupOf.getOrNull(it - 1) == chorusGroup }
                val kind = when {
                    allBefore -> SongSectionKind.PRE_CHORUS
                    allAfter -> SongSectionKind.POST_CHORUS
                    else -> SongSectionKind.HOOK
                }
                members.forEach { kinds[it] = kind }
            }
            // Bridge: the last one-off block that comes after at least two choruses and before
            // another chorus (V C V C [B] C).
            val chorusIdx = segments.indices.filter { kinds[it] == SongSectionKind.CHORUS }
            if (chorusIdx.size >= 3) {
                val lastChorus = chorusIdx.last()
                val secondChorus = chorusIdx[1]
                val candidate = (lastChorus - 1 downTo secondChorus + 1)
                    .firstOrNull { kinds[it] == SongSectionKind.VERSE }
                if (candidate != null && (candidate + 1 until lastChorus).none { kinds[it] == SongSectionKind.VERSE }) {
                    kinds[candidate] = SongSectionKind.BRIDGE
                }
            }
        }

        // Merge neighbours of the same kind (a chorus split by a short pause is one chorus).
        val merged = mutableListOf<Pair<SongSectionKind, MutableList<SyncedLine>>>()
        segments.indices.forEach { i ->
            val last = merged.lastOrNull()
            if (last != null && last.first == kinds[i] && !isBreakBetween(last.second.last(), segments[i].lines.first(), stanzaGap * 2)) {
                last.second += segments[i].lines
            } else {
                merged += kinds[i] to segments[i].lines.toMutableList()
            }
        }
        if (merged.size < 2 && chorusGroup < 0) {
            // Nothing repeats and no stanza breaks: a structure of one "Verse" isn't useful.
            return null
        }

        // Words-free gaps become intro / instrumental / outro.
        val typicalLineMs = medianGap.coerceIn(1_500L, 5_000L)
        fun lineEnd(line: SyncedLine, next: Long?): Long {
            val natural = line.endTime?.toLong()
                ?: line.words?.lastOrNull()?.let { (it.endTime ?: it.time).toLong() + 400 }
                ?: (line.time + typicalLineMs * 1.25).toLong()
            return if (next != null) min(natural, next) else natural
        }

        val starts = mutableListOf<Triple<Long, SongSectionKind, String>>()
        val firstStart = merged.first().second.first().time.toLong()
        if (firstStart >= MIN_INTRO_MS) starts += Triple(0L, SongSectionKind.INTRO, SongSectionKind.INTRO.title)
        merged.forEachIndexed { i, (kind, blockLines) ->
            val start = blockLines.first().time.toLong()
            starts += Triple(if (starts.isEmpty()) 0L else start, kind, kind.title)
            val nextStart = merged.getOrNull(i + 1)?.second?.first()?.time?.toLong()
            val end = lineEnd(blockLines.last(), nextStart)
            if (nextStart != null && nextStart - end >= MIN_INSTRUMENTAL_MS) {
                starts += Triple(end, SongSectionKind.INSTRUMENTAL, SongSectionKind.INSTRUMENTAL.title)
            } else if (nextStart == null && durationMs > 0 && durationMs - end >= MIN_OUTRO_MS) {
                starts += Triple(end, SongSectionKind.OUTRO, SongSectionKind.OUTRO.title)
            }
        }
        return finish(starts, synced, durationMs, numberUnnumbered = true)
    }

    private fun isBreakBetween(a: SyncedLine, b: SyncedLine, gap: Long) = b.time - a.time >= gap

    // ─── Shared ─────────────────────────────────────────────────────────────────────────

    /**
     * Turns section starts into sections with ends, adds an intro when the words start late,
     * merges duplicates at the same time and numbers repeated verses (Verse 1, Verse 2…).
     */
    private fun finish(
        rawStarts: List<Triple<Long, SongSectionKind, String>>,
        synced: List<SyncedLine>,
        durationMs: Long,
        numberUnnumbered: Boolean,
    ): List<SongSection>? {
        val starts = rawStarts.sortedBy { it.first }
            .fold(mutableListOf<Triple<Long, SongSectionKind, String>>()) { acc, s ->
                if (acc.isNotEmpty() && s.first - acc.last().first < 500) acc[acc.lastIndex] = s else acc += s
                acc
            }
        if (starts.isEmpty()) return null
        val firstWords = synced.firstOrNull { it.line.isNotBlank() }?.time?.toLong() ?: 0L
        if (starts.first().first > 0) {
            if (starts.first().first >= MIN_INTRO_MS && firstWords >= MIN_INTRO_MS && starts.first().second != SongSectionKind.INTRO) {
                starts.add(0, Triple(0L, SongSectionKind.INTRO, SongSectionKind.INTRO.title))
            } else {
                starts[0] = starts[0].copy(first = 0L)
            }
        }

        val lastLineTime = synced.maxOfOrNull { it.time.toLong() } ?: 0L
        val songEnd = if (durationMs > 0) max(durationMs, lastLineTime + 1_000) else lastLineTime + 8_000

        // Number verses (and other repeatable parts) that came without a number.
        val labels = starts.map { it.third }.toMutableList()
        if (numberUnnumbered) {
            val numberedKinds = setOf(SongSectionKind.VERSE)
            val totals = starts.groupingBy { it.second }.eachCount()
            val seen = mutableMapOf<SongSectionKind, Int>()
            starts.forEachIndexed { i, (_, kind, label) ->
                if (kind in numberedKinds && (totals[kind] ?: 0) > 1 && label.none { it.isDigit() }) {
                    val n = (seen[kind] ?: 0) + 1
                    seen[kind] = n
                    labels[i] = "${kind.title} $n"
                }
            }
        }

        return starts.mapIndexed { i, (start, kind, _) ->
            val end = starts.getOrNull(i + 1)?.first ?: songEnd
            SongSection(kind = kind, label = labels[i], startMs = start, endMs = max(end, start + 1))
        }.takeIf { it.size >= 2 }
    }

    internal fun normalise(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace(Regex("""[(\[].*?[)\]]"""), " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()

    /** Word-level Jaccard similarity of two normalised lines. */
    internal fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        val wa = a.split(' ').filter { it.isNotEmpty() }.toSet()
        val wb = b.split(' ').filter { it.isNotEmpty() }.toSet()
        if (wa.isEmpty() || wb.isEmpty()) return 0f
        val inter = wa.intersect(wb).size
        return inter.toFloat() / (wa.size + wb.size - inter)
    }

    private fun overlap(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        var hits = 0
        a.forEach { line -> if (b.any { it == line || similarity(it, line) >= 0.8f }) hits++ }
        return hits.toFloat() / min(a.size, b.size)
    }
}
