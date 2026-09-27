package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.database.CloudSongEntity
import com.theveloper.pixelplay.data.database.preserveDownloadedSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CloudSongDownloadMergeTest {
    private val remote = CloudSongEntity("track", "Title", "Artist", duration = 1000,
        sourceType = "spotify", contentUriString = "spotify://track")
    private val downloaded = remote.copy(isDownloaded = true, localFilePath = "/music/track.m4a",
        localContentUri = "file:///music/track.m4a", youtubeId = "abcdefghijk")

    @Test fun `late metadata cannot clear a completed download or source match`() {
        val merged = remote.copy(title = "New title").preserveDownloadedSource(downloaded)
        assertEquals("New title", merged.title)
        assertTrue(merged.isDownloaded)
        assertEquals(downloaded.localFilePath, merged.localFilePath)
        assertEquals(downloaded.localContentUri, merged.localContentUri)
        assertEquals(downloaded.youtubeId, merged.youtubeId)
    }
    @Test fun `new completed download can replace its old file`() {
        val replacement = downloaded.copy(localFilePath = "/music/new.m4a")
        assertEquals(replacement, replacement.preserveDownloadedSource(downloaded))
        assertEquals(remote, remote.preserveDownloadedSource(null))
    }
}
