# Daily Mix card redesign: verified prompt and plan (2026-10-01)

Your choices: **cleaner compact bar**, **artist mixes row + Artist as a filter**, **one scrollable chip row**.

## 1. Evidence

- E1: `presentation/screens/HomeScreen.kt:403-435`. Home renders `DailyMixLauncher(songs = yourMixSongs, filterPool = mixFilterPool, …)`. This is the card in use today.
- E2: `HomeScreen.kt:409`. `onPlayMix = { playerViewModel.startContinuousMix(MixFlavor.NORMAL, yourMixSongs.shuffled()) }`
- E3: `HomeScreen.kt:545`. Artist navigation already exists: `navController.navigateSafely(Screen.ArtistDetail.createRouteForName(name))`
- E4: `components/DailyMixLauncher.kt:97-110`. The bar is a `Row` with `.height(64.dp)`, a hand-built `RoundedCornerShape(… bottomCorner)` and `colors.primaryContainer`.
- E5: `DailyMixLauncher.kt:87-91`. `animateDpAsState(if (expanded) 0.dp else 22.dp, tween(durationMillis = 220))` uses no easing token.
- E6: `DailyMixLauncher.kt:136-159`. The "Mix" pill is a `Surface(shape = CircleShape, … Modifier.height(40.dp))` with the icon set to `contentDescription = null`.
- E7: `DailyMixLauncher.kt:164-191`. The "Open" pill toggles `expanded`. Its colour is `colors.surface.copy(alpha = 0.55f)`.
- E8: `DailyMixLauncher.kt:195-199`. The sheet uses `expandVertically(tween(240)) + fadeIn(tween(180))` and `shrinkVertically(tween(200)) + fadeOut(tween(120))`. These are raw ms values with no easing token, and they animate layout size.
- E9: `DailyMixLauncher.kt:250-311`. The header packs 4 `MixFilterButton`s at `weight(1f)` each, plus a 74dp "Mix filters" handle in the middle.
- E10: `DailyMixLauncher.kt:403-409`. `MixFilterButton` uses `.heightIn(min = 34.dp)` and `clickable(enabled = hasOptions)`. A field with no options is disabled, not hidden.
- E11: `DailyMixLauncher.kt:314-331`. Picking a field expands an inline `MixFilterOptionList` (a second expand animation using `tween(180)` and `tween(160)`).
- E12: `DailyMixLauncher.kt:342-355`. Generated mixes appear in a `LazyRow` of `GeneratedMixCard`s with `key = { it.id }` and no `animateItem()`.
- E13: `DailyMixLauncher.kt:506-577`. `GeneratedMixCard` is 140dp wide with a 124dp `MixArtCollage` and a 34dp play overlay.
- E14: `DailyMixLauncher.kt:584`. A reusable `MixArtCollage(songs, size, corner)` exists.
- E15: `DailyMixLauncher.kt:646-662`. `MixFilterSelectionSaver` saves 4 positional strings (country, era, genre, mood).
- E16: Hardcoded corners throughout: `RoundedCornerShape(26.dp / 18.dp / 14.dp)` at lines 238-243, 439, 518, 530 and 365.
- E17: `components/HomeMixFilters.kt:17-22`. `enum class MixFilterField { Country, Era, Genre, Mood }`
- E18: `HomeMixFilters.kt:26-51`. `MixFilterSelection(country, era, genre, mood)` defines `valueOf`, `with`, `activeCount` and `isEmpty`.
- E19: `HomeMixFilters.kt:125-155`. `buildMixFilterOptions` uses `facet {}` (sorted by count, `MaxOptionsPerField = 24`), and `applyMixFilters` ANDs the fields together.
- E20: `HomeMixFilters.kt:162-217`. `buildGeneratedMixes` groups by the first unpinned facet. Only when every field is pinned does it fall back to `pool.groupBy { it.displayArtist }` (187-191). `MinSongsPerGeneratedMix = 4`.
- E21: `HomeMixFilters.kt:220-228`. `describeSelection` joins mood, era, genre and country.
- E22: `data/model/Song.kt:186-188, 286-290`. `artist`, `artistId` and `artists: List<ArtistRef>` exist, and `primaryArtist: ArtistRef` falls back to `ArtistRef(id = artistId, name = artist, isPrimary = true)`.
- E23: `data/repository/ArtistImageRepository.kt:268`. `suspend fun getEffectiveArtistImageUrl(artistId: Long, artistName: String): String?`
- E24: `ui/theme/MotionTokens.kt`. It defines `EmphasizedDecelerate`, `EmphasizedAccelerate`, `Emphasized`, `DurationShort3 = 150`, `DurationShort4 = 200`, `DurationMedium1 = 250` and `DurationMedium2 = 300`.
- E25: `ui/theme/ReducedMotion.kt`. `@Composable fun rememberSystemReducedMotion(): Boolean` returns true when the animator scale is 0.
- E26: `ui/theme/Shape.kt`. `Shapes(small = 8.dp, medium = 16.dp, large = 24.dp)`

