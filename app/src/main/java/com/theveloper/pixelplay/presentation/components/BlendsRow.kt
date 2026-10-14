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
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.liveRegion
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlinx.coroutines.launch
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
        id = blendPlaylistId(blend),
        name = blend.friendName,
        songIds = blend.songs.map { it.id },
        coverImageUri = blend.songs.firstOrNull()?.albumArtUriString
    )
    PlaylistViewModel.registerTransientPlaylist(page, blend.songs)
    return page.id
}

/** Page id of a friend blend (also what the player gets as playlistId, for the playing badge). */
fun blendPlaylistId(blend: BlendMix): String =
    "${PlaylistViewModel.GENERATED_MIX_PREFIX}$BLEND_ID_PART${blend.friendId}:${blend.day}"

/** Page id of the custom blend for its current members and day. */
fun customBlendPlaylistId(blend: com.theveloper.pixelplay.data.social.CustomBlend): String =
    "${PlaylistViewModel.GENERATED_MIX_PREFIX}${BLEND_ID_PART}custom:${blend.friendIds.sorted().joinToString(",").hashCode()}:${blend.day}"

private const val BLEND_ID_PART = "blend:"
private const val FRIENDS_MIX_ID_PART = "friends_mix:"

/** Page id of today's Friends Mix ([day] from [BlendMixGenerator.today]). */
fun friendsMixPlaylistId(day: Int): String = "${PlaylistViewModel.GENERATED_MIX_PREFIX}$FRIENDS_MIX_ID_PART$day"

/**
 * Registers Friends Mix (every song friends played this week) as a read-only generated playlist
 * named [com.theveloper.pixelplay.data.social.FRIENDS_MIX_QUEUE_NAME], so it opens on the playlist
 * page with the same sticky filters as a blend. Playing from that page keeps the Friends Mix queue
 * name, so the Friends tab still shows it as playing.
 */
fun registerFriendsMixPlaylist(
    songs: List<com.theveloper.pixelplay.data.model.Song>,
    day: Int = BlendMixGenerator.today(),
): String {
    val page = Playlist(
        id = friendsMixPlaylistId(day),
        name = com.theveloper.pixelplay.data.social.FRIENDS_MIX_QUEUE_NAME,
        songIds = songs.map { it.id },
        coverImageUri = songs.firstOrNull()?.albumArtUriString
    )
    PlaylistViewModel.registerTransientPlaylist(page, songs)
    return page.id
}

/** What a blend page id points at: one friend's blend, the custom (multi-friend) blend, or Friends Mix. */
sealed interface BlendPageId {
    data class Friend(val friendId: String) : BlendPageId
    data object Custom : BlendPageId
    /** Friends Mix: friends' week, filterable by friend like a blend (no "You" side). */
    data object FriendsMix : BlendPageId
}

/** Reads a playlist id made by [blendPlaylistId] / [customBlendPlaylistId] / [friendsMixPlaylistId]; null for anything else. */
fun parseBlendPageId(playlistId: String?): BlendPageId? {
    val generated = playlistId?.removePrefix(PlaylistViewModel.GENERATED_MIX_PREFIX)?.takeIf { it != playlistId } ?: return null
    if (generated.startsWith(FRIENDS_MIX_ID_PART)) return BlendPageId.FriendsMix
    val rest = generated.takeIf { it.startsWith(BLEND_ID_PART) }?.removePrefix(BLEND_ID_PART) ?: return null
    if (rest.startsWith("custom:")) return BlendPageId.Custom
    // Friend ids can contain ':' ("SPOTIFY:user"), so only the trailing day is cut off.
    val friendId = rest.substringBeforeLast(':', missingDelimiterValue = "")
    return friendId.takeIf { it.isNotBlank() }?.let { BlendPageId.Friend(it) }
}

/**
 * Title for a custom blend from its friends' names: one friend = their name; more = each
 * friend's initial ("J · M · K"), at most [CUSTOM_BLEND_MAX_INITIALS] then "+n". No friends yet =
 * [CUSTOM_BLEND_NAME].
 */
