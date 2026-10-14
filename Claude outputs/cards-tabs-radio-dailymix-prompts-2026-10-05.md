# PixelPlayer: song card options, tab theme, radio sheet, Daily Mix sheet (2026-10-05)

There are four areas here, so this file has four prompts, each with its own plan. Ship them in this order: **A → C → B → D**. A fixes a real reorder bug, and C reuses the player sheet.

Code paths are relative to `app/src/main/java/com/theveloper/pixelplay/`.

## Your answers (Phase 1)
- **Select mode:** holding a card opens the options sheet. The sheet gets a **Select** action that starts multi-select with that song already ticked.
- **Tab theme:** a three-way choice, **Follow app / Light / Dark**. It's remembered, and it only changes the Instruments page and its options.
- **Radio sheet:** both sheets use the player's layout, from your screenshot. Both get a **Website** card with a **Map** icon button to its right.
- **Mix pool:** local songs, plus Your Music's liked/streamed songs, plus online picks fetched when a filter is set.

## 1. Evidence

**Shared**
- E1: `ui/theme/MotionTokens.kt` defines `Emphasized`, `EmphasizedDecelerate` and `EmphasizedAccelerate`. Durations: `DurationShort3=150`, `DurationShort4=200`, `DurationMedium1=250`, `DurationMedium2=300`, `DurationMedium3=350`, `DurationMedium4=400`.
- E2: `ui/theme/ReducedMotion.kt:17`: `fun rememberSystemReducedMotion(): Boolean`.
- E3: `presentation/components/AnimatedSheetDismiss.kt`: `rememberAnimatedSheetDismiss(sheetState, onDismiss)` calls `sheetState.hide()` and then `onDismiss`.

**A: Song card options and reorder**
- E4: `presentation/components/subcomps/EnhancedSongListItem.kt:100-104` has the params `showMoreOptionsButton: Boolean = true`, `onLongPress`, `extraTrailing` ("shown just left of the options button") and `onMoreOptionsClick`.
- E5: `EnhancedSongListItem.kt:260-274`: `detectTapGestures(onTap = { if (isSelectionMode) onLongPress() else onClick() }, onLongPress = { // Long press always activates/toggles selection ... })`.
- E6: `EnhancedSongListItem.kt:375,396-412`: `showTrailingAction = showMoreOptionsButton && !isSelectionMode`, then a `FilledIconButton` with `Icons.Rounded.MoreVert` at 36.dp.
- E7: Callers pass `onMoreOptionsClick` to song rows in `screens/LibrarySongsTab.kt:363`, `LibrarySongsAndFavoritesTabs.kt:465`, `LibraryScreen.kt:2964`, `YourMusicScreen.kt:419,450`, `GenreDetailScreen.kt:391`, `AlbumDetailScreen.kt:385`, `DailyMixScreen.kt:366`, `RecentlyPlayedScreen.kt:482` and `SearchScreen.kt:1360`. Separate hand-built MoreVert rows are at `YourMusicScreen.kt:890`, `components/QuickPicksSection.kt:250`, `RecentTracksList.kt:274`, `PlaylistsHub.kt:883`, `player/RadioHistorySheet.kt:227`, `screens/RecentlyHeardScreen.kt:482` and `artist/ArtistPageSections.kt:366`.
- E8: `screens/LibrarySongsTab.kt:351-353`: `rememberedOnLongPress = { onSongLongPress(song) }`. A long press starts library multi-select.
- E9: `components/QueueBottomSheet.kt:2715` defines `QueuePlaylistSongItem(... onLongClick, isSelectionMode, isSelected ...)`. At `:3033-3051`, `if (isFromPlaylist && !isSelectionMode)` shows a MoreVert `FilledIconButton`.
- E10: `screens/PlaylistDetailScreen.kt` has two separate modes:
  - `:265` `isReorderModeEnabled`, and `:275` `isSelectionMode` ("hold a song to start").
  - `:604` `onLongClick = if (isReorderModeEnabled) null else { isSelectionMode = true ... }`.
  - `:570-573` `ReorderableItem(... enabled = isReorderModeEnabled)`.
  - `:630` the drag handle is shown only in reorder mode.