Not read: `MixFilterDropdown.kt`, `DailyMixSection.kt`, `DailyMixBannerHero.kt` and `DailyMixMenu.kt` (no evidence that Home uses the last three), plus the artist avatar composables in `screens/artist/`.

## 2. Improved prompt

**Goal:** Redesign the Daily Mix card on Home as a clean compact bar. A single scrollable filter chip row (with Artist added as a filter) sits directly under it, followed by one results row that leads with artist mixes. The user can play, filter and jump to an artist without opening anything.

**Scope:** The Daily Mix card on Home only. Files: `presentation/components/DailyMixLauncher.kt` (E4-E16), `presentation/components/HomeMixFilters.kt` (E17-E21) and the call site in `presentation/screens/HomeScreen.kt` (E1-E3). The Daily Mix screen, mix engine and other Home shelves are out of scope.

**Current behaviour:**
- Home shows a 64dp `primaryContainer` bar with a "Mix" pill and an "Open" pill (E4, E6, E7).
- "Open" expands a connected sheet. The sheet has 4 equal-width filter buttons around a centre handle (E8, E9). Tapping one expands a second inline option list (E11). Generated mix cards sit below (E12, E13).
- Artist exists only as the last-resort grouping when all four filters are pinned (E20).
- Motion uses raw ms tweens with no easing tokens and animates layout size (E5, E8, E11).
- Corners are hardcoded dp values (E16). Some touch targets are under 48dp (E6, E10).

**Spec:**

1. **Bar** (one row, `surfaceContainerHigh`, `MaterialTheme.shapes.large` (E26) on all corners; drop the animated corner from E5):
   - Leading: `MixArtCollage` thumbnail (E14) at 48dp, `shapes.medium`.
   - Text column: "Daily Mix" in `titleMedium`, `onSurface`. Subtitle in `bodyMedium`, `onSurfaceVariant`: "N tracks · Artist A, Artist B" with the top 2 distinct `primaryArtist.name` values (E22). While filters are active, the subtitle shows the `describeSelection` text (E21).
   - Trailing: one `FilledIconButton` (48dp, `primary`/`onPrimary`) that calls the existing `onPlayMix` (E2). Its icon is Play, or Shuffle while playing (E6), and it gets a real contentDescription.
   - Tapping the bar body calls `onOpenDailyMixScreen` (existing).
   - Remove the "Open" pill and the expand/collapse sheet (E7, E8).
2. **Filter chip row** (always visible, directly under the bar, `LazyRow` with 16dp side padding and 8dp spacing):
   - Order: **Artist**, Genre, Mood, Era, Country. Each chip is an M3 `FilterChip` with a trailing drop-down arrow.
   - Unselected, a chip shows the field label. Selected, it shows the chosen value with `selected = true`, using the M3 default `secondaryContainer` roles.
   - Tapping a chip opens an M3 `DropdownMenu` anchored to it, listing "Any" plus the options. This replaces the inline expanding list (E11).
   - Fields with no options are **hidden** rather than shown disabled (change from E10).
   - When `activeCount > 0`, a trailing "Clear" `AssistChip` appears and resets every field.
3. **Results row** (one `LazyRow` under the chips, keyed by stable id, using `Modifier.animateItem()`):
   - **Artist mixes come first.** Each is a 72dp circular artist photo with the name in `labelLarge` under it, `onSurface`, 1 line, ellipsised. Artists come from `primaryArtist` (E22) within the filtered pool, have at least `MinSongsPerGeneratedMix` songs (E20), and are sorted by song count, up to 8.
   - Tap plays that artist's mix through the existing `onPlayGeneratedMix` path. Long-press opens the artist page via `Screen.ArtistDetail.createRouteForName(name)` (E3).
   - Photo comes from `ArtistImageRepository.getEffectiveArtistImageUrl` (E23). If there's no image, show an initial on `secondaryContainer`.
   - When the Artist filter is set, the artist mixes are omitted.
   - **Then the facet mixes:** the existing `GeneratedMixCard`s (E13) from `buildGeneratedMixes` (E20), restyled with `shapes.large` (card) and `shapes.medium` (collage). Background is `surfaceContainer`. While one of a card's songs is playing, the background is `secondaryContainer` instead of an alpha-tinted primary (E13).
   - Empty state: one line of `bodyMedium` text in `onSurfaceVariant` plus a "Clear filters" `TextButton`.
