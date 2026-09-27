package com.theveloper.pixelplay.data.stream

import java.io.File
import java.io.IOException

/**
 * The first bytes of streamed songs, kept so a song that was played or prewarmed before can
 * start from local bytes while its (expiring) stream URL is resolved again in parallel.
 *
 * A YouTube rendition is identified by video id + itag, and `clen` is its exact byte size, so
 * the bytes behind a [Key] never change even though every signed URL for them expires. The
 * key is stored in the file name (`<videoId>.<itag>.<clen>.<bitrate>.<ext>`), which makes the
 * directory its own index: after a restart it still says which rendition each song used.
 *
 * Files are written to a temporary name and renamed, so a partially written head is never
 * served. A small RAM tier keeps the most recent heads for the next-song case.
 */
class StreamHeadCache(
    private val directory: File,
    private val maxDiskBytes: Long = DEFAULT_MAX_DISK_BYTES,
    val headBytes: Int = DEFAULT_HEAD_BYTES,
    private val maxRamEntries: Int = DEFAULT_RAM_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Key(
        val videoId: String,
        val itag: Int,
        val contentLength: Long,
        val bitrate: Int,
        /** "audio/webm" or "audio/mp4". */
        val mimeType: String,
    ) {
        internal val fileName: String
            get() = "$videoId.$itag.$contentLength.$bitrate.${if (mimeType == WEBM) "webm" else "m4a"}"
    }

    /** A cached head: bytes `[0, length)` of the rendition [key]. */
    data class Entry(val key: Key, val length: Int)

    private val lock = Any()
    private var indexed = false
    /** Latest entry per video id. */
    private val entries = HashMap<String, Entry>()
    private val ram = object : LinkedHashMap<String, ByteArray>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>) = size > maxRamEntries
    }

    /** The cached head for [videoId], if any bytes are stored. */
    fun lookup(videoId: String): Entry? = synchronized(lock) {
        ensureIndexed()
        entries[videoId]
    }

    /**
     * Returns bytes `[from, entry.length)` of the head, or null if the file vanished (the
     * entry is then forgotten).
     */
    fun read(entry: Entry, from: Int = 0): ByteArray? {
        require(from in 0..entry.length)
        val bytes = synchronized(lock) { ram[entry.key.fileName] } ?: run {
            val file = File(directory, entry.key.fileName)
            val loaded = try { file.readBytes() } catch (_: IOException) { null }
            if (loaded == null || loaded.size != entry.length) {
                remove(entry.key)
                return null
            }
            touch(file)
            synchronized(lock) { ram[entry.key.fileName] = loaded }
            loaded
        }
        return if (from == 0) bytes else bytes.copyOfRange(from, bytes.size)
    }

    /** Whether a head at least [minBytes] long is already stored for exactly [key]. */
    fun has(key: Key, minBytes: Int = 1): Boolean = synchronized(lock) {
        ensureIndexed()
        entries[key.videoId]?.let { it.key == key && it.length >= minBytes } == true
    }

    /**
     * Stores up to [headBytes] leading bytes of [key]. A shorter head never replaces a
     * longer one for the same rendition. Returns false when nothing was written.
     */
    fun store(key: Key, bytes: ByteArray, length: Int = bytes.size): Boolean {
        val size = minOf(length, headBytes, bytes.size)
        if (size < MIN_STORED_BYTES && size.toLong() != key.contentLength) return false
        synchronized(lock) {
            ensureIndexed()
            val existing = entries[key.videoId]
            if (existing != null && existing.key == key && existing.length >= size) return false
        }
        val data = if (size == bytes.size) bytes else bytes.copyOf(size)
        val target = File(directory, key.fileName)
        try {
            directory.mkdirs()
            val temp = File(directory, "${key.fileName}.${Thread.currentThread().id}.tmp")
            temp.writeBytes(data)
            if (!temp.renameTo(target)) {
                temp.delete()
                return false
            }
        } catch (_: IOException) {
            return false
        }
        synchronized(lock) {
            // One rendition per song: a new one replaces whatever was stored before.
            entries.put(key.videoId, Entry(key, size))?.takeIf { it.key != key }?.let { old ->
                ram.remove(old.key.fileName)
                File(directory, old.key.fileName).delete()
            }
            ram[key.fileName] = data
        }
        evictIfNeeded()
        return true
    }

    /** Forgets the head for [key] (e.g. YouTube no longer serves that exact rendition). */
    fun remove(key: Key) {
        synchronized(lock) {
            if (entries[key.videoId]?.key == key) entries.remove(key.videoId)
            ram.remove(key.fileName)
        }
        File(directory, key.fileName).delete()
    }

    /** Drops the RAM tier; disk entries stay. */
    fun trimMemory() = synchronized(lock) { ram.clear() }

    private fun ensureIndexed() {
        if (indexed) return
        indexed = true
        val files = directory.listFiles() ?: return
        for (file in files) {
            if (file.name.endsWith(".tmp")) { file.delete(); continue }
            val key = parse(file.name)
            val length = file.length()
            if (key == null || length <= 0 || length > key.contentLength) { file.delete(); continue }
            val entry = Entry(key, length.toInt())
            val previous = entries[key.videoId]
            if (previous == null || File(directory, previous.key.fileName).lastModified() < file.lastModified()) {
                previous?.let { File(directory, it.key.fileName).delete() }
                entries[key.videoId] = entry
            } else {
                file.delete()
            }
        }
    }

    private fun evictIfNeeded() {
        val files = directory.listFiles { file -> !file.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxDiskBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxDiskBytes) break
            total -= file.length()
            parse(file.name)?.let { remove(it) } ?: file.delete()
        }
    }

    /** Keeps recently used heads from being evicted; at most one metadata write per day. */
    private fun touch(file: File) {
        val now = clock()
        if (now - file.lastModified() > TOUCH_INTERVAL_MS) file.setLastModified(now)
    }

    companion object {
        const val DEFAULT_HEAD_BYTES = 512 * 1024
        const val DEFAULT_MAX_DISK_BYTES = 96L * 1024 * 1024
        const val DEFAULT_RAM_ENTRIES = 8
        /** Heads shorter than this are not worth a file (unless they are the whole song). */
        const val MIN_STORED_BYTES = 32 * 1024
        private const val TOUCH_INTERVAL_MS = 24L * 60 * 60 * 1000
        private const val WEBM = "audio/webm"
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

        internal fun parse(name: String): Key? {
            val parts = name.split('.')
            if (parts.size != 5 || !VIDEO_ID.matches(parts[0])) return null
            val itag = parts[1].toIntOrNull() ?: return null
            val length = parts[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
            val bitrate = parts[3].toIntOrNull() ?: return null
            val mime = when (parts[4]) { "webm" -> WEBM; "m4a" -> "audio/mp4"; else -> return null }
            return Key(parts[0], itag, length, bitrate, mime)
        }
    }
}
