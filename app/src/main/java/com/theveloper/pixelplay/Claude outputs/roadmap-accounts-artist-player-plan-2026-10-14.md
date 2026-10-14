# PixelPlayer roadmap: Accounts, Library, Artist page, Players, Search links (plan, 2026-10-14)

**Status:** plan only. No code has been changed.
**Code read this session:** `AccountsScreen.kt`, `PlaylistContainer.kt` + `PlaylistsHub.kt` (platform cards, Practice), `ArtistDetailScreen.kt` (tab row + section list), `ArtistEventsTab.kt`, `SetupScreen.kt` (page list, InitialFeaturesPage), `UserPreferencesRepository.kt` (defaults), `PlaylistSoonPanel.kt`, `FriendsScreen.kt` / `FriendHistorySheet.kt` / `FriendTrackSheet`, `RadioScreen.kt` (ViewModeFab), `PlaylistDetailScreen.kt` (MaybePullToRefresh), `data/search/MediaUrlParser.kt`, `data/artist/*`.
**Order agreed:** phased, quick wins first.

---

## Design rules for every item (so it all looks like the rest of the app)

| Thing | Use what already exists |
|---|---|
| Top bars | `CollapsibleCommonTopBar` (Accounts/Settings style), artist keeps `CustomCollapsingTopBar` |
| Headings | `GoogleSansRounded`, titleMedium/titleLarge Bold, `semantics { heading() }` |
| Motion | `MotionTokens` (DurationShort4 + Emphasized), `animateContentSize`, `animateItem()` in lists; reduced-motion snaps |
| Pull to refresh | The Friends one: `PullToRefreshBox` + `PullToRefreshDefaults.LoadingIndicator` (the M3 Expressive shape indicator) |
| Loading | `ArtistSectionReveal` + skeletons (`ArtistSectionLoading.kt`), shimmer skeletons used on Playlist |
| Brand colours | `SpotifyGreen`, `YouTubeRed`, Apple Music icon `ic_source_apple_music`; status green = `FriendsLiveGreen` |
| Song badges | `TrackStatusBadges` + `CloudHeartIcon` (one component, used everywhere) |
| Song options | `SongInfoBottomSheet` (one sheet for the whole app), "Soon" = `PlaylistSoonPanel` |
| Cards | 16–20 dp corners, `surfaceContainer*` roles, `PlatformButton` style for service tiles |
| Text | every new string in `res/values/strings_*.xml` (res isn't in the linked folder, see "Files I can't reach") |

---

## Found while reading (affects the plan)

1. **Four files from 13 Oct are written but not used anywhere:** `data/search/MediaUrlParser.kt` (Spotify + YT Music link parser, already handles `OLAK5uy_` albums), `data/artist/ArtistSocialLinks.kt` (Streaming / Social / Community hub), `data/artist/ArtistAiAssistant.kt` (incl. `BandMember`), `data/artist/MerchPriceConverter.kt` (fixed-rate AUD). No build notes exist for them. I'll review and reuse them rather than start again.
2. **Several items already exist** and only need checking/polish:
   - 4.1 Setup already has `ThemeSelection` and `InitialFeatures` pages with Up-next notification, cutout overlay and Song Search Reader toggles. Missing: the separate "tap camera hole" gesture toggle.
   - 4.2 Defaults in `UserPreferencesRepository` already match: scrollbar shown (true), compact nav off, queue history on, persistent shuffle on, pause at volume 0 on, cast autoplay on (`DISABLE_CAST_AUTOPLAY` false), keep playing in background on. Note: defaults only affect new installs; your current saved values won't change.
   - 4.3 Practice toggle exists (`show_practice_mode`, hides the card and the route in `AppNavigation`).
   - 3.6 AUD conversion exists but is not wired to merch cards.
3. `Library` service cards today: `PlatformPlaylistCards` is a fixed Spotify | YT Music row (no Apple Music, no 0/1/3 layouts).
4. Playlist pull-to-refresh exists (`MaybePullToRefresh`) but uses the plain indicator and is only on for some playlists.
5. Friends long-press opens its own `FriendTrackSheet` instead of `SongInfoBottomSheet`.

---

## Phase 1: Quick wins

**P1.1 Defaults + Practice (4.2, 4.3).** Audit only: confirm each default above, check `SettingsScreen` reset-to-defaults and Backup restore use the same values, confirm Practice off also hides Practice in search/shortcuts.

**P1.2 Song badges everywhere (3.2 badges, 10.1).** Rule (one pure function, unit-tested):
| Song state | Badge |
|---|---|
| Cloud, not liked | Cloud |
| Cloud, liked | Cloud-heart |
| Downloaded/local, not liked | none |
| Downloaded/local, liked | Heart only |
Apply via `TrackStatusBadges` in playlist rows, artist All Songs rows and search rows. Like button stays tappable on every row (3.2 "persistent like").

**P1.3 Radio FAB hides with the nav bar (9.1).** `ViewModeFab` slides down (`graphicsLayer.translationY`, same spec as the bottom nav) when scrolling down, comes back on scroll up. Reads the nav bar's existing hide state so they move together.

**P1.4 Share sheet metadata fix (7.1).** Album / Artist / Genre cards in `ShareSongSheet` sometimes show nothing or the wrong thing. Plan: find where they read `song.album/artist` (online songs carry `artistId = name.hashCode()` and sometimes blank album), fall back through: song fields → `SongInfoBottomSheetViewModel` enriched metadata → parsed artist names → hide the card rather than show blank. Root cause to confirm in code first.

**P1.5 Shuffle in "Soon" (11).** In `PlaylistSoonPanel`, add a shuffle `FilledTonalIconButton` to the left of the confirm button. On = the songs go in random order (unweighted) instead of top-to-bottom of the current filter. Shown only when more than one song is being added. Reaches every caller automatically (song options, queue, search, playlists, radio, multi-select).

**P1.6 Friends use the normal song options (11, second part).** Long-press on a friend's song opens `SongInfoBottomSheet` (with Soon on hold-Next like everywhere else) instead of `FriendTrackSheet`. Under the artist row: a small avatar chip "‹Friend› is listening now" / "played 2 h ago". Friend-only actions (open friend, follow) move into that chip so nothing is lost. `FriendTrackSheet` is removed after its actions are mapped.

**P1.7 Visualiser colour (2.5).** `PlayingEqIcon` on cover art: your own "now playing" uses `MaterialTheme.colorScheme.primary` (wallpaper / Material You); friends keep `FriendsLiveGreen`. 13 call sites; each gets the right role.

**P1.8 Expressive refresh for Blends and Playlists (2.6).** Same `PullToRefreshBox` + `LoadingIndicator` as Friends on Playlist detail (all synced/friend/blend playlists) and the Blends row/screen. Needs a `refresh()` on the blend source; to confirm in code.

## Phase 2: Accounts + Library

**P2.1 Service header (1.2).** Replace the stacked `ConnectedAccountCard`s with one card holding a row of three equal tiles: Spotify, YouTube Music, Apple Music. Connected = green tint + dot, disconnected = neutral grey, tap = connect. Tapping a connected tile reveals a logout `FilledTonalIconButton` directly above it, same width as the tile (animated in, error-tinted, confirm on tap). Each service's playlists/refresh move below the header for the selected tile.

**P2.2 Connect progress (1.1).** During handshake the tile shows a determinate `LinearWavyProgressIndicator` stepped by real stages (open login → token → profile → playlists), with "~10 s" text. Falls back to indeterminate if a stage stalls.

**P2.3 Bottom accordions (1.3, 1.4).** Friends and Hidden Playlists as two half-width tiles at the bottom. Expanding one makes it full width (the other drops to a tile below), animated with `animateContentSize`. In Friends, tapping a friend expands an inline list of their public playlists (tap = playlist page).

**P2.4 Library service card (2.1).** `PlatformPlaylistCards` becomes adaptive: 0 connected = one card with stacked three logos + "Connect" button (opens Accounts); 1 = full width above Practice; 2 = 50/50; 3 = 33/33/33. Adds Apple Music.

**P2.5 Friend playlist sync (2.4).** Followed friends: playlists auto-cache in the background (bounded worker, Wi-Fi/metered policy). Unfollowed: keep the manual Save. Card shows a small sync animation while caching. Opening an uncached playlist goes straight to the Playlist screen with a full skeleton (header + rows) while it loads.

**P2.6 Pickers include friends/blends (2.2).** Every "add to playlist" / playlist picker shows sections: Yours · Blends · Friends' · Shared mixes. Non-editable ones are only offered where it makes sense (play/queue, not "add to").

**P2.7 Live Recent and Liked (2.3).** Make both lists `Flow`-backed straight from Room (`collectAsStateWithLifecycle`), so a like or a play appears instantly. Check which screens still load once.

## Phase 3: Artist page

**P3.1 Sticky compact tabs (3.1).** Move `ArtistTabRow` out of the stats item into a `stickyHeader` that docks under the collapsed top bar. Chip height ~32 dp. Tabs: Music · Events · Merch · Socials (+ About stays at the bottom of Music).

**P3.2 All Songs (3.2).** 6 rows, 6th fades out under a gradient blur. "See more" → 10 rows, button becomes "Show all" (opens existing `ArtistSeeAllPage`), with a "Show less" icon button on its left back to 6. Discography visualiser bottom-right of an album thumb when that album is playing and the album filter is on. Top shuffle = unweighted random over the full catalogue.

**P3.3 Videos + Live (3.3).** "Music videos" carousel with cards styled like playlist cards; tap plays, with a one-tap video/audio switch (reuses `SongVideoSection`). "Live performances" right below, from YouTube search "‹artist› live", filtered by title/duration.

**P3.4 Genres/Moods chips + Artist's Playlists (3.4).** Filter chips over the catalogue sections. One "Artist's playlists" carousel merging Spotify + YT Music, source logo top-right of each card.

**P3.5 Events map in place (3.5).** Drop the List/Map segmented control. Compact map card on the page (OpenFreeMap, attribution kept); tap expands it in-line to ~70% height with pan/zoom; back or a collapse button shrinks it.

**P3.6 Merch in AUD (3.6).** Wire `MerchPriceConverter` into merch cards, showing only "$X AUD". Rates: fetched once a day from a keyless rates service through `NetworkAccessPolicy`, cached; the fixed rates stay as the offline fallback. Merch tab stays visible whenever an official domain is known, even with 0 items (shows "Visit official store").

**P3.7 Socials hub (3.7).** Full-width "Official website" button above the sub-tabs, with a search icon on its left that runs a web search via the device's default search (`ACTION_WEB_SEARCH`). Tabs All · Streaming · Social · Community, row cards like Music/Merch/Events, unlinked services hidden. Data from `ArtistSocialLinks.kt` (sources: MusicBrainz URL relations, Spotify/Deezer where known).

**P3.8 About finale + AI (3.8).** Centred About section last on the page: bio, Band members (photo, name, instrument/role; from MusicBrainz "member of band" + Wikimedia images), then an "Ask about ‹artist›" box. Gemini Nano only (`MlKitNanoEngine`); hidden on phones without it. No network calls from the box.

## Phase 4: Players

**P4.1 Mini player swipe (8.1).** Horizontal drag moves cover + title + artist together with your finger. Swipe left past threshold = next (content exits left, new song enters from right); swipe right = previous (opposite). Below threshold it springs back. Works with the existing `MiniSongWaveState` transition and doesn't fight the vertical sheet drag (direction lock after a few px).

**P4.2 Mini player timeline (8.2).** A thin progress bar along the bottom edge, coloured from the cover palette, running from the very left edge to the very right. Drawn as a clipped path of the mini player's own shape, so it follows rounded corners and morphs when the shape changes. Progress read in `drawBehind` (no recomposition per frame).

## Phase 5: Search, onboarding, profile

**P5.1 Paste links (6.1).** Wire `MediaUrlParser` into the search field. A pasted link opens the exact item:
| Link | Opens |
|---|---|
| Spotify artist | Artist page (by name) |
| Spotify album / playlist | Album / Playlist screen with its tracks |
| Spotify track | The song (play + options) |
| YT Music `OLAK5uy_` playlist | Album |
| YT Music `PL…` playlist | Playlist |
| YT Music `watch?v=` | Song |
Without Spotify sign-in, Spotify links resolve from the public page info (title/artist/type) and are matched; when signed in, the Spotify API gives full tracklists. Unit tests for all 7 example links.

**P5.2 Setup (4.1).** Add the "tap camera hole" gesture toggle next to the island toggle; confirm the theme page offers Light / Dark / System.

**P5.3 Avatar flow (5.1).** Tap profile picture → crop view (`ImageCropView`) with a circular preview; tapping the circle opens the photo picker.

---

## Files I can't reach from here

`res/values/strings_*.xml`, `res/drawable` (logos), `AndroidManifest.xml`, `app/src/test`. New strings, any new icons and unit tests will be listed for you to add, or you can link the `app/src` folder so I can do it.

## Risks / needs on-device testing

- Mini player swipe vs sheet drag and back gesture; timeline path on every corner-radius setting.
- Background friend-playlist caching: battery and data (bounded, Wi-Fi by default).
- Spotify public-page lookups can change format; fallback is a normal search with the parsed name.
- Gemini Nano availability differs per phone and needs the model downloaded.
- Removing `FriendTrackSheet`: check every action it had is still reachable.
- Nothing compiled or run; every phase ends with your `./gradlew :app:compileDebugKotlin`.

## Still open

- **9.2** is empty in your list. What should it be?
