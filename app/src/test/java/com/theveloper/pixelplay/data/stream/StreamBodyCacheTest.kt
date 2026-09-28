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

class StreamBodyCacheTest {
    @TempDir lateinit var dir: File

    private fun key(id: String = "abcdefghijk", size: Long = 1_000, itag: Int = 251) =
        StreamHeadCache.Key(id, itag, size, 160_000, "audio/webm")

    private fun bytes(size: Int) = ByteArray(size) { (it * 7).toByte() }

    @Test fun `a song written in order becomes available only once complete`() {
        val cache = StreamBodyCache(dir)
        val data = bytes(1_000)
        val writer = cache.openWriter(key(), 0)!!
        assertTrue(writer.append(data, 0, 600, 0))
        assertNull(cache.lookup("abcdefghijk"), "a partial song is never served")
        assertTrue(writer.append(data, 600, 400, 600))
        assertTrue(writer.close())

        val entry = cache.lookup("abcdefghijk")!!
        assertArrayEquals(data, entry.file.readBytes())
        assertEquals(key(), entry.key)
    }

    @Test fun `a partial song is continued exactly where it ended`() {
        val cache = StreamBodyCache(dir)
        val data = bytes(1_000)
        cache.openWriter(key(), 0)!!.apply { append(data, 0, 300, 0); assertFalse(close()) }
        assertEquals(300, cache.storedLength(key()))

        assertNull(cache.openWriter(key(), 200), "a gap or overlap can't continue it")
        val writer = cache.openWriter(key(), 300)!!
        writer.append(data, 300, 700, 300)
        assertTrue(writer.close())
        assertArrayEquals(data, cache.lookup("abcdefghijk")!!.file.readBytes())
    }

    @Test fun `bytes out of order stop the writer without corrupting it`() {
        val cache = StreamBodyCache(dir)
        val data = bytes(1_000)
        val writer = cache.openWriter(key(), 0)!!
        writer.append(data, 0, 100, 0)
        assertFalse(writer.append(data, 300, 100, 300))
        assertFalse(writer.append(data, 100, 100, 100), "stays stopped")
        assertFalse(writer.close())
        assertEquals(100, cache.storedLength(key()))
    }

    @Test fun `one writer per song`() {
        val cache = StreamBodyCache(dir)
        val first = cache.openWriter(key(), 0)!!
        assertNull(cache.openWriter(key(), 0))
        first.close()
        assertNotNull(cache.openWriter(key(), 0))
    }

    @Test fun `complete songs survive a restart and a new rendition replaces the old one`() {
        StreamBodyCache(dir).openWriter(key(), 0)!!.apply { append(bytes(1_000), 0, 1_000, 0); close() }
        val restarted = StreamBodyCache(dir)
        assertEquals(key(), restarted.lookup("abcdefghijk")?.key)
        assertNull(restarted.openWriter(key(), 0), "already complete")

        val mp4 = StreamHeadCache.Key("abcdefghijk", 140, 800, 128_000, "audio/mp4")
        restarted.openWriter(mp4, 0)!!.apply { append(bytes(800), 0, 800, 0); close() }
        assertEquals(mp4, restarted.lookup("abcdefghijk")?.key)
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test fun `least recently used songs are evicted over the budget`() {
        var now = 1_000_000L
        val cache = StreamBodyCache(dir, maxDiskBytes = 2_500, clock = { now })
        for ((index, id) in listOf("aaaaaaaaaaa", "bbbbbbbbbbb", "ccccccccccc").withIndex()) {
            cache.openWriter(key(id), 0)!!.apply { append(bytes(1_000), 0, 1_000, 0); close() }
            File(dir, key(id).fileName).setLastModified(now + index * 10_000L)
        }
        assertNull(cache.lookup("aaaaaaaaaaa"))
        assertNotNull(cache.lookup("bbbbbbbbbbb"))
        assertNotNull(cache.lookup("ccccccccccc"))
    }

    @Test fun `oversized songs are not cached`() {
        val cache = StreamBodyCache(dir, maxEntryBytes = 500)
        assertNull(cache.openWriter(key(), 0))
    }

    @Test fun `remove forgets complete and partial copies`() {
        val cache = StreamBodyCache(dir)
        cache.openWriter(key(), 0)!!.apply { append(bytes(1_000), 0, 1_000, 0); close() }
        cache.remove("abcdefghijk")
        assertNull(cache.lookup("abcdefghijk"))
        assertEquals(0, cache.storedLength(key()))
    }
}
