package com.theveloper.pixelplay.data.songsterr

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A pitch-shifted note that leaves General MIDI's 0..127 must keep its pitch class (fold by
 * octaves), not pile up on the edge note as the old `coerceIn(0, 127)` did.
 */
class TabMidiFoldTest {
    @Test fun `in-range notes are unchanged`() {
        for (n in 0..127) assertEquals(n, TabMidi.foldToMidiRange(n))
    }

    @Test fun `notes above 127 fold down by octaves and keep their pitch class`() {
        assertEquals(116, TabMidi.foldToMidiRange(128)) // G#9 → G#8, not G9 (127)
        assertEquals(127, TabMidi.foldToMidiRange(139))
        assertEquals(128 % 12, TabMidi.foldToMidiRange(128) % 12)
    }

    @Test fun `notes below 0 fold up by octaves and keep their pitch class`() {
        assertEquals(11, TabMidi.foldToMidiRange(-1)) // not 0
        assertEquals(0, TabMidi.foldToMidiRange(-12))
        assertEquals(1, TabMidi.foldToMidiRange(-23))
    }

    @Test fun `every shifted note lands in range`() {
        for (n in -40..170) assertEquals(true, TabMidi.foldToMidiRange(n) in 0..127, "note $n")
    }
}
