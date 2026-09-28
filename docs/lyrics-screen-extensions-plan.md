# Lyrics screen extensions — implementation plan

Scope: extend the existing vertical lyrics screen (`LyricsSheet`) in place. No redesign. The horizontal layout is out of scope.
Baseline: `origin/main` @ `960dc2c` ("Queue persistence, smart resume, and cooldowns"), which matches the local checkout.

---

## 0. What already exists (reuse, don't rebuild)

| Need | Existing code | Notes |
|---|---|---|
| Lyrics screen | `presentation/components/LyricsSheet.kt` → `LyricsSheet(...)` | Hosted by `FullPlayerContent.kt` (~L1184) behind local `showLyricsSheet`; reopened via `PlayerViewModel.openLyricsSheet()` / `lyricsOpenRequests`. |
| Cover-art colours | `lyricsSheetColors(colorScheme)` → `LyricsSheetColors` (container, accent, warmHighlight, playPause, syncButton…) | `colorScheme = LocalMaterialTheme.current` (album-art scheme). Contrast helpers `preferredContrastColor`, `resolveBrightWarmColor` in the same file. |
| Top song component | `ConnectedHeader` (`lyrics/SongStructureBar.kt`) wrapping `LyricsTrackInfo` + `SongStructureStrip` | Wrapped in `AnimatedContent(targetState = currentSong)`. Lists use a hard-coded `top = 130.dp + structureTopExtra` padding. |
| Active lyric "card" | `LyricLineRow` → `activePillColor` background on the row + `LyricLineLayers(reserveStyle = Bold, restStyle = Normal, activeStyle = Bold)` | Card already wraps content; the bold "reserve" layer prevents reflow on activation. |
| Typography prefs | `LyricsDisplayPrefs.kt`: `LyricsFont` (5), `LyricsTextSize` (S/M/L), `LyricsAlignment`, `lyricsFontFamily()`, `LYRICS_SHEET_WEIGHTS = 400..700` | Font + size are set in Settings → Lyrics (`LyricsSettings.kt`); alignment in `LyricsMoreBottomSheet`. `lyricsTextStyle` is built in `FullPlayerContent`. |
| Immersive auto-hide | `immersiveMode`, `lastInteractionTime`, `resetImmersiveTimer()`, `showControlsNow()` in `LyricsSheet` | Immersive text scale ×1.4 (`fontScale`). |
| Split / table mode | `lyrics/SplitFaceLyricsView.kt` (two `LyricsHalf`s, top one `rotationZ = 180f`) | Each half has its own `LazyListState`, same position flow. Uses `lyricsTextStyle × 1.15`. |
| Bottom controls | Play/pause + `LyricsPlaybackSeekBar` + `LyricsFloatingToolbar` | |
| Queue actions | `PlayerViewModel.showAndPlaySong(song, queue, name)`, `addSongNextToQueue`, `addSongToQueue`, `playQueueItem`, `nextSong()` | Queue entries carry `QueueEntryMetadata(tier = PRIORITY/SESSION, pinned, origin)`. |
| Play Soon | **Does not exist** | See decision D1. |
| Compact action tiles | `FriendActionTile` + friend song sheet in `PlaylistsHub.kt` (Play / Play next / Add to queue) | Reuse the tile for the new action sheet. |
| Search | `presentation/screens/SearchScreen.kt` (nav route, needs `NavHostController`) → `SearchResultsList` → `onSongResultClick` → `showAndPlaySong`; also `SearchBrowseSheet` | Song rows are `EnhancedSongListItem(onClick, onMoreOptionsClick, onLongPress)`. |
| Player → nav pattern | `triggerArtistNavigationFromPlayer` + `scoped/PlayerArtistNavigationEffect.kt` (collapse sheet → await collapse → emit nav request) | Model for opening Search from lyrics. |
| Recommendation signals | `data/MixFeedback.kt` (EXCLUDE/SNOOZE/REMOVE), `data/MixLearning.kt` (`MixLearningDatabase` v4, attempts, micro-skip cooldowns) | Reactions get their own table here (§8). |
| Like | `onFavoriteToggle` / `isFavoriteProvider` passed into `LyricsSheet` → `LyricsMoreBottomSheet` | Stays separate from reactions. |
| Haptics / motion | `LocalHapticFeedback`, `AppHapticsConfig`, `ui/theme/MotionTokens` | |

---

## 1. Decisions needed before/while building

