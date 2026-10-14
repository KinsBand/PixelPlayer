package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Add a song" glyph: a music note with a small plus badge at its bottom-end.
 * [noteSize] is the note; the plus is ~55 % of it.
 */
@Composable
fun MusicNoteAddIcon(
    modifier: Modifier = Modifier,
    noteSize: Dp = 20.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null
) {
    val badge = noteSize * 0.55f
    Box(
        modifier = modifier
            .size(noteSize + badge * 0.35f)
            .then(
                if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription }
                else Modifier
            )
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(noteSize)
                .align(Alignment.TopStart)
        )
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(badge)
                .align(Alignment.BottomEnd)
                .offset(x = badge * 0.08f, y = badge * 0.08f)
        )
    }
}