- E11: `PlaylistDetailScreen.kt:1647-1652`: `FilledTonalButton(onClick = onReorderClick) { Icon(drag_order_icon) … Text(reorderLabel) }` in the header's second row.
- E12: `PlaylistDetailScreen.kt:493-520`. In reorder mode, an extra LazyColumn item `item(key = "reorder_banner")` ("Drag songs to reorder" + Done) is inserted **between the header and the songs**.
- E13: `PlaylistDetailScreen.kt:1404`: `private const val HEADER_ITEM_COUNT = 1`. At `:323-336`, `onMove` uses `from.index - HEADER_ITEM_COUNT` and `to.index - HEADER_ITEM_COUNT`. **Bug:** while the banner from E12 is shown there are 2 items above the songs, so every index is off by one. The wrong song moves, and the first song can't be dragged correctly.
- E14: `PlaylistDetailScreen.kt:339-349`. When the drag ends, it commits once with `lastMovedFrom` (the first from) and `lastMovedTo` (the last to), using `playlistViewModel.reorderSongsInPlaylist(id, from, to)`.
- E15: `presentation/viewmodel/PlaylistViewModel.kt:1259-1290`. `reorderSongsInPlaylist(playlistId, fromIndex, toIndex)` moves one item by index in `_uiState.currentPlaylistSongs`, then persists `newSongOrderIds` (local prefs, or `connectedLibraryRepository.reorder` for YouTube Music) and sets `MANUAL_ORDER_MODE`. There is no multi-item move.
- E16: `PlaylistDetailScreen.kt:309-312`. `localReorderableSongs = remember(songsInPlaylist)` and `localSongKeys = uniqueSongKeys(...)` handle duplicate YouTube Music entries, but selection uses `song.id` (`selectedIds`). Duplicates are selected together.
- E17: `screens/radio/RadioScreen.kt:896-970`. The station row is a `Surface(onClick = onClick)` with a favourite `IconButton` and `if (onMore != null) IconButton(onClick = onMore) { MoreVert }`. `:270,:285` set `onMore = { optionsFor = it }`.
- E18: Your screenshots of Library → Albums show grid cards with no trailing options button. That is the target look for the rows.

**B: Tab theme**
- E19: `components/LyricsSheet.kt:223-240`. `lyricsSheetColors(colorScheme)` maps: container is `primaryContainer`, controlContainer is `surfaceContainerLowest`, controlContent is `onSurface`, accent is `primary`.
- E20: `LyricsSheet.kt:452`: `sheetColors = remember(colorScheme) { lyricsSheetColors(colorScheme) }`. `components/player/FullPlayerContent.kt:1353` passes `colorScheme = LocalMaterialTheme.current` (the album scheme).
- E21: `components/scoped/SheetThemeState.kt:70,88-89`: `activePlayerSchemePair?.let { if (isDarkTheme) it.dark else it.light }`. `isDarkTheme` comes from `LocalPixelPlayDarkTheme` (`UnifiedPlayerSheetV2.kt:596`). Both variants already exist.
- E22: `LyricsSheet.kt:1561-1563`: `InstrumentsPerformanceView(controller, onBackgroundColor = onBackgroundColor, accentColor = accentColor …)`. `components/TabNotationView.kt:209-217` builds `ScoreColors(ink = onBg…, faint = onBg…, accent…)` from those.
- E23: `components/tabs/TabPracticePanel.kt:1177` defines `TabOptionsSection(controller, contentColor, accentColor, itemBackgroundColor, onAccentColor)`, the "Instruments" group. `LyricsSheet.kt:2051-2060` passes it as `tabOptions`.
- E24: `LyricsSheet.kt:186-187,537-545` is the DataStore pattern for an Instruments-page preference (`LYRICS_WITH_TAB_KEY`, `booleanPreferencesKey`, `context.dataStore.edit`).
- E25: `TabNotationView.kt:514,554,569` hardcode `Color(0xF0202124)` and `Color.White` for the drum overlays. These are dark already and don't change with the theme.

**C: Radio station sheet**
- E26: `screens/radio/RadioStationOptions.kt:88-150`. `RadioStationOptionsSheet` is one flat scrolling Column, in this order: header, `RadioActionsRow` (Like | Play | Share) **near the top**, `RadioPresetPicker`, a `RadioOptionCard("Open station website", Language)`, a separate `RadioOptionCard("Show on map", Map)`, then `RadioStationDetails`. It uses `rememberModalBottomSheetState(skipPartiallyExpanded = true)` inline.
- E27: `RadioScreen.kt:302-330`. `onPlay`, `onOpenHomepage` and `onShowOnMap` set `optionsFor = null` directly, and so does `onDismiss`. The sheet disappears without its slide-down.
- E28: `components/SongInfoBottomSheet.kt`:
  - `:361` `rememberPagerState(pageCount = { 2 })`; `:381` `rememberAnimatedSheetDismiss`.
  - `:506` page 0 is "Options"; `:630` page 1 is "Details / Metadata".
  - `:740-830` is the bottom bar with `TabAnimation` **OPTIONS** / **INFO**.
  - `:200` `isRadio = radioStation != null`.
  - `:536-556` is the radio branch: `RadioPracticeRow()`, `RadioPresetPicker`, `RadioOptionCard("Open station website")`. There is no map.
- E29: Your screenshot of the player's radio sheet shows, from top to bottom: header (logo, name, "NSW · Conse."), Queue | Playlist | Next, "Add current song to Practice", Save to preset, Open station website, **Like | Play | Share at the bottom of the page**, then the OPTIONS / INFO bar.
- E30: `presentation/viewmodel/RadioViewModel.kt:201-210`: `showOnMap(station)` sets `RadioViewMode.MAP`, `focusStation` and `focusRequest + 1`. `navigation/Screen.kt:43` defines `object Radio : Screen("radio")`.
- E31: `RadioStationOptions.kt:383-402` defines `openStationHomepage(context, station)` and `shareStation(...)`.