- **D1 — Play Soon.** There's no Play Soon in the app. Proposal: add `PlaybackDispatchStateHolder.addSongSoon(song)` that inserts after the current song plus any consecutive `PRIORITY`/pinned entries, capped at `current + 3`, with `QueueEntryMetadata(tier = PRIORITY, pinned = true)`. Exposed through `PlayerViewModel.addSongSoon`. Alternative: show only Play / Next / Queue until a real smart queue exists.
- **D2 — Where Add Song search lives.** Recommended: reuse the real `Search` nav route (collapse player → navigate → act → pop back → `openLyricsSheet()`), following the artist-navigation pattern. Album and artist buttons keep working because it's the real navigation graph. The alternative, embedding `SearchScreen` inside the lyrics overlay, breaks album/artist destinations because they'd open behind the player.
- **D3 — Placement of the + button.** Recommended: in `LyricsFloatingToolbar`, beside the ⋮ menu (one extra icon button, same style). Also shown as a small chip next to the "show controls" arrow while immersive.
- **D4 — Reactions in split mode.** Recommended: hidden in split mode, which is meant to stay uncluttered.
- **D5 — Tap-outside dismissal of reactions.** Recommended: dismiss and consume the tap if it lands on a lyric line, so it doesn't also seek. Any other tap dismisses without consuming.
- **D6 — Immediate effect of reactions.** Recommended: record signals now, and have only 😐 / 👎 trigger `continuousMixRuntime.refreshFuture(...)` so upcoming automatic picks re-plan. Deeper mixing integration comes later.

---

## 2. Phased implementation

### Phase A — Groundwork (no visible change)

1. **Colour roles.** Extend `LyricsSheetColors` with `surfaceSecondary`, `onSurfaceSecondary`, `reactionPositive`, `reactionNegative` and `toastContainer`. Derive them all from the album `colorScheme` (secondaryContainer / tertiaryContainer / errorContainer tones) and run them through `preferredContrastColor`.
   - Replace the fixed amber fallbacks in `resolveBrightWarmColor` with a tone shift of `tertiary`/`primary` (lighten or darken in HCT via `ColorUtils` until ≥ 4.5:1). This keeps the fallback in the artwork's hue family. Pure black/white stays as the last resort.
   - All new UI below reads only from `LyricsSheetColors`.
2. **Measured header height.** Replace `top = 130.dp + structureTopExtra` (synced list, plain list, empty state, and the 130 dp top gradient) with `headerHeightDp + gap`. `headerHeightDp` is measured with `onSizeChanged` on the header container and animated with `animateDpAsState`. This removes a magic number and is required for the collapsed header.
3. **Autoscroll re-snap keys.** Add `textStyle` and `contentPadding` to the keys of the autoscroll `LaunchedEffect` in `SyncedLyricsList`, so a font or padding change re-centres the active line immediately instead of on the next line change.
4. **Session/UI state holder.** Add `LyricsSessionStateHolder` (injected into `PlayerViewModel`, same style as the other holders) that owns:
   - `addSongSession: StateFlow<AddSongSession?>` (origin route, started-at)
   - `confirmations: SharedFlow<LyricsConfirmation>` (Playing now / Playing next / Playing soon / Added to queue)
   - `nextUp: StateFlow<Song?>`, derived from `playerUiState.currentPlaybackQueue` + `stablePlayerState.currentMediaItemIndex`, respecting repeat-all wrap and `null` at the end of the queue.

   It lives above the lyrics sheet because `LyricsSheet` is disposed while Search is open.

### Phase B — Collapsible top song component (§3, §4 and add-on §1)

Files: `LyricsSheet.kt`, `lyrics/SongStructureBar.kt`, new `lyrics/CollapsedNowNextBar.kt`, `LyricsDisplayPrefs.kt`.

