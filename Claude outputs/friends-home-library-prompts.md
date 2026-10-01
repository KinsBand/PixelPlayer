# PixelPlayer: Friends, Home & Library batch (prompts + plans)

Your list covered 8 different areas, so it's split into 10 prompts. Each one has its own plan and can be handed to an agent separately. Run them in this order, because later ones depend on earlier ones:

| # | Prompt | Your items | Depends on |
|---|---|---|---|
| P1 | Friends screen (own page) | 8, 5, 5.2, 5.4, card cleanup | none |
| P2 | Friends selection mode (bulk delete / rename) | 5.1 | P1 |
| P3 | Friend playlist card readability | 5.3 | P1 (**needs your screenshot**) |
| P4 | Friend attribution in the queue | 1 | none |
| P5 | Liking friend songs reliably | 2 | none (do before P6/P7) |
| P6 | Friends Mix | 8 (mix button) | P1, P4 |
| P7 | Pin a friend's playlist | 6 | P1 |
| P8 | Home: Playlists above Speed Dial | 3 | none |
| P9 | Home: "New from friends" section | 4 | P4 |
| P10 | Your Music button into the Library top row | 7 | none |

Build check used by every prompt:
`cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio2\jbr&& gradlew.bat assembleDebug"`

Shared motion reference (all names are from `ui/theme/MotionTokens.kt`): enter = `tween` + `MotionTokens.EmphasizedDecelerate`, exit = `tween` + `MotionTokens.EmphasizedAccelerate` at roughly 60–70% of the enter duration, in-screen state change = `MotionTokens.Emphasized`, screen push/pop = `MotionTokens.DurationMedium3`.

---

## P1: Friends screen

### Improved prompt
**Goal:** Turn the Friends dropdown in the Library Playlists tab into a card that opens a full Friends screen, with online and offline sections and a history shortcut on each avatar.

**Scope:**
- `presentation/components/PlaylistsHub.kt`: `FriendsDropdownCard`, `FriendSection`, `FriendAvatar`, `FriendTrackLabel`, `ActiveFriendsPill`, `FriendHistorySheet`
- `presentation/components/PlaylistContainer.kt`: the `friends_dropdown` item
- `presentation/navigation/Screen.kt` and `presentation/navigation/AppNavigation.kt`: new route
- New `presentation/screens/FriendsScreen.kt`
- `presentation/viewmodel/FriendsViewModel.kt`
- Delete the dead stubs `presentation/screens/SpotifyFriendsSection.kt` and `presentation/viewmodel/SpotifyFriendsViewModel.kt`. Both say they're safe to delete.

**Current problem:** The friends list expands inline inside the Playlists tab's `LazyColumn`. There's no separation between who is online and who isn't. Opening history takes three steps (tap name, expand, tap History). The card subtitle says "No one's listening right now" even when that adds nothing.

**Spec:**
- **Friends card (Library):**
  - Stays full width at its current position.
  - Tapping it navigates to `Screen.Friends`. Remove the expand arrow and the `AnimatedVisibility` body, and use a trailing chevron-right icon instead.
  - Subtitle: "Add a friend's playlist to start" when there are no friends, "N listening now" when N > 0, and nothing when nobody is live. Remove the "No one's listening right now" text.
  - Keep the "Following {name}" line whenever `following != null`.
  - Keep `ActiveFriendsPill`.
- **Friends screen:**
  - Top bar, left: a back arrow (`Icons.AutoMirrored.Rounded.ArrowBack`, content description "Back") that pops the screen.
  - Top bar, title: "Friends".
  - Top bar, right: the `live/total` ratio pill (reuse `ActiveFriendsPill`).
  - When following someone, the top bar shows "Following {name}" as a supporting line under the title. Tapping that line opens a small menu or dialog with "Stop following", which calls `stopFollowing()`.
- **Body:** one `LazyColumn`, in this order:
  1. Slot for the Friends Mix button (built in P6; leave a keyed placeholder item or skip it until P6)
  2. "Online" section header, then online friends
  3. "Offline" section header, then everyone else
  4. The "Add friend's playlist" button
  5. The "Live listening and history appear once Spotify friend activity is connected." notice when `!hasLiveSource`
- **Online vs offline:**
  - Online = `presence == LISTENING_NOW`. Offline = every other presence.
  - Inside each section, keep the existing order: has playlists, then most recent `playedAt`, then name.
  - Hide a section header when that section is empty.
- **Friend row:** as it is now: avatar, name (tap toggles playlists), and the track label.
  - Tapping the avatar opens `FriendHistorySheet` for that friend directly.
  - The History button inside the expanded row stays.
