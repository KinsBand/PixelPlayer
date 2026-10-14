# Search: version folding, artist hub, top result, filter counts, swipe-to-queue (build notes, 2026-10-11)

**Status:** implemented, not compiled, not run on a device.

## What changed

### 1. Versions folded under one song (+N versions)
- **`SearchResultItem.SongItem`** has two new fields: `versions: List<Song> = emptyList()` and `isOfficialArtist: Boolean = false`. Both have defaults, so every existing `SongItem(song)` call still works.
- **New `data/search/SearchVersionGrouping.kt`** (pure code) folds the song results:
  - A family is songs with the same `ArtistCatalog.baseKey`, taken after the YouTube title noise is removed. That noise is "(Official Video)", a leading "Artist - ", and "(… cover)" / "[Karaoke]" brackets. Songs in a family also share a main artist (`RecordingKeys.primaryArtist`).
  - **When other artists join:** families by other artists with the same title fold in as covers in two cases. Either the query names the official artist ("the cure lovesong" folds Adele and 311 under The Cure), or the song is marked cover / karaoke / tribute.
  - **When they don't:** "hello" on its own keeps Adele's and Lionel Richie's songs apart, because they're different songs.
  - **Which song shows:** the original (no version tag) by the official artist. Ties go to the earlier search position, then the earlier year.
  - **Version order:** the artist's own cuts first, then covers. Each group follows search order.
  - Every regex is compiled once (§5 OOM rule).
- **`SearchStateHolder.publishResults()`** now groups the results on `Dispatchers.Default`.
  - A generation counter stops a slower pass from overwriting a newer one.
  - If grouping throws, the results show ungrouped.
  - Paging counts folded versions as already shown.
- **UI:**
  - `EnhancedSongListItem` has a new optional `belowSubtitle` slot. It defaults to null, so the 17 other callers are unchanged.
  - The pill uses `primaryContainer` at 50%, 8 dp corners, `labelSmall` SemiBold, and a chevron that rotates.
  - The accordion uses `AnimatedVisibility(expandVertically(tween(250)) + fadeIn() / shrinkVertically(tween(200)) + fadeOut())`.
  - Version rows sit 28 dp in, 4 dp apart, with `AbsoluteSmoothCornerShape(12.dp, 60)` corners, `surfaceContainerLowest` at 65%, and a 48 dp minimum height.
  - Each row shows a badge (Live / Remastered / Acoustic / … / "Cover by X"), the title, year · album, the duration and a play icon.
  - **Tap a version:** it plays in the normal search queue, taking the original's place in it.
  - **Hold a version:** the song's options open.
- **Loading:** a new `isSearching` flow (exposed as `PlayerViewModel.searchInProgress`) makes the results area show skeleton rows while a new query loads. A top-result shimmer shows too on All. Before this, it showed "No results" while loading. If nothing arrives, it gives up after 6 s.

### 2. "Because you listened" artist hub (`SearchHomeContent`)
- This replaces the old flat "Because you listen to X" list.
- **Layout:** two `surfaceContainerHigh` cards side by side, each at least 220 dp tall with 20 dp corners. A 56 dp round artist picture with a border, the name in GoogleSansRounded bold and "Because you listened" sit on top. Below that are 3 search tiles (12 dp corners, `surfaceContainerHighest`, a 16 dp search icon, `bodySmall` Medium text).
- **Tiles:** "<artist> best songs", a related artist, "<artist> live", then "acoustic" / "deep cuts".
- **Related artists** come only from the artist page's cache (`ArtistAggregationRepository.getCachedProfile`). There's no network call. They show only if you've opened that artist's page before.
- **Taps:** the picture and name open the artist page. A tile runs `submitSearch(query)` straight away.
- **Other states:**
  - One artist: a full-width card with 4 tiles.
  - No history: a "Start exploring" card (New releases / Top charts / Trending songs).
  - While insights load: two skeleton cards, for at most 4 s.

### 3. Top result card
- **New `data/search/SearchTopResult.kt`** (pure code) scores artists, songs and albums against the query:
  - 1.0 = the same text once accents, case and punctuation are ignored
  - 0.99 = the same once spaces are ignored
  - 0.97 = the same words in a different order
  - otherwise, how much of the longer text the shorter one covers
- Songs and albums also match "title artist" and "artist title".
- The threshold is 0.95.
- **Ties:** an exact artist wins only if the results contain songs by that artist ("Radiohead"). Otherwise the song wins ("Creep").
- **Card:** item 0 of the list, on the **All** tab only. 72 dp art (round for artists), a "TOP RESULT • …" pill, title and subtitle, a 48 dp play button, a gradient background and a 1 dp border.
- **Tapping the card** opens the artist or album, or plays the song.
- **The play button:**
  - Library artist: plays the artist.
  - Online artist: plays their songs from these results. If there are none, it opens their quick sheet.
  - Album: plays a library album, or opens the sheet for an online one.

### 4. Filter chips with counts
- The chip row was already fixed above the results, so it never scrolled away. It now:
  - has a `surface` background,
  - uses 36 dp chips with 10 dp corners,
  - colours the selected chip `secondaryContainer`,
  - grows a check mark using `motionScheme.defaultSpatialSpec()`,
  - shows counts ("Songs (24)", or "(24+)" when more can load).
- **Filter switch without a blank list:** `SearchStateHolder.performSearch` now seeds the new filter with the results of that kind that are already loaded. They show at once and stay merged under the filter's own results. The filter's own search still runs, because each filter fetches different online results. I didn't make it purely client-side, since that would lose Profiles and the filter-specific online pages.