- **Persistence.** Add `LyricsDisplayPrefs.headerCollapsed` (DataStore key `lyrics_header_collapsed_v1`) so the state survives the Add Song round trip and song changes.
- **Expanded state.** Leave `ConnectedHeader` + `LyricsTrackInfo` untouched. The only addition is a small collapse chevron (24 dp) at the trailing edge of `LyricsTrackInfo`, placed after `PlayingEqIcon`. Swiping up on the header also collapses it.
- **Collapsed state (`CollapsedNowNextBar`).** A full-width row with the same horizontal padding (18 dp).
  - **Left (≤ 50 % width, `weight(1f, fill=false)` + `widthIn(max = 0.5 × maxWidth)`):** 40 dp cover (same circle + rotation as `LyricsTrackInfo`, so it reads as the same component), title (`titleSmall`, SemiBold, 1 line, ellipsis) and an expand button (`KeyboardArrowDown`, 32 dp touch target of at least 48 dp). Background: `backgroundColor`, which is the same as today's header.
  - **Right (remaining width):** a "Next" micro-label, 32 dp square-rounded cover, and title (`bodyMedium`, 0.7 alpha), on `surfaceSecondary` at a lower weight so Current stays dominant. No artist and no controls.
  - **End of queue:** the right side is hidden and the left side keeps its max width.
- **Section indicators.** `SongStructureStrip` stays attached underneath via `ConnectedHeader(header = { CollapsedNowNextBar(...) }, bottom = {...})`, so section indicators are preserved in both states.
- **Switching states.** `AnimatedContent(targetState = collapsed)` with `SizeTransform` + fade. List padding follows the measured height (Phase A2), so the lyrics slide up smoothly.
- **Tapping Next (add-on §1).** The whole right-side surface is `clickable` → `playerViewModel.nextSong()`. There is no confirmation, the screen stays on lyrics, and lyrics, colours and the bar update through the existing flows. A haptic `TextHandleMove` fires. No child buttons exist inside Next; if any are added later they consume their own clicks.
- **Current → Next transition (§4).** Add a `NowNextTransition` state inside `CollapsedNowNextBar`.
  - Remember the previous `nextUp.id`. When `currentSong.id` becomes that id (the queue moved forward), animate:
    1. The right card's content translates to the left slot and scales up (cover 32 → 40 dp) with `spring(dampingRatio = 0.85, stiffness = MediumLow)`, about 350 ms.
    2. The old current fades out and moves 12 dp left.
    3. The new next fades in from 16 dp right after an 80 ms delay.
  - Slot bounds come from `onGloballyPositioned`, and the move runs in `graphicsLayer`, so there is no relayout per frame.
  - Any other change (previous, jump, new queue) uses the existing crossfade + scale from `headerAnimation`.
  - The expanded header keeps its current `AnimatedContent` behaviour.

### Phase C — Lyric typography (§5)

Files: `LyricsDisplayPrefs.kt`, `FullPlayerContent.kt` (where `lyricsTextStyle` is built), `LyricsSheet.kt` (`LyricLineRow`, `PlainLyricsLine`), `subcomps/LyricsMoreBottomSheet.kt`, `screens/settings/LyricsSettings.kt`, `lyrics/SmoothLyricsFill.kt`.

- **New prefs.**
  - `LyricsFontWeight { LIGHT(300), REGULAR(400), MEDIUM(500), SEMIBOLD(600) }`, default REGULAR.
  - `LyricsLineSpacing { TIGHT(1.1), NORMAL(1.25), RELAXED(1.45) }`, applied as the `lineHeight` multiplier.
  - `LyricsTextSize` gains `XL(1.3)`.
  - Font and alignment are reused as they are.
- **Weights.** Extend `LYRICS_SHEET_WEIGHTS` to `300..900` so variable fonts render every rest/active pair. Non-variable fonts (System, Montserrat) fall back to the platform's synthetic weights.
- **Hierarchy rule.** `restWeight = chosen` and `activeWeight = min(chosen + 300, 900)`. `LyricLineLayers` currently hard-codes Bold/Normal; pass `reserveStyle = activeStyle = style.copy(fontWeight = activeWeight)` and `restStyle = style.copy(fontWeight = restWeight)`. The reserve layer then still matches the active layout, so activation never reflows. Apply the same change in the word-cluster path in `SmoothLyricsFill`.
- **Active card sizing.** The row's vertical padding (`verticalPadding` / `targetPadding`) scales with `lineHeight / baseLineHeight`. The card is the row's own background, so it grows with the text automatically.
- **Size caps.** Clamp `fontSize × immersiveScale(1.4) × sizeMultiplier` so one line can't exceed about 30 % of the viewport height (computed from `BoxWithConstraints.maxHeight`). This prevents XL + immersive from clipping on small phones.
- **UI.** Add a compact "Text" section to `LyricsMoreBottomSheet` with segmented rows for Font / Size / Weight / Spacing (alignment already lives here) and a one-line live preview in the current `LyricsSheetColors`. Settings → Lyrics gets the same two new rows. Changes write via `editLyricsDisplayPrefs` and apply live.
- **Plumbing.** Build `lyricsTextStyle` from the prefs (weight, spacing) in `FullPlayerContent`. `SplitFaceLyricsView` gets the same style (see Phase D).