- **Hidden activity (item 5.4):** when `presence == HIDDEN` and there's no track, the track label reads "{n} playlists" ("1 playlist" when there's one). Use `friend.playlists.size`, the same `bodyMedium` / `onSurfaceVariant` style, and no italics. When n = 0, keep "Activity hidden".
- **Polling:** collect `viewModel.polling` while the Friends screen is on screen, which replaces the `expanded` condition. The Library card doesn't poll.
- **Styling:** colours from M3 roles only. The row dividers keep `outlineVariant`. Shapes come from `MaterialTheme.shapes` / app shape tokens, which replaces the card's hardcoded `RoundedCornerShape(24.dp)` in code you touch. Type uses `MaterialTheme.typography` roles.

**Motion:**
- Screen push/pop: the same NavHost transition as `Screen.YourMusic` (direction-aware slide + fade, `MotionTokens.DurationMedium3`). No new transition.
- Section membership changes (a friend comes online or goes offline): items use `Modifier.animateItem()` with stable keys (`friend.id`, `"header_online"`, `"header_offline"`), so rows move between sections instead of jumping.
- Expanding playlists in a row: keep `expandVertically() + fadeIn()` / `shrinkVertically() + fadeOut()`. Enter uses `tween(DurationMedium2, easing = EmphasizedDecelerate)` and exit uses `tween(DurationShort4, easing = EmphasizedAccelerate)`.
- History sheet: `ModalBottomSheet`, dismissed with `sheetState.hide()` then `onDismiss`.
- Interruption: a back gesture during a push reverses from its current progress. Tapping a row again mid-expand reverses from its current value.
- Reduced motion: when the animator duration scale is 0, rows appear or disappear with no movement, and the screen change uses the system default.

**Accessibility:**
- Avatar tap target is at least 48dp, with content description "Open {name}'s history".
- The ratio pill reads "{live} of {total} friends online".
- Section headers are marked `semantics { heading() }`.

