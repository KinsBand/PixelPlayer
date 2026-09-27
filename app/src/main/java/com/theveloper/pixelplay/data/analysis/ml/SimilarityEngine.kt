package com.theveloper.pixelplay.data.analysis.ml

import com.theveloper.pixelplay.data.database.EnrichmentDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class SimilarityEngine @Inject constructor(
    private val enrichmentDao: EnrichmentDao
) {
    private var cachedTrackIds: LongArray? = null
    private var cachedMatrix: Array<FloatArray>? = null
    private var lastCacheTime: Long = 0L

    /**
     * Warms up in-memory matrix cache from Room.
     */
    suspend fun warmUp() = withContext(Dispatchers.Default) {
        val all = enrichmentDao.getAllEmbeddings()
        if (all.isEmpty()) return@withContext

        val trackIds = LongArray(all.size)
        val matrix = Array(all.size) { FloatArray(128) }

        all.forEachIndexed { i, entity ->
            trackIds[i] = entity.trackId
            val emb = entity.embedding
            val len = minOf(emb.size, 128)
            emb.copyInto(matrix[i], 0, 0, len)
        }

        cachedTrackIds = trackIds
        cachedMatrix = matrix
        lastCacheTime = System.currentTimeMillis()
    }

    /**
     * Calculates the top [limit] similar track IDs based on vector cosine similarity.
     * Uses cached matrix when available (<50ms), falls back to direct query.
     */
    suspend fun getSimilarTracks(trackId: Long, limit: Int = 5): List<Long> = withContext(Dispatchers.Default) {
        if (cachedMatrix == null || System.currentTimeMillis() - lastCacheTime > 60_000L) {
            warmUp()
        }

        val trackIds = cachedTrackIds
        val matrix = cachedMatrix

        if (trackIds != null && matrix != null) {
            val queryIdx = trackIds.indexOf(trackId)
            if (queryIdx >= 0) {
                val queryVec = matrix[queryIdx]
                val scores = mutableListOf<Pair<Long, Float>>()
                for (i in trackIds.indices) {
                    if (trackIds[i] == trackId) continue
                    val sim = computeCosineSimilarity(queryVec, matrix[i])
                    scores.add(trackIds[i] to sim)
                }
                return@withContext scores.sortedByDescending { it.second }.take(limit).map { it.first }
            }
        }

        // Direct DB fallback if not found in cache
        val currentEmbeddingEntity = enrichmentDao.getEmbedding(trackId) ?: return@withContext emptyList()
        val currentVector = currentEmbeddingEntity.embedding
        val allEmbeddings = enrichmentDao.getAllEmbeddings()
        if (allEmbeddings.size <= 1) return@withContext emptyList()

        allEmbeddings
            .filter { it.trackId != trackId }
            .map { other -> other.trackId to computeCosineSimilarity(currentVector, other.embedding) }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun computeCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in vectorA.indices) {
            val a = vectorA[i]
            val b = vectorB[i]
            dotProduct += a * b
            normA += a * a
            normB += b * b
        }

        if (normA <= 0f || normB <= 0f) return 0f
        return (dotProduct / (sqrt(normA.toDouble()) * sqrt(normB.toDouble()))).toFloat()
    }
}