### Phase D — Split / table mode refinements (§6)

Files: `lyrics/SplitFaceLyricsView.kt`, `LyricsSheet.kt`.

- Pass the user's typography (Phase C) instead of the hard-coded `× 1.15`, with its own cap (a half has less height).
- **Sync.** Both halves already read one position flow, but each resolves its index independently. Hoist a single `activeIndex` (resolved once in `SplitFaceLyricsView`) and pass it to both `LyricsHalf`s so they can never disagree for a frame.
- Keep 180° rotation as is. Verify the swipe-direction inversion for the top half still holds.
- Reactions, collapsed bar, + button and toast are **not** shown in split mode (D4). Header pills are unchanged.

### Phase E — Add Song flow + universal song action sheet (§7 and add-on §2–§8)

New: `components/SongActionSheet.kt`, `scoped/PlayerSearchNavigationEffect.kt`, `LocalSongPrimaryTap` (CompositionLocal).
Edited: `LyricsFloatingToolbar.kt`, `LyricsSheet.kt`, `PlayerViewModel.kt`, `PlaybackDispatchStateHolder.kt` (D1), `SearchScreen.kt`, `search/components/SearchBrowseSheet.kt`, the detail screens that use `EnhancedSongListItem` (Album, Genre, Playlist), and `AppNavigation.kt`.

1. **Opening search.** Tapping + calls `playerViewModel.startAddSongFromLyrics()`:
   - It records the origin route, sets `addSongSession`, collapses the player sheet and awaits collapse (the same code path as `triggerArtistNavigationFromPlayer`), then emits `searchNavigationRequests`.
   - `PlayerSearchNavigationEffect` navigates to `Screen.Search.route` (`launchSingleTop`).
   - Search autofocuses its field while the session is active.
2. **Intercepting only the primary tap.**
   - While `addSongSession != null`, `AppNavigation` provides `LocalSongPrimaryTap = { song, defaultPlay -> openActionSheet(song) }`.
   - Song-card call sites change from `onClick = { play(song) }` to `onClick = songPrimaryTap(song) { play(song) }`. The helper returns the default when no session is active.
   - `onMoreOptionsClick`, `onLongPress` (multi-select), and artist/album/overflow buttons are untouched and behave as today.
   - Album "Play all" / shuffle buttons are dedicated controls and are **not** intercepted.
   - Rows in the sequence: `SearchResultsList` → `SearchBrowseSheet` → `AlbumDetailScreen` / `GenreDetailScreen` / `PlaylistDetailScreen` (the screens reachable from search results). `ArtistDetailScreen` uses a different row composable, so its primary song tap gets the same wrapper.
3. **`SongActionSheet`.**
   - A `ModalBottomSheet` wrapped in `MaterialTheme(colorScheme = album scheme)` so it uses cover-art colours.
   - Header: a small 40 dp cover + title/artist, one line each.
   - Four `FriendActionTile`s: **Play · Next · Soon · Queue**, using icons `PlayArrow`, `PlaylistPlay`, `Schedule`, `QueueMusic`.
   - Layout: a `BoxWithConstraints` picks one row if `maxWidth / 4 ≥ 76.dp` and the label fits (measured with `TextMeasurer` at the current font scale); otherwise it uses a 2 × 2 grid. Tiles are at least 64 dp tall. No scrolling.
   - Dismissing by swipe down or tapping outside only closes the sheet and leaves the user on Search.
4. **Choosing an action.**
   - **Play** → `showAndPlaySong(song, listOf(song) or the current results queue, "Search: …")`. This keeps today's search semantics; confirm whether "Play" should keep the rest of the queue (open question Q1).
   - **Next** → `addSongNextToQueue`.
   - **Soon** → `addSongSoon` (D1).
   - **Queue** → `addSongToQueue`.
   - Then `finishAddSong(confirmation)`:
     1. End the session.
     2. `popBackStack(originRoute, inclusive = false)` so the user isn't left in Search, a detail page or the queue.
     3. `openLyricsSheet()`.
     4. Emit the confirmation.
   - Because lyrics always re-sync to the live playback position, Next / Soon / Queue land back on the current song at the current line. Play lands on the new song through the existing flows.