**D: Daily Mix sheet**
- E32: `screens/HomeScreen.kt:1327-1334`: `buildHomeShelves`, with `mixFilterPool = (yourMixSongs + allSongs).distinctBy { it.id }`. That's the local library only (`allSongs` is `playerViewModel.allSongsFlow`, `:306`).
- E33: `HomeScreen.kt:383-398`: `mixFilterOptions` and `dailyMixSheetModel` are built by `produceState` on `Dispatchers.Default` from `moodMixFilterPoolSongs`.
- E34: `presentation/viewmodel/YourMusicViewModel.kt:108-112`: `combine(musicRepository.getAudioFiles(), likedSongsRepository.likedSongs, removals.keys)`. Your Music = local + liked/streamed (`data/library/LikedSongsRepository.kt:63`).
- E35: `viewmodel/DailyMixStateHolder.kt:103`: `suspend fun nextMixBatch(flavor, seeds, excluded, limit = 12)`. `PlayerViewModel.kt:2960-2976` calls it with `MixFlavor.SMART` under `withTimeoutOrNull(8_000)`, then `dailyMixStateHolder.analysed(...)`.
- E36: `components/DailyMixLauncher.kt:295-306`: `Text(if (selection.isEmpty) "Mixes for you" else "Made from your filters")` plus a count.
- E37: `DailyMixLauncher.kt:720-800`. `MixResultsRow` puts artist mixes first and generated mixes after, in one `LazyRow`. It uses `AnimatedContent` with `MotionTokens` and `rememberSystemReducedMotion()`.
- E38: `components/HomeMixFilters.kt:97-104`: `DailyMixSheetModel(artistMixes, generatedMixes, filtered, options)`. `:547` `buildDailyMixSheetModel(pool, selection, options)`.
- E39: `DailyMixLauncher.kt:421-456`. `MixFilterChipRow` is a `LazyRow`. Clear is an `AssistChip` labelled "Clear", added as the first **scrolling** item only when the selection isn't empty.
- E40: `DailyMixLauncher.kt:511-665`. `MixFilterPickerDialog` is a centred `Dialog`. The search field is at the top and **auto-focused** (`LaunchedEffect(Unit) { focusRequester.requestFocus() }`). The list reads top to bottom: "Use …", "Any {field}", then the options. **Clear / Close** are `TextButton`s at the bottom right.
- E41: `HomeMixFilters.kt:22-41` lists the `MixFilterField` entries: Artist … Source, with `allowsCustom` and `searchHint`.
- E42: Existing segmented controls: `components/ToggleSegmentButton.kt:43` and `screens/TabAnimation.kt:33`. The latter is used by the player sheet's OPTIONS/INFO bar (E28).

Not read: `Theme.kt` and `Type.kt` in full, and `Shape.kt` beyond its `Shapes(...)` declaration. Shape/type references below use `MaterialTheme.shapes` and `MaterialTheme.typography` roles only.

---

## Prompt A: Hold-for-options song cards, and a single Edit mode in playlists

**Goal:** Song, radio and similar list cards lose their options (⋮) button. Holding a card opens its options sheet. In playlists, one **Edit** button turns on select mode and reorder mode together, and reordering works reliably, including moving several selected songs at once.

**Scope:** `EnhancedSongListItem.kt` (E4-E6), `QueuePlaylistSongItem` in `QueueBottomSheet.kt` (E9), `PlaylistDetailScreen.kt` (E10-E16), `PlaylistViewModel.reorderSongsInPlaylist` (E15), `RadioScreen.kt` station row (E17), and the callers in E7.

**Current behaviour:**
- Rows show a MoreVert button (E6, E9, E17).
- A long press starts multi-select (E5, E8, E10 :604).
- Playlists have a separate Reorder button (E11) and a separate reorder mode with a banner (E12).
- The banner shifts the list indices by one, so drags move the wrong rows (E13).
- Only one from/to pair is committed per drag (E14), and the view model can only move one item (E15).

**Spec:**
1. **Rows.** Remove the trailing MoreVert from `EnhancedSongListItem` (E6) and from `QueuePlaylistSongItem` (E9). Keep `extraTrailing` and the playing indicator. In `EnhancedSongListItem`, the gesture becomes:
   - Tap: `onClick`. In selection mode, a tap toggles selection.
   - Long press, not in selection mode: `onMoreOptionsClick(song)` with `HapticFeedbackConstantsCompat.LONG_PRESS`.
   - Long press in selection mode: toggles selection.
   - Keep the `onMoreOptionsClick` param name so the callers in E7 compile unchanged. Remove `showMoreOptionsButton` once no caller sets it.
2. **Select from the sheet.** The options sheet these rows open (E7 callers → `SongInfoBottomSheet`) gets a **Select** action. It closes the sheet with `rememberAnimatedSheetDismiss` (E3), then calls the screen's existing selection entry (`onSongLongPress`, E8) for that song. Only screens that already have multi-select show it, via an optional `onSelect: (() -> Unit)? = null` param.
3. **Hand-built rows** (the second list in E7) and the **radio station row** (E17): remove the MoreVert. Open the same menu on long press using `combinedClickable(onClick, onLongClick)`, and keep tap as it is. On the radio row the favourite button stays.
4. **Playlist header** (E11): replace "Reorder" with a `FilledTonalButton` labelled **Edit** (icon `Icons.Rounded.Edit`). It's shown when `canReorder || canEditSongs`.
5. **Edit mode** replaces `isReorderModeEnabled` + `isSelectionMode` (E10) with one `isEditMode`. While it's on:
   - Every row shows a check state and a drag handle.
   - Tapping a row toggles its selection.
   - The selection top bar and actions (existing, E10 :689/:779) are shown, plus **Done**.
   - Back and Done both exit.
   - Holding a playlist row **outside** Edit mode opens the options sheet, the same as everywhere else.
   - Remove the reorder banner item (E12). Its hint moves into the selection bar's subtitle: "Drag to reorder".
