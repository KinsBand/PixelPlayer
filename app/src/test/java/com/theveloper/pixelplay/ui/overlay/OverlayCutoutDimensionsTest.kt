package com.theveloper.pixelplay.ui.overlay

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OverlayCutoutDimensionsTest {

    @Test
    fun pixel10ProHardwareMeasures_matchSpecifications() {
        assertEquals(412.dp, OverlayCutoutDimensions.DEFAULT_SCREEN_WIDTH)
        assertEquals(919.dp, OverlayCutoutDimensions.DEFAULT_SCREEN_HEIGHT)
        assertEquals(34.dp, OverlayCutoutDimensions.DEFAULT_CUTOUT_DIAMETER)
        assertEquals(66.dp, OverlayCutoutDimensions.DEFAULT_STATUS_BAR_HEIGHT)
        assertEquals(33.dp, OverlayCutoutDimensions.DEFAULT_CUTOUT_CENTER_Y)
    }

    @Test
    fun snapHeights_progressLogically() {
        val geometry = CutoutGeometry(
            centerXDp = 206.dp,
            centerYDp = 33.dp,
            topDp = 0.dp,
            widthDp = 34.dp,
            heightDp = 66.dp,
            bottomDp = 66.dp,
            isRealHardwareCutout = true
        )
        val metrics = IslandMetrics(geometry, IslandDisplay(412, 919), 1f)
        val l1 = metrics.height(CutoutExpansionLevel.LEVEL_1_SINGLE)
        val l2 = metrics.height(CutoutExpansionLevel.LEVEL_2_THREE)
        val l3 = metrics.height(CutoutExpansionLevel.LEVEL_3_SIX)

        assertTrue(geometry.bottomDp < l1)
        assertTrue(l1 < l2)
        assertTrue(l2 < l3)
    }

    @Test
    fun nearestSnapLevel_correctlySnapsAcrossFourLevels() {
        val screenHeight = 919.dp
        val geometry = CutoutGeometry(
            centerXDp = 206.dp,
            centerYDp = 33.dp,
            topDp = 0.dp,
            widthDp = 34.dp,
            heightDp = 66.dp,
            bottomDp = 66.dp,
            isRealHardwareCutout = true
        )

        val metrics = IslandMetrics(geometry, IslandDisplay(412, screenHeight.value.toInt()), 1f)
        // Each current resting height, perturbed slightly, returns to its own level.
        CutoutExpansionLevel.entries.forEach { level ->
            assertEquals(level, metrics.snapLevel(metrics.height(level) + 2.dp, 0f))
        }
        // A deliberate fast downward flick advances beyond a slow release.
        assertEquals(CutoutExpansionLevel.LEVEL_2_THREE,
            metrics.snapLevel(metrics.height(CutoutExpansionLevel.LEVEL_1_SINGLE), 500f))

    }

    @Test
    fun fallbackCutoutGeometry_isCentered() {
        val density = Density(2.625f) // 420 dpi scale
        val geometry = OverlayCutoutDimensions.resolveCutoutGeometry(
            displayCutout = null,
            density = density,
            screenWidthPx = 1080,
            screenHeightPx = 2410
        )

        val expectedCenter = with(density) { (540).toDp() }
        assertEquals(expectedCenter.value, geometry.centerXDp.value, 0.5f)
        assertEquals(33.dp.value, geometry.centerYDp.value, 0.1f)
    }
}
