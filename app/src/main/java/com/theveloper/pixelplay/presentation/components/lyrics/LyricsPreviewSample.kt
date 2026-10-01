package com.theveloper.pixelplay.presentation.components.lyrics

import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord

/**
 * Silent sample for the Animation style preview when the playing song has no synced lyrics.
 * Explicit timings (ms) show a line change, several short words, a long held word ("on"),
 * a pause between phrases and a quick handover, then loop.
 */
object LyricsPreviewSample {
    const val LOOP_MS = 7_600L

    private fun w(text: String, start: Int, end: Int) = SyncedWord(time = start, word = text, endTime = end)

    val lines: List<SyncedLine> = listOf(
        SyncedLine(
            time = 0, endTime = 1_500, line = "We run through the city",
            words = listOf(w("We", 0, 200), w("run", 220, 420), w("through", 440, 700), w("the", 720, 840), w("city", 860, 1_500))
        ),
        // 1500–2400: pause between phrases.
        SyncedLine(
            time = 2_400, endTime = 5_000, line = "and we hold on",
            words = listOf(w("and", 2_400, 2_560), w("we", 2_580, 2_740), w("hold", 2_760, 3_100), w("on", 3_120, 5_000))
        ),
        SyncedLine(
            time = 5_300, endTime = 6_600, line = "to the light",
            words = listOf(w("to", 5_300, 5_460), w("the", 5_480, 5_620), w("light", 5_640, 6_600))
        ),
        // 6600–7600: rest, then the loop jumps back to 0 (resolved like a seek, nothing replays).
    )
}