**Don't:**
- Change `FriendActivityRepository` presence rules.
- Add new colours.
- Keep the dropdown as an option.
- Build the Friends Mix logic here (that's P6).

**Acceptance criteria:**
- [ ] The Library card navigates to Friends. The card has no dropdown and no "No one's listening right now".
- [ ] "Following X" shows on the card and in the Friends screen header, and Stop works from the header.
- [ ] The header shows back arrow, "Friends", and the live/total pill. Back and the system back gesture both return to Library.
- [ ] Online friends are listed above offline ones, and a friend going live moves sections with animation.
- [ ] Tapping an avatar opens that friend's history sheet.
- [ ] A hidden-activity friend with playlists shows "N playlists".
- [ ] The stub files are deleted and the build command passes.

### Plan
1. Read `PlaylistsHub.kt` (lines ~157–520), `PlaylistContainer.kt` `friends_dropdown` item, and the `Screen.YourMusic` wiring in `Screen.kt`/`AppNavigation.kt`. Done when you know which NavHost transition applies to `YourMusic`.
2. Add `object Friends : Screen("friends")` in `Screen.kt` and a `composable(Screen.Friends.route)` wrapped in `ScreenWrapper` in `AppNavigation.kt`, matching `YourMusic`. Done when navigating to the route shows a blank screen with default transitions.
3. Move the list body, sheets, dialogs, and polling out of `FriendsDropdownCard` into `FriendsScreen.kt`, keeping the helper composables in `PlaylistsHub.kt`. Make the helpers `internal` where the screen needs them. Done when the screen renders the same list the dropdown did.
4. Add the top bar: back arrow, "Friends" title, `ActiveFriendsPill`, and the Following supporting line with a Stop menu. Use M3 roles and typography roles. Done when the header matches the Spec.
5. Split the list into Online and Offline in `FriendsViewModel`. Expose `online: List<FriendUi>` and `offline: List<FriendUi>`, or a sectioned list, with the existing sort inside each. Done when a live friend appears under Online.
6. Render the sections with keyed items, heading semantics, and `Modifier.animateItem()`. Done when you can watch a friend move between sections.
7. Make `FriendAvatar` clickable (48dp target, content description) and set `historyFor = friend.id`. Done when tapping an avatar opens the sheet.
8. Update `FriendTrackLabel` for HIDDEN: "N playlists", or "Activity hidden" when N = 0. Done when a hidden friend shows the count.
9. Reduce `FriendsDropdownCard` to a nav card: subtitle rules, Following line, pill, chevron, and no polling. Swap hardcoded shapes you touch for shape tokens. Done when the card only navigates.
10. Set the expand/collapse enter/exit specs from the Motion section. Delete the two stub files. Done when the build passes.

**Verification:**
- Build command passes.
- Card → screen → back arrow, and card → screen → back gesture (including interrupting the gesture halfway).
- Following shows on the card and the header, and Stop works.
- Online/offline move animates when a friend's status changes. Test with animator scale 0x and 0.5x.
- Avatar opens history.
- Hidden friend shows "N playlists".
- Check light and dark themes, and several album-art palettes.

**Risks:**
- The polling lifecycle moves. If you forget to collect it on the new screen, statuses freeze.
- Removing `animateContentSize` from the card could change neighbouring item spacing in the Playlists tab.
- Keys must stay unique across headers and friend ids.

---

## P2: Friends selection mode (bulk delete / rename)

### Improved prompt
**Goal:** On the Friends screen, long-pressing a friend starts a selection mode where I can select several friends and delete them, or rename a single selected friend.

**Scope:**
- `FriendsScreen.kt` (from P1)
- `presentation/components/PlaylistsHub.kt`: `FriendSection`
- `presentation/viewmodel/FriendsViewModel.kt`
- `data/accounts/ConnectedLibraryRepository.kt`: `renameFriend` / `addFriendPlaylist`; locate how friend playlists and aliases are stored
- `data/social/FriendActivity.kt`: `FriendActivityRepository`

**Current problem:** Long-pressing a friend's name only renames inline, and there's no way to remove a friend.

**Spec:**
- **Entering and using selection mode:**
  - Long-pressing anywhere on a friend row enters selection mode with that friend selected. This replaces the current long-press-to-rename on the name.
  - While in selection mode, tapping a row toggles its selection. Tapping the avatar also toggles instead of opening history.
- **Top bar in selection mode:**
  - Left: close icon (exit selection).
  - Title: "{n} selected".
  - Right: "Rename" (only enabled when n == 1) and "Delete".
  - This matches the multi-select pattern already used in the Library. Locate it in `LibraryScreen.kt` (the `multiSelectionState` bar) and reuse its component and styling if it's reusable.
- **Selected row:** `secondaryContainer` background and a check badge over the avatar.
- **Rename:** an `AlertDialog` with a text field pre-filled with the current name and `platformName` as placeholder. Saving calls the existing `rename`.
- **Delete:**
  - Asks for confirmation first: "Remove {n} friends? Their saved playlists are removed from your library."
  - On confirm, it removes their saved friend playlists and hides the friend id, so the Spotify friend feed doesn't bring them back. Add a persisted `hiddenFriendIds` set in the repository that owns aliases, and have `FriendsViewModel.friends` filter it out.
  - If the deleted friend is being followed, stop following.
- **Exit:** back gesture, the close icon, or deleting all selected friends exits selection mode.

**Motion:**
- Selection top bar enter: fade + slide down, `tween(DurationShort4, easing = EmphasizedDecelerate)`. Exit: `tween(DurationShort3, easing = EmphasizedAccelerate)`. It crossfades with the normal top bar.
- Row selected background: `animateColorAsState` with `tween(DurationShort4, easing = Emphasized)`.
- Deleted rows leave via `Modifier.animateItem()`.
- Interruption: toggling rapidly retargets the colour from its current value.
- Reduced motion: instant colour and bar swap.

**Accessibility:**
- Rows expose `selected` state and `onLongClickLabel = "Select"`.
- Top bar actions are at least 48dp with labels "Rename friend" and "Remove friends".

**Don't:**
- Delete anything on the platform side.
- Remove liked songs.
- Remove the inline rename code paths other screens use. Only remove the long-press trigger.

**Acceptance criteria:**
- [ ] Long-press enters selection. Taps toggle selection. Back exits.
- [ ] Rename is enabled only with exactly 1 selected, and the new name shows everywhere.
- [ ] Deleting 2+ friends removes them and their saved playlists, and they stay gone after the next poll and after an app restart.
- [ ] Deleting the followed friend stops following.
- [ ] Build command passes.

### Plan
1. Locate friend alias storage and friend playlist storage in `ConnectedLibraryRepository`, and the Library multi-select bar in `LibraryScreen.kt`. Done when you can name both.
2. Add a persisted `hiddenFriendIds` set and `removeFriends(ids)` to the repository, which deletes saved playlists with that `friendId` and adds the ids to hidden. Done when a unit call removes them.
3. In `FriendsViewModel`, filter out hidden ids, and add `removeFriends(ids)`, which also calls `follow.stop()` when needed. Done when removed friends vanish from `friends`.
4. Add selection state (`Set<String>`, saveable) to `FriendsScreen`. Change `FriendSection` to `combinedClickable` on the whole row, with long-press = select and tap = toggle while selecting. Done when selection toggles.
5. Add the selection top bar (reusing the Library one if possible), the rename dialog, and the delete confirmation. Done when all three actions work.
6. Add the selected visuals (animated `secondaryContainer`, avatar check badge) and the Motion specs. `BackHandler` exits selection. Done when back exits selection before leaving the screen.

**Verification:**
- Build command passes.
- Every acceptance criterion checked by hand, including after an app restart.
- Animator scale 0x.
- Light and dark themes.

**Risks:**
- The Spotify feed re-adds friends unless you filter on every emission, not just once.
- `rememberSaveable` of a Set needs a `listSaver`.

---

## P3: Friend playlist card readability

### Improved prompt
**Goal:** Make every piece of text on friend playlist cards readable.

**Scope:** `presentation/components/PlaylistsHub.kt` (`FriendPlaylistCard`, `SourceBadge` usage). If the screenshot turns out to show `PlaylistDetailScreen` for a friend playlist, work there instead.

**Current problem:** Some info on friend playlist cards is unreadable. **The screenshot you mentioned wasn't attached; attach it so the prompt can say exactly which text (title, "N songs · duration", "Counting songs…", "Tap to save", or the source badge) is failing and why.** Likely causes:
- Text sitting on the cover art with no scrim.
- `labelSmall` + `onSurfaceVariant` too faint on album-art-tinted surfaces.
- Title clipped by the fixed 128dp width.

**Spec (fill in from the screenshot):**
- Text that sits on art gets an `onSurface` colour over a `surfaceContainerHighest` scrim/container.
- Text below the art uses `onSurface` for the title and `onSurfaceVariant` for metadata. Never use primary-on-tinted for small text.
- The title gets up to 2 lines. Metadata gets 1 line with ellipsis.
- Width is derived from shape/size tokens, not a hardcoded 128dp.
- Contrast is at least 4.5:1 in light, dark, and album-art palettes.

**Motion:** none.

**Accessibility:** the card announces "{title}, {n} songs, {platform}".

**Don't:** redesign the card layout.

**Acceptance criteria:**
- [ ] All text readable in light, dark, and 3 different album-art palettes.
- [ ] Build command passes.

### Plan
1. Compare the screenshot with `FriendPlaylistCard` and identify each failing element. Done when there's a list of elements with their current role and background.
2. Swap colour roles and containers per the Spec. Done when contrast passes.
3. Replace hardcoded sizes/shapes with tokens and set line limits. Done when nothing is clipped.
4. Add merged semantics. Done when TalkBack reads a single line.

**Verification:** build passes; manual check across themes and palettes.

**Risks:** `SourceBadge` is shared with other screens, so change it only through parameters.

---

## P4: Friend attribution in the queue

### Improved prompt
**Goal:** Every queue entry that got there because of a friend shows that friend's avatar and name.

**Scope:**
- `data/model/QueueEntryMetadata.kt`: add the attribution fields
- `MainActivity.kt`: the `friendFollowController.newSongs` collector, around line 265
- `presentation/components/PlaylistsHub.kt`: `onTrackAction` PLAY/NEXT/QUEUE and history Shuffle
- `presentation/viewmodel/PlayerViewModel.kt`: `playSongs`, `addSongNextToQueue`, `addSongToQueue`, which delegate to `playbackDispatchStateHolder`. Locate where Songs become MediaItems there.
- `presentation/components/QueueBottomSheet.kt`: `QueuePlaylistSongItem`, around line 2637

**Current problem:** Songs queued by following a friend, or played from their activity or history, look the same as any other song in the queue.

**Spec:**
- **Data:**
  - Add optional fields `friendId`, `friendName`, and `friendAvatarUrl` to `QueueEntryMetadata`. Write them to MediaItem extras under new `pixelplay.queue.friend*` keys in `writeTo`, and read them in `read`.
  - Thread an optional `attribution` parameter (default null) through `playSongs`, `addSongNextToQueue`, and `addSongToQueue` down to MediaItem creation.
- **Tagged sources:**
  - Follow mode (the MainActivity collector, using the session's friend)
  - Friend track actions PLAY / NEXT / QUEUE
  - History Shuffle
  - Friends Mix (P6), where each song carries the friend who played it
  - "New from friends" (P9)
- **Queue row (`QueuePlaylistSongItem`):**
  - When the entry has attribution, the supporting line starts with an avatar (circle, `MaterialTheme.typography.labelSmall` line height, so no hardcoded size) followed by the friend's name in `labelMedium` / `onSurfaceVariant`, then a separator and the artist.
  - "If it fits" means: when width is short, the friend's name ellipsizes first, then drops out, and the avatar always stays.
  - Fallback when there's no avatar URL: initial on `secondaryContainer`.
- The attribution must survive queue reorder, undo, queue snapshot restore (`PlaybackQueueSnapshot`), and app restart. Check that snapshot serialization keeps the new extras.

**Motion:** none new. Rows keep their existing `animateItem()`.

**Accessibility:** the row's content description appends "added from {friend}'s listening".

**Don't:**
- Tag songs from friend playlists. That would badge every row; see Assumptions.
- Change the queue row height.

**Acceptance criteria:**
- [ ] Following a friend: queued songs show their avatar and name.
- [ ] Play, Play next, and Queue from a friend track, plus History Shuffle, are all tagged.
- [ ] Normal songs are unchanged.
- [ ] Tags survive reorder, undo, and an app restart.
- [ ] On a narrow width, the name ellipsizes and the avatar stays.
- [ ] Build command passes.

### Plan
1. Locate MediaItem creation in `playbackDispatchStateHolder` and how `QueueEntryMetadata.attach` is applied. Done when you know the one place to pass attribution.
2. Add the attribution fields, extras keys, and read/write to `QueueEntryMetadata`. Done when round-trip read equals write.
3. Add the optional `attribution` parameter through the PlayerViewModel → dispatch holder → MediaItem path. Done when default-null calls compile unchanged.
4. Pass attribution from the MainActivity follow collector and from the PlaylistsHub PLAY/NEXT/QUEUE/Shuffle paths. Done when the extras are present on those items.
5. Render the avatar + name in `QueuePlaylistSongItem`, with the ellipsize/drop order and the fallback avatar. Done when it shows as in the Spec.
6. Confirm `PlaybackQueueSnapshot` persists the extras. Done when tags survive a restart.

**Verification:**
- Build command passes.
- Each acceptance criterion checked by hand.
- Light, dark, and album-art palettes.
- TalkBack reads the attribution.

**Risks:**
- Extras are lost if some path rebuilds MediaItems from a `Song`. Grep for `MediaItem.Builder` / `toMediaItem`.
- Adding a param to hot player APIs; keep it defaulted.

---

## P5: Liking friend songs reliably

### Improved prompt
**Goal:** Liking a song works every time for songs that came from friends: followed, queued, played from activity or history, and friend playlists.

**Scope:**
- `presentation/viewmodel/PlayerViewModel.kt`: `toggleFavorite`, `toggleFavoriteSpecificSong`, `resolveFavoriteSongId`, `isCurrentSongFavorite`
- `data/social/FriendFollowController.kt`: `toPlayableSong`
- `data/spotify/SpotifyRepository.kt`: `SpotifyTrack.toSong` builds `id = "spotify_$id"`
- `data/spotify/SpotifyToYouTubeResolver.kt`
- `data/library/LikedSongsRepository.kt`
- The `FriendTrackSheet` `isLiked` check in `PlaylistsHub.kt`

**Current problem:** Sometimes a like on a friend-sourced song doesn't stick or doesn't show as liked. The cause isn't confirmed yet. Suspects to check first:
- The song is liked under `spotify_<id>`, but once the resolver matches audio the playing item may carry a different id (e.g. `yt_…`), so the heart reads a different id than the one saved. The reverse can also happen.
- `FriendTrackSheet` checks `song.id in favoriteIds` against the raw `spotify_` id, while the player checks the resolved id.
- `saveCloudSong` may not persist a `spotify://` song, so the like exists but `LikedSongsRepository` can't join it to a song.
- A race: `toggleFavoriteSpecificSong` reads `getFavoriteSongIdsOnce()`, while the UI reads the `favoriteSongIds` StateFlow.

**Spec:**
- Reproduce it first. Log the song id at like time, the id stored, and the id the heart reads, for each friend path: follow, Play, Play next, History, friend playlist.
- Fix it with one canonical favourite id for a recording across its `spotify_`, `yt_`, and downloaded forms. Reuse existing identity helpers (`data/library/RecordingKeys.kt`, `CollectionKeys.kt`) if they already map these. Don't invent a new one.
- Every heart (full player, mini player, queue row, `FriendTrackSheet`, song info sheet) checks liked state through the same resolver.
- A like made before the audio match still shows as liked after the match, and the reverse.
- The liked song appears in Your Music with title and art.

**Motion:** unchanged. The heart keeps its existing animation.

**Accessibility:** unchanged.

**Don't:**
- Add retries or delays to mask the bug.
- Change how local-file likes work.

**Acceptance criteria:**
- [ ] 10 of 10 likes stick on each friend path (follow, Play, Play next, History, friend playlist), both before and after the audio match finishes.
- [ ] The heart state agrees across full player, queue, and friend sheet.
- [ ] Liked friend songs appear in Your Music.
- [ ] Local and YouTube likes behave as before.
- [ ] Build command passes.

### Plan
1. Add temporary debug logs at `toggleFavoriteSpecificSong`, `toggleFavorite`, `resolveFavoriteSongId`, and the resolver's id swap. Reproduce on each path. Done when the failing path and the mismatched ids are written down.
2. Check `RecordingKeys.kt` / `CollectionKeys.kt` for an existing cross-source key. Done when you know whether to reuse one or extend `resolveFavoriteSongId`.
3. Make `resolveFavoriteSongId` return the canonical id for `spotify_` / `yt_` / downloaded forms of the same recording. Done when all forms resolve equal.
4. Point `isCurrentSongFavorite`, the `FriendTrackSheet` `isLiked` check, and the queue/info-sheet hearts at the resolver. Done when every heart agrees.
5. Make sure `saveCloudSong` persists enough of a `spotify://` song for `LikedSongsRepository` to show it. Done when it appears in Your Music.
6. Remove the debug logs. Done when the build passes.

**Verification:**
- Build command passes.
- The 10× test on each path.
- Before-match and after-match likes.
- Regression checks on local and YouTube likes.

**Risks:**
- Changing favourite ids can orphan existing likes. Migrate them or keep a read-time alias.
- `isCurrentSongFavorite` runs on every song change, so keep the resolver cheap and off the main thread.

---

## P6: Friends Mix

### Improved prompt
**Goal:** A full-width "Friends Mix" button at the top of the Friends screen plays a shuffled mix of every song all friends played in the last 7 days, and the mix grows as friends keep listening.

**Scope:**
- `FriendsScreen.kt` (P1 slot)
- `presentation/viewmodel/FriendsViewModel.kt`
- `data/social/FriendActivity.kt`: `FriendActivityRepository.history`, which already holds a rolling 7-day window (`FRIEND_HISTORY_WINDOW_MS`)
- `PlayerViewModel`: `playSongs` and add-to-queue, with P4 attribution

**Spec:**
- **Button:**
  - First item in the Friends list, full width, `FilledTonalButton`-style container using `secondaryContainer` / `onSecondaryContainer` and a shape token.
  - Leading: a stack of up to 3 friend avatars.
  - Label: "Friends Mix" in `titleSmall`.
  - Supporting text: "{n} songs this week" in `bodySmall`.
  - Trailing: a play icon.
- **Mix content:**
  - Union of every friend's `history`, turned into songs with `toPlayableSong()`, deduped by recording (reuse the P5 canonical id), and shuffled.
  - Each song carries the attribution of the friend who played it most recently (P4).
- **Playback:** tapping calls `playSongs(mix, first, queueName = "Friends Mix")`.
- **Updates:**
  - The count updates live from the `history` flow.
  - While "Friends Mix" is the active queue, newly seen plays that aren't already in the queue get appended to the end.
- **States:**
  - Loading: disabled, with "Gathering this week…".
  - Empty (no playable history): disabled, with "Nothing played this week yet".
  - When no live source is connected, the button is hidden.

**Motion:**
- Count changes use `AnimatedContent` with a vertical slide + fade: enter `tween(DurationShort4, easing = EmphasizedDecelerate)`, exit `tween(DurationShort3, easing = EmphasizedAccelerate)`.
- Press uses the default M3 ripple/state layer.
- Reduced motion: an instant text swap.

**Accessibility:**
- Content description "Play Friends Mix, {n} songs from this week".
- At least 48dp tall.

**Don't:**
- Build a new mix engine. Don't use `startContinuousMix` / Daily Mix learning.
- Store a separate copy of history.

**Acceptance criteria:**
- [ ] The button shows the correct count, and it updates while the screen is open.
- [ ] Tapping plays a shuffled mix with no duplicates, and every row shows its friend (P4).
- [ ] A new friend play during a Friends Mix session gets appended.
- [ ] Empty and hidden states behave as specified.
- [ ] Build command passes.

### Plan
1. Add `friendsMix: StateFlow<List<Pair<Song, FriendUi>>>` to `FriendsViewModel`, built from `activity.history` + `friends`, deduped with the P5 id. Done when the count matches the history.
2. Build the `FriendsMixButton` composable with its states, avatar stack, and animated count. Done when it renders in the P1 slot.
3. Wire the tap to `playSongs` with attribution. Done when the queue is tagged.
4. Add an app-scoped collector (next to the follow collector in `MainActivity`, or in a small singleton) that appends new plays while `queueName == "Friends Mix"`. Done when a new play gets appended.
5. Add the semantics and reduced-motion handling. Done when the build passes.

**Verification:** build passes; each criterion checked by hand; animator scale 0x.

**Risks:**
- The append collector must stop when the queue changes, or it'll inject into unrelated queues.
- History only exists for time the app was polling, so the mix reflects what PixelPlayer saw, not the full platform history.

---

## P7: Pin a friend's playlist

### Improved prompt
**Goal:** Liking a friend's playlist pins it into my playlists. It stays live (not a copy) and keeps the friend's avatar, name, and platform badge.

**Scope:**
- `presentation/components/PlaylistsHub.kt`: `FriendPlaylistCard`
- `presentation/screens/PlaylistDetailScreen.kt`: header actions for a friend playlist
- `presentation/components/PlaylistContainer.kt`: the "Your playlists" list
- `data/accounts/ConnectedLibraryRepository.kt`: friend playlists have `friendId`; add the pinned flag here
- `presentation/viewmodel/FriendsViewModel.kt`

**Spec:**
- **Pin/unpin controls:**
  - A heart toggle on the friend playlist detail header, labelled "Pin to your playlists".
  - The same toggle via a long-press menu on `FriendPlaylistCard`.
  - Pinning an unsaved public playlist saves it first (reuse `savePublicPlaylist`).
- **Storage:** a persisted `pinned` flag on that connected playlist. Unpinning removes it from Your playlists but not from Friends.
- **In Your playlists:**
  - Pinned friend playlists are listed alongside my own, following the existing sort.
  - The row shows the cover, title, a trailing or overlaid friend avatar, "by {friend name}" in the supporting line, and the existing `SourceBadge`.
  - Songs refresh when the friend's playlist changes, using the existing friend-playlist sync.
- **Scope of liking:** pinning does **not** like the songs in the playlist.

**Motion:**
- Heart toggle uses the app's existing favourite heart animation. Locate it and reuse it.
- The playlist appearing in the list uses `Modifier.animateItem()`.

**Accessibility:**
- Toggle state is announced ("Pinned" / "Not pinned").
- Avatar content description is "{friend}'s playlist".

**Don't:**
- Duplicate the playlist into a local copy.
- Add new colours.

**Acceptance criteria:**
- [ ] Pin from detail and from the card, including a playlist that wasn't saved yet.
- [ ] The pinned playlist appears in Your playlists with avatar, name, and platform badge, and survives a restart.
- [ ] Unpin removes it.
- [ ] Liked songs are unchanged.
- [ ] Build command passes.

### Plan
1. Locate the friend playlist storage and the `PlaylistContainer` data source for "Your playlists". Done when you know where the flag lives.
2. Add the persisted `pinned` flag plus `setPinned(id, Boolean)`. Done when it survives a restart.
3. Include pinned friend playlists in the Your playlists data source. Done when they're listed.
4. Add the friend avatar and "by {name}" to that row, reusing `FriendAvatar` and `SourceBadge`. Done when it displays.
5. Add the heart toggle to the `PlaylistDetailScreen` header (friend playlists only) and to the card long-press menu. Done when both toggle.
6. Add semantics and `animateItem()`. Done when the build passes.

**Verification:** build passes; each criterion checked by hand; light and dark themes.

**Risks:**
- `LikedSongsRepository` excludes `friendId != null` playlists from favourites. Keep it that way.
- The sort/filter code may assume every item is local.

---

## P8: Home, Playlists above Speed Dial

### Improved prompt
**Goal:** On Home, move the Playlists carousel directly under the Daily Mix launcher, above Speed Dial.

**Scope:** `presentation/screens/HomeScreen.kt`. The `PlaylistsCarousel` call is at ~line 510 and `SpeedDialRow` at ~line 437, both inside the `yourMixSongs.isNotEmpty()` branch.

**Spec:**
- New order: DailyMixLauncher → PlaylistsCarousel → SpeedDialRow → QuickPicks → Recently → Radio → Stats.
- Keep the existing spacer sizes between sections, with one spacer between each pair.
- No other changes.

**Motion:** none.

**Accessibility:** unchanged.

**Don't:** touch the empty or loading placeholder branch.

**Acceptance criteria:**
- [ ] The carousel sits above Speed Dial.
- [ ] Spacing is consistent.
- [ ] Build command passes.

### Plan
1. Move the `PlaylistsCarousel` block and its spacer so they sit after `DailyMixLauncher`'s `Column`. Done when the order matches.
2. Fix the spacers so there's no double gap. Done when you've looked at it on a device.

**Verification:** build passes; scroll Home in light and dark themes.

**Risks:** Speed Dial is hidden when it's empty, so check spacing with and without it.

---

## P9: Home, "New from friends" section

### Improved prompt
**Goal:** A Home section that combines my history with friends' 7-day history to surface artists and songs I haven't played.

**Scope:**
- `presentation/screens/HomeScreen.kt`: place it after `RecentlyPicksSection`
- A new `presentation/components/FriendsDiscoverySection.kt`
- A small new ViewModel, or `FriendsViewModel`
- My history source: locate what feeds `recentlyPlayedQueue` and the playback history repository. `data/backup/module/PlaybackHistoryModuleHandler.kt` points to it.

**Spec:**
- **Data:** friends' history minus anything in my listening history or library.
  - **New artists:** artists friends played that I've never played. Show up to 10, ranked by how many friends and plays.
  - **New songs:** songs friends played that I've never played. Show up to 20, most recent first.
- **Layout:**
  - Title "New from friends" in `titleMedium`.
  - Two rows: an artist row of circle cards, then a song row. Each song shows the avatar of the friend who played it.
- **Actions:**
  - Tapping a song plays the song list with P4 attribution, `queueName = "New from friends"`.
  - Tapping an artist opens `Screen.ArtistDetail.createRouteForName(name)`.
- **States:** the section is hidden when there's no friend history or nothing new. There's no loading skeleton; it just appears once data is ready.

**Motion:**
- The section appearing uses `AnimatedVisibility` with fadeIn + expandVertically: enter `tween(DurationMedium2, easing = EmphasizedDecelerate)`, exit `tween(DurationShort4, easing = EmphasizedAccelerate)`.
- Row items use `animateItem()`.

**Accessibility:**
- Cards are at least 48dp.
- Song content description: "{title} by {artist}, played by {friend}".

**Don't:**
- Show songs I've already played.
- Poll friend activity from Home. Read the cached history only.

**Acceptance criteria:**
- [ ] The section only shows unplayed artists and songs from friends.
- [ ] Tapping plays with attribution, and tapping an artist opens the artist page.
- [ ] The section is hidden when empty.
- [ ] Build command passes.

### Plan
1. Locate my playback history source and how `recentlyPlayedQueue` is built. Done when you have a flow of played recording keys and artist names.
2. Build a `friendsDiscovery` flow that diffs friends' history against mine, using P5's canonical ids for songs and lowercase names for artists. Done when test data diffs correctly.
3. Build `FriendsDiscoverySection` with the two rows, avatars, and states. Done when it renders.
4. Insert it after `RecentlyPicksSection` in `HomeScreen`, wiring play (with attribution) and artist navigation. Done when taps work.
5. Add the motion specs and semantics. Done when the build passes.

**Verification:** build passes; each criterion checked by hand; animator scale 0x; light and dark themes.

**Risks:**
- Artist-name matching is fuzzy ("feat.", casing), so normalise it with the app's existing artist delimiter logic.
- A large history could slow the diff, so compute it off the main thread.

---

## P10: Your Music button into the Library top row

### Improved prompt
**Goal:** On the Playlists tab, move "Your Music" from the full-width pinned card into the top action row, between the New playlist button and the sort/filter button.

**Scope:**
- `presentation/components/subcomps/LibraryActionRow.kt`: on the Playlists tab the search field is hidden, which leaves that gap
- `presentation/screens/LibraryScreen.kt`: the `LibraryActionRow(` call at ~line 1062
- `presentation/components/PlaylistContainer.kt`: the `pinned_system_playlists` item with `PinnedSystemPlaylistItem("Your Music")`

**Spec:**
- When `isPlaylistTab`, the row shows three things: New playlist (existing icon button), a "Your Music" button, and Sort.
  - The Your Music button is a `FilledTonalButton` with `primaryContainer` / `onPrimaryContainer`, the `round_favorite_24` icon, and the label "Your Music" in `labelLarge`.
  - It takes `Modifier.weight(1f)` so it fills the gap.
  - It navigates to `Screen.YourMusic`.
  - It's disabled in selection mode, as before.
- Remove the `pinned_system_playlists` item from `PlaylistContainer`. The liked count and song count move to the button's content description only.

**Motion:** crossfade the Your Music button with the search field when switching tabs: enter `tween(DurationShort4, easing = EmphasizedDecelerate)`, exit `tween(DurationShort3, easing = EmphasizedAccelerate)`.

**Accessibility:**
- Content description "Your Music, {songs} songs, {liked} liked".
- At least 48dp tall.

**Don't:**
- Change other tabs' rows.
- Add a new icon.

**Acceptance criteria:**
- [ ] On the Playlists tab: New playlist | Your Music | Sort, all in one row.
- [ ] The old pinned card is gone.
- [ ] Other tabs are unchanged.
- [ ] Tab switching crossfades.
- [ ] Build command passes.

### Plan
1. Add an `onYourMusicClick` param and the button slot to `LibraryActionRow`, shown only when `isPlaylistTab`. Done when it renders in the gap.
2. Pass the click and the counts from `LibraryScreen`. Done when it navigates.
3. Remove the `pinned_system_playlists` item. Done when the list starts at the platform cards.
4. Add the crossfade and semantics. Done when the build passes.

**Verification:** build passes; switch between all tabs; light and dark themes; animator scale 0.5x.

**Risks:** row width on small screens, since New playlist + Your Music + Sort + Locate may overflow. The Your Music label should ellipsize before anything wraps.

---

## Assumptions to confirm

- **5.3 needs your screenshot.** It wasn't attached, so P3 stays a diagnosis prompt until you send it.
- **"Online" means listening right now** (`LISTENING_NOW`). "Recent" (played in the last 30 min) counts as offline. If you'd rather count Recent as online, change P1 step 5, and the header ratio will change too.
- **Deleting a friend** hides them in PixelPlayer and removes their saved playlists. Nothing is changed on Spotify (P2).
- **5.4 applies only to friends with hidden activity.** Friends with no recent activity still say "No recent activity". If both should show "N playlists", change P1 step 8.
- **Queue attribution skips songs from friend playlists** (P4), because every row would get a badge. Say so if you want those tagged too.
- **"Whole week" for Friends Mix means a rolling last 7 days**, which matches the existing history window, not Monday–Sunday. It only includes plays PixelPlayer saw while polling (P6).
- **The "New from friends" title is a placeholder,** and the section is my reading of item 4: discovery = friends' plays you haven't heard (P9).
- **Pinning a friend playlist doesn't like its songs** (P7).
