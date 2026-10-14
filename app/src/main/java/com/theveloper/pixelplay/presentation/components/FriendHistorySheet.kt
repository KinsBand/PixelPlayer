@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

import com.theveloper.pixelplay.presentation.components.subcomps.rememberShuffleSpin
import com.theveloper.pixelplay.presentation.components.subcomps.shuffleSpin
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.social.FriendPresence
import com.theveloper.pixelplay.data.social.FriendSocialStats
import com.theveloper.pixelplay.data.social.FriendTrack
import com.theveloper.pixelplay.data.social.FriendWeeklyArtist
import com.theveloper.pixelplay.data.social.FriendWeeklyArtists
import com.theveloper.pixelplay.data.social.SocialPerson
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.viewmodel.FriendUi
import com.theveloper.pixelplay.presentation.viewmodel.FriendsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SongStartPhase
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the friend history sheet shows: their week, or their followers / following. */
internal enum class HistorySheetMode { HISTORY, FOLLOWERS, FOLLOWING }

/** Which of someone's lists an [SheetStackEntry.AccountPeople] entry shows. */
internal enum class RelationType { FOLLOWERS, FOLLOWING }

/**
 * One step of the history sheet's own back stack: the friend's week at the bottom, then any
 * followers / following lists and profiles opened from there. [userId] is a friend-style id
 * ("SPOTIFY:<id>") so the root friend and the same person reached through a list match.
 */
internal sealed interface SheetStackEntry {
    val userId: String
    val name: String
    val avatarUrl: String?

    data class RootFriendHistory(val friend: FriendUi) : SheetStackEntry {
        override val userId: String get() = friend.id
        override val name: String get() = friend.name
        override val avatarUrl: String? get() = friend.avatarUrl
    }

    /** Someone's profile: counts and public playlists (loaded by [FriendsViewModel.userProfile]). */
    data class AccountProfile(
        override val userId: String,
        override val name: String,
        override val avatarUrl: String?,
    ) : SheetStackEntry

    data class AccountPeople(
        override val userId: String,
        override val name: String,
        override val avatarUrl: String?,
        val relation: RelationType,
    ) : SheetStackEntry
}

/**
 * What makes two entries "the same place": a person's profile (the root friend's week counts as
 * their profile) or one of their two lists. Re-opening a place already in the stack pops back to
 * it instead of pushing a loop (A → followers → B → following → A).
 */
private val SheetStackEntry.stackKey: String
    get() = when (this) {
        is SheetStackEntry.RootFriendHistory, is SheetStackEntry.AccountProfile -> "profile:$userId"
        is SheetStackEntry.AccountPeople -> "people:${relation.name}:$userId"
    }

/** Pushes [entry] onto [stack], or pops back to it when it's already there (see [stackKey]). */
/** Whose options sheet is open; [isListFriend] = in your Friends list (adds Play along). */
private data class OptionsTarget(val friendId: String, val name: String, val avatarUrl: String?, val isListFriend: Boolean)

private fun pushOrPopTo(stack: MutableList<SheetStackEntry>, entry: SheetStackEntry) {
    val existing = stack.indexOfLast { it.stackKey == entry.stackKey }
    when {
        // Tapping the open list's pill again closes that list.
        existing == stack.lastIndex && entry is SheetStackEntry.AccountPeople && stack.size > 1 -> stack.removeAt(stack.lastIndex)
        existing >= 0 -> while (stack.lastIndex > existing) stack.removeAt(stack.lastIndex)
        else -> {
            val top = stack.last()
            // Followers <-> following of the same person swaps the list rather than stacking both.
            if (entry is SheetStackEntry.AccountPeople && top is SheetStackEntry.AccountPeople && top.userId == entry.userId) {
                stack[stack.lastIndex] = entry
            } else {
                stack.add(entry)
            }
        }
    }
}

private val NoMutualIds = kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>())

/** A profile being loaded for the sheet (cached per person while the sheet is open). */
private sealed interface ProfileLoad {
    data object Loading : ProfileLoad
    data class Loaded(val profile: com.theveloper.pixelplay.presentation.viewmodel.SocialProfileUi) : ProfileLoad
    data object Failed : ProfileLoad
}

/**
 * A friend's last 7 days. Header: picture, name (+ "Friends" when you follow each other), what
 * they're doing, and "[♪] n this week · [group] followers · [search] following" (the last two open
 * those lists in the sheet). Then Shuffle · this week's artists · Follow (the buttons shrink to
 * circles as the list scrolls, giving the artists room), then every play by day.
 *
 * From a followers / following list, tapping someone opens their profile (counts and public
 * playlists) in the same sheet; their counts open their lists, and so on. A breadcrumb strip on
 * top shows the path and jumps back; Back pops one step.
 *
 * Song rows: tap plays (starting trace, then the playing bars, or the failed shake), the heart
 * likes, long press for more. Long titles scroll.
 */
