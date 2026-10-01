@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.social.BlendMix
import com.theveloper.pixelplay.data.social.BlendMixGenerator
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * Registers a blend as a normal (read-only, generated) playlist and returns its id, so it opens
 * on the regular playlist page named after the friend.
 */
fun registerBlendPlaylist(blend: BlendMix): String {
    val page = Playlist(
        id = "${PlaylistViewModel.GENERATED_MIX_PREFIX}blend:${blend.friendId}:${blend.day}",
        name = blend.friendName,
        songIds = blend.songs.map { it.id },
        coverImageUri = blend.songs.firstOrNull()?.albumArtUriString
    )
    PlaylistViewModel.registerTransientPlaylist(page, blend.songs)
    return page.id
}

/**
 * "Blends" heading and a horizontal row with one daily mix per friend (you + them).
 * [placeholderFriends] shows "Building…" cards while the blends are still being worked out.
 */
@Composable
fun BlendsRow(
    blends: List<BlendMix>,
    onOpen: (BlendMix) -> Unit,
    modifier: Modifier = Modifier,
    placeholderFriends: List<com.theveloper.pixelplay.presentation.viewmodel.FriendUi> = emptyList(),
    /** Shown after the last blend (the custom blend card). */
    trailing: (@Composable () -> Unit)? = null,
) {
    val shown = blends.ifEmpty {
        placeholderFriends.map { BlendMix(it.id, it.name, it.avatarUrl, emptyList(), 0, BlendMixGenerator.today()) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
            Text("Blends", style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("You + a friend, mixed fresh every day", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { "blend:" + it.friendId }) { blend ->
                BlendCard(blend, Modifier.animateItem()) { onOpen(blend) }
            }
            if (trailing != null) item(key = "blend:custom") { trailing() }
        }
    }
}

/**
 * One blend, drawn exactly like a friend's playlist card: a filled card with the cover on top,
 * a two-line title and a song-count line, so it reads clearly on any background.
 */
@Composable
private fun BlendCard(blend: BlendMix, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val durationMs = remember(blend.songs) { blend.songs.sumOf { it.duration } }
    val songsLabel = if (blend.isBuilding) "Building\u2026"
        else listOfNotNull(if (blend.songs.size == 1) "1 song" else "${blend.songs.size} songs",
            durationMs.takeIf { it > 0 }?.let(::blendDurationLabel)).joinToString(" \u00B7 ")
    Column(
        modifier
            .width(BlendCardWidth)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = "Open blend with ${blend.friendName}", onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Blend with ${blend.friendName}, $songsLabel"
            }
            .padding(8.dp)
    ) {
        Box {
            // Cover: a square 2x2 mosaic of the mix's art (like a playlist cover), or the first art.
            val covers = remember(blend.songs) {
                blend.songs.mapNotNull { it.albumArtUriString?.takeIf { a -> a.isNotBlank() } }.distinct().take(4)
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                when {
                    covers.size >= 4 -> Column(Modifier.fillMaxSize()) {
                        covers.chunked(2).forEach { rowArt ->
                            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().weight(1f)) {
                                rowArt.forEach { art ->
                                    SmartImage(model = art, contentDescription = null,
                                        modifier = Modifier.weight(1f).fillMaxSize())
                                }
                            }
                        }
                    }
                    covers.isNotEmpty() -> SmartImage(model = covers.first(), contentDescription = null,
                        modifier = Modifier.fillMaxSize())
                    else -> Text(blend.friendName.take(1).uppercase(), style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            // Friend's picture where a friend playlist shows its platform badge, on a solid backing.
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(2.dp)
            ) {
                if (blend.avatarUrl != null) {
                    SmartImage(model = blend.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), shape = CircleShape)
                } else {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center) {
                        Text(blend.friendName.take(1).uppercase(), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(blend.friendName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(songsLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Blend", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Same width as a friend's playlist card. */
private val BlendCardWidth = 148.dp

private fun blendDurationLabel(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "$minutes min"
        minutes == 0L -> "$hours hr"
        else -> "$hours hr $minutes min"
    }
}

// ---- Custom blend ------------------------------------------------------------------------------

/** Registers the custom blend as a read-only generated playlist and returns its id. */
fun registerCustomBlendPlaylist(blend: com.theveloper.pixelplay.data.social.CustomBlend): String {
    val page = Playlist(
        id = "${PlaylistViewModel.GENERATED_MIX_PREFIX}blend:custom:${blend.friendIds.sorted().joinToString(",").hashCode()}:${blend.day}",
        name = CUSTOM_BLEND_NAME,
        songIds = blend.songs.map { it.id },
        coverImageUri = blend.songs.firstOrNull()?.albumArtUriString
    )
    PlaylistViewModel.registerTransientPlaylist(page, blend.songs)
    return page.id
}

const val CUSTOM_BLEND_NAME = "Custom Blend"

/**
 * The last card in the Blends row: one playlist mixing you with every friend you tick.
 * Same size and layout as a friend's blend; the ticked friends' pictures sit on the cover.
 */
@Composable
fun CustomBlendCard(
    blend: com.theveloper.pixelplay.data.social.CustomBlend?,
    pickedFriends: List<com.theveloper.pixelplay.presentation.viewmodel.FriendUi>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val songs = blend?.songs.orEmpty()
    val durationMs = remember(songs) { songs.sumOf { it.duration } }
    val who = when (pickedFriends.size) {
        0 -> "Pick friends to blend"
        1 -> "You + ${pickedFriends.first().name}"
        else -> "You + ${pickedFriends.size} friends"
    }
    val songsLabel = when {
        pickedFriends.isEmpty() || blend == null -> "Tap to choose"
        blend.isBuilding -> "Building\u2026"
        else -> listOfNotNull(if (songs.size == 1) "1 song" else "${songs.size} songs",
            durationMs.takeIf { it > 0 }?.let(::blendDurationLabel)).joinToString(" \u00B7 ")
    }
    Column(
        modifier
            .width(BlendCardWidth)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = "Choose friends for the custom blend", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$CUSTOM_BLEND_NAME, $who, $songsLabel" }
            .padding(8.dp)
    ) {
        Box {
            val covers = remember(songs) {
                songs.mapNotNull { it.albumArtUriString?.takeIf { a -> a.isNotBlank() } }.distinct().take(4)
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (covers.size >= 4 && pickedFriends.isNotEmpty()) {
                    Column(Modifier.fillMaxSize()) {
                        covers.chunked(2).forEach { rowArt ->
                            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().weight(1f)) {
                                rowArt.forEach { art ->
                                    SmartImage(model = art, contentDescription = null, modifier = Modifier.weight(1f).fillMaxSize())
                                }
                            }
                        }
                    }
                } else {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.GroupAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            // Ticked friends' pictures, overlapping, in the corner.
            if (pickedFriends.isNotEmpty()) {
                androidx.compose.foundation.layout.Row(
                    Modifier.align(Alignment.TopEnd).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy((-10).dp)
                ) {
                    pickedFriends.take(3).forEach { friend -> SmallAvatar(friend.name, friend.avatarUrl) }
                    if (pickedFriends.size > 3) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+${pickedFriends.size - 3}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(CUSTOM_BLEND_NAME, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(who, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(songsLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Blend", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SmallAvatar(name: String, avatarUrl: String?, size: androidx.compose.ui.unit.Dp = 28.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(2.dp)
    ) {
        if (avatarUrl != null) {
            SmartImage(model = avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), shape = CircleShape)
        } else {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

/**
 * Pick who is in the custom blend. You are always in it; tick any friends. The mix (up to
 * [com.theveloper.pixelplay.data.social.BlendMixGenerator.GROUP_TARGET_SONGS] songs) updates as
 * you tick, and the choice is remembered.
 */
@Composable
fun CustomBlendSheet(
    friends: List<com.theveloper.pixelplay.presentation.viewmodel.FriendUi>,
    selectedIds: Set<String>,
    songCounts: Map<String, Int>,
    blend: com.theveloper.pixelplay.data.social.CustomBlend?,
    onToggle: (String) -> Unit,
    onSetAll: (Set<String>) -> Unit,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val songs = blend?.songs.orEmpty()
    val ready = selectedIds.isNotEmpty() && songs.isNotEmpty()
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            val allIds = friends.mapTo(HashSet()) { it.id }
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
            ) {
                Text(CUSTOM_BLEND_NAME, style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() })
                androidx.compose.material3.TextButton(
                    onClick = { onSetAll(if (selectedIds.containsAll(allIds)) emptySet() else allIds) },
                    enabled = friends.isNotEmpty()
                ) { Text(if (friends.isNotEmpty() && selectedIds.containsAll(allIds)) "Clear" else "Select all") }
            }
            Spacer(Modifier.height(8.dp))
            // Open / Play sit right under the title.
            androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = onOpen, enabled = ready, modifier = Modifier.weight(1f)) {
                    Text("Open playlist")
                }
                androidx.compose.material3.Button(onClick = onPlay, enabled = ready, modifier = Modifier.weight(1f)) {
                    Text("Play")
                }
            }
            Spacer(Modifier.height(12.dp))
            Spacer(Modifier.height(4.dp))
            // You: always in the blend.
            BlendMemberRow(name = "You", avatarUrl = null, detail = "Always in your blend", checked = true, enabled = false, onToggle = {})
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                items(friends, key = { it.id }) { friend ->
                    val known = songCounts[friend.id] ?: 0
                    BlendMemberRow(
                        name = friend.name,
                        avatarUrl = friend.avatarUrl,
                        detail = when {
                            known == 0 -> "No listening yet"
                            known < com.theveloper.pixelplay.data.social.BlendMixGenerator.MIN_FRIEND_SONGS -> "A few songs so far"
                            else -> "$known songs to mix from"
                        },
                        checked = friend.id in selectedIds,
                        enabled = true,
                        onToggle = { onToggle(friend.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BlendMemberRow(name: String, avatarUrl: String?, detail: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (enabled) Modifier.toggleable(
                    value = checked,
                    role = androidx.compose.ui.semantics.Role.Checkbox,
                    onValueChange = { onToggle() }
                ) else Modifier
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallAvatar(name, avatarUrl, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // The whole row toggles; the checkbox only shows the state.
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
