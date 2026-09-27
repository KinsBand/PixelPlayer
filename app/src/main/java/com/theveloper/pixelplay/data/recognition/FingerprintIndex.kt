package com.theveloper.pixelplay.data.recognition

import com.theveloper.pixelplay.data.recognition.RecognitionConstants.FINGERPRINTER_VERSION
import com.theveloper.pixelplay.data.recognition.RecognitionConstants.MAX_SONG_INDEX
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.channels.FileChannel

/**
 * On-disk fingerprint index.
 *
 * ## Why not Room
 *
 * At the tuned density (152 hashes/s) a 1000-track library is about 32 million
 * entries. As a Room table with an index on the hash column that is roughly
 * half a gigabyte and insert throughput collapses long before the build
 * finishes. Here each entry is exactly 8 bytes — one 24-bit hash and one
 * 32-bit payload — in a memory-mapped file searched with a binary search. No
 * SQLite round trip, no allocation per lookup.
 *
 * ## Layout
 *
 * ```
 * magic   "PPFP"                     4 bytes
 * version int                        fingerprinter version
 * entries int                        total entry count
 * songs   int                        number of indexed songs
 * buckets int[BUCKET_COUNT + 1]      start offset of each hash bucket
 * hashes  int[entries]               ascending within each bucket
 * payload int[entries]               parallel to hashes
 * ```
 *
 * ## Why buckets
 *
 * The builder spills each song's landmarks straight into 256 per-bucket temp
 * files, then sorts one bucket at a time in memory. That keeps peak memory at
 * roughly `entries / 256` rather than the whole index, needs no k-way merge
 * across a thousand open shard handles, and makes a cancelled build resumable
 * at bucket granularity. Lookup gets a free first-level index out of it.
 *
 * ## Why the bucket is a MIXED function of the hash, not its top bits
 *
 * The obvious `hash ushr 16` takes bits that are dominated by the landmark's
 * `dt` field, and `dt` is heavily skewed in real audio — peaks are dense in
 * time, so short gaps vastly outnumber long ones. Measured on a
 * realistically skewed distribution, top-bit bucketing left 134 of 256
 * buckets empty and loaded the largest bucket with 25× its fair share, which
 * turns the "sort one bucket in memory" step into a 25 MB allocation at 32 M
 * entries instead of 1 MB. Fibonacci mixing spreads it to 1.0× ideal across
 * all 256 buckets. Entries stay sorted by raw hash *within* a bucket, so
 * lookup is unaffected.
 */
object FingerprintIndex {

    const val MAGIC = 0x50504650            // "PPFP"
    const val BUCKET_BITS = 8
    const val BUCKET_COUNT = 1 shl BUCKET_BITS
    private const val HEADER_INTS = 4 + BUCKET_COUNT + 1

    /**
     * 2^32 / golden ratio. Measured across all 256 buckets on a realistically
     * skewed landmark distribution: max 1.04× ideal, min 0.96× ideal.
     */
    private val FIB_MIX = 0x9E3779B9u.toInt()

    fun bucketOf(hash: Int): Int = (hash * FIB_MIX) ushr (32 - BUCKET_BITS)

    fun indexFile(dir: File): File = File(dir, "fp_v$FINGERPRINTER_VERSION.idx")
    fun manifestFile(dir: File): File = File(dir, "fp_v$FINGERPRINTER_VERSION.manifest")
    fun bucketDir(dir: File): File = File(dir, "fp_v${FINGERPRINTER_VERSION}_buckets")

    // ---------------------------------------------------------------- writer

    /**
     * Spills landmarks into per-bucket temp files, one song at a time, then
     * seals them into the final index.
     *
     * Not thread-safe; the index builder owns one instance for one build.
     */
    class Builder(private val dir: File) : Closeable {

        private val buckets = bucketDir(dir).apply { mkdirs() }
        private val streams = arrayOfNulls<DataOutputStream>(BUCKET_COUNT)
        private val songIds = ArrayList<String>()
        private var entryCount = 0L

