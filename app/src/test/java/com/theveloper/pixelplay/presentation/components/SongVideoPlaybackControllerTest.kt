package com.theveloper.pixelplay.presentation.components

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SongVideoPlaybackControllerTest {
    @Test
    fun `version switches acquire audio once and return releases video before resuming audio with automatic position jump`() {
        val events = mutableListOf<String>()
        val player = mockk<YouTubePlayer>(relaxed = true)
        val controller = SongVideoPlaybackController {
            events += "acquire"
            { resume: Boolean, pos: Long? -> events.add("release:$resume:$pos"); Unit }
        }
        controller.player = player
        controller.releaseVideo = { events += "destroyVideo" }
        assertTrue(controller.prepare())
        assertTrue(controller.prepare())
        controller.seekTo(45_000L)
        controller.stop(true)
        controller.stop()
        assertEquals(listOf("acquire", "destroyVideo", "release:true:45000"), events)
        verify(atLeast = 1) { player.pause() }
    }

    @Test
    fun `missing audio controller prevents video autoplay`() {
        val controller = SongVideoPlaybackController { null }
        assertFalse(controller.prepare())
    }
}