@Composable
internal fun FriendHistorySheet(
    friend: FriendUi,
    history: List<FriendTrack>,
    isFollowing: Boolean,
    canPlay: (FriendTrack) -> Boolean,
    onPlay: (FriendTrack) -> Unit,
    onMore: (FriendTrack) -> Unit,
    onFollow: () -> Unit,
    onShuffle: () -> Unit,
    onDismiss: () -> Unit,
    /** PixelPlayer song id a track plays as (for the starting / failed states); null = not playable. */
    songIdFor: (FriendTrack) -> String? = { null },
    /** The song playing in PixelPlayer now. */
    currentSongId: String? = null,
    isLiked: (FriendTrack) -> Boolean = { false },
    onToggleLike: ((FriendTrack) -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null,
    /** Swipe a song row: left = Queue, right = Next, further right = Soon. Null = no swipes. */
    onSwipe: ((FriendTrack, FriendSwipe) -> Unit)? = null,
    /** Soon swipe opens the "after which song" slider (needs the player and the matched song). */
    soonPlayer: com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel? = null,
    soonSong: ((FriendTrack) -> com.theveloper.pixelplay.data.model.Song?)? = null,
    onSoonConfirm: ((FriendTrack, Int) -> Unit)? = null,
    /** Opened for someone who isn't a friend (or from a people list): show their profile, not a history. */
    asProfile: Boolean = false,
    /** Spotify follow data (counts, followers / following lists); null hides it. */
    socialViewModel: FriendsViewModel? = null,
    /** A public playlist tapped on someone's profile: (owner id, playlist). */
    onOpenAccountPlaylist: ((String, com.theveloper.pixelplay.presentation.viewmodel.FriendPlaylistUi) -> Unit)? = null,
) {
    val social = socialViewModel?.takeIf { it.supportsSocial(friend.id) }
    val stats by produceState<FriendSocialStats?>(initialValue = null, friend.id, social) {
        value = social?.socialStats(friend.id)
    }
    val backStack = remember(friend.id) { mutableStateListOf<SheetStackEntry>(SheetStackEntry.RootFriendHistory(friend)) }
    // Keep the root entry's friend current (name, presence) without resetting the stack.
    LaunchedEffect(friend) {
        if (backStack.firstOrNull() is SheetStackEntry.RootFriendHistory) backStack[0] = SheetStackEntry.RootFriendHistory(friend)
    }
    val top = backStack.last()
    val profiles = remember(friend.id) { mutableStateMapOf<String, ProfileLoad>() }
    var profileAttempt by remember(friend.id) { mutableIntStateOf(0) }
    // Load the profile of whoever is on top (not the root friend: their header has its own data).
    LaunchedEffect(top.userId, profileAttempt, social) {
        val vm = social ?: return@LaunchedEffect
        val id = top.userId
        if ((id == friend.id && !asProfile) || profiles[id] is ProfileLoad.Loaded) return@LaunchedEffect
        profiles[id] = ProfileLoad.Loading
        profiles[id] = try {
            ProfileLoad.Loaded(vm.userProfile(id, force = profileAttempt > 0))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ProfileLoad.Failed
        }
    }
    val mutualIds by (social?.mutualFriendIds ?: NoMutualIds).collectAsStateWithLifecycle()
    val myFollowing by (social?.myFollowingIds ?: NoMutualIds).collectAsStateWithLifecycle()
    val navigate: (SheetStackEntry) -> Unit = { entry -> pushOrPopTo(backStack, entry) }
    // The options sheet (follow / block / hide) and the full-screen picture.
    var optionsFor by remember(friend.id) { mutableStateOf<OptionsTarget?>(null) }
    var viewingAvatar by remember(friend.id) { mutableStateOf<Pair<String, String>?>(null) }
    optionsFor?.let { target ->
        val uid = com.theveloper.pixelplay.data.social.FriendSocialGraph.spotifyUserId(target.friendId)
        val canFollow = social != null && uid != null && social.supportsSocial(target.friendId)
        FriendOptionsSheet(
            name = target.name,
            following = if (canFollow) uid in myFollowing else null,
            onToggleFollow = {
                if (social != null && uid != null) {
                    social.setPersonFollowing(
                        com.theveloper.pixelplay.data.social.SocialPerson(uid, target.name, target.avatarUrl, -1),
                        uid !in myFollowing
                    )
                }
            },
            onBlock = {
                socialViewModel?.blockPerson(target.friendId, target.name, target.avatarUrl)
                if (target.friendId == friend.id) onDismiss() else if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            },
            onHide = {
                socialViewModel?.hidePerson(target.friendId)
                if (target.friendId == friend.id) onDismiss() else if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            },
            playingAlong = if (target.isListFriend) isFollowing else null,
            onTogglePlayAlong = onFollow,
            onDismiss = { optionsFor = null },
        )
    }
    viewingAvatar?.let { (name, url) -> AvatarViewer(name = name, imageUrl = url, onDismiss = { viewingAvatar = null }) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        // Back pops the sheet's own stack before it closes the sheet.
        BackHandler(enabled = backStack.size > 1) { backStack.removeAt(backStack.lastIndex) }
        // Full height in every mode, so the sheet stays half or fully open when switching to
        // Followers / Following (a shorter list used to shrink a half-open sheet).
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            androidx.compose.animation.AnimatedVisibility(
                visible = backStack.size > 1,
                enter = androidx.compose.animation.expandVertically(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)) +
                    fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
                exit = androidx.compose.animation.shrinkVertically(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)) +
                    fadeOut(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)),
            ) {
                SheetBreadcrumbs(
                    stack = backStack.toList(),
                    rootFriend = friend,
                    onSelect = { index -> while (backStack.lastIndex > index) backStack.removeAt(backStack.lastIndex) },
                )
            }
            // Header: the person on top. It only animates when the person changes, not when one
            // of their lists opens.
            val openList: ((String, String, String?, RelationType) -> Unit)? = if (social == null) null else { id, name, avatar, relation ->
                navigate(SheetStackEntry.AccountPeople(id, name, avatar, relation))
            }
            AnimatedContent(
                targetState = top.userId,
                transitionSpec = {
                    fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                        fadeOut(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate))
                },
                label = "friendHistoryHeader"
            ) { headerUserId ->
                val entry = backStack.lastOrNull { it.userId == headerUserId } ?: top
                val openRelation = (entry as? SheetStackEntry.AccountPeople)?.relation
                if (headerUserId == friend.id && !asProfile) {
                    val mode = when (openRelation) {
                        RelationType.FOLLOWERS -> HistorySheetMode.FOLLOWERS
                        RelationType.FOLLOWING -> HistorySheetMode.FOLLOWING
                        null -> HistorySheetMode.HISTORY
                    }
                    FriendHistoryHeader(
                        friend = friend, history = history, stats = stats, mode = mode,
                        onOpenPeople = openList?.let { open -> { followers: Boolean ->
                            open(friend.id, friend.name, friend.avatarUrl, if (followers) RelationType.FOLLOWERS else RelationType.FOLLOWING)
                        } },
                        onAvatarClick = { url -> viewingAvatar = friend.name to url },
                        // A friend in your list: always the options button.
                        trailing = { ProfileOptionsButton(friend.name, onClick = { optionsFor = OptionsTarget(friend.id, friend.name, friend.avatarUrl, isListFriend = true) }) },
                    )
                } else {
                    val spotifyUid = com.theveloper.pixelplay.data.social.FriendSocialGraph.spotifyUserId(headerUserId)
                    val followingThem = spotifyUid != null && spotifyUid in myFollowing
                    val isMe = social != null && spotifyUid != null && spotifyUid == social.myUserId
                    val shownName = (profiles[headerUserId] as? ProfileLoad.Loaded)?.profile?.name ?: entry.name
                    val shownAvatar = (profiles[headerUserId] as? ProfileLoad.Loaded)?.profile?.avatarUrl ?: entry.avatarUrl
                    AccountProfileHeader(
                        name = entry.name,
                        avatarUrl = entry.avatarUrl,
                        mutual = headerUserId in mutualIds,
                        load = profiles[headerUserId] ?: ProfileLoad.Loading,
                        openRelation = openRelation,
                        onOpenPeople = { relation -> openList?.invoke(headerUserId, entry.name, entry.avatarUrl, relation) },
                        onAvatarClick = { url -> viewingAvatar = shownName to url },
                        trailing = when {
                            isMe -> null
                            // Following them: the options button. Not following: Follow.
                            followingThem -> { { ProfileOptionsButton(shownName, onClick = { optionsFor = OptionsTarget(headerUserId, shownName, shownAvatar, isListFriend = false) }) } }
                            social != null && spotifyUid != null -> { {
                                ProfileFollowButton(onClick = {
                                    social.setPersonFollowing(com.theveloper.pixelplay.data.social.SocialPerson(spotifyUid, shownName, shownAvatar, -1), true)
                                })
                            } }
                            else -> null
                        },
                    )
                }
            }
            AnimatedContent(
                targetState = top,
                contentKey = { it.stackKey },
                modifier = Modifier.fillMaxWidth().weight(1f),
                transitionSpec = {
                    val forward = backStack.indexOfFirst { it.stackKey == targetState.stackKey } >=
                        backStack.indexOfFirst { it.stackKey == initialState.stackKey }
                    (slideInHorizontally(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)) { w -> if (forward) w / 4 else -w / 4 } +
                        fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate))) togetherWith
                        (slideOutHorizontally(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)) { w -> if (forward) -w / 4 else w / 4 } +
                            fadeOut(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)))
                },
                label = "friendHistoryMode"
            ) { shown ->
                when (shown) {
                    // Someone who isn't a friend yet: their public playlists, like any profile.
                    is SheetStackEntry.RootFriendHistory -> if (asProfile) {
                        if (social != null) {
                            val entry = SheetStackEntry.AccountProfile(friend.id, friend.name, friend.avatarUrl)
                            AccountPlaylistsContent(
                                entry = entry,
                                load = profiles[friend.id] ?: ProfileLoad.Loading,
                                viewModel = social,
                                onRetry = { profiles.remove(friend.id); profileAttempt++ },
                                onOpenPlaylist = { playlist -> onOpenAccountPlaylist?.invoke(friend.id, playlist) },
                            )
                        }
                    } else HistoryContent(
                        friend = friend,
                        history = history,
                        isFollowing = isFollowing,
                        canPlay = canPlay,
                        onPlay = onPlay,
                        onMore = onMore,
                        onFollow = onFollow,
                        onShuffle = onShuffle,
                        songIdFor = songIdFor,
                        currentSongId = currentSongId,
                        isLiked = isLiked,
                        onToggleLike = onToggleLike,
                        onArtistClick = onArtistClick,
                        onSwipe = onSwipe,
                        soonPlayer = soonPlayer,
                        soonSong = soonSong,
                        onSoonConfirm = onSoonConfirm,
                    )
                    is SheetStackEntry.AccountPeople -> if (social != null) {
                        SocialPeopleContent(
                            userId = shown.userId,
                            followers = shown.relation == RelationType.FOLLOWERS,
                            viewModel = social,
                            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                            onOpenPerson = { person ->
                                navigate(SheetStackEntry.AccountProfile(person.friendId, person.name, person.avatarUrl))
                            },
                        )
                    }
                    is SheetStackEntry.AccountProfile -> if (social != null) {
                        AccountPlaylistsContent(
                            entry = shown,
                            load = profiles[shown.userId] ?: ProfileLoad.Loading,
                            viewModel = social,
                            onRetry = { profiles.remove(shown.userId); profileAttempt++ },
                            onOpenPlaylist = { playlist -> onOpenAccountPlaylist?.invoke(shown.userId, playlist) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The path through the sheet: each step's 24 dp picture (with a small followers / following mark
 * on list steps) and a chevron between them. Tapping a step goes back to it. Scrolls to the end.
 */
@Composable
private fun SheetBreadcrumbs(
    stack: List<SheetStackEntry>,
    rootFriend: FriendUi,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(stack.size) { if (stack.isNotEmpty()) listState.animateScrollToItem(stack.lastIndex) }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().height(36.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(stack, key = { index, entry -> "$index:${entry.stackKey}" }) { index, entry ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.animateItem()) {
                if (index > 0) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                        Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                val current = index == stack.lastIndex
                val relation = (entry as? SheetStackEntry.AccountPeople)?.relation
                val spoken = when (relation) {
                    RelationType.FOLLOWERS -> stringResource(R.string.friend_breadcrumb_followers, entry.name)
                    RelationType.FOLLOWING -> stringResource(R.string.friend_breadcrumb_following, entry.name)
                    null -> entry.name
                }
                Surface(
                    onClick = { if (!current) onSelect(index) },
                    shape = CircleShape,
                    color = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    modifier = Modifier
                        .heightIn(min = 28.dp)
                        .semantics {
                            contentDescription = spoken
                            if (current) this.selected = true
                        }
                ) {
                    Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (entry is SheetStackEntry.RootFriendHistory) {
                            FriendAvatar(rootFriend, size = 24.dp, showPresence = false)
                        } else {
                            PersonAvatar(entry.name, entry.avatarUrl, 24.dp)
                        }
                        if (relation != null) {
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                if (relation == RelationType.FOLLOWERS) Icons.Rounded.Group else Icons.Rounded.PersonSearch,
                                null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A circular picture, or the first letter of [name] when there's no picture. */
@Composable
internal fun PersonAvatar(name: String, avatarUrl: String?, size: androidx.compose.ui.unit.Dp) {
    if (avatarUrl != null) {
        SmartImage(model = avatarUrl, contentDescription = null, modifier = Modifier.size(size), shape = CircleShape)
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(
                name.take(1).uppercase(),
                style = if (size < 32.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Someone else's header in the sheet: 56 dp picture, name (+ Friends icon when you follow each
 * other) and two pills, followers and following, which open those lists. Grey placeholders while
 * the profile loads.
 */
@Composable
private fun AccountProfileHeader(
    name: String,
    avatarUrl: String?,
    mutual: Boolean,
    load: ProfileLoad,
    openRelation: RelationType?,
    onOpenPeople: (RelationType) -> Unit,
    /** Tapping the picture (opens it full screen); null when there's no picture. */
    onAvatarClick: ((String) -> Unit)? = null,
    /** Top-right of the card: Follow, or the options button for someone you follow. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val profile = (load as? ProfileLoad.Loaded)?.profile
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        val picture = profile?.avatarUrl ?: avatarUrl
        Box(
            Modifier
                .clip(CircleShape)
                .then(if (picture != null && onAvatarClick != null) Modifier.clickable { onAvatarClick(picture) } else Modifier)
        ) {
            PersonAvatar(profile?.name ?: name, picture, 56.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile?.name ?: name, Modifier.weight(1f, fill = false).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (mutual) {
                    Spacer(Modifier.width(8.dp))
                    MutualFriendsBadge(showLabel = false)
                }
            }
            Spacer(Modifier.height(4.dp))
            when (load) {
                ProfileLoad.Loading -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricShimmer()
                    MetricShimmer()
                }
                ProfileLoad.Failed -> Text(stringResource(R.string.friend_profile_unable), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                is ProfileLoad.Loaded -> Row(verticalAlignment = Alignment.CenterVertically) {
                    val stats = load.profile.stats
                    MetricPill(
                        icon = Icons.Rounded.Group,
                        text = if (stats.followers >= 0) stats.followers.toString() else "–",
                        spoken = if (stats.followers >= 0) pluralStringResource(R.plurals.friend_history_followers_count, stats.followers, stats.followers)
                            else stringResource(R.string.friend_history_followers_title),
                        clickLabel = stringResource(R.string.friend_history_view_followers),
                        selected = openRelation == RelationType.FOLLOWERS,
                        onClick = { onOpenPeople(RelationType.FOLLOWERS) }
                    )
                    MetricSeparator()
                    MetricPill(
                        icon = Icons.Rounded.PersonSearch,
                        text = if (stats.following >= 0) stats.following.toString() else "–",
                        spoken = if (stats.following >= 0) pluralStringResource(R.plurals.friend_history_following_count, stats.following, stats.following)
                            else stringResource(R.string.friend_history_following_title),
                        clickLabel = stringResource(R.string.friend_history_view_following),
                        selected = openRelation == RelationType.FOLLOWING,
                        onClick = { onOpenPeople(RelationType.FOLLOWING) }
                    )
                }
            }
        }
    }
}

/** A pill-sized grey placeholder that pulses gently (still when reduced motion is on). */
@Composable
private fun MetricShimmer() {
    val reduced = rememberSystemReducedMotion()
    val alpha = if (reduced) 0.6f else {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "metricShimmer")
        val a by transition.animateFloat(
            initialValue = 0.35f, targetValue = 0.8f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                tween(MotionTokens.DurationLong2, easing = MotionTokens.Emphasized),
                androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "metricShimmerAlpha"
        )
        a
    }
    Box(
        Modifier.size(width = 52.dp, height = 24.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha))
    )
}

/** Below someone's profile header: "Playlists" and their public playlists in a row. */
@Composable
private fun AccountPlaylistsContent(
    entry: SheetStackEntry.AccountProfile,
    load: ProfileLoad,
    viewModel: FriendsViewModel,
    onRetry: () -> Unit,
    onOpenPlaylist: (com.theveloper.pixelplay.presentation.viewmodel.FriendPlaylistUi) -> Unit,
) {
    val pinnedKeys by viewModel.pinnedPlaylistKeys.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Text(
            stringResource(R.string.friend_profile_playlists),
            Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp).semantics { heading() },
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
            fontWeight = FontWeight.SemiBold
        )
        when (load) {
            ProfileLoad.Loading -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary)
            }
            ProfileLoad.Failed -> Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.friend_profile_unable), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.friend_people_retry)) }
            }
            is ProfileLoad.Loaded -> {
                val profile = load.profile
                if (profile.playlists.isEmpty()) {
                    Text(stringResource(R.string.friend_profile_no_playlists), Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        items(profile.playlists, key = { it.source + ":" + it.remoteId }) { playlist ->
                            val pinned = (playlist.source + ":" + playlist.remoteId) in pinnedKeys ||
                                (playlist.playlistId != null && playlist.playlistId in pinnedKeys)
                            FriendPlaylistCard(
                                playlist = playlist,
                                friendName = profile.name,
                                friendPlatformName = profile.name,
                                pinned = pinned,
                                onClick = { onOpenPlaylist(playlist) },
                                onTogglePin = { viewModel.setAccountPlaylistPinned(entry.userId, profile.name, playlist, !pinned) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** Picture, name (+ Friends), what they're doing, and "[♪] n this week · followers · following". */
@Composable
private fun FriendHistoryHeader(
    friend: FriendUi,
    history: List<FriendTrack>,
    stats: FriendSocialStats?,
    mode: HistorySheetMode,
    onOpenPeople: ((followers: Boolean) -> Unit)?,
    /** Tapping the picture (opens it full screen); null when there's no picture. */
    onAvatarClick: ((String) -> Unit)? = null,
    /** Top-right of the card: the options button. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val live = friend.presence == FriendPresence.LISTENING_NOW
    val newest = remember(history) { history.maxByOrNull { it.playedAt } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        val picture = friend.avatarUrl
        Box(
            Modifier
                .clip(CircleShape)
                .then(if (picture != null && onAvatarClick != null) Modifier.clickable { onAvatarClick(picture) } else Modifier)
        ) {
            FriendAvatar(friend, size = 52.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(friend.name, Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (friend.isMutual) {
                    Spacer(Modifier.width(8.dp))
                    MutualFriendsBadge()
                }
            }
            val status = when {
                live && friend.track != null -> stringResource(R.string.friend_history_listening_to, friend.track.title)
                newest != null -> stringResource(R.string.friend_history_last_played, agoLabel(newest.playedAt))
                else -> stringResource(R.string.friend_history_no_plays)
            }
            Text(status, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (live) FriendsLiveGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal)
            Spacer(Modifier.height(2.dp))
            HistoryMetricRow(playCount = history.size, stats = stats, mode = mode, onOpenPeople = onOpenPeople)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.align(Alignment.Top)) { trailing() }
        }
    }
}

@Composable
private fun HistoryContent(
    friend: FriendUi,
    history: List<FriendTrack>,
    isFollowing: Boolean,
    canPlay: (FriendTrack) -> Boolean,
    onPlay: (FriendTrack) -> Unit,
    onMore: (FriendTrack) -> Unit,
    onFollow: () -> Unit,
    onShuffle: () -> Unit,
    songIdFor: (FriendTrack) -> String?,
    currentSongId: String?,
    isLiked: (FriendTrack) -> Boolean,
    onToggleLike: ((FriendTrack) -> Unit)?,
    onArtistClick: ((String) -> Unit)?,
    onSwipe: ((FriendTrack, FriendSwipe) -> Unit)? = null,
    soonPlayer: com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel? = null,
    soonSong: ((FriendTrack) -> com.theveloper.pixelplay.data.model.Song?)? = null,
    onSoonConfirm: ((FriendTrack, Int) -> Unit)? = null,
) {
    val zone = remember { ZoneId.systemDefault() }
    val days = remember(history) {
        history.sortedByDescending { it.playedAt }.groupBy { Instant.ofEpochMilli(it.playedAt).atZone(zone).toLocalDate() }
    }
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val live = friend.presence == FriendPresence.LISTENING_NOW
    val newest = remember(history) { history.maxByOrNull { it.playedAt } }
    val playableCount = remember(history) { history.count(canPlay) }
    // Plays of each song this week (one row = one real play); 2+ shows "🔥 n" on its rows.
    val playCounts = remember(history) { com.theveloper.pixelplay.data.social.FriendPlayMerge.playCounts(history) }
    val topArtists = remember(history) { FriendWeeklyArtists.from(history, limit = 5) }
    val topSongs = remember(history) { com.theveloper.pixelplay.data.social.FriendWeeklyTop.topSongs(history) }
    // History (default, first) or Top this week, per friend.
    var showTop by rememberSaveable(friend.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val topListState = rememberLazyListState()
    // Every song row can be swiped (Queue / Next / Soon) when it's playable here. Soon opens the
    // "after which song" slider under the row (same as Search and the song / playlist options).
    var soonOpenFor by rememberSaveable(friend.id) { mutableStateOf<String?>(null) }
    val swipeRow: @Composable (FriendTrack, @Composable () -> Unit) -> Unit = { track, row ->
        if (onSwipe != null) {
            val rowKey = "${track.playedAt}:${track.key}"
            Column {
                SwipeToQueueRow(
                    enabled = canPlay(track),
                    onPlayNext = { onSwipe(track, FriendSwipe.NEXT) },
                    onAddToQueue = { onSwipe(track, FriendSwipe.QUEUE) },
                    onSoon = if (soonSong != null && soonPlayer != null) ({ soonOpenFor = rowKey }) else ({ onSwipe(track, FriendSwipe.SOON) }),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    // Opaque card in the sheet's colour: the Next / Soon / Queue labels sit behind
                    // the row and must not show through it while it slides.
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BottomSheetDefaults.ContainerColor)
                    ) { row() }
                }
                if (soonSong != null && soonPlayer != null) {
                    InlineSoonPanel(
                        visible = soonOpenFor == rowKey,
                        song = { soonSong(track) ?: error("Soon opened for a song that can't play") },
                        playerViewModel = soonPlayer,
                        onClose = { soonOpenFor = null },
                        onConfirm = { k ->
                            soonOpenFor = null
                            onSoonConfirm?.invoke(track, k)
                        }
                    )
                }
            }
        } else row()
    }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            HistoryActionRow(
                // The picture row is replaced by Top this week below.
                artists = emptyList(),
                isFollowing = isFollowing,
                shuffleEnabled = playableCount > 0,
                onShuffle = onShuffle,
                onFollow = onFollow,
                onArtistClick = onArtistClick,
            )
            androidx.compose.animation.AnimatedVisibility(isFollowing) {
                Text(stringResource(R.string.friend_history_follow_hint, friend.name),
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            if (history.isNotEmpty()) {
                // History first (index 0), Top this week on the right.
                ConnectedTabGroup(
                    labels = listOf(
                        stringResource(R.string.friend_sheet_tab_history),
                        stringResource(R.string.friend_sheet_tab_top)
                    ),
                    selectedIndex = if (showTop) 1 else 0,
                    onSelect = { showTop = it == 1 }
                )
                Spacer(Modifier.height(4.dp))
            }
        }
        if (history.isEmpty()) {
            Text(stringResource(R.string.friend_history_empty, friend.name),
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
            return@Column
        }
        val reduced = rememberSystemReducedMotion()
        val nowLabel = stringResource(R.string.friend_history_now)
        AnimatedContent(
            targetState = showTop,
            transitionSpec = {
                if (reduced) {
                    androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                } else {
                    fadeIn(tween(MotionTokens.DurationShort4, delayMillis = MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                        fadeOut(tween(MotionTokens.DurationShort2, easing = MotionTokens.EmphasizedAccelerate))
                }
            },
            label = "friendSheetTop"
        ) { top ->
        if (top) {
            FriendTopThisWeek(
                artists = topArtists,
                songs = topSongs,
                listState = topListState,
                onArtistClick = onArtistClick,
                row = { song, rank ->
                    swipeRow(song.track) {
                        HistorySongRow(
                            track = song.track,
                            isFriendNow = false,
                            playable = canPlay(song.track),
                            songId = songIdFor(song.track),
                            currentSongId = currentSongId,
                            timeLabel = "♪ " + stringResource(R.string.friend_history_this_week, song.plays),
                            timesThisWeek = 1,
                            liked = isLiked(song.track),
                            onPlay = { onPlay(song.track) },
                            onMore = { onMore(song.track) },
                            onToggleLike = onToggleLike?.let { { it(song.track) } },
                            marquee = !reduced,
                            rank = rank,
                            outerPadding = onSwipe == null,
                        )
                    }
                }
            )
        } else
        LazyColumn(Modifier.fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            days.forEach { (day, tracks) ->
                stickyHeader(key = "day:$day") {
                    Surface(color = BottomSheetDefaults.ContainerColor, modifier = Modifier.fillMaxWidth()) {
                        Text(dayLabel(day), Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(tracks, key = { "${it.playedAt}:${it.key}" }) { track ->
                    val time = if (live && track == newest) nowLabel
                        else Instant.ofEpochMilli(track.playedAt).atZone(zone).toLocalTime().format(timeFormat)
                    Box(Modifier.animateItem()) {
                        swipeRow(track) {
                            HistorySongRow(
                                track = track,
                                isFriendNow = live && track == newest,
                                playable = canPlay(track),
                                songId = songIdFor(track),
                                currentSongId = currentSongId,
                                timeLabel = time,
                                timesThisWeek = playCounts[track.key] ?: 1,
                                liked = isLiked(track),
                                onPlay = { onPlay(track) },
                                onMore = { onMore(track) },
                                onToggleLike = onToggleLike?.let { { it(track) } },
                                marquee = !reduced,
                                outerPadding = onSwipe == null,
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

/** What a swipe on a friend's song does. */
enum class FriendSwipe { NEXT, SOON, QUEUE }

/**
 * Top this week: the friend's top 5 artists (ranked card, #1 tinted) and their top songs (ranked
 * rows with plays this week and a heart), from the same 7-day history as History.
 */
@Composable
private fun FriendTopThisWeek(
    artists: List<FriendWeeklyArtist>,
    songs: List<com.theveloper.pixelplay.data.social.FriendTopSong>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onArtistClick: ((String) -> Unit)?,
    row: @Composable (com.theveloper.pixelplay.data.social.FriendTopSong, Int) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        if (artists.isNotEmpty()) {
            item(key = "top_artists") {
                Surface(
                    shape = AbsoluteSmoothCornerShape(20.dp, 60),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            stringResource(R.string.friend_sheet_top_artists),
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).semantics { heading() },
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        artists.forEachIndexed { index, artist ->
                            val first = index == 0
                            val spoken = stringResource(R.string.friend_sheet_rank_cd, index + 1, artist.name, artist.plays)
                            val openLabel = stringResource(R.string.friend_history_open_artist, artist.name)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (first) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable(enabled = onArtistClick != null, onClickLabel = openLabel, role = Role.Button) { onArtistClick?.invoke(artist.name) }
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .semantics(mergeDescendants = true) { contentDescription = spoken },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val content = if (first) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                Text(
                                    "${index + 1}",
                                    Modifier.widthIn(min = 24.dp),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                                    fontWeight = FontWeight.Bold,
                                    color = content
                                )
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (artist.coverUrl != null) {
                                        SmartImage(model = artist.coverUrl, contentDescription = null, modifier = Modifier.size(40.dp), shape = CircleShape)
                                    } else {
                                        Text(artist.name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    artist.name,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                                    fontWeight = FontWeight.SemiBold,
                                    color = content,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(Icons.Rounded.MusicNote, null, Modifier.size(13.dp), tint = content.copy(alpha = 0.8f))
                                Spacer(Modifier.width(3.dp))
                                Text("${artist.plays}", style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }
        }
        if (songs.isNotEmpty()) {
            item(key = "top_songs_header") {
                Text(
                    stringResource(R.string.friend_sheet_top_songs),
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            itemsIndexed(songs, key = { _, s -> "top:" + s.track.key }) { index, song ->
                Box(Modifier.animateItem()) { row(song, index + 1) }
            }
        }
    }
}

/** "[♪] n this week · [group] followers · [search] following". Followers / following open their lists. */
@Composable
private fun HistoryMetricRow(playCount: Int, stats: FriendSocialStats?, mode: HistorySheetMode, onOpenPeople: ((Boolean) -> Unit)?) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelMedium
    val playsSpoken = pluralStringResource(R.plurals.friend_history_plays_this_week, playCount, playCount)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.semantics(mergeDescendants = true) { contentDescription = playsSpoken },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.MusicNote, null, Modifier.size(13.dp), tint = color)
            Spacer(Modifier.width(3.dp))
            Text(stringResource(R.string.friend_history_this_week, playCount), style = style, color = color, maxLines = 1)
        }
        if (stats != null && onOpenPeople != null) {
            if (stats.followers >= 0) {
                MetricSeparator()
                MetricPill(
                    icon = Icons.Rounded.Group,
                    text = stats.followers.toString(),
                    spoken = pluralStringResource(R.plurals.friend_history_followers_count, stats.followers, stats.followers),
                    clickLabel = stringResource(R.string.friend_history_view_followers),
                    selected = mode == HistorySheetMode.FOLLOWERS,
                    onClick = { onOpenPeople(true) }
                )
            }
            MetricSeparator()
            MetricPill(
                icon = Icons.Rounded.PersonSearch,
                text = if (stats.following >= 0) stats.following.toString() else "–",
                spoken = if (stats.following >= 0) pluralStringResource(R.plurals.friend_history_following_count, stats.following, stats.following)
                    else stringResource(R.string.friend_history_following_title),
                clickLabel = stringResource(R.string.friend_history_view_following),
                selected = mode == HistorySheetMode.FOLLOWING,
                onClick = { onOpenPeople(false) }
            )
        }
    }
}

@Composable
private fun MetricSeparator() {
    Text(CompactMeta.SEPARATOR, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MetricPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    spoken: String,
    clickLabel: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(CircleShape)
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
            )
            .semantics { this.selected = selected }
            .heightIn(min = 24.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(13.dp), tint = color)
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Full-width Shuffle (play-along moved to the options sheet), then this week's artists underneath. */
@Composable
private fun HistoryActionRow(
    artists: List<FriendWeeklyArtist>,
    isFollowing: Boolean,
    shuffleEnabled: Boolean,
    onShuffle: () -> Unit,
    onFollow: () -> Unit,
    onArtistClick: ((String) -> Unit)?,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val shuffleSpin = rememberShuffleSpin()
            HistoryActionButton(
                icon = Icons.Rounded.Shuffle,
                label = stringResource(R.string.friend_history_shuffle),
                compact = false,
                enabled = shuffleEnabled,
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
                onClick = { shuffleSpin.play(); onShuffle() },
                modifier = Modifier.weight(1f),
                iconModifier = Modifier.shuffleSpin(shuffleSpin),
            )
        }
        if (artists.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            WeeklyArtistsRow(artists, onArtistClick, Modifier.fillMaxWidth())
        }
    }
}

/** A pill with icon + label that shrinks to a 48 dp circle (icon only) when [compact]. */
@Composable
private fun HistoryActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    compact: Boolean,
    enabled: Boolean,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) container else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        contentColor = if (enabled) content else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier = modifier
            .height(48.dp)
            .widthIn(min = 48.dp)
            .animateContentSize(tween(MotionTokens.DurationMedium2, easing = MotionTokens.Emphasized))
            .semantics { contentDescription = label }
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 14.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, Modifier.size(20.dp).then(iconModifier))
            androidx.compose.animation.AnimatedVisibility(
                visible = !compact,
                enter = expandHorizontally(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)) +
                    fadeIn(tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate)),
                exit = shrinkHorizontally(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedAccelerate)) +
                    fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)),
            ) {
                Row {
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

/** This week's artists as 40 dp pictures; tap opens the artist, long press names them. */
@Composable
private fun WeeklyArtistsRow(artists: List<FriendWeeklyArtist>, onArtistClick: ((String) -> Unit)?, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        items(artists, key = { it.name.lowercase() }) { artist ->
            val openLabel = stringResource(R.string.friend_history_open_artist, artist.name)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(artist.name) } },
                state = rememberTooltipState()
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(enabled = onArtistClick != null, onClickLabel = openLabel, role = Role.Button) { onArtistClick?.invoke(artist.name) }
                        .semantics { contentDescription = openLabel },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artist.coverUrl != null) {
                            SmartImage(model = artist.coverUrl, contentDescription = null, modifier = Modifier.size(40.dp), shape = CircleShape)
                        } else {
                            Text(artist.name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** One play: cover (with starting / playing state), scrolling title, artist · time, 🔥 n, heart. */
@Composable
private fun HistorySongRow(
    track: FriendTrack,
    isFriendNow: Boolean,
    playable: Boolean,
    songId: String?,
    currentSongId: String?,
    timeLabel: String,
    timesThisWeek: Int,
    liked: Boolean,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    onToggleLike: (() -> Unit)?,
    marquee: Boolean,
    modifier: Modifier = Modifier,
    /** Leaderboard position ("1", "2"…) shown before the cover; null = none. */
    rank: Int? = null,
    /** Its own 12 dp side inset; off when a swipe row around it already insets it. */
    outerPadding: Boolean = true,
) {
    val cardShape = RoundedCornerShape(12.dp)
    val phase = rememberSongStartPhase(songId)
    val isStarting = phase == SongStartPhase.STARTING
    val isPlayingHere = !isStarting && songId != null && songId == currentSongId
    val failed = phase == SongStartPhase.FAILED
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = if (outerPadding) 12.dp else 0.dp, vertical = 2.dp)
            .clip(cardShape)
            .songStartFeedback(songId, cardShape)
            .background(if (failed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else Color.Transparent)
            .combinedClickable(
                onClick = { if (playable) onPlay() else onMore() },
                onLongClick = onMore,
                onLongClickLabel = stringResource(R.string.friend_history_song_options),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null) {
            Text(
                "$rank",
                Modifier.widthIn(min = 24.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                color = if (rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(contentAlignment = Alignment.Center) {
            SmartImage(model = track.coverUrl, contentDescription = null, modifier = Modifier.size(56.dp), shape = cardShape)
            when {
                isStarting -> Box(
                    Modifier.size(56.dp).clip(cardShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) { SongCoverLoadingIndicator(size = 28.dp, color = MaterialTheme.colorScheme.onPrimary) }
                isPlayingHere || isFriendNow -> Box(
                    Modifier.size(56.dp).clip(cardShape).background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Playing here = yours (theme colour); otherwise the friend's live green.
                    PlayingEqIcon(
                        Modifier.size(width = 18.dp, height = 16.dp),
                        color = com.theveloper.pixelplay.presentation.components.subcomps.playingIndicatorColor(
                            if (isPlayingHere) com.theveloper.pixelplay.presentation.components.subcomps.PlayingSource.MINE
                            else com.theveloper.pixelplay.presentation.components.subcomps.PlayingSource.FRIEND,
                            onCover = true
                        ),
                        isPlaying = true,
                        phaseDurationMillis = 2400
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                color = when {
                    isPlayingHere -> MaterialTheme.colorScheme.primary
                    isFriendNow -> FriendsLiveGreen
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = if (marquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1200) else Modifier
            )
            Text(listOf(track.artist, timeLabel).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (timesThisWeek >= 2) {
            Spacer(Modifier.width(8.dp))
            val spoken = stringResource(R.string.friend_history_played_times, timesThisWeek)
            Text(
                text = "🔥 $timesThisWeek",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.semantics { contentDescription = spoken }
            )
        }
        if (onToggleLike != null) {
            IconButton(onClick = onToggleLike) {
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(if (liked) R.string.friend_history_unlike else R.string.friend_history_like, track.title),
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private sealed interface PeopleState {
    data object Loading : PeopleState
    data class Loaded(val people: List<SocialPerson>) : PeopleState
    data object Failed : PeopleState
}

/**
 * Someone's followers or following, inside the history sheet, with a back arrow to the step
 * before. Tapping a person opens their profile in the sheet.
 */
@Composable
private fun SocialPeopleContent(
    userId: String,
    followers: Boolean,
    viewModel: FriendsViewModel,
    onBack: () -> Unit,
    onOpenPerson: (SocialPerson) -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<PeopleState>(PeopleState.Loading, userId, followers, attempt) {
        value = PeopleState.Loading
        value = try {
            PeopleState.Loaded(viewModel.socialPeople(userId, followers, force = attempt > 0))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PeopleState.Failed
        }
    }
    val myFollowing by viewModel.myFollowingIds.collectAsStateWithLifecycle()
    val mutual by viewModel.mutualFriendIds.collectAsStateWithLifecycle()
    val me = viewModel.myUserId
    val title = stringResource(if (followers) R.string.friend_history_followers_title else R.string.friend_history_following_title)
    val confirmUnfollow = rememberUnfollowConfirmation(viewModel, mutual)

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Spacer(Modifier.width(4.dp))
            Text(title, Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded), fontWeight = FontWeight.SemiBold)
        }
        when (val s = state) {
            PeopleState.Loading -> Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary)
            }
            PeopleState.Failed -> Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(if (followers) R.string.friend_people_unable_followers else R.string.friend_people_unable_following),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { attempt++ }) { Text(stringResource(R.string.friend_people_retry)) }
            }
            is PeopleState.Loaded -> if (s.people.isEmpty()) {
                Text(stringResource(if (followers) R.string.friend_people_empty_followers else R.string.friend_people_empty_following),
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // Friends (you follow each other) first, then people you follow, then everyone else.
                val ordered = remember(s.people, myFollowing, mutual) {
                    s.people.sortedBy { p ->
                        when {
                            p.friendId in mutual -> 0
                            p.userId in myFollowing -> 1
                            else -> 2
                        }
                    }
                }
                LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(ordered, key = { it.userId }) { person ->
                        SocialPersonCard(
                            person = person,
                            isMe = person.userId == me,
                            following = person.userId in myFollowing,
                            mutual = person.friendId in mutual,
                            onToggleFollow = { follow ->
                                // Following is one tap; unfollowing (Following / Friends) asks first.
                                if (follow) viewModel.setPersonFollowing(person, true) else confirmUnfollow(person)
                            },
                            onClick = { onOpenPerson(person) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * The "Unfollow X?" dialog for people cards. Returns the function that asks; confirming unfollows
 * on Spotify (the button flips back if Spotify refuses).
 */
@Composable
internal fun rememberUnfollowConfirmation(viewModel: FriendsViewModel, mutual: Set<String>): (SocialPerson) -> Unit {
    var confirmUnfollow by remember { mutableStateOf<SocialPerson?>(null) }
    confirmUnfollow?.let { person ->
        AlertDialog(
            onDismissRequest = { confirmUnfollow = null },
            title = { Text(stringResource(R.string.friend_people_unfollow_title, person.name)) },
            text = {
                Text(stringResource(
                    if (person.friendId in mutual) R.string.friend_people_unfollow_friend_body else R.string.friend_people_unfollow_body,
                    person.name
                ))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setPersonFollowing(person, false)
                    confirmUnfollow = null
                }) { Text(stringResource(R.string.friend_people_unfollow)) }
            },
            dismissButton = { TextButton(onClick = { confirmUnfollow = null }) { Text(stringResource(R.string.friend_people_cancel)) } }
        )
    }
    return remember { { person: SocialPerson -> confirmUnfollow = person } }
}

/**
 * One person: 44 dp picture, name (+ the Friends icon when you follow each other), their follower
 * and following counts, and Follow / Following / Friends (always icon + text). Tapping the card
 * opens their profile when [onClick] is set.
 */
@Composable
internal fun SocialPersonCard(
    person: SocialPerson,
    isMe: Boolean,
    following: Boolean,
    mutual: Boolean,
    onToggleFollow: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val openLabel = stringResource(R.string.friend_people_open_profile, person.name)
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = openLabel, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatar(person.name, person.avatarUrl, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(person.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (mutual) {
                    Spacer(Modifier.width(6.dp))
                    MutualFriendsBadge(showLabel = false)
                }
            }
            // [group] followers · [search] following, each only when the platform said.
            val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
            val followersSpoken = if (person.followers >= 0) pluralStringResource(R.plurals.friend_people_followers, person.followers, person.followers) else null
            val followingSpoken = if (person.following >= 0) pluralStringResource(R.plurals.friend_history_following_count, person.following, person.following) else null
            if (followersSpoken != null || followingSpoken != null) {
                Row(
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = listOfNotNull(followersSpoken, followingSpoken).joinToString(", ")
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (followersSpoken != null) {
                        Icon(Icons.Rounded.Group, null, Modifier.size(12.dp), tint = metaColor)
                        Spacer(Modifier.width(3.dp))
                        Text(person.followers.toString(), style = MaterialTheme.typography.bodySmall, color = metaColor, maxLines = 1)
                    }
                    if (followersSpoken != null && followingSpoken != null) {
                        Text(CompactMeta.SEPARATOR, style = MaterialTheme.typography.bodySmall, color = metaColor)
                    }
                    if (followingSpoken != null) {
                        Icon(Icons.Rounded.PersonSearch, null, Modifier.size(12.dp), tint = metaColor)
                        Spacer(Modifier.width(3.dp))
                        Text(person.following.toString(), style = MaterialTheme.typography.bodySmall, color = metaColor, maxLines = 1)
                    }
                }
            }
        }
        if (!isMe) {
            Spacer(Modifier.width(8.dp))
            FollowStateButton(
                state = when { mutual -> FollowButtonState.FRIENDS; following -> FollowButtonState.FOLLOWING; else -> FollowButtonState.FOLLOW },
                onToggleFollow = onToggleFollow,
            )
        }
    }
}

internal enum class FollowButtonState { FOLLOW, FOLLOWING, FRIENDS }

/**
 * Follow (filled, person-add), Following (tonal green, check) or Friends (tonal primary
 * container, group). 36 dp tall, 16 dp icon, labelMedium; crossfades between states.
 */
@Composable
internal fun FollowStateButton(state: FollowButtonState, onToggleFollow: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)) togetherWith
                fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
        },
        label = "personFollow"
    ) { shown ->
        val padding = PaddingValues(horizontal = 12.dp)
        val content: @Composable androidx.compose.foundation.layout.RowScope.(androidx.compose.ui.graphics.vector.ImageVector, Int) -> Unit = { icon, label ->
            Icon(icon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        when (shown) {
            FollowButtonState.FOLLOW -> Button(
                onClick = { onToggleFollow(true) },
                modifier = Modifier.height(36.dp),
                contentPadding = padding
            ) { content(this, Icons.Rounded.PersonAdd, R.string.friend_people_follow) }
            FollowButtonState.FOLLOWING -> FilledTonalButton(
                onClick = { onToggleFollow(false) },
                modifier = Modifier.height(36.dp),
                contentPadding = padding,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = FriendsLiveGreen.copy(alpha = 0.18f), contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) { content(this, Icons.Rounded.Check, R.string.friend_people_following) }
            FollowButtonState.FRIENDS -> FilledTonalButton(
                onClick = { onToggleFollow(false) },
                modifier = Modifier.height(36.dp),
                contentPadding = padding,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) { content(this, Icons.Rounded.Group, R.string.friend_people_friends) }
        }
    }
}
