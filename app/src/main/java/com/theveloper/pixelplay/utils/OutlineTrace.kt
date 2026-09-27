package com.theveloper.pixelplay.utils

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

/**
 * A rounded rectangle's outline as two halves that both start at the top middle: one runs left,
 * down the left side and along the bottom; the other runs right and down the right side. They
 * meet at the bottom middle. Drawing both up to the same fraction gives the "spreads from the
 * top, finishes at the bottom" progress trace used by music-note breaks and cover downloads.
 */
internal fun splitOutlineFromTop(l: Float, t: Float, r: Float, b: Float, rad: Float): Pair<Path, Path> {
    val cx = (l + r) / 2f
    val left = Path().apply {
        moveTo(cx, t)
        lineTo(l + rad, t)
        arcTo(Rect(l, t, l + 2 * rad, t + 2 * rad), 270f, -90f, false)
        lineTo(l, b - rad)
        arcTo(Rect(l, b - 2 * rad, l + 2 * rad, b), 180f, -90f, false)
        lineTo(cx, b)
    }
    val right = Path().apply {
        moveTo(cx, t)
        lineTo(r - rad, t)
        arcTo(Rect(r - 2 * rad, t, r, t + 2 * rad), 270f, 90f, false)
        lineTo(r, b - rad)
        arcTo(Rect(r - 2 * rad, b - 2 * rad, r, b), 0f, 90f, false)
        lineTo(cx, b)
    }
    return left to right
}
