package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Lets a screen hide the bottom navigation bar for a while (e.g. while Your Music search is
 * open, so the keyboard and results aren't covered). Read by MainActivity next to the
 * per-route hidden list; the bar slides out/in with its usual animation.
 */
object NavBarVisibility {
    var hiddenByScreen by mutableStateOf(false)

    /** Set by [NavBarScrollWatcher]: scrolling down hides the bar, scrolling up shows it. */
    var hiddenByScroll by mutableStateOf(false)

    /**
     * The bar's live slide progress (1 = fully shown, 0 = gone), handed over once by
     * MainActivity. Things that sit above the bar (the mini player, Radio's list/map button)
     * read it inside `graphicsLayer {}` so they move down with the bar frame by frame
     * without recomposing.
     */
    var visibilityProgress: State<Float> = mutableFloatStateOf(1f)
        internal set

    /**
     * False on screens whose route already hides the bar (artist, album, playlist pages…).
     * Scrolling there must not flip [hiddenByScroll]: nothing would visibly change on that
     * screen, but the flag would survive to the next screen and make the mini player and bar
     * play a needless hide/show the moment you navigate back. Set by MainActivity.
     */
    var scrollHideAllowed: Boolean = true
        internal set
}

/**
 * Watches vertical scrolling of every screen (attached once around the navigation host, so
 * each LazyColumn / verticalScroll reports to it through nested scroll). Horizontal rows
 * only report x, so carousels and chip rows never move the bar.
 *
 * Direction has to persist for [thresholdPx] before the bar changes, so small jitters don't
 * flicker it. Pulling down at the very top (nothing left to scroll) always shows it.
 */
class NavBarScrollWatcher(private val thresholdPx: Float) : NestedScrollConnection {
    private var accumulated = 0f

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (!NavBarVisibility.scrollHideAllowed) {
            if (NavBarVisibility.hiddenByScroll) show() else accumulated = 0f
            return Offset.Zero
        }
        val dy = consumed.y
        if (dy == 0f) {
            // At the top and still pulling down: show.
            if (available.y > 0f) show()
            return Offset.Zero
        }
        if ((dy > 0f) != (accumulated > 0f)) accumulated = 0f
        accumulated += dy
        when {
            accumulated <= -thresholdPx -> { NavBarVisibility.hiddenByScroll = true; accumulated = 0f }
            accumulated >= thresholdPx -> show()
        }
        return Offset.Zero
    }

    fun show() {
        accumulated = 0f
        NavBarVisibility.hiddenByScroll = false
    }
}
