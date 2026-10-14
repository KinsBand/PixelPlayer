# Batch 16 plan: visualisers, Adjust layout, download quality, friend history, playlist options, blends, queue History/Heard, notification heart (2026-10-10)

This plan covers all 13 items (plus 7.1, 7.2, 10.5 and 10.7). Every claim about the current code cites an evidence line (E#) read on 2026-10-10 from `PixelPlayer-Clean-` (working copy, uncommitted changes included).
Paths are relative to `app/src/main/java/com/theveloper/pixelplay/` unless they start with `app/`.

**Status:** plan only. No code has been changed. Compiled: no. Run on device: no.

**Your answers (2026-10-10)**
- 7.2 "imaginary slider": the ruler with **no pill background**. The ticks float and fade at the edges, and the centre line stays.
- Delete / Remove playlist: a **slim full-width row under Auto | Export**.
- Home > Playlists: hide **friends'** playlists (saved and unsaved). Your own imported Spotify/YT Music playlists still show. A friend's picture shows all of theirs.
- Download quality: **infer** old downloads from the file's codec/bitrate and record new ones in a **small side store**. No Room migration.

**Suggested build order** (smallest and highest value first, each one shippable alone):
J (heart), E (loader), I-1 (Heard playback fix), A (visualisers), B (home grid), F (friend history), D (Adjust), G (playlist options and queueing), H (blends), I-2 (History/Heard UI), C (download quality).

---

## 1. Evidence

**Notification heart (13)**
- E1 `data/service/MusicService.kt:2251-2257`: the Like button is `CommandButton.Builder(if (isFavorite) ICON_HEART_FILLED else ICON_HEART_UNFILLED)` with **one fixed** `SessionCommand(CUSTOM_COMMAND_LIKE)` in both states.
- E2 `MusicService.kt:2259-2272`: Shuffle, which you say updates correctly, uses a **different command action per state** (`CUSTOM_COMMAND_SHUFFLE_ON` / `_OFF`).
- E3 `MusicService.kt:973-987`: the favourites Room flow triggers `refreshMediaSessionUi` when the current song's favourite flag flips. `MusicService.kt:2068-2094`: `setCurrentSongFavoriteState` updates `favoriteSongIds` optimistically, then refreshes.
- E4 `MusicService.kt:2230-2244`: the button signature includes `isSongFavorite(...)`, so a state change does reach `setMediaButtonPreferences` (`:1958`).
- E5 `MusicService.kt:629-645`: `onConnect` registers the custom commands. `data/service/MusicNotificationProvider.kt:11-13`: has `CUSTOM_COMMAND_LIKE` and `CUSTOM_COMMAND_SET_FAVORITE_STATE`. There is no LIKE_ON/LIKE_OFF pair.

**Full player loader (8)**
- E6 `presentation/components/player/FullPlayerContent.kt:2193-2239`: a separate `LoadingIndicator` chip (`AnimatedVisibility(visible = isBuffering)`, using raw `FastOutSlowInEasing` and 400/300 ms) sits left of the lyrics/queue buttons.
- E7 `presentation/components/player/AnimatedPlaybackControls.kt:262-289`: `MorphingPlayPauseIcon` already has a loading state (`iconState 3`, which Crossfades to `LoadingIndicator`). Its content description is "Loading video".
- E8 `FullPlayerContent.kt:924`: `isLoadingProvider = if (isVideoMode) ({ videoPlayback.isLoading }) else ({ false })`. Audio buffering never reaches the play button.

**Visualisers (1, 3, 4, 5)**
- E9 `presentation/components/PlaylistPlayingBadge.kt:27-35`: `rememberIsPlaylistPlaying(playlistId)` matches `NowPlayingPlaylist.current`. `:41-66`: `PlaylistPlayingBadge` is a primary circle with `PlayingEqIcon`, scaleIn 250 / scaleOut 180 (raw tweens).
- E10 `data/playback/NowPlayingPlaylist.kt:18-20` and `presentation/viewmodel/PlaybackDispatchStateHolder.kt:875`: set on every `internalPlaySongs(..., playlistId)`. `PlayerViewModel.kt:2616`: `playSongs(..., playlistId: String? = null)`.
- E11 `presentation/components/PlaylistContainer.kt:666-683`: the Library list uses the badge at **BottomEnd** with the real `isPlaying`. This is the "normal" look.
- E12 `presentation/components/PlaylistsHub.kt:721-726`: `FriendPlaylistCard` uses the badge at **TopStart** with `isPlaying = true` hard-coded.
- E13 `presentation/components/SpeedDialGrid.kt:308-318`: `containsSong` returns `false` for `PlaylistItem` and `FriendPlaylistItem`, so home grid playlist tiles never show as playing.
- E14 `presentation/screens/PlaylistDetailScreen.kt:1559-1593`: the hero cover `Box` has no badge.
- E15 `presentation/screens/FriendsScreen.kt:283-285`: Friends Mix plays with `playSongs(songs, it, FRIENDS_MIX_QUEUE_NAME)` and **no playlistId**. `PlaylistsHub.kt:356-421`: `FriendsMixButton` ends with a static `Icons.Rounded.PlayArrow`.
- E16 `FriendsScreen.kt:393-395`: the custom blend Play button plays with **no playlistId**. `presentation/components/BlendsRow.kt:47-56` and `:182-191`: blend and custom-blend page ids are `GENERATED_MIX_PREFIX + "blend:…:day"`. `BlendCard` (`:95-166`) and `CustomBlendCard` (`:200-287`) have no badge.
- E17 `data/social/FriendsMixController.kt:12`: `FRIENDS_MIX_QUEUE_NAME = "Friends Mix"`.

**Home playlists (2)**
- E18 `presentation/screens/HomeScreen.kt:802-838`: `ownPlaylists` includes saved friend playlists (it only filters system/liked/favourites). `friendPublic` adds friends' unsaved playlists to the grid always.
- E19 `HomeScreen.kt:492-502` and `SpeedDialGrid.kt:197-207`: friend filters exist. Selecting one shows `PlaylistItem` whose id is in `friend.playlistIds`, plus that friend's `FriendPlaylistItem`. `data/model/Playlist.kt:27`: `friendId: String?`.

**Download quality (6)**
- E20 `data/youtube/DownloadQuality.kt:4-11`: `HIGH` (best, usually Opus ~160 kbps), `MEDIUM` (AAC ~128 kbps M4A), `LOW` (~48–70 kbps). `:28-32`: `pick(manifest)`.
- E21 `data/youtube/SongDownloadManager.kt:100`: `downloadSong(song, quality: DownloadQuality? = null)`. `:122-131`: `downloadOptions(song)`.
- E22 `data/youtube/DownloadQueue.kt:104`: `enqueueAll(songs, label)` has **no quality** parameter (grep found no "quality" in DownloadQueue.kt). `presentation/viewmodel/PlaylistViewModel.kt:889-902`: `downloadPlaylist(songs, wifiOnly)`.
- E23 `data/database/CloudSongEntity.kt:19-41`: has `isDownloaded`, `localSongId` and `localFilePath`, but **no quality** column. `data/database/MusicDao.kt:127,138`: the songs table stores `mime_type` and `bitrate`.
- E24 `PlaylistDetailScreen.kt:418-432`: `onDownloadClick` leads to `showDownloadConfirm` (an AlertDialog, `:1075-1110`) or `showDownloadedSheet` (`:1034-1072`).

**Adjust (7, 7.1, 7.2)**
- E25 `presentation/components/CoverAdjustSlider.kt:86-147`: a 64 dp `CircleShape` pill on `surfaceContainer`. Left: icon, or the value once moved (`AnimatedContent`). Middle: `AdjustRuler`. Right: a reset `IconButton` that calls `onValueChange(default)` **once** (`:134-137`).
- E26 `CoverAdjustSlider.kt:176-185`: an outside change glides only the **ruler** (`glide.animateTo(..., tween(DurationMedium2, Emphasized))`). The value itself, and so the cover and the number, jumps straight to default.
- E27 `presentation/components/CoverFiltersPanel.kt:268-300`: the slider slot sits **inside** the panel under the tool tiles (`AnimatedVisibility`, `expandVertically` + `coverEditLayoutSpring`).
- E28 `presentation/screens/CreatePlaylistScreen.kt:1727-1836`: the bottom row is Change · Done/Apply · Filters. With filters open, Done shrinks to a centred 184 dp Apply pill (`ApplyPillWidth`, `:1841`). `:1734-1735`: `EditActionsDivider()` stays above the row while filters are open.
- E29 `CoverFiltersPanel.kt:304-362`: the tool tile shows its value instead of the icon once moved (`:350-359`).

**Friend history (9)**
- E30 `data/social/FriendActivity.kt:196-201`: a poll counts as the same play only if `key` matches **and** `|playedAt - previous| < 30 s`. Otherwise a new row is added.
- E31 `data/spotify/web/SpotifyFriendActivitySource.kt:61-64`: `durationMs = null` and `playedAt = it.timestamp`. `data/spotify/web/SpotifySocialRepository.kt:205-216`: live-feed rows take `timestamp` from the feed (`fromLiveFeed = true`), and `:177` treats a recent live-feed row as PAUSED. The feed timestamp moves on pause, resume and seek, which breaks the ±30 s rule and makes back-to-back duplicates.
- E32 `FriendActivity.kt:76-78`: `FRIEND_HISTORY_WINDOW_MS` is 7 days and `MIN_TRACK_MS` is 3 min. `PlaylistsHub.kt:762-800`: `FriendHistorySheet` groups by day and shows "`history.size` plays this week".
- E33 `presentation/components/QueueBottomSheet.kt:3236-3242`: the app already shows a count as `"🔥 ${mentionCount}x"` (`labelSmall`, `tertiary`) on Heard cards.

**Playlist page and options (10, 10.5)**
- E34 `PlaylistDetailScreen.kt:286-294,516-519`: `onEditClick` always sets `isSelectionMode = true`. Only Back and `exitSelection()` leave edit mode. `:1708-1724`: Edit is a `FilledTonalButton` with no selected state.
- E35 `PlaylistDetailScreen.kt:905-1031`: the options sheet has a title, Sync (connected playlists), a full-width "Download new songs automatically" item (`:955-964`, only when `downloaded > 0 || keepInSync`), then a row of Export Playlist + Delete/Remove.
- E36 `PlaybackDispatchStateHolder.kt:1103-1171`: `addSongNextToQueue` and `addSongSoon` insert **one** song. `:1365-1376`: `playSoonInsertionIndex` (cap `PLAY_SOON_MAX_AHEAD = 3`). `MultiSelectionStateHolder.kt:212-237`: multi-song queue/next loop per song. `data/model/QueueEntryMetadata.kt:14-39`: per-item extras (`tier`, `origin`, `pinned`, …) with no group/playlist field.
- E37 `QueueBottomSheet.kt:876-881,2147-2178`: `QueueSourceBadge` (top right of the queue) draws `queueSourceName` as plain `Text`, with no animation. `PlaybackDispatchStateHolder.kt:904,919`: the name is only set when a queue is (re)started.

**Blends (10.7, 11)**
- E38 `BlendsRow.kt:193`: `CUSTOM_BLEND_NAME = "Custom Blend"` is used for the custom blend card, page and sheet title (`:185, :276, :330`). `:263-275`: the ticked friends' pictures sit at the **TopEnd** of the cover.
- E39 `BlendsRow.kt:311-371`: `CustomBlendSheet` is a title with Select all, then Open playlist / Play, then the member list. `FriendsScreen.kt:313-319,385-407`: the custom card always opens this sheet.
- E40 `BlendsRow.kt:97,215`: "Building…" is a text label only. There's no building animation.
- E41 `PlaylistDetailScreen.kt:1698-1724`: the second header row is Add (when `canEditSongs`) · Edit · Sort icon (`FilledTonalIconButton`). Blend pages are read-only (registered as transient generated playlists, E16).

**Queue History / Heard (12)**
- E42 `QueueBottomSheet.kt:992-1001`: `HeardSongsSection` is a full card at the top of the list. `:3065-3160`: it has a "Heard Suggestions" title, a count, a Clear button and expand/collapse. `:3166-3327`: each card has Approve (✓), Next and Dismiss (✕) buttons and **no long-press**.
- E43 `QueueBottomSheet.kt:1037-1052` (portrait) and `:1294-1303` (landscape): `QueueHistoryToggle` is a full-width button. `:3431-3510`: when open it shows Clear plus a Close ✕ button. History rows already long-press to options (`:2867-2873`, `isFromPlaylist = true`).
- E44 `data/recognition/ambient/NowPlayingNotificationListener.kt:56-63`: **Pixel Now Playing suggestions are added as `Song.emptySong().copy(id = random UUID, title, artist)`.** That's an empty `contentUriString`/`path` (`data/model/Song.kt:293-305`) with no YouTube id, so **they can't play**. This is the bug.
- E45 `data/recognition/ambient/AmbientSuggestionController.kt:141-155`: spoken suggestions are resolved properly (local search, then `youTubeRepository.searchSongs` + `AmbientSongMatcher.bestMatch`). This is the same kind of path that works from Search.
- E46 `presentation/viewmodel/PlayerViewModel.kt:248-256`: approve/next queue `item.song` as-is, with no resolve step.

**Motion tokens**
- E47 `ui/theme/MotionTokens.kt`: `Emphasized`, `EmphasizedDecelerate`, `EmphasizedAccelerate`, `Standard*`. Durations: `DurationShort1–4 = 50/100/150/200`, `DurationMedium1–4 = 250/300/350/400`, `DurationLong1/2 = 400/500`.
- E48 `CoverFiltersPanel.kt:93-96`: the Adjust screen's own `coverEditLayoutSpring()` (damping 0.85, `StiffnessMediumLow`).

---

## 2. Prompts (one per area, copy-ready)

### A. "Now playing" visualiser on every playlist-like thing (items 1, 3, 4, 5)

```
Goal: whenever the current queue came from a playlist, friend playlist, blend, custom blend or
Friends Mix, its cover/button shows the animated bars badge. Same look everywhere: bottom-right
of the cover, bars move while playing and rest while paused (like Library, E11).

Scope: PlaylistPlayingBadge.kt (E9), SpeedDialGrid.kt (E13), PlaylistsHub.kt FriendPlaylistCard
(E12) + FriendsMixButton (E15), BlendsRow.kt BlendCard/CustomBlendCard (E16),
PlaylistDetailScreen.kt hero cover (E14), FriendsScreen.kt play calls (E15, E16).

Current behaviour: Friends Mix and custom-blend Play pass no playlistId (E15, E16), so nothing
can match. The home grid never marks playlists (E13). The friend card badge is top-left and
always "playing" (E12). The playlist page cover and blend cards have no badge (E14, E16).

Spec:
- Badge component: add an isPlaying-aware overload that reads stablePlayerState.isPlaying
  itself, so call sites stop hard-coding true. Default placement Alignment.BottomEnd with
  padding 6.dp on cards, offset like E11 on small list covers.
- Ids:
  - Friends Mix: FRIENDS_MIX_PLAYLIST_ID = "${GENERATED_MIX_PREFIX}friends-mix", passed as
    playlistId in FriendsScreen:285.
  - Blend: blendPlaylistId(blend) and customBlendPlaylistId(blend), pure functions pulled out
    of registerBlendPlaylist / registerCustomBlendPlaylist (same strings as today, E16). Pass
    customBlendPlaylistId to the custom Play call (FriendsScreen:395).
- Home grid: PlaylistItem/FriendPlaylistItem tiles show the badge when
  rememberIsPlaylistPlaying(id) matches. Don't reuse containsSong (that's for song tiles).
- Friend playlist card: move the badge to BottomEnd (the source badge stays TopEnd), with the
  real isPlaying.
- Blend cards: the badge goes BottomEnd on the cover (the friend avatar stays TopEnd until H
  moves pictures to the bottom middle; then the badge stays BottomEnd and the avatars centre).
- Playlist page hero cover (E14): the badge goes BottomEnd, size 32.dp, when this playlist is
  playing.
- Friends Mix button (item 3): the trailing PlayArrow becomes PlayingEqIcon (onSecondaryContainer)
  while Friends Mix is the current queue, and goes back to PlayArrow otherwise. Tapping while
  playing toggles play/pause instead of restarting the mix.
- Colors: primary / onPrimary, with the ring in the card's own container role. Shape CircleShape.

Motion:
- Badge enter: scaleIn(0.6) + fadeIn, tween(DurationShort4, EmphasizedDecelerate).
- Badge exit: scaleOut(0.6) + fadeOut, tween(DurationShort3, EmphasizedAccelerate).
  (Replaces the raw 250/180 tweens in E9.)
- Friends Mix icon swap: AnimatedContent, fadeIn+scaleIn tween(DurationShort4,
  EmphasizedDecelerate) togetherWith fadeOut tween(DurationShort2, EmphasizedAccelerate).
- Interruption: the visibility target flips mid-animation and resumes from the current scale.
  The bars only animate while visible.
- Reduced motion (animator scale 0): the badge appears and disappears instantly and the bars
  are static (PlayingEqIcon isPlaying=false).

Accessibility: the badge has stateDescription "Now playing" / "Paused" merged into the card.
The Friends Mix button's description becomes "Friends Mix, playing" while playing.

Don't: add a second badge component; hard-code colors; read isPlaying in composition per frame
(the bars already animate internally).

Acceptance criteria:
- [ ] Play a friend's playlist: its home tile, friend card and playlist page cover all show the
      bars bottom-right. Pause: bars rest.
- [ ] Play Friends Mix: the play arrow becomes moving bars. Play anything else: back to the arrow.
- [ ] Play a friend blend from its page: the blend card shows the badge.
- [ ] Play the custom blend: the custom card shows the badge.
- [ ] Your own playlist: the badge is on its page cover and home tile.
- [ ] No badge anywhere after playing an album or a single song.
```

### B. Home > Playlists: yours by default, friends' behind their picture (item 2)

```
Goal: the home Playlists grid shows only your playlists. Tapping a friend's picture next to the
title shows all of that friend's playlists (saved and not yet saved).

Scope: HomeScreen.kt:802-838 (E18), SpeedDialGrid.kt:197-207 (E19).

Current behaviour: saved friend playlists sit in ownPlaylists and unsaved friend playlists are
always appended (E18).

Spec:
- Unfiltered grid = YourMusic + playlists with friendId == null (keep your own imported
  Spotify/YTM playlists) + mood tiles.
- Pass the friend playlists separately (saved: friendId != null; unsaved: friendPublic) and show
  them only when that friend's filter is selected. Selected friend = their saved +
  unsaved playlists, nothing else.
- PlaylistFriendFilter list: friends with ≥1 playlist (saved or public). Today it needs a
  saved id or a public one in the grid (E19); keep that working once friend items aren't in
  the default list.
- Pins: a pinned friend playlist stays visible in the default grid (it was explicitly pinned).

Motion: the grid content swaps with the existing pager/scroll-to-page-0 (E19, :212). Tiles
fade through: fadeIn tween(DurationMedium1, EmphasizedDecelerate), fadeOut tween(DurationShort3,
EmphasizedAccelerate). Reduced motion: an instant swap.

Accessibility: friend pictures keep their existing selected/dimmed semantics.

Acceptance criteria:
- [ ] Default grid: no friend playlists (unless pinned).
- [ ] Tap a friend: only their playlists. Tap again: back to yours.
- [ ] Your imported Spotify playlist still shows by default.
```

### C. Playlist download popup with High / Mid / Low and counts (item 6)

```
Goal: the playlist Download popup lets you pick High, Medium or Low. Each card shows
"x songs" already downloaded at that quality. Picking a quality downloads the missing songs at
it and can also re-download the already-downloaded ones at that quality (upgrade or downgrade).

Scope: PlaylistDetailScreen.kt download dialog/sheet (E24), PlaylistViewModel.kt downloadPlaylist
(E22), DownloadQueue.kt enqueueAll (E22), SongDownloadManager.kt downloadSong (E21), new
data/youtube/DownloadQualityStore.kt (side store).

Current behaviour: one "Download N songs?" dialog with no quality choice (E24). The queue has
no quality (E22) and the DB has no quality column (E23).

Spec:
- Store: DownloadQualityStore (@Singleton, @Inject), a small AtomicFile JSON map songId -> quality
  name in noBackupFilesDir. Write it when a download finishes with a known quality.
- Infer for older downloads: from the downloaded local song's mime_type + bitrate (E23):
  opus/webm >= 120 kbps -> HIGH; mp4/aac -> MEDIUM; any < 90 kbps -> LOW; otherwise unknown.
  Pure function classifyDownloadQuality(mime, bitrateKbps), unit-tested.
- ViewModel: qualityCounts(playlist): Map<DownloadQuality, Int> + unknown, computed off-main.
- Popup (replaces the AlertDialog with a ModalBottomSheet):
  - Title "Download playlist", with the song count line.
  - Three cards side by side (High / Medium / Low): label (titleSmall), codec+kbps hint
    (labelSmall), and "x songs" (labelMedium). Selected = primaryContainer, others
    surfaceContainerHigh. Shape MaterialTheme.shapes.large.
  - Default selection: the starred preferred quality (DownloadCoordinator.preferredQuality),
    else MEDIUM.
  - Toggle row "Also change already-downloaded songs to <quality>" (off by default), shown only
    when some downloaded songs are at a different quality. It says how many.
  - "Only download on Wi-Fi" (existing) and "Download new songs automatically" (existing
    keepInSync).
  - Primary button: "Download N" / "Change N" / "Download N · change M".
- Download path: DownloadQueue.enqueueAll(songs, label, quality) passes quality through to
  downloadSong(song, quality). Re-download = download the new file first, then swap the cloud
  row and delete the old file only after success, so a failure never loses the song.
- Fully downloaded playlists open this same sheet (replaces the "Downloaded" dialog, E24),
  with the toggle preselected.

Motion: sheet show/hide via sheetState.show/hide() then onDismiss. Card selection is a color
change with animateColorAsState tween(DurationShort4, Emphasized). Counts use AnimatedContent
(slide+fade, DurationShort4 EmphasizedDecelerate in, DurationShort3 EmphasizedAccelerate out).

Accessibility: the cards are a selectableGroup with Role.RadioButton and stateDescription
"Selected, 12 songs downloaded at High". Targets are ≥ 48dp.

Don't: add a Room column (decision); delete an old file before the new one is saved; download
local songs.

Acceptance criteria:
- [ ] New playlist: pick Low -> all songs download at Low, and the Low card shows N songs.
- [ ] 10 at High, 5 missing: pick Medium without the toggle -> 5 download at Medium (High 10,
      Medium 5).
- [ ] Same with the toggle on -> Medium 15, High 0.
- [ ] Turning off Wi-Fi mid-upgrade leaves the old files playable.
```

### D. Adjust: slider in the bottom area, value left of Apply, reset right, smooth reset, no pill (7, 7.1, 7.2)

```
Goal: (your drawing) with a tool selected, the slider moves down into the bottom area. The tool
row stays where it is. The bottom row becomes [value] [Apply] [reset], and the separator above
the row goes away while a slider is open.

Scope: CoverAdjustSlider.kt (E25, E26), CoverFiltersPanel.kt slider slot (E27),
CreatePlaylistScreen.kt PlaylistCoverAdjustContent bottom row (E28).

Current behaviour: a pill with icon/value · ruler · reset sits under the tiles (E25, E27). The
divider stays while filters are open (E28). Reset jumps the value; only the ruler glides (E26).

Spec:
- Hoist the slider target: CoverFiltersPanel exposes the open SliderTarget (or takes it as
  state) so the screen can draw the slider and the value/reset outside the panel.
- Layout while a slider is open (filters open):
  1. Panel: tabs + tool tiles (unchanged).
  2. Ruler: full width (16.dp side padding), **no pill background** (7.2). The ticks fade at
     the edges (the existing fade, E25 Canvas), with the centre line in primary. Height 56.dp.
  3. Bottom row: [value 56.dp circle, surfaceContainerHigh, titleMedium number, or the tool
     icon at default] — [Apply pill, unchanged] — [reset 56.dp FilledTonalIconButton,
     disabled at default].
  4. EditActionsDivider hidden while a slider is open (shown again when no slider).
- With filters open and no tool selected: the Apply pill alone, as today.
- The value bubble takes over the tile's "icon becomes number" behaviour (E29). The tile keeps
  showing its number too.
- 7.1 smooth reset: reset runs an Animatable from the current value to default over
  tween(DurationMedium2, Emphasized). Each frame it emits the rounded value through
  onValueChange (the cover, number and ruler all glide), then calls onValueChangeFinished
  once at the end. A drag or a tool switch cancels the reset animation and carries on from
  where it is.
- Strength (presets) uses the same bottom layout. Its default is 100 (E27).

Motion:
- Ruler enter: expandVertically + fadeIn(tween(DurationMedium1, EmphasizedDecelerate)) with
  coverEditLayoutSpring (E48). Exit: shrinkVertically + fadeOut(tween(DurationShort3,
  EmphasizedAccelerate)).
- Value and reset buttons: the existing editSideButtonEnter/Exit pattern (E28).
- Divider: fadeOut tween(DurationShort3, EmphasizedAccelerate) / fadeIn tween(DurationShort4,
  EmphasizedDecelerate).
- Interruption: closing mid-reset stops the Animatable and keeps the reached value.
- Reduced motion: reset snaps to default.

Accessibility: the value circle has contentDescription "<tool> <value>". Reset is
"Reset <tool>" and disabled at default. The ruler keeps its setProgress semantics (E25).

Don't: change tool math or presets; hard-code colors; add a parallel easing.

Acceptance criteria:
- [ ] Tap White point: the ruler appears low, [value][Apply][reset] row, no divider line.
- [ ] Drag to -40: the left circle shows -40 and the cover updates live.
- [ ] Reset: the number counts back to 0 and the cover fades back over ~300 ms. Reset disables.
- [ ] Tap the tile again: the slider closes, the divider returns, Apply stays centred.
```

### E. Full player: the play/pause button is the loader (item 8)

```
Goal: while a song is loading/buffering in the full player, the big play/pause button shows
the loading indicator. The separate chip left of the lyrics button goes away.

Scope: FullPlayerContent.kt:924 and :2193-2239 (E6, E8), AnimatedPlaybackControls.kt
MorphingPlayPauseIcon (E7).

Spec:
- isLoadingProvider = video ? videoPlayback.isLoading : stablePlayerState.isBuffering (read via
  a provider lambda so the controls don't recompose the screen).
- Remove the buffering chip block (E6).
- MorphingPlayPauseIcon loading description: "Loading" (not "Loading video").
- Tapping the button while loading still toggles play/pause, as today.
- Short buffering blips: show the loader only after 250 ms of continuous buffering, so seeks
  don't flicker.

Motion: the existing Crossfade with motionScheme.fastEffectsSpec() (E7). The play/pause corner
morph is unchanged. Reduced motion: an instant swap.

Acceptance criteria:
- [ ] Start a streamed song: the centre button spins, then becomes pause.
- [ ] No spinner chip next to lyrics/queue.
- [ ] A seek on a downloaded song shows no flicker.
```

### F. Friend week history: one card per real play + 🔥 count (item 9)

```
Goal: a friend's week history never shows the same song twice in a row unless they really
replayed it. Every card shows "🔥 n" when that song was played n ≥ 2 times this week.

Scope: FriendActivity.kt report() (E30), SpotifyFriendActivitySource.kt (E31),
PlaylistsHub.kt FriendHistorySheet (E32), new pure data/social/FriendPlayMerge.kt + test.

Root cause: a play counts as "the same" only within ±30 s of the first start (E30), but the
live feed's timestamp moves on pause/resume/seek (E31). durationMs is null (E31), so the
code can't tell a replay from a resume.

Spec (pure function, unit-tested):
- mergeObservation(history, observation, isPlaying, now): newest-first list. Let last =
  the newest row for that friend.
  - Different song from last -> new row.
  - Same key as last -> same play if ANY of:
    a) |obs.playedAt - last.playedAt| < 30 s (today's rule), or
    b) obs.playedAt - last.playedAt < effectiveDuration(last) + 60 s grace, where
       effectiveDuration = durationMs ?: MIN_TRACK_MS (3 min), or
    c) the friend was seen paused on this song since last.playedAt (a resume).
    Same play -> update last.lastSeenAt (new field, default 0). Don't add a row.
  - Same key, outside the window and not a resume -> a real replay: new row.
- FriendTrack gets lastSeenAt: Long = 0 and pausedSeen: Boolean = false (Gson-safe defaults,
  same checked* rebuild, E30 :209-212). Field names stay stable (CLAUDE.md §8).
- Duration: when a track has a Spotify URI and the Spotify web library can return the track
  length (check SpotifyWebLibrary for an existing track lookup before adding one; reuse only),
  fill durationMs once per URI (cached). Otherwise use the 3-min fallback.
- Clean-up of stored history: on load, run the same merge over each friend's list
  (oldest -> newest), so existing duplicates collapse once. Save the result.
- 🔥 count: playCount = number of rows with the same key in the 7-day window (after merge).
  The card shows "🔥 n" (labelSmall, tertiary, bold, same style as E33) when n ≥ 2. Header:
  "x plays this week" counts merged rows.

Motion: rows use Modifier.animateItem(). No other new motion.

Accessibility: card description appends ", played n times this week" when n ≥ 2.

Tests (not run): FriendPlayMergeTest:
- pause/resume of one 3:30 song -> 1 row;
- song A, song B, song A -> 3 rows;
- A then A again after its full length -> 2 rows, count 2;
- polls every 10 s with drifting timestamps -> 1 row;
- 3 plays across days -> 🔥 3.

Acceptance criteria:
- [ ] A friend pausing and resuming one song shows one card.
- [ ] A friend looping a song 3 times shows 3 cards, each with 🔥 3.
- [ ] Old duplicate rows collapse after the app restarts.
```

### G. Playlist page: Edit toggles off + options sheet redesign + queueing a playlist (10, 10.5)

```
Goal: (10) pressing Edit again leaves edit mode. (10.5) the options sheet gets a new layout and
whole-playlist queueing with a turnover card and an animated source title.

Scope: PlaylistDetailScreen.kt (E34, E35), PlaybackDispatchStateHolder.kt (E36),
QueueEntryMetadata.kt (E36), QueueBottomSheet.kt QueueSourceBadge + list (E37), PlayerViewModel.

Spec – Edit (10):
- onEditClick toggles: if isSelectionMode -> exitSelection(), else enter.
- The Edit button shows a selected state while editing: container secondaryContainer ->
  primary, content onPrimary, icon Edit -> Check, label "Done". Use animateColorAsState
  tween(DurationShort4, Emphasized) and AnimatedContent for icon/label.

Spec – Options sheet (10.5), top to bottom:
1. Title + playlist name (unchanged).
2. Sync (connected playlists only, unchanged).
3. Row of three equal FilledTonalButtons: [Queue] [Soon] [Next] (icons: queue music,
   schedule, playlist play; labels labelLarge).
4. Row of two equal (50/50) buttons: [Auto] (left, the download-done icon, label "Auto",
   toggled look when keepInSync is on, shown always and disabled until the playlist has a
   download) and [Export] (right, label "Export").
5. A slim full-width Delete / Remove playlist row (errorContainer) when allowed (your answer).
- Soon: tapping it slides a panel up **over rows 4–5** (same sheet, AnimatedContent):
  - A slider (Material Slider, steps = upcoming count) "after song k of N".
  - Below it: the song at k (art 40dp, title, artist), so you see "plays after <title>".
  - Top right of the panel: a confirm FilledIconButton (Check). Top left: back (closes the panel).
  - Default k = playSoonInsertionIndex result (E36) mapped to "after song k".
- Queue / Next / Soon(k) call a new PlayerViewModel.queuePlaylist(playlist, songs, placement),
  placement = End | Next | AfterUpcoming(k).
- New PlaybackDispatchStateHolder.queuePlaylist: builds all MediaItems tagged with new
  QueueEntryMetadata fields groupId (UUID) + groupName (playlist name) + groupPlaylistId, then
  one controller.addMediaItems(index, items). Tier SESSION, pinned = true.
  Inside the block there's no per-song de-dupe (the playlist stays whole).
- Turnover card: in QueueBottomSheet, before the first song of each group, a non-song row:
  playlist cover 40dp + "Then: <playlist name>" (titleSmall) + "n songs" (labelMedium),
  surfaceContainerHigh, shapes.large. Long-press opens "Remove playlist from queue" (removes
  the whole group).
- Title switch: when the current item's groupName differs from currentQueueSourceName
  (onMediaItemTransition in MediaControllerSyncStateHolder.kt:688), set currentQueueSourceName =
  groupName and NowPlayingPlaylist.onQueueStarted(groupPlaylistId, groupName), so the
  visualiser from A moves to that playlist too.
- QueueSourceBadge text uses AnimatedContent: slideInVertically{it/2}+fadeIn tween(DurationMedium1,
  EmphasizedDecelerate) togetherWith slideOutVertically{-it/2}+fadeOut tween(DurationShort3,
  EmphasizedAccelerate), plus SizeTransform with coverEditLayoutSpring-like spatial spring
  (MaterialTheme.motionScheme spatial) so the pill width eases.

Motion (sheet): the Soon panel enters with slideInVertically{it}+fadeIn tween(DurationMedium2,
EmphasizedDecelerate) and exits with slideOutVertically{it}+fadeOut tween(DurationShort4,
EmphasizedAccelerate). Actions close the sheet via sheetState.hide() then dismiss (existing
rememberAnimatedSheetDismiss). Reduced motion: instant.

Accessibility: Soon slider stateDescription "After song k, <title>". Confirm is "Queue after
<title>". All buttons ≥ 48dp.

Acceptance criteria:
- [ ] Edit -> edit mode, button shows Done. Tap again -> out, selection cleared.
- [ ] Sheet rows: Queue|Soon|Next, Auto|Export (50/50), Delete row.
- [ ] Soon -> slider over Auto/Export. Pick "after song 4 (Title X)" -> confirm -> the playlist
      starts right after Title X in the queue, under a "Then: <name>" card.
- [ ] When the first playlist song starts, the top-right title animates to the playlist name
      and that playlist's cover shows the bars.
- [ ] Queue/Next put the whole playlist at the end/next, in order.
```

### H. Blends: names, cover pictures, friends deck, first-run flow, building animation (10.7, 11)

```
Goal: the custom blend is named from its members' initials, shows their pictures bottom-centre
on the cover, and has a friends button. First use goes friend list -> building animation ->
built blend page. Later uses open the built blend straight away.

Scope: BlendsRow.kt (E38–E40), FriendsScreen.kt custom flow (E39), PlaylistDetailScreen.kt
header second row (E41), FriendsViewModel custom blend state.

Spec:
- Name: customBlendTitle(friends) = initials of each ticked friend, joined with " · " (e.g.
  "J · M · K"). More than 5 -> first 5 + " +n". Used for the card title, page name
  (registerCustomBlendPlaylist) and queue name. A one-friend custom blend uses that friend's
  name. "Custom Blend" stays only as the empty-state card label ("Pick friends").
- Cover pictures: the ticked friends' avatars (overlapping row, 28dp, ring in
  surfaceContainerHighest) move from TopEnd to **BottomCenter** with 8dp bottom padding, on
  custom blend cards and the custom blend page cover. Tapping them opens the friend picker.
- Friend blends (friend-on-friend, 10.7 "both"): the page gets the same deck. The cover picture
  stays one avatar, also moved to BottomCenter for consistency.
- Blend page deck (PlaylistDetailScreen second row, E41) when the playlist id is a blend:
  [friends IconButton (no text, Icons.Rounded.Group)] [Edit (centred)] [Filter/Sort
  IconButton]. The row is symmetric so Edit is exactly centred (the two side buttons are the
  same size). Friends button / avatars -> the picker sheet.
- First run (no custom blend saved yet): tapping the custom card opens the picker sheet with
  only the friend list + one "Build blend" button. The Open/Play pair is removed (E39).
  Build -> the sheet closes -> the card plays the building animation (below) -> when ready,
  navigate to the built blend page.
- Returning (a custom blend exists): tapping the card opens the built blend page directly.
  Changing friends = the friends button/avatars on the page; the page rebuilds in place with
  the same building animation on the cover.
- Building animation (item 11, also friend blend cards while isBuilding): the whole card (cover
  + text) shows a shimmering sweep across a tonal container (tertiaryContainer) with the
  members' avatars gathering to the centre, plus "Building…". When the songs arrive, the card
  morphs into the built card: the cover crossfades to the mosaic, avatars settle bottom-centre,
  and the title AnimatedContent goes "Building…" -> initials.

Motion:
- Shimmer: infiniteRepeatable tween(DurationLong2 * 3, Standard), drawn in drawBehind from a
  State<Float> (no recomposition per frame).
- Built morph: cover Crossfade tween(DurationMedium2, Emphasized). Avatars move with the
  motionScheme spatial spring (graphicsLayer translation, no layout animation).
- Navigate to the page after the morph settles (or immediately with reduced motion).
- Interruption: leaving the screen mid-build cancels nothing in the data layer, and the card
  shows the current state on return.
- Reduced motion: no shimmer, static "Building…" and an instant swap.

Accessibility: the friends button is "Choose blend friends". The avatar row is one click target
with the same label. Building cards announce "Building blend with …" via liveRegion Polite.

Acceptance criteria:
- [ ] Pick Jack, Mia, Kai -> the card and page are titled "J · M · K".
- [ ] Avatars sit bottom-centre and the cover art is still visible. Tapping them opens the
      picker.
- [ ] First time: list -> Build -> card building animation -> blend page opens.
- [ ] Second time: the card opens the page directly. The deck is [friends][Edit][filter], with
      Edit centred.
```

### I. Queue: History | Heard side by side, Heard playback fix, long-press (item 12)

```
Goal: two half-width buttons, History and Heard. Heard only shows while there are heard
songs. Opening one turns the OTHER half into a styled Clear button for the opened list. No
close buttons; tap the opened button again to close. Heard rows look and behave like
History rows, with approve/next added. Heard songs actually play. Long-press any card opens
its options.

Scope: QueueBottomSheet.kt (E42, E43), NowPlayingNotificationListener.kt (E44),
AmbientSuggestionController.kt (E45), PlayerViewModel.kt heard actions (E46),
HeardSongsRepository.

I-1 Playback fix (root cause E44):
- Add a shared HeardSongResolver (@Singleton, @Inject), pulled out of
  AmbientSuggestionController.resolveSuggestion (E45): local searchSongs with a 750 ms timeout ->
  AmbientSongMatcher.bestMatch; else youTubeRepository.searchSongs("title artist") with an
  8 s timeout -> bestMatch.
- NowPlayingNotificationListener resolves through it before addOrUpvote. Unresolvable ->
  don't add (it's still recorded in RecentlyHeard, as today).
- Defensive: approve/next (E46) check playability (contentUriString/path/youtubeId present).
  If missing, resolve first, toast "Couldn't find <title>" on failure, and never queue an
  empty song.

I-2 UI:
- Replace HeardSongsSection (E42) and the full-width QueueHistoryToggle (E43) with one
  QueueHistoryHeardBar row: [History (weight 1)] [Heard (weight 1)].
  - Only history: History full width. Only heard: Heard full width. Neither: no row.
  - History open: the right half becomes "Clear history" (FilledTonalButton,
    errorContainer/onErrorContainer, DeleteSweep icon), in the same 50% slot.
  - Heard open: the left half becomes "Clear heard" in the same style.
  - The open button gets the selected look (secondaryContainer) with an ExpandMore/Less chevron.
    Tapping it closes. No ✕ anywhere.
  - Only one list open at a time.
- Heard list rows: QueuePlaylistSongItem like the history rows (E43), not the card style.
  Trailing actions: ✓ (add to queue) and Next as small tonal icon buttons. Swipe to dismiss
  (the old ✕). Tap = play now (like history's playFromHistory). 🔥 count as E33.
- Heard disappears when the list empties (including after Clear).
- Long-press: history rows already open options (E43). Heard rows get the same
  onLongClick -> onSongInfoClick(song) (only after the song is resolved).
- Rename the label "Heard Suggestions" to "Heard".
- Landscape (E43 :1294): same bar in the left column.

Motion:
- Half-to-Clear swap: AnimatedContent per slot, fadeIn+scaleIn(0.92) tween(DurationShort4,
  EmphasizedDecelerate) togetherWith fadeOut tween(DurationShort2, EmphasizedAccelerate).
- Heard appearing or disappearing: the History button's width animates via the motionScheme
  spatial spring, with Heard fading in/out (DurationShort4 in, DurationShort3 out).
- Rows: Modifier.animateItem(). The list keeps the existing "grow upward from the button"
  anchoring (toggleHistory, E43 :392-413).
- Reduced motion: instant swaps.

Accessibility: both halves are ≥ 48dp tall. Selected state is exposed via stateDescription
"Open"/"Closed". Clear buttons are labelled "Clear history" / "Clear heard songs".

Acceptance criteria:
- [ ] With heard songs: History | Heard side by side. Without: History full width.
- [ ] Open History -> the right half is a red-tinted "Clear history". Tap History -> closes.
- [ ] Open Heard -> the left half is "Clear heard". Rows look like history rows.
- [ ] A Pixel Now Playing song approved from Heard plays (it was failing every time).
- [ ] Long-press a heard or history row -> the song options sheet.
```

### J. Notification / media-controls heart fills when liked (item 13)

```
Goal: the heart in Android's media controls fills when the song is liked (from the app, the
notification, or the widget) and empties when unliked.

Scope: MusicService.kt buildMediaButtonPreferences (E1), onConnect (E5), onCustomCommand
(:756), MusicNotificationProvider.kt.

Likely root cause: the Like button keeps one command action in both states (E1). System UI
media controls key custom actions by action string and keep the old icon. Shuffle works
because its action changes with its state (E2). The data side is already right (E3, E4).

Spec:
- Add CUSTOM_COMMAND_LIKE_ON / CUSTOM_COMMAND_LIKE_OFF (MusicNotificationProvider) and register
  both in onConnect.
- Build the button with LIKE_OFF when liked (filled heart) and LIKE_ON when not (empty heart).
- onCustomCommand: LIKE_ON -> setCurrentSongFavoriteState(true), LIKE_OFF -> (false).
  CUSTOM_COMMAND_LIKE stays as a toggle for old controllers/widgets.
- Display name "Like" / "Unlike" to match the state.

Don't: call onUpdateNotification manually (see the comment at :1952-1957).

Acceptance criteria:
- [ ] Like from the notification: the heart fills within about 1 s. Unlike: it empties.
- [ ] Like inside the app: the notification heart fills.
- [ ] Skip to a liked song: filled. Skip to an unliked one: empty.
```

---

## 3. Plan (steps trace to the prompts above)

**J. Heart**
1. Add `CUSTOM_COMMAND_LIKE_ON/OFF` to `MusicNotificationProvider.kt` (E5). Done when: both constants exist.
2. Register both in `onConnect` (`MusicService.kt:629-645`). Done when: both are in `customCommands`.
3. Pick the action by state in `buildMediaButtonPreferences` (E1), and set the display name. Done when: the filled heart pairs with LIKE_OFF.
4. Handle both in `onCustomCommand` next to `:756`. Done when: each calls `setCurrentSongFavoriteState` with a fixed target.

**E. Loader**
5. In `FullPlayerContent.kt:924`, feed audio buffering into `isLoadingProvider` with a 250 ms debounce (E8). Done when: video is unchanged and audio buffering reaches the button.
6. Delete the chip block `:2196-2239` (E6) and its now-unused imports. Done when: grep finds no `LoadingIndicator` chip near the lyrics row.
7. Change the content description in `AnimatedPlaybackControls.kt:286` to "Loading" via a string resource (E7). Done when: the string exists in `strings_player.xml`.

**I-1. Heard playback fix**
8. Extract `HeardSongResolver` from `AmbientSuggestionController.resolveSuggestion` (E45) into `data/recognition/ambient/` (Hilt `@Singleton`). Done when: the controller calls it with no behaviour change.
9. Use it in `NowPlayingNotificationListener.handle` before `addOrUpvote` (E44). Done when: no `Song.emptySong()` reaches `HeardSongsRepository`.
10. Add the guard in `approveHeardSong` / `playNextHeardSong` (E46). Done when: an unplayable song is resolved or refused with a toast.

**A. Visualisers**
11. Add the self-reading `isPlaying` overload and token motion to `PlaylistPlayingBadge` (E9). Done when: the old call sites still compile by signature (default params).
12. Extract `blendPlaylistId` / `customBlendPlaylistId` and add `FRIENDS_MIX_PLAYLIST_ID` (E16, E17). Done when: the register* functions use them.
13. Pass the playlistIds at `FriendsScreen.kt:285` and `:395` (E15, E16).
14. Badge on SpeedDialGrid playlist tiles (E13), FriendPlaylistCard moved to BottomEnd (E12), BlendCard/CustomBlendCard (E16), and the playlist hero cover (E14).
15. FriendsMixButton: trailing icon swap plus tap-to-pause while playing (E15).

**B. Home grid**
16. Split `ownPlaylists` by `friendId` and keep the friend lists separate (E18). Pass them to `SpeedDialGrid` and show them only for the selected friend (E19). Keep pinned friend items in the default grid.
17. Update the friend-filter availability so friends with only hidden items still get a picture (E19).

**F. Friend history**
18. Write `FriendPlayMerge.kt` (pure) and `FriendPlayMergeTest` under `app/src/test/...` with the 5 cases. If `app/src/test` isn't reachable, say so.
19. Add `lastSeenAt` / `pausedSeen` to `FriendTrack` and its `checkedTrack` rebuild (E30). Keep the Gson field names stable.
20. Use the merge in `report()` (E30) and collapse stored history once on load.
21. Duration fill: only if `SpotifyWebLibrary` already has a track lookup (read it first). Otherwise the 3-min fallback stays.
22. `FriendHistorySheet`: per-card 🔥 n plus a11y, and the merged plays count (E32, E33).

**D. Adjust**
23. Add `animatedReset` to `CoverAdjustSlider` (an Animatable emitting rounded values, cancellable by drag) (E25, E26).
24. Split the slider into parts: `AdjustRuler` (no pill), `AdjustValueBubble`, `AdjustResetButton` (E25).
25. Hoist `SliderTarget` out of `CoverFiltersPanel` and remove the in-panel slider slot (E27).
26. `PlaylistCoverAdjustContent`: put the ruler above the bottom row, make the row [value][Apply][reset], and hide the divider while a slider is open (E28).

**G. Playlist page / options / queueing**
27. Edit toggle plus the selected look (E34).
28. `QueueEntryMetadata`: add `groupId` / `groupName` / `groupPlaylistId` to the extras, with read/attach (E36).
29. `PlaybackDispatchStateHolder.queuePlaylist(placement)` plus the PlayerViewModel wrapper (E36).
30. Options sheet layout: Queue|Soon|Next, Auto|Export, Delete row, and the Soon panel with its slider (E35). Shorten `playlist_action_export_playlist` to "Export". New strings go in `strings_screens.xml`.
31. Queue turnover card row plus "remove group" (QueueBottomSheet list).
32. On transition, switch the source name and NowPlayingPlaylist (`MediaControllerSyncStateHolder.kt:688`). Animate `QueueSourceBadge` (E37).

**H. Blends**
33. `customBlendTitle()` (pure) plus a test, used in the register function, card and queue name (E38).
34. Move the avatars to BottomCenter on the cards and the page cover, and make them clickable to open the picker (E38).
35. Blend page deck [friends][Edit][filter], symmetric (E41).
36. Picker sheet: friend list plus Build only. Route the card tap to the built page when a blend exists (E39).
37. Building card animation and the morph to the built card, then navigate (E40).

**I-2. History | Heard UI**
38. `QueueHistoryHeardBar` with the slot-swapping Clear buttons (E42, E43), in portrait and landscape.
39. Heard rows as `QueuePlaylistSongItem` with ✓/Next, swipe-dismiss and long-press options. Remove `HeardSongsSection`/`HeardSongItemCard` once nothing uses them.

**C. Download quality**
40. `DownloadQualityStore` plus `classifyDownloadQuality` (pure) and a test (E20, E23).
41. Thread `quality` through `DownloadQueue.enqueueAll` to `downloadSong` (E21, E22), and record it on success.
42. Safe re-download (new file first, then swap, then delete the old one). Read `downloadLocked` first to see how it skips already-downloaded songs.
43. `PlaylistViewModel.qualityCounts` and `downloadPlaylist(songs, quality, wifiOnly, alsoChange)`.
44. Download sheet UI replacing the AlertDialog and the "Downloaded" dialog (E24).

**Each step:** re-read the file right before editing, keep CRLF line endings, and keep edits minimal.
**After each area:** ktlint parse (errors only), a brace-balance script, an `R.string` existence check, and a duplicate-resource grep.

---

## 4. Verification

| Check | Status |
|---|---|
| Compiled | **no** (you run `./gradlew :app:compileDebugKotlin`, then `./gradlew :app:testDebugUnitTest`) |
| Run on device | **no** |
| Static checks the implementing session must run | ktlint parse errors only, brace balance, `R.*` references resolve, no duplicate string names |
| New unit tests (not run) | FriendPlayMergeTest, CustomBlendTitleTest, DownloadQualityClassifyTest |

**Manual checks, mapped to the acceptance criteria:**
- J: like/unlike from the app, the notification, and the lock-screen controls. Skip between liked and unliked songs.
- E: stream a cold song, seek a downloaded one, and use video mode.
- A: every badge case in A's checklist. Pause/resume. Animator scale 0x and 0.5x.
- B: default grid, friend filter on/off, a pinned friend playlist.
- F: a friend pauses/resumes, loops a song, and the app restarts with old duplicates.
- D: each tool, Strength, the reset glide, a drag interrupting the reset, back gesture with the slider open. Light/dark.
- G: Edit toggle, all three queue placements, the Soon slider at both ends, the title animation at the turnover, removing a group.
- H: first run vs returning, the 5+ friends title, the building animation then auto-navigate, reduced motion.
- I: heard and history combos, both Clear buttons, a Now Playing song approved and played, long-press.
- C: new playlist at Low, a mixed upgrade, Wi-Fi drop mid-upgrade.
- Everything with album-art colours and Dynamic Color.

**Needs on-device testing:** J (System UI behaviour is the hypothesis), E (buffering timing), F (real Spotify feed timing), D (layout geometry), G (queue index maths with a running mix), H (animations), C (file swap, storage).

---

## 5. Risks

- **J is a hypothesis.** It's strongly suggested by Shuffle working with per-state actions (E2), but only a device can confirm it. If the heart still doesn't fill, the next check is whether `mediaId` differs from the favourites id for stream songs.
- **The `QueueEntryMetadata` extras (G)** ride on MediaItems and get read by the mix code. The new keys must default safely, so old items read as "no group".
- **Mix runtime (G):** a running Smart/Continuous mix may re-plan around the inserted block. `pinned = true` should protect it (E36), but test it.
- **Friend history merge (F)** rewrites the stored file once on load. If the merge is wrong, history is lost. Keep a one-time backup of `friend-activity-v1.json` before the first merge.
- **Download re-download (C)** touches files and MediaStore. A failure mid-swap must leave the old file. That needs the most careful test.
- **QueueBottomSheet.kt is 3,577 lines.** Edit only the named blocks, and check landscape (`:1260-1310`) separately.
- **PlaylistDetailScreen sheet (G)** shares `closeOptionsSheet` and the delete dialog. Keep that wiring.
- **Badge per tile (A)** adds a `NowPlayingPlaylist` collector per tile. It's cheap (one StateFlow), but keep `isPlaying` reads inside the badge, not the grid.

## 6. Assumptions

- Item 2: "user made" means everything without `friendId` (your answer). Affects step 16.
- 10.5: Auto is always shown, disabled until something is downloaded (today it's hidden, E35). Affects step 30.
- 10.5: queuing a playlist keeps duplicates that already sit elsewhere in Up next (the block stays whole). Affects step 29.
- 10.5: Soon's slider range is 0..(upcoming count), and the default is where single-song Play Soon would land (E36). Affects step 30.
- 10.7: "filter button" means the existing Sort icon (E41). Affects step 35.
- 10.7: the initials separator is " · ", capped at 5 plus "+n". Affects step 33.
- 12: tapping a heard row plays it now (like history). ✓ queues it and Next plays it next. Swipe dismisses. Affects step 39.
- 9: without a Spotify track-length lookup already in `SpotifyWebLibrary`, durations use the 3-minute fallback. Very short or very long songs may merge or split slightly wrong. Affects step 21.
- C: High/Medium/Low thresholds for inferring older downloads (opus ≥ 120 kbps / AAC / < 90 kbps) are heuristics. Unknowns show under no card, with "n unknown" text. Affects step 40.