### 5. Swipe to queue
- **Swipe right** = Play next (`addSongNextToQueue`). **Swipe left** = end of queue (`addSongToQueue`).
- The gesture is a custom `detectHorizontalDragGestures` + `Animatable`, not `SwipeToDismissBox`. The repo has no `SwipeToDismissBox` usage to copy, and its API changed in recent material3 alphas.
- The offset is read only in `graphicsLayer`.
- **Feedback:** a haptic tick at the threshold and `LongPress` haptics when it fires. A `primaryContainer` background with the PlaylistAdd icon and a label shows behind the row. An inverse-surface "Playing next" / "Added to queue" pill shows for 1.6 s.
- Swiping is off in selection mode. TalkBack users get custom actions for both.

### Small extra fix
- `LyricsLookupMetadata.cleanArtist` was building a `Regex` on every call (VEVO names). The new grouping calls it per search result, so I moved it to a `private val` (the §5 OOM rule).

## Files touched
- `data/model/SearchResultItem.kt` (CRLF kept)
- `data/search/SearchVersionGrouping.kt` (new)
- `data/search/SearchTopResult.kt` (new)
- `data/repository/LyricsLookupMetadata.kt` (regex hoisted)
- `presentation/viewmodel/SearchStateHolder.kt`
- `presentation/viewmodel/PlayerViewModel.kt` (adds `searchInProgress`)
- `presentation/viewmodel/LibraryCollectionViewModel.kt` (injects `ArtistAggregationRepository`, adds `cachedRelatedArtistNames`)
- `presentation/screens/SearchScreen.kt`
- `presentation/screens/search/components/SearchHomeContent.kt` (signature changed: `suggestions` replaced by `artistHubs`, `hubsLoading`, `fallbackQueries`, `onArtistClick`; the only caller is updated)
- `presentation/screens/search/components/SearchResultExtras.kt` (new: pill, versions list, top-result card, swipe row, feedback pill, skeleton, count chip)
- `presentation/components/subcomps/EnhancedSongListItem.kt` (optional `belowSubtitle` slot)
- `res/values/strings_screens.xml` (CRLF kept): 52 new lines of strings and plurals, English only
- `app/src/test/.../data/search/SearchVersionGroupingTest.kt` and `SearchTopResultTest.kt` (new)

No new dependencies, no manifest, Room, preferences or settings-registry changes, and no new network paths.

## Verification
| Check | Result |
|---|---|
| Compiled | **no**. Run `./gradlew :app:compileDebugKotlin`, then `./gradlew :app:testDebugUnitTest` |
| Run on device | **no** |
| Unit tests (14 new cases) | written, **not run** |
| ktlint 1.3.1 parse, 11 main files + 2 tests | 0 parse errors (a known-bad control file was flagged, so the check works) |
| `strings_screens.xml` well-formed | pass |
| Every `R.string` / `R.plurals` / `R.drawable` used in the touched UI files exists (67 refs) | pass |
| Duplicate string / plural names across `res/values` | none |
| Line endings (2 CRLF files stayed CRLF, the rest LF, none mixed) | pass |

## Needs on-device testing
- **Version folding:**
  - Try "the cure lovesong" (Adele and 311 under The Cure), "hello" (Adele and Lionel Richie stay separate) and "creep" (live cuts under the studio version).
  - Open and close the pill; tap and hold a version row.
  - Check that the pill tap doesn't also play the song.
- **Swipe:**
  - The threshold feel (96 dp).
  - That vertical scrolling isn't hijacked.
  - That a slow horizontal drag doesn't start playback.
  - That swiping doesn't fight the press-prewarm or long-press.
  - The feedback pill's position above the mini player and keyboard.
- **Top result:**
  - "Radiohead" (artist card), "Creep" (song card), "radiohead creep" (song card).
  - The play button for library and online artists.
- **Filter chips:** counts after All, then Songs, then back to All. Switching filters should never show an empty list, and the check-mark animation should look right.
- **Skeletons:** a new query shows shimmer rows instead of "No results"; a query with no results shows the empty state after about 6 s at most.
- **Hub:**
  - With 0, 1 and 2 top artists.
  - Related-artist tiles appear only after you've opened that artist's page once.
  - The picture opens the artist page.
  - Skeleton timing on a cold start.
- **Performance:** grouping runs on `Default` per publish. Watch for jank while fast-typing long queries.

## Open items / decisions
- **"Popularity" for picking the shown version** is the search's own order. Search results carry no Deezer rank.
- **Top result shows on the All tab only**, so category tabs don't show the same item twice.
- **Select all** in selection mode selects the shown songs only, not their folded versions.
- **Hard-coded strings:** the version-kind labels are in `strings_screens.xml`. An OTHER tag shows the title's own bracket text ("2011 Mix", "Mono") as-is.

## Follow-up (2026-10-11, after your first release build)
- **Build fix:** `SearchScreen.kt:1739` failed with "Argument type mismatch: actual type is 'Unit?', but 'ComposableFunction0<Unit>?' was expected". Kotlin read `if (…) null else @Composable { … }` as an annotated block, not a lambda. The pill is now a typed `val versionsPill: @Composable () -> Unit`, passed as `versionsPill.takeIf { item.versions.isNotEmpty() }`. That was the only compile error in the log.
- The many "removing resource … without required default value" warnings were already there before this change (strings that exist only in translations). They're unrelated.
- **Verification:** Compiled: no (your build gets past type-checking only after this fix). Run on device: no. ktlint parse of `SearchScreen.kt`: 0 errors. LF line endings kept.
