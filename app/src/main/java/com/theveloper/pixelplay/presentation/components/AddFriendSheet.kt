@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.FriendsViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.delay

/**
 * Add a friend: a half-height sheet with one centred search field that looks people up on
 * Spotify as you type (300 ms after the last key), pages in more as you scroll, and lets you
 * Follow / Unfollow on the spot. Following someone on Spotify makes them a friend here. An empty
 * field shows suggestions (people who follow you, friends of friends). "Add by playlist link"
 * keeps the old way of adding a friend through one of their public playlists.
 */
@Composable
internal fun AddFriendSheet(
    viewModel: FriendsViewModel,
    onAddByLink: () -> Unit,
    onDismiss: () -> Unit,
    /** Tapping a person opens them (a friend's week, or anyone's profile). Null = cards not tappable. */
    onOpenPerson: ((com.theveloper.pixelplay.data.social.SocialPerson) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val state by viewModel.userSearch.collectAsStateWithLifecycle()
    val myFollowing by viewModel.myFollowingIds.collectAsStateWithLifecycle()
    val mutual by viewModel.mutualFriendIds.collectAsStateWithLifecycle()
    val confirmUnfollow = rememberUnfollowConfirmation(viewModel, mutual)
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    val canSearch = viewModel.canSearchPeople
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val circleOf by viewModel.addFriendFilter.collectAsStateWithLifecycle()
    // Spotify friends you can browse the circle of (pinned first, then name).
    val circleFriends = remember(friends) {
        friends.filter { viewModel.supportsSocial(it.id) }
            .sortedWith(compareByDescending<com.theveloper.pixelplay.presentation.viewmodel.FriendUi> { it.pinned }.thenBy { it.name.lowercase() })
    }
    // The sheet always opens on Everyone.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { viewModel.setAddFriendFilter(null) } }

    // Debounced: a search goes out 300 ms after typing stops (blank = suggestions). A friend's
    // circle is filtered locally instead, so it doesn't search Spotify.
    LaunchedEffect(query, canSearch, circleOf) {
        if (!canSearch || circleOf != null) return@LaunchedEffect
        if (query.isNotBlank()) delay(SEARCH_DEBOUNCE_MS)
        viewModel.searchSpotifyUsers(query)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(Modifier.fillMaxWidth().fillMaxHeight().imePadding()) {
            Text(
                stringResource(R.string.add_friend_title),
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                enabled = canSearch,
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text(stringResource(R.string.add_friend_search_hint), maxLines = 1) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.add_friend_clear))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    viewModel.searchSpotifyUsers(query)
                }),
            )
            if (canSearch && circleFriends.isNotEmpty()) {
                FriendCircleChips(
                    friends = circleFriends,
                    selectedId = circleOf,
                    onSelect = viewModel::setAddFriendFilter,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val circleFriend = circleFriends.firstOrNull { it.id == circleOf }
                when {
                    canSearch && circleOf != null -> FriendCircleResults(
                        viewModel = viewModel,
                        query = query,
                        friendName = circleFriend?.name.orEmpty(),
                        myFollowing = myFollowing,
                        mutual = mutual,
                        onUnfollow = confirmUnfollow,
                        onRetry = { viewModel.setAddFriendFilter(circleOf, force = true) },
                        onOpenPerson = onOpenPerson,
                    )
                    !canSearch -> CenteredNote(
                        icon = { Icon(Icons.Rounded.PersonSearch, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        text = stringResource(R.string.add_friend_connect_spotify),
                    )
                    state.isLoading -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                        LoadingIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary)
                    }
                    state.failed && state.results.isEmpty() -> CenteredNote(
                        icon = { Icon(Icons.Rounded.PersonSearch, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        text = stringResource(R.string.add_friend_failed),
                        action = { TextButton(onClick = viewModel::retryUserSearch) { Text(stringResource(R.string.friend_people_retry)) } },
                    )
                    state.results.isEmpty() -> CenteredNote(
                        icon = { Icon(Icons.Rounded.PersonSearch, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        text = stringResource(if (state.query.isBlank()) R.string.add_friend_no_suggestions else R.string.add_friend_no_results),
                    )
                    else -> PeopleResults(
                        viewModel = viewModel,
                        showSuggestionsHeader = state.query.isBlank(),
                        myFollowing = myFollowing,
                        mutual = mutual,
                        onUnfollow = confirmUnfollow,
                        onOpenPerson = onOpenPerson,
                    )
                }
            }
            TextButton(
                onClick = onAddByLink,
                modifier = Modifier.align(Alignment.CenterHorizontally).navigationBarsPadding().padding(bottom = 8.dp)
            ) {
                Icon(Icons.Rounded.Link, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_friend_by_link))
            }
        }
    }
}

/** The results list with infinite scrolling: the next page loads within 3 rows of the end. */
@Composable
private fun PeopleResults(
    viewModel: FriendsViewModel,
    showSuggestionsHeader: Boolean,
    myFollowing: Set<String>,
    mutual: Set<String>,
    onUnfollow: (com.theveloper.pixelplay.data.social.SocialPerson) -> Unit,
    onOpenPerson: ((com.theveloper.pixelplay.data.social.SocialPerson) -> Unit)? = null,
) {
    val state by viewModel.userSearch.collectAsStateWithLifecycle()
    val me = viewModel.myUserId
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.hasMore, state.isPaging) {
        if (shouldLoadMore && !state.isPaging && state.hasMore && !state.failed) {
            viewModel.loadMoreSpotifyUsers()
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showSuggestionsHeader) {
            item(key = "suggestions_header", contentType = "header") {
                Text(
                    stringResource(R.string.add_friend_suggestions),
                    Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        items(state.results, key = { it.userId }, contentType = { "person" }) { person ->
            SocialPersonCard(
                person = person,
                isMe = person.userId == me,
                following = person.userId in myFollowing,
                mutual = person.friendId in mutual,
                onToggleFollow = { follow -> if (follow) viewModel.setPersonFollowing(person, true) else onUnfollow(person) },
                modifier = Modifier.animateItem(),
                onClick = onOpenPerson?.let { open -> { open(person) } },
            )
        }
        if (state.isPaging) {
            item(key = "paging", contentType = "footer") {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                }
            }
        } else if (state.failed) {
            item(key = "paging_retry", contentType = "footer") {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    LoadMoreRetryPill(onClick = viewModel::retryUserSearch)
                }
            }
        }
    }
}

/** "Everyone" + one chip per Spotify friend: picking a friend shows their followers + following. */
@Composable
private fun FriendCircleChips(
    friends: List<com.theveloper.pixelplay.presentation.viewmodel.FriendUi>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "everyone") {
            androidx.compose.material3.FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.add_friend_filter_all)) }
            )
        }
        items(friends, key = { it.id }) { friend ->
            val label = stringResource(R.string.add_friend_filter_cd, friend.name)
            androidx.compose.material3.FilterChip(
                selected = selectedId == friend.id,
                onClick = { onSelect(if (selectedId == friend.id) null else friend.id) },
                label = { Text(friend.name, maxLines = 1) },
                leadingIcon = { FriendAvatar(friend, size = 20.dp, showPresence = false) },
                modifier = Modifier.semantics { contentDescription = label }
            )
        }
    }
}

