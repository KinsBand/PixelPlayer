package com.theveloper.pixelplay.data.lyrics

import android.content.Context
import android.util.AtomicFile
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Finds a song's structure once and keeps it with the song.
 *
 *  - Each song's structure is saved in its own file (`noBackupFilesDir/song-structure-v1/`),
 *    keyed by song id, so opening the lyrics again never searches again.
 *  - If the song's lyrics change, the structure is rebuilt from the new lyrics. The online
 *    lookup (LRCLIB's plain lyrics, which often carry "[Verse]" / "[Chorus]" headers) is cached
 *    separately by title + artist, hits and misses both, so it is also never repeated
 *    (a miss is retried after [MISS_RETRY_MS]).
 *
 * Order: tags in the synced lyrics → headers in the lyrics we already have → LRCLIB headers →
 * on-device analysis of the synced lyrics ([SongStructureAnalyzer]).
 */
class SongStructureRepository private constructor(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val directory = File(context.noBackupFilesDir, "song-structure-v1").apply { mkdirs() }
    private val memory = ConcurrentHashMap<String, SongStructure>().also { map ->
        com.theveloper.pixelplay.data.diagnostics.HeapPressure.register("song-structure") { map.clear() }
    }
    private val inFlight = ConcurrentHashMap<String, Deferred<SongStructure?>>()

    /** Already-known structure for [songId] (memory only; no disk or network). */
    fun cached(songId: String): SongStructure? = memory[songId]

    /** Resolve in the background, e.g. as soon as lyrics load, so the sheet opens with it. */
    fun prefetch(song: Song, lyrics: Lyrics?) {
        if (lyrics?.synced.isNullOrEmpty()) return
        scope.launch { runCatching { resolve(song, lyrics) } }
    }

    /**
     * The structure for [song] with these [lyrics]; null when the lyrics have no timing (a
     * structure without times can't follow playback) or nothing useful could be found.
     */
    suspend fun resolve(song: Song, lyrics: Lyrics?): SongStructure? {
        val synced = lyrics?.synced?.takeIf { it.isNotEmpty() } ?: return null
        val fingerprint = SongStructureAnalyzer.fingerprint(synced)
        memory[song.id]?.takeIf { it.lyricsFingerprint == fingerprint }?.let { return it }

        val key = "${song.id}|$fingerprint"
        inFlight[key]?.let { return it.await() }
        val job = scope.async {
            try {
                readSaved(song.id)?.takeIf { it.lyricsFingerprint == fingerprint }
                    ?: build(song, lyrics, fingerprint)?.also { save(song.id, it) }
            } catch (e: Exception) {
                Timber.w(e, "Song structure failed for ${song.id}")
                null
            }
        }
        inFlight[key] = job
        return try {
            job.await()?.also {
                // One entry per song played this session; keep it bounded on long sessions.
                if (memory.size >= MAX_MEMORY_ENTRIES) memory.clear()
                memory[song.id] = it
            }
        } finally {
            inFlight.remove(key)
        }
    }

    private suspend fun build(song: Song, lyrics: Lyrics, fingerprint: String): SongStructure? {
        val synced = lyrics.synced.orEmpty()
        val duration = song.duration

        SongStructureAnalyzer.fromSyncedTags(synced, duration)?.let {
            return SongStructure(it, "tags", fingerprint)
        }
        lyrics.plain?.let { plain ->
            SongStructureAnalyzer.fromPlainHeaders(plain, synced, duration)?.let {
                return SongStructure(it, "lyrics_text", fingerprint)
            }
        }
        onlineHeaderedLyrics(song)?.let { text ->
            SongStructureAnalyzer.fromPlainHeaders(text.lines(), synced, duration)?.let {
                return SongStructure(it, "lrclib", fingerprint)
            }
        }
        return SongStructureAnalyzer.analyse(synced, duration)?.let {
            SongStructure(it, "analysis", fingerprint)
        }
    }

    // ─── Online: LRCLIB plain lyrics with section headers ──────────────────────────────

    private suspend fun onlineHeaderedLyrics(song: Song): String? = withContext(Dispatchers.IO) {
        val title = cleanTitle(song.title)
        val artist = cleanArtist(song.displayArtist.ifBlank { song.artist })
        if (title.isBlank()) return@withContext null
        val cacheFile = File(directory, "online-" + hash("${norm(title)}|${norm(artist)}") + ".json")
        readJson(cacheFile)?.let { saved ->
            val text = saved.optString("text")
            val at = saved.optLong("at")
            if (text.isNotEmpty()) return@withContext text
            if (System.currentTimeMillis() - at < MISS_RETRY_MS) return@withContext null
        }

        val found = lrclibWithHeaders(title, artist, song.duration)
        writeJson(cacheFile, JSONObject().put("text", found.orEmpty()).put("at", System.currentTimeMillis()))
        found
    }

    private fun lrclibWithHeaders(title: String, artist: String, durationMs: Long): String? {
        val durationSec = (durationMs / 1000).toInt()
        fun ok(plain: String?) = plain?.takeIf { SongStructureAnalyzer.containsHeaders(it.lines()) }

        // Exact match first.
        val exact = buildString {
            append("https://lrclib.net/api/get?track_name=").append(enc(title))
            append("&artist_name=").append(enc(artist))
            if (durationSec > 0) append("&duration=").append(durationSec)
        }
        getText(exact)?.let { body ->
            runCatching { ok(JSONObject(body).optString("plainLyrics")) }.getOrNull()?.let { return it }
        }

        // Then any search result of about the same length that has headers.
        val search = "https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}"
        val results = getText(search)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return null
        for (i in 0 until minOf(results.length(), 10)) {
            val hit = results.optJSONObject(i) ?: continue
            val hitDuration = hit.optDouble("duration", 0.0)
            if (durationSec > 0 && hitDuration > 0 && abs(hitDuration - durationSec) > 4) continue
            ok(hit.optString("plainLyrics"))?.let { return it }
        }
        return null
    }

    private fun getText(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 6_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PixelPlayer (https://github.com/KinsBand/PixelPlayer)")
        }
        return try {
            if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    // ─── Storage ───────────────────────────────────────────────────────────────────────

    private fun songFile(songId: String) = File(directory, hash(songId) + ".json")

    private fun readSaved(songId: String): SongStructure? {
        val json = readJson(songFile(songId)) ?: return null
        val sections = json.optJSONArray("sections") ?: return null
        val list = (0 until sections.length()).mapNotNull { i ->
            val s = sections.optJSONObject(i) ?: return@mapNotNull null
            SongSection(
                kind = SongSectionKind.fromKey(s.optString("kind")),
                label = s.optString("label"),
                startMs = s.optLong("start"),
                endMs = s.optLong("end"),
            )
        }
        if (list.size < 2) return null
        return SongStructure(
            sections = list,
            source = json.optString("source"),
            lyricsFingerprint = json.optString("fingerprint"),
            createdAt = json.optLong("at"),
        )
    }

    private fun save(songId: String, structure: SongStructure) {
        val sections = JSONArray()
        structure.sections.forEach {
            sections.put(
                JSONObject()
                    .put("kind", it.kind.key)
                    .put("label", it.label)
                    .put("start", it.startMs)
                    .put("end", it.endMs)
            )
        }
        writeJson(
            songFile(songId),
            JSONObject()
                .put("songId", songId)
                .put("source", structure.source)
                .put("fingerprint", structure.lyricsFingerprint)
                .put("at", structure.createdAt)
                .put("sections", sections)
        )
    }

    private fun readJson(file: File): JSONObject? = try {
        AtomicFile(file).openRead().bufferedReader().use { JSONObject(it.readText()) }
    } catch (_: Exception) {
        null
    }

    private fun writeJson(file: File, json: JSONObject) {
        val atomic = AtomicFile(file)
        val out = try { atomic.startWrite() } catch (e: Exception) { return }
        try {
            out.write(json.toString().toByteArray())
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun cleanTitle(title: String): String = title
        .replace(Regex("""\s*[(\[][^)\]]*(official|video|audio|lyric|lyrics|visualizer|hd|4k|remaster(ed)?|explicit|clean)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+(ft\.?|feat\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun cleanArtist(artist: String): String = artist
        .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*(,|&| x | feat\.? | ft\.? ).*$""", RegexOption.IGNORE_CASE), "")
        .trim()
        .takeUnless { it.equals("Unknown Artist", true) }
        .orEmpty()

    private fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    companion object {
        private const val MISS_RETRY_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_MEMORY_ENTRIES = 64

        @Volatile private var instance: SongStructureRepository? = null

        fun get(context: Context): SongStructureRepository =
            instance ?: synchronized(this) {
                instance ?: SongStructureRepository(context.applicationContext).also { instance = it }
            }
    }
}