6. **Fix reordering:**
   - (a) Compute the header offset from the actual items. Add a `songKeyToIndex` lookup from `localSongKeys`, and resolve `from.key`/`to.key` instead of `index - HEADER_ITEM_COUNT` (E13).
   - (b) Commit the whole order, not just one from/to pair. Add `PlaylistViewModel.setPlaylistOrder(playlistId, orderedKeys)`, which persists the full id order through the same two backends as E15 and sets Manual mode. Call it once when `isAnyItemDragging` goes false (E14).
   - (c) Use the unique row keys (E16) for selection, so duplicate YouTube Music rows select on their own.
7. **Multi-reorder:** when you drag a row that is selected and 2 or more rows are selected, the other selected rows collapse into the dragged row: they animate out and the list closes up. On drop, all selected rows are inserted together, in their original relative order, at the drop position. Dragging an unselected row moves just that row.

**Motion:**
- Edit enter (handles and checkboxes): fade + horizontal slide-in, `tween(MotionTokens.DurationMedium1, MotionTokens.EmphasizedDecelerate)`.
- Edit exit: `tween(MotionTokens.DurationShort3, MotionTokens.EmphasizedAccelerate)`. All rows share these durations.
- Rows reflowing after a move or multi-collapse use `Modifier.animateItem(placementSpec = tween(MotionTokens.DurationMedium1, MotionTokens.Emphasized))`.
- Drag lift: replace the bare `animateFloatAsState` 1.05 scale (`:575`) with `MaterialTheme.motionScheme` spatial spring, applied through `graphicsLayer`.
- Interruption: toggling Edit mid-animation reverses from the current value. A drop during the collapse finishes the collapse first, then inserts.
- Reduced motion (`rememberSystemReducedMotion()`, E2): no slide, alpha only, and `snap()` for placement.

**Accessibility:**
- Rows add `onLongClickLabel = "Song options"` and keep a 48dp hit area.
- Drag handles keep `reorderSongCd`.
- Add custom accessibility actions "Move up" and "Move down" in Edit mode, so screen readers can reorder.
- The Edit button reads "Edit playlist".

**Don't:**
- Change what the options sheet contains, apart from Select.
- Hardcode colours or corners in new code (use `MaterialTheme.shapes` and the M3 colour roles).
- Touch the queue sheet's own drag code.

**Acceptance criteria:**
- [ ] No ⋮ on song rows in Library, Your Music, Album, Genre, Daily Mix, Recently played, Search, playlist rows or radio station rows.
- [ ] Holding any of those rows opens its options sheet, with a haptic.
- [ ] Select in the sheet starts multi-select with that row ticked (where supported).
- [ ] The playlist header shows Edit, not Reorder. Edit gives handles, checkboxes and the selection bar together. Done and Back exit.
- [ ] Dragging the 1st, middle and last rows moves exactly that row. The order is still right after leaving and reopening the playlist, for both local and YouTube Music playlists.
- [ ] Selecting 3 non-adjacent rows and dragging one of them moves all 3, in order.
- [ ] Duplicate YouTube Music rows can be selected individually.
- [ ] `cmd.exe /c "set JAVA_HOME=C:\Program Files\Android\Android Studio2\jbr&& gradlew.bat assembleDebug"` passes.

### Plan A
1. Change the `EnhancedSongListItem` gesture (long press → `onMoreOptionsClick`, toggle in selection mode) and remove the MoreVert. Files: `EnhancedSongListItem.kt` (E4-E6). Done when: holding a library row opens the sheet.
2. Add the optional `onSelect` and the Select action to the options sheet. Wire `onSongLongPress` from the Library/YourMusic/Genre callers. Files: `SongInfoBottomSheet.kt` (E28) and the E7 callers. Uses E3. Done when: Select starts multi-select with that row ticked.
3. Move the hand-built MoreVert rows to `combinedClickable` long press. Files: the second list in E7. Done when: no MoreVert icons remain in them.
4. Long press on the radio station row opens `optionsFor`, and its MoreVert is removed. File: `RadioScreen.kt` (E17). Done when: holding a station opens the sheet.
5. Remove the MoreVert from `QueuePlaylistSongItem`. File: `QueueBottomSheet.kt:3033` (E9). Done when: playlist rows have no ⋮, and the queue is unaffected.
6. Replace the Reorder header button with Edit. File: `PlaylistDetailScreen.kt:1647` (E11). Done when: Edit shows.
7. Merge the two modes into `isEditMode`, remove `reorder_banner`, and make long press outside Edit open options. File: `PlaylistDetailScreen.kt` (E10, E12). Done when: one mode shows handles, checkboxes and the bar.
8. Resolve moves by key instead of `HEADER_ITEM_COUNT`. File: `PlaylistDetailScreen.kt:323` (E13). Done when: the first and last rows drag correctly.
9. Add `setPlaylistOrder(playlistId, orderedIds)` and commit the full order when the drag ends. Files: `PlaylistViewModel.kt` (E15), `PlaylistDetailScreen.kt:339` (E14). Done when: the order survives reopening, for local and YouTube Music playlists.
10. Switch selection to unique row keys. File: `PlaylistDetailScreen.kt:309` (E16). Done when: duplicates select individually.
11. Add multi-reorder (collapse the selected rows into the dragged row, insert them as a block on drop). File: `PlaylistDetailScreen.kt`. Done when: the 3-row test passes.
12. Add the motion: Edit enter/exit tokens, `animateItem`, the spring lift and the reduced-motion fallback. Uses E1, E2. Done when: the checks under Verification pass.
13. Add accessibility: `onLongClickLabel`, Move up/down actions, the Edit button label. Done when: TalkBack can reorder.
14. Verify (below).

