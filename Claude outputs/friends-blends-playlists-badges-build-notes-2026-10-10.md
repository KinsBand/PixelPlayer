# Build notes: Friends screen & blends, playlist creation & multi-select, song-count badges (2026-10-10)

This implements the spec "Friends Screen & Blend Interactions / New Playlists & Multi-Selection Revamp / Universal Track Count Badging". The code was written straight into `PixelPlayer-Clean-`.

**Compiled: no. Run on device: no. Tests: added, not run.**

## What changed, by item

| Spec item | What it does now | Decisions / root cause |
|---|---|---|
| Mutual "Friends" badge | An 18 dp `primaryContainer` pill ([group icon] Friends) appears next to the name on friend rows, in the history sheet header, and on followers/following cards when you and that person follow each other on Spotify. | New `FriendSocialGraph` (`data/social`) reads who you follow and who follows you (Spotify `user-profile-view/v3/profile/{me}/following` and `/followers`). It's cached for 10 min and refreshed on pull-to-refresh. `FriendUi.isMutual` is set in `FriendsViewModel.friends`. Spotify friends only. |
| History header stats + Followers/Following view | The "N plays this week" line is now `[♪] N this week · [group] followers · [search] following`. The last two are pills that switch the sheet to a Followers or Following list, with a back arrow (system Back works too). Each card has a 44 dp picture, the name + Friends badge, "[group] n followers", and **Follow / Following / Friends**. Loading uses the expressive indicator. If loading fails: "Unable to load …" + Retry. | Counts come from the friend's profile (`followers_count`, and `following_count` when Spotify sends it, otherwise "–"). **Follow here means your real Spotify account**: it uses Spotify's documented Web API `PUT/DELETE /v1/me/following?type=user`. `SpotifyPathfinder` gained PUT/DELETE for this. The history sheet's own Follow button is still PixelPlayer's "follow along" (their new songs play next); it doesn't follow on Spotify. |
| Playlist card badges + compact time | Friend playlist cards show: the description (grey, 1 line) when known; `[♪] 42 · 2h 14m`; `[↻] 3d · [♥] saves`; "By {curator}" when someone other than the friend made it; and a collaborative badge beside the platform badge. There are no "Updated"/"ago" words. | `FriendPublicPlaylist` / `FriendPlaylistUi` gained `collaborative`, `ownerName`, `lastUpdated`, `saves`, `description`. **Spotify doesn't report an edit time** for public playlists, so `lastUpdated` = when PixelPlayer saw the song count change between polls (set in `FriendActivityRepository.report`). It starts empty and fills in over time. Collaborative / saves / description are parsed only if Spotify's profile response includes them (`is_collaborative`, `followers_count`, `description`); this is unverified, so they may never appear. Curator ("By Spotify") uses `owner_name`, which is already parsed. |
| Playlist card action cleanup | The heart button is gone from the card face. Long press pins/unpins with a haptic tick, and TalkBack reads "Pinned to your playlists" / "Not pinned". Tap opens it; an unsaved public playlist is saved first, as before. | `CollectionCard` `onAction`/`actionContent` are no longer passed. |
| Blend liking / pinning | The playlist page heart now also shows on blend pages (friend blends and the custom blend). Pinned blends are listed in Your playlists as today's mix (still rebuilt daily). Tapping one opens the current blend page. They can't be selected for delete/merge. | **No Room change.** Pins are stored in SharedPreferences (`PinnedBlends`, same pattern as `CustomBlendSelection`), which also covers the offline case. `FriendsViewModel.pinnedBlendPlaylists` builds and registers the day's blend pages only when something is pinned. |
| Blend contributor sticky filter | On blend pages, a chip row (All · You · each friend) sits under the header and sticks below the top bar once you scroll past it, turning `surfaceContainerHigh`. Picking a chip filters the list, and tapping a song then plays only that filtered list. If a filter has no songs: "No songs contributed by X" + Clear filter. | 1-on-1 blends now record whose side each song came from (`BlendMix.contributors`: `me` / friend id / `both`). Shared songs show under both people. The custom blend uses its existing `contributors`. |
| Visualizer badge centering | The bars fill the canvas and are centred (`startX = (w − totalBarsW)/2`). | Uses the exact formula from the spec. Side effect: bars are about 55% wider everywhere `PlayingEqIcon` is used (queue, lyrics, cards). If that's too chunky, the alternative is to keep the old bar width and only centre it. |
| History row playback lifecycle | Tapping a row: a primary trace runs round the card, with a primary scrim and the expressive indicator on the cover. Once playing: green bars over a 35% black scrim. On failure: error tint, outline and 4 dp shake. | Reuses `songStartFeedback` / `SongStartReducer`. The playing overlay compares against the current song id. Catalog (Spotify) songs matched to YouTube audio get a different id, so their "playing" overlay may not show (the starting/failed states do). |
| Actions shrink + weekly artists | The row is Shuffle · this week's artists (40 dp pictures, tooltip with the name, tap opens the artist) · Follow. Once the list scrolls, Shuffle and Follow shrink to 48 dp circles and the picture row widens. With fewer than 2 artists, the two buttons share the row. | Artists come from the main artist of each play, most played first, with the newest cover (`FriendWeeklyArtists`, pure). |
| Pull-to-refresh + loader | The Friends screen has `PullToRefreshBox` with the expressive loading indicator. Refresh polls friend activity and playlists once and re-reads the follow graph. Song-card cover spinners (EnhancedSongListItem, history rows) now use the same `LoadingIndicator` (`SongCoverLoadingIndicator`). | New `FriendActivityRepository.refreshNow()`. It runs beside the normal polling loop without changing its rhythm. |
| Like + marquee on history rows | A 20 dp heart sits on each row (48 dp target). A song PixelPlayer can't match yet opens Search instead. Long titles scroll (`basicMarquee`, 1.2 s delay). With reduced motion, titles ellipsize instead. | |
| Long-press selection | There's a haptic tick when selection starts. Rename only shows with exactly 1 selected. ✕ is "Deselect all". | The rest (check badges, batch bar, Back exits, auto-clean on removal) already existed. |
| Create playlist header | The title is `titleLarge` / GoogleSansRounded / SemiBold with the Edit screen's spring transition. The `scaleX = 1.2` stretch is removed. | |
| Unified Add songs | Create playlist step 1 now uses the same `SongPickerContent` as adding to a playlist: search, Favourites / Local / Cloud chips, checkmarks, and the now-playing quick-add card docked beside the 56 dp confirm button. A search with no results shows a "Clear search" button. | `SongPickerContent` gained `showTitle`, `modifier`, `confirmContentDescription`. The create screen's own bottom bar is removed. |
| Multi-selection sheet revamp | Header: the selected covers overlap. Tap one to act on it alone (2.5 dp primary outline), tap again for all. The title reads "1 playlist selected" / "N playlists selected". With 1 playlist active there's a row of Add to queue · Play next · Soon. Then a full-width Delete (`errorContainer`), and Merge (2+ active) · Share. Share morphs in place into: the platform link button (Spotify / YT Music / Apple Music playlists), Share file (.m3u) or Share ZIP (2+), and Back. Export is gone. | Callbacks now take the active playlists (`onDelete`, `onMerge`, `onShareFiles`, `onQueue`). Both callers (Library, Search) were updated. Soon = after the next 3 songs (`AfterUpcoming(3)`). Play next uses the PlaylistPlay icon, because the spec gave the same glyph for Queue and Next. |
| `CompactCountBadge` everywhere | `[♪] n` (13 dp note, `labelMedium` or the row's own style) replaces "n songs/tracks" on: Your playlists rows, friend playlist cards, blend + custom blend cards, the Daily Mix bar, album grid + list, and artist rows. TalkBack still says "n songs". | New `presentation/components/CompactMeta.kt` (badge, Friends pill, pure formatters). Artist rows keep `CloudAwareText` so the cloud mark still renders. |
| Contextual dates | Playlists sorted by date created show `[♪] 42 · 10/10/26`. Albums by release year show `· 2026`, and by date added show `· dd/MM/yy`. Unknown values (0) are omitted. | Album `dateAdded` comes in seconds from MediaStore and in ms from other sources. Values that are clearly seconds are scaled up. |

## Files touched

**New**
- `data/social/FriendSocialGraph.kt`
- `data/social/PinnedBlends.kt`
- `data/social/FriendWeeklyArtists.kt`
- `presentation/components/CompactMeta.kt`
- `presentation/components/FriendHistorySheet.kt`: moved out of `PlaylistsHub.kt` and rebuilt

**New tests (not run)**
- `test/.../presentation/components/CompactMetaTest.kt`
- `test/.../presentation/components/BlendFilterTest.kt`
- `test/.../data/social/FriendSocialGraphTest.kt`
- `test/.../data/social/FriendWeeklyArtistsTest.kt`
- `test/.../data/social/BlendMixContributorsTest.kt`

**Edited**
- `data/spotify/web/SpotifyPathfinder.kt` (PUT/DELETE)
- `data/spotify/web/SpotifySocialRepository.kt`
- `data/spotify/SpotifyRepository.kt` (`SpotifyPlaylist` gains `saves`, `description` with defaults)
- `data/spotify/web/SpotifyFriendActivitySource.kt`
- `data/social/FriendActivity.kt`
- `data/social/BlendMix.kt`
- `presentation/viewmodel/FriendsViewModel.kt`
- `presentation/components/PlaylistsHub.kt`
- `presentation/screens/FriendsScreen.kt`
- `presentation/components/BlendsRow.kt`
- `presentation/screens/PlaylistDetailScreen.kt`
- `presentation/components/PlaylistContainer.kt`
- `presentation/components/SongPickerBottomSheet.kt`
- `presentation/screens/CreatePlaylistScreen.kt`
- `presentation/components/PlaylistMultiSelectionBottomSheet.kt` (rewritten)
- `presentation/screens/LibraryScreen.kt`
- `presentation/screens/SearchScreen.kt`
- `presentation/screens/LibraryMediaTabs.kt`
- `presentation/components/DailyMixLauncher.kt`
- `presentation/components/SongStartFeedback.kt`
- `presentation/components/subcomps/EnhancedSongListItem.kt`
- `presentation/components/subcomps/PlayingEqIcon.kt`
- `res/values/strings_library.xml` (52 strings, 7 plurals)

**Not touched:** Room / schema, manifest, permissions, Gradle, ProGuard (new `data.social` classes are covered by the existing `-keep class com.theveloper.pixelplay.data.social.**`), settings registry, backup. No new dependencies.

## Verification

| Check | Result |
|---|---|
| Compiled | **no**. Run `./gradlew :app:compileDebugKotlin`, then `./gradlew :app:testDebugUnitTest` |
| Run on device | **no** |
| ktlint 1.3.1 parse, 27 touched main files + 5 new tests | 0 parse errors (style warnings ignored) |
| XML well-formed (`strings_library.xml`) | pass |
| Every `R.string` / `R.plurals` / `R.drawable` used in touched files exists | pass |
| Duplicate string / plural names across `res/values` | none |
| Line endings preserved (4 CRLF files stayed CRLF, rest LF, none mixed) | pass |

## Needs your testing

- **Spotify social (highest risk: undocumented endpoints):**
  - Check that the Friends badge shows for someone you follow who follows you back, and not for one-way follows.
  - Open a friend's history: the followers and following counts appear. Tap each and the list loads, then Back returns to the week.
  - Try a private profile, then airplane mode (expect "Unable to load…" and Retry).
  - **Follow / unfollow from a list**, then check the Spotify app. This uses `api.spotify.com/v1/me/following` with the web-player token. If Spotify rejects it (403), the button won't stick and a notice shows.
- **Playlist cards:** "By Spotify" on an editorial playlist a friend saved. ↻ time appears only after a friend's playlist changes size while PixelPlayer is polling. Saves, description and the collaborative badge appear only if Spotify sends those fields.
- **History sheet:**
  - Scroll so the buttons shrink to circles, with the artists row widening.
  - Tap an artist picture.
  - Tap songs: the starting trace, then the bars, or the failed shake.
  - Use the heart on a matched song and on an unmatched one (which goes to Search).
  - Check marquee on long titles, and reduced motion (titles ellipsize).
- **Pull to refresh** on Friends, and check the indicator matches the song-card spinner.
- **Blends:**
  - Heart on a friend blend and on the custom blend. Each shows in Your playlists, opens today's mix, and still shows after an app restart and the next day.
  - The filter chips: You, the friend, shared songs under both, and the sticky row under the top bar.
  - A filter with no songs (shows Clear filter).
- **Create playlist:** the title looks the same as Edit. On Add songs: now-playing quick add, chips, search with no results (Clear search), then confirm creates.
- **Multi-select sheet:**
  - Select 1 and then 3 playlists.
  - Tap a cover to focus it, and tap again to go back to all.
  - Queue / Next / Soon on one.
  - Delete a focused one (only that one).
  - Merge with 2+.
  - Share → .m3u for 1 and .zip for 2+.
  - The platform link on a Spotify playlist.
- **Badges:** playlists, albums (grid and list), artists, blends and the Daily Mix bar. Sort playlists by date created, and albums by year and by date added.
- **Visualizer bars** are now wider; check they still look right in the queue, the lyrics and on covers.
- **Everywhere:** animator scale 0× and 0.5×, light and dark themes, TalkBack on the badges.

## Open items / limits

- **Followers/following and the follow toggle are Spotify-only.** YouTube Music / Apple Music friends show no counts and no badge.
- **Two meanings of "Follow":** the history sheet's Follow is still PixelPlayer's follow-along. The follow buttons on followers/following cards are real Spotify follows. If you want the sheet button to be a Spotify follow too, that's a separate change.
- **`lastUpdated` is observed, not reported.** Spotify's public-profile playlists carry no edit time.
- **Pinned blends aren't in Backup** (neither is the custom blend selection). Add both to `BackupManager` if you want them restored.
- **Search screen Merge** still just closes the sheet, as before (it had no merge dialog). Library's Merge opens the merge dialog.
- **Queueing from the multi-select sheet** loads songs with `PlaylistViewModel.getPlaylistsWithSongs`, the same as Share. Connected or friend playlists whose songs aren't in the local DB may queue fewer songs.
- **Catalog songs in the history sheet:** the "playing here" overlay compares song ids, so a Spotify song playing as matched YouTube audio may not show the bars.

## Follow-up (2026-10-10, round 2)

| # | Change | Notes |
|---|---|---|
| 1 | The word "Blend" is gone from the blend cards, custom blend card and pinned blends in Your playlists. | |
| 1.1 | On the Friends list, the mutual badge is just the group icon (no "Friends" text). | It still reads "You follow each other" to TalkBack. The history sheet and people cards keep the text pill. |
| 1.2 | Playlist pages read `[♪] 42 · 1h 59m`, and a friend's playlist adds `· [♥] saves` when known. The Daily Mix page, the mix cards in the Daily Mix sheet, the "View full Daily Mix" row and the daypart hero all use `[♪] n` (no "tracks"), with times in `1h 59m` style. Blend card times also use `1h 59m`. | New `CompactMeta.duration`. `GeneratedMix.subtitle` no longer contains "n tracks"; the card draws the count. |
| 1.3 | History sheet: the shrinking middle row is gone. Shuffle and Follow are side by side, with this week's artists in their own row below. | |
| 1.4 | Blend pages: the filter row is now in the Your Music style, under the friends · Edit · filter deck, and sticky. It has Liked, then You and each friend, then the vibe presets by category (tap again to turn off). It shows on every blend page. | Vibes use the same `MusicVibeFilters.score ≥ MATCH_THRESHOLD` test as Your Music. Typed custom filters (pull to type) aren't on blend pages. |
| 1.5 | Each blend song has a badge at the end of its row: your picture (you brought it), the friend's picture (theirs), or a sparkle (a song you both play). This works in the custom blend too. | Your picture is your Spotify profile picture (`FriendSocialGraph.myAvatarUrl`). It falls back to "Y" until loaded. 1-on-1 shared songs come from `contributors == both`; custom blends gained `CustomBlend.sharedSongIds`. |
| 2 | Profile: "This phone" and "In your account" are two tiles side by side in one row. | |
| 2.1 | The profile picture (Profile header and the Settings card) comes from the signed-in email via **Gravatar**. It's looked up by a SHA-256 hash of the email, never the email itself; with no picture or offline, the initial stays. | This is a new outbound request to gravatar.com (hash only), loaded through Coil. Say if you'd rather not use it. |
| 3 | Liking friend playlists: the heart flips at once, cards show a filled heart badge top-left when pinned, and failures flip it back with a notice. | Likely cause: pinning an unsaved public playlist first saves it, which reads every song (several seconds) with no feedback, and the card had no pinned mark. Pins now go through `pendingPins` / `pinnedPlaylistKeys`, keyed `SOURCE:remoteId`, which matches unsaved ones too. If it still fails, the notice text says why. |
| 4 | The lyrics loading trace shows with cover lyrics on (portrait and landscape). | The cover-lyrics border was drawn in the same accent colour right under the trace, hiding it. The border now dims to 30% while lyrics load. |
| 5 | **Follow:** this now goes through Spotify's own social-graph endpoint (`spclient…/socialgraph/v2/following`, POST follow / DELETE unfollow), which the Spotify apps use, falling back to the Web API. The button flips at once and flips back if refused. Tapping **Following** or **Friends** asks "Unfollow X?" first. The tick icon is removed. Lists are sorted Friends → Following → everyone else. **Sheet:** the header (picture, name, badge, last played, `[♪] n this week`, followers, following) stays on top in Followers/Following. The open list's pill is highlighted, and tapping it again goes back. Every mode fills the sheet height, so a half-open sheet stays half-open and a full one stays full. | The social-graph endpoint is undocumented, so test it on device. If both endpoints refuse, the notice shows Spotify's message. |

**Files touched (round 2):**
- `SpotifySocialRepository.kt`
- `FriendSocialGraph.kt`
- `BlendMix.kt`
- `FriendsViewModel.kt`
- `CompactMeta.kt`
- `FriendHistorySheet.kt`
- `PlaylistsHub.kt`
- `FriendsScreen.kt`
- `BlendsRow.kt`
- `PlaylistDetailScreen.kt`
- `PlaylistContainer.kt`
- `ProfileScreen.kt`
- `FullPlayerContent.kt`
- `DailyMixScreen.kt`
- `HomeMixFilters.kt`
- `DailyMixLauncher.kt`
- `DaypartMixHero.kt`
- `strings_library.xml` (+9 strings)
- Tests: `CompactMetaTest` (duration), new `GravatarUrlTest`

**Verification:** Compiled: no. Run on device: no. Tests: not run.
- ktlint parse on all 17 touched files + the new test: 0 errors.
- Every `R.string`/`R.plurals` resolves; no duplicate names; strings XML is valid.
- Line endings preserved.

**Needs on-device testing:**
- Follow/unfollow via the new endpoint (check in the Spotify app).
- The Unfollow dialog and the Friends → Following → others order.
- Opening Followers from a half-open and from a full sheet.
- Blend badges (your picture, the friend's, a sparkle on shared songs).
- Blend vibe/Liked/person filters.
- Pinning an unsaved friend playlist (instant heart, then it appears in Your playlists).
- Lyrics trace with cover lyrics on.
- Profile tiles on a narrow phone.
- The Gravatar picture for your email.

## Follow-up (2026-10-10, round 3): artist page, queue, radio history

| # | Change | Notes |
|---|---|---|
| 1 | Artist page: the tick inside the selected All songs sort chip is gone. The selected chip still shows as selected. | Song rows themselves had no tick; the only song-section tick was the sort chip. |
| 1.1 | **Timeline:** now a vertical rail with a primary pill per decade, the decade name and a count, then a node per release. The node is filled when you have songs from it. Each release sits on a rounded card with a 52 dp cover, title and "2019 · Album · [♪] 12" ("[♪] 3/12" in accent when you have some). **Consistency:** every count is now `[♪] n`, with no "tracks"/"songs" words. This covers the All songs count badge, "Show all [♪] n", album / single / latest-release / mix-albums cards, the stats row ("♪ 42 Songs"), and discography completion ("You have [♪] 12/40"). Listening time is "1h 59m" and "Catalogue updated 5m". | New `ReleaseMetaLine` in `ArtistPageSections.kt`. TalkBack still reads "12 songs". |
| 1.2 | The search icon next to All songs opens the app's Search with the artist's name. The inline search bar is removed from the artist page. | The "Show all" songs list still has its own filter field. |
| 2 | Queue: History and Heard always show side by side. An empty one is dimmed with no chevron and doesn't open. | |
| 2.1 | The queue header count is `[♪] n` instead of "n tracks". | |
| 3 | Radio history sheet: "[♪] 5 in session". "Listening for" uses "45m" / "1h 59m". | |

**Files:**
- `ArtistDetailScreen.kt`
- `artist/ArtistPageSections.kt`
- `artist/ArtistMixActions.kt`
- `QueueBottomSheet.kt`
- `player/RadioHistorySheet.kt`

**Verification:** Compiled: no. Run on device: no. ktlint parse: 0 errors on all 5 files. Line endings preserved (`RadioHistorySheet` stays CRLF).

**Needs on-device testing:**
- How the timeline looks: the rail lines up through decade pills and nodes, and ends at the last release.
- Search icon → Search screen with the artist name.
- Queue bar with history but no heard songs (Heard dimmed and not opening).
- The radio history count.

## Follow-up (2026-10-10, round 4): two OutOfMemory crashes

**Crash 1:** `OutOfMemoryError` in `Inflater` during `ConnectedLibraryRepository.sync` → `refreshed`.
- Every sync re-downloaded every manual playlist in full, one after another.
- Opening a friend's row saves all their public playlists (manual friend playlists), so the library could hold dozens of big friend playlists. All of them were re-read on every sync.
- At the same time, the friend activity source read every song of up to 6 friend playlists per poll (every 10–30 s), just to count them.

**Crash 2:** `PatternSyntaxException: U_MEMORY_ALLOCATION_ERROR` in `BlendMixGenerator`.
- `TrackMatching.normalize` / `stripAccents` built 4–5 new ICU `Regex` objects on every call. Blends call them for every song of every friend.
- Blends were also rebuilt every 30 s, because `friends` re-emits on the presence clock.
- Native pattern memory ran out (and the Java heap was already low from crash 1).

**Fixes**

| Area | Change |
|---|---|
| Regex | `TrackMatching` patterns compiled once, with an ASCII fast path in `stripAccents`. Per-song regexes also hoisted in `AdaptiveMix` (genre-tag cache), `MusicVibeFilters` (custom filters), `CollectionKeys`, `RecordingKeys` and `SongUnifier`. |
| Blends | Built from `friendIdentities` (id / name / picture, `distinctUntilChanged`) and `friendPlaylistSongs` (only friend playlists, distinct), with `mapLatest`. They no longer rebuild on the 30 s clock or on unrelated library saves. |
| Sync | A friend's playlist is re-read at most once a day, at most 3 per sync (oldest first). New field `ConnectedPlaylist.fetchedAt` (Gson, default 0). |
| Caps | Friends' playlists keep at most 1,000 songs when added or refreshed (Spotify stops paging at the cap; YouTube / Apple are trimmed). Already-saved oversized ones are trimmed on load. |
| Counting | Friend playlist counts come from the first page (`SpotifyWebLibrary.playlistSummary`). The length is only computed for playlists of 300 songs or fewer. |
| Background saves | Opening a friend's row saves at most 4 of their public playlists, each 1,000 songs or fewer. |
| Last resort | One playlist that still can't fit (OutOfMemoryError while reading it) keeps its previous copy with "too big to load in full", instead of crashing the app. |
| Rule | Memory rules added to `CLAUDE.md` §5, so future sessions don't reintroduce these patterns. |

**Files:**
- `data/spotify/TrackMatching.kt`
- `data/AdaptiveMix.kt`
- `presentation/library/MusicVibeFilters.kt`
- `presentation/viewmodel/FriendsViewModel.kt`
- `data/spotify/web/SpotifyWebLibrary.kt`
- `data/spotify/SpotifyRepository.kt`
- `data/accounts/ConnectedLibraryRepository.kt`
- `data/spotify/web/SpotifyFriendActivitySource.kt`
- `data/library/CollectionKeys.kt`
- `data/library/RecordingKeys.kt`
- `data/library/SongUnifier.kt`
- `CLAUDE.md`
- New test: `test/.../data/spotify/TrackMatchingTest.kt`

**Verification:** Compiled: no. Run on device: no. Tests: not run. ktlint parse: 0 errors on all 11 files. Line endings preserved.

**Needs on-device testing:**
- Open the app, let a sync run, open several friends, and leave Friends open for 10+ minutes. Watch memory in Android Studio's profiler.
- Blends and the custom blend still build.
- A friend playlist over 1,000 songs shows the first 1,000. Opening it still works.
- Matching/search still finds the same songs.
