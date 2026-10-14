# Phases 2–5 + mini player curve fix: build notes (2026-10-14)

Plan: `roadmap-accounts-artist-player-plan-2026-10-14.md`. Follows Phase 1 and the shuffle round.
**Compiled: no. Run on device: no.**

## Mini player on screens without a nav bar (your bug)
- **Cause:** on routes that hide the nav bar (artist page, etc.), scrolling still drove `NavBarVisibility`. The mini player's bottom corners then *snapped* between the "above nav" and "bottom of screen" shapes.
- **Fix 1:**
  - `NavBarVisibility.scrollHideAllowed` is false on hidden-nav routes. MainActivity sets it from `routesWithHiddenNavigationBar`.
  - `NavBarScrollWatcher` ignores scrolls there and resets to shown, so the mini player stays put.
- **Fix 2 (smoother curve):**
  - `SheetVisualState` has a new `navBarVisibilityProvider`.
  - The collapsed bottom corners now `lerp` continuously from the docked radius to 32 dp as the nav bar slides away, so the corners morph with it instead of jumping.
  - `UnifiedPlayerSheetV2` passes `NavBarVisibility.visibilityProgress`.

## Phase 2: Accounts and library
- **Accounts screen:**
  - A services header with three tiles (green = connected, grey = not).
  - The logout button sits above the selected tile.
  - A wavy progress bar shows connect stages (Checking → Profile → Syncing → Done) via `AccountsViewModel.connectWithProgress`.
- **Friends and Hidden panels:**
  - They sit side by side. The open one goes full width.
  - Friends expand inline to show their playlists.
- **Playlists hub:**
  - Platform cards adapt to how many services are connected (0 → "Connect services" card, 1 → wide card, 2–3 → grid). Apple Music added.
- **Friend playlist cards:** show a "Syncing…" scrim with the loading indicator while caching.
- **Saving a remote playlist:** opening it while the save is still running now shows the skeleton (it waits up to 30 s) instead of "not found".
- **Home Recent:** now live, no longer a frozen snapshot.
- **Fixed pre-existing bugs:**
  - `FriendsScreen` passed params `FriendRow` didn't have.
  - `FriendsViewModel`'s auto-sync `init` read `friends` before it was set (crash risk).

## Phase 3: Artist page
- **Tabs:** Events · Merch (store or website) · Socials, docked under the top bar while scrolling. The map expands in place (200 ↔ 460 dp) and is interactive when expanded. The List/Map toggle is gone.
- **All Songs:**
  - 6 rows, with the 6th fading.
  - Then 10, then "Show all", with "Show less".
  - Shuffle uses the full catalogue, unweighted.
- **New sections:**
  - **Live performances**, with a see-all.
  - **Playlists**, one carousel for Spotify and YouTube Music with source badges. YT playlists open in-app.
- **Card style:** Appears on / Fans also like / Similar (songs, artists, playlists) now use the same card style as videos and playlists, via `CollectionCard` aspect ratio and shape.
- **About finale:**
  - **Band members** from MusicBrainz (current/past, Deezer photos).
  - **Socials hub.**
  - **"Ask about the artist"** box. It's on-device Gemini Nano only, with a local fallback, and hidden when Nano isn't ready.
- **Merch prices in AUD:**
  - Live rates from Frankfurter, cached daily, with built-in fallback rates.
- **Album headers:** show the visualiser when one of their songs is playing.

## Phase 4: Mini player
- **Song swipe:**
  - The mini player is swipe-to-change-song by default; the content slides with your finger.
  - New setting under Appearance: **"Swipe mini player to change song"** (off = old swipe-to-dismiss). Registry key `mini_player_swipe`.
- **Timeline:** a thin progress line drawn along the mini player's bottom edge, following its rounded corners.
- **Previous:** a new `previousSongNow()` that always goes to the previous song (not restart).

## Phase 5
- **Pasted links in search:** pasting a Spotify / YouTube Music / YouTube link shows a card (artist, album, playlist or track) that opens the match in-app.
  - It works **without Spotify sign-in**: it reads the page's public Open Graph tags or YouTube oEmbed.
  - The 7 roadmap links are covered by tests.
- **Camera-hole tap:** a new Setup toggle that turns tap-to-open on or off for the cutout overlay.
- **Profile picture:** you can now pan and zoom inside the circle crop before saving.

