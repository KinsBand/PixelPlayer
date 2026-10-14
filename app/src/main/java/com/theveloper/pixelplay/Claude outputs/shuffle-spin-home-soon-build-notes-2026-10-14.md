# Shuffle spin, Home shuffle → Soon, endless Fresh Finds: build notes (2026-10-14)

Follow-up to Phase 1. **Compiled: no. Run on device: no.**

## What changed

**1. One shuffle animation everywhere.** New `presentation/components/subcomps/ShuffleSpin.kt`:
- `rememberShuffleSpin()` + `Modifier.shuffleSpin(spin)` + `spin.play()`. The glyph does one quick flip around its vertical axis (the arrows visibly cross) while it dips to 80 % and springs back. Timing: 450 ms, `MotionTokens.EmphasizedDecelerate`. The progress is read only in `graphicsLayer`, so there's no recomposition. It's off when the system has animations turned off.
- Applied to every shuffle button:
  - Home Quick picks and Fresh Finds
  - Playlist page, album page (both the top-bar and bottom shuffle buttons), Daily Mix
  - Your Music and History heroes
  - Library shuffle (on top of its existing tab-switch rotation)
  - Artist page: mix button, release cards, albums sheet, offline shuffle
  - Genre sort sheet, friend history, the shuffle button in Soon
  - Queue mix button, and the full-player and bottom-row mix toggles (via a new `iconModifier` param on `ToggleSegmentButton`)
- Skipped: the settings switch icon (not a button) and three composables nothing calls (`YourMixHeader`, `DailyMixBannerHero`, `VinylSleeveCard`).

**2. Hold Home's Shuffle → Soon.**
- New `ShuffleSoonButton` (looks like the old tonal Shuffle pill; tap = shuffle, hold = haptic + spin + Soon).
- New `ShuffleSoonSheet` (bottom sheet hosting `PlaylistSoonPanel` with its own wording: "Shuffle Quick picks soon", "Playing now, then the shuffle" / "Then the shuffle, after").
- Confirm queues the shuffled block after the chosen song with `queuePlaylist(..., shuffled = true)`.
- Fresh Finds Soon still tags friend plays and marks the week seen.
- `PlaylistSoonPanel` gained `titleOverride` / `thenNowOverride` / `thenAfterOverride`.

**3. Fresh Finds keeps going.**
- Tapping Shuffle now starts `startContinuousMix(SMART, initial = shuffled finds, playInitialFirst = true)`. The shuffled finds play first, then Smart Mix keeps adding new discoveries like them, so the queue doesn't end.
- New `playInitialFirst` param on `startContinuousMix` (Normal Mix already worked this way).
- Side effect: the queue title shows "Smart Mix", and the mix toggle shows Smart Mix on (that's what's running). Tapping the toggle stops it as usual.
- Scheduling Fresh Finds with Soon only inserts the shuffled finds; it doesn't start a mix, so your current queue isn't taken over.

## Strings (`res/values/strings_home_screen.xml`, CRLF kept)
`home_shuffle_label`, `home_shuffle_soon_hold`, `home_shuffle_soon_title`, `home_shuffle_soon_then_now`, `home_shuffle_soon_then_after`, `home_quick_picks_name`, `home_fresh_finds_name`. Each is defined once across `values/`.

## Files
New: `subcomps/ShuffleSpin.kt`, `components/ShuffleSoonSheet.kt`.
Edited:
- Home: `HomeScreen.kt`, `QuickPicksSection.kt`, `FreshFindsSection.kt`
- Soon and the player: `PlaylistSoonPanel.kt`, `PlayerViewModel.kt`, `ToggleSegmentButton.kt`, `player/FullPlayerContent.kt`, `player/BottomToggleRow.kt`, `QueueBottomSheet.kt`
- Library, playlists and albums: `subcomps/LibraryActionRow.kt`, `PlaylistDetailScreen.kt`, `AlbumDetailScreen.kt`, `DailyMixScreen.kt`, `YourMusicScreen.kt`, `RecentlyPlayedScreen.kt`, `GenreSortBottomSheet.kt`
- Artist page: `ArtistDetailScreen.kt`, `artist/ArtistPageSections.kt`, `artist/ArtistMixActions.kt`
- Friends: `FriendHistorySheet.kt`
- `res/values/strings_home_screen.xml`

## Verification
| Check | Result |
|---|---|
| Compiled / run | **No / No** |
| ktlint 1.3.1 parse, 22 edited Kotlin files | No parse errors |
| `strings_home_screen.xml` | Well-formed, no duplicate names |
| Nullable `onSmartMixActivate` | Kept nullable (`?.let`), so long-press behaviour is unchanged where it was null |

Please run: `./gradlew :app:compileDebugKotlin`

## Needs on-device testing
- Spin feel on every shuffle button (speed, no clipping inside FABs / small 20 dp action circles).
- Hold Quick picks / Fresh Finds Shuffle → the sheet slides up. The slider positions the shuffle, the tick queues it, and the toast reads "… plays after N songs, shuffled".
- Fresh Finds Shuffle: after the finds, new songs keep arriving (needs network). Check the friend tags on finds still show in the queue.
