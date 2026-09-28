package com.theveloper.pixelplay.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlaybackQueueSnapshotTest {
    @Test fun `legacy snapshots receive distinct occurrence IDs and safe defaults`() {
        val legacy = """{"items":[{"mediaId":"a","uri":"file:///a.flac"},{"mediaId":"a","uri":"file:///a.flac"}],"currentIndex":1}"""
        val snapshot = Json.decodeFromString<PlaybackQueueSnapshot>(legacy)
        assertNotEquals(snapshot.items[0].queueEntry.entryId, snapshot.items[1].queueEntry.entryId)
        assertTrue(snapshot.items.all { it.queueEntry.tier == QueueTier.SESSION })
        assertFalse(snapshot.playWhenReady)
    }
    @Test fun `queue order tiers pins origin and selected occurrence survive serialization`() {
        val priority = QueueEntryMetadata(tier = QueueTier.PRIORITY, pinned = true)
        val generated = QueueEntryMetadata(origin = QueueOrigin.GENERATED, decisionId = "decision", sessionId = "session")
        val snapshot = PlaybackQueueSnapshot(listOf(
            PlaybackQueueItemSnapshot("a", "file:///a.flac", queueEntry = priority),
            PlaybackQueueItemSnapshot("a", "file:///a.flac", queueEntry = generated)),
            currentIndex = 1, currentEntryId = generated.entryId)
        assertEquals(snapshot, Json.decodeFromString<PlaybackQueueSnapshot>(Json.encodeToString(snapshot)))
    }
}
