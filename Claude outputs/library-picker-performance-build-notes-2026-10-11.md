# Library and "Add songs" performance (build notes, 2026-10-11)

**Status:** implemented, not compiled, not run, not profiled. There was no fresh trace or meminfo, so these fixes come from reading the code. The newest perf logs in `build/` are from 2026-09-22 and predate most of the current code.

## What was causing the stutter and memory use

1. **Song rows were converted on the main thread.**
   - Every song row from the database goes through `SongEntity.toSong()`, which parses about 9 JSON columns with Gson (features, credits, mix data…), the artists JSON, and normalises title, artist, album, genre and **full lyrics**.
   - For paged lists, that conversion runs where the `PagingData` is cached. That's `cachedIn(viewModelScope)`, which is the main thread.
   - So each 50-song page (Library Songs and Liked, and the Add songs picker) parsed 50 × 9 JSON blobs during the frame. This hit opening Library, opening Add songs, and scrolling.
2. **Liked songs stacked one Paging transform per liked cloud song.**
   - `getPaginatedFavoriteSongs` called `insertHeaderItem` once for each liked YouTube Music song. With hundreds of likes, that's hundreds of nested transforms, all re-run for every page.
   - Library collects the Liked list on entry even when you open another tab. The picker also loaded it on open, even with the Liked chip off.
3. **Opening Library composed three tabs in the first frame.** `beyondViewportPageCount = 1` built the visible tab plus both neighbours (each a full list with its own queries) during the screen transition.
4. **The album and artist shelves rebuilt at the same moment.** `followInsights()` re-reads the whole play history whenever there were new plays, and it started straight away on the Albums and Artists tabs.
5. **Every song row started its own collector.** Each Library song row created its own Flow collector (a coroutine plus a lifecycle observer) for "is this the playing song?" as it scrolled into view.
6. **Text cleanup allocated on every field.** `normalizeMetadataText` built a list and ran a Unicode normaliser for every text field of every song, even plain ASCII.

## Changes
| # | Change | File |
|---|---|---|
| 1 | Paged song conversion runs on `Dispatchers.Default` (songs and liked) | `data/repository/MediaStoreSongRepository.kt` |
| 2 | Liked cloud songs: one placeholder header expanded with `flatMap`, instead of N stacked headers. Same order, same items. | `data/repository/MusicRepositoryImpl.kt` |
| 3 | Neighbouring Library tabs pre-load only after 600 ms, once the transition is over. Swiping before then still works; the page builds on demand. | `presentation/screens/LibraryScreen.kt` |
| 4 | The Albums and Artists shelves' insights rebuild starts 900 ms after the tab appears | `LibraryScreen.kt` |
| 5 | One shared playback state per list. Rows use `derivedStateOf`, so only the rows whose playing state actually changes recompose. Songs, Liked and Folders all use it; the old per-row path stays as the default for other callers. | `LibraryPlaybackAwareSongItem.kt`, `LibrarySongsTab.kt`, `LibrarySongsAndFavoritesTabs.kt`, `LibraryScreen.kt` |
| 6 | `normalizeMetadataText`: an ASCII fast path (returns the trimmed string with no copies), the marker list allocated once, and `Normalizer.isNormalized` checked before normalising. Output is unchanged. | `utils/Extensions.kt` |
| 7 | Add songs picker: the Liked list is collected only when the Liked chip is on, and row covers decode at 112 px instead of 168 px (36 dp image). | `presentation/components/SongPickerBottomSheet.kt` |

New test: `app/src/test/.../utils/NormalizeMetadataTextTest.kt` checks that the fast path gives the same output as before (ASCII, NUL removal, mojibake repair, NFC).

No new dependencies, and no changes to the database schema, manifest, preferences or network.

## Verification
| Check | Result |
|---|---|
| Compiled | **no**. Run `./gradlew :app:compileDebugKotlin`, then `./gradlew :app:testDebugUnitTest` |
| Run on device / profiled | **no** |
| ktlint 1.3.1 parse, the 8 touched main files | 0 errors |
| Line endings (4 CRLF files stayed CRLF, 4 LF stayed LF, none mixed) | pass |
| New unit test | written, **not run** |

## Needs on-device testing
- **Open Library** from Home several times, landing on each tab. It should be smoother during the transition, and the neighbouring tab should be ready shortly after.
- **Swipe tabs within the first half-second.** The page should still appear (built on demand).
- **Liked tab** with liked YouTube Music songs: they should still sit at the top in the same order, and the local liked songs should page below them.
- **Playlist › Add songs:** open it, scroll, toggle Liked, Local and Online, and search.
- **The playing-song highlight and equaliser icon** in Songs, Liked and Folders should still follow play/pause and track changes.
- **Albums and Artists shelves** appear about 1 s later than before on a first visit after new plays.
- **To measure:** run `adb shell dumpsys gfxinfo com.theveloper.pixelplay reset`, open Library 5 times, then run `adb shell dumpsys gfxinfo com.theveloper.pixelplay` and compare "Janky frames" before and after. Do the same for Add songs. For memory, run `adb shell dumpsys meminfo com.theveloper.pixelplay` after scrolling Songs to the end.

## Not changed (possible next steps)
- **Lyrics in list rows:** paged list queries still load every column, including lyrics and synced LRC inside the JSON. A slim list query (no lyrics) would cut memory further, but songs from lists are also played and queued, so they need the full data. That needs a check of every place a list song is used.
- **Library collects all four paged lists** (Songs, Albums, Artists, Liked) on entry. They're cached in the ViewModel, so it's mostly first-entry cost. Moving each into its own tab is a bigger refactor.
- **Rest of the app:** I haven't profiled the player, lyrics or queue sheets. A Perfetto trace or the app's Debug performance report while reproducing the lag would show what else is worth fixing.
