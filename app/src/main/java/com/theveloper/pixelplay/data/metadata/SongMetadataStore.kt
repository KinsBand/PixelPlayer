package com.theveloper.pixelplay.data.metadata

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.lyrics.LyricsAssetRevision
import com.theveloper.pixelplay.data.lyrics.LyricsTiming
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class MetadataState { KNOWN, ESTIMATED, UNKNOWN, UNAVAILABLE, NOT_APPLICABLE, NOT_ANALYSED, STALE }
data class MetadataClaim(val value: String?, val source: String, val retrievedAt: Long,
    val state: MetadataState = MetadataState.KNOWN, val locked: Boolean = false,
    val confidence: Float? = null, val unit: String? = null, val methodVersion: String? = null,
    val assetRevision: String? = null)
data class SongMetadataDocument(val schemaVersion: Int = 1, val songId: String,
    val assetRevision: String, val claims: Map<String, List<MetadataClaim>> = emptyMap()) {
    fun selected(key: String): MetadataClaim? = claims[key]?.lastOrNull { it.locked }
        ?: claims[key]?.lastOrNull { it.state != MetadataState.STALE }
        ?: claims[key]?.lastOrNull()
}

internal fun mergeMetadataClaim(existing: List<MetadataClaim>, incoming: MetadataClaim): List<MetadataClaim> {
    if (existing.lastOrNull()?.let { it.value == incoming.value && it.source == incoming.source && it.state == incoming.state && it.locked == incoming.locked && it.assetRevision == incoming.assetRevision } == true) return existing
    // Keep the manual claim even after many automatic refreshes. Limit automatic history per field.
    return (existing.filter { it.locked && !(incoming.locked && it.source == incoming.source) } + existing.filterNot { it.locked }.takeLast(18) + incoming)
}

@Singleton
class SongMetadataStore @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.noBackupFilesDir, "song-metadata-v1").apply { mkdirs() }
    private val gson = Gson()
    private val mutex = Mutex()
    private fun file(song: Song) = AtomicFile(File(directory, LyricsTiming.hash(song.id) + ".json"))
    private fun load(song: Song): SongMetadataDocument {
        val current = LyricsAssetRevision.of(song)
        val saved = try {
            file(song).openRead().bufferedReader().use { gson.fromJson(it, SongMetadataDocument::class.java) }
                ?: error("Metadata file is empty; existing data was preserved")
        } catch (_: java.io.FileNotFoundException) {
            return SongMetadataDocument(songId = song.id, assetRevision = current)
        }
        if (saved.assetRevision == current) return saved
        val assetFields = SongMetadataCatalogue.fields.filter { it.scope == MetadataScope.ASSET }.map { it.key }.toSet()
        return saved.copy(assetRevision = current, claims = saved.claims.mapValues { (key, claims) ->
            if (key in assetFields) claims.map { it.copy(state = MetadataState.STALE) } else claims
        })
    }
    private fun save(song: Song, document: SongMetadataDocument) {
        val target = file(song)
        val out = target.startWrite()
        try { out.write(gson.toJson(document).toByteArray()); target.finishWrite(out) }
        catch (e: Exception) { target.failWrite(out); throw e }
    }
    suspend fun read(song: Song): SongMetadataDocument = withContext(Dispatchers.IO) {
        mutex.withLock {
            var document = load(song)
            val tree = gson.toJsonTree(song)
            SongMetadataCatalogue.fields.forEach { field ->
                var value: JsonElement? = tree
                field.songPath?.split('.')?.forEach { part -> value = value?.takeIf { it.isJsonObject }?.asJsonObject?.get(part) }
                if (field.songPath != null && value != null && value?.isJsonNull == false) {
                    val text = value!!.let { if (it.isJsonPrimitive) it.asString else it.toString() }
                    if (text.isNotBlank() && text != "[]" && !(text == "0" && field.key !in setOf("user.play_count"))) {
                        val claim = MetadataClaim(text, "library", System.currentTimeMillis(),
                            assetRevision = if (field.scope == MetadataScope.ASSET) document.assetRevision else null)
                        document = document.copy(claims = document.claims + (field.key to mergeMetadataClaim(document.claims[field.key].orEmpty(), claim)))
                    }
                }
            }
            save(song, document)
            document
        }
    }
    suspend fun setManual(song: Song, key: String, value: String): SongMetadataDocument = record(song, mapOf(key to
        MetadataClaim(value.trim().takeIf { it.isNotEmpty() }, "user", System.currentTimeMillis(),
            state = if (value.isBlank()) MetadataState.UNKNOWN else MetadataState.KNOWN, locked = true)))
    suspend fun record(song: Song, values: Map<String, MetadataClaim>): SongMetadataDocument = withContext(Dispatchers.IO) {
        require(values.keys.all { it.matches(Regex("[a-z][a-z0-9_.]{0,119}")) })
        require(values.values.all { (it.value?.length ?: 0) <= 100000 })
        mutex.withLock {
            val document = load(song)
            val merged = document.claims.toMutableMap()
            values.forEach { (key, claim) -> merged[key] = mergeMetadataClaim(merged[key].orEmpty(), claim) }
            document.copy(claims = merged).also { save(song, it) }
        }
    }
}
