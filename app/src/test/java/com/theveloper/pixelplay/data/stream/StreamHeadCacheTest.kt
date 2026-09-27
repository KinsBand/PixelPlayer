package com.theveloper.pixelplay.data.stream

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class StreamHeadCacheTest {
    @TempDir lateinit var dir: File

    private val key = StreamHeadCache.Key("dQw4w9WgXcQ", 251, 3_000_000, 160_000, "audio/webm")
    private val data = ByteArray(100_000) { (it % 251).toByte() }

    private fun cache(maxDisk: Long = 1_000_000) = StreamHeadCache(dir, maxDiskBytes = maxDisk, headBytes = 64 * 1024)

    @Test fun `stores at most the head size and serves any offset`() {
        val cache = cache()
        assertTrue(cache.store(key, data))
        val entry = cache.lookup(key.videoId)!!
        assertEquals(64 * 1024, entry.length)
        assertArrayEquals(data.copyOfRange(10, 64 * 1024), cache.read(entry, 10))
        assertTrue(dir.list()!!.none { it.endsWith(".tmp") })
    }

    @Test fun `tiny heads are not stored and a shorter head never replaces a longer one`() {
        val cache = cache()
        assertFalse(cache.store(key, ByteArray(1_000)))
        assertTrue(cache.store(key, data))
        assertFalse(cache.store(key, data, 40_000))
        assertEquals(64 * 1024, cache.lookup(key.videoId)!!.length)
    }

    @Test fun `file names are the index after a restart`() {
        cache().store(key, data)
        val restarted = cache()
        val entry = restarted.lookup(key.videoId)
        assertEquals(key, entry?.key)
        assertArrayEquals(data.copyOf(64 * 1024), restarted.read(entry!!))
    }

    @Test fun `a new rendition replaces the previous one for the song`() {
        val cache = cache()
        cache.store(key, data)
        val aac = key.copy(itag = 140, contentLength = 2_000_000, bitrate = 128_000, mimeType = "audio/mp4")
        assertTrue(cache.store(aac, data))
        assertEquals(aac, cache.lookup(key.videoId)!!.key)
        assertEquals(1, dir.list()!!.size)
    }

    @Test fun `least recently written heads are evicted over the disk budget`() {
        val cache = cache(maxDisk = 200_000)
        listOf("aaaaaaaaaaa", "bbbbbbbbbbb", "ccccccccccc", "ddddddddddd").forEach { id ->
            cache.store(key.copy(videoId = id), data)
            Thread.sleep(15)
        }
        assertTrue(dir.listFiles()!!.sumOf { it.length() } <= 200_000)
        assertNull(cache.lookup("aaaaaaaaaaa"))
        assertNotNull(cache.lookup("ddddddddddd"))
    }

    @Test fun `a vanished file is forgotten instead of served`() {
        val cache = cache()
        cache.store(key, data)
        val entry = cache.lookup(key.videoId)!!
        cache.trimMemory()
        File(dir, entry.key.fileName).delete()
        assertNull(cache.read(entry))
        assertNull(cache.lookup(key.videoId))
    }

    @Test fun `malformed names are rejected`() {
        assertNull(StreamHeadCache.parse("bad.name"))
        assertNull(StreamHeadCache.parse("dQw4w9WgXcQ.251.0.1.webm"))
        assertEquals(key, StreamHeadCache.parse(key.fileName))
    }
}