fun customBlendTitleFromNames(names: List<String>): String {
    val clean = names.map { it.trim() }.filter { it.isNotEmpty() }
    return when (clean.size) {
        0 -> CUSTOM_BLEND_NAME
        1 -> clean.first()
        else -> {
            val initials = clean.take(CUSTOM_BLEND_MAX_INITIALS).joinToString(" · ") { name ->
                (name.firstOrNull { it.isLetterOrDigit() } ?: name.first()).uppercaseChar().toString()
            }
            if (clean.size > CUSTOM_BLEND_MAX_INITIALS) "$initials +${clean.size - CUSTOM_BLEND_MAX_INITIALS}" else initials
        }
    }
}

const val CUSTOM_BLEND_MAX_INITIALS = 5

/** [customBlendTitleFromNames] for [blend]'s friends, in the order they appear in [friends]. */
fun customBlendTitle(
    blend: com.theveloper.pixelplay.data.social.CustomBlend?,
    friends: List<com.theveloper.pixelplay.presentation.viewmodel.FriendUi>,
): String {
    val ids = blend?.friendIds?.toHashSet() ?: return CUSTOM_BLEND_NAME
    return customBlendTitleFromNames(friends.filter { it.id in ids }.map { it.name })
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
            durationMs.takeIf { it > 0 }?.let(CompactMeta::duration)).joinToString(" \u00B7 ")
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
                // Still building: the whole cover becomes the building animation, then crossfades away.
                androidx.compose.animation.AnimatedVisibility(
                    visible = blend.isBuilding,
                    enter = fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
                    exit = fadeOut(tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    BlendBuildingCover(listOf(blend.friendName to blend.avatarUrl), Modifier.fillMaxSize())
                }
            }
            // Friend's picture, bottom-centre so the cover art stays clear.
            if (!blend.isBuilding) {
                BlendAvatarRow(
                    members = listOf(blend.friendName to blend.avatarUrl),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                )
            }
            PlaylistPlayingBadge(
                visible = rememberIsPlaylistPlaying(blendPlaylistId(blend)),
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                ringColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(blend.friendName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        if (blend.isBuilding) {
            Text(songsLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            CompactCountBadge(count = blend.songs.size, trailing = durationMs.takeIf { it > 0 }?.let(CompactMeta::duration))
        }
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

/** Registers the custom blend as a read-only generated playlist (named [title]) and returns its id. */
fun registerCustomBlendPlaylist(blend: com.theveloper.pixelplay.data.social.CustomBlend, title: String = CUSTOM_BLEND_NAME): String {
    val page = Playlist(
        id = customBlendPlaylistId(blend),
        name = title,
        songIds = blend.songs.map { it.id },
        coverImageUri = blend.songs.firstOrNull()?.albumArtUriString
    )
    PlaylistViewModel.registerTransientPlaylist(page, blend.songs)
    return page.id
}

const val CUSTOM_BLEND_NAME = "Custom Blend"

/**
 * The last card in the Blends row: one playlist mixing you with every friend you tick.
 * Same size and layout as a friend's blend. Titled with the friends' initials; their pictures sit
 * bottom-centre on the cover (tap them to change who's in it). While [building], the whole cover
 * plays the building animation, then crossfades into the finished mosaic.
 */
@Composable
fun CustomBlendCard(
    blend: com.theveloper.pixelplay.data.social.CustomBlend?,
    pickedFriends: List<com.theveloper.pixelplay.presentation.viewmodel.FriendUi>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Forces the building look (e.g. right after "Build blend", until the mix is ready). */
    building: Boolean = false,
    /** Tapping the friends' pictures (opens the friend picker). */
    onAvatarsClick: (() -> Unit)? = null,
) {
    val songs = blend?.songs.orEmpty()
    val durationMs = remember(songs) { songs.sumOf { it.duration } }
    val isBuilding = building || (pickedFriends.isNotEmpty() && blend?.isBuilding == true)
    val title = customBlendTitleFromNames(pickedFriends.map { it.name })
    val who = when (pickedFriends.size) {
        0 -> "Pick friends to blend"
        1 -> "You + ${pickedFriends.first().name}"
        else -> "You + ${pickedFriends.size} friends"
    }
    val songsLabel = when {
        pickedFriends.isEmpty() || blend == null && !isBuilding -> "Tap to choose"
        isBuilding -> "Building…"
        else -> listOfNotNull(if (songs.size == 1) "1 song" else "${songs.size} songs",
            durationMs.takeIf { it > 0 }?.let(CompactMeta::duration)).joinToString(" · ")
    }
    Column(
        modifier
            .width(BlendCardWidth)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                onClickLabel = if (pickedFriends.isEmpty()) "Choose friends for the blend" else "Open blend",
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $who, $songsLabel"
                if (isBuilding) liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
            }
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
                } else if (covers.isNotEmpty() && pickedFriends.isNotEmpty()) {
                    SmartImage(model = covers.first(), contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.GroupAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(44.dp)
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = isBuilding,
                    enter = fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
                    exit = fadeOut(tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    BlendBuildingCover(pickedFriends.map { it.name to it.avatarUrl }, Modifier.fillMaxSize())
                }
            }
            // Ticked friends' pictures, bottom-centre so the art stays visible. Tap = change friends.
            androidx.compose.animation.AnimatedVisibility(
                visible = pickedFriends.isNotEmpty() && !isBuilding,
                enter = fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)) +
                    androidx.compose.animation.slideInVertically(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)) { -it },
                exit = fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            ) {
                BlendAvatarRow(
                    members = pickedFriends.map { it.name to it.avatarUrl },
                    onClick = onAvatarsClick
                )
            }
            PlaylistPlayingBadge(
                visible = blend != null && rememberIsPlaylistPlaying(customBlendPlaylistId(blend)),
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                ringColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.animation.AnimatedContent(
            targetState = title,
            transitionSpec = {
                (fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) +
                    androidx.compose.animation.slideInVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) { it / 2 }) togetherWith
                    fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
            },
            label = "customBlendTitle"
        ) { text ->
            Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(who, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        if (pickedFriends.isEmpty() || blend == null || isBuilding) {
            Text(songsLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            CompactCountBadge(count = songs.size, trailing = durationMs.takeIf { it > 0 }?.let(CompactMeta::duration))
        }
    }
}

/**
 * Members' pictures overlapping in a row (up to three, then "+n"), on solid backings so they read
 * over any cover. [onClick] makes the whole row one button ("Choose blend friends").
 */
@Composable
fun BlendAvatarRow(
    members: List<Pair<String, String?>>,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 28.dp,
    onClick: (() -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Row(
        modifier
            .then(
                if (onClick != null) Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Choose blend friends", role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
                    .semantics { contentDescription = "Choose blend friends" }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 6.dp)
                else Modifier
            ),
        horizontalArrangement = Arrangement.spacedBy(-(size * 0.36f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        members.take(3).forEach { (name, url) -> SmallAvatar(name, url, size) }
        if (members.size > 3) {
            Box(
                Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Text("+${members.size - 3}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * Full-cover "building" animation: a soft light sweeping across a tonal container while the
 * members' pictures drift in towards the centre and back. Both values are read in draw /
 * graphicsLayer only, so nothing recomposes per frame.
 */
@Composable
fun BlendBuildingCover(members: List<Pair<String, String?>>, modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "blendBuilding")
    val sweep = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(MotionTokens.DurationLong2 * 3, easing = MotionTokens.Standard)
        ),
        label = "blendSweep"
    )
    val gather = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(MotionTokens.DurationLong2 * 2, easing = MotionTokens.Emphasized),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "blendGather"
    )
    val container = MaterialTheme.colorScheme.tertiaryContainer
    val shine = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.14f)
    Box(
        modifier.drawBehind {
            drawRect(container)
            val x = size.width * (sweep.value * 2f - 0.5f)
            drawRect(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(androidx.compose.ui.graphics.Color.Transparent, shine, androidx.compose.ui.graphics.Color.Transparent),
                    start = androidx.compose.ui.geometry.Offset(x - size.width * 0.45f, 0f),
                    end = androidx.compose.ui.geometry.Offset(x + size.width * 0.45f, size.height)
                )
            )
        },
        contentAlignment = Alignment.Center
    ) {
        val shown = members.take(4).ifEmpty { listOf("?" to null) }
        shown.forEachIndexed { index, (name, url) ->
            val angle = (2.0 * Math.PI * index / shown.size - Math.PI / 2).toFloat()
            Box(
                Modifier.graphicsLayer {
                    val radius = 30.dp.toPx() * (1f - 0.55f * gather.value) * if (shown.size == 1) 0f else 1f
                    translationX = kotlin.math.cos(angle) * radius
                    translationY = kotlin.math.sin(angle) * radius
                    val s = 0.92f + 0.08f * gather.value
                    scaleX = s
                    scaleY = s
                }
            ) { SmallAvatar(name, url, size = 34.dp) }
        }
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
 * Pick who is in the custom blend: just the friend list to tick, then "Build blend". You are
 * always in it. The choice is remembered, so next time the card opens the built blend directly
 * (change friends from the blend page's friends button or the pictures on its cover).
 */
@Composable
fun CustomBlendSheet(
    friends: List<com.theveloper.pixelplay.presentation.viewmodel.FriendUi>,
    selectedIds: Set<String>,
    songCounts: Map<String, Int>,
    onToggle: (String) -> Unit,
    onSetAll: (Set<String>) -> Unit,
    onBuild: () -> Unit,
    onDismiss: () -> Unit,
    /** "Build blend" the first time; "Rebuild blend" from an existing blend page. */
    buildLabel: String = "Build blend",
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
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
            Spacer(Modifier.height(12.dp))
            // Build: the sheet slides away first, then the card plays the building animation.
            androidx.compose.material3.Button(
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onBuild() }
                },
                enabled = selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Text(buildLabel)
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

// ---- Blend page: contributor filter ---------------------------------------------------------------

/**
 * The people who added songs to a blend, as filter chips (id to label): You first, then friends
 * in [friends] order. [contributors] maps song id -> [BlendMixGenerator.ME], a friend id or
 * [BlendMixGenerator.BOTH]. Fewer than two people = no filter row.
 */
fun blendFilterChips(
    contributors: Map<String, String>,
    friends: List<com.theveloper.pixelplay.presentation.viewmodel.FriendUi>,
    youLabel: String,
): List<Pair<String, String>> {
    if (contributors.isEmpty()) return emptyList()
    val ids = contributors.values.toHashSet()
    val hasMe = BlendMixGenerator.ME in ids || BlendMixGenerator.BOTH in ids
    val friendIds = ids - BlendMixGenerator.ME - BlendMixGenerator.BOTH
    val byId = friends.associateBy { it.id }
    val orderedFriends = friends.filter { it.id in friendIds }.map { it.id to it.name } +
        friendIds.filter { it !in byId }.map { it to "Friend" }
    return (if (hasMe) listOf(BlendMixGenerator.ME to youLabel) else emptyList()) + orderedFriends
}

/** True when a song added by [contributor] shows under the [filter] chip (shared songs show under both people). */
fun blendSongMatches(contributor: String?, filter: String): Boolean =
    contributor == filter || (contributor == BlendMixGenerator.BOTH && filter != BlendMixGenerator.BOTH)

/** True when a song added by [contributor] shows under any of the [selectedFilters] chips. */
fun blendSongMatchesAny(contributor: String?, selectedFilters: Set<String>): Boolean {
    if (selectedFilters.isEmpty()) return true
    return selectedFilters.any { filter -> blendSongMatches(contributor, filter) }
}

/**
 * The blend page's filter row, in the Your Music style: Liked, then You and each friend (whose
 * songs), then the vibe presets grouped by category. Tap a chip again to turn it off. Used as a
 * sticky header: once it reaches the top ([stuck]) it moves below the floating top bar
 * ([topInset]) and takes the surfaceContainerHigh colour.
 */
@Composable
fun BlendContributorFilterRow(
    chips: List<Pair<String, String>>,
    selectedPeople: Set<String>,
    onTogglePerson: (String) -> Unit,
    stuck: Boolean,
    topInset: androidx.compose.ui.unit.Dp,
    likedOnly: Boolean,
    onToggleLiked: () -> Unit,
    vibeId: String?,
    onSelectVibe: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by androidx.compose.animation.animateColorAsState(
        targetValue = if (stuck) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        animationSpec = tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized),
        label = "blendFilterContainer"
    )
    val inset by androidx.compose.animation.core.animateDpAsState(
        targetValue = topInset,
        animationSpec = tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized),
        label = "blendFilterInset"
    )
    val presets = com.theveloper.pixelplay.presentation.library.MusicVibeFilters.presets
    val pickedVibe = remember(vibeId) { presets.firstOrNull { it.id == vibeId } }
    val likedLabel = androidx.compose.ui.res.stringResource(com.theveloper.pixelplay.R.string.blend_filter_liked)
    LazyRow(
        modifier
            .fillMaxWidth()
            .drawBehind { drawRect(container) }
            .padding(top = inset)
            .padding(vertical = 6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "liked") {
            ExpressiveFilterChip(
                label = likedLabel,
                selected = likedOnly,
                onClick = onToggleLiked,
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        if (likedOnly) androidx.compose.material.icons.Icons.Rounded.Favorite
                        else androidx.compose.material.icons.Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.animateItem()
            )
        }
        items(chips, key = { "who:" + it.first }) { (id, label) ->
            val isSelected = id in selectedPeople
            BlendFilterChip(label = label, selected = isSelected, modifier = Modifier.animateItem()) {
                onTogglePerson(id)
            }
        }
        // The picked vibe sits first after the people; tap it again to turn it off.
        if (pickedVibe != null) {
            item(key = "vibe:" + pickedVibe.id) {
                BlendFilterChip(label = pickedVibe.label, selected = true, modifier = Modifier.animateItem()) { onSelectVibe(null) }
            }
        }
        com.theveloper.pixelplay.presentation.library.VibeCategory.values()
            .filter { it != com.theveloper.pixelplay.presentation.library.VibeCategory.CUSTOM }
            .forEach { category ->
                item(key = "divider_" + category.name) {
                    androidx.compose.material3.VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp).animateItem())
                }
                items(presets.filter { it.category == category && it.id != vibeId }, key = { "vibe:" + it.id }) { filter ->
                    BlendFilterChip(label = filter.label, selected = false, modifier = Modifier.animateItem()) { onSelectVibe(filter.id) }
                }
            }
    }
}

/** True when [song] fits the vibe [vibeId] (null = no vibe picked), the same test Your Music uses. */
fun blendSongMatchesVibe(song: com.theveloper.pixelplay.data.model.Song, vibeId: String?): Boolean {
    if (vibeId == null) return true
    val filter = com.theveloper.pixelplay.presentation.library.MusicVibeFilters.presets.firstOrNull { it.id == vibeId } ?: return true
    return com.theveloper.pixelplay.presentation.library.MusicVibeFilters.score(song, filter) >=
        com.theveloper.pixelplay.presentation.library.MusicVibeFilters.MATCH_THRESHOLD
}

/** Whose a blend song is: you, a friend (their picture), or a pick you share (sparkle). */
sealed interface BlendSongSource {
    data class Person(val name: String, val avatarUrl: String?) : BlendSongSource
    data object Shared : BlendSongSource
}

/** The small badge at the end of a blend song row: the person's picture, or a sparkle for shared picks. */
@Composable
fun BlendSongBadge(source: BlendSongSource, modifier: Modifier = Modifier) {
    val spoken = when (source) {
        is BlendSongSource.Person -> androidx.compose.ui.res.stringResource(com.theveloper.pixelplay.R.string.blend_song_from, source.name)
        BlendSongSource.Shared -> androidx.compose.ui.res.stringResource(com.theveloper.pixelplay.R.string.blend_song_shared)
    }
    Box(
        modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(
                if (source is BlendSongSource.Shared) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            )
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center
    ) {
        when (source) {
            BlendSongSource.Shared -> androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(15.dp)
            )
            is BlendSongSource.Person -> if (source.avatarUrl != null) {
                SmartImage(model = source.avatarUrl, contentDescription = null, modifier = Modifier.size(26.dp), shape = CircleShape)
            } else {
                Text(source.name.take(1).uppercase(), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.horizontalScroll(androidx.compose.foundation.rememberScrollState())

@Composable
private fun BlendFilterChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ExpressiveFilterChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier
    )
}

/** No songs from the picked person: says so, with a Clear filter button. */
@Composable
fun BlendFilterEmpty(name: String?, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (name != null) androidx.compose.ui.res.stringResource(com.theveloper.pixelplay.R.string.blend_filter_empty, name)
            else androidx.compose.ui.res.stringResource(com.theveloper.pixelplay.R.string.blend_filter_none),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.TextButton(onClick = onClear) {
            Text(androidx.compose.ui.res.stringResource(com.theveloper.pixelplay.R.string.blend_filter_clear))
        }
    }
}
