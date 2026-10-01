package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.DiscoveryArtist
import com.theveloper.pixelplay.presentation.viewmodel.DiscoverySong
import com.theveloper.pixelplay.presentation.viewmodel.FriendsViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens

/**
 * Home: "New from friends". Your friends' last 7 days next to your own listening, showing only
 * what's new to you: artists you've never played, then songs you've never played. Reads the
 * saved friend history only (it doesn't poll). Hidden when there's nothing new.
 */
@Composable
fun FriendsDiscoverySection(
    currentSongId: String?,
    onPlaySongs: (songs: List<Song>, start: Song) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val discovery by viewModel.discovery.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = !discovery.isEmpty,
        enter = expandVertically(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)) +
            fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
        exit = shrinkVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)) +
            fadeOut(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)),
    ) {
        val placement = tween<IntOffset>(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized)
        val fadeInSpec = tween<Float>(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)
        val fadeOutSpec = tween<Float>(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)
        // Padding lives inside, so a hidden section leaves no gap.
        Column(modifier.fillMaxWidth()) {
            Text(
                "New from friends",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() }
            )
            Text(
                "Artists and songs your friends played this week that you haven't",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            if (discovery.artists.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(discovery.artists, key = { "artist:" + it.name.lowercase() }) { artist ->
                        DiscoveryArtistCard(artist, onClick = { onOpenArtist(artist.name) },
                            modifier = Modifier.animateItem(fadeInSpec, placement, fadeOutSpec))
                    }
                }
            }
            if (discovery.songs.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(discovery.songs, key = { "song:" + it.song.id }) { item ->
                        DiscoverySongCard(
                            item = item,
                            isCurrent = item.song.id == currentSongId,
                            onClick = {
                                val songs = viewModel.prepareDiscoverySongs()
                                val start = songs.firstOrNull { it.id == item.song.id } ?: item.song
                                onPlaySongs(songs.ifEmpty { listOf(item.song) }, start)
                            },
                            modifier = Modifier.animateItem(fadeInSpec, placement, fadeOutSpec)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryArtistCard(artist: DiscoveryArtist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val friendsLabel = when (artist.friends.size) {
        1 -> artist.friends.first().name
        else -> "${artist.friends.size} friends"
    }
    Column(
        modifier
            .width(ArtistCardSize)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${artist.name}, played by $friendsLabel" }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            SmartImage(model = artist.coverUrl, contentDescription = null,
                modifier = Modifier.size(ArtistCardSize - 8.dp), shape = CircleShape)
            FriendFaces(artist.friends.map { it.avatarUrl to it.name }, Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.height(6.dp))
        Text(artist.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(friendsLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DiscoverySongCard(item: DiscoverySong, isCurrent: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(SongCardWidth)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${item.track.title} by ${item.track.artist}, played by ${item.friend.name}"
            }
            .padding(8.dp)
    ) {
        Box {
            SmartImage(model = item.track.coverUrl ?: item.song.albumArtUriString, contentDescription = null,
                modifier = Modifier.size(SongCardWidth - 16.dp), shape = MaterialTheme.shapes.medium)
            FriendFaces(listOf(item.friend.avatarUrl to item.friend.name), Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(item.track.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.track.artist, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Up to three friends' pictures, overlapping, on a surface ring so they read on any cover. */
@Composable
private fun FriendFaces(faces: List<Pair<String?, String>>, modifier: Modifier = Modifier) {
    val shown = faces.take(3)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
        shown.forEach { (avatar, name) ->
            Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(2.dp)) {
                if (avatar != null) {
                    SmartImage(model = avatar, contentDescription = null, modifier = Modifier.size(22.dp), shape = CircleShape)
                } else {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center) {
                        Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}

private val ArtistCardSize = 104.dp
private val SongCardWidth = 136.dp
