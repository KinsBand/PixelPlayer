package com.theveloper.pixelplay.data.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlaybackTraceTest {
    private var nowMs = 0L
    private val lines = mutableListOf<String>()

    @BeforeEach fun setUp() {
        PlaybackTrace.setForTest(nanoTime = { nowMs * 1_000_000 }, sink = { lines += it })
    }

    @Test fun `logs one line with stage times since the tap`() {
        PlaybackTrace.begin("yt_abc")
        nowMs = 8; PlaybackTrace.mark("dispatch")
        nowMs = 341; PlaybackTrace.mark("manifest", "visionos")
        nowMs = 400; PlaybackTrace.mark("manifest", "newpipe") // recorded once
        nowMs = 402; PlaybackTrace.mark("first_bytes", "network")
        nowMs = 612; PlaybackTrace.audioStarted("yt_abc")
        assertEquals(
            listOf("tap_to_audio_ms=612 song=yt_abc stages=dispatch:8,manifest:341(visionos),first_bytes:402(network),audio:612"),
            lines
        )
    }

    @Test fun `a newer tap replaces the trace and other songs do not end it`() {
        PlaybackTrace.begin("yt_old")
        nowMs = 100; PlaybackTrace.begin("yt_new")
        nowMs = 150; PlaybackTrace.audioStarted("yt_old")
        assertTrue(lines.isEmpty())
        PlaybackTrace.audioStarted("yt_new")
        assertEquals("tap_to_audio_ms=50 song=yt_new stages=audio:50", lines.single())
        PlaybackTrace.audioStarted("yt_new")
        assertEquals(1, lines.size)
    }
}