**Verification:** run the build command. Then check by hand: Edit enter/exit; toggling Edit mid-animation; a drag that ends with Back pressed; animator scale 0x and 0.5x; light and dark; two different album-art palettes.

**Risks:**
- Many screens rely on long press meaning select (E7, E8). Any screen missed in step 2 loses multi-select.
- The YouTube Music reorder call (E15) now gets the full order. Check that the API accepts it.
- `LibrarySongsAndFavoritesTabs` placeholders pass `onMoreOptionsClick = {}`, so a long press there must do nothing.

---

## Prompt B: Light/dark choice for the Instruments (tab) page

**Goal:** In the Instruments options ("Instruments" group, E23), add **Tab appearance: Follow app · Light · Dark**. It recolours the Instruments page, its practice panel and its options to the album scheme's light or dark variant.

**Scope:** `LyricsSheet.kt` (E19, E20, E22, E24), `TabPracticePanel.kt` `TabOptionsSection` (E23), and how the light/dark pair reaches the sheet (E21).

**Current behaviour:** the Instruments page takes its colours from `lyricsSheetColors(colorScheme)` (E19). That scheme is the album scheme already resolved to light or dark by the app setting (E20, E21), so the tab page can't differ from the rest of the app.

**Spec:**
1. Store an enum `TabAppearance { FollowApp, Light, Dark }` under a new DataStore key `TAB_APPEARANCE_KEY` (string), using the same pattern as E24. The default is FollowApp.
2. Pass the album `ColorSchemePair` (E21) into `LyricsSheet` as a new optional `colorSchemePair` param next to `colorScheme` (E20). Then build `instrumentsScheme`:
   - FollowApp: `colorScheme`.
   - Light: `pair.light`.
   - Dark: `pair.dark`.
   - If the pair is null, fall back to `colorScheme`.
3. Build `instrumentSheetColors = lyricsSheetColors(instrumentsScheme)`. Use it only for the Instruments page: `InstrumentsPerformanceView` (E22), `TabPracticePanel`, and `TabOptionsSection` (E23). The Lyrics page is unchanged.
4. Add a three-segment control to `TabOptionsSection`, under the "Instruments" title, labelled "Tab appearance", using `ToggleSegmentButton` (E42). It uses the passed `contentColor`/`accentColor`, and the label uses `MaterialTheme.typography.titleSmall`.

**Motion:**
- Switching recolours through `animateColorAsState`, `tween(MotionTokens.DurationMedium2, MotionTokens.Emphasized)` (a state change). Every colour shares that duration.
- Score ink is rebuilt when the resolved colours change (`remember(onBg, accent)`, E22). Accept the per-frame rebuild during the 300ms, or switch the ink at the end. Pick whichever profiles under 16ms per frame.
- Interruption: a new choice retargets from the current colour.
- Reduced motion: snap.

**Accessibility:**
- The segments have 48dp targets and `Role.RadioButton` semantics, with `stateDescription` "Selected".
- `ScoreColors.ink` on the container keeps at least 4.5:1 contrast in both variants (check `onSurface` on `primaryContainer`, E19).

**Don't:**
- Change the app's global dark setting.
- Recolour the drum overlay chips (E25, already dark).
- Add hex colours.

**Acceptance criteria:**
- [ ] With the app in light mode, choosing Dark turns the tab page, practice panel and Instruments options dark, while Lyrics stays light. The reverse also works.
- [ ] Follow app tracks the app setting.
- [ ] The choice survives a restart.
- [ ] The build passes.

### Plan B
1. Add the `TabAppearance` enum and DataStore key, read and written like `LYRICS_WITH_TAB_KEY`. File: `LyricsSheet.kt` (E24). Done when: the value persists.
2. Pass the `ColorSchemePair` down to `LyricsSheet`. Files: `FullPlayerContent.kt:1353` (E20), `SheetThemeState.kt` (E21). Done when: the pair is available in `LyricsSheet`.
3. Resolve `instrumentsScheme` and `instrumentSheetColors`, and apply them to the Instruments page, panel and options only. File: `LyricsSheet.kt:1561`, `:2051` (E22, E23). Done when: forcing Dark changes only that page.
4. Add the segmented control to `TabOptionsSection`. File: `TabPracticePanel.kt:1177` (E23, E42). Done when: tapping a segment switches the theme.
5. Add the colour animation, interruption handling and the reduced-motion snap (E1, E2).
6. Check contrast and add the semantics.
7. Verify: build. Then check all three choices with the app in light and in dark, switching mid-animation, Lyrics + Tab mode, two album palettes, animator scale 0x.