4. **Artist filter data** (E17-E21, E15):
   - Add `MixFilterField.Artist` and `MixFilterSelection.artist`, and update `valueOf`, `with` and `activeCount`.
   - Add `songArtistLabel(song) = song.primaryArtist.name`, with blank or "Unknown Artist" mapped to null.
   - Add an `artists` facet in `buildMixFilterOptions`, include artist in `applyMixFilters` and `describeSelection`, and add an Artist grouper to `buildGeneratedMixes` (keeping today's last-resort artist fallback).
   - Extend the saver to 5 entries, artist last.
   - New `buildArtistMixes(pool, selection): ImmutableList<GeneratedMix>` with id `artist_<name>` and title = artist name.
5. **Shape and colour:** use only `MaterialTheme.shapes.*` and colour roles. No `RoundedCornerShape(n.dp)` (E16) and no `.copy(alpha = …)` on roles (E7).

**Motion:**
- The bar does not expand any more, so the E5 and E8 animations are deleted.
- Chip select and deselect use the FilterChip's built-in colour change. No custom animation.
- DropdownMenu uses the M3 default enter and exit.
- Results row changes (filter applied or cleared) use `Modifier.animateItem()` with `fadeInSpec = tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)`, `fadeOutSpec = tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate)` (150/250 = 60%), and `placementSpec = tween(MotionTokens.DurationMedium1, easing = MotionTokens.Emphasized)`. Fades are slide-free crossfades and items keep their size.
- Results to empty state: `AnimatedContent` using the same enter and exit specs.
- Interruption: a new filter before the fade finishes retargets from the current alpha and position. Nothing snaps.
- Reduced motion: if `rememberSystemReducedMotion()` (E25) is true, pass `null` specs to `animateItem` and use `EnterTransition.None`/`ExitTransition.None`.

**Accessibility:**
- The Play button, every chip and every artist avatar have a touch target of at least 48dp (chips via `minimumInteractiveComponentSize`).
- Content descriptions:
  - Play button: "Play Daily Mix" (or "Shuffle Daily Mix").
  - Artist: "Play Artist mix", with `onLongClickLabel = "Open Artist"`.
  - Chip: "Filter by Genre, currently Rock".
- Text uses role pairs only (`on*` on its matching container), so contrast holds with dynamic and album-art colours.

**Don't:**
- Don't use hardcoded hex, dp corners, sp sizes or raw-ms tweens.
- Don't create new token files.
- Don't touch `DailyMixScreen`, the mix engine, `DailyMixSection`, `DailyMixBannerHero` or `DailyMixMenu`.
- Don't use a modal sheet for filters.
- Don't change the play-shuffle behaviour (E2).

**Acceptance criteria:**
- [ ] The bar is a single row: collage, title and artist subtitle, one 48dp play button. No "Open" pill.
- [ ] Tapping the bar body opens the Daily Mix screen. Play starts the mix.
- [ ] The chip row shows Artist, Genre, Mood, Era and Country, and hides empty fields. Picking a value updates the subtitle and results. "Clear" resets them.
- [ ] The results row starts with artist circles. Tap plays the artist mix and long-press opens the artist page. With the Artist filter set, those circles disappear.
- [ ] Filters, including Artist, survive rotation and process death.
- [ ] No `RoundedCornerShape(` with a dp literal and no `.copy(alpha` remain in `DailyMixLauncher.kt`.
- [ ] Animator scale 0 gives instant changes. At 0.5x, changes are smooth.
- [ ] Light, dark and album-art colours all read correctly.
- [ ] `cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio2\jbr&& gradlew.bat assembleDebug"` passes.

## 3. Plan

1. **Read the remaining files.** Read `MixFilterDropdown.kt` and the artist avatar composables in `screens/artist/`. If either provides a dropdown chip or an artist image loader, reuse it in steps 6 and 7. **Done when:** the reuse decision is noted (Assumptions A2 and A3).
2. **Add the Artist field to the filter model.** File: `HomeMixFilters.kt` (E17, E18, E22). Add the `Artist` enum entry, the `artist` property, the `valueOf`/`with`/`activeCount` branches and `songArtistLabel`. **Done when:** it compiles and `MixFilterField.entries` has 5 entries.
3. **Wire Artist into options, filtering, descriptions and grouping.** File: `HomeMixFilters.kt` (E19, E20, E21). Add the `artists` facet, the `applyMixFilters` clause, the `describeSelection` part and an Artist grouper. Add `buildArtistMixes(pool, selection)`. **Done when:** picking an artist narrows the pool and `buildArtistMixes` returns nothing when the artist is pinned.
4. **Extend the saver.** File: `DailyMixLauncher.kt` (E15). Save 5 entries with artist last. **Done when:** the selection survives rotation and "Don't keep activities".
5. **Rebuild the bar.** File: `DailyMixLauncher.kt` (E4, E6, E7, E14). Use `shapes.large` on `surfaceContainerHigh`, a 48dp collage, the artist subtitle and a 48dp `FilledIconButton`; the bar body is clickable and opens the Daily Mix screen. Delete `expanded`, `bottomCorner` and the Open pill (E5). **Done when:** acceptance criteria 1 and 2 pass.
6. **Replace the sheet header with the chip row.** File: `DailyMixLauncher.kt` (E9, E10, E11). Use a `LazyRow` of `FilterChip`s and `DropdownMenu`, hide empty fields, add the Clear chip, and delete `MixFilterButton`, `MixFilterOptionList`, `MixOptionChip` and the sheet `AnimatedVisibility` (E8). **Done when:** acceptance criterion 3 passes.
7. **Add the artist mix item and the results row.** Files: `DailyMixLauncher.kt` (E12, E23); `HomeScreen.kt` (E1, E3). Add an `ArtistMixAvatar` composable (circle image or initial fallback) with tap and long-press. Put the artist mixes first, then `GeneratedMixCard`s, in one keyed `LazyRow`. Add an `onOpenArtist: (String) -> Unit` parameter, wired at the Home call site to `Screen.ArtistDetail.createRouteForName`. **Done when:** acceptance criterion 4 passes.
8. **Restyle `GeneratedMixCard` and the empty state.** File: `DailyMixLauncher.kt` (E13, E16). Use `shapes.large` and `shapes.medium`, `secondaryContainer` for the current mix, and a 48dp play overlay target. Add the empty state with a "Clear filters" button. **Done when:** no dp corners or alpha-copied roles remain (acceptance criterion 6).
9. **Add motion and reduced motion.** File: `DailyMixLauncher.kt` (E24, E25). Add `animateItem` specs and the `AnimatedContent` for the empty state, with the reduced-motion branch. **Done when:** the animations behave at 1x, 0.5x and 0x.
10. **Add accessibility labels.** File: `DailyMixLauncher.kt`. Add the content descriptions, `onLongClickLabel` and chip state descriptions, and confirm 48dp targets. **Done when:** TalkBack reads each control correctly.
11. **Verify** (see below).

**Verification:**
- The build command passes.
- Manual checks, one per acceptance criterion:
  - Bar layout.
  - Tapping the bar versus tapping play.
  - Each chip, including Artist, plus Clear.
  - Artist tap and long-press.
  - Rotation and process death.
  - Grep for `RoundedCornerShape(` and `.copy(alpha` in the file.
  - Animator scale at 0x and 0.5x.
  - Light, dark and album-art colours.
  - Changing filters rapidly mid-fade (should retarget, not snap).
  - The back gesture from the Artist page returns to Home with the filters intact.

**Risks:**
- `mixFilterPool` also feeds "Speed Dial" (`HomeScreen.kt:486`). Don't change the pool itself, only how this card filters it.
- Changing the saver format (E15) means previously saved 4-entry state restores with artist = null. This is fine, but check that `getOrNull(4)` is used.
- The artist image lookup (E23) is a `suspend` network or disk call per avatar. Load it lazily per item and cache it, otherwise the Home scroll will jank.
- Hiding empty fields (E10) means a library with no country tags shows no Country chip. This is intended.
- `GeneratedMix.id` (E20) can collide between `artist_x` and `mix_x`. The prefixes keep them distinct, and the `LazyRow` keys depend on that.

## 4. Assumptions

- A1: The chip row and results row are always visible (about 48dp + 120dp under the bar). If you want it tighter, a collapse chevron on the bar could hide them. This affects steps 5 and 6.
- A2: `MixFilterDropdown.kt` was not read. It may already be a reusable chip dropdown (step 1/6).
- A3: There may be an existing artist avatar or image loader in `screens/artist/` (step 1/7). If not, `ArtistMixAvatar` resolves the URL with `produceState` through a ViewModel or repository accessor.
- A4: Artist photos are circles (`CircleShape`), as is conventional for artists. This is the only non-theme shape and it is intentional.
- A5: The app's user-adjustable corner radius is assumed to flow through `MaterialTheme.shapes`. Shape.kt only shows static values (E26), so confirm it in Theme.kt.
- A6: Artist mixes play shuffled, the same as generated mixes do today (`HomeScreen.kt:414-424`).