/** A friend's followers + following, ranked and filtered by the field (no network per key). */
@Composable
private fun FriendCircleResults(
    viewModel: FriendsViewModel,
    query: String,
    friendName: String,
    myFollowing: Set<String>,
    mutual: Set<String>,
    onUnfollow: (com.theveloper.pixelplay.data.social.SocialPerson) -> Unit,
    onRetry: () -> Unit,
    onOpenPerson: ((com.theveloper.pixelplay.data.social.SocialPerson) -> Unit)? = null,
) {
    val state by viewModel.friendCircle.collectAsStateWithLifecycle()
    when (val s = state) {
        com.theveloper.pixelplay.presentation.viewmodel.FriendCircleState.Loading ->
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                LoadingIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary)
            }
        com.theveloper.pixelplay.presentation.viewmodel.FriendCircleState.Failed -> CenteredNote(
            icon = { Icon(Icons.Rounded.PersonSearch, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            text = stringResource(R.string.add_friend_failed),
            action = { TextButton(onClick = onRetry) { Text(stringResource(R.string.friend_people_retry)) } },
        )
        is com.theveloper.pixelplay.presentation.viewmodel.FriendCircleState.Loaded -> {
            val shown = remember(s.people, query) { viewModel.rankCircle(query, s.people) }
            if (shown.isEmpty()) {
                CenteredNote(
                    icon = { Icon(Icons.Rounded.PersonSearch, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    text = stringResource(R.string.add_friend_circle_empty, friendName),
                )
            } else {
                val me = viewModel.myUserId
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(shown, key = { it.userId }, contentType = { "person" }) { person ->
                        SocialPersonCard(
                            person = person,
                            isMe = person.userId == me,
                            following = person.userId in myFollowing,
                            mutual = person.friendId in mutual,
                            onToggleFollow = { follow -> if (follow) viewModel.setPersonFollowing(person, true) else onUnfollow(person) },
                            modifier = Modifier.animateItem(),
                            onClick = onOpenPerson?.let { open -> { open(person) } },
                        )
                    }
                }
            }
        }
    }
}

/** "Couldn't load more – Retry" as an error-container pill (search and people paging). */
@Composable
internal fun LoadMoreRetryPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.heightIn(min = 40.dp)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.load_more_failed_retry), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CenteredNote(icon: @Composable () -> Unit, text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

private const val SEARCH_DEBOUNCE_MS = 300L
