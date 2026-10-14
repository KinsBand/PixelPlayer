# Phase 1 quick wins: build notes (2026-10-14)

Plan: `roadmap-accounts-artist-player-plan-2026-10-14.md` (Phase 1, P1.1–P1.8).
**Compiled: no. Run on device: no.**

## What changed

| Item | Result |
|---|---|
| P1.1 Defaults + Practice | **No code change needed.** All 7 defaults already match in `UserPreferencesRepository` and `SettingsUiState`. Backup exports all prefs generically. The Practice toggle hides the card (`PlaylistContainer`) and the route pops back (`AppNavigation`). |
| P1.2 Badges | The badge rule already lived in `TrackStatusBadges`. Playlist/queue rows (`QueuePlaylistSongItem`) had their own copy with different colours and no downloading/failed states; they now use `TrackStatusBadges`. Artist song rows now show the same badges (they used to show only a heart for some liked songs) and have a **like button that's always there**. Like state is live from favourites, not the catalogue snapshot. |
| P1.3 Radio button | The list/map button now moves down with the nav bar, the same way the mini player does. `NavBarVisibility.visibilityProgress` is set by MainActivity, and the button reads it in `graphicsLayer` (no recomposition per frame). It never hides. |
| P1.4 Share sheet | **Root cause:** the share sheet used the raw song, but the Details tab uses the filled-in `detailsSong`. Online songs often have a blank album/genre or placeholders ("YouTube Music", "<unknown>"), so the sheet showed "Unknown", blanks or wrong values. Also, the artist card *showed* `displayArtist` but *shared* the raw `artist`. Fix: the sheet uses `detailsSong`, and a new `shareMetaOrNull()` drops blanks and placeholders. A card with no value is left out (the other one fills the row). Album shares as "Album - Artist". Link searches use the cleaned artist. |
| P1.5 Shuffle in Soon | `PlaylistSoonPanel` has a new optional `onConfirmShuffled`. It adds a tonal Shuffle button left of the tick that queues the block in a plain random order. Wired for the playlist page and for playlist multi-select (Library + Search). `PlayerViewModel.queuePlaylist(..., shuffled)`. The toast says "…, shuffled". **Single-song Soon has no shuffle button** (one song has no order). |
| P1.6 Friends song options | Holding a friend's song now opens the normal `SongInfoBottomSheet`. It has all its features: Soon on hold-Next, song radio, add to playlist, details, share, etc. A new `headerExtra` slot under the artist shows `FriendListeningChip` (avatar + "‹name› is listening now" in green with live bars, or "‹name› · 2 h ago"). Tapping the chip opens that friend's history. Play/Next/Queue/Soon still tag the friend in the queue. `FriendTrackSheet` (+ `FriendActionRow`) were removed. Its "Search in PixelPlayer" row is gone; unmatched songs still go straight to search as before. |
| P1.7 Visualiser colour | New `PlayingSource { MINE, FRIEND }` + `playingIndicatorColor()` in `PlayingEqIcon.kt`. Yours = theme primary (over a darkened cover: the light tone of the same colour); a friend live = green. Fixed: the friend history row (green even when *you* were playing it, and its title too), the Friends Mix play button, the friend row's lead bars and text when playing here, artist song rows and Practice covers (were white). Presence dots/follow chips stay green (they're about the friend). |
| P1.8 Refresh | The playlist page uses the M3 Expressive `LoadingIndicator` (same as Friends). Pull-to-refresh is now on for **blend pages** too (re-polls friends' listening, which the blend is built from). Platform playlists (`ConnectedPlaylistsTab`) switched to the same indicator. Plain local playlists have nothing to refresh, so there's no pull there. |

## Files touched (19 + 1 test)
`MainActivity.kt`, `presentation/navigation/NavBarVisibility.kt`, `presentation/screens/radio/RadioScreen.kt`, `presentation/components/QueueBottomSheet.kt`, `presentation/screens/artist/ArtistPageSections.kt`, `presentation/screens/ArtistDetailScreen.kt`, `presentation/components/SongInfoBottomSheet.kt`, `presentation/components/PlaylistSoonPanel.kt`, `presentation/viewmodel/PlayerViewModel.kt`, `presentation/screens/PlaylistDetailScreen.kt`, `presentation/components/PlaylistMultiSelectionBottomSheet.kt`, `presentation/screens/LibraryScreen.kt`, `presentation/screens/SearchScreen.kt`, `presentation/screens/FriendsScreen.kt`, `presentation/components/PlaylistsHub.kt`, `presentation/components/FriendHistorySheet.kt`, `presentation/components/subcomps/PlayingEqIcon.kt`, `presentation/screens/PracticeScreen.kt`, `presentation/screens/ConnectedPlaylistsTab.kt`
New test: `app/src/test/.../presentation/components/ShareMetaTest.kt`

## Verification
| Check | Result |
|---|---|
| Compiled | **No** (no Android SDK here, per project rules) |
| Run on device | **No** |
| ktlint 1.3.1 parse of all 20 files | No parse errors (style warnings only, as before) |
| Line endings | Preserved per file (some are CRLF, `PlaylistMultiSelectionBottomSheet.kt` is CR CR LF, `PlaylistDetailScreen.kt` is mixed) |
| Removed `FriendTrackSheet` | No references left (grep) |
| Unit test `ShareMetaTest` | Written, **not run** |

Things I checked by hand that a compile would catch: composable lambda for `headerExtra` passed via `if/else` (not `?.let`), so the expected type flows into it. The lambda parameter type for the shuffled Soon callback is explicit. `PlaylistBottomSheet` / `editSongMetadata` / `deleteFromDevice` signatures match other callers.

## Please run
`./gradlew :app:compileDebugKotlin` then `./gradlew :app:testDebugUnitTest --tests "*ShareMetaTest*"`

## Needs on-device testing
- Radio: scroll the list. The button should slide down with the nav bar and come back, in step with the mini player, and stay tappable.
- Share sheet on an online (YouTube/Spotify) song from the full player: Genre/Album cards appear once details fill in, and are hidden otherwise.
- Friends: hold a friend's song → normal options with the friend chip. Queue/Next/Soon show the friend tag in the queue. Chip tap opens their history.
- Light theme: your visualiser over cover art (artist rows, friend history) should be the light tone of your wallpaper colour and readable.
- Blend page pull-to-refresh: indicator shows, then stops when polling ends.

## Open items
- Several new labels are hard-coded English ("Like"/"Unlike", "Shuffle in after…", "‹name› is listening now", the "…, shuffled" toast), matching the surrounding code. `res/values` isn't linked, so they can't move to `strings_*.xml` yet.
- Some unused imports were left in `PlaylistsHub.kt` after removing `FriendTrackSheet` (warnings only).
- 9.2 is still blank in the roadmap.
