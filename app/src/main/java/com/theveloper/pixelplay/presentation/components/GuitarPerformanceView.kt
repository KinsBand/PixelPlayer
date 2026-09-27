package com.theveloper.pixelplay.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.theveloper.pixelplay.presentation.components.tabs.TabPracticeController

/**
 * Guitar / bass tab from Songsterr as full-width notation. Practice controls (play, loop,
 * speed, pitch, transpose, metronome…) live in the swipe-up panel under the toolbar, and
 * share it through [practice].
 */
@Composable
fun GuitarPerformanceView(
    onBackgroundColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    title: String = "",
    artist: String = "",
    songId: String? = null,
    practice: TabPracticeController? = null,
) {
    val controller = practice ?: rememberTabPractice(title, artist, songId)
    TabScoreView(controller, onBackgroundColor, accentColor, modifier)
}
