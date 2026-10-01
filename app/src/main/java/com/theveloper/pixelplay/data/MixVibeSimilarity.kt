package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.model.Song
import kotlin.math.abs
import kotlin.math.ln

/**
 * How alike two songs *feel* (0 = nothing alike … 1 = same vibe), from whatever is known:
 * energy, valence, danceability, acousticness, vocals, tempo, mood, genre, artist and, when
 * both have one, the on-device sound embedding.
 *
 * Unknown features don't count either way, and a small prior pulls thinly described songs
 * towards "average" (≈ 0.35), so one shared genre tag can't make two songs look identical.
 *
 * Used for the live signals a listener gives while listening:
 * - songs removed from the queue (and early skips) → "not this vibe": similar songs drop,
 * - songs queued by hand → "this vibe": similar songs rise,
 * - friends-in-the-room picks → the friend song that best fits what's playing.
 */
internal object MixVibeSimilarity {
    /** Similarity assumed where nothing is known. */
    const val PRIOR = 0.35
    private const val PRIOR_WEIGHT = 1.0

    private fun unit(value: Float?): Double? = value?.takeIf { it.isFinite() && it in 0f..1f }?.toDouble()
    private fun bpm(song: Song): Double? = song.musicalFeatures.bpm?.takeIf { it.isFinite() && it in 30f..300f }?.toDouble()
    private fun mood(song: Song): String? = song.mixIntelligence.mood?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("unknown", true) && !MixVibe.isMoodEstimated(song) }
        ?.lowercase(java.util.Locale.ROOT)

    fun similarity(a: Song, b: Song, embeddings: Map<String, FloatArray> = emptyMap()): Double {
        if (a.id == b.id) return 1.0
        var sum = PRIOR * PRIOR_WEIGHT
        var weight = PRIOR_WEIGHT
        fun add(agreement: Double, w: Double) {
            sum += agreement.coerceIn(0.0, 1.0) * w
            weight += w
        }
        fun feature(x: Float?, y: Float?, w: Double) {
            val left = unit(x) ?: return
            val right = unit(y) ?: return
            add(1.0 - abs(left - right) / 0.5, w)
        }
        val ma = a.mixIntelligence
        val mb = b.mixIntelligence
        feature(ma.energy, mb.energy, 1.6)
        feature(ma.valence, mb.valence, 1.0)
        feature(ma.danceability, mb.danceability, 0.8)
        feature(ma.acousticness, mb.acousticness, 0.8)
        feature(ma.instrumentalness, mb.instrumentalness, 0.6)
        val bpmA = bpm(a)
        val bpmB = bpm(b)
        if (bpmA != null && bpmB != null) {
            // Catalogue BPMs are sometimes half / double the felt tempo: fold octaves.
            val gap = minOf(abs(ln(bpmB * 0.5 / bpmA)), abs(ln(bpmB / bpmA)), abs(ln(bpmB * 2.0 / bpmA)))
            add(1.0 - gap / 0.25, 0.8)
        }
        val moodA = mood(a)
        val moodB = mood(b)
        if (moodA != null && moodB != null) add(if (moodA == moodB) 1.0 else 0.0, 0.8)
        val genreA = GenreTaxonomy.match(a.genre)
        val genreB = GenreTaxonomy.match(b.genre)
        if (genreA != null && genreB != null) {
            add(if (genreA.id == genreB.id) 1.0 else if (genreA.family == genreB.family) 0.6 else 0.0, 1.2)
        }
        // A shared artist says a lot; a different artist says little (it isn't counted against).
        if (a.artist.isNotBlank() && a.artist.equals(b.artist, ignoreCase = true)) add(1.0, 1.5)
        val va = embeddings[a.id]
        val vb = embeddings[b.id]
        if (va != null && vb != null && va.size == vb.size) {
            var dot = 0f
            for (i in va.indices) dot += va[i] * vb[i]
            // VGGish cosines between music clips mostly sit in 0.55…0.95.
            add((dot - 0.55) / 0.4, 2.5)
        }
        return (sum / weight).coerceIn(0.0, 1.0)
    }

    /** Recency-weighted similarity of [song] to [context] (newest last counts most). */
    fun toContext(song: Song, context: List<Song>, embeddings: Map<String, FloatArray> = emptyMap()): Double {
        val recent = context.takeLast(3)
        if (recent.isEmpty()) return PRIOR
        var total = 0.0
        var weights = 0.0
        recent.forEachIndexed { index, seed ->
            val w = (index + 1).toDouble()
            total += similarity(song, seed, embeddings) * w
            weights += w
        }
        return total / weights
    }
}

/**
 * The listener's live "vibe" signals, kept for [TTL_MS] whether or not a mix is running, so
 * removing or queueing songs before a mix starts (or while friends are in the room) still
 * counts once it does. Newest last.
 */
internal class MixVibeSignals {
    private data class Signal(val song: Song, val at: Long)

    private val removed = ArrayDeque<Signal>()
    private val picked = ArrayDeque<Signal>()

    @Synchronized fun markRemoved(song: Song) = push(removed, song).also { drop(picked, song) }
    @Synchronized fun unmarkRemoved(song: Song) = drop(removed, song)
    @Synchronized fun markPicked(song: Song) = push(picked, song).also { drop(removed, song) }

    @Synchronized fun removedSongs(now: Long = System.currentTimeMillis()): List<Song> = fresh(removed, now)
    @Synchronized fun pickedSongs(now: Long = System.currentTimeMillis()): List<Song> = fresh(picked, now)

    @Synchronized fun clear() { removed.clear(); picked.clear() }

    private fun push(list: ArrayDeque<Signal>, song: Song) {
        drop(list, song)
        list.addLast(Signal(song, System.currentTimeMillis()))
        while (list.size > MAX) list.removeFirst()
    }

    private fun drop(list: ArrayDeque<Signal>, song: Song) {
        val identity = mixIdentity(song)
        list.removeAll { it.song.id == song.id || mixIdentity(it.song) == identity }
    }

    private fun fresh(list: ArrayDeque<Signal>, now: Long): List<Song> {
        while (list.isNotEmpty() && now - list.first().at > TTL_MS) list.removeFirst()
        return list.map { it.song }
    }

    companion object {
        const val TTL_MS = 45 * 60_000L
        const val MAX = 12
    }
}