5. **Back / abandon.** System back from the Search root while a session is active cancels the session and runs the same pop + `openLyricsSheet()` without a toast. Deeper back steps pop normally.
6. **Confirmation toast.** Build `LyricsConfirmationPill` inside `LyricsSheet`:
   - A pill with a check icon under the header (below the collapsed bar or strip), using `toastContainer`/`accent`.
   - Fade + scale in over 180 ms, hold for 1.6 s, then fade out over 250 ms.
   - Non-blocking: no pointer input and it doesn't consume touches.
   - Text: "Playing now ✓", "Playing next ✓", "Playing soon ✓", "Added to queue ✓".
   - It collects `confirmations` with `replay = 0` plus a pending slot, so it survives the sheet opening after the emit.

### Phase F — Reactions (§8–§13)

New: `lyrics/ReactionCorners.kt` (UI), `data/reactions/SongReaction.kt` + DAO in `MixLearningDatabase` (v5 migration), `ReactionRecorder` (injected).
Edited: `LyricsSheet.kt`, `PlayerViewModel.kt`.

- **Placement.** Two triggers sit in the lyrics `Box` at `BottomStart` / `BottomEnd` (16 dp inset), above the bottom gradient. They sit just above the controls when those are visible, and 24 dp above the nav inset when immersive.
  - The triggers are 40 dp circles on `surfaceSecondary` showing a neutral glyph (🙂 left, 😕 right; or a small heart / thumb-down icon to match the icon language).
  - Menus are overlays and never change list padding. The lyric list's bottom padding (100 dp) already keeps the active line clear.
- **State.** `openSide: Side? (POSITIVE / NEGATIVE)`, `touching: Boolean`, `lastReactionInteraction: Long`, `hoverIndex: Int?`. Opening one side sets `openSide`, which closes the other.
- **Animation (§9).**
  - Each item is driven by `Animatable(0f → 1f)` using `spring(dampingRatio = 0.7, stiffness = 700)`, which settles in about 220 ms, with a 35 ms stagger.
  - Per item: `translationY = lerp(0, -(i + 1) × 52 dp, p)`, `scale = lerp(0.4, 1, p)`, `alpha = p`, with `transformOrigin` at the trigger's centre.
  - Closing runs in reverse order with a shorter spring, so items sink back into the trigger.
- **Interaction (§10).** One `pointerInput` via `awaitEachGesture` on a container covering the trigger plus the menu column.
  - Down on the trigger opens the menu and sets `touching = true`.
  - If the pointer moves up past touch slop, it's a drag-select. Hit-test the item bounds (from `onGloballyPositioned`); a new hover index scales that emoji to 1.25 and plays a haptic (`SegmentFrequentTick` on API 34+, else `TextHandleMove`, gated by `AppHapticsConfig`).
  - Release over an item selects it. Release on the trigger with no drag leaves the menu open (a normal tap), and tapping the trigger again closes it. Release elsewhere keeps the menu open and restarts the timer.
  - Normal taps on items also work: each item has `clickable`.
- **Timeout (§11).** `LaunchedEffect(openSide, lastReactionInteraction, touching) { if (openSide != null && !touching) { delay(3_000); close() } }`. Every down, move or hover updates `lastReactionInteraction`.
- **Tap outside.**
  - A `pointerInput` on the root `Box` at `PointerEventPass.Initial` (same technique as `resetsLyricsImmersive`) closes the menu on any down outside the menu bounds.
  - It consumes the tap only if it lands on a lyric line (D5).
  - Opening a menu also calls `resetImmersiveTimer()` so the controls don't auto-hide mid-interaction.
- **After selection.** The menu collapses, and the trigger briefly shows the chosen emoji with a pop (scale 1 → 1.3 → 1) for 1.2 s before returning to neutral. No toast.
- **Signals (§12).** `enum class SongReaction(val axis: Axis, val polarity: Int, val strength: Float)`:

  | Reaction | Axis | Meaning |
  |---|---|---|
  | ❤️ LOVE | SONG_PREFERENCE | +1.0 strong song preference |
  | 🔥 FIRE | SESSION_VIBE | +1.0 energy/vibe fit for this session |
  | 👌 ALRIGHT | SONG_PREFERENCE | +0.4 mild positive |
  | 😐 NOT_THE_VIBE | SESSION_VIBE | −0.7, session-scoped |
  | 🥱 BORING | ENGAGEMENT | −0.6 |
  | 👎 DISLIKE | SONG_PREFERENCE | −1.0 |

  - Rows are stored as `(id, songId, recordingId, mixSessionId, reaction, positionMs, durationMs, at)` in a new `reactions` table in `MixLearningDatabase` (v4 → v5 migration with a schema JSON under `schemas/`, the same pattern as the 3 → 4 migration and its test).
  - `ReactionRecorder.record(song, reaction)` writes the row and exposes `reactionsFor(sessionId)` / `latestFor(songId)` for later mixing work.
  - Immediate effects are limited to D6.
