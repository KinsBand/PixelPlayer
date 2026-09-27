package com.theveloper.pixelplay.data.songsterr

/**
 * Groups a [RenderedTrack] into song sections, each drawn as one horizontal row.
 *
 * - A section starts at every Songsterr marker (Intro, Verse 1, Chorus …). Songs without
 *   markers are cut into 8-bar sections.
 * - Inside a section, repeated riffs are folded: a riff is shown once with "×N".
 * - A section that plays exactly like an earlier one points at it ([TabSection.sameAs]).
 */
object TabLayout {

    private const val UNMARKED_SECTION_BARS = 8
    private const val MAX_RIFF_BARS = 4

    fun sections(track: RenderedTrack): List<TabSection> {
        val measures = track.measures
        if (measures.isEmpty()) return emptyList()

        val hasMarkers = measures.any { it.marker != null }
        val starts = if (hasMarkers) {
            (listOf(0) + measures.filter { it.marker != null }.map { it.index }).distinct().sorted()
        } else {
            (measures.indices step UNMARKED_SECTION_BARS).toList()
        }

        val sections = ArrayList<TabSection>(starts.size)
        val firstByKey = HashMap<String, Int>()
        starts.forEachIndexed { i, start ->
            val end = starts.getOrNull(i + 1) ?: measures.size
            if (end <= start) return@forEachIndexed
            val bars = measures.subList(start, end)
            val name = bars.first().marker
                ?: if (hasMarkers) "Intro" else "Bars ${start + 1}–$end"
            val key = bars.joinToString("‖") { it.contentKey }
            val isRest = bars.all { it.isEmpty }
            val sameAs = if (isRest) null else firstByKey[key]
            if (!isRest && sameAs == null) firstByKey[key] = sections.size
            sections += TabSection(
                index = sections.size,
                name = name,
                startMeasure = start,
                endMeasure = end,
                segments = fold(bars),
                isRest = isRest,
                sameAs = sameAs,
            )
        }
        return sections
    }

    /** Splits a section's bars into segments; each segment is drawn once and played [TabSegment.repeat] times. */
    fun fold(bars: List<RenderedMeasure>): List<TabSegment> {
        val n = bars.size
        if (n == 0) return emptyList()
        val keys = bars.map { it.contentKey }

        // Whole section is one riff played N times (e.g. a 4-bar verse riff × 2).
        for (len in 1..n / 2) {
            if (n % len != 0) continue
            if ((len until n).all { keys[it] == keys[it % len] }) {
                return listOf(TabSegment(bars.subList(0, len), n / len))
            }
        }

        // Otherwise fold local repeats left to right.
        val raw = ArrayList<TabSegment>()
        var i = 0
        while (i < n) {
            var bestLen = 1
            var bestRep = 1
            for (len in 1..minOf(MAX_RIFF_BARS, (n - i) / 2)) {
                var rep = 1
                while (i + (rep + 1) * len <= n &&
                    (0 until len).all { keys[i + rep * len + it] == keys[i + it] }
                ) rep++
                if (rep >= 2 && rep * len > bestRep * bestLen) {
                    bestLen = len
                    bestRep = rep
                }
            }
            raw += TabSegment(bars.subList(i, i + bestLen), bestRep)
            i += bestLen * bestRep
        }

        // Merge neighbouring bars that are played once, so they share one run.
        val merged = ArrayList<TabSegment>()
        for (seg in raw) {
            val last = merged.lastOrNull()
            if (last != null && last.repeat == 1 && seg.repeat == 1) {
                merged[merged.lastIndex] = TabSegment(last.measures + seg.measures, 1)
            } else {
                merged += seg
            }
        }
        return merged
    }
}

data class TabSegment(
    val measures: List<RenderedMeasure>,
    val repeat: Int,
)

data class TabSection(
    val index: Int,
    val name: String,
    /** First bar, 0-based. */
    val startMeasure: Int,
    /** One past the last bar, 0-based. */
    val endMeasure: Int,
    val segments: List<TabSegment>,
    /** Every bar is a rest for this instrument. */
    val isRest: Boolean,
    /** Index of an earlier section with exactly the same playing. */
    val sameAs: Int?,
) {
    val barCount: Int get() = endMeasure - startMeasure
    val barRange: String get() = if (barCount == 1) "Bar ${startMeasure + 1}" else "Bars ${startMeasure + 1}–$endMeasure"
    val shownMeasures: List<RenderedMeasure> get() = segments.flatMap { it.measures }
}