## Strings
New entries were added to the res `strings_*.xml` files:
- `strings_home_screen.xml`, `strings_settings.xml`, `strings_library.xml`, `strings_screens.xml`.
- Checks: all well-formed, no duplicate names, every `R.string` used exists (except the pre-existing `android.R.string.ok`).

## Files
- **New:**
  - `subcomps/` and `components/`: `player/MiniPlayerChrome.kt`.
  - `data/artist/`: `ArtistHubRepository.kt`, `ExchangeRateRepository.kt`.
  - Search links: `data/search/MediaLinkResolver.kt`, `viewmodel/MediaLinkViewModel.kt`, `search/components/MediaLinkCard.kt`.
  - `screens/artist/ArtistHubSections.kt`.
  - Tests: `MediaUrlParserLinksTest`, `SpotifyPageTagsTest`, `MerchAndHubTest`.
- **Edited:** 73 main files in total across this round, including:
  - `MainActivity`, `NavBarVisibility`, `SheetVisualState`, the `UnifiedPlayerSheet*` files
  - `AccountsScreen` / `AccountsViewModel`, `PlaylistsHub`, `PlaylistContainer`, `PlaylistViewModel`, `FriendsViewModel`, `HomeScreen`
  - `ArtistDetailScreen` / `ArtistDetailViewModel`, `ArtistEventsTab`, `ArtistPageSections`
  - MusicBrainz API, models and repository, `MerchPriceConverter`, `VideoRepository`
  - `PlayerViewModel`, `PlaybackStateHolder`, `UserPreferencesRepository`, `SettingsViewModel`, `AppearanceSettings`, `SettingsRegistry`
  - `SearchScreen`, `SetupViewModel` / `SetupScreen`, the overlay cutout files, `ProfileScreen`

## Verification
| Check | Result |
|---|---|
| Compiled / run on device | **No / No** |
| ktlint 1.3.1 parse, 73 main + 3 test files | No parse errors after the fixes below |
| Found and fixed during the check | `ArtistDetailViewModel` `AI_PREFIX` string missing its closing quote. 11 files had my inserted lines saved as CR CR LF; normalised to each file's own ending (files that are natively CR CR LF were left alone). |
| Strings / R refs | Well-formed, no duplicates, no missing refs |
| Unit tests | Written, **not run** |

## Please run
`./gradlew :app:compileDebugKotlin` then `./gradlew :app:testDebugUnitTest`

## Needs on-device testing
- Artist page (no nav bar): scroll up and down. The mini player must not move or change shape. On Home/Library, its corners should morph smoothly as the nav bar hides.
- Mini player swipe changes song (and the setting switches back to dismiss). The timeline follows the bottom corners.
- Accounts: connect progress stages, logout button position, Friends/Hidden panels expanding full width.
- Artist page: docked tabs, map expand, All Songs 6 → 10 → all, Live / Playlists / Members / Socials / Ask box (Nano phones only), AUD prices.
- Paste each roadmap link into search while signed out of Spotify.
- Camera-hole tap toggle; avatar pan/zoom crop.

## Open items
- **P2.6** (friend playlists, blends and shared mixes in playlist pickers): only "add to playlist" pickers exist, and those playlists aren't editable. Needs your call on what this should do.
- **3.4** genre/mood filter chips: skipped, because tracks have no per-track genre data.
- **9.2** is blank in the roadmap.
- **New keyless network calls:**
  - MusicBrainz, Frankfurter, Spotify public pages, YouTube oEmbed.
  - All go through `NetworkAccessPolicy` (enrichment, respects offline). Worth a look at whether you want them behind an extra toggle.
- **Possible ProGuard keep rules:** the new Gson MusicBrainz models (`Mb*`) may need them for release builds.
- **New constructor dependencies (Hilt should provide them; flagging in case tests construct these by hand):**
  - `ArtistDetailViewModel` gains `artistHubRepository`, `exchangeRates`, `nanoEngine`, `youTubeMusicApi`.
  - `PlaylistViewModel` gains `appleMusicSession`.
- Phase 1's hard-coded English labels (Like/Unlike, friend chip, "…, shuffled" toast) can now move to `strings_*.xml`.
