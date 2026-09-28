package com.theveloper.pixelplay.presentation.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.data.repository.CrossPlatformArtistMetrics
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistAnalyticsBottomSheet(
    metrics: CrossPlatformArtistMetrics,
    artistName: String,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 28.dp, smoothnessAsPercentTL = 60,
            cornerRadiusTR = 28.dp, smoothnessAsPercentTR = 60,
            cornerRadiusBL = 0.dp, smoothnessAsPercentBL = 0,
            cornerRadiusBR = 0.dp, smoothnessAsPercentBR = 0
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Insights,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "ARTIST AUDIENCE METRICS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Aggregated Summary Cards: TOTAL MONTHLY LISTENERS | TOTAL FOLLOWERS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricAggregateCard(
                    modifier = Modifier.weight(1f),
                    title = "TOTAL MONTHLY LISTENERS",
                    value = "${formatMetricCompact(metrics.totalMonthlyListeners)} Aggregate",
                    accentColor = MaterialTheme.colorScheme.primary
                )

                MetricAggregateCard(
                    modifier = Modifier.weight(1f),
                    title = "TOTAL FOLLOWERS",
                    value = "${formatMetricCompact(metrics.totalFollowers)} Aggregate",
                    accentColor = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(18.dp))

            // PLATFORM BREAKDOWN
            Text(
                text = "PLATFORM BREAKDOWN",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlatformMetricRow(
                    platformName = "Spotify",
                    listeners = metrics.spotifyListeners,
                    followers = metrics.spotifyFollowers,
                    brandColor = Color(0xFF1DB954)
                )

                PlatformMetricRow(
                    platformName = "iTunes / Apple Music",
                    listeners = metrics.itunesListeners,
                    followers = metrics.itunesFollowers,
                    brandColor = Color(0xFFFC3C44)
                )

                PlatformMetricRow(
                    platformName = "YouTube Music",
                    listeners = metrics.youtubeMusicListeners,
                    followers = metrics.youtubeMusicFollowers,
                    brandColor = Color(0xFFFF0000)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(18.dp))

            // NET EARNINGS (Cross-Platform Gross Revenue)
            Text(
                text = "NET EARNINGS (Cross-Platform Gross Revenue)",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Total Payout Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Total Estimated Payout",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatCurrency(metrics.estimatedGrossPayout),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.AttachMoney,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Platform Revenue Breakdown Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RevenueColumn(
                    platform = "Spotify",
                    payout = metrics.spotifyPayout
                )
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
                RevenueColumn(
                    platform = "iTunes",
                    payout = metrics.itunesPayout
                )
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
                RevenueColumn(
                    platform = "YouTube Music",
                    payout = metrics.youtubeMusicPayout
                )
            }
        }
    }
}

@Composable
private fun MetricAggregateCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    accentColor: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                RoundedCornerShape(18.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold
                ),
                color = accentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PlatformMetricRow(
    platformName: String,
    listeners: Long,
    followers: Long,
    brandColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brandColor)
                )
                Text(
                    text = platformName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Listeners: ${formatMetricCompact(listeners)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Followers: ${formatMetricCompact(followers)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RevenueColumn(
    platform: String,
    payout: Double
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = platform,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = formatCurrency(payout),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun formatMetricCompact(number: Long): String {
    return when {
        number >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", number / 1_000_000_000.0)
        number >= 1_000_000L -> String.format(Locale.US, "%.1fM", number / 1_000_000.0)
        number >= 1_000L -> String.format(Locale.US, "%.0fK", number / 1_000.0)
        else -> number.toString()
    }
}

fun formatCurrency(amount: Double): String {
    return NumberFormat.getCurrencyInstance(Locale.US).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }.format(amount)
}
