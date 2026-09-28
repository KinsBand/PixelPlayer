package com.theveloper.pixelplay.data.stream

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Whole streamed songs, kept on disk so playing one again, seeking back in it, or playing a
 * next song that was prefetched in full needs no network at all: no stream URL, no CDN, no
 * way for the playback to fail. Metrolist, ViTune and Spotube all keep such a cache; this one
 * sits in the stream proxy, next to [StreamHeadCache].
 *
 * A rendition is identified exactly like in [StreamHeadCache] (video id + itag + byte size), so
 * the bytes stored for a [StreamHeadCache.Key] never change even though its URLs expire.
 *
 * Bytes are written strictly in order into `<name>.part` while they stream to the player (or
 * while the next song is prefetched); the file is renamed to `<name>` once every byte is there,
 * so a partial song is never served. A request that picks up exactly where a partial file ends
 * (the player reopening after a stall, a prefetch resumed later) continues it.
 *
 * Least recently used files are evicted beyond [maxDiskBytes]. Thread-safe.
 */
class StreamBodyCache(
    private val directory: File,
    private val maxDiskBytes: Long = DEFAULT_MAX_DISK_BYTES,
    /** Longer renditions (hour-long mixes) are not cached. */
    val maxEntryBytes: Long = DEFAULT_MAX_ENTRY_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** A complete song on disk. */
    data class Entry(val key: StreamHeadCache.Key, val file: File)

    private val lock = Any()
    private var indexed = false
    /** Latest complete rendition per video id. */
    private val complete = HashMap<String, StreamHeadCache.Key>()
    /** Video ids with a writer open. */
    private val writing = HashSet<String>()

    /** The complete cached song for [videoId], if any. */
    fun lookup(videoId: String): Entry? = synchronized(lock) {
        ensureIndexed()
        val key = complete[videoId] ?: return null
        val file = File(directory, key.fileName)
        if (file.length() != key.contentLength) {
            complete.remove(videoId)
            file.delete()
            return null
        }
        Entry(key, file)
    }

    /** Marks [entry] as just used, so eviction keeps it longest. */
    fun touch(entry: Entry) {
        val now = clock()
        if (now - entry.file.lastModified() > TOUCH_INTERVAL_MS) entry.file.setLastModified(now)
    }

    /** Bytes already stored in order for [key] (0 when nothing, the full size when complete). */
    fun storedLength(key: StreamHeadCache.Key): Long = synchronized(lock) {
        ensureIndexed()
        if (complete[key.videoId] == key) return key.contentLength
        partFile(key).length()
    }

    /**
     * A writer that stores [key]'s bytes from [startAt] on, or null when this can't continue in
     * order: another writer is busy with the song, the song is already complete, it is too big,
     * or [startAt] is neither 0 nor where the partial file ends. Starting at 0 discards any
     * partial file of the song.
     */
    fun openWriter(key: StreamHeadCache.Key, startAt: Long): Writer? {
        if (key.contentLength <= 0 || key.contentLength > maxEntryBytes) return null
        synchronized(lock) {
            ensureIndexed()
            if (complete[key.videoId] == key) return null
            if (key.videoId in writing) return null
            val part = partFile(key)
            val existing = if (part.isFile) part.length() else 0L
            if (startAt != 0L && startAt != existing) return null
            if (startAt >= key.contentLength) return null
            // One partial rendition per song.
            directory.listFiles { file -> file.name.startsWith("${key.videoId}.") && file.name.endsWith(PART) }
                ?.filter { it != part }?.forEach { it.delete() }
            val output = try {
                directory.mkdirs()
                FileOutputStream(part, startAt != 0L)
            } catch (_: IOException) {
                return null
            }
            writing += key.videoId
            return Writer(key, part, output, startAt)
        }
    }

    /** Forgets whatever is stored for [videoId], complete or partial. */
    fun remove(videoId: String) {
        synchronized(lock) {
            ensureIndexed()
            complete.remove(videoId)
            directory.listFiles { file -> file.name.startsWith("$videoId.") }
                ?.filter { it.name.endsWith(PART).not() || videoId !in writing }
                ?.forEach { it.delete() }
        }
    }

    /**
     * Appends bytes in order. [append] with a position other than [length] stops the writer
     * (the stored bytes stay valid); [close] completes the song when every byte is there.
     */
    inner class Writer internal constructor(
        val key: StreamHeadCache.Key,
        private val part: File,
        private val output: FileOutputStream,
        startAt: Long,
    ) {
        @Volatile var length: Long = startAt
            private set
        @Volatile private var usable = true
        private var closed = false

        /** Stores [count] bytes of [bytes] that sit at byte [position] of the song. */
        fun append(bytes: ByteArray, offset: Int, count: Int, position: Long): Boolean {
            if (!usable || count <= 0) return usable
            if (position != length || position + count > key.contentLength) {
                usable = false
                return false
            }
            return try {
                output.write(bytes, offset, count)
                length += count
                true
            } catch (_: IOException) {
                usable = false
                false
            }
        }

        val isComplete: Boolean get() = length == key.contentLength

        /** Ends writing; returns true when the song is now complete in the cache. */
        fun close(): Boolean {
            synchronized(lock) {
                if (closed) return complete[key.videoId] == key
                closed = true
                writing -= key.videoId
            }
            try { output.close() } catch (_: IOException) { usable = false }
            if (length != key.contentLength || part.length() != key.contentLength) return false
            val target = File(directory, key.fileName)
            synchronized(lock) {
                if (!part.renameTo(target)) {
                    part.delete()
                    return false
                }
                complete.put(key.videoId, key)?.takeIf { it != key }?.let { old ->
                    File(directory, old.fileName).delete()
                }
            }
            evictIfNeeded(keep = target)
            return true
        }
    }

    private fun partFile(key: StreamHeadCache.Key) = File(directory, key.fileName + PART)

    private fun ensureIndexed() {
        if (indexed) return
        indexed = true
        val files = directory.listFiles() ?: return
        val now = clock()
        for (file in files) {
            if (file.name.endsWith(PART)) {
                // Nobody resumes a partial song days later; free the space.
                if (now - file.lastModified() > STALE_PART_MS) file.delete()
                continue
            }
            val key = StreamHeadCache.parse(file.name)
            if (key == null || file.length() != key.contentLength) { file.delete(); continue }
            val previous = complete[key.videoId]
            if (previous == null || File(directory, previous.fileName).lastModified() < file.lastModified()) {
                previous?.let { File(directory, it.fileName).delete() }
                complete[key.videoId] = key
            } else {
                file.delete()
            }
        }
    }

    private fun evictIfNeeded(keep: File) {
        val files = directory.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxDiskBytes) return
        synchronized(lock) {
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= maxDiskBytes) break
                if (file == keep) continue
                val isPart = file.name.endsWith(PART)
                val videoId = file.name.substringBefore('.')
                if (isPart && videoId in writing) continue
                total -= file.length()
                if (!isPart) StreamHeadCache.parse(file.name)?.let { key ->
                    if (complete[key.videoId] == key) complete.remove(key.videoId)
                }
                file.delete()
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_DISK_BYTES = 256L * 1024 * 1024
        const val DEFAULT_MAX_ENTRY_BYTES = 40L * 1024 * 1024
        private const val PART = ".part"
        private const val TOUCH_INTERVAL_MS = 60L * 60 * 1000
        private const val STALE_PART_MS = 3L * 24 * 60 * 60 * 1000

        /** Reads [length] bytes at [position] of [file] into [buffer]; false if the file ended. */
        internal fun readFully(file: RandomAccessFile, position: Long, buffer: ByteArray, length: Int): Boolean {
            file.seek(position)
            var filled = 0
            while (filled < length) {
                val read = file.read(buffer, filled, length - filled)
                if (read < 0) return false
                filled += read
            }
            return true
        }
    }
}
