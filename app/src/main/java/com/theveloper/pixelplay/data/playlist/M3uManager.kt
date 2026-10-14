package com.theveloper.pixelplay.data.playlist

import android.content.Context
import android.net.Uri
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Types a document picker should offer for playlist files. Many file managers label .m3u as
 * plain text or octet-stream, so those are included too.
 */
val M3U_PICKER_MIME_TYPES: Array<String> = arrayOf(
    "audio/x-mpegurl",
    "audio/mpegurl",
    "application/x-mpegurl",
    "application/vnd.apple.mpegurl",
    "text/plain",
    "application/octet-stream"
)

/** One track line of an .m3u, and the song it became (null while nothing matched). */
data class M3uEntry(
    /** The path / URL as written in the file. */
    val raw: String,
    /** The `#EXTINF` title ("Artist - Title"), when the file has one. */
    val info: String?,
    val songId: String? = null,
    /** True when the song was found online (it wasn't on this phone). */
    val foundOnline: Boolean = false
)

/** What an M3U file turned into, in file order. */
data class M3uImportResult(
    val name: String,
    val entries: List<M3uEntry>
) {
    val songIds: List<String> get() = entries.mapNotNull { it.songId }
    /** Track entries in the file (comments / directives not counted). */
    val totalEntries: Int get() = entries.size
    val matchedCount: Int get() = entries.count { it.songId != null }
    val onlineCount: Int get() = entries.count { it.foundOnline }
    /** Entries that matched nothing, as written in the file (or "Artist - Title"). */
    val unmatched: List<String> get() = entries.filter { it.songId == null }.map { it.info ?: it.raw }
}

