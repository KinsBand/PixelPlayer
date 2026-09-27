package com.theveloper.pixelplay.data.analysis

import com.theveloper.pixelplay.data.analysis.ml.SimilarityEngine
import com.theveloper.pixelplay.data.database.EnrichmentDao
import com.theveloper.pixelplay.data.database.TrackEmbeddingEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarityEngineTest {

    private val enrichmentDao = mockk<EnrichmentDao>()
    private val similarityEngine = SimilarityEngine(enrichmentDao)

    @Test
    fun testGetSimilarTracks_returnsTracksInDescendingOrderOfSimilarity() = runTest {
        val targetTrackId = 1L
        
        // Mock current embedding
        val current = TrackEmbeddingEntity(
            trackId = targetTrackId,
            embedding = floatArrayOf(1.0f, 0.0f, 0.0f)
        )
        coEvery { enrichmentDao.getEmbedding(targetTrackId) } returns current

        // Mock library embeddings:
        // - Track 2: same vector (similarity 1.0)
        // - Track 3: orthogonal vector (similarity 0.0)
        // - Track 4: opposite vector (similarity -1.0)
        // - Track 5: close vector (similarity 0.866)
        val t2 = TrackEmbeddingEntity(2L, floatArrayOf(1.0f, 0.0f, 0.0f))
        val t3 = TrackEmbeddingEntity(3L, floatArrayOf(0.0f, 1.0f, 0.0f))
        val t4 = TrackEmbeddingEntity(4L, floatArrayOf(-1.0f, 0.0f, 0.0f))
        val t5 = TrackEmbeddingEntity(5L, floatArrayOf(0.866f, 0.5f, 0.0f))

        coEvery { enrichmentDao.getAllEmbeddings() } returns listOf(current, t2, t3, t4, t5)

        val results = similarityEngine.getSimilarTracks(targetTrackId, limit = 5)

        assertEquals(4, results.size)
        // Expect: Track 2 (1.0), Track 5 (0.866), Track 3 (0.0), Track 4 (-1.0)
        assertEquals(2L, results[0])
        assertEquals(5L, results[1])
        assertEquals(3L, results[2])
        assertEquals(4L, results[3])
    }

    @Test
    fun testGetSimilarTracks_returnsEmptyIfNoEmbeddingForTarget() = runTest {
        coEvery { enrichmentDao.getAllEmbeddings() } returns emptyList()
        coEvery { enrichmentDao.getEmbedding(10L) } returns null
        val results = similarityEngine.getSimilarTracks(10L)
        assertTrue(results.isEmpty())
    }
}