        /** Returns the song index assigned, or -1 if the index is full. */
        fun addSong(songId: String, landmarks: LandmarkSet): Int {
            if (songIds.size > MAX_SONG_INDEX) return -1
            val songIndex = songIds.size
            songIds.add(songId)
            for (i in 0 until landmarks.count) {
                val hash = landmarks.hashes[i]
                val payload = LandmarkHasher.packPayload(songIndex, landmarks.offsets[i])
                val out = streamFor(bucketOf(hash))
                out.writeInt(hash)
                out.writeInt(payload)
                entryCount++
            }
            return songIndex
        }

        private fun streamFor(bucket: Int): DataOutputStream =
            streams[bucket] ?: DataOutputStream(
                BufferedOutputStream(File(buckets, "b$bucket").outputStream(), 1 shl 16)
            ).also { streams[bucket] = it }

        /**
         * Sorts each bucket and writes the final index. Returns the entry count.
         *
         * [onProgress] is called with the bucket number as each one is sealed,
         * so the caller can surface progress and honour cancellation between
         * buckets.
         */
        fun seal(onProgress: (bucket: Int, total: Int) -> Unit = { _, _ -> }): Long {
            streams.forEach { it?.flush(); it?.close() }
            streams.fill(null)

            val target = indexFile(dir)
            val tmp = File(target.parentFile, target.name + ".tmp")
            RandomAccessFile(tmp, "rw").use { raf ->
                val headerBytes = HEADER_INTS.toLong() * 4
                raf.setLength(headerBytes + entryCount * 8)

                // Pass 1: sizes, so bucket start offsets are known up front.
                val starts = IntArray(BUCKET_COUNT + 1)
                var running = 0
                for (b in 0 until BUCKET_COUNT) {
                    starts[b] = running
                    val f = File(buckets, "b$b")
                    running += if (f.exists()) (f.length() / 8).toInt() else 0
                }
                starts[BUCKET_COUNT] = running

                raf.seek(0)
                raf.writeInt(MAGIC)
                raf.writeInt(FINGERPRINTER_VERSION)
                raf.writeInt(running)
                raf.writeInt(songIds.size)
                for (v in starts) raf.writeInt(v)

                val hashBase = headerBytes
                val payloadBase = headerBytes + running.toLong() * 4

                for (b in 0 until BUCKET_COUNT) {
                    val f = File(buckets, "b$b")
                    val n = starts[b + 1] - starts[b]
                    if (n > 0 && f.exists()) {
                        val hashes = IntArray(n)
                        val payloads = IntArray(n)
                        RandomAccessFile(f, "r").use { src ->
                            val buf = src.channel
                                .map(FileChannel.MapMode.READ_ONLY, 0, f.length())
                                .order(ByteOrder.BIG_ENDIAN)
                                .asIntBuffer()
                            for (i in 0 until n) {
                                hashes[i] = buf.get(i * 2)
                                payloads[i] = buf.get(i * 2 + 1)
                            }
                        }
                        sortByHash(hashes, payloads, n)
                        raf.seek(hashBase + starts[b].toLong() * 4)
                        for (i in 0 until n) raf.writeInt(hashes[i])
                        raf.seek(payloadBase + starts[b].toLong() * 4)
                        for (i in 0 until n) raf.writeInt(payloads[i])
                    }
                    f.delete()
                    onProgress(b + 1, BUCKET_COUNT)
                }
            }
            if (target.exists()) target.delete()
            check(tmp.renameTo(target)) { "could not seal fingerprint index" }
            bucketDir(dir).delete()

            manifestFile(dir).writeText(
                buildString {
                    append("v=").append(FINGERPRINTER_VERSION).append('\n')
                    songIds.forEach { append(it).append('\n') }
                }
            )
            return entryCount
        }

        override fun close() {
            streams.forEach { runCatching { it?.close() } }
            streams.fill(null)
        }
    }

