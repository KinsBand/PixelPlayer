package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Width of a [CollectionCard]; carousels of them line up on this. */
val CollectionCardWidth = 148.dp

/**
 * The playlist / album card used by Friends' playlists and the artist page: a square cover inside
 * a padded card, a title that always takes two lines (so cards in a row line up), one subtitle
 * line, and an optional round action button in the cover's bottom-right corner.
 *
 * [CollectionCardSkeleton] has exactly the same size, so a loading row never jumps.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionCard(
    title: String,
    subtitle: String = "",
    coverModel: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** What TalkBack reads for the whole card. */
    description: String = listOf(title, subtitle).filter { it.isNotBlank() }.joinToString(", "),
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    /** Shown under the subtitle, e.g. "Tap to save". */
    extraLine: String? = null,
    /** Overlays drawn on the cover (badges). */
    coverOverlay: (@Composable BoxScope.() -> Unit)? = null,
    /** Round button in the cover's bottom-right corner. */
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null,
    actionContent: (@Composable () -> Unit)? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    /** Cover width ÷ height: 1 (square) for albums / playlists / songs, 16:9 for videos. */
    coverAspectRatio: Float = 1f,
    /** Cover clip; null = the card's usual rounded square. Artists use a circle. */
    coverShape: androidx.compose.ui.graphics.Shape? = null,
) {
    Column(
        modifier
            .width(CollectionCardWidth)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(8.dp)
    ) {
        Box {
            SmartImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(coverAspectRatio),
                shape = coverShape ?: MaterialTheme.shapes.medium
            )
            coverOverlay?.invoke(this)
            if (onAction != null && actionContent != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(CollectionCardActionSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(onClick = onAction, onClickLabel = actionLabel),
                    contentAlignment = Alignment.Center
                ) { actionContent() }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        if (subtitleContent != null) {
            subtitleContent()
        } else if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (extraLine != null) {
            Text(
                extraLine,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/** A loading [CollectionCard]: same box, same line heights, shimmering blocks in place of content. */
@Composable
fun CollectionCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(CollectionCardWidth)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .clearAndSetSemantics { }
            .padding(8.dp)
    ) {
        ShimmerBox(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium))
        Spacer(Modifier.height(8.dp))
        SkeletonTextLine(MaterialTheme.typography.titleSmall, lines = 2, fraction = 0.85f)
        Spacer(Modifier.height(2.dp))
        SkeletonTextLine(MaterialTheme.typography.labelMedium, lines = 1, fraction = 0.5f)
    }
}

/**
 * Reserves exactly the height [lines] of text in [style] take (an invisible Text measures it),
 * with a shimmering bar on the first line. Used so skeletons match real text to the pixel.
 */
@Composable
fun SkeletonTextLine(
    style: androidx.compose.ui.text.TextStyle,
    lines: Int = 1,
    fraction: Float = 0.7f,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth()) {
        Text(" ", style = style, minLines = lines, maxLines = lines, color = Color.Transparent)
        ShimmerBox(
            Modifier
                .padding(top = 3.dp)
                .fillMaxWidth(fraction)
                .height(with(androidx.compose.ui.platform.LocalDensity.current) { (style.fontSize.toPx() * 0.8f).toDp() })
                .clip(CircleShape)
        )
    }
}

val CollectionCardActionSize = 40.dp
