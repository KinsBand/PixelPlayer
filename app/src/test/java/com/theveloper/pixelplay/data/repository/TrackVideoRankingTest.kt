package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.model.TrackVideo
import com.theveloper.pixelplay.data.model.VideoType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TrackVideoRankingTest {
    private fun video(id: String, title: String, channel: String = "Artist") = TrackVideo(id, title, channel, "")

    @Test
    fun `official matching performance ranks above unrelated official video and audio upload`() {
        val results = rankTrackVideos(listOf(
            video("audio", "Artist - Northern Lights (Official Audio)"),
            video("unrelated", "Another Track (Official Video)", "Someone Else"),
            video("live", "Artist - Northern Lights Live"),
            video("official", "Artist - Northern Lights (Official Music Video)"),
        ), "Artist", "Northern Lights", VideoType.MUSIC_VIDEO)
        assertEquals("official", results.first().id)
    }

    @Test
    fun `covers remain eligible and duplicate video ids are removed`() {
        val cover = video("cover", "Northern Lights - acoustic cover", "Another Artist")
        val results = rankTrackVideos(listOf(cover, cover), "Artist", "Northern Lights", VideoType.COVER)
        assertEquals(listOf(cover), results)
    }

    @Test
    fun `live preset excludes studio result even if search returns it`() {
        val live = video("live", "Artist - Northern Lights (Live at Wembley)")
        val results = rankTrackVideos(listOf(video("studio", "Artist - Northern Lights"), live),
            "Artist", "Northern Lights", VideoType.LIVE_PERFORMANCE)
        assertEquals(listOf(live), results)
    }
}