**Risks:**
- Lyrics + Tab mode shows lyrics and tab together. The lyrics half keeps the app scheme, so there may be a visible seam. Accept it, or tint only the tab pane.
- The `ScoreRenderer` cache key includes `colors` (E22), so every switch re-lays out the score.

---

## Prompt C: Radio station sheet matches the player sheet, with Website + Map

**Goal:** The Radio screen's station sheet (E26) gets the same layout as the player's radio sheet (E28, E29). In both sheets, "Open station website" becomes **Website**, and a **Map** icon button sits to its right and opens the Radio map focused on the station.

**Scope:** `RadioStationOptions.kt` (E26, E31), `RadioScreen.kt:302-330` (E27), the radio branch of `SongInfoBottomSheet.kt` (E28), `RadioViewModel.showOnMap` (E30).

**Current behaviour:**
- The Radio screen sheet is a single column with Like/Play/Share near the top and a separate "Show on map" card (E26).
- Its actions close the sheet without the slide-down (E27).
- The player sheet has an Options/Info pager with Like/Play/Share at the bottom of the Options page and no map (E28, E29).

**Spec:**
1. Pull the radio layout out into one shared composable, `RadioStationSheetContent(station, presets, isFavorite, playState, onPlay, onToggleFavorite, onSetPreset, onWebsite, onMap, showQueueRow, showPracticeRow)`, in `RadioStationOptions.kt`. It has:
   - **Header:** `RadioStationHeader` (E26).
   - **A two-page pager, the same as E28.**
     - **Options page,** in screenshot order (E29): Queue | Playlist | Next (player only, `showQueueRow`), Practice (player only), Save to preset, the **Website row**, then Like | Play | Share (`RadioActionsRow`) at the bottom.
     - **Info page:** `RadioStationDetails` (location, genres, language, stream, copy link).
   - **Bottom bar:** the OPTIONS / INFO bar built with `TabAnimation` (E42), the same as E28.
2. **Website row:** a `Row` with the existing `RadioOptionCard` (weight 1f, title **"Website"**, subtitle = host) and, to its right, a `FilledTonalIconButton` (`Icons.Rounded.Map`, contentDescription "Show {station} on map"). The button matches the card's height and uses `MaterialTheme.shapes.large` and `surfaceContainerHigh`.
   - With no homepage, the Map button takes the whole row as a card titled "Map", with the place as its subtitle.
   - With no geo (`!station.hasGeo`), the button is hidden.
   - Remove the separate "Show on map" card.
3. **Radio screen:** `RadioStationOptionsSheet` hosts `RadioStationSheetContent` (no Queue or Practice rows). Every action closes through `rememberAnimatedSheetDismiss` (E3) and then runs: Play, Website, and Map → `viewModel.showOnMap(station)` (E30).
4. **Player sheet:** the radio branch (E28 :536-556) uses the shared Website row. Map: dismiss the sheet, collapse the player, navigate to `Screen.Radio`, and call `showOnMap` through a shared pending-focus holder (see Assumptions).

**Motion:**
- The pager and bottom bar reuse the E28 behaviour as it is.
- Dismiss is `sheetState.hide()` then the action (E3).
- For the player Map path, the player collapse and the navigation run one after the other, not at the same time.
- Interruption: a second tap while closing is ignored (E3 already handles this).
- Reduced motion: inherits the sheet defaults.

**Accessibility:** Website and Map are separate 48dp+ targets with distinct labels ("Open website", "Show on map").

**Don't:**
- Change the presets logic or the details content.
- Add new card styles.

**Acceptance criteria:**
- [ ] Holding a station on the Radio screen opens a sheet that looks like your player screenshot, without the Queue and Practice rows.
- [ ] OPTIONS / INFO switches pages.
- [ ] Both sheets read "Website", with a map icon to its right.
- [ ] From the Radio screen, Map slides the sheet down, switches to the map and centres the station. The same works from the player.
- [ ] Play, Website and Map all slide the sheet away instead of snapping it shut.
- [ ] The build passes.

### Plan C
1. Extract `RadioStationSheetContent` with the pager and bottom bar, copying E28's structure. File: `RadioStationOptions.kt` (E26, E28). Done when: it renders in a preview.
2. Build the Website + Map row and remove the "Show on map" card. File: `RadioStationOptions.kt`. Done when: all three cases render (homepage only, geo only, both).
3. Host the content in `RadioStationOptionsSheet` with `rememberAnimatedSheetDismiss`, and route the actions through it. Files: `RadioStationOptions.kt`, `RadioScreen.kt:302` (E27, E3). Done when: no snap-close is left.
4. Replace the player radio branch's website card with the shared row. File: `SongInfoBottomSheet.kt:536` (E28). Done when: the player shows "Website" + Map.
5. Add a pending-focus holder plus navigation for Map from the player. Files: `RadioViewModel.kt:201` (E30), the player overlay host. Done when: Map from the player opens the radio map centred on the station.
6. Add the accessibility labels.
7. Verify: build. Then check: open and close by swipe, scrim and Back; tap Map while the sheet is still opening; animator scale 0x; light and dark; with and without a station logo.

