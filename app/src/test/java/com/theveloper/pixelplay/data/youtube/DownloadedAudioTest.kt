package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.database.CloudSongEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DownloadedAudioTest {
    @TempDir lateinit var directory: Path
    private fun entity(path: String, downloaded: Boolean = true) = CloudSongEntity(
        id = "spotify_alias", title = "Song", artist = "Artist", duration = 1000,
        sourceType = "spotify", contentUriString = "youtube://abcdefghijk",
        youtubeId = "abcdefghijk", isDownloaded = downloaded, localFilePath = path
    )

    @Test fun `only published readable nonempty files qualify for local resolution`() {
        val file = directory.resolve("audio.m4a").toFile()
        assertNull(entity(file.path).downloadedAudioFile())
        file.createNewFile()
        assertNull(entity(file.path).downloadedAudioFile())
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(entity(file.path, false).downloadedAudioFile())
        assertEquals(file, entity(file.path).downloadedAudioFile())
        assertNull(entity(directory.toString()).downloadedAudioFile())
    }
}
