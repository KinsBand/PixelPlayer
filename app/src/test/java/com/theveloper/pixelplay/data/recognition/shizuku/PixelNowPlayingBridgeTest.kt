package com.theveloper.pixelplay.data.recognition.shizuku

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.recognition.VoiceSearchMode
import org.junit.Test

class PixelNowPlayingBridgeTest {

    @Test
    fun `parseContentQueryOutput parses standard Pixel Now Playing row format`() {
        val sampleOutput = "Row: 0 _id=1, song_title=Starboy, artist_name=The Weeknd, timestamp=1700000000000"
        val result = PixelNowPlayingBridge.parseContentQueryOutput(sampleOutput)

        assertThat(result).hasSize(1)
        val item = result[0]
        assertThat(item.song.title).isEqualTo("Starboy")
        assertThat(item.song.artist).isEqualTo("The Weeknd")
        assertThat(item.detectedAtEpochMs).isEqualTo(1700000000000L)
    }

    @Test
    fun `parseContentQueryOutput handles multiple rows and alternative field keys`() {
        val sampleOutput = """
            Row: 0 _id=10, title=Levitating, artist=Dua Lipa, detected_time=1700000005000
            Row: 1 _id=11, song_title=As It Was, artist_name=Harry Styles, timestamp=1700000008000
        """.trimIndent()

        val result = PixelNowPlayingBridge.parseContentQueryOutput(sampleOutput)

        assertThat(result).hasSize(2)
        // Ordered descending by timestamp
        assertThat(result[0].song.title).isEqualTo("As It Was")
        assertThat(result[1].song.title).isEqualTo("Levitating")
    }

    @Test
    fun `parseContentQueryOutput returns empty list for blank output`() {
        val result = PixelNowPlayingBridge.parseContentQueryOutput("")
        assertThat(result).isEmpty()

        val whitespaceResult = PixelNowPlayingBridge.parseContentQueryOutput("   \n\n  ")
        assertThat(whitespaceResult).isEmpty()
    }

    @Test
    fun `two voice search modes are distinct and configured correctly`() {
        val humAndSing = VoiceSearchMode.HUM_AND_SING
        val listenAndNowPlaying = VoiceSearchMode.LISTEN_AND_NOW_PLAYING

        assertThat(humAndSing.isHumOrSing).isTrue()
        assertThat(humAndSing.isListenOrNowPlaying).isFalse()

        assertThat(listenAndNowPlaying.isListenOrNowPlaying).isTrue()
        assertThat(listenAndNowPlaying.isHumOrSing).isFalse()

        assertThat(VoiceSearchMode.entries).hasSize(2)
    }
}