**Risks:**
- `RadioViewModel` is Hilt-scoped to the Radio destination. A focus request sent before it exists is lost unless it's held in a singleton such as `RadioStore` (E26 import).
- E28's pager height animation uses `tween(280, FastOutSlowInEasing)`, not a token. Reusing it as-is keeps the two sheets identical, so it's out of scope here.

---

## Prompt D: Daily Mix sheet (streaming pool, Mix/Artist tabs, fixed clear button, new filter picker)

**Goal:**
- The Home Daily Mix sheet draws on local and streaming music.
- It splits results into **Mix** and **Artist** tabs.
- It pins an icon-only clear button at the start of the filter row.
- Its filter picker becomes an in-sheet search bar that moves from the chip to the centre, with options rising above it.

**Scope:** `HomeScreen.kt` (E32, E33), `HomeMixFilters.kt` (E38, E41), `DailyMixLauncher.kt` (E36, E37, E39, E40), `YourMusicViewModel` sources (E34), `DailyMixStateHolder.nextMixBatch` (E35).

**Current behaviour:**
- The pool is local only (E32).
- A "Mixes for you" heading sits over one combined row (E36, E37).
- The "Clear" chip scrolls away with the row (E39).
- The picker is a centred dialog with search auto-focused, "Any" at the top, and Clear/Close bottom right (E40).

**Spec:**

**D1 Pool.**
- `mixFilterPool` = local (E32) + `LikedSongsRepository.likedSongs` (E34), with removals excluded, de-duplicated by `mixIdentity`. This one pool feeds both the options and the model (E33).
- When the selection isn't empty, also fetch `nextMixBatch(MixFlavor.SMART, seeds = model.filtered.take(5), excluded = pool identities)` under an 8s timeout, as in E35. Run the results through `analysed(...)` and append them to `filtered` and the mixes.
- While the fetch runs, show the existing progress bar (E36 `LinearProgressIndicator`). If it fails, keep the library results and show no error.

**D2 Tabs.** Replace the heading text (E36) with a two-segment toggle, **Mix · Artist**, built with `ToggleSegmentButton` (E42) and left-aligned. Keep the count to the right, for the current tab. Mix shows `generatedMixes`, and Artist shows `artistMixes` (E38). Each tab has its own empty state ("No artist mixes for these filters"). The default is Mix.

**D3 Clear.**
- The filter row becomes a `Row`: a fixed `IconButton` (`Icons.Rounded.FilterAltOff`, contentDescription "Clear all mix filters"), then a `VerticalDivider` (`outlineVariant`, 24dp tall, 8dp horizontal padding), then the `LazyRow` of chips (`weight(1f)`).
- The clear button is always shown. It's disabled at 38% alpha when the selection is empty, so the row doesn't jump.

**D4 Picker.** Replace `MixFilterPickerDialog` (E40) with an in-sheet overlay.
- **Open:** the tapped chip's bounds (`onGloballyPositioned`) are the start. A pill-shaped search bar moves from the chip's position to the horizontal centre, at the chip row's height.
  - **Clear** (icon button, `Icons.Rounded.Close` outline variant or `Backspace`) appears on its left.
  - **Close** (`Icons.Rounded.ExpandMore`) appears on its right.
  - The rest of the sheet's content dims behind it with a `scrim` at 32% alpha.
- **The bar is the search field.** It's not focused on open (remove the auto-focus from E40). At rest it shows `field.label` and its value, as a compact pill.
  - Tapping it focuses it, and it widens to the space between Clear and Close, with the keyboard up.
  - Losing focus, or emptying it and pressing IME Done, shrinks it back.
- **Options rise above the bar.** Use a `LazyColumn(reverseLayout = true)` anchored to the bar's top edge, up to 60% of the sheet height.
  - Item 0, nearest the bar, is **"Any {field}"**. Then "Use '…'" when typing (for `allowsCustom`, E41). Then the options in the existing order.
  - Dragging down scrolls through the rest of the list.
  - `PickerRow` (E40) is reused.
- **Close:** the bar moves back to the chip, and the options collapse downward into the bar.
- **Actions:** picking a value closes and applies it. Clear sets `null` and stays open. Back closes the picker first.