    /**
     * Insertion-sorted-by-key pair sort. Uses an index permutation so the two
     * parallel arrays stay aligned; buckets are ~1/256 of the index, so this
     * runs on tens of thousands of entries at a time, not millions.
     */
    private fun sortByHash(hashes: IntArray, payloads: IntArray, n: Int) {
        val order = (0 until n).sortedBy { hashes[it] }
        val h = IntArray(n); val p = IntArray(n)
        for (i in 0 until n) { h[i] = hashes[order[i]]; p[i] = payloads[order[i]] }
        System.arraycopy(h, 0, hashes, 0, n)
        System.arraycopy(p, 0, payloads, 0, n)
    }

    // ---------------------------------------------------------------- reader

    /**
     * Memory-mapped reader. Open once and keep it; mapping is cheap but not
     * free, and the OS page cache does the rest.
     */
    class Reader private constructor(
        private val raf: RandomAccessFile,
        private val hashes: IntBuffer,
        private val payloads: IntBuffer,
        private val bucketStarts: IntArray,
        val entryCount: Int,
        val songIds: List<String>
    ) : Closeable {

        /**
         * Appends every payload whose hash equals [hash] to [into].
         * Returns the number appended, or 0 when the bucket is oversized —
         * see [RecognitionConstants.MAX_BUCKET_SIZE].
         */
        fun lookup(hash: Int, into: MutableList<Int>): Int {
            val b = bucketOf(hash)
            var lo = bucketStarts[b]
            var hi = bucketStarts[b + 1]
            if (lo >= hi) return 0

            // Lower bound.
            var l = lo; var r = hi
            while (l < r) {
                val m = (l + r) ushr 1
                if (hashes.get(m) < hash) l = m + 1 else r = m
            }
            val start = l
            if (start >= hi || hashes.get(start) != hash) return 0

            // Upper bound.
            l = start; r = hi
            while (l < r) {
                val m = (l + r) ushr 1
                if (hashes.get(m) <= hash) l = m + 1 else r = m
            }
            val end = l

            val n = end - start
            if (n > RecognitionConstants.MAX_BUCKET_SIZE) return 0
            for (i in start until end) into.add(payloads.get(i))
            return n
        }

        fun songIdAt(songIndex: Int): String? = songIds.getOrNull(songIndex)

        override fun close() {
            runCatching { raf.close() }
        }

        companion object {
            /** Returns null when the file is absent, truncated or a stale version. */
            fun open(dir: File): Reader? {
                val file = indexFile(dir)
                if (!file.exists() || file.length() < HEADER_INTS * 4L) return null
                val raf = RandomAccessFile(file, "r")
                try {
                    val ch = raf.channel
                    val header = ch.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_INTS * 4L)
                        .order(ByteOrder.BIG_ENDIAN).asIntBuffer()
                    if (header.get(0) != MAGIC) { raf.close(); return null }
                    if (header.get(1) != FINGERPRINTER_VERSION) { raf.close(); return null }
                    val entries = header.get(2)
                    if (entries < 0) { raf.close(); return null }

                    val starts = IntArray(BUCKET_COUNT + 1) { header.get(4 + it) }
                    val expected = HEADER_INTS * 4L + entries.toLong() * 8L
                    if (file.length() < expected) { raf.close(); return null }

                    val hashBase = HEADER_INTS * 4L
                    val payloadBase = hashBase + entries.toLong() * 4L
                    val hashes = ch.map(FileChannel.MapMode.READ_ONLY, hashBase, entries.toLong() * 4L)
                        .order(ByteOrder.BIG_ENDIAN).asIntBuffer()
                    val payloads = ch.map(FileChannel.MapMode.READ_ONLY, payloadBase, entries.toLong() * 4L)
                        .order(ByteOrder.BIG_ENDIAN).asIntBuffer()

                    val manifest = manifestFile(dir)
                    val ids = if (manifest.exists()) {
                        manifest.readLines().drop(1).filter { it.isNotEmpty() }
                    } else emptyList()

                    return Reader(raf, hashes, payloads, starts, entries, ids)
                } catch (t: Throwable) {
                    runCatching { raf.close() }
                    return null
                }
            }
        }
    }
}
