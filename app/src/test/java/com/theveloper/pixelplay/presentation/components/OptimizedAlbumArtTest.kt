package com.theveloper.pixelplay.presentation.components

import coil.size.Dimension
import coil.size.Size
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OptimizedAlbumArtTest {

    @Test
    fun safeAlbumArtTargetSize_clampsOriginalRequests() {
        val targetSize = safeAlbumArtTargetSize(Size.ORIGINAL)

        assertThat((targetSize.width as Dimension.Pixels).px)
            .isEqualTo(MaxSafeAlbumArtDimensionPx)
        assertThat((targetSize.height as Dimension.Pixels).px)
            .isEqualTo(MaxSafeAlbumArtDimensionPx)
    }

    @Test
    fun smallerArtworkKeys_pointAtListRowCacheEntries() {
        val url = "https://lh3.googleusercontent.com/cover=w1400-h1400-l90-rj"

        assertThat(smallerArtworkKeyCandidates(url)).containsExactly(
            "https://lh3.googleusercontent.com/cover=w256-h256-l90-rj",
            "https://lh3.googleusercontent.com/cover=w512-h512-l90-rj",
            url
        ).inOrder()
        assertThat(smallerArtworkKeyCandidates("content://media/external/audio/albumart/1"))
            .containsExactly("content://media/external/audio/albumart/1")
        assertThat(smallerArtworkKeyCandidates(null)).isEmpty()
    }

    @Test
    fun safeAlbumArtTargetSize_keepsBoundedRequests() {
        val targetSize = Size(800, 600)

        assertThat(safeAlbumArtTargetSize(targetSize)).isEqualTo(targetSize)
    }
}