@Singleton
class M3uManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val youTubeRepository: com.theveloper.pixelplay.data.youtube.YouTubeRepository
) {

    /** Kept for existing callers: (playlist name, matched song ids). */
    suspend fun parseM3u(uri: Uri): Pair<String, List<String>> {
        val result = importM3u(uri)
        return result.name to result.songIds
    }

    /**
     * Reads an .m3u / .m3u8 file and matches every entry to a library song.
     *
     * Entries written on other devices rarely match a path exactly, so matching falls back step
     * by step: exact path → same trailing folders + file name (handles relative paths, other
     * storage roots, Windows `\` separators, `file://` and %-encoding) → file name → file name
     * without extension → the `#EXTINF` "Artist - Title" (or bare title) against library tags.
     */
    suspend fun importM3u(uri: Uri): M3uImportResult = withContext(Dispatchers.IO) {
        val allSongs = musicRepository.getAllSongsOnce()
        val index = LibraryIndex(allSongs)

        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            // .m3u is often Latin-1, .m3u8 is UTF-8; UTF-8 with a BOM is common for both.
            val utf8 = String(bytes, Charsets.UTF_8)
            if (utf8.contains('�')) String(bytes, Charsets.ISO_8859_1) else utf8
        }.orEmpty().removePrefix("﻿")

        var directiveName: String? = null
        var pendingInfo: String? = null
        val entries = ArrayList<M3uEntry>()

        text.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.isEmpty() -> Unit
                line.startsWith("#PLAYLIST:", ignoreCase = true) ->
                    directiveName = line.substringAfter(':').trim().ifBlank { null }
                line.startsWith("#EXTINF", ignoreCase = true) ->
                    // "#EXTINF:123 tvg-x=\"…\",Artist - Title"
                    pendingInfo = line.substringAfter(',', "").trim().ifBlank { null }
                line.startsWith("#") -> Unit
                else -> {
                    entries += M3uEntry(raw = line, info = pendingInfo, songId = index.match(line, pendingInfo)?.id)
                    pendingInfo = null
                }
            }
        }

        val fileName = queryDisplayName(uri)
            ?.substringBeforeLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
        M3uImportResult(
            name = directiveName ?: fileName ?: "Imported Playlist",
            entries = entries
        )
    }

    /**
     * Songs the file lists that aren't on this phone (not in the library, not downloaded):
     * looks each one up online by its "Artist - Title" (or file name) and adds the best match as
     * an online song, so it can go straight into the playlist. Entries keep their file order;
     * ones with no convincing match stay unmatched. [onProgress] gets (looked up, to look up).
     */
    suspend fun findMissingOnline(
        result: M3uImportResult,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): M3uImportResult = kotlinx.coroutines.coroutineScope {
        val missing = result.entries.withIndex().filter { it.value.songId == null }
        if (missing.isEmpty()) return@coroutineScope result
        val gate = kotlinx.coroutines.sync.Semaphore(6)
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val found = missing.map { (index, entry) ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val song = try {
                        lookUpOnline(entry)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    onProgress(done.incrementAndGet(), missing.size)
                    index to song
                }
            }
        }.awaitAll()
        val updated = result.entries.toMutableList()
        found.forEach { (index, song) ->
            if (song == null) return@forEach
            try {
                // Saved as an online song so the playlist (and likes) can resolve it later.
                musicRepository.saveCloudSong(song)
                updated[index] = updated[index].copy(songId = song.id, foundOnline = true)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Leave it unmatched.
            }
        }
        result.copy(entries = updated)
    }

    private suspend fun lookUpOnline(entry: M3uEntry): Song? {
        val (artist, title) = describe(entry) ?: return null
        val wantTitle = cleanTitle(title)
        val wantArtist = artist?.let(::cleanTitle)
        if (wantTitle.isBlank()) return null
        fun overlap(a: String, b: String): Double {
            val x = a.split(' ').filter { it.isNotBlank() }.toSet()
            val y = b.split(' ').filter { it.isNotBlank() }.toSet()
            if (x.isEmpty() || y.isEmpty()) return 0.0
            return x.intersect(y).size.toDouble() / minOf(x.size, y.size)
        }
        fun pick(candidates: List<Song>, needArtist: Boolean): Song? = candidates.take(10).firstOrNull { song ->
            val gotTitle = cleanTitle(song.title)
            val titleOk = gotTitle == wantTitle || gotTitle.contains(wantTitle) || wantTitle.contains(gotTitle) ||
                overlap(gotTitle, wantTitle) >= 0.6
            val gotArtist = cleanTitle(song.displayArtist)
            val artistOk = wantArtist.isNullOrBlank() ||
                overlap(gotArtist, wantArtist) >= 0.5 || gotArtist.contains(wantArtist) || wantArtist.contains(gotArtist) ||
                // File names often have no reliable artist part; a strong title match is enough.
                (!needArtist && gotTitle == wantTitle)
            titleOk && artistOk
        }
        suspend fun search(query: String): List<Song> {
            // One retry: a single slow or failed request shouldn't drop the song.
            repeat(2) { attempt ->
                val got = try {
                    kotlinx.coroutines.withTimeoutOrNull(10_000) { youTubeRepository.searchSongs(query) }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (!got.isNullOrEmpty()) return got
                if (attempt == 0) kotlinx.coroutines.delay(400)
            }
            return emptyList()
        }
        // "Artist Title" first, then the title alone (still checked against the artist).
        val first = search(listOfNotNull(artist, title).joinToString(" "))
        pick(first, needArtist = entry.info != null)?.let { return it }
        if (artist != null) {
            pick(search(title), needArtist = true)?.let { return it }
        }
        return null
    }

    /** Normalised title without "(Official Video)", "[Remastered 2011]", "feat. …" and the like. */
    private fun cleanTitle(text: String): String =
        LibraryIndex.normalizeText(
            text.replace(Regex("[\\(\\[][^\\)\\]]*[\\)\\]]"), " ")
                .replace(Regex("(?i)\\s(feat\\.?|ft\\.?|featuring)\\s.*$"), " ")
                .replace(Regex("(?i)\\s-\\s(remaster(ed)?|live|radio edit|single version).*$"), " ")
        ).ifBlank { LibraryIndex.normalizeText(text) }

    /** (artist, title) for an entry: from `#EXTINF`, else from the file name. */
    private fun describe(entry: M3uEntry): Pair<String?, String>? {
        val text = entry.info ?: run {
            if (entry.raw.startsWith("http://", true) || entry.raw.startsWith("https://", true)) return null
            val file = entry.raw.replace('\\', '/').substringAfterLast('/')
            val decoded = if (file.contains('%')) runCatching { URLDecoder.decode(file.replace("+", "%2B"), "UTF-8") }.getOrDefault(file) else file
            decoded.substringBeforeLast('.')
                .replace('_', ' ')
                // "01 - ", "07. ", "3-" track numbers
                .replace(Regex("^\\s*\\d{1,3}\\s*[-._)]\\s*"), "")
                .trim()
        }
        if (text.isBlank()) return null
        val separator = text.indexOf(" - ")
        return if (separator > 0) {
            text.substring(0, separator).trim().ifBlank { null } to text.substring(separator + 3).trim()
        } else {
            null to text.trim()
        }.takeIf { it.second.isNotBlank() }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
    } catch (_: Exception) {
        null
    } ?: uri.lastPathSegment?.substringAfterLast('/')

    fun generateM3u(playlist: Playlist, songs: List<Song>): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        sb.append("#PLAYLIST:${playlist.name}\n")
        for (song in songs) {
            sb.append("#EXTINF:${song.duration / 1000},${song.artist} - ${song.title}\n")
            sb.append("${song.path}\n")
        }
        return sb.toString()
    }

    /** Library lookups for M3U matching, built once per import. */
    private class LibraryIndex(songs: List<Song>) {
        private val byPath = HashMap<String, Song>()
        private val byFileName = HashMap<String, MutableList<Song>>()
        private val byStem = HashMap<String, MutableList<Song>>()
        private val byArtistTitle = HashMap<String, Song>()
        private val byTitle = HashMap<String, MutableList<Song>>()

        init {
            songs.forEach { song ->
                listOf(song.path, song.contentUriString).filter { it.isNotBlank() }.forEach { raw ->
                    val path = normalizePath(raw)
                    byPath.putIfAbsent(path, song)
                    val file = path.substringAfterLast('/')
                    byFileName.getOrPut(file) { ArrayList() } += song
                    byStem.getOrPut(file.substringBeforeLast('.')) { ArrayList() } += song
                }
                val title = normalizeText(song.title)
                if (title.isNotEmpty()) {
                    byTitle.getOrPut(title) { ArrayList() } += song
                    song.artist.split(",", "/", "&", ";", " feat. ", " ft. ")
                        .map(::normalizeText)
                        .filter { it.isNotEmpty() }
                        .forEach { artist -> byArtistTitle.putIfAbsent("$artist\u0000$title", song) }
                }
            }
        }

        fun match(entry: String, info: String?): Song? {
            if (!entry.startsWith("http://", true) && !entry.startsWith("https://", true)) {
                val path = normalizePath(entry)
                byPath[path]?.let { return it }
                val file = path.substringAfterLast('/')
                byFileName[file]?.let { candidates -> return bestBySuffix(candidates, path) }
                byStem[file.substringBeforeLast('.')]?.let { candidates -> return bestBySuffix(candidates, path) }
            }
            if (info != null) {
                val separator = info.indexOf(" - ")
                if (separator > 0) {
                    val artist = normalizeText(info.substring(0, separator))
                    val title = normalizeText(info.substring(separator + 3))
                    byArtistTitle["$artist\u0000$title"]?.let { return it }
                    byTitle[title]?.singleOrNull()?.let { return it }
                }
                byTitle[normalizeText(info)]?.singleOrNull()?.let { return it }
            }
            return null
        }

        /** Of several songs with the same file name, the one sharing the most trailing folders. */
        private fun bestBySuffix(candidates: List<Song>, path: String): Song {
            if (candidates.size == 1) return candidates.first()
            val wanted = path.split('/').reversed()
            return candidates.maxByOrNull { song ->
                val have = normalizePath(song.path).split('/').reversed()
                wanted.zip(have).takeWhile { (a, b) -> a == b }.size
            } ?: candidates.first()
        }

        companion object {
            fun normalizePath(raw: String): String {
                var p = raw.trim().replace('\\', '/')
                if (p.startsWith("file://", ignoreCase = true)) p = p.substring(7)
                if (p.contains('%')) p = try { URLDecoder.decode(p.replace("+", "%2B"), "UTF-8") } catch (_: Exception) { p }
                return p.lowercase(Locale.ROOT)
            }

            fun normalizeText(raw: String): String =
                Normalizer.normalize(raw, Normalizer.Form.NFD)
                    .replace(Regex("\\p{Mn}+"), "")
                    .lowercase(Locale.ROOT)
                    .replace(Regex("\\s*[(\\[].*?[)\\]]"), "") // "(Remastered)", "[Live]"
                    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                    .trim()
        }
    }
}

/** Lets composables (the Create playlist popup) reach the importer without a ViewModel change. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface M3uImportEntryPoint {
    fun m3uManager(): M3uManager
}