- **Like stays separate (§13).** Reactions never call `onFavoriteToggle` or `LikedSongsRepository`. The existing heart in `LyricsMoreBottomSheet` is unchanged.
- **Where reactions are hidden.** Split mode (D4), the Instruments view, and while the fetch-lyrics dialog is open.

### Phase G — Quiet chrome (§14)

- Add `chromeIdle` (true after `immersiveLyricsTimeout` with no touch; reuse `lastInteractionTime`).
- While idle, the reaction triggers, the collapsed bar's Next side and the immersive + chip animate to 0.55 alpha. Current song and lyrics stay at full alpha. Any touch restores them over 200 ms.
- Existing immersive hide/show of the bottom controls is unchanged.

---

## 3. Untouched on purpose (§15)

Play/pause, seek bar/waveform, `LyricsFloatingToolbar` layout (apart from the one + icon, D3), synced/plain toggle, ⋮ menu content (apart from the new Text section), section strip, swipe-to-skip gesture, swipe-to-hide controls, the Instruments view, and the predictive-back animation.

---

## 4. Open questions

- **Q1.** For Play from Add Song: replace the queue with the search results (today's search behaviour), or play the song as a one-off and keep the current queue after it? The plan keeps today's behaviour.
- **Q2.** Should Love also optionally offer "Add to Liked" as a long-press on ❤️? The plan says no, to keep them separate.
- **Q3.** Should reactions be one per song per session (latest wins) or cumulative? The plan stores every event; the UI shows the latest.

---

## 5. Verification checklist

- **Header.** Collapse/expand keeps the line position; the active line is re-centred after the padding change. Long titles ellipsize at 50 % width. End of queue hides Next. Repeat-all wraps. The state persists across app restart and the Add Song round trip.
- **Next tap.** Tapping Next plays it immediately with no dialog; lyrics, colours and the section strip swap. A natural track end runs the slide transition; previous/jump crossfade.
- **Typography.** Every font × size × weight × spacing × alignment, plus immersive ×1.4: no overlap or clipping, the card hugs the text, word fill stays aligned, autoscroll stays centred. Test CJK/Cyrillic fallback (#2427) and system font scale 1.3.
- **Split.** Both halves are on the same line at all times (log the index from both), rotation is correct, and swipe direction is inverted on top. No reactions or toast appear.
- **Add Song.**
  - Tapping a song opens the sheet; ⋮, long-press and artist/album buttons behave as before.
  - Swipe-down or tapping outside returns to Search.
  - Each of the 4 actions returns to lyrics with the right toast, and Next/Soon/Queue keep the same song and line.
  - Back from Search cancels cleanly.
  - The sheet switches between 1 × 4 and 2 × 2 at 320 dp width and large font scale.
  - Rows in Album/Genre/Playlist detail reached from search are intercepted; the same screens opened normally are not.
- **Reactions.**
  - Tap opens, tap an item selects. Press-drag-release selects, with a haptic per new hover.
  - The menu closes after 3 s idle but never while touching; any interaction resets the timer.
  - A tap outside closes it without seeking (D5). Only one side is open at a time.
  - DB rows have the right axis and polarity, and Liked Songs are unchanged.
- **Regression.** Existing unit tests pass. Add tests for `addSongSoon` index maths, `nextUp` derivation (shuffle, repeat modes, end of queue), the v4 → v5 migration, and the reaction signal mapping.

---

## 6. Suggested commit order

1. Phase A (groundwork)
2. Phase B (header)
3. Phase C (typography)
4. Phase D (split)
5. Phase E (Add Song; D1 queue logic first, as its own commit with tests)
6. Phase F (reactions: DB first, then UI)
7. Phase G (quiet chrome)

Each phase is independently shippable, and B, C and F don't depend on E.
