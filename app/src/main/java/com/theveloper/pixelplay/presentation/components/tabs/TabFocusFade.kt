package com.theveloper.pixelplay.presentation.components.tabs

import com.theveloper.pixelplay.data.songsterr.TabTimeline
import com.theveloper.pixelplay.presentation.components.lyricsBlurRadius
import androidx.compose.ui.unit.dp

/**
 * Tab "Focus fade": how far each drawn bar is from the one playing, counted the way animated
 * lyrics count lines (a whole number; 0 = playing now).
 *
 * Distance is in bars *until it plays*, using the timeline's play order with repeats unrolled,
 * so every pass of a repeat counts: the bar after a 4-bar ×5 riff is 20 away when the riff
 * starts and steps 16 → 12 → 8 → 4 → 1 as the passes go by. Bars that won't play again count
 * up behind the cursor (1, 2, 3…), like lines above the current lyric.
 */
class TabFocusIndex(timeline: TabTimeline) {
    /** Real bar → entry indices (ascending) where it plays. */
    private val entriesOf: Map<Int, IntArray>

    init {
        val lists = HashMap<Int, MutableList<Int>>()
        timeline.entries.forEachIndexed { i, e -> lists.getOrPut(e.measure) { ArrayList(2) } += i }
        entriesOf = lists.mapValues { it.value.toIntArray() }
    }

    /** Distance of a drawn bar standing for [realMeasures] from [cursor]. */
    fun distance(realMeasures: List<Int>, cursor: TabTimeline.Position): Int {
        if (realMeasures.isEmpty() || cursor.measure in realMeasures) return 0
        val at = cursor.entry
        var next = Int.MAX_VALUE
        var last = -1
        for (real in realMeasures) {
            val list = entriesOf[real] ?: continue
            // First index with entry > at.
            var lo = 0
            var hi = list.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (list[mid] > at) hi = mid else lo = mid + 1
            }
            if (lo < list.size) next = minOf(next, list[lo])
            if (lo > 0) last = maxOf(last, list[lo - 1])
        }
        return when {
            next != Int.MAX_VALUE -> next - at
            last >= 0 -> at - last
            else -> FAR
        }
    }

    companion object {
        /** Bars that never play (e.g. skipped endings). */
        const val FAR = 100
    }
}

/**
 * Ink strength for a bar [distance] away — the animated-lyrics table (1 / 0.55 / 0.30) softened
 * ("Subtle"): the bar playing and the next one stay sharp so you can read ahead, and nothing
 * goes below 0.6.
 */
fun tabFocusAlpha(distance: Int): Float = when (distance) {
    0, 1 -> 1f
    2 -> 0.85f
    in 3..4 -> 0.72f
    else -> 0.6f
}

/**
 * Blur (dp) for a bar [distance] away: the lyrics mapping ([lyricsBlurRadius]) at half the
 * user's lyrics blur strength and capped at 3dp, because notation is denser than lyric text.
 * The next bar is never blurred.
 */
fun tabFocusBlurDp(distance: Int, blurOn: Boolean, strength: Float): Float {
    if (!blurOn || distance <= 1) return 0f
    return lyricsBlurRadius((distance - 1).toFloat(), strength * 0.5f, max = 3.dp).value
}
