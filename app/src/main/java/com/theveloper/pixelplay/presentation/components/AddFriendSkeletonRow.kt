package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * The last row of the Friends list: a static friend-shaped placeholder (dashed avatar with +,
 * two skeleton bars) that opens "Add a friend". Same height and padding as FriendRow, no
 * shimmer, so it never reads as "loading".
 */
@Composable
fun AddFriendSkeletonRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClickLabel = "Add a friend", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Add a friend" }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val outline = colors.outlineVariant
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(40.dp)
                    .drawBehind {
                        val stroke = 1.5.dp.toPx()
                        drawCircle(
                            color = outline,
                            radius = size.minDimension / 2f - stroke / 2f,
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = Stroke(
                                width = stroke,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Add a friend",
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant
            )
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(10.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.surfaceContainerHigh)
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .width(64.dp)
                .height(10.dp)
                .clip(MaterialTheme.shapes.small)
                .background(colors.surfaceContainerHigh)
        )
    }
}