**Motion:**
- Chip → centre move: `Animatable` offset with the `MaterialTheme.motionScheme` spatial spring (it's a morph), applied as `graphicsLayer` translation.
- Clear and Close fade in: `tween(MotionTokens.DurationShort4, MotionTokens.EmphasizedDecelerate)`. They fade out: `tween(MotionTokens.DurationShort3, MotionTokens.EmphasizedAccelerate)`.
- Options list enter: scaleY from the bottom origin plus fade, `tween(MotionTokens.DurationMedium2, MotionTokens.EmphasizedDecelerate)`, items with `Modifier.animateItem()`. Exit: `tween(MotionTokens.DurationShort4, MotionTokens.EmphasizedAccelerate)`.
- Search expand and collapse: a spatial spring on width. This is a layout change and is accepted here because the bar is a morphing element. Keep the list anchored so it doesn't jump.
- Mix/Artist switch: reuse E37's `AnimatedContent` fade (`DurationMedium1` in, `DurationShort3` out).
- Interruption: closing mid-open retargets the spring from its current offset. Focusing during the move is allowed and the width spring starts from its current value.
- Reduced motion (E2): no move. Fade the bar in at the centre with the spatial springs replaced by `snap()`, and fade only for the list.

**Accessibility:**
- The bar has the `paneTitle` "{field} filter".
- Clear and Close have 48dp targets and labels ("Clear {field}", "Close filter").
- When the picker opens, focus moves to the bar for TalkBack without opening the keyboard.
- The tabs have `Role.Tab`.

**Don't:**
- Change the filtering or mix algorithms (only the pool input changes).
- Hardcode colours or corners (use `MaterialTheme.shapes.extraLarge` for the pill).
- Keep the `Dialog`.

**Acceptance criteria:**
- [ ] With a liked YouTube Music song that isn't on the device, filtering by its artist shows it in the results and mixes.
- [ ] With a filter set, online picks are added after the spinner. In airplane mode the library results still show.
- [ ] "Mixes for you" is gone. The Mix and Artist tabs split the results, and each has its own count and empty state.
- [ ] The clear icon is pinned at the start with a divider, stays put while the chips scroll, and is disabled when nothing is set.
- [ ] Tapping the Artist chip moves the bar to the centre with Clear on its left and Close on its right. The keyboard does not open.
- [ ] Tapping the bar expands it and searches. Unfocusing shrinks it back.
- [ ] "Any artist" sits directly above the bar, and the list continues upward and scrolls by dragging down.
- [ ] Close and Back return the bar to the chip.
- [ ] The build passes.

### Plan D
1. Add liked/streamed songs to the pool, de-duplicated. Files: `HomeScreen.kt:1327` (E32), with the source from `LikedSongsRepository` (E34). Done when: a streamed-only song appears in the Artist options.
2. Add the online top-up when a selection is set (8s timeout, `analysed`) and show the progress bar. Files: `HomeScreen.kt:390` (E33), `DailyMixStateHolder.kt:103` (E35). Done when: extra songs appear after the spinner, and offline still works.
3. Replace the heading with the Mix/Artist toggle, a per-tab count and per-tab empty states. File: `DailyMixLauncher.kt:288-330` (E36, E37, E42). Done when: the tabs filter the row.
4. Rebuild the filter row with a pinned clear `IconButton` + `VerticalDivider` + chips. File: `DailyMixLauncher.kt:421` (E39). Done when: clear stays fixed while scrolling.
5. Capture chip bounds and add the overlay state (`picking` field + origin rect). File: `DailyMixLauncher.kt:432` (E39). Done when: the origin is logged correctly.
6. Build the search-bar pill with Clear and Close, not auto-focused, expanding on focus. File: `DailyMixLauncher.kt` (replacing E40). Done when: focus and blur expand and shrink it.
7. Build the reverse-layout options list anchored above the bar, with Any first. Reuse `PickerRow`. File: `DailyMixLauncher.kt`. Done when: the order and scroll direction are right.
8. Add the motion: the spring move, the paired button fades, list enter/exit, interruption retargeting and the reduced-motion fallback. Uses E1, E2. Done when: closing mid-open reverses smoothly.
9. Add Back handling and the scrim, and delete `MixFilterPickerDialog`. Done when: Back closes the picker before the sheet.
10. Accessibility: labels, pane title, focus, `Role.Tab`.
11. Verify: build. Then check: open and close each field; close during the move; type then close; Back; animator scale 0x and 0.5x; light and dark; two album palettes; a large library (300 options cap, `MaxOptionsPerField`).

**Risks:**
- A bigger pool makes `buildMixFilterOptions` and `buildDailyMixSheetModel` heavier (E33). They're already off the main thread, but check the time on a large library.
- `mixFilterPool` also feeds the mood chips and Quick Picks (E32 `:335`, `:353`, `:362`). Adding streaming songs changes those too. Either keep a separate `dailyMixPool`, or accept the change.
- The overlay must sit above the docked sheet's own drag handling. Otherwise a drag on the list will drag the sheet.

---

## 4. Assumptions
- **A, step 2:** I assumed the rows in E7 open `SongInfoBottomSheet`. Each caller's `openOptions` / `showSongOptionsSheet` target still needs checking before Select is wired.
- **A, step 11:** multi-reorder (collapse, then insert as a block) is my reading of "reorder multiple". Change it if you'd rather drag a stack.
- **A:** "The expanded feature" is read as the selection bar and the multi-select sheet that open after you select (E10 :689/:779).
- **B:** I couldn't confirm in code why the tab page looks permanently light. The player's colours follow the app setting (E21), so the new override fixes it either way. If a hardcoded light surface turns up, add it to step 3.
- **C, step 5:** the pending-focus holder (singleton vs. nav argument) is unverified.
- **D, step 1:** the de-duplication key `mixIdentity` comes from its use in `PlayerViewModel` (E35). Its signature hasn't been read.
- **D3:** the icon `Icons.Rounded.FilterAltOff` assumes material-icons-extended is present. It's used elsewhere for other icons, but this one isn't verified. The fallback is `Icons.Rounded.ClearAll`.
