package com.theveloper.pixelplay.presentation.library

import android.content.Context
import com.theveloper.pixelplay.data.MixAffinityIndex
import com.theveloper.pixelplay.data.mixIdentity
import com.theveloper.pixelplay.data.model.Song
import org.json.JSONArray
import java.util.Locale
import kotlin.math.abs

/**
 * Builds the "up next" songs for the mix buttons under the current song in the queue.
 *
 * - [SIMILAR_ID] ranks the pool by how close each song is to the playing one: same artist,
 *   album and genre, the vibe presets both songs fit, and the analysed features (energy,
 *   mood, danceability, tempo) when both songs have them.
 * - Any other id is a Your Music vibe filter (a preset or one of the user's custom filters).
 *   It uses the same mix builder as the Your Music screen, nudged towards the playing song.
 *
 * Pure: no Android, no I/O except [loadCustomFilters]. Runs on a background thread.
 */
object QueueVibeMix {

    const val SIMILAR_ID = "similar"
    const val DEFAULT_SIZE = 40
    private const val MAX_PER_ARTIST = 3

    /** Custom filters the user created on the Your Music screen (same storage). */
    fun loadCustomFilters(context: Context): List<VibeFilter> = try {
        val prefs = context.getSharedPreferences("your_music_filters", Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString("custom_filters", "[]") ?: "[]")
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id")
            val text = obj.optString("text")
            if (id.isBlank() || text.isBlank()) null else MusicVibeFilters.custom(id, text)
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Every filter the queue row can show, in Your Music order (presets, then custom). */
    fun filters(custom: List<VibeFilter>): List<VibeFilter> = MusicVibeFilters.presets + custom

    fun build(
        filterId: String,
        current: Song,
        pool: List<Song>,
        likedIds: Set<String>,
        customFilters: List<VibeFilter>,
        seed: Long,
        size: Int = DEFAULT_SIZE,
        /** Learned per-song adjustment shared with the continuous mix (see MusicVibeFilters.buildMix). */
        adjust: Map<String, Double> = emptyMap()
    ): List<Song> {
        val currentIdentity = mixIdentity(current)
        val candidates = pool.asSequence()
            .filter { it.id != current.id }
            .distinctBy { mixIdentity(it) }
            .filter { mixIdentity(it) != currentIdentity }
            .toList()
        if (candidates.isEmpty()) return emptyList()
        return if (filterId == SIMILAR_ID) {
            similar(current, candidates, likedIds, seed, size, adjust)
        } else {
            val filter = filters(customFilters).firstOrNull { it.id == filterId } ?: return emptyList()
            vibe(current, candidates, filter, likedIds, seed, size, adjust)
        }
    }

    private fun similar(current: Song, pool: List<Song>, likedIds: Set<String>, seed: Long, size: Int, adjust: Map<String, Double>): List<Song> {
        val random = java.util.Random(seed)
        val affinity = MixAffinityIndex(listOf(current))
        val currentVibes = MusicVibeFilters.presets.filter {
            MusicVibeFilters.score(current, it) >= MusicVibeFilters.MATCH_THRESHOLD
        }
        val scored = pool.map { song ->
            var score = affinity.affinity(song).toDouble()
            // Shared vibe presets: songs that fit the same moods / contexts as the playing one.
            if (currentVibes.isNotEmpty()) {
                val shared = currentVibes.count { MusicVibeFilters.score(song, it) >= MusicVibeFilters.MATCH_THRESHOLD }
                score += shared * 1.5
            }
            score += featureCloseness(current, song) * 4.0
            if (song.id in likedIds) score += 0.75
            score += random.nextDouble() * 1.5
            song to score
        }
        // Nothing related at all: better to say so than to queue random songs. Relatedness is
        // judged before the learned adjustment, which only reorders related songs.
        val related = scored.filter { it.second >= 2.0 }.map { (song, score) -> song to score + (adjust[song.id] ?: 0.0) }
        return spread(related.sortedByDescending { it.second }.map { it.first }, size)
    }

    private fun vibe(
        current: Song,
        pool: List<Song>,
        filter: VibeFilter,
        likedIds: Set<String>,
        seed: Long,
        size: Int,
        adjust: Map<String, Double>
    ): List<Song> {
        val mix = MusicVibeFilters.buildMix(pool, filter, likedIds, emptyMap(), seed, size = size * 2, adjust = adjust)
        if (mix.isEmpty()) return emptyList()
        // Lean the mix towards the playing song so it continues from what is on now.
        val affinity = MixAffinityIndex(listOf(current))
        val ranked = mix.mapIndexed { index, song ->
            song to ((mix.size - index) * 0.5 / mix.size * 10.0 + affinity.affinity(song) * 0.6)
        }.sortedByDescending { it.second }.map { it.first }
        return spread(ranked, size)
    }

    /** 0..1 closeness of analysed audio features; 0 when either song has none. */
    private fun featureCloseness(a: Song, b: Song): Double {
        var total = 0.0
        var weight = 0.0
        fun add(x: Float?, y: Float?, scale: Float = 1f) {
            if (x == null || y == null) return
            total += (1.0 - (abs(x - y) / scale).coerceIn(0f, 1f))
            weight += 1.0
        }
        add(a.mixIntelligence.energy, b.mixIntelligence.energy)
        add(a.mixIntelligence.valence, b.mixIntelligence.valence)
        add(a.mixIntelligence.danceability, b.mixIntelligence.danceability)
        add(a.mixIntelligence.acousticness, b.mixIntelligence.acousticness)
        // Fold half / double tempo: catalogue BPMs are often an octave off.
        val bpmA = a.musicalFeatures.bpm
        val bpmB = b.musicalFeatures.bpm
        val folded = if (bpmA != null && bpmB != null && bpmA > 0f) listOf(bpmA, bpmA * 2f, bpmA / 2f).minByOrNull { abs(it - bpmB) } else bpmA
        add(folded, bpmB, scale = 40f)
        return if (weight == 0.0) 0.0 else total / weight
    }

    /** Caps songs per artist and avoids the same artist twice in a row. */
    private fun spread(ranked: List<Song>, size: Int): List<Song> {
        fun artistKey(song: Song) = song.displayArtist.lowercase(Locale.ROOT)
            .substringBefore(",").substringBefore(" feat").trim()
        val used = HashMap<String, Int>()
        val remaining = ranked.toMutableList()
        val chosen = ArrayList<Song>(size)
        while (chosen.size < size && remaining.isNotEmpty()) {
            val previous = chosen.lastOrNull()?.let(::artistKey)
            val index = remaining.indexOfFirst { song ->
                val key = artistKey(song)
                (used[key] ?: 0) < MAX_PER_ARTIST && key != previous
            }.takeIf { it >= 0 } ?: remaining.indexOfFirst { (used[artistKey(it)] ?: 0) < MAX_PER_ARTIST }
            if (index < 0) break
            val song = remaining.removeAt(index)
            used.merge(artistKey(song), 1) { x, y -> x + y }
            chosen += song
        }
        return chosen
    }
}
