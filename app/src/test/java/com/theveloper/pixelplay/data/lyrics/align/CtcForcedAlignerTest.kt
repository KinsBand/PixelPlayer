package com.theveloper.pixelplay.data.lyrics.align

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CtcForcedAlignerTest {
    private val vocab = CtcVocabulary.WAV2VEC2_960H

    private fun labels(text: String): IntArray {
        val out = ArrayList<Int>()
        for ((i, word) in text.split(' ').withIndex()) {
            if (i > 0) out += vocab.wordSeparator
            for (c in word) out += vocab.idOf(c)!!
        }
        return out.toIntArray()
    }

    @Test fun `finds each letter where it is sung`() {
        val em = FakeEmissions(200)
        em.sing("HI THERE", startMs = 1000)
        val result = CtcForcedAligner.align(em.build(), 0, 200, labels("HI THERE"), vocab.blank)!!
        val firstFrames = result.spans.map { it.firstFrame }
        // H at 1000 ms (frame 50), I at 1100 (55), | at 1200 (60), T at 1300 (65) …
        assertEquals(listOf(50, 55, 60, 65, 70, 75, 80, 85), firstFrames)
        assertEquals(50, result.startFrame)
        assertTrue(result.spans.all { it.confidence > 0.9f })
    }

    @Test fun `audio before and after the line falls to the star states`() {
        val em = FakeEmissions(200)
        // Someone else's words right before and after.
        em.sing("ZZZ ZZZ", startMs = 200)
        em.sing("NO", startMs = 1500)
        em.sing("QQQ", startMs = 2500)
        val result = CtcForcedAligner.align(em.build(), 0, 200, labels("NO"), vocab.blank)!!
        assertEquals(75, result.spans[0].firstFrame)
        assertEquals(80, result.spans[1].firstFrame)
        assertTrue(result.endFrame <= 125, "trailing star should begin before the next line, was ${result.endFrame}")
    }

    @Test fun `held vowel holds until the next letter`() {
        val em = FakeEmissions(200)
        // L at 1000, O at 1100, V at 2000 (held O for 900 ms), E at 2100.
        em.setFrame(50, vocab.idOf('L')!!, 0.95f)
        em.setFrame(55, vocab.idOf('O')!!, 0.95f)
        em.setFrame(100, vocab.idOf('V')!!, 0.95f)
        em.setFrame(105, vocab.idOf('E')!!, 0.95f)
        val result = CtcForcedAligner.align(em.build(), 0, 200, labels("LOVE"), vocab.blank)!!
        assertEquals(100, result.holdUntilFrame[1])
    }

    @Test fun `double letters need a blank between them`() {
        val em = FakeEmissions(200)
        em.sing("HELLO", startMs = 1000)
        val result = CtcForcedAligner.align(em.build(), 0, 200, labels("HELLO"), vocab.blank)!!
        assertEquals(listOf(50, 55, 60, 65, 70), result.spans.map { it.firstFrame })
    }

    @Test fun `window shorter than the transcript gives up`() {
        val em = FakeEmissions(200)
        assertNull(CtcForcedAligner.align(em.build(), 0, 3, labels("HELLO"), vocab.blank))
        // Three tokens, but "LL" needs a blank between: 4 frames minimum.
        assertNull(CtcForcedAligner.align(em.build(), 0, 3, labels("ALL"), vocab.blank))
        assertNotNull(CtcForcedAligner.align(em.build(), 0, 4, labels("ALL"), vocab.blank))
    }
}
